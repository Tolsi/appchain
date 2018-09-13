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
  //  private val statePortBindings = Map("5432" -> List(PortBinding.randomPort("0.0.0.0")).asJava)

  private val commonHostBuilder = HostConfig.builder
    .memory(128 * FileUtils.ONE_MB)
    .memorySwap(0L)

  private def contractHostConfig(stateContainerName: String) = commonHostBuilder.build

  private val dbImage = "postgres:10.5-alpine"

  private def stateHostConfig(contract: Contract, stateVolume: Volume) = commonHostBuilder
    .binds(Bind.from(stateVolume).to("/var/lib/postgresql/data").build())
    //    .portBindings(statePortBindings.asJava)
    .build

  private def deployContract(contract: Contract): Task[Unit] = {
    import contract._
    Task {
      docker.pull(image)
      docker.createContainer(ContainerConfig.builder
        .image(image)
        .hostConfig(contractHostConfig(stateContainerName))
        .build, containerName)
    }
  }

  private def deployContractState(contract: Contract): Task[Unit] = Task {
    import contract._
    docker.pull(dbImage)
    val stateVolume = docker.createVolume(Volume.builder().name(stateVolumeName).build())
    docker.createContainer(ContainerConfig.builder
      .image(dbImage)
      .hostConfig(stateHostConfig(contract, stateVolume))
      .exposedPorts("5432")
      .build, stateContainerName)
  }

  private def isDeployed(contract: Contract): Boolean = {
    Try(docker.inspectContainer(contract.containerName)).toEither.isRight
  }

  override def deploy(contract: Contract, params: JsValue): Task[Unit] = {
    if (!isDeployed(contract)) {
      deployContractState(contract).flatMap(_ =>
        deployContract(contract)).flatMap(_ =>
        executor.init(contract, params)).timeout(timeout.duration)
    } else {
      Task.unit
    }
  }
}