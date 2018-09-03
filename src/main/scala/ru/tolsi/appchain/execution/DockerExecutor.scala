package ru.tolsi.appchain.execution

import java.io.PrintWriter
import java.net.{InetSocketAddress, Socket}
import java.nio.ByteBuffer

import akka.util.Timeout
import com.softwaremill.sttp.asynchttpclient.monix.AsyncHttpClientMonixBackend
import com.softwaremill.sttp.json.sprayJson._
import com.softwaremill.sttp.{SttpBackend, _}
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient.LogsParam
import com.spotify.docker.client.messages.{ContainerInfo, NetworkSettings}
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable
import ru.tolsi.appchain.{Contract, Executor}
import spray.json.{DefaultJsonProtocol, JsValue}

import scala.collection.JavaConverters._
import scala.util.Try

class DockerExecutor(docker: DefaultDockerClient)(implicit timeout: Timeout) extends Executor with DefaultJsonProtocol {
  private implicit val io: SchedulerService = Scheduler.io(name = "contracts-requests")
  private implicit val sttpBackend: SttpBackend[Task, Observable[ByteBuffer]] = AsyncHttpClientMonixBackend(SttpBackendOptions.connectionTimeout(timeout.duration))

  def stop(): Unit = {
    sttpBackend.close()
    io.shutdown()
  }

  private def checkHostPort(host: String, port: Int): Task[Boolean] = Task {
    Try {
      val s = new Socket()
      try {
        s.connect(new InetSocketAddress(host, port), 1000)
      } finally {
        s.close()
      }
    }.isSuccess
  }

  private def waitInLog(containerId: String, str: String): Task[Boolean] = Task {
    docker.logs(containerId, Seq(
      LogsParam.timestamps(false),
      LogsParam.follow(false),
      LogsParam.stderr(false),
      LogsParam.stdout(true),
      LogsParam.tail(100)): _*).readFully().contains(str)
  }

  private def extractBindedPortFromNetworkSettings(port: Int)(settings: NetworkSettings): Int = {
    settings.ports().asScala(s"$port/tcp").asScala.head.hostPort().toInt
  }

  private def startContainer(containerName: String, testPort: NetworkSettings => Int, waitStringInLog: Option[String] = None): Task[ContainerInfo] = {
    Task(docker.inspectContainer(containerName)).flatMap(cs => {
      if (!cs.state().running()) {
        docker.startContainer(containerName)
      }
      checkHostPort("localhost", testPort(docker.inspectContainer(containerName).networkSettings())).restartUntil(identity).flatMap(_ =>
        waitStringInLog match {
          case Some(str) => waitInLog(cs.id(), str).restartUntil(identity)
          case None => Task.unit
        }
      ).map(_ =>
        docker.inspectContainer(containerName)
      )
    })
  }

  private def makeContractRequest[B: BodySerializer](contract: Contract, uriWithPort: Int => Uri, body: B): Task[String] = {
    startContainer(contract.stateContainerName, extractBindedPortFromNetworkSettings(5432), Some("database system is ready to accept connections")).flatMap(_ =>
      startContainer(contract.containerName, extractBindedPortFromNetworkSettings(5000))).flatMap(cs => {
      val bindedPorts = cs.networkSettings().ports().asScala
      val bindedHttpPort = bindedPorts.head._2.asScala.head.hostPort().toInt

      val resultF = sttp.body(body)
        .post(uriWithPort(bindedHttpPort))
        .send()

      resultF.map(r =>
        r.body.right.get
      ).timeout(timeout.duration).doOnFinish(eo => Task {
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
