package ru.tolsi.appchain

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.RegistryAuth
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import ru.tolsi.appchain.deploy.DockerDeployer
import ru.tolsi.appchain.execution.DockerExecutor
import scorex.crypto.encode.Base58
import scorex.crypto.signatures.Curve25519
import spray.json.{DefaultJsonProtocol, JsNumber, JsString, JsValue}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

object VoxelContractCall extends DefaultJsonProtocol with StrictLogging {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    docker.auth(RegistryAuth.builder.serverAddress("localhost:5000").build)

    docker.ping()

    val executor = new DockerExecutor(docker, ContractExecutionLimits(1000, 1000, Timeout(5 seconds)))
    val deployer = new DockerDeployer(docker, executor, Timeout(10 seconds))

    val c = Contract("voxel-contract-scala", "localhost:5000/voxel-contract-scala", 1)

    val (privateKey, publicKey) = Curve25519.createKeyPair(Array[Byte](3,6,3))
    val (secondPrivateKey, secondPublicKey) = Curve25519.createKeyPair(Array[Byte](1,2,6))

    try {
      val issueParams = Map[String, JsValue](
        "issuer" -> JsString(Base58.encode(publicKey)),
        "counterparty" -> JsString(Base58.encode(secondPublicKey)),
        "amount" -> JsNumber(1000000),
        "penalty" -> JsNumber(0.03),
        "until" -> JsNumber(10000000000000L)
      ).toJson

      val requestBytes = "repay".getBytes
      val signature = Curve25519.sign(secondPrivateKey, requestBytes)
      val executeParams = Map[String, JsValue](
        "operation" -> JsString("repay"),
        "counterparty" -> JsString(Base58.encode(secondPublicKey)),
        "signature" -> JsString(Base58.encode(signature))
      ).toJson

      val resultExecuteF = deployer.deploy(c, issueParams).flatMap(_ =>
        executor.execute(c, executeParams)).runAsync

      val resultExecute = Await.result(resultExecuteF, 5 minutes)

//      docker.stopContainer(c.containerName, 1)
//      docker.stopContainer(c.stateContainerName, 1)

      logger.info(s"Result execute: $resultExecute")

      val applyParams = Map[String, JsValue]("operation" -> JsString("balance"), "address" -> JsString(Base58.encode(publicKey))).toJson

      val resultApplyF = deployer.deploy(c, issueParams).flatMap(_ =>
        executor.apply(c, applyParams, JsString(resultExecute))).runAsync

      val resultApply = Await.result(resultApplyF, 5 minutes)

//      docker.stopContainer(c.containerName, 1)
//      docker.stopContainer(c.stateContainerName, 1)

      logger.info(s"Result apply: $resultApply")

      val applyParams2 = Map[String, JsValue]("operation" -> JsString("balance"), "address" -> JsString(Base58.encode(secondPublicKey))).toJson

      val resultApplyF2 = deployer.deploy(c, issueParams).flatMap(_ =>
        executor.apply(c, applyParams2, JsString(resultExecute))).runAsync

      val resultApply2 = Await.result(resultApplyF2, 5 minutes)

      logger.info(s"Result apply 2: $resultApply2")

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
