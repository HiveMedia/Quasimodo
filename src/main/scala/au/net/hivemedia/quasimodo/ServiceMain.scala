package au.net.hivemedia.quasimodo

import java.io.File

import akka.actor._
import akka.pattern._
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * The main entry point of the service
  *
  * @author Liam Haworth
  */
object ServiceMain extends LazyLogging {

  /**
    * Represents the config loaded and used to configure the service
    */
  var config: Config = _

  /**
    * Represents the Actor System used by the service to run its actors in
    */
  implicit var actorSystem: ActorSystem = _

  /**
    * Default timeout for actor asks
    */
  implicit val timeout: Timeout = Timeout(60, SECONDS)

  /**
    * The actor that supervises the other actors running in this service
    */
  var supervisor: ActorRef = _

  /**
    * The actor running as the discovery actor
    */
  var discoveryActor: ActorRef = _

  /**
    * Main entry point into the service when first started
    *
    * @param args Arguments passed in during start
    */
  def main(args: Array[String]): Unit = {
    logger.info(s"Starting Quasimodo v${BuildInfo.version}") // BuildInfo is automatically generated by sbt-buildinfo (LiamHaworth)

    initialize()

    logger.info("Adding shutdown hook into runtime")

    sys.addShutdownHook(shutdown())

    run()
  }

  /**
    * Called to initialize the service and and its sub processes
    */
  private def initialize(): Unit = {
    logger.info("Loading configuration from /etc/quasimodo.conf")

    val defaultConfig = ConfigFactory.load()
    val overridesConfig = ConfigFactory.parseFile(new File("/etc/quasimodo.conf"))
    config = overridesConfig.withFallback(defaultConfig)

    logger.info("Initializing actor system")

    actorSystem = ActorSystem("Quasimodo", ConfigFactory.parseString(
      """
        |akka {
        |  loggers = ["akka.event.slf4j.Slf4jLogger"]
        |  loglevel = "DEBUG"
        |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
        |}
      """.stripMargin))


    logger.info("Initializing the actor supervisor")

    supervisor = actorSystem.actorOf(Props[Supervisor], "Supervisor")


    logger.info("Initializing and starting the discovery system")

    Await.result(supervisor ? (Props[Discovery], "Discovery"), timeout.duration) match {
      case actor: ActorRef =>
        discoveryActor = actor
    }
  }

  /**
    * Called once the service has been initialized
    */
  private def run(): Unit = {
    logger.info("Initialization complete, starting main processes")

    discoveryActor ! "Start"
  }

  /**
    * Called when the service receives a shutdown signal
    */
  private def shutdown(): Unit = {
    logger.warn("Received shutdown signal, terminating processes")

    actorSystem.terminate() onComplete {
      case Success(_) =>
        logger.info("System successfully shutdown")

      case Failure(ex) =>
        logger.error("System shutdown with an exception", ex)
    }
  }
}
