package ru.tolsi.appchain.deploy

import akka.util.Timeout
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding}
import monix.eval.Task
import org.apache.commons.io.FileUtils
import ru.tolsi.appchain.{Contract, Deployer}

import scala.util.Try
import scala.collection.JavaConverters._

class DockerDeployer(docker: DefaultDockerClient) extends Deployer {
  private val portBindings = Map("5000" -> List(PortBinding.randomPort("0.0.0.0")).asJava)
  private val hostConfig = HostConfig.builder.portBindings(portBindings.asJava)
    .memory(128 * FileUtils.ONE_MB)
    .memorySwap(0L)
    .build

  override def deploy(contract: Contract): Task[String] = Task {
    import contract._
    val containerStatus = Try(docker.inspectContainer(containerName)).toEither

    if (containerStatus.isLeft) {
      docker.pull(image)
      val container = docker.createContainer(ContainerConfig.builder
        .image(image)
        .hostConfig(hostConfig)
        .exposedPorts("5000")
        .build, containerName)
      container.id()
    } else {
      containerStatus.right.get.id()
    }
  }
}