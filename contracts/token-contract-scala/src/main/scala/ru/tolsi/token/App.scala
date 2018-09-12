package ru.tolsi.token

import play.api.libs.json.{JsObject, Json}
import scalikejdbc.{ConnectionPool, DB}

import scala.util.control.NonFatal

object App {
  Class.forName("org.postgresql.Driver")
  ConnectionPool.singleton("jdbc:postgresql://state/", "postgres", "postgres")

  def main(args: Array[String]): Unit = {
    try {
      val paramsString = args(0)
      val json = Json.parse(paramsString)
      val command = json("command").as[String]
      val params = json("params").as[JsObject]
      val storage = new TokenStorage()
      DB localTx { implicit session =>
        command match {
          case "init" =>
            storage.init()
            storage.updateBalance(params("issuer").as[String], params("amount").as[Long])
            println()
          case "execute" | "apply" =>
            val operation = params("operation").as[String]
            operation match {
              case "balance" =>
                println(storage.balance(params("address").as[String]))
              case "transfer" =>
                val t = Transfer(Address(params("from").as[String]), Address(params("to").as[String]), params("amount").as[Long], params("signature").as[String])
                storage.transfer(t.from, t.to, t.amount).get
                println()
            }
        }
      }
    } catch {
      case NonFatal(e) =>
        e.printStackTrace()
        System.exit(-1)
    }
  }
}
