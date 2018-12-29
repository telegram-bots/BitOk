package com.github.telegram_bots.bitnotify

import cats.syntax.functor._
import cats.effect.{ExitCode, IO, IOApp}

import com.github.telegram_bots.bitnotify.BlockChainDotCom._

object Main extends IOApp {
  def run(args: List[String]): IO[ExitCode] =
    args match {
      case ConfigE(cfg) =>
        BitNotify.start(cfg).as(ExitCode.Success)
      case _ =>
        IO(System.err.println("Usage: BitNotify botToken workersNum workersDelay")).as(ExitCode.Error)
    }
}
