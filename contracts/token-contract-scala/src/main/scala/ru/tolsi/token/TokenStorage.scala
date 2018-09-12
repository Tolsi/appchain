package ru.tolsi.token

import scalikejdbc._

import scala.util.{Failure, Try}

class TokenStorage {

  import TokenBalance.tb

  def init()(implicit session: DBSession): Unit = {
    sql"create table token_balance(address varchar(50) PRIMARY KEY, balance bigint NOT NULL);".update().apply()
  }

  def balance(address: String)(implicit session: DBSession): Long =
    withSQL {
      select(tb.balance).from(TokenBalance as tb).where.eq(tb.address, address)
    }.map(_.long(1)).headOption().apply().getOrElse(0L)

  def updateBalance(address: String, balance: Long)(implicit session: DBSession): Unit =
    sql"insert into token_balance values($address, $balance) ON CONFLICT (address) DO UPDATE SET balance = $balance".update().apply()

  def transfer(from: Address, to: Address, amount: Long)(implicit session: DBSession): Try[Unit] = {
    val fromBalance = balance(from.publicKey)
    val toBalance = balance(to.publicKey)
    if (fromBalance >= amount) Try {
      updateBalance(from.publicKey, Math.subtractExact(fromBalance, amount))
      updateBalance(to.publicKey, Math.addExact(toBalance, amount))
    } else Failure(new IllegalStateException(s"$from have not enough balance: $fromBalance - $amount = ${fromBalance - amount}"))
  }
}