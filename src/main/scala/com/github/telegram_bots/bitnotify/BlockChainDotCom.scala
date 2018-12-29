package com.github.telegram_bots.bitnotify

import scala.collection.JavaConverters._
import scala.util.matching.Regex

import cats.data.EitherT
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import cats.effect.Sync
import mouse.any._

import org.jsoup.Jsoup

object BlockChainDotCom {
  implicit final def instance[F[_]](implicit F: Sync[F]): Client[F] with Parser[F] = new Client[F] with Parser[F] {
    val uri = "https://www.blockchain.com/btc/tx"
    val times: Regex = """(\d+)\sConfirmations""".r
    val price: Regex = """([\d\.]+)\sBTC""".r

    def load(id: TxId): F[Array[Byte]] =
      for {
        response <- F.delay(requests.get(s"$uri/$id"))
        body     <- F.delay(response.contents)
      } yield body

    def parse(data: Array[Byte]): F[Either[Throwable, Confirmations]] =
      (for {
        html    <- F.delay(new String(data) |> (Jsoup.parse(_, "https://www.blockchain.com/btc/tx/"))).attemptT
        buttons <- F.delay(html.select(".txdiv > div > button").asScala.map(_.text).toList) |> (EitherT.right(_))
        confirm <- EitherT.fromEither(buttons match {
          case times(t) :: price(_) :: Nil                   => Confirmations(t.toInt).asRight
          case "Unconfirmed Transaction!" :: price(_) :: Nil => Confirmations.Unconfirmed.asRight
          case price(_) :: Nil                               => Confirmations.Confirmed.asRight
          case other                                         => new Throwable(other.toString).asLeft
        })
      } yield confirm).value
  }
}
