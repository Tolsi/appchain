//package ru.tolsi.appchain
//
//import java.util.UUID
//
//import ru.tolsi.appchain.BlockChain._
//import scorex.crypto.encode.Base58
//import scorex.crypto.hash.Blake2b256
//import scorex.crypto.signatures.{Curve25519, PrivateKey, PublicKey, Signature}
//
//import scala.util.Try
//
//object Block {
//  def forge(parent: String, transactions: Seq[Transaction]): Block = {
//    Block(Some(parent), randomId, transactions)
//  }
//}
//
//case class Block(parent: Option[String], id: String, transactions: Seq[Transaction])
//
//trait Transaction {
//  def id: String = Base58.encode(Blake2b256.hash(bytes))
//  def bytes: Array[Byte]
//}
//
//object CreateAppTransaction {
//  def sign(appName: AppName, owner: Address, appDockerFile: DockerFile, pk: PrivateKey): CreateAppTransaction = {
//    val b = bytes(appName, owner, appDockerFile)
//    val signature = Curve25519.sign(pk, b)
//    CreateAppTransaction(appName, owner, appDockerFile, signature)
//  }
//
//  def bytes(appName: AppName, owner: Address, appDockerFile: DockerFile): Array[Byte] = appName.getBytes ++ owner.getBytes ++ appDockerFile.getBytes
//}
//
//case class CreateAppTransaction(appName: AppName, owner: Address, appDockerFile: DockerFile, signature: Signature) extends Transaction {
//  override def bytes: Array[Byte] = CreateAppTransaction.bytes(appName, owner, appDockerFile)
//}
//
//object CallTransaction {
//
//  def sign(appName: AppName, params: Params, pk: PrivateKey): CallTransaction = {
//    val b = bytes(appName, params)
//    val signature = Curve25519.sign(pk, b)
//    CallTransaction(appName, params, signature)
//  }
//
//  def bytes(appName: AppName, params: Params): Array[Byte] =
//    appName.getBytes ++ params.getBytes
//}
//
//case class CallTransaction(appName: AppName, params: Params, signature: Signature) extends Transaction {
//  override def bytes: Array[Byte] = CallTransaction.bytes(appName, params)
//}
//
//object BlockChain {
//  type AppName = String
//  type Id = String
//  type Address = String
//  type Params = String
//  type DockerFile = String
//
//  val genesis: Block = Block(None, randomId, Seq.empty)
//
//  val ministryOfRealEstate: (PrivateKey, PublicKey) = Curve25519.createKeyPair(Array[Byte](1, 6, 3, 9, 3))
//
//  def randomId: String = UUID.randomUUID().toString
//
//  def addressFromPublicKey(publicKey: PublicKey): String = Base58.encode(publicKey)
//
//  def valid(tx: Transaction, current: BlockChain): Boolean = {
//    tx match {
//      case CreateAppTransaction(appName, owner, appDockerFile, signature) =>
//        // todo
//        ???
//        val isValidMinistrySignature = Curve25519.verify(signature, CreateAppTransaction.bytes(appName, owner, appDockerFile), ministryOfRealEstate._2)
//        isValidMinistrySignature
//      case CallTransaction(appName, params, signature) =>
//        ???
//
//    }
//  }
//
//  def valid(current: BlockChain, block: Block): Boolean = {
//    val isTransactionsValid = block.transactions.forall(tx => valid(tx, current))
//    val isLinkToLastBlock = current.lastOption.exists(_.id == block.parent.get)
//    val isNeedGenesisBlock = current.isEmpty && block.parent.isEmpty
//    val isIdValid = Try {
//      UUID.fromString(block.id)
//    }.isSuccess
//
//    isTransactionsValid && (isLinkToLastBlock || isNeedGenesisBlock) && isIdValid
//  }
//}
//
//case class BlockChain(private val blocks: Seq[Block] = Seq.empty, owners: Map[RealtyAddress, Address] = Map.empty) extends Iterable[Block] {
//
//  def append(b: Block): Either[String, BlockChain] = {
//    if (valid(this, b)) {
//      Right(BlockChain(blocks :+ b, owners ++ updatedOwners(b)))
//    } else {
//      Left(s"Block '${b.id}' is invalid")
//    }
//  }
//
//  override def iterator: Iterator[Block] = blocks.iterator
//}
//
//object Appchain extends App {
//  val (vasyaPrivate, vasyaPublic) = Curve25519.createKeyPair(Array[Byte](2, 6, 4, 9, 1))
//  val (petyaPrivate, petyaPublic) = Curve25519.createKeyPair(Array[Byte](1, 6, 4, 2, 0, 14))
//
//  val blockChain = BlockChain()
//
//  val blockChainWithGenesis = blockChain.append(genesis).right.get
//
//  val block = Block.forge(genesis.id, Seq(create1))
//
//  val blockchainWithSomeRealty = blockChainWithGenesis.append(block).right.get
//
//  val realtyHash = realtyAddressHash(realtyAddress)
//
//  val vasyaToPetyaTransaction = CallTransaction.sign(randomId, addressFromPublicKey(petyaPublic), realtyHash, ministryOfRealEstate._1)
//
//  val blockchainWithRealtyTransfer = blockchainWithSomeRealty.append(Block.forge(blockchainWithSomeRealty.last.id, Seq(vasyaToPetyaTransaction)))
//
//  println(blockchainWithRealtyTransfer)
//}