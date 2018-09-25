package ru.tolsi.voxel

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
            val amount = params("amount").as[Long]
            val issuer = Address(params("issuer").as[String])
            val penalty = params("penalty").as[Float]
            val until = params("until").as[Long]
            val counterparty = Address(params("counterparty").as[String])

            require(amount > 0, "amount should be positive")

            storage.init()

            storage.setParam("treasuryPublicKey", issuer.publicKey)
            storage.setParam("counterpartyPublicKey", counterparty.publicKey)
            storage.setParam("penalty", penalty.toString)
            storage.setParam("until", until.toString)

            storage.updateBalance(issuer, amount)
            println()
          case "execute" | "apply" =>
            val operation = params("operation").as[String]
            operation match {
              case "balance" =>
                println(storage.balance(Address(params("address").as[String])))
              case "repay" =>
                val r = Repay(Address(params("counterparty").as[String]), params("signature").as[String])

                val now = System.currentTimeMillis()

                val treasuryPublicKey = Address(storage.getParam("treasuryPublicKey").get)
                val counterpartyPublicKey = Address(storage.getParam("counterpartyPublicKey").get)
                val penalty = storage.getParam("penalty").get.toFloat
                val until = storage.getParam("until").get.toLong

                val applyPenalty = now < until

                val appliedAmount = if (r.counterParty == counterpartyPublicKey) {
                  val fullAmount = storage.balance(treasuryPublicKey)

                  val finalAmount = if (applyPenalty) {
                    fullAmount - (fullAmount * penalty).toLong
                  } else {
                    fullAmount
                  }

                  storage.updateBalance(treasuryPublicKey, 0)
                  storage.updateBalance(counterpartyPublicKey, finalAmount)
                  finalAmount
                } else {
                  throw new IllegalAccessException("counter party address is not match to declared")
                }
                println(appliedAmount)
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
