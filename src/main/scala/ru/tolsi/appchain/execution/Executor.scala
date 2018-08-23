package ru.tolsi.appchain.execution

import monix.eval.Task
import spray.json.JsValue

trait Executor {
  def execute(appName: String, params: JsValue): Task[String]

  def apply(appName: String, params: JsValue, result: JsValue): Task[String]
}