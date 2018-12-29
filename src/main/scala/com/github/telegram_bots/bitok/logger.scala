package com.github.telegram_bots.bitok

import io.chrisdavenport.log4cats.Logger

object logger {
  object syntax extends LoggerSyntax
}

trait LoggerSyntax {
  implicit final def toLoggerOps[F[_]](sc: StringContext): LoggerOps[F] = new LoggerOps(sc)
}

final class LoggerOps[F[_]](private val sc: StringContext) extends AnyVal {
  def info(args: Any*)(implicit L: Logger[F]): F[Unit] = L.info(sc.s(args: _*))

  def error(args: Any*)(implicit L: Logger[F]): F[Unit] = L.error(sc.s(args: _*))

  def warn(args: Any*)(implicit L: Logger[F]): F[Unit] = L.warn(sc.s(args: _*))

  def debug(args: Any*)(implicit L: Logger[F]): F[Unit] = L.debug(sc.s(args: _*))

  def trace(args: Any*)(implicit L: Logger[F]): F[Unit] = L.trace(sc.s(args: _*))
}