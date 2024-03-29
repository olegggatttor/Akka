package external

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

import org.mockserver.client.MockServerClient
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.{request => mockRequest}
import org.mockserver.model.HttpResponse.{response => mockResponse}
import org.mockserver.model.{RegexBody, HttpRequest => MockRequest, HttpResponse => MockResponse}

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

object DSL {

  implicit def string2body(string: String): body = body(string)

  implicit def bytes2body(array: Array[Byte]): bytes = bytes(array)

  implicit def intToTimesOps(times: Int): TimesOps = TimesOps(times)

  case class TimesOps(exactlyTimes: Int) {
    def times: Times = Times.exactly(exactlyTimes)
  }

  sealed trait RequestModifier {
    def +(modifier: RequestModifier): RequestModifier = and(modifier)

    def and(modifier: RequestModifier): CompositeRequestModifier = CompositeRequestModifier(this, modifier)

    def apply(mockRequest: MockRequest): MockRequest
  }

  case class CompositeRequestModifier(requestModifiers: RequestModifier*) extends RequestModifier {
    override def apply(request: MockRequest): MockRequest =
      requestModifiers.foldLeft(request)((request, modifier) => modifier(request))
  }

  sealed trait ResponseModifier {
    def +(modifier: ResponseModifier): ResponseModifier = and(modifier)

    def and(modifier: ResponseModifier): CompositeResponseModifier = CompositeResponseModifier(this, modifier)

    def apply(mockResponse: MockResponse): MockResponse
  }

  object DoNothingResponseModifier extends ResponseModifier {
    override def apply(mockResponse: MockResponse): MockResponse = mockResponse
  }

  case class CompositeResponseModifier(responseModifiers: ResponseModifier*) extends ResponseModifier {
    override def apply(response: MockResponse): MockResponse =
      responseModifiers.foldLeft(response)((response, modifier) => modifier(response))
  }

  case class ExpectationBuilder(requestModifier: RequestModifier,
                                responseModifier: ResponseModifier = DoNothingResponseModifier)
                               (implicit mockServerClient: MockServerClient) {

    def has(additionalRequestModifier: RequestModifier): ExpectationBuilder =
      ExpectationBuilder(requestModifier and additionalRequestModifier, responseModifier)

    def after(duration: FiniteDuration): ExpectationBuilder =
      ExpectationBuilder(requestModifier, responseModifier + delay(duration))

    def respond(modifier: ResponseModifier): ExpectationSetter =
      new ExpectationSetter(this.copy(responseModifier = CompositeResponseModifier(this.responseModifier, modifier)))
  }

  class MethodModifier(method: String) extends RequestModifier {
    override def apply(request: MockRequest): MockRequest = request.withMethod(method)
  }

  class ExpectationSetter(val builder: ExpectationBuilder)(implicit mockServerClient: MockServerClient) {
    def apply(times: Times): Unit = mockServerClient
      .when(builder.requestModifier(mockRequest), times)
      .respond(builder.responseModifier(mockResponse))
    def always = apply(Times.unlimited())
    def once = apply(Times.once())
    def never = apply(Times.exactly(0))
    def exactly(times: Times) = apply(times)
  }

  case object GET extends MethodModifier("GET")

  case object HEAD extends MethodModifier("HEAD")

  case object POST extends MethodModifier("POST")

  case object PUT extends MethodModifier("PUT")

  case object DELETE extends MethodModifier("DELETE")

  case object TRACE extends MethodModifier("TRACE")

  case object CONNECT extends MethodModifier("CONNECT")

  case object OPTIONS extends MethodModifier("OPTIONS")

  case class path(str: String) extends RequestModifier {
    override def apply(request: MockRequest): MockRequest = request.withPath(str)
  }

  case class param(name: String, value: Any) extends RequestModifier {
    override def apply(request: MockRequest): MockRequest = request.withQueryStringParameter(name, value.toString)
  }

  case class header(name: String, value: Any) extends RequestModifier with ResponseModifier {
    override def apply(request: MockRequest): MockRequest =
      request.withHeader(name, value.toString)

    override def apply(response: MockResponse): MockResponse =
      response.withHeader(name, value.toString)
  }

  case class status(code: Int) extends ResponseModifier {
    override def apply(response: MockResponse): MockResponse = response.withStatusCode(code)
  }

