name := "FBSimulator"

version := "1.0"

scalaVersion := "2.11.7"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.13",
  "com.typesafe.akka" %% "akka-remote" % "2.3.13",
  "org.apache.commons" % "commons-math3" % "3.2",
  "commons-codec" % "commons-codec" % "1.10",
  "io.spray" %% "spray-can" % "1.3.3",
  "io.spray" % "spray-json_2.11" % "1.3.2",
  "io.spray" % "spray-routing_2.11" % "1.3.3",
  "io.spray" % "spray-client_2.11" % "1.3.3",
  "com.google.code.gson" % "gson" % "2.2.4",
  "org.json4s" %% "json4s-native" % "3.2.11"
)
    