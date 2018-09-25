package ru.tolsi.token

import scalikejdbc._

import scala.util.{Failure, Try}

class TokenStorage {

  def init()(implicit session: DBSession): Unit = {
    sql"create table token_balance(address varchar(50) PRIMARY KEY, balance bigint NOT NULL)".update().apply()
  }

  def balance(address: Address)(implicit session: DBSession): Long =
    sql"select balance from token_balance where address = ${address.publicKey}".map(_.long(1)).headOption().apply().getOrElse(0L)

  def updateBalance(address: Address, balance: Long)(implicit session: DBSession): Unit =
    sql"insert into token_balance values(${address.publicKey}, $balance) ON CONFLICT (address) DO UPDATE SET balance = $balance".update().apply()

  def transfer(from: Address, to: Address, amount: Long)(implicit session: DBSession): Try[Unit] = {
    val fromBalance = balance(from)
    val toBalance = balance(to)
    if (fromBalance >= amount) Try {
      updateBalance(from, Math.subtractExact(fromBalance, amount))
      updateBalance(to, Math.addExact(toBalance, amount))
    } else Failure(new IllegalStateException(s"$from have not enough balance: $fromBalance - $amount = ${fromBalance - amount}"))
  }
}