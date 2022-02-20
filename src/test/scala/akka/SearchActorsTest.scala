package akka

import actors.MasterActor
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import org.mockserver.integration.ClientAndServer
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.HttpRequest.request
import org.scalatest.BeforeAndAfter
import org.scalatest.funspec.AnyFunSpec
import server.{ServerUtils, StubServerBase}
import wrappers.{SearchEngine, SearchRequest}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SearchActorsTest extends AnyFunSpec with BeforeAndAfter {
  private val GOOGLE = "Google"
  private val YANDEX = "Yandex"
  private val BING = "Bing"

  private val TEST_QUERY = "test"

  private val serverYandex: ClientAndServer = startClientAndServer(8887)
  private val serverGoogle: ClientAndServer = startClientAndServer(8888)
  private val serverBing: ClientAndServer = startClientAndServer(8889)

  implicit val responseTimeout: Timeout = Timeout(60.seconds)

  def setupServersAndRequest(query: String, durations: (Int, Int, Int)): SearchRequest = {
    serverYandex.clear {
      request.withPath("/search").withMethod("GET")
    }
    serverGoogle.clear {
      request.withPath("/search").withMethod("GET")
    }
    serverBing.clear {
      request.withPath("/search").withMethod("GET")
    }
    StubServerBase.getServerStub(YANDEX, durations._1.seconds)(serverYandex)
    StubServerBase.getServerStub(GOOGLE, durations._2.seconds)(serverGoogle)
    StubServerBase.getServerStub(BING, durations._3.seconds)(serverBing)

    val engines = Seq(
      SearchEngine(YANDEX, "http://localhost:8887/search"),
      SearchEngine(GOOGLE, "http://localhost:8888/search"),
      SearchEngine(BING, "http://localhost:8889/search")
    )
    SearchRequest(query, engines)
  }

  describe("Requests") {
    it("should return all answers normally") {
      val request = setupServersAndRequest(TEST_QUERY, (1, 1, 1))

      val system = ActorSystem.create("TestSystem")
      val masterActor = system.actorOf(Props(new MasterActor(60.seconds)), "master")
      val future = (masterActor ? request).mapTo[Seq[Seq[String]]]
      val result: Seq[Seq[String]] = Await.result(future, 5.seconds)

      assert(result.size == 3)
      assert(result contains ServerUtils.serverResponseFor(YANDEX))
      assert(result contains ServerUtils.serverResponseFor(GOOGLE))
      assert(result contains ServerUtils.serverResponseFor(BING))
    }
    it("should return empty Seq for server on delay error") {
      val request = setupServersAndRequest(TEST_QUERY, (1, 100, 1))

      val system = ActorSystem.create("TestSystem")
      val masterActor = system.actorOf(Props(new MasterActor(5.seconds)), "master")
      val future = (masterActor ? request).mapTo[Seq[Seq[String]]]
      val result: Seq[Seq[String]] = Await.result(future, 10.seconds)

      assert(result.size == 3)
      assert(result contains ServerUtils.serverResponseFor(YANDEX))
      assert(!(result contains ServerUtils.serverResponseFor(GOOGLE)))
      assert(result contains ServerUtils.serverResponseFor(BING))
    }
    it("should return empty Seq for server on delay error for any amount of servers") {
      val request = setupServersAndRequest(TEST_QUERY, (100, 100, 100))

      val system = ActorSystem.create("TestSystem")
      val masterActor = system.actorOf(Props(new MasterActor(5.seconds)), "master")
      val future = (masterActor ? request).mapTo[Seq[Seq[String]]]
      val result: Seq[Seq[String]] = Await.result(future, 10.seconds)

      assert(result.size == 3)
      assert(!(result contains ServerUtils.serverResponseFor(YANDEX)))
      assert(!(result contains ServerUtils.serverResponseFor(GOOGLE)))
      assert(!(result contains ServerUtils.serverResponseFor(BING)))
    }
  }
}
