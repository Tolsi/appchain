package ru.tolsi.appchain.execution

import java.text.SimpleDateFormat

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient.LogsParam
import com.spotify.docker.client.messages.{ContainerInfo, NetworkSettings}
import monix.eval.Task
import org.apache.commons.lang.StringEscapeUtils
import ru.tolsi.appchain.{Contract, ContractExecutionLimits, ExecutionInDocker, Executor}
import spray.json.{DefaultJsonProtocol, JsString, JsValue}

import scala.collection.JavaConverters._

object DockerExecutor {
  val DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSSSSSSSX")
}
class DockerExecutor(override val docker: DefaultDockerClient, override val contractExecutionLimits: ContractExecutionLimits) extends Executor with DefaultJsonProtocol with ExecutionInDocker {
  import DockerExecutor._
  private def waitInLog(containerId: String, str: String, from: Long = System.currentTimeMillis()): Task[Boolean] = Task {
    val all = docker.logs(containerId, Seq(
      LogsParam.timestamps(true),
      LogsParam.follow(false),
      LogsParam.stderr(false),
      LogsParam.stdout(true),
      LogsParam.tail(100)): _*).readFully()
    val lines = all.split("\n")
    lines.exists(line => {
      DateFormat.parse(line.split(" ").head).getTime > from && line.contains(str)
    })
  }

  private def extractBindedPortFromNetworkSettings(port: Int)(settings: NetworkSettings): Int = {
    settings.ports().asScala(s"$port/tcp").asScala.head.hostPort().toInt
  }

  private def startContainer(containerName: String, waitStringInLog: Option[String] = None): Task[ContainerInfo] = {
    Task(docker.inspectContainer(containerName)).flatMap(cs => {
      if (!cs.state().running()) {
        docker.startContainer(containerName)
      }
      (waitStringInLog match {
        case Some(str) => waitInLog(cs.id(), str).restartUntil(identity)
        case None => Task.unit
      }).map(_ =>
        docker.inspectContainer(containerName)
      )
    })
  }

  private def makeContractRequest(contract: Contract, body: JsValue): Task[String] = {
    startContainer(contract.stateContainerName, Some("database system is ready to accept connections")).flatMap(_ =>
      startContainer(contract.containerName)).flatMap(cs => {
      executeCommandInContainer(cs.id(), Array[String]("/bin/sh", "-c", s"/run.sh ${StringEscapeUtils.escapeJavaScript(body.toString())}")).timeout(contractExecutionLimits.timeout.duration)
        .doOnFinish(eo => Task {
          // todo to kill or not to kill?
          eo.foreach(_ => {
            docker.stopContainer(contract.containerName, 1)
            docker.stopContainer(contract.stateContainerName, 1)
          })
          // todo what to do with data container ???
        })
    })
  }

  override def execute(contract: Contract, params: JsValue): Task[String] = {
    if (params.toString().length > contractExecutionLimits.inputParamsMaxLength) {
      Task.raiseError(new IllegalArgumentException("params are too long"))
    } else makeContractRequest(contract, Map("command" -> JsString("execute"), "params" -> params).toJson).flatMap(r =>
      if (r.length > contractExecutionLimits.resultMaxLength) {
        Task.raiseError(new IllegalArgumentException("result is too long"))
      } else Task.now(r))
  }

  override def apply(contract: Contract, params: JsValue, result: JsValue): Task[String] = {
    if (params.toString().length > contractExecutionLimits.inputParamsMaxLength) {
      Task.raiseError(new IllegalArgumentException("params are too long"))
    } else if (result.toString().length > contractExecutionLimits.inputParamsMaxLength) {
      Task.raiseError(new IllegalArgumentException("result is too long"))
    } else makeContractRequest(contract, Map("command" -> JsString("apply"), "params" -> params, "result" -> result).toJson).flatMap(r =>
      if (r.length > contractExecutionLimits.resultMaxLength) {
        Task.raiseError(new IllegalArgumentException("result is too long"))
      } else Task.now(r))
  }
}
