package ru.tolsi.appchain.deploy

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.HostConfig.Bind
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding, Volume}
import monix.eval.Task
import org.apache.commons.io.FileUtils
import ru.tolsi.appchain.{Contract, Deployer}

import scala.util.Try
import scala.collection.JavaConverters._

class DockerDeployer(docker: DefaultDockerClient) extends Deployer {
  private val portBindings = Map("5000" -> List(PortBinding.randomPort("0.0.0.0")).asJava)

  private val commonHostBuilder = HostConfig.builder
    .memory(128 * FileUtils.ONE_MB)
    .memorySwap(0L)

  private def contractHostConfig(stateContainerName: String) = commonHostBuilder
    .portBindings(portBindings.asJava)
    .links(stateContainerName)
    .build

  private val dbImage = "oscarfonts/h2:alpine"
  private def stateHostConfig(contract: Contract, stateVolume: Volume) =commonHostBuilder
    .binds(Bind.from(stateVolume).to("/opt/h2-data").build())
    .build

  private def deployContract(contract: Contract): Unit = {
    import contract._
    docker.pull(image)
    docker.createContainer(ContainerConfig.builder
      .image(image)
      .hostConfig(contractHostConfig(stateContainerName))
      .exposedPorts("5000")
      .build, containerName)
  }

  private def deployContractState(contract: Contract): Unit = {
    import contract._
    docker.pull(dbImage)
    val stateVolume = docker.createVolume(Volume.builder().name(stateVolumeName).build())
    docker.createContainer(ContainerConfig.builder
      .image(dbImage)
      .hostConfig(stateHostConfig(contract, stateVolume))
      .build, stateContainerName)
  }

  private def isDeployed(contract: Contract): Boolean = {
    Try(docker.inspectContainer(contract.containerName)).toEither.isRight
  }

  override def deploy(contract: Contract): Task[Unit] = Task {
    if (!isDeployed(contract)) {
      deployContractState(contract)
      deployContract(contract)
    }
  }
}