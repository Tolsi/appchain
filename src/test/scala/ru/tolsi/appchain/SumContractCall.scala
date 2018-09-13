package ru.tolsi.appchain

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.RegistryAuth
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import ru.tolsi.appchain.deploy.DockerDeployer
import ru.tolsi.appchain.execution.DockerExecutor
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsString, JsValue}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object SumContractCall extends DefaultJsonProtocol with StrictLogging {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    docker.auth(RegistryAuth.builder.serverAddress("localhost:5000").build)

    docker.ping()

    val executor = new DockerExecutor(docker, ContractExecutionLimits(1000, 1000, Timeout(5 seconds)))
    val deployer = new DockerDeployer(docker, executor, Timeout(5 seconds))

    val c = Contract("sum-contract", "localhost:5000/sum-contract", 1)

    try {
      val issueParams = JsObject()

      val executeParams = Map[String, JsValue](
        "a" -> JsNumber(1000000),
        "b" -> JsNumber(12)).toJson

      val resultExecuteF = deployer.deploy(c, issueParams).flatMap(_ =>
        executor.execute(c, executeParams)).runAsync

      val resultExecute = Await.result(resultExecuteF, 5 minutes)

      logger.info(s"Result execute: $resultExecute")

      val applyParams = executeParams

      val resultApplyF = deployer.deploy(c, issueParams).flatMap(_ =>
        executor.apply(c, applyParams, JsString(resultExecute))).runAsync

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