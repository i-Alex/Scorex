package examples.hybrid

import akka.actor.{ActorRef, ActorSystem, Props}
import examples.commons._
import examples.hybrid.blocks._
import examples.hybrid.history.{HybridHistory, HybridSyncInfo}
import examples.hybrid.mining.{HybridMiningSettings, HybridSettings}
import examples.hybrid.state.HBoxStoredState
import examples.hybrid.wallet.HBoxWallet
import scorex.core.serialization.Serializer
import scorex.core.settings.ScorexSettings
import scorex.core.transaction.Transaction
import scorex.core.transaction.box.proposition.PublicKey25519Proposition
import scorex.core.transaction.state.PrivateKey25519Companion
import scorex.core.utils.{NetworkTimeProvider, ScorexEncoding, ScorexLogging}
import scorex.core.{ModifierTypeId, NodeViewHolder, NodeViewModifier}
import scorex.crypto.encode.Base58
import scorex.crypto.signatures.PublicKey


class HybridNodeViewHolder(hybridSettings: HybridSettings,
                           timeProvider: NetworkTimeProvider)
  extends NodeViewHolder[SimpleBoxTransaction, HybridBlock] {

  override type SI = HybridSyncInfo
  override type HIS = HybridHistory
  override type MS = HBoxStoredState
  override type VL = HBoxWallet
  override type MP = SimpleBoxTransactionMemPool

  override lazy val scorexSettings: ScorexSettings = hybridSettings.scorexSettings
  private lazy val minerSettings: HybridMiningSettings = hybridSettings.mining

  override val modifierSerializers: Map[ModifierTypeId, Serializer[_ <: NodeViewModifier]] =
    Map(PosBlock.ModifierTypeId -> PosBlockCompanion,
      PowBlock.ModifierTypeId -> PowBlockCompanion,
      Transaction.ModifierTypeId -> SimpleBoxTransactionCompanion)

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    super.preRestart(reason, message)
    log.error("HybridNodeViewHolder has been restarted, not a valid situation!")
    reason.printStackTrace()
    System.exit(100) // this actor shouldn't be restarted at all so kill the whole app if that happened
  }

  /**
    * Hard-coded initial view all the honest nodes in a network are making progress from.
    */
  override protected def genesisState: (HIS, MS, VL, MP) =
    HybridNodeViewHolder.generateGenesisState(hybridSettings, timeProvider)

  /**
    * Restore a local view during a node startup. If no any stored view found
    * (e.g. if it is a first launch of a node) None is to be returned
    */
  override def restoreState(): Option[(HIS, MS, VL, MP)] = {
    if (HBoxWallet.exists(hybridSettings.walletSettings)) {
      Some((
        HybridHistory.readOrGenerate(scorexSettings, minerSettings, timeProvider),
        HBoxStoredState.readOrGenerate(scorexSettings),
        HBoxWallet.readOrGenerate(hybridSettings.walletSettings, 1),
        SimpleBoxTransactionMemPool.emptyPool))
    } else None
  }
}

