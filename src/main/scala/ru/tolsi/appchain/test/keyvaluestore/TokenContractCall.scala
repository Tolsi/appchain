package ru.tolsi.appchain.test.keyvaluestore

import akka.util.Timeout
import com.google.common.primitives.Longs
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.RegistryAuth
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import ru.tolsi.appchain.deploy.{DockerDeployer, DockerWithPostgresDeployer}
import ru.tolsi.appchain.execution.{DockerExecutor, DockerWithPostgresExecutor}
import ru.tolsi.appchain._
import scorex.crypto.encode.Base58
import scorex.crypto.signatures.Curve25519
import spray.json.{DefaultJsonProtocol, JsNumber, JsString, JsValue}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal
import spray.json._

object TokenContractCall extends DefaultJsonProtocol with StrictLogging {
  def main(args: Array[String]): Unit = {
    implicit val io: SchedulerService = Scheduler.forkJoin(10, 10)

    val docker = DefaultDockerClient.fromEnv.build

    docker.auth(RegistryAuth.builder.serverAddress("localhost:5000").build)

    docker.ping()

    val executor = new DockerExecutor(docker, ContractExecutionLimits(1000, 1000, Timeout(5 seconds)))
    val deployer = new DockerDeployer(docker, executor, Timeout(1 minute))

    val c = Contract("token-contract-scala-kv", "localhost:5000/token-contract-scala-kv", 1)

    val (privateKey, publicKey) = Curve25519.createKeyPair(Array[Byte](3,6,3))
    val (secondPrivateKey, secondPublicKey) = Curve25519.createKeyPair(Array[Byte](1,2,6))

    val server = new NodeDataApiMockServer()

    try {
      val issueParams = Map[String, JsValue]("issuer" -> JsString(Base58.encode(publicKey)),
        "amount" -> JsNumber(1000000),
        "contract" -> JsString(c.appName)).toJson

      val requestBytes = publicKey ++ secondPublicKey ++ Longs.toByteArray(2)
      val signature = Curve25519.sign(privateKey, requestBytes)
      val executeParams = Map[String, JsValue](
        "operation" -> JsString("transfer"),
        "from" -> JsString(Base58.encode(publicKey)),
        "to" -> JsString(Base58.encode(secondPublicKey)),
        "amount" -> JsNumber(2),
        "signature" -> JsString(Base58.encode(signature)),
        "contract" -> JsString(c.appName)
      ).toJson

      Await.result(server.start(), 1 minute)
      val resultExecuteF = deployer.deploy(c, issueParams).flatMap(init => {
        val initUpdates = init.parseJson.convertTo[Seq[DataEntry[_]]]
        val newState = server.state.update(c.appName, initUpdates)
        server.updateState(newState)
        executor.execute(c, executeParams)
      }).runAsync

      val resultExecute = Await.result(resultExecuteF, 5 minutes)
      val executeUpdates = resultExecute.parseJson.convertTo[Seq[DataEntry[_]]]
      val newState = server.state.update(c.appName, executeUpdates)

      server.updateState(newState)

      //      docker.stopContainer(c.containerName, 1)
      //      docker.stopContainer(c.stateContainerName, 1)

      logger.info(s"Result execute: $resultExecute")

      logger.info(s"Done!")
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
    } finally {
      Await.result(server.stop(), 10 seconds)
      Try(docker.killContainer(c.containerName))
      Try(docker.killContainer(c.stateContainerName))

      Try(docker.removeContainer(c.containerName))
      Try(docker.removeContainer(c.stateContainerName))
      Try(docker.removeVolume(c.stateVolumeName))

      docker.close()
    }
  }
}
