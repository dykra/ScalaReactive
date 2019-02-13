name := """reactive-lab2"""

version := "1.1"

scalaVersion := "2.12.7"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.17",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.17" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "com.typesafe.akka" %% "akka-persistence" % "2.5.17",
  "org.iq80.leveldb"            % "leveldb"          % "0.9",
  "org.fusesource.leveldbjni"   % "leveldbjni-all"   % "1.8"
)
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.5.17" % Test

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource


mainClass in(Compile, run) := Some("OrderManagerApp")
