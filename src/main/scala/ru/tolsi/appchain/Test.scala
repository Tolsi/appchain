package ru.tolsi.appchain

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.RegistryAuth
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import ru.tolsi.appchain.deploy.DockerDeployer
import ru.tolsi.appchain.execution.DockerExecutor
import spray.json.DefaultJsonProtocol
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

object Test extends DefaultJsonProtocol {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    val registryAuth = RegistryAuth.builder.serverAddress("localhost:5000").build
    val statusCode = docker.auth(registryAuth)
    println(statusCode)

    docker.ping()

    val deployer = new DockerDeployer(docker)
    val executor = new DockerExecutor(docker)(Timeout(5 seconds))

    //Contract("sum-contract-1", "localhost:5000/sum-contract")
    //Contract("slow-init-contract-1", "localhost:5000/slow-init-contract")
    //Contract("sleep-contract-1", "localhost:5000/sleep-contract")
    try {
      val c = Contract("sleep-contract", "localhost:5000/sleep-contract", 1)

      val params = Map("execute_sleep" -> 4.7, "apply_sleep" -> 4.7).toJson

      val resultF = deployer.deploy(c).flatMap(_ =>
        executor.execute(c.containerName, params)).runAsync

      val result = Await.result(resultF, 1 minute)

      println(result)

      docker.stopContainer(c.containerName, 1)

      println(s"Done!")
    } catch { case NonFatal(e) =>
        e.printStackTrace()
    } finally {
      executor.stop()
      docker.close()
    }
  }
}