package actors

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.util.Timeout
import wrappers.{SearchEngine, SearchRequest}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class MasterActor(childTimeoutDuration: FiniteDuration) extends Actor {
  implicit val childTimeout: Timeout = Timeout(childTimeoutDuration)

  override def receive: Receive = {
    case request: SearchRequest =>
      val respondTo = sender
      val futures: Seq[Future[Seq[String]]] = request.searchers map {
        case SearchEngine(engineName: String, url: String) =>
          val child = context.actorOf(Props[ChildActor], engineName)
          (child ? (url, request.searchQuery)).mapTo[Seq[String]] recover { case _ => Seq() }
      }
      Future.sequence(futures) onComplete {
        case Success(value) =>
          respondTo ! value
        case Failure(exception) => throw exception
      }
  }
}
