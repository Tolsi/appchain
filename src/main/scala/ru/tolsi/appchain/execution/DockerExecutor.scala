package ru.tolsi.appchain.execution

import java.nio.ByteBuffer

import akka.util.Timeout
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.softwaremill.sttp.json.sprayJson._
import com.softwaremill.sttp.{SttpBackend, _}
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.ContainerInfo
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable
import ru.tolsi.appchain.{Contract, Executor}
import spray.json.{DefaultJsonProtocol, JsValue}

import scala.collection.JavaConverters._

class DockerExecutor(docker: DefaultDockerClient)(implicit timeout: Timeout) extends Executor with DefaultJsonProtocol {
  private implicit val io: SchedulerService = Scheduler.io(name = "contracts-requests")
  private implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend(SttpBackendOptions.connectionTimeout(timeout.duration))

  def stop(): Unit = {
    sttpBackend.close()
    io.shutdown()
  }

  private def startContainer(containerName: String): Task[ContainerInfo] = {
    Task(docker.inspectContainer(containerName)).map(cs => {
      if (!cs.state().running()) {
        docker.startContainer(containerName)
      }
      docker.inspectContainer(containerName)
    })
  }

  private def makeContractRequest[B: BodySerializer](contract: Contract, uriWithPort: Int => Uri, body: B): Task[String] = {
    startContainer(contract.stateContainerName).flatMap(_ => startContainer(contract.containerName)).flatMap(cs => {
      val bindedPorts = cs.networkSettings().ports().asScala
      val bindedHttpPort = bindedPorts.head._2.asScala.head.hostPort().toInt

      val resultF = sttp.body(body)
        .post(uriWithPort(bindedHttpPort))
        .send()

      resultF.map(_.body.right.get).timeout(timeout.duration).doOnFinish(eo => Task {
        // todo to kill or not to kill?
        eo.foreach(_ => {
          docker.killContainer(contract.containerName)
          docker.killContainer(contract.stateContainerName)
        })
        // todo what to do with data container ???
      })
    })
  }

  override def execute(contract: Contract, params: JsValue): Task[String] = {
    makeContractRequest(contract, bindedPort => uri"http://localhost:$bindedPort/execute", params)
  }

  override def apply(contract: Contract, params: JsValue, result: JsValue): Task[String] = {
    makeContractRequest(contract, bindedPort => uri"http://localhost:$bindedPort/apply", Map("parameters" -> params, "result" -> result).toJson)
  }
}
