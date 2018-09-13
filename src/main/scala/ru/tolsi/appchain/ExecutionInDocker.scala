package ru.tolsi.appchain

import com.spotify.docker.client.DockerClient.ExecCreateParam
import com.spotify.docker.client.{DockerClient, LogStream}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task

trait ExecutionInDocker extends StrictLogging {
  def docker: DockerClient
  def executeCommandInContainer(containerId: String, command: Array[String], privileged: Boolean = false): Task[String] = Task {
    val execId = docker.execCreate(containerId, command,
      ExecCreateParam.attachStdout(true),
      ExecCreateParam.attachStderr(true),
      ExecCreateParam.privileged(privileged)).id()

    var stream: LogStream = null
    try {
      stream = docker.execStart(execId)
      val result = stream.readFully()
      val inspect = docker.execInspect(execId)
      logger.debug(s"Process was finished with code ${inspect.exitCode()}: $result")
      if (inspect.exitCode() == 0) {
        val lastString = result.split("\n").flatMap(_.split("\r"))
          .filter(_.nonEmpty).lastOption.map(_.trim).getOrElse("")
        if (lastString.contains(" ")) "" else lastString
      } else {
        throw new Exception(s"Process was failed with code ${inspect.exitCode()}")
      }
    } finally {
      if (stream != null)
        stream.close()
    }
  }
}
