import actors.MasterActor
import org.mockserver.integration.ClientAndServer
import server.StubServerBase
import org.mockserver.integration.ClientAndServer.startClientAndServer

import scala.concurrent.duration.DurationInt
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import wrappers.{SearchEngine, SearchRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success

object Main {
  def main(args : Array[String]): Unit = {
    val serverYandex: ClientAndServer = startClientAndServer(8887)
    StubServerBase.getServerStub("Yandex", 10.seconds)(serverYandex)
    val serverGoogle: ClientAndServer = startClientAndServer(8888)
    StubServerBase.getServerStub("Google", 3.seconds)(serverGoogle)
    val serverBing: ClientAndServer = startClientAndServer(8889)
    StubServerBase.getServerStub("Bing", 1.seconds)(serverBing)

    val system = ActorSystem.create("SearchSystem")
    val masterActor = system.actorOf(Props(new MasterActor(2.seconds)), "master")

    implicit val responseTimeout: Timeout = Timeout(60.seconds)
    val engines = Seq(
      SearchEngine("Yandex", "http://localhost:8887/search"),
      SearchEngine("Google", "http://localhost:8888/search"),
      SearchEngine("Bing", "http://localhost:8889/search")
    )
    val request = SearchRequest("пироги", engines)
    val future = (masterActor ? request).mapTo[Seq[Seq[String]]]

    future onComplete {
      case Success(value) =>
        println(value)

        system.terminate()
        serverYandex.stop()
        serverGoogle.stop()
        serverBing.stop()
      case _ =>
        println("Some failure occurred.")

        system.terminate()
        serverYandex.stop()
        serverGoogle.stop()
        serverBing.stop()
    }
  }
}
