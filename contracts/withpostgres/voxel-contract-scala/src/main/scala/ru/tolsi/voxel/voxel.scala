package ru.tolsi

import scorex.crypto.encode.Base58
import scorex.crypto.signatures.{Curve25519, PublicKey, Signature}

package object voxel {

  case class Address(publicKey: String) {
    require(Base58.decode(publicKey).get.length == 32)
  }

  // todo replay attack fix
  case class Repay(counterParty: Address, signature: String) {
    require(Curve25519.verify(Signature(Base58.decode(signature).get), bytes, PublicKey(Base58.decode(counterParty.publicKey).get)), "signature should be valid")

    def bytes: Array[Byte] = "repay".getBytes
  }

}
