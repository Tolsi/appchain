name := "token-contract-kv"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % "2.6.10",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scorexfoundation" %% "scrypto" % "2.1.2",
  "com.google.guava" % "guava" % "26.0-jre",
  "com.typesafe.play" %% "play-json" % "2.6.9",
  "com.softwaremill.sttp" %% "core" % "1.3.4"
)

mainClass in assembly := Some("ru.tolsi.token.App")

assemblyMergeStrategy in assembly := {
  case PathList("module-info.class") => MergeStrategy.discard
  case x => (assemblyMergeStrategy in assembly).value(x)
}