package ru.tolsi.appchain

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.RegistryAuth
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import ru.tolsi.appchain.deploy.DockerDeployer
import ru.tolsi.appchain.execution.DockerExecutor
import spray.json.{DefaultJsonProtocol, _}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object Test extends DefaultJsonProtocol with StrictLogging {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    docker.auth(RegistryAuth.builder.serverAddress("localhost:5000").build)

    docker.ping()

    val deployer = new DockerDeployer(docker)
    val executor = new DockerExecutor(docker, ContractExecutionLimits(1000, 1000, Timeout(5 seconds)))

    //Contract("sum-contract", "localhost:5000/sum-contract")
    //Contract("slow-init-contract", "localhost:5000/slow-init-contract")
    //Contract("sleep-contract", "localhost:5000/sleep-contract")
    //Contract("memory-allocate-contract", "localhost:5000/memory-allocate-contract")

    //    val c = Contract("sleep-contract", "localhost:5000/sleep-contract", 1)
    val c = Contract("db-hash-contact", "localhost:5000/db-hash-contact", 1)

    try {
//      val params = Map("command" -> "execute".toJson, "params" -> Map("apply_sleep" -> 0.1).toJson).toJson
      val params = Map("params" -> Map("apply_sleep" -> 0.1).toJson).toJson
      val resultF = deployer.deploy(c).flatMap(_ =>
        executor.apply(c, params, JsString("0a41b113c6e24196"))).runAsync

      val result = Await.result(resultF, 5 minutes)

      logger.info(s"Result: $result")

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