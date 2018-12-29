package com.github.telegram_bots

import java.time.Instant
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Success

import cats.{Functor, Parallel}
import cats.syntax.all._
import cats.instances.list._
import cats.instances.try_._
import cats.effect._
import cats.effect.syntax.all._
import mouse.any._
import mouse.string._

import fs2.concurrent.Queue

import com.bot4s.telegram.api
import com.bot4s.telegram.clients.ScalajHttpClient
import com.bot4s.telegram.methods.{Request, SendMessage}
import com.bot4s.telegram.models.Message
import com.github.telegram_bots.bitnotify._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.estatico.newtype.macros.newtype

package object bitnotify extends Extractors with Data with Implementation {
  @newtype case class BotToken(repr: String)

  @newtype case class TxId(repr: String)
  object TxId {
    def unapply(str: String): Option[TxId] =
      Success(str).filter(_.length == 64).map(BigInt(_, 16)).as(TxId(str)).toOption
  }

  @newtype case class Confirmations(repr: Int)
  object Confirmations {
    val Confirmed = Confirmations(Int.MaxValue)
    val Unconfirmed = Confirmations(0)
    val Unknown = Confirmations(-1)
  }

  case class Config(botToken: BotToken, workersCount: Int, workersDelay: FiniteDuration)

  case class Chat(userId: Long, messageId: Int)

  case class Job(
      chat: Chat,
      txId: TxId,
      lastRunAt: Option[Instant],
      currentN: Int,
      untilN: Int,
      everyN: Int
  )
}

sealed trait Data {
  trait Client[F[_]] {
    def load(id: TxId): F[Array[Byte]]
  }

  trait Parser[F[_]] {
    def parse(data: Array[Byte]): F[Either[Throwable, Confirmations]]
  }

  trait TelegramBotF[F[_]] {
    def token: BotToken

    def actions: List[Message => F[Unit]]

    def requestF[R](request: Request[R]): F[R]

    def reply(chat: Chat, text: String)(implicit F: Functor[F]): F[Unit] =
      requestF(SendMessage(chat.userId, text, replyToMessageId = chat.messageId.some)).as(())

    def runF: F[Unit]
  }

  object TelegramBotF {
    abstract class Polling[F[_]: Effect: ContextShift] extends TelegramBotF[F] {
      private lazy val unsafe = new Unsafe(token.repr, m => actions.traverse(_(m)).toIO.unsafeRunSync, ContextShift[F])
        with api.Polling {}

      def requestF[R](request: Request[R]): F[R] =
        IO.fromFuture(IO(unsafe.request(request))).to[F]

      def runF: F[Unit] =
        IO.fromFuture(IO(unsafe.run)).to[F]
    }

    private[this] abstract class Unsafe[F[_]](token: String, onMessage: Message => Unit, ctx: ContextShift[F])
      extends api.BotBase with api.BotExecutionContext {
      implicit val executionContext: ExecutionContext = ctx.getClass
        .getDeclaredField("ec")
        .unsafeTap(_.setAccessible(true))
        .get(ctx)
        .asInstanceOf[ExecutionContext]

      val client: api.RequestHandler = new ScalajHttpClient(token)

      override def receiveMessage(message: Message): Unit = {
        onMessage(message)
        super.receiveMessage(message)
      }
    }
  }
}

sealed trait Extractors {
  object ChatE {
    def unapply(message: Message): Option[Chat] = Chat(message.chat.id, message.messageId).some
  }

  object ConfigE {
    object Int {
      def unapply(str: String): Option[Int] = str.parseIntOption
    }

    def unapply(list: List[String]): Option[Config] = list match {
      case token :: Int(workersCount) :: Int(workersDelay) :: Nil =>
        Config(BotToken(token), workersCount, workersDelay.seconds).some
      case _ => None
    }
  }

  object JobE {
    object Times {
      def unapply(str: String): Option[Int] = str.parseIntOption.filter((1 to 100).contains(_))
    }

    def unapply(msg: Message): Option[(Chat, TxId, Int, Int)] =
      (msg, msg.text.toList.flatMap(_.trim.split("\\s+"))) match {
        case (ChatE(chat), TxId(txId) :: Nil)                                   => (chat, txId, 2, 2).some
        case (ChatE(chat), TxId(txId) :: Times(times) :: Nil)                   => (chat, txId, times, times).some
        case (ChatE(chat), TxId(txId) :: Times(untilN) :: Times(everyN) :: Nil) => (chat, txId, untilN, everyN).some
        case _                                                                  => None
      }
  }
}

