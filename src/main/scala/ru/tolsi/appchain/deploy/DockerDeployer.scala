package ru.tolsi.appchain.deploy

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.HostConfig.Bind
import com.spotify.docker.client.messages._
import monix.eval.Task
import org.apache.commons.io.FileUtils
import ru.tolsi.appchain.{Contract, Deployer}

import scala.util.Try

class DockerDeployer(docker: DefaultDockerClient) extends Deployer {
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

  private def deployContract(contract: Contract): Unit = {
    import contract._
    docker.pull(image)
    docker.createContainer(ContainerConfig.builder
      .image(image)
      .hostConfig(contractHostConfig(stateContainerName))
      .build, containerName)
  }

  private def deployContractState(contract: Contract): Unit = {
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

  override def deploy(contract: Contract): Task[Unit] = Task {
    if (!isDeployed(contract)) {
      deployContractState(contract)
      deployContract(contract)
    }
  }
}