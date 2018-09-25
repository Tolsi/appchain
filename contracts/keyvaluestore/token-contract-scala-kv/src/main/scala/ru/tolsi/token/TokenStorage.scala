package ru.tolsi.token

import com.softwaremill.sttp.StatusCodes
import com.softwaremill.sttp.quick._
import play.api.libs.json.Json

import scala.util.{Failure, Try}
import ru.tolsi.token.DataEntry.Format

class TokenStorage(contractAccount: String) {
  def balance(address: Address): Long = {
    val req = sttp.get(uri"http://node:6000/addresses/data/$contractAccount/${address.publicKey}").send()
    if (req.code == StatusCodes.Ok) {
      Json.parse(req.body.right.get).as[DataEntry[_]].asInstanceOf[IntegerDataEntry].value
    } else if (req.code == StatusCodes.NotFound) {
      0L
    } else {
      throw new IllegalStateException("Can't read value from the node")
    }
  }

  def updateBalance(address: Address, balance: Long): IntegerDataEntry =
    IntegerDataEntry(address.publicKey, balance)

  def transfer(from: Address, to: Address, amount: Long): Try[Seq[IntegerDataEntry]] = {
    val fromBalance = balance(from)
    val toBalance = balance(to)
    if (fromBalance >= amount) Try {
      Seq(
        updateBalance(from, Math.subtractExact(fromBalance, amount)),
        updateBalance(to, Math.addExact(toBalance, amount))
      )
    } else Failure(new IllegalStateException(s"$from have not enough balance: $fromBalance - $amount = ${fromBalance - amount}"))
  }
}