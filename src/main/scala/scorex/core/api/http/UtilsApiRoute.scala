package scorex.core.api.http

import java.security.SecureRandom

import akka.actor.ActorRefFactory
import akka.http.scaladsl.server.Route
import io.circe.Json
import io.circe.syntax._
import scorex.core.settings.RESTApiSettings
import scorex.core.utils.ScorexLogging
import scorex.crypto.hash.Blake2b256


case class UtilsApiRoute(override val settings: RESTApiSettings)(implicit val context: ActorRefFactory)
  extends ApiRoute with ScorexLogging {

  private val SeedSize = 32

  private def seed(length: Int): String = {
    val seed = new Array[Byte](length)
    new SecureRandom().nextBytes(seed) //seed mutated here!
    encoder.encode(seed)
  }

  override val route: Route = pathPrefix("utils") {
    seedRoute ~ length ~ hashBlake2b
  }

  def seedRoute: Route = (get & path("seed")) {
    ApiResponse(seed(SeedSize).asJson)
  }

  def length: Route = (get & path("seed" / IntNumber)) { length =>
    ApiResponse(seed(length).asJson)
  }

  def hashBlake2b: Route = {
    (post & path("hash" / "blake2b") & entity(as[Json])) { json =>
      json.asString match {
        case Some(message) => ApiResponse(encoder.encode(Blake2b256(message)).asJson)
        case None => ApiError.BadRequest
      }
    }
  }
}
