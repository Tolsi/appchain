package ru.tolsi

import com.google.common.primitives.Longs
import scalikejdbc._
import scorex.crypto.encode.Base58
import scorex.crypto.signatures.{Curve25519, PublicKey, Signature}

package object token {

  case class Address(publicKey: String) {
    require(Base58.decode(publicKey).get.length == 32)
  }

  case class Transfer(from: Address, to: Address, amount: Long, signature: String) {
    require(Curve25519.verify(Signature(Base58.decode(signature).get), bytes, PublicKey(Base58.decode(from.publicKey).get)), "signature should be valid")

    def bytes: Array[Byte] = Base58.decode(from.publicKey).get ++ Base58.decode(to.publicKey).get ++ Longs.toByteArray(amount)
  }

  case class TokenBalance(address: String, balance: Long)

  object TokenBalance extends SQLSyntaxSupport[TokenBalance] {
    val tb = syntax

    def apply(tb: ResultName[TokenBalance])(rs: WrappedResultSet) =
      new TokenBalance(rs.string(tb.address), rs.long(tb.balance))
  }

}