object HybridNodeViewHolder extends ScorexLogging with ScorexEncoding {
  def generateGenesisState(hybridSettings: HybridSettings,
                           timeProvider: NetworkTimeProvider):
  (HybridHistory, HBoxStoredState, HBoxWallet, SimpleBoxTransactionMemPool) = {
    val settings: ScorexSettings = hybridSettings.scorexSettings
    val minerSettings: HybridMiningSettings = hybridSettings.mining

    val GenesisAccountsNum = 50
    val GenesisBalance = Value @@ 100000000L

    //propositions with wallet seeds minerNode1 (20accs), minerNode2 (20 accs), minerNode3 (10 accs)
    val icoMembers: IndexedSeq[PublicKey25519Proposition] = IndexedSeq(
      "DF41VhWJKvDNdiXC8NVBxENLKSQg5g3rQATpsW5W4jdF", // minerNode1 addresses
      "2J3WtXxtbWr2x2ZsUdDhVa6f3U29kYyfWk9R3f2rmCHc",
      "GNsaiXHmB3vejoK15ErxHjgouABAUfquBy7KRaUzciCL",
      "H1eX8a7bk7rz6MyA2X1zugQWxmU6kNJ71YDZp9ga8BuJ",
      "Ey7TKrPdsTa5L9Cpszvp91jheSRe7kTe1N2o6dAf6Pan",
      "DwhZXNip6ZyZVKkbpEhUmwd12o9m48K23vu7trK11dzW",
      "5Gd3pnPpL7S4aaf1kAvKfh5frfXd9Tg56rVW6t12eEx1",
      "EiSkJHeCuNxJgbDKrQRfGBHZScBYx845qqZQWwprCGJf",
      "7quTgekLQvQ8fgRXJz9XhHyCRMcm9uDo8yYXEWdVjBVQ",
      "5KqWAG2PyPcpZRrsSJ1cn4LuewLr9HTWzH4fkgWhiaLm",
      "26w7eWv3rmbssMCPY28SkAhzXfb7Zf45DCSsQzuZhvVh",
      "Dxwk3pFEA22C85DGSHVmeGfrraqiPwgRRmALYroj9bEE",
      "2wPysgNYgwW3tcCE7E2zFMYkEAgLpi8jzoQg3NchV3rN",
      "EKjyaQ34BTZeytD2LSJJtQCdUCKR8VCEm5z2Z6RYdDtW",
      "Feia9W6TB7yDnKtimnYGcsxA6fYeamsCfsdgCTpNhG6j",
      "Ge6xpFipv2696BRqrw559mnn3H76GJ7Dh4rnpv8AAmNf",
      "4Cn16mSP6DeaRu9WgNGud9CpxaUkcysEb81exuoSrjJi",
      "6dEFJTtmdD5RENp8RBF7gpNenENcWkV52yMiJPRW6cDQ",
      "92FkXhPmfbMqwY7Q1JWmtMfBez4J1iCYbGzChNHXYc2r",
      "7Lux1raYnLWvQnKZoFvwcF2fPY3uNXiZwrvqWtqCcWJV",
      "GQz8mafKfC8Scb11ppCagiPGAHVSzDd3DQhZgsrzHKq8",
      "GBa7NdFDQYjkEsjn4xJvgYBZdwrN6Ds6FHMzcMhgAqFw",
      "Eozk3S7aTZStqAEmN8pLYAcSNpgNtUBBHykeNPqcKbwE",
      "26AZ94vmuVMiruQbxpaArtooeByf4mg7YERm7ASPLtzX",
      "4FLYR7RY2VPwxrk11naQeE2kuHe2sm6gttxFYzMAsipU",
      "B3HzLmPcDribF2csqSvdteTVcQsNkmxCKNFR3xLZ3Rqu",
      "2YE8p31Fr7KfgQTSWdCWK7C1wk4Y3Yb3gzvecHfjGQCS",
      "6haBGvcBz8ZrBza5BBWAGtVghKoSDunp1JXyFjhRL9Pg",
      "8Na86fSM2Cv5LvoezW5aL8h2qaP76Cs7EXVRjPZvY2dG",
      "5TSGDgKxXQmBL7K1UjXJJijA9uEZWYku7hQZjA4YchmJ",
      "6KDfFLDnSxTBQ58NvBWqeXLTTJtbALrw2uNDW2haGkTs",
      "G8vHzNUhbs8LH12p27dexZnXhYXcHa2F5rybLDyRC59y",
      "BjwrFU2FyBBB7x2vn3d5r3P9THG7kJi37A1VcJZj9ngy",
      "BXs7geU54HqMCicgzsuWNeF2CgD7DfQWg2KyJSuu35cj",
      "8r11HX4Ap8V9JsUVD7fivyzLzZ14DG9fSHhXDb2pgoeo",
      "FKroztkLwNbqibtwP6g5GYECuVRoVShT2GyuaATYYWeZ",
      "FUsLAekPGpPPQvvksV1VphYzPJgaEsbwEiBxEh4U9T6p",
      "7FkG9kkU66XQtPJuzgyAEB4Lcw4b78PZHfXwNbFgvohA",
      "ASpaQgkEP49UHUR8hAMrruiG4HpGo6WybvJ88njD5L7B",
      "FRRXWdY6br8kcTWk4VWnwiL7kAsgNvAbRyoXxdAkFqZt",
      "5YgmHSQ9AZpniZ9DMfTkZSfM3A1BJsXKqCAtCSr3Ybkq",
      "7vV4aqkg1YY5VnM34oJ7BRMXZyvULGPva6Tesmng9XvH",
      "45VGbsfFeiXkW2uoC7tDRVUSHjnYhtpfrYN57wTANHsn",
      "8QwTmye6VsHx3fkAvmJqvSgHPjdPCaT3wakEfpujsWM5",
      "6nUtKXw7WFgV2tRuFyYwBrg4kBMYzADekPqLTwnUccxV",
      "3Kw5jix8XMWj2SHsxt7c1w9iiK3s6qc4NMyY6bDUXvTg",
      "EVjrmbKvTkVk7JRzDEaHBL2tpcdAtHgyNhDyPXGcAXLv",
      "GXkCiK2P7khngAtfhG8TSqm4nfPbpMDNFBiG8CF41ZtP",
      "8etCeR343fg5gktxMh5j64zofFvWuyNTwmHAzWbsptoC",
      "AnwYrjV3yb9NuYWz31C758TZGTUCLD7zZdSYubbewygt"
    ).map(s => PublicKey25519Proposition(PublicKey @@ Base58.decode(s).get))
      .ensuring(_.length == GenesisAccountsNum)

    val genesisAccount = PrivateKey25519Companion.generateKeys("genesis".getBytes)
    val genesisAccountPriv = genesisAccount._1
    val powGenesis = PowBlock(minerSettings.GenesisParentId, minerSettings.GenesisParentId, 1481110008516L, 38,
      0, Array.fill(32)(0: Byte), genesisAccount._2, Seq())


    val genesisTxs = Seq(SimpleBoxTransaction(
      IndexedSeq(genesisAccountPriv -> Nonce @@ 0L),
      icoMembers.map(_ -> GenesisBalance),
      0L,
      0L))


    log.debug(s"Initialize state with transaction ${genesisTxs.headOption} with boxes ${genesisTxs.headOption.map(_.newBoxes)}")

    val genesisBox = PublicKey25519NoncedBox(genesisAccountPriv.publicImage, Nonce @@ 0L, GenesisBalance)
    val attachment = "genesis attachment".getBytes
    val posGenesis = PosBlock.create(powGenesis.id, 0, genesisTxs, genesisBox, attachment, genesisAccountPriv)

    var history = HybridHistory.readOrGenerate(settings, minerSettings, timeProvider)
    history = history.append(powGenesis).get._1
    history = history.append(posGenesis).get._1

    val gs = HBoxStoredState.genesisState(settings, Seq[HybridBlock](posGenesis, powGenesis))
    val gw = HBoxWallet.genesisWallet(hybridSettings.walletSettings, Seq[HybridBlock](posGenesis, powGenesis))
      .ensuring(_.boxes().map(_.box.value.toLong).sum >= GenesisBalance ||
        !encoder.encode(hybridSettings.walletSettings.seed).startsWith("genesis"))
      .ensuring(_.boxes().forall(b => gs.closedBox(b.box.id).isDefined))

    (history, gs, gw, SimpleBoxTransactionMemPool.emptyPool)
  }
}

object HybridNodeViewHolderRef {
  def props(settings: HybridSettings,
            timeProvider: NetworkTimeProvider): Props =
    Props(new HybridNodeViewHolder(settings, timeProvider))

  def apply(settings: HybridSettings,
            timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider))

  def apply(name: String,
            settings: HybridSettings,
            timeProvider: NetworkTimeProvider)
           (implicit system: ActorSystem): ActorRef =
    system.actorOf(props(settings, timeProvider), name)
}
