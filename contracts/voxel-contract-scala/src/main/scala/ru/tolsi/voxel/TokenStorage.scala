package ru.tolsi.voxel

import scalikejdbc._

class TokenStorage {
  def init()(implicit session: DBSession): Unit = {
    sql"create table token_balance(address varchar(50) PRIMARY KEY, balance bigint NOT NULL)".update().apply()
    sql"create table params(name varchar(50) PRIMARY KEY, value varchar(50) NOT NULL)".update().apply()
  }

  def setParam(name: String, value: String)(implicit session: DBSession): Unit = {
    sql"insert into params values($name, $value) ON CONFLICT (name) DO UPDATE SET value = $value".update().apply()
  }

  def getParam(name: String)(implicit session: DBSession): Option[String] =
    sql"select value from params where name = $name".map(_.string(1)).headOption().apply()

  def balance(address: Address)(implicit session: DBSession): Long =
    sql"select balance from token_balance where address = ${address.publicKey}".map(_.long(1)).headOption().apply().getOrElse(0L)

  def updateBalance(address: Address, balance: Long)(implicit session: DBSession): Unit =
    sql"insert into token_balance values(${address.publicKey}, $balance) ON CONFLICT (address) DO UPDATE SET balance = $balance".update().apply()

}