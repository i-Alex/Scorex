package examples.commons

import io.iohk.iodb.ByteArrayWrapper
import scorex.core.ModifierId
import scorex.core.transaction.MemoryPool
import scorex.core.utils.ScorexLogging

import scala.collection.concurrent.TrieMap
import scala.util.{Success, Try, Failure}


case class SimpleBoxTransactionMemPool(unconfirmed: TrieMap[ModifierId, SimpleBoxTransaction])
  extends MemoryPool[SimpleBoxTransaction, SimpleBoxTransactionMemPool] with ScorexLogging {
  override type NVCT = SimpleBoxTransactionMemPool

  //getters
  override def getById(id: ModifierId): Option[SimpleBoxTransaction] =
  unconfirmed.get(id)

  override def contains(id: ModifierId): Boolean = unconfirmed.contains(id)

  override def getAll(ids: Seq[ModifierId]): Seq[SimpleBoxTransaction] = ids.flatMap(getById)

  //modifiers
  override def put(tx: SimpleBoxTransaction): Try[SimpleBoxTransactionMemPool] = {
    var isOk: Boolean = true
    unconfirmed.foreach(utx => {
      if (utx._2.boxIdsToOpen.map(ByteArrayWrapper.apply).intersect(tx.boxIdsToOpen.map(ByteArrayWrapper.apply)).size > 0) {
        isOk = false
      }
    })
    if (isOk) {
      unconfirmed.put(tx.id, tx)
      Success(this)
    }
    else {
      log.info(s"Transaction contains ${tx.id} boxes to remove, that are already in mempool.")
      Failure(new Exception(s"Transaction contains ${tx.id} boxes to remove, that are already in mempool."))
    }
  }

  //todo
  override def put(txs: Iterable[SimpleBoxTransaction]): Try[SimpleBoxTransactionMemPool] = {
    txs.foreach(tx => put(tx))
    Success(this)
  }

  override def putWithoutCheck(txs: Iterable[SimpleBoxTransaction]): SimpleBoxTransactionMemPool = {
    txs.foreach(tx => unconfirmed.put(tx.id, tx))
    this
  }

  override def remove(tx: SimpleBoxTransaction): SimpleBoxTransactionMemPool = {
    unconfirmed.remove(tx.id)
    this
  }

  override def take(limit: Int): Iterable[SimpleBoxTransaction] =
    unconfirmed.values.toSeq.sortBy(-_.fee).take(limit)

  override def filter(condition: (SimpleBoxTransaction) => Boolean): SimpleBoxTransactionMemPool = {
    unconfirmed.retain { (k, v) =>
      condition(v)
    }
    this
  }

  override def size: Int = unconfirmed.size
}


object SimpleBoxTransactionMemPool {
  lazy val emptyPool: SimpleBoxTransactionMemPool = SimpleBoxTransactionMemPool(TrieMap())
}