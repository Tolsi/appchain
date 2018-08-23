package ru.tolsi.appchain.execution

import java.nio.ByteBuffer

import com.softwaremill.sttp._
import com.softwaremill.sttp.SttpBackend
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.softwaremill.sttp.json.sprayJson._
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.ContainerInfo
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable
import spray.json.{DefaultJsonProtocol, JsValue}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.collection.JavaConverters._

class DockerExecutor(docker: DefaultDockerClient) extends Executor with DefaultJsonProtocol {
  private implicit val io: SchedulerService = Scheduler.io(name = "contracts-requests")
  private implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend()

  def stop(): Unit = {
    sttpBackend.close()
    io.shutdown()
  }

  private def startAppContainer(appName: String): Either[Throwable, ContainerInfo] = {
    Try(docker.inspectContainer(appName)).toEither.map(cs => {
      if (!cs.state().running()) {
        docker.startContainer(appName)
      }
      docker.inspectContainer(appName)
    })
  }

  override def execute(appName: String, params: JsValue): Task[String] = Task {
    startAppContainer(appName).left.flatMap(_ => throw new IllegalStateException("App container isn't exists")).map(cs => {
      val bindedPort = cs.networkSettings().ports().asScala.head._2.asScala.head.hostPort()

      val resultF = sttp.body(params.toJson)
        .post(uri"http://localhost:$bindedPort/execute")
        .send()

      Await.result(resultF.map(_.body.right.get).runAsync, 1 minute)
    }).right.get
  }

  override def apply(appName: String, params: JsValue, result: JsValue): Task[String] = Task {
    startAppContainer(appName).left.flatMap(_ => throw new IllegalStateException("App container isn't exists")).map(cs => {
      val bindedPort = cs.networkSettings().ports().asScala.head._2.asScala.head.hostPort()

      val resultF = sttp.body(Map("parameters" -> params, "result" -> result).toJson)
        .post(uri"http://localhost:$bindedPort/apply")
        .send()

      Await.result(resultF.map(_.body.right.get).runAsync, 1 minute)
    }).right.get
  }
}
