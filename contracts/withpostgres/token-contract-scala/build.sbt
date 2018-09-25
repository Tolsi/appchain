name := "token-contract"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq(
  "org.scalikejdbc" %% "scalikejdbc" % "3.3.1",
  "com.typesafe.play" %% "play-json" % "2.6.10",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scorexfoundation" %% "scrypto" % "2.1.2",
  "org.postgresql" % "postgresql" % "42.2.5",
  "com.google.guava" % "guava" % "26.0-jre"
)

mainClass in assembly := Some("ru.tolsi.token.App")

assemblyMergeStrategy in assembly := {
  case PathList("module-info.class") => MergeStrategy.discard
  case x => (assemblyMergeStrategy in assembly).value(x)
}