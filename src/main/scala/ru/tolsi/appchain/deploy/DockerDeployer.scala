package ru.tolsi.appchain.deploy

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.HostConfig.Bind
import com.spotify.docker.client.messages._
import monix.eval.Task
import org.apache.commons.io.FileUtils
import ru.tolsi.appchain.{Contract, Deployer, ExecutionInDocker}

import scala.util.Try

class DockerDeployer(override val docker: DefaultDockerClient) extends Deployer with ExecutionInDocker {
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
      docker.startContainer(containerName)
    }.flatMap(_ =>
      executeCommandInContainer(containerName, Array[String]("/bin/sh", "-c", "apk update && apk add --no-cache iptables && " +
        "NODE=$(nslookup host.docker.internal | awk -F' ' 'NR==3 { print $3 }') && " +
        "STATE=$(ifconfig | grep -A 1 'eth0' | tail -1 | cut -d ':' -f 2 | cut -d ' ' -f 1 | sed 's/3$/4/') &&" +
        "iptables -P INPUT DROP && iptables -P OUTPUT DROP && iptables -P FORWARD DROP && " +
        "iptables -A OUTPUT -p tcp --dport 5432 -d $STATE -j ACCEPT && " +
        "iptables -A INPUT -p tcp --sport 5432 -s $STATE -j ACCEPT && " +
        "iptables -A OUTPUT -p tcp --dport 6000 -d $NODE -j ACCEPT && " +
        "iptables -A INPUT -p tcp --sport 6000 -s $NODE -j ACCEPT && " +
        "echo \"$STATE state\" >> /etc/hosts && " +
        "echo \"$NODE node\" >> /etc/hosts"), privileged = true)
    ).map(_ => ())
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

  override def deploy(contract: Contract): Task[Unit] = {
    if (!isDeployed(contract)) {
      deployContractState(contract).flatMap(_ =>
        deployContract(contract))
    } else {
      Task.unit
    }
  }
}