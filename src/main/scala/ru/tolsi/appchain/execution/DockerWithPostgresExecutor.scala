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
import scala.util.Try

object DockerWithPostgresExecutor {
  val DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.SSSSSSSSSX")
}

class DockerWithPostgresExecutor(override val docker: DefaultDockerClient, override val contractExecutionLimits: ContractExecutionLimits) extends Executor with DefaultJsonProtocol with ExecutionInDocker {

  import DockerWithPostgresExecutor._

  private def waitInLog(containerId: String, str: String, from: Long): Task[Boolean] = Task {
    val all = docker.logs(containerId, Seq(
      LogsParam.timestamps(true),
      LogsParam.follow(false),
      LogsParam.stderr(false),
      LogsParam.stdout(true),
      LogsParam.tail(100)): _*).readFully()
    val lines = all.split("\n")
    lines.exists(line => Try {
      val lineTs = DateFormat.parse(line.split(" ").head).getTime
      lineTs > from && line.contains(str)
    }.toOption.contains(true))
  }

  private def extractBindedPortFromNetworkSettings(port: Int)(settings: NetworkSettings): Int = {
    settings.ports().asScala(s"$port/tcp").asScala.head.hostPort().toInt
  }

  private def startContainer(containerName: String, waitStringInLog: Option[String] = None, afterStart: String => Task[Unit] = _ => Task.unit): Task[ContainerInfo] = {
    Task(docker.inspectContainer(containerName)).flatMap(cs => {
      val wasRunned = cs.state().running()
      if (!wasRunned) {
        docker.startContainer(containerName)
        afterStart(cs.id())
      } else {
        Task.unit
      }
    }).flatMap(_ => {
      val now = System.currentTimeMillis()
      (waitStringInLog match {
        case Some(str) => waitInLog(containerName, str, now).restartUntil(identity)
        case None => Task.unit
      }).map(_ =>
        docker.inspectContainer(containerName)
      )
    })
  }

  private def makeContractRequest(contract: Contract, body: JsValue): Task[String] = {
    startContainer(contract.stateContainerName, Some("server started")).flatMap(stateContainerInfo =>
      startContainer(contract.containerName, afterStart =
        id =>
          executeCommandInContainer(id, Array[String]("/bin/sh", "-c", "apk update && apk add --no-cache iptables && " +
            "NODE=$(nslookup host.docker.internal | awk -F' ' 'NR==3 { print $3 }') && " +
            s"STATE=${stateContainerInfo.networkSettings().ipAddress()} &&" +
            "iptables -P INPUT DROP && iptables -P OUTPUT DROP && iptables -P FORWARD DROP && " +
            "iptables -A OUTPUT -p tcp --dport 5432 -d $STATE -j ACCEPT && " +
            "iptables -A INPUT -p tcp --sport 5432 -s $STATE -j ACCEPT && " +
            "iptables -A OUTPUT -p tcp --dport 6000 -d $NODE -j ACCEPT && " +
            "iptables -A INPUT -p tcp --sport 6000 -s $NODE -j ACCEPT && " +
            "echo \"$STATE state\" >> /etc/hosts && " +
            "echo \"$NODE node\" >> /etc/hosts"), privileged = true).map(_ => ()))).flatMap(contractContainerInfo => {
      executeCommandInContainer(contractContainerInfo.id(), Array[String]("/bin/sh", "-c", s"/run.sh ${StringEscapeUtils.escapeJavaScript(body.toString())}")).timeout(contractExecutionLimits.timeout.duration)
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

  private def executeCommand(command: String, contract: Contract, params: JsValue): Task[String] = {
    if (params.toString().length > contractExecutionLimits.inputParamsMaxLength) {
      Task.raiseError(new IllegalArgumentException("params are too long"))
    } else makeContractRequest(contract, Map("command" -> JsString(command), "params" -> params).toJson).flatMap(r =>
      if (r.length > contractExecutionLimits.resultMaxLength) {
        Task.raiseError(new IllegalArgumentException("result is too long"))
      } else Task.now(r))
  }

  override def execute(contract: Contract, params: JsValue): Task[String] = {
    executeCommand("execute", contract, params)
  }

  override def init(contract: Contract, params: JsValue): Task[Unit] = {
    executeCommand("init", contract, params).map(_ => ())
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
