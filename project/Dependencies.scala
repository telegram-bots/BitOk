import sbt._

sealed trait Dependencies {
  val version: AnyRef
  def apply: Seq[ModuleID]
}

object Dependencies {
  object Other extends Other
  object StdLib extends StdLib
  object Compile extends Compile
  object FP extends FP
  object Logging extends Logging
  object Testing extends Testing
}

trait Other extends Dependencies {
  object version {
    val bot4s    = "4.0.0-RC2"
    val fs2      = "1.0.1"
    val requests = "0.1.4"
    val jsoup    = "1.11.3"
  }

  lazy val bot4sCore = Seq("com.bot4s" %% "telegram-core" % version.bot4s)

  lazy val requests = Seq("com.lihaoyi" %% "requests" % version.requests)

  lazy val jsoup = Seq("org.jsoup" % "jsoup" % version.jsoup)

  lazy val fs2 = Seq("co.fs2" %% "fs2-core" % version.fs2)

  lazy val apply     = Seq()
}

trait StdLib extends Dependencies {
  object version {
    val newType         = "0.4.2"
    val enumeratum      = "1.5.13"
    val java8Compat     = "0.9.0"
  }

  lazy val newType     = "io.estatico"            %% "newtype"            % version.newType
  lazy val enumeratum  = "com.beachape"           %% "enumeratum"         % version.enumeratum
  lazy val java8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % version.java8Compat

  lazy val apply      = Seq(newType, enumeratum, java8Compat)
}

trait Compile extends Dependencies {
  object version {
    val kindProjector = "0.9.9"
    val macroParadise = "2.1.1"
    val betterFor     = "0.3.0-M4"
  }

  lazy val kindProjector = "org.spire-math"       %% "kind-projector"     % version.kindProjector
  lazy val betterFor     = "com.olegpy"           %% "better-monadic-for" % version.betterFor
  lazy val macroParadise = "org.scalamacros"      %  "paradise_2.12.8"    % version.macroParadise

  lazy val apply         = Seq(kindProjector, betterFor, macroParadise)
}

trait FP extends Dependencies {
  object version {
    val catsCore        = "1.5.0"
    val catsEffect      = "1.1.0"
    val catsTagless     = "0.2.0"
    val kittens         = "1.2.0"
    val mouse           = "0.20"
    val shapeless       = "2.3.3"
  }

  lazy val catsCore      = "org.typelevel"        %% "cats-core"           % version.catsCore
  lazy val catsEffect    = "org.typelevel"        %% "cats-effect"         % version.catsEffect
  lazy val catsTagless   = "org.typelevel"        %% "cats-tagless-macros" % version.catsTagless
  lazy val kittens       = "org.typelevel"        %% "kittens"             % version.kittens
  lazy val mouse         = "org.typelevel"        %% "mouse"               % version.mouse
  lazy val shapeless     = "com.chuusai"          %% "shapeless"           % version.shapeless

  lazy val apply         = Seq(catsCore, catsEffect, kittens, mouse, shapeless)
}

trait Logging extends Dependencies {
  object version {
    val log4cats = "0.2.0"
    val logback  = "1.2.3"
  }

  lazy val log4catsCore  = "io.chrisdavenport" %% "log4cats-core"   % version.log4cats
  lazy val log4catsSlf4j = "io.chrisdavenport" %% "log4cats-slf4j"  % version.log4cats
  lazy val logback       = "ch.qos.logback"    %  "logback-classic" % version.logback

  lazy val apply = Seq(log4catsCore, log4catsSlf4j, logback)
}

trait Testing extends Dependencies {
  object version {
    val scalaTest = "3.0.5"
    val scalaMock = "3.6.0"
  }

  lazy val scalaTest = "org.scalatest" %% "scalatest"                   % version.scalaTest % Test
  lazy val scalaMock = "org.scalamock" %% "scalamock-scalatest-support" % version.scalaMock % Test

  lazy val apply     = Seq(scalaTest, scalaMock)
}