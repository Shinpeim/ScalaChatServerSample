name := "chatServerSample"

version := "0.1"

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka"        %% "akka-actor" % "2.1.4",
  "org.apache.logging.log4j" %  "log4j-api"  % "2.0-beta6",
  "org.apache.logging.log4j" %  "log4j-core" % "2.0-beta6"
)

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
