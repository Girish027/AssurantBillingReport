organization := "com.dp2"

name := "AssurantBillingReport"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += Resolver.mavenLocal

//resolvers += "Nexus" at "http://nexus-master.cicd.247-inc.net:8081/nexus/content/repositories/releases/"
//resolvers += "Nexus" at "http://nexus-master.cicd.247-inc.net:8081/nexus/content/repositories/nightly/"
resolvers += "Nexus" at "http://localhost:1111/nexus/content/repositories/releases/"

resolvers += "Nexus" at "http://maven.twttr.com"

libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.2.0"

libraryDependencies += "com.tfs.dp" % "spark-avro" % "promoted"
libraryDependencies += "io.spray" %% "spray-json" % "1.3.3"
libraryDependencies += "com.github.nscala-time" %% "nscala-time" % "2.16.0"
libraryDependencies += "org.bouncycastle" % "bcprov-jdk15on" % "1.58" % "provided"
libraryDependencies += "org.jasypt" % "jasypt" % "1.6" % "provided"
libraryDependencies += "com.hadoop.gplcompression" % "hadoop-lzo" % "0.4.20"
libraryDependencies += "org.json" % "json" % "20171018"
libraryDependencies += "org.apache.hadoop" % "hadoop-client" % "2.7.2"
libraryDependencies += "org.jasypt" % "jasypt" % "1.9.2"

libraryDependencies += "junit" % "junit"   % "4.11" % Test
libraryDependencies += "net.liftweb" %% "lift-json" % "2.6+"
libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"
libraryDependencies += "org.scalamock" %% "scalamock" % "4.0.0" % Test

libraryDependencies ++= Seq("org.apache.logging.log4j" %% "log4j-api-scala" % "11.0",
  "org.apache.logging.log4j" % "log4j-api" % "2.8.2",
  "org.apache.logging.log4j" % "log4j-core" % "2.8.2" % Runtime,
  "org.apache.logging.log4j" % "log4j-1.2-api" % "2.8.2",
  "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.8.2")