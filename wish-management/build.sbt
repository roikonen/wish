import scala.collection.immutable.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.7.4"

Compile / run / fork := true

// The following two lines can be removed after updating to future Scala versions
Compile / run / javaOptions += "--sun-misc-unsafe-memory-access=allow"
Test / javaOptions += "--sun-misc-unsafe-memory-access=allow"

lazy val root = (project in file("."))
  .settings(
    name := "wish-management"
  )

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "cask" % "0.11.3",
  "com.lihaoyi" %% "upickle" % "4.4.1",
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

Test / test := {
  val result: Unit = (Test / test).value
  val _ = (Test / runMain).toTask(" fi.roikonen.tools.GwtDocGenerator").value
  result
}
