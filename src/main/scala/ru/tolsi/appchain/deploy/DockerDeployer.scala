package ru.tolsi.appchain.deploy

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.HostConfig.Bind
import com.spotify.docker.client.messages._
import monix.eval.Task
import org.apache.commons.io.FileUtils
import ru.tolsi.appchain._
import spray.json.JsValue

import scala.util.Try

class DockerDeployer(override val docker: DefaultDockerClient, override val executor: Executor, timeout: Timeout) extends Deployer with ExecutionInDocker {
  private val commonHostBuilder = HostConfig.builder
    .memory(128 * FileUtils.ONE_MB)
    .memorySwap(0L)

  private def contractHostConfig = commonHostBuilder.build

  private def deployContract(contract: Contract): Task[Unit] = {
    import contract._
    Task {
      docker.pull(image)
      docker.createContainer(ContainerConfig.builder
        .image(image)
        .hostConfig(contractHostConfig)
        .build, containerName)
    }
  }

  private def isDeployed(contract: Contract): Boolean = {
    Try(docker.inspectContainer(contract.containerName)).toEither.isRight
  }

  override def deploy(contract: Contract, params: JsValue): Task[String] = {
    if (!isDeployed(contract)) {
      deployContract(contract).flatMap(_ =>
        executor.init(contract, params)).timeout(timeout.duration)
    } else {
      Task.now("[]")
    }
  }
}