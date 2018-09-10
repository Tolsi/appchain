package ru.tolsi.appchain.execution

import com.spotify.docker.client.DockerClient.{ExecCreateParam, LogsParam}
import com.spotify.docker.client.messages.{ContainerInfo, NetworkSettings}
import com.spotify.docker.client.{DefaultDockerClient, LogStream}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import org.apache.commons.lang.StringEscapeUtils
import ru.tolsi.appchain.{Contract, ContractExecutionLimits, Executor}
import spray.json.{DefaultJsonProtocol, JsString, JsValue}

import scala.collection.JavaConverters._

class DockerExecutor(docker: DefaultDockerClient, override val contractExecutionLimits: ContractExecutionLimits) extends Executor with DefaultJsonProtocol with StrictLogging {

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

  private def executeCommandInContainer(containerId: String, command: Array[String], privileged: Boolean = false): Task[String] = Task {
    val execId = docker.execCreate(containerId, command,
      ExecCreateParam.attachStdout(true),
//      ExecCreateParam.attachStderr(true),
      ExecCreateParam.privileged(privileged)).id()

    var stream: LogStream = null
    try {
      stream = docker.execStart(execId)
      val result = stream.readFully()
      val inspect = docker.execInspect(execId)
      logger.debug(s"Process was finished with code ${inspect.exitCode()}: $result")
      if (inspect.exitCode() == 0) {
        result.split("\n").last
      } else {
        throw new Exception(s"Process was failed with code ${inspect.exitCode()}")
      }
    } finally {
      if (stream != null)
        stream.close()
    }
  }

  private def makeContractRequest(contract: Contract, body: JsValue): Task[String] = {
    startContainer(contract.stateContainerName, Some("database system is ready to accept connections")).flatMap(_ =>
      startContainer(contract.containerName)).flatMap(cs => {
      executeCommandInContainer(cs.id(), Array[String]("/bin/sh", "-c", "apk update && apk add --no-cache iptables && " +
        "NODE=$(nslookup host.docker.internal | awk -F' ' 'NR==3 { print $3 }') && " +
        "STATE=$(ifconfig | grep -A 1 'eth0' | tail -1 | cut -d ':' -f 2 | cut -d ' ' -f 1 | sed 's/4$/3/') &&" +
        "iptables -P INPUT DROP && iptables -P OUTPUT DROP && iptables -P FORWARD DROP && " +
        "iptables -A OUTPUT -p tcp --dport 5432 -d $STATE -j ACCEPT && " +
        "iptables -A INPUT -p tcp --sport 5432 -s $STATE -j ACCEPT && " +
        "iptables -A OUTPUT -p tcp --dport 6000 -d $NODE -j ACCEPT && " +
        "iptables -A INPUT -p tcp --sport 6000 -s $NODE -j ACCEPT && " +
        "echo \"$STATE state\" >> /etc/hosts && " +
        "echo \"$NODE node\" >> /etc/hosts"), privileged = true).flatMap(_ => {
        executeCommandInContainer(cs.id(), Array[String]("/bin/sh", "-c", s"/run.sh ${StringEscapeUtils.escapeJavaScript(body.toString())}")).timeout(contractExecutionLimits.timeout.duration)
          .doOnFinish(eo => Task {
            // todo to kill or not to kill?
            eo.foreach(_ => {
              docker.killContainer(contract.containerName)
              docker.killContainer(contract.stateContainerName)
            })
            // todo what to do with data container ???
          })
      })
    })
  }

  override def execute(contract: Contract, params: JsValue): Task[String] = {
    if (params.toString().length > contractExecutionLimits.inputParamsMaxLength) {
      Task.raiseError(new IllegalArgumentException("params are too long"))
    } else makeContractRequest(contract, params).flatMap(r =>
      if (r.length > contractExecutionLimits.resultMaxLength) {
        Task.raiseError(new IllegalArgumentException("result is too long"))
      } else Task.now(r))
  }

  override def apply(contract: Contract, params: JsValue, result: JsValue): Task[String] = {
    if (params.toString().length > contractExecutionLimits.inputParamsMaxLength) {
      Task.raiseError(new IllegalArgumentException("params are too long"))
    } else if (result.toString().length > contractExecutionLimits.inputParamsMaxLength) {
      Task.raiseError(new IllegalArgumentException("result is too long"))
    } else makeContractRequest(contract, Map("command" -> JsString("apply"), "parameters" -> params, "result" -> result).toJson).flatMap(r =>
      if (r.length > contractExecutionLimits.resultMaxLength) {
        Task.raiseError(new IllegalArgumentException("result is too long"))
      } else Task.now(r))
  }
}
