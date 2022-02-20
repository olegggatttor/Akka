package server

import external.DSL._
import org.mockserver.client.MockServerClient
import io.circe.syntax._

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

object StubServerBase {
  def getServerStub(searcher: String, delay: FiniteDuration)(implicit server : MockServerClient) {
    when get "/search" has {
      param("text", ***)
    } after delay respond {
      string2body(ServerUtils.serverResponseFor(searcher).asJson.noSpaces)
    } always
  }
}
