package ru.tolsi.appchain.test.withpostgres

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.RegistryAuth
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import ru.tolsi.appchain.deploy.DockerWithPostgresDeployer
import ru.tolsi.appchain.execution.DockerWithPostgresExecutor
import ru.tolsi.appchain.{Contract, ContractExecutionLimits}
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsValue}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object SleepContractCall extends DefaultJsonProtocol with StrictLogging {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    docker.auth(RegistryAuth.builder.serverAddress("localhost:5000").build)

    docker.ping()

    val executor = new DockerWithPostgresExecutor(docker, ContractExecutionLimits(1000, 1000, Timeout(5 seconds)))
    val deployer = new DockerWithPostgresDeployer(docker, executor, Timeout(1 minute))

    val c = Contract("sleep-contract", "localhost:5000/sleep-contract", 1)

    try {
      val executeParams = Map[String, JsValue](
        "init_sleep" -> JsNumber(0),
        "execute_sleep" -> JsNumber(1),
        "apply_sleep" -> JsNumber(1)).toJson

      val issueParams = executeParams

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