  case class body(body: String, maybeCharset: Option[Charset] = None) extends RequestModifier with ResponseModifier {
    override def apply(request: MockRequest): MockRequest =
      if (maybeCharset.isDefined)
        request.withBody(body, maybeCharset.get)
      else
        request.withBody(body)

    override def apply(response: MockResponse): MockResponse =
      response.withBody(body)
  }

  case class regexBody(body: String) extends RequestModifier {
    override def apply(request: MockRequest): MockRequest = request.withBody(new RegexBody(body))
  }

  case class bytes(bytes: Array[Byte]) extends RequestModifier with ResponseModifier {
    override def apply(request: MockRequest): MockRequest =
      request.withBody(bytes)

    override def apply(response: MockResponse): MockResponse =
      response.withBody(bytes)
  }

  case class delay(duration: FiniteDuration) extends ResponseModifier {
    override def apply(response: MockResponse): MockResponse =
      response.withDelay(TimeUnit.MILLISECONDS, duration.toMillis)
  }

  val *** = ""

  def when(implicit client: MockServerClient) = new {

    def get(pathStr: String = ***): ExpectationBuilder = ExpectationBuilder(GET + path(pathStr))

    def head(pathStr: String = ***): ExpectationBuilder = ExpectationBuilder(HEAD + path(pathStr))

    def post(pathStr: String = ***): ExpectationBuilder = ExpectationBuilder(POST + path(pathStr))

    def put(pathStr: String = ***): ExpectationBuilder = ExpectationBuilder(PUT + path(pathStr))

    def delete(pathStr: String = ***): ExpectationBuilder = ExpectationBuilder(DELETE + path(pathStr))

    def trace(pathStr: String = ***): ExpectationBuilder = ExpectationBuilder(TRACE + path(pathStr))

    def connect(pathStr: String = ***): ExpectationBuilder = ExpectationBuilder(CONNECT + path(pathStr))

    def options(pathStr: String = ***): ExpectationBuilder = ExpectationBuilder(OPTIONS + path(pathStr))
  }

  def forAnyRequest(implicit client: MockServerClient) = new ExpectationBuilder(CompositeRequestModifier())

  object Headers {
    def Location(url: String) = header("Location", url)

    def Accept(contentTypes: String*) = header("Accept", contentTypes.mkString(","))

    def AcceptCharset(charsets: String*) = header("Accept-Charset", charsets.mkString(","))

    def AcceptEncoding(encodings: String*) = header("Accept-Encoding", encodings.mkString(","))

    def AcceptLanguage(languages: String*) = header("Accept-Language", languages.mkString(","))

    def Authorization(value: String) = header("Authorization", value)

    def CacheControl(value: String) = header("Cache-Control", value)

    def ContentType(value: String) = header("Content-Type", value)

    def Connection(value: String) = header("Connection", value)

    def Cookie(value: String) = header("Cookie", value)
  }

  object Statuses {
    val Ok = status(200)
    val Created = status(201)
    val Accepted = status(202)
    val NonAuthoritativeInformation = status(203)
    val NoContent = status(204)
    val ResetContent = status(205)
    val PartialContent = status(206)

    val MultipleChoices = status(300)
    val MovedPermanently = status(301)
    val Found = status(302)
    val SeeOther = status(303)
    val NotModified = status(304)
    val UseProxy = status(305)
    val SwitchProxy = status(306)
    val TemporaryRedirect = status(307)
    val PermanentRedirect = status(308)
    val ResumeIncomplete = status(309)

    val BadRequest = status(400)
    val Unauthorized = status(401)
    val PaymentRequired = status(402)
    val Forbidden = status(403)
    val NotFound = status(404)
    val MethodNotAllowed = status(405)
    val NotAcceptable = status(406)
    val RequestTimeout = status(408)
    val Conflict = status(409)
    val Gone = status(410)
    val LengthRequired = status(411)
    val PreconditionFailed = status(412)
    val PayloadTooLarge = status(413)
    val RequestUriTooLong = status(414)
    val UnsupportedMediaType = status(415)

    val InternalServerError = status(500)
    val NotImplemented = status(501)
    val BadGateway = status(502)
    val ServiceUnavailable = status(503)
    val GatewayTimeout = status(504)
    val HttpVersionNotSupported = status(505)
    val UnknownError = status(520)
  }

}
