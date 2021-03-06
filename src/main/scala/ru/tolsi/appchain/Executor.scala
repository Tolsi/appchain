package ru.tolsi.appchain

import monix.eval.Task
import spray.json.JsValue

trait Executor {
  def contractExecutionLimits: ContractExecutionLimits

  def execute(contract: Contract, params: JsValue): Task[String]

  def apply(contract: Contract, params: JsValue, result: JsValue): Task[String]

  def init(contract: Contract, params: JsValue): Task[String]
}
