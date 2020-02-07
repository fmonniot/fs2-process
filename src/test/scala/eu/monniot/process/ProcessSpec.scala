package eu.monniot.process

import java.nio.file.Paths

import cats.implicits._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import fs2.Stream
import org.scalatest.matchers.should.Matchers

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
      val file = Paths
        .get(System.getProperty("user.dir", "/"), "/doesntexists")
        .normalize()
        .toAbsolutePath.toString

      val expected = s"ls: $file: No such file or directory\n"
      Process.spawn[IO]("ls", file).use { process =>
        process.stderr
          .through(fs2.text.utf8Decode)
          .compile
          .foldMonoid
          .asserting(_ shouldEqual expected)
      }
    }

    "will spawn a process a let us access its standard input" in {
      Process.spawn[IO]("cat").use { process =>
        val in = Stream("hello", " ", "me")
          .through(fs2.text.utf8Encode)
          .through(process.stdin)
          .compile
          .drain
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

