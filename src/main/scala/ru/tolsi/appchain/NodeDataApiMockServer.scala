package ru.tolsi.appchain

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future

class NodeDataApiMockServer extends SprayJsonSupport with StrictLogging {
  implicit val system = ActorSystem("node-data-api-mock-server")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  import DataEntry._

  @volatile
  var state = MockKeyValueState()

  def updateState(state: MockKeyValueState): Unit = {
    logger.debug(s"Updated to $state")
    this.state = state
  }

  private val route: Route =
    pathPrefix("addresses") {
      pathPrefix("data") {
        pathPrefix(Segment) { address =>
          pathEnd {
            get {
              complete {
                state.data.getOrElse(address, Seq.empty[DataEntry[_]]).map(_.asJson)
              }
            }
          } ~ path(Segment) { key =>
            complete {
              val v = state.data.getOrElse(address, Seq.empty[DataEntry[_]]).find(_.key == key)
              logger.debug(s"return to $address [$key] = ${v.map(_.asJson)}")
              v.map(_.asJson) match {
                case Some(v) => v
                case None => StatusCodes.NotFound
              }
            }
          }
        }
      }
    }

  def start(): Future[Http.ServerBinding] = {
    Http().bindAndHandle(route, "0.0.0.0", 6000)
  }

  def stop(): Future[Unit] = {
    system.terminate().map(_ => ())
  }
}