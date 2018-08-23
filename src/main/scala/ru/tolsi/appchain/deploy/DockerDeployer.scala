package ru.tolsi.appchain.deploy

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding}
import monix.eval.Task

import scala.util.Try
import scala.collection.JavaConverters._

class DockerDeployer(docker: DefaultDockerClient) extends Deployer {
  private val portBindings = Map("5000" -> List(PortBinding.randomPort("0.0.0.0")).asJava)
  private val hostConfig = HostConfig.builder.portBindings(portBindings.asJava).build

  override def deploy(containerName: String, image: String): Task[String] = Task.eval {
    val containerStatus = Try(docker.inspectContainer(containerName)).toEither

    if (containerStatus.isLeft) {
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