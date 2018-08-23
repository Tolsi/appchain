package ru.tolsi.appchain.deploy

import monix.eval.Task

trait Deployer {
  def deploy(appName: String, image: String): Task[String]
}