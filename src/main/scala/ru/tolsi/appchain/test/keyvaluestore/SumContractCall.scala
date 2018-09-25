package ru.tolsi.appchain.test.keyvaluestore

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.RegistryAuth
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import ru.tolsi.appchain.deploy.DockerDeployer
import ru.tolsi.appchain.execution.DockerExecutor
import ru.tolsi.appchain.{Contract, ContractExecutionLimits, DataEntry}
import ru.tolsi.appchain.DataEntry.dataEntryFormat
import spray.json.{DefaultJsonProtocol, JsNumber, JsObject, JsValue}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal
import spray.json._

object SumContractCall extends DefaultJsonProtocol with StrictLogging {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    docker.auth(RegistryAuth.builder.serverAddress("localhost:5000").build)

    docker.ping()

    val executor = new DockerExecutor(docker, ContractExecutionLimits(1000, 1000, Timeout(5 seconds)))
    val deployer = new DockerDeployer(docker, executor, Timeout(1 minute))

    val c = Contract("sum-contract-kv", "localhost:5000/sum-contract-kv", 1)

    try {
      val issueParams = JsObject()

      val executeParams = Map[String, JsValue](
        "a" -> JsNumber(1000000),
        "b" -> JsNumber(12)).toJson

      val resultExecuteF = deployer.deploy(c, issueParams).flatMap(init =>{
        executor.execute(c, executeParams).map(r =>
          r.parseJson.convertTo[Seq[DataEntry[_]]]
        )}
      ).runAsync

      val resultExecute = Await.result(resultExecuteF, 5 minutes)

      logger.info(s"Result execute: $resultExecute")

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
