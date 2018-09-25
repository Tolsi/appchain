package ru.tolsi.token

import play.api.libs.json.{JsObject, Json}

import scala.util.control.NonFatal

object App {
  def main(args: Array[String]): Unit = {
    try {
      val paramsString = args(0)
      val json = Json.parse(paramsString)
      val command = json("command").as[String]
      val params = json("params").as[JsObject]
      val contractAccount = params("contract").as[String]
      val storage = new TokenStorage(contractAccount)
      command match {
        case "init" =>
          val amount = params("amount").as[Long]
          val issuer = Address(params("issuer").as[String])
          require(amount > 0, "amount should be positive")
          println(Json.arr(storage.updateBalance(issuer, amount).toJson))
        case "execute" | "apply" =>
          val operation = params("operation").as[String]
          operation match {
            case "balance" =>
              println(storage.balance(Address(params("address").as[String])))
            case "transfer" =>
              val t = Transfer(Address(params("from").as[String]), Address(params("to").as[String]), params("amount").as[Long], params("signature").as[String])
              println(Json.toJson(storage.transfer(t.from, t.to, t.amount).get.map(_.toJson)))
          }
      }
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        System.exit(-1)
    }
  }
}
