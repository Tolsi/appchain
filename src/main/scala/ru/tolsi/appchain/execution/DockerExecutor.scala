package ru.tolsi.appchain.execution

import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException

import akka.util.Timeout
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
import ru.tolsi.appchain.Executor
import spray.json.{DefaultJsonProtocol, JsValue}

import scala.collection.JavaConverters._

class DockerExecutor(docker: DefaultDockerClient)(implicit timeout: Timeout) extends Executor with DefaultJsonProtocol {
  private implicit val io: SchedulerService = Scheduler.io(name = "contracts-requests")
  private implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend(SttpBackendOptions.connectionTimeout(timeout.duration))

  def stop(): Unit = {
    sttpBackend.close()
    io.shutdown()
  }

  private def startAppContainer(appName: String): Task[ContainerInfo] = {
    Task(docker.inspectContainer(appName)).map(cs => {
      if (!cs.state().running()) {
        docker.startContainer(appName)
      }
      docker.inspectContainer(appName)
    })
  }

  private def makeContractRequest[B: BodySerializer](appName: String, uriWithPort: Int => Uri, body: B): Task[String] = {
    startAppContainer(appName).flatMap(cs => {
      val bindedPort = cs.networkSettings().ports().asScala.head._2.asScala.head.hostPort().toInt

      val resultF = sttp.body(body)
        .post(uriWithPort(bindedPort))
        .send()

      resultF.map(_.body.right.get).timeoutTo(timeout.duration, Task {
        docker.killContainer(appName)
        throw new TimeoutException("Contract call timeout")
      })
    })
  }

  override def execute(appName: String, params: JsValue): Task[String] = {
    makeContractRequest(appName, bindedPort => uri"http://localhost:$bindedPort/execute", params)
  }

  override def apply(appName: String, params: JsValue, result: JsValue): Task[String] = {
    makeContractRequest(appName, bindedPort => uri"http://localhost:$bindedPort/apply", Map("parameters" -> params, "result" -> result).toJson)
  }
}