sealed trait Implementation { self: Data with Extractors =>
  class Worker[F[_]: Sync](name: String, in: Queue[F, Job], out: Queue[F, Job]) {
    val log: Logger[F] = Slf4jLogger.unsafeCreate[F].withModifiedString(s"[Worker][$name] " + _)

    def start(client: Client[F], parser: Parser[F]): F[Unit] =
      for {
        _      <- log.info("Started")
        oldJob <- in.tryDequeue1
        _      <- oldJob.fold(Sync[F].unit) { job =>
          for {
            raw    <- client.load(job.txId)
            parsed <- parser.parse(raw)
            _      <- log.warn(s"Unknown confirmations response: $parsed").whenA(parsed.isLeft)
            newJob <- Sync[F].delay(job.copy(
              lastRunAt = Instant.now.some,
              currentN = parsed.getOrElse(Confirmations.Unknown).repr
            ))
            _      <- log.debug(s"Processing: $job <---> $newJob")

            _ <- (out.enqueue1(newJob) *> log.debug(s"Out: $newJob")).whenA {
              import newJob._
              currentN > job.currentN && (currentN % everyN == 0 || currentN >= untilN)
            }
            _ <- (in.enqueue1(newJob) *> log.debug(s"In: $newJob")).whenA {
              newJob.currentN >= 0 && newJob.currentN < newJob.untilN
            }
          } yield ()
        }
      } yield ()
  }

  class Workers[F[_]: Sync: ContextShift: Timer, M[_]](
      size: Int,
      delay: FiniteDuration,
      in: Queue[F, Job],
      out: Queue[F, Job]
  )(implicit P: Parallel[F, M]) {
    def start(client: Client[F], parser: Parser[F]): F[Unit] =
      for {
        _      <- Sync[F].unit
        pool   = Resource(Sync[F].delay {
          val ec = Executors.newFixedThreadPool(size)
          val ctx = ExecutionContext.fromExecutor(ec)
          (ctx, Sync[F].delay(ec.shutdown()))
        })
        workers = (1 to size).map(n => new Worker(s"№$n", in, out)).toList
        _       <- pool.use { ctx =>
          (workers.parTraverse(w => ContextShift[F].evalOn(ctx)(w.start(client, parser))) *> Timer[F].sleep(delay))
            .foreverM[Unit]
        }
      } yield ()
  }

  class Bot[F[_]: ConcurrentEffect: ContextShift: Timer](val token: BotToken, in: Queue[F, Job], out: Queue[F, Job])
    extends TelegramBotF.Polling[F] {
    val log: Logger[F] = Slf4jLogger.unsafeCreate[F].withModifiedString("[Bot] " + _)

    def actions: List[Message => F[Unit]] = List({
      case JobE(chat, txId, untilN, everyN) => track(chat, txId, untilN, everyN)
      case ChatE(chat)                      => reply(chat, "Not a txId!")
    })

    def track(chat: Chat, txId: TxId, untilN: Int, everyN: Int): F[Unit] =
      for {
        job <- Sync[F].delay(Job(chat, txId, None, 0, untilN, everyN))
        _   <- log.debug(s"Add: $job")
        _   <- in.enqueue1(job)
        _   <- reply(chat, s"Added to tracking: $txId")
      } yield ()

    def notifyF: F[Unit] =
      for {
        job <- out.dequeue1
        _   <- log.debug(s"Notify: $job")
        _   <- reply(job.chat, job.currentN match {
          case n if n == Confirmations.Unknown.repr   => s"Failed to check TxId ${job.txId} for confirmations"
          case n if n == Confirmations.Confirmed.repr => s"TxId ${job.txId} is fully confirmed"
          case n                                      => s"TxId ${job.txId} confirmed $n times"
        })
      } yield ()

    def start: F[Fiber[F, Unit]] = Concurrent[F].start {
      for {
        _ <- log.info("Started")
        _ <- runF.start
        _ <- (notifyF.start *> Timer[F].sleep(2.seconds)).foreverM[Unit]
      } yield ()
    }
  }

  object BitNotify {
    def start[F[_]: ContextShift: Timer, M[_]](cfg: Config)(
        implicit C: ConcurrentEffect[F],
        P: Parallel[F, M],
        client: Client[F],
        parser: Parser[F]
    ): F[Unit] =
      for {
        in  <- Queue.unbounded[F, Job]
        out <- Queue.unbounded[F, Job]

        bot     = new Bot(cfg.botToken, in, out)
        workers = new Workers(cfg.workersCount, cfg.workersDelay, in, out)

        _ <- bot.start
        _ <- workers.start(client, parser)
      } yield ()
  }
}