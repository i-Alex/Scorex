package examples.hybrid

import akka.actor.Status.Success
import akka.actor.{ActorRef, ActorSystem, Props}
import examples.commons._
import examples.hybrid.blocks._
import examples.hybrid.history.{HybridHistory, HybridSyncInfo}
import examples.hybrid.mining.{HybridMiningSettings, HybridSettings}
import examples.hybrid.state.HBoxStoredState
import examples.hybrid.wallet.HBoxWallet
import io.iohk.iodb.ByteArrayWrapper
import scorex.core.serialization.Serializer
import scorex.core.settings.ScorexSettings
import scorex.core.transaction.Transaction
import scorex.core.transaction.box.proposition.PublicKey25519Proposition
import scorex.core.transaction.state.{BoxStateChanges, PrivateKey25519Companion}
import scorex.core.utils.{NetworkTimeProvider, ScorexEncoding, ScorexLogging}
import scorex.core.{ModifierTypeId, NodeViewHolder, NodeViewModifier}
import scorex.crypto.encode.Base58
import scorex.crypto.signatures.PublicKey

import scala.util.{Failure, Try}


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

  override protected def validate(pmod: HybridBlock): Boolean = {
    minimalState().validate(pmod)
    val cs = minimalState().changes(pmod).get
    val boxIdsToRemove = cs.toRemove.map(_.boxId).map(ByteArrayWrapper.apply)
    val boxesToAdd = cs.toAppend.map(_.box).map(b => ByteArrayWrapper(b.id) -> ByteArrayWrapper(b.bytes))

    if (boxIdsToRemove.distinct.size != boxIdsToRemove.size || boxesToAdd.distinct.size != boxesToAdd.size) {
      return false
    }
    return true
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
      "7tqnFLUdy1DehQNTR1gddcnhD9Ye79a9vcL6eLMuVrpn", // minerNode2 addresses
      "DSgdWDVRNLGHfpnn951Q9FcRjGsdf71tFbJJsfbGXKFm",
      "2j6R11wd7ibgtVKhsnap85hn1zKEdYjBGqzpaePrkNzN",
      "2vxnvA1GUydJapjkTFGgKZZh8K5sqdM5jRLkjBKE7hcX",
      "3nRKeiSkd1k9P1aWfK9WMPZw9zKGGoqfSYiDcc3R77jQ",
      "2dAvNM1Yh1G8jF4cXR1HgxpmCCW6AK9Xt5oZ1ea9pH2w",
      "6vACC474SDrDgLUP65JCcMYj9FFhvUWNTyWR42r2uKc5",
      "EWFXd8LTn48tpKk4kjnRnugcPtsYQ4AZb43U2YRBSaY1",
      "7m3oAaeyZHg9aeWSVDe3HNrvqnmx5UJnDRH6FGT4xmYZ",
      "7QM8VfQM4PicDdbUJFPKo8SmrBgJXw5csC9SP6mQpMfD",
      "BCM5NqypJGSuYyMiAYCJpGpSw7RoJMn36gdKzgygoMMC",
      "5MKQRRbUtuhH4Ujc7sC6RsSvqZQaoozf49Q8Dus97fo2",
      "EfusD22uv8Xp56skxRgaqYFH8ZFXMWGviicQxHyNyD5X",
      "3nqREHALo8pgAAQayhX5xrXgcw1a3XQQfKGkdSAjCCez",
      "DwrJEwxFhrC8aLNy6wCFnN4rCtKjzasPacQ8rGBQH1kL",
      "ErowFEwA7U9yHpRUA5DCnhonmSyQNuhX9JD2EP3FMLLv",
      "4CtK9JLVaeeNr1yW5C5pq6tYFbZqJJTmJCxQHWizEaJx",
      "3c8mYQHcqMLEiZZeTyyg7hVcd1ELSWo7a9VyHdgv4Vdy",
      "DRE9XHdz5nWqcmWWfRtrMUjBkuaPU5LTGzkPAXNgTdHJ",
      "Ha5HQTAizctz7smFvVtd9UKQTVmFiVsHXAjwM9REQNgv",
      "CFqxy9746MNrGvhXeC6UZDF3TQhQ5K3k7Mrcsqg2GU87", // minerNode3 addresses
      "2Vh59rku78vJ4uNGeqYBdBsRcaT4mAe1bKGJFCBKkNFs",
      "EvLrqJVuT3Kewtzh9X7CyrwUQF9nk5DBtD2M1u72dC5V",
      "8bCi5kH7tURpWiMCzk3cR7oHB31swghbgdRuGMJfeVgp",
      "2PuA1UZ2ytAvCMe5nPjtFLWoPswLmrjguqWgWSkhEyLc",
      "nckWWM2ApQE1or4e8GUoTj6ypozT326tGgGwJb8aPmA",
      "Dxmc8kL3aMm4ekHKWyNCtjtCKbeYo18h9vterPwRj9wV",
      "HxgL14n9Kc9Z4y7TYDGgkfjqh61UyGuXnyP9qaB26rn3",
      "2pWNrYacCxBASsRMmDP9mLPr6nS5w8AkxGVD9r2tJwme",
      "5K6gVp6oo9pWU1mnEgCAeJpkndEECU4P7WeDeyp36RwJ"
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
