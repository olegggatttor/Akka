package actors

import akka.actor.Actor
import scalaj.http.Http
import io.circe.Decoder
import io.circe.parser.decode

class ChildActor extends Actor{
  override def receive: Receive = {
    case (url: String, query: String) =>
      val response = Http(url).param("text", query).asString
      val decoder = Decoder[Seq[String]]
      val decoded =  decode(response.body)(decoder)
      decoded match {
        case Right(searchResponse) =>
          sender ! searchResponse
        case Left(error) => throw error
      }
  }
}
