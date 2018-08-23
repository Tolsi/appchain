package ru.tolsi.appchain

import com.spotify.docker.client.DefaultDockerClient
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import ru.tolsi.appchain.deploy.DockerDeployer
import ru.tolsi.appchain.execution.DockerExecutor
import spray.json.DefaultJsonProtocol
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._

object Test extends DefaultJsonProtocol {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    val deployer = new DockerDeployer(docker)
    val executor = new DockerExecutor(docker)


    //      .image("localhost:5000/sum-contract")
    try {
      val containerName = "sleep-contract-1"

      val resultF = deployer.deploy("sleep-contract-1", "localhost:5000/sleep-contract").flatMap(_ =>
        executor.execute("sleep-contract-1", Map("execute_sleep" -> 20, "apply_sleep" -> 20).toJson)).runAsync

      val result = Await.result(resultF, 1 minute)

      println(result)

      docker.stopContainer(containerName, 0)

      println(s"Done!")
    } finally {
      executor.stop()
      docker.close()
    }
  }
}