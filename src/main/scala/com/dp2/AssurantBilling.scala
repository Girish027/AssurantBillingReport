// sirius "nemo-client-sirius" 2018-07-01 2018-07-01 C:\\Users\\girish.prajapati\\Desktop\\Assurant -INSIGHTS-9089\\output
package com.dp2
import java.util.{Date, TimeZone}
import java.text.SimpleDateFormat
import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{SaveMode, SparkSession}

object AssurantBilling {
  Logger.getLogger("org").setLevel(Level.ERROR)
  def main(args: Array[String]): Unit = {

      // Enable below params for production execution.
    val IDMclientId = args(0) //sirius
    println(IDMclientId)
    val date = args(1) //2018-07-01
    println("Start date is : "+date)
    val ToDate = args(2) //2018-07-01
    println("End date is : "+ToDate)
    val outputDir = args(3) // C:\\Users\\girish.prajapati\\Desktop\\Assurant -INSIGHTS-9089\\output
    println("Output path is : "+outputDir)
    val IDM_PATH = getIdmPath(IDMclientId, date)
    println("IDM path is : "+IDM_PATH)

    // Enable below params for local execution.
//    val IDM_PATH = "C:\\Users\\girish.prajapati\\Desktop\\Assurant -INSIGHTS-9089\\input\\raw\\prod\\rtdp\\idm\\events\\sirius\\year=2018\\month=07\\day=17\\hour=18\\min=00\\packed_2018-07-17T1905Z.da096338-2520-4129-adef-b1846636b6a3.0.avro"
//    val outputDir = "C:\\Users\\girish.prajapati\\Desktop\\Assurant -INSIGHTS-9089\\output1"

    val spark = SparkSession.builder().appName("Billing").config("spark.some.config.option","360000").getOrCreate()
//    val spark = SparkSession.builder().appName("Billing").config("spark.some.config.option","360000").config("spark.master", "local").getOrCreate()
    val sc = spark.sparkContext

    val idmDf = spark.read.format("com.databricks.spark.avro").load(IDM_PATH)
    idmDf.createOrReplaceTempView("t1")


    val treatmentDf = spark.sql("""SELECT *
                      FROM t1 WHERE body.typeSpecificBody.body.Treatment IS NOT NULL
                        AND header.channel = 'OMNICHANNEL'
                        AND
                          (
                          (body.typeSpecificBody.body.Treatment.treatmentCategory=='SELF_SERVICE' AND
                          body.typeSpecificBody.body.Treatment.reportDimensions.attributes.intentName IS NOT NULL
                          AND body.typeSpecificBody.body.Treatment.reportDimensions.attributes.billType IS NOT NULL
                          AND body.typeSpecificBody.body.Treatment.reportDimensions.attributes.intentName !='null'
                          AND body.typeSpecificBody.body.Treatment.reportDimensions.attributes.billType !='null')
                          OR
                          (body.typeSpecificBody.body.Treatment.treatmentCategory IS NOT NULL
                          AND body.typeSpecificBody.body.Treatment.treatmentCategory !='SELF_SERVICE'
                          AND body.typeSpecificBody.body.Treatment.reportDimensions.attributes.intentName !='null')
                          )""")
    treatmentDf.createOrReplaceTempView("treatmentDf")


    val targetIdentifierdf = spark.sql("""SELECT
                        body.typeSpecificBody.body.Treatment.deflection.targetIdentifier As targetIdentifier,
                        header.channelSessionId AS channelSessionId
                        FROM t1
                        WHERE body.typeSpecificBody.body.Treatment IS NOT NULL
                        AND body.typeSpecificBody.body.Treatment.treatmentCategory=='DEFLECT'""")
    targetIdentifierdf.createOrReplaceTempView("targetIdentifierdf")


    val billingReportDf = spark.sql("""SELECT
                        DATE_FORMAT(from_unixtime(a.header.timeEpochMillisUTC/1000),'yyyy-MM-dd HH:mm:ss') AS resolutionTime,
                        a.body.typeSpecificBody.body.Treatment.reportDimensions.attributes.trackingId AS User,
                        a.body.typeSpecificBody.body.Treatment.reportDimensions.attributes.intentName AS Intent,
                        a.header.channelSessionId AS channelSessionId,
                        a.body.typeSpecificBody.body.Treatment.reportDimensions.attributes.billType AS billType,
                        a.body.typeSpecificBody.body.Treatment.treatmentCategory AS treatmentCategory,
                        a.body.typeSpecificBody.body.Treatment.deflection.deflectionType AS DeflectionType,
                        a.body.typeSpecificBody.body.Treatment.reportDimensions.journeyType As Journeytype,
                        b.targetIdentifier
                      FROM treatmentDf a LEFT OUTER JOIN targetIdentifierdf b
                        ON a.header.channelSessionId = b.channelSessionId""")
    billingReportDf.createOrReplaceTempView("billingReportDf")


    val channelSessionIDVsIntentName = spark.sql("""SELECT
                        channelSessionId,
                        DeflectionType,
                        Intent,
                        resolutionTime,
                        Journeytype
                      FROM billingReportDf
                      WHERE treatmentCategory = 'DEFLECT'""")
    channelSessionIDVsIntentName.createOrReplaceTempView("channelSessionIDVsIntentName")


    val resultBillingDf = spark.sql("""SELECT
                        T1.targetIdentifier,
                        T1.channelSessionId AS ChannelSessionId,
                        T1.Journeytype,
                        T1.DeflectionType AS EscalationReason,
                        T1.User AS UserId,
                        T1.resolutionTime AS ResolutionTime,
                        CASE WHEN T1.treatmentCategory = 'DEFLECT' THEN TRUE WHEN T1.treatmentCategory = 'SELF_SERVICE'
                          AND T1.resolutionTime<=T2.resolutionTime AND T1.Intent = T2.Intent
                          THEN TRUE ELSE FALSE END AS Escalated,
                        T1.Intent AS Intentname,
                        CASE WHEN T1.treatmentCategory = 'SELF_SERVICE' THEN concat(T1.treatmentCategory,' ',T1.billType)
                         WHEN T1.treatmentCategory = 'DEFLECT' AND T1.DeflectionType = 'BUSINESS_RULE' THEN 'TRANSFER BY DESIGN'
                         WHEN T1.treatmentCategory = 'DEFLECT' AND T1.DeflectionType = 'USER_REQUEST' THEN 'USER_REQUEST'
                         WHEN T1.treatmentCategory = 'DEFLECT' AND T1.DeflectionType = 'APP_DECISION' THEN 'NOT_HANDLED'
                         ELSE '' END AS ResolutionType
                      FROM billingReportDf AS T1 LEFT OUTER JOIN channelSessionIDVsIntentName AS T2
                      ON T1.channelSessionId = T2.channelSessionId""")
    resultBillingDf.createOrReplaceTempView("idmdata")


    val IntConnToAgentEvent = spark.sql("""SELECT
                      DISTINCT body.customProperties.AppInteractionId.value.string As ChannelSessionId,
                      header.optSharedSessionId AS InteractionId
                      FROM t1
                      WHERE body.typeSpecificBody.body.OnlineInteractionConnectionToAgentEvent IS NOT NULL""")
    IntConnToAgentEvent.createOrReplaceTempView("IntConnToAgentEvent")


    val finalquery = spark.sql("""SELECT
                        COALESCE(a.ChannelSessionId,'NULL') AS ChannelSessionId,
                        COALESCE(a.targetIdentifier,'NULL') AS InteractionID,
                        CASE WHEN b.InteractionId IS NULL THEN 'NO' ELSE 'YES' END AS ConnectedChats,
                        'WEB' AS Channel,
                        COALESCE(a.UserId,'NULL') AS UserId,
                        COALESCE(a.ResolutionTime,'NULL') AS ResolutionTime,
                        COALESCE(a.Intentname,'NULL') AS IntentName,
                        COALESCE(a.ResolutionType,'NULL') AS ResolutionType,
                        a.Escalated AS Escalated,
                        COALESCE(a.Journeytype,'NULL') AS JourneyType
                       FROM idmdata As a LEFT OUTER JOIN IntConnToAgentEvent b
                        ON a.ChannelSessionId = b.ChannelSessionId
                        ORDER BY a.ResolutionTime""")
    finalquery.createOrReplaceTempView("finalquery")


    val finalone = spark.sql("""SELECT
                          DISTINCT ChannelSessionId,
                          InteractionID,
                          ConnectedChats,
                          Channel,
                          UserId,
                          ResolutionTime,
                          IntentName,
                          ResolutionType,
                          Escalated,
                          JourneyType
                        FROM finalquery""")
    finalone.coalesce(1).write.format("com.databricks.spark.csv").mode(SaveMode.Overwrite).option("header","true").save(outputDir)
  }

  def getIdmPath(IDMclientId: String, reportDate: String): String = {
    val clientDate: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val BASE_IDM_PATH = "/raw/prod/rtdp/idm/events/"
    val date: Date = clientDate.parse(reportDate)
    val hdfsPath: SimpleDateFormat = new SimpleDateFormat("'year='yyyy'/month='MM'/day='dd'/hour='HH")
    var combinedPath: String = "{"
        for (i <- 0 to 23) {
                            combinedPath += hdfsPath.format(date.getTime() + i * 3600 * 1000) + ","
                            }

    combinedPath = combinedPath.substring(0, combinedPath.length - 1) + "}"
    val inputPath = s"$BASE_IDM_PATH$IDMclientId/$combinedPath/min=00/*"
    inputPath
  }
}