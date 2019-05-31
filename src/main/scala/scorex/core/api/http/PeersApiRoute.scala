package scorex.core.api.http

import java.net.{InetAddress, InetSocketAddress}

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import io.circe.generic.semiauto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import scorex.core.api.http.PeersApiRoute.{BlacklistedPeers, PeerInfoResponse}
import scorex.core.network.Handshake
import scorex.core.network.NetworkController.ReceivableMessages.ConnectTo
import scorex.core.network.peer.PeerInfo
import scorex.core.network.peer.PeerManager.ReceivableMessages.{GetAllPeers, GetBlacklistedPeers, GetConnectedPeers}
import scorex.core.settings.RESTApiSettings

import scala.concurrent.ExecutionContext

case class PeersApiRoute(peerManager: ActorRef,
                         networkController: ActorRef,
                         override val settings: RESTApiSettings)
                        (implicit val context: ActorRefFactory, val ec: ExecutionContext) extends ApiRoute {

  override lazy val route: Route = pathPrefix("peers") { allPeers ~ connectedPeers ~ blacklistedPeers ~ connect }

  def allPeers: Route = (path("all") & post) {
    val result = askActor[Map[InetSocketAddress, PeerInfo]](peerManager, GetAllPeers).map {
      _.map { case (address, peerInfo) =>
        PeerInfoResponse.fromAddressAndInfo(address, peerInfo)
      }
    }
    ApiResponse(result)
  }

  def connectedPeers: Route = (path("connected") & post) {
    val now = System.currentTimeMillis()
    val result = askActor[Seq[Handshake]](peerManager, GetConnectedPeers).map {
      _.map { handshake =>
        PeerInfoResponse(
          address = handshake.declaredAddress.map(_.toString).getOrElse(""),
          lastSeen = now,
          name = Some(handshake.nodeName),
          connectionType = None)
      }
    }
    ApiResponse(result)
  }

  private val addressAndPortRegexp = "([\\w\\.]+):(\\d{1,5})".r

  def connect: Route = (path("connect") & post & withAuth & entity(as[Json])) { json =>
    val maybeAddress = json.asString.flatMap(addressAndPortRegexp.findFirstMatchIn)
    maybeAddress match {
      case None => ApiError.BadRequest
      case Some(addressAndPort) =>
        val host = InetAddress.getByName(addressAndPort.group(1))
        val port = addressAndPort.group(2).toInt
        networkController ! ConnectTo(new InetSocketAddress(host, port))
        ApiResponse.OK
    }
  }

  def blacklistedPeers: Route = (path("blacklisted") & post) {
    val result = askActor[Seq[String]](peerManager, GetBlacklistedPeers).map(BlacklistedPeers(_).asJson)
    ApiResponse(result)
  }
}

object PeersApiRoute {

  case class PeerInfoResponse(address: String,
                              lastSeen: Long,
                              name: Option[String],
                              connectionType: Option[String])

  object PeerInfoResponse {

    def fromAddressAndInfo(address: InetSocketAddress, peerInfo: PeerInfo): PeerInfoResponse = PeerInfoResponse(
      address.toString,
      peerInfo.lastSeen,
      peerInfo.nodeName,
      peerInfo.connectionType.map(_.toString)
    )
  }

  case class BlacklistedPeers(addresses: Seq[String])

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  implicit val encodePeerInfoResponse: Encoder[PeerInfoResponse] = deriveEncoder

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  implicit val encodeBlackListedPeers: Encoder[BlacklistedPeers] = deriveEncoder

}

