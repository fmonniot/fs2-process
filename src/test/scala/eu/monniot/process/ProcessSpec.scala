package eu.monniot.process

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import fs2.Stream
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ProcessSpec extends AsyncFreeSpec with AsyncIOSpec with Matchers {

  // Global isn't great, but ScalaTest default serial EC is worse. We don't have access to CE3's thread pool creation.
  override implicit def executionContext: ExecutionContext = ExecutionContext.global

  "Process#spawn" - {
    "will spawn a process and let us access its process id" in {
      Process.spawn[IO]("whoami").use { process =>
        process.pid.asserting(_ shouldBe >(0))
      }
    }

    "will spawn a process and let us access its status code" in {
      Process
        .spawn[IO]("sh", "-c", "exit 2")
        .use(process => process.statusCode)
        .asserting(_ shouldEqual 2)
    }

    "will spawn a process a let us access its standard output" in {
      Process.spawn[IO]("echo", "test").use { process =>
        process.stdout
          .through(fs2.text.utf8.decode)
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
          .through(fs2.text.utf8.decode)
          .compile
          .foldMonoid
          .asserting(result => expected should contain(result))
      }
    }

    "will spawn a process a let us access its standard input" in {
      Process
        .spawn[IO]("cat")
        .use { process =>
          val in = (Stream[IO, String]("hello", " ", "me") ++ Stream.sleep_[IO](1.milli))
            .through(fs2.text.utf8.encode)
            .through(process.stdin)
            .onFinalize(IO(println("stdin done")))
            .compile
            .drain

          val out = process.stdout
            .through(fs2.text.utf8.decode)
            .onFinalize(IO(println("stdout done")))
            .compile
            .foldMonoid
            .asserting(_ shouldEqual "hello me")

          (in, out).parMapN { case (_, assert) => assert }
        }
    }

    "will close the stdin when the stream complete" in {
      Process
        .spawn[IO]("cat")
        .use { process =>
          val bytes =
            Stream("hello", " ", "me").through(fs2.text.utf8.encode) ++
              Stream(", a second", " time").through(fs2.text.utf8.encode) ++
              Stream.sleep_[IO](1.milli)

          val in = bytes.through(process.stdin).compile.drain

          val out = process.stdout
            .through(fs2.text.utf8.decode)
            .compile
            .foldMonoid
            .asserting(_ shouldEqual "hello me, a second time")

          (in, out).parMapN { case (_, assert) => assert }
        }
    }
  }

}
