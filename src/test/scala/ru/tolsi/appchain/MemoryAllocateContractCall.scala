package ru.tolsi.appchain

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.RegistryAuth
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import org.apache.commons.io.FileUtils
import ru.tolsi.appchain.deploy.DockerDeployer
import ru.tolsi.appchain.execution.DockerExecutor
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object MemoryAllocateContractCall extends DefaultJsonProtocol with StrictLogging {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    docker.auth(RegistryAuth.builder.serverAddress("localhost:5000").build)

    docker.ping()

    val executor = new DockerExecutor(docker, ContractExecutionLimits(1000, 1000, Timeout(5 seconds)))
    val deployer = new DockerDeployer(docker, executor, Timeout(10 seconds))

    val c = Contract("memory-allocate-contract", "localhost:5000/memory-allocate-contract", 1)

    try {
      val issueParams = JsObject()

      val executeParams = JsObject("allocate" -> JsNumber(100 * FileUtils.ONE_MB))

      val resultExecuteF = deployer.deploy(c, issueParams).flatMap(_ =>
        executor.execute(c, executeParams)).runAsync

      val resultExecute = Await.result(resultExecuteF, 5 minutes)

      logger.info(s"Result execute: $resultExecute")

      val applyParams = executeParams

      val resultApplyF = deployer.deploy(c, issueParams).flatMap(_ =>
        executor.apply(c, applyParams, JsObject())).runAsync

      val resultApply = Await.result(resultApplyF, 5 minutes)

      logger.info(s"Result apply: $resultApply")

      logger.info(s"Done!")
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
    } finally {
      Try(docker.killContainer(c.containerName))
      Try(docker.killContainer(c.stateContainerName))

      Try(docker.removeContainer(c.containerName))
      Try(docker.removeContainer(c.stateContainerName))
      Try(docker.removeVolume(c.stateVolumeName))

      docker.close()
    }
  }
}
