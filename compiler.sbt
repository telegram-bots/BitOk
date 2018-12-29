import Dependencies.Compile

scalaVersion in ThisBuild := "2.12.8"

scalacOptions in ThisBuild ++= Seq(
  "-Ypartial-unification",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Ywarn-dead-code",
  "-Yno-adapted-args",
  //  "-Xfatal-warnings",
  "-language:existentials",
  "-language:experimental.macros",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps"
)

libraryDependencies in ThisBuild ++= Seq(
  compilerPlugin(Compile.kindProjector),
  compilerPlugin(Compile.macroParadise),
  compilerPlugin(Compile.betterFor),
)