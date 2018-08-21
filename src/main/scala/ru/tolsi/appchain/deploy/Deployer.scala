package ru.tolsi.appchain.deploy

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.ContainerConfig
import java.util

import com.softwaremill.sttp._
import com.softwaremill.sttp.akkahttp.AkkaHttpBackend

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import com.softwaremill.sttp.json.sprayJson._
import spray.json._
import com.spotify.docker.client.messages.{HostConfig, PortBinding}

import scala.concurrent.{Await, Future}
import scala.util.Try

object Deployer extends App with DefaultJsonProtocol {
  val docker = DefaultDockerClient.fromEnv.build

  val containerName = "sum-contract-1"

  val portBindings = Map("5000" -> List(PortBinding.randomPort("0.0.0.0")).asJava)

  val hostConfig = HostConfig.builder.portBindings(portBindings.asJava).build

  val containerStatus = Try(docker.inspectContainer(containerName)).toEither

  if (containerStatus.isLeft) {
    val container = docker.createContainer(ContainerConfig.builder
      .image("localhost:5000/sum-contract")
      .hostConfig(hostConfig)
      .exposedPorts("5000")
      .build, containerName)
  } else if (!containerStatus.right.get.state().running()) {
    docker.startContainer(containerName)
  }

  implicit val sttpBackend = AkkaHttpBackend()

  val bindedPort = docker.inspectContainer(containerName).networkSettings().ports().asScala.head._2.asScala.head.hostPort()

  val resultRequest = sttp.body(Map("a" -> 5, "b" -> 3).toJson)
    .post(uri"http://localhost:$bindedPort/execute")
    .send()

  val r = Await.result(resultRequest, 1 minute)

  println(r.body.right.get)

  docker.stopContainer(containerName, 0)

  println(s"Done!")

  sttpBackend.close()
  docker.close()
}
