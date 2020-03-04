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
      // Depending on the OS the error message is a bit different
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
        .use { process =>

          val in = (Stream[IO, String]("hello", " ", "me") ++ Stream.sleep_(1.milli))
            .through(fs2.text.utf8Encode)
            .through(process.stdin)
            .onFinalize(IO(println("stdin done")))
            .compile
            .drain

          val out = process.stdout
            .through(fs2.text.utf8Decode)
            .onFinalize(IO(println("stdout done")))
            .compile
            .foldMonoid
            .asserting(_ shouldEqual "hello me")

          (in, out).parMapN { case (_, assert) => assert }
        }
    }

    "will close the stdin when the stream complete" in {
      Process.spawn[IO]("cat")
        .use { process =>

          val bytes =
            Stream("hello", " ", "me").through(fs2.text.utf8Encode) ++
              Stream(", a second", " time").through(fs2.text.utf8Encode) ++
              Stream.sleep_(1.milli)

          val in = bytes.through(process.stdin).compile.drain

          val out = process.stdout
            .through(fs2.text.utf8Decode)
            .compile
            .foldMonoid
            .asserting(_ shouldEqual "hello me, a second time")

          (in, out).parMapN { case (_, assert) => assert }
        }
    }
  }

}
