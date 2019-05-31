package examples.hybrid.api.http

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import examples.commons.SimpleBoxTransactionMemPool
import examples.hybrid.blocks.{HybridBlock, PosBlock, PowBlock}
import examples.hybrid.history.HybridHistory
import examples.hybrid.mining.PowMiner.ReceivableMessages.{StartMining, StopMining}
import examples.hybrid.state.HBoxStoredState
import examples.hybrid.wallet.HBoxWallet
import io.circe.syntax._
import scorex.core.bytesToId
import scorex.core.api.http.{ApiResponse, ApiRouteWithFullView}
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.{ScorexEncoding, ScorexLogging}

import scala.util.Try


case class DebugApiRoute(override val settings: RESTApiSettings, nodeViewHolderRef: ActorRef, miner: ActorRef)
                        (implicit val context: ActorRefFactory)
  extends ApiRouteWithFullView[HybridHistory, HBoxStoredState, HBoxWallet, SimpleBoxTransactionMemPool] 
    with ScorexEncoding {

  override val route: Route = (pathPrefix("debug") & withCors) {
    infoRoute ~ chain ~ delay ~ myblocks ~ generators ~ startMining ~ stopMining
  }

  def delay: Route = {
    (post & path("delay" / Segment / IntNumber)) { case (encodedSignature, count) =>
      withNodeView { view =>
        val result: Try[String] = for {
          id <- encoder.decode(encodedSignature)
          delay <- view.history.averageDelay(bytesToId(id), count)
        } yield delay.toString
        ApiResponse("delay" -> result.getOrElse("Undefined"))
      }
    }
  }

  def infoRoute: Route = (post & path("info")) {
    withNodeView { view =>
      val bestBlockJson = view.history.bestBlock match {
        case block: PosBlock => block.asJson
        case _ => view.history.bestBlock.asInstanceOf[PowBlock].asJson
      }

      ApiResponse(
        "height" -> view.history.height.toString.asJson,
        "bestPoS" -> encoder.encode(view.history.bestPosId).asJson,
        "bestPoW" -> encoder.encode(view.history.bestPowId).asJson,
        "bestBlock" -> bestBlockJson,
        "stateVersion" -> encoder.encode(view.state.version).asJson
      )
    }
  }

  def myblocks: Route = (post & path("myblocks")) {
    withNodeView { view =>
      val pubkeys = view.vault.publicKeys

      def isMyPosBlock(b: HybridBlock): Boolean = b match {
        case pos: PosBlock => pubkeys.exists(pk => java.util.Arrays.equals(pk.pubKeyBytes, pos.generatorBox.proposition.pubKeyBytes))
        case _ => false
      }

      def isMyPowBlock(b: HybridBlock): Boolean = b match {
        case pow: PowBlock => pubkeys.exists(pk => java.util.Arrays.equals(pk.pubKeyBytes, pow.generatorProposition.pubKeyBytes))
        case _ => false
      }

      val posCount = view.history.count(isMyPosBlock)
      val powCount = view.history.count(isMyPowBlock)

      ApiResponse(
        "pubkeys" -> pubkeys.map(pk => encoder.encode(pk.pubKeyBytes)).asJson,
        "count" -> (posCount + powCount).asJson,
        "posCount" -> posCount.asJson,
        "powCount" -> powCount.asJson
      )
    }
  }

  def generators: Route = (post & path("generators")) {
    withNodeView { view =>
      val map: Map[String, Int] = view.history.generatorDistribution()
        .map(d => encoder.encode(d._1.pubKeyBytes) -> d._2)
      ApiResponse(map.asJson)
    }
  }

  def chain: Route = (post & path("chain")) {
    withNodeView { view =>
      ApiResponse("history" -> view.history.toString)
    }
  }

  def startMining: Route = (post & path("startMining")) {
    withNodeView { view =>
      miner ! StartMining
      //wallet.scanPersistent(view.history.bestPosBlock) // find spenadable boxes in the last block
      ApiResponse(
        "response" -> "ok"
      )
    }
  }

  def stopMining: Route = (post & path("stopMining")) {
    withNodeView { view =>
      miner ! StopMining
      //wallet.scanPersistent(view.history.bestPosBlock) // find spenadable boxes in the last block
      ApiResponse(
        "response" -> "ok"
      )
    }
  }
}
