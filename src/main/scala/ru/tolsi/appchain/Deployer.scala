package ru.tolsi.appchain

import monix.eval.Task
import spray.json.JsValue

trait Deployer {
  def executor: Executor
  def deploy(contract: Contract, params: JsValue): Task[String]
}
