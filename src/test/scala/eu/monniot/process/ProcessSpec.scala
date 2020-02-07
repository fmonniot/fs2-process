package eu.monniot.process

import cats.effect.concurrent.Deferred
import cats.implicits._
import cats.effect.{IO, Resource}
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.Stream
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ProcessSpec extends AsyncIOSpec with Matchers {

  "Process#spawn" - {
    "will spawn a process and let us access its process id" in {
      Process.spawn[IO]("whoami").use { process =>
        process.pid
          .asserting(_ shouldBe >(0))
      }
    }

    "will spawn a process a let us access its standard output" in {
      Process.spawn[IO]("echo", "test").use { process =>
        process.stdout
          .through(fs2.text.utf8Decode)
          .compile
          .foldMonoid
          .asserting(_ shouldEqual "test\n")
      }
    }

    "will spawn a process a let us access its standard error" in {
      val file = "/doesnt/exists"
      val expected = List(
        s"ls: $file: No such file or directory\n",
        s"ls: cannot access '$file': No such file or directory\n"
      )

      Process.spawn[IO]("ls", file).use { process =>
        process.stderr
          .through(fs2.text.utf8Decode)
          .compile
          .foldMonoid
          .asserting(result => expected should contain(result))
      }
    }

    "will spawn a process a let us access its standard input" in {
      Process.spawn[IO]("cat")
          .flatMap(Resource.liftF(Deferred[IO, Unit]).tupleLeft)
          .use { case (process, barrier) =>

            val in = Stream("hello", " ", "me")
              .through(fs2.text.utf8Encode)
              .through(process.stdin)
              .compile
              .drain
              .flatMap(_ => barrier.get)
              .flatMap(_ => process.terminate())

            val out = process.stdout
              .through(fs2.text.utf8Decode)
              .compile
              .foldMonoid
              .flatTap(_ => barrier.complete(()))
              .asserting(_ shouldEqual "hello me")

            (in, out).parMapN { case (_, assert) => assert }
          }

      Process.spawn[IO]("cat").use { process =>
        val in = Stream("hello", " ", "me")
          .through(fs2.text.utf8Encode)
          .through(process.stdin)
          .compile
          .drain
          .flatMap(_ => IO.sleep(10.milliseconds))
          .flatMap(_ => process.terminate())

        val out = process.stdout
          .through(fs2.text.utf8Decode)
          .compile
          .foldMonoid
          .asserting(_ shouldEqual "hello me")

        (in, out).parMapN { case (_, assert) => assert }
      }
    }
  }

}
