import Dependencies._

name         := "BitOk"
organization := "telegram-bots"
version      := "0.0.1"
scalaVersion := "2.12.8"

libraryDependencies ++= Other.requests ++ Other.jsoup ++ Other.fs2 ++ Other.bot4sCore ++ StdLib.apply ++ FP.apply ++ Logging.apply ++ Testing.apply
shellPrompt in ThisBuild := { state =>
  val project = Project.extract(state).currentRef.project
  s"[$project]> "
}