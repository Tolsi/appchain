package ru.tolsi.appchain

import monix.eval.Task

trait Deployer {
  def deploy(contract: Contract): Task[Unit]
}
