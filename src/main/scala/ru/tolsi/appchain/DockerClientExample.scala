package ru.tolsi.appchain


import java.util

import com.spotify.docker.client.{DefaultDockerClient, DockerClient}
import com.spotify.docker.client.messages.{ContainerConfig, HostConfig, PortBinding}
// Create a client based on DOCKER_HOST and DOCKER_CERT_PATH env vars// Create a client based on DOCKER_HOST and DOCKER_CERT_PATH env vars

object DockerClientExample extends App {
  val docker = DefaultDockerClient.fromEnv.build

  // Pull an image
  docker.pull("busybox")

  // Bind container ports to host ports
  val ports = Array("80", "22")
  val portBindings = new util.HashMap[String, java.util.List[PortBinding]]()
  for (port <- ports) {
    val hostPorts = new java.util.ArrayList[PortBinding]
    hostPorts.add(PortBinding.of("0.0.0.0", port))
    portBindings.put(port, hostPorts)
  }

  // Bind container port 443 to an automatically allocated available host port.
  val randomPort = new java.util.ArrayList[PortBinding]
  randomPort.add(PortBinding.randomPort("0.0.0.0"))
  portBindings.put("443", randomPort)

  val hostConfig = HostConfig.builder.portBindings(portBindings).build

  // Create container with exposed ports
  val containerConfig = ContainerConfig.builder.hostConfig(hostConfig).image("busybox").exposedPorts(ports: _*).cmd("sh", "-c", "while :; do sleep 1; done").build

  val creation = docker.createContainer(containerConfig)
  val id = creation.id

  // Inspect container
  val info = docker.inspectContainer(id)

  // Start container
  docker.startContainer(id)

  // Exec command inside running container with attached STDOUT and STDERR
  val command = Array("sh", "-c", "ls")
  val execCreation = docker.execCreate(id, command, DockerClient.ExecCreateParam.attachStdout, DockerClient.ExecCreateParam.attachStderr)
  val output = docker.execStart(execCreation.id)
  val execOutput = output.readFully

  // Kill container
  docker.killContainer(id)

  // Remove container
  docker.removeContainer(id)

  // Close the docker client
  docker.close()
}
