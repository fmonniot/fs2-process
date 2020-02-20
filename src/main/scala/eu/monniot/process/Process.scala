package eu.monniot.process

import java.nio.ByteBuffer
import java.nio.file.Path

import cats.effect.concurrent.Deferred
import cats.effect.{ConcurrentEffect, Effect, IO, Resource}
import cats.implicits._
import com.zaxxer.nuprocess.{NuAbstractProcessHandler, NuProcess, NuProcessBuilder}
import fs2.concurrent.{InspectableQueue, NoneTerminatedQueue, Queue}
import fs2.{Chunk, Pipe, Stream}
import scodec.bits.ByteVector
import Compat.CollectionConverters._

trait Process[F[_]] {
  def stdout: Stream[F, Byte]

  def stderr: Stream[F, Byte]

  def stdin: Pipe[F, Byte, Unit]

  def kill(): F[Unit]

  def terminate(): F[Unit]

  def pid: F[Int]
}

object Process {

  def spawn[F[_]](args: String*)(implicit F: ConcurrentEffect[F]): Resource[F, Process[F]] =
    spawn(args.toList)

  def spawn[F[_]](args: List[String],
                  cwd: Option[Path] = None,
                  environment: Option[Map[String, String]] = None)(
      implicit F: ConcurrentEffect[F]): Resource[F, Process[F]] = {

    val acquire: F[Process[F]] = for {
      processD <- Deferred[F, Process[F]]
      stdout   <- Queue.noneTerminated[F, ByteVector]
      stderr   <- Queue.noneTerminated[F, ByteVector]
      stdin    <- InspectableQueue.bounded[F, ByteVector](10) // TODO config
      handler = new NuAbstractProcessHandler {

        /**
          * This method is invoked when you call the ''ProcessBuilder#start()'' method.
          * Unlike the ''#onStart(NuProcess)'' method, this method is invoked
          * before the process is spawned, and is guaranteed to be invoked before any
          * other methods are called.
          * The { @link NuProcess} that is starting. Note that the instance is not yet
          * initialized, so it is not legal to call any of its methods, and doing so
          * will result in undefined behavior. If you need to call any of the instance's
          * methods, use ''#onStart(NuProcess)'' instead.
          */
        override def onPreStart(nuProcess: NuProcess): Unit = {
          val proc = processFromNu[F](nuProcess, stdout, stderr, stdin)
          nuProcess.setProcessHandler(proc)
          F.runAsync(processD.complete(proc)) {
              case Left(_)   => IO.unit // todo something with error ?
              case Right(()) => IO.unit
            }
            .unsafeRunSync()
        }

      }
      builder <- F.delay {
        val b = environment.fold(new NuProcessBuilder(args.asJava))(env =>
          new NuProcessBuilder(args.asJava, env.asJava))
        b.setProcessListener(handler)
        cwd.foreach(b.setCwd)
        b
      }

      _       <- F.delay(builder.start())
      process <- processD.get
    } yield process

    val release: Process[F] => F[Unit] = _.terminate()

    Resource.make(acquire)(release)
  }

  def processFromNu[F[_]](
      proc: NuProcess,
      stdoutQ: NoneTerminatedQueue[F, ByteVector],
      stderrQ: NoneTerminatedQueue[F, ByteVector],
      stdinQ: InspectableQueue[F, ByteVector]
  )(implicit F: Effect[F]): NuAbstractProcessHandler with Process[F] =
    new NuAbstractProcessHandler with Process[F] {

      // Nu, unsafe logic

      def enqueueByteBuffer(buffer: ByteBuffer, q: NoneTerminatedQueue[F, ByteVector]): Unit = {
        // Copy the buffer content
        val bv = ByteVector(buffer)
        // Ensure we consume the entire buffer in case it's not used.
        buffer.position(buffer.limit)

        F.runAsync(q.enqueue1(Some(bv))) {
            case Left(_)   => IO.unit // todo something with error ?
            case Right(()) => IO.unit
          }
          .unsafeRunSync()
      }

      override def onStdout(buffer: ByteBuffer, closed: Boolean): Unit =
        enqueueByteBuffer(buffer, stdoutQ)

      override def onStderr(buffer: ByteBuffer, closed: Boolean): Unit =
        enqueueByteBuffer(buffer, stderrQ)

      override def onStdinReady(buffer: ByteBuffer): Boolean = {
        val write = stdinQ.dequeue1
          .flatMap { vector =>
            F.delay {
              buffer.put(vector.toArray)
              buffer.flip()
            }
          }
          .flatMap(_ => stdinQ.getSize)
          .map(_ > 0)

        // false means we have nothing else to write at this time
        var ret: Boolean = false
        F.runAsync(write) {
            case Left(_) => IO.unit // todo something with error ?
            case Right(next) =>
              IO {
                ret = next
              }
          }
          .unsafeRunSync()

        ret
      }

      override def onExit(statusCode: Int): Unit =
        F.runAsync(stdoutQ.enqueue1(None) *> stderrQ.enqueue1(None)) {
            case Left(_)   => IO.unit // todo something with error ?
            case Right(()) => IO.unit
          }
          .unsafeRunSync()

      // Nu, wrapped, safe logic

      override def kill(): F[Unit] = F.delay(proc.destroy(true))

      override def terminate(): F[Unit] = F.delay(proc.destroy(false))

      override def pid: F[Int] = F.delay(proc.getPID)

      // fs2, safe logic

      override def stdout: Stream[F, Byte] =
        stdoutQ.dequeue.flatMap(v => Stream.chunk(Chunk.byteVector(v)))

      override def stderr: Stream[F, Byte] =
        stderrQ.dequeue.flatMap(v => Stream.chunk(Chunk.byteVector(v)))

      override def stdin: Pipe[F, Byte, Unit] =
        _.chunks
          .map(_.toByteVector)
          .through(stdinQ.enqueue)
          .evalMap { _ =>
            // TODO Trigger wantWrite only if queue was empty before this element
            F.delay(proc.wantWrite())
          }
    }

}
