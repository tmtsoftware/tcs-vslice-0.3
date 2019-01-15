package org.tmt.tcs.mcs.MCSassembly

import java.time.Instant
import java.util.Calendar

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.event.api.scaladsl.EventService
import csw.framework.CurrentStatePublisher
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.ControlCommand
import csw.params.core.models.Prefix
import csw.params.events.{Event, EventName, SystemEvent}
import org.tmt.tcs.mcs.MCSassembly.Constants.EventHandlerConstants
import org.tmt.tcs.mcs.MCSassembly.EventMessage._

import scala.concurrent.duration._
import org.tmt.tcs.mcs.MCSassembly.msgTransformer.EventTransformerHelper

import scala.concurrent.{Await, ExecutionContextExecutor, Future}

sealed trait EventMessage

object EventMessage {
  case class StartEventSubscription()                                extends EventMessage
  case class hcdLocationChanged(hcdLocation: Option[CommandService]) extends EventMessage
  case class PublishHCDState(event: Event)                           extends EventMessage
  case class StartPublishingDummyEvent()                             extends EventMessage

}

object EventHandlerActor {
  def createObject(eventService: EventService,
                   hcdLocation: Option[CommandService],
                   eventTransformer: EventTransformerHelper,
                   currentStatePublisher: CurrentStatePublisher,
                   loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.setup(
      ctx =>
        EventHandlerActor(
          ctx: ActorContext[EventMessage],
          eventService: EventService,
          hcdLocation: Option[CommandService],
          eventTransformer: EventTransformerHelper,
          currentStatePublisher: CurrentStatePublisher,
          loggerFactory: LoggerFactory
      )
    )
}
/*
This actor is responsible consuming incoming events to MCS Assembly and publishing outgoing
events from MCS Assembly using CSW EventService
 */
case class EventHandlerActor(ctx: ActorContext[EventMessage],
                             eventService: EventService,
                             hcdLocation: Option[CommandService],
                             eventTransformer: EventTransformerHelper,
                             currentStatePublisher: CurrentStatePublisher,
                             loggerFactory: LoggerFactory)
    extends AbstractBehavior[EventMessage] {

  private val log                           = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val duration: Timeout            = 20 seconds
  private val eventSubscriber               = eventService.defaultSubscriber
  private val eventPublisher                = eventService.defaultPublisher

  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
    msg match {
      case _: StartEventSubscription => subscribeEventMsg()
      case x: hcdLocationChanged =>
        EventHandlerActor.createObject(eventService, x.hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
      case x: PublishHCDState => publishReceivedEvent(x.event)
      case _: StartPublishingDummyEvent =>
        EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
      case _ =>
        log.error(s"************************ Received unknown message  in EventHandlerActor $msg *********************")
        EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
    }
  }

  private def publishReceivedEvent(event: Event): Behavior[EventMessage] = {
    eventPublisher.publish(event)
    //log.error(s"Published event : $event")
    EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
  }
  /*
   *This function subscribes to position demand Events received from Other TCS Assemblies
   * using CSW EventService
   */
  private def subscribeEventMsg(): Behavior[EventMessage] = {
    //log.info(msg = s"Started subscribing events Received from tpkAssembly.")
    eventSubscriber.subscribeCallback(EventHandlerConstants.PositionDemandKey, event => sendEventByAssemblyCurrentState(event))
    EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory)
  }
  /*
    This function publishes position demands by using currentStatePublisher
   */
  private def sendEventByAssemblyCurrentState(msg: Event): Future[_] = {

    msg match {
      case systemEvent: SystemEvent =>
        //systemEvent.eventTime.time
        val event        = systemEvent.add(EventHandlerConstants.ASSEMBLY_RECEIVAL_TIME_KEY.set(Instant.now()))
        val currentState = eventTransformer.getCurrentState(event)
        currentStatePublisher.publish(currentState)
      case _ => log.error(s"Unable to map received position demands from tpk assembly to systemEvent: $msg")
    }
    Future.successful("Successfully sent positionDemand by CurrentStatePublisher")
  }

  /*
      This function publishes event by using EventPublisher to the HCD
   */
  /* private def sendEventByEventPublisher(msg: Event): Future[_] = {
    msg match {
      case systemEvent: SystemEvent =>
        log.info(s"Received event : $systemEvent")
        val assemblyRecTime = Instant.now()
        val event =
          new SystemEvent(EventHandlerConstants.ASSEMBLY_POSDEMANDS_PREFIX, EventHandlerConstants.ASSEMBLY_POSDEMANDS_EVENT)
            .add(systemEvent.get(EventHandlerConstants.AzPosKey).get)
            .add(systemEvent.get(EventHandlerConstants.ElPosKey).get)
            .add(systemEvent.get(EventHandlerConstants.TimeStampKey).get)
            .add(EventHandlerConstants.ASSEMBLY_RECEIVAL_TIME_KEY.set(assemblyRecTime))
        log.info(s" *** publishing positionDemand event: $event from EventHandlerActor *** ")
        eventPublisher.publish(event)
    }
    Future.successful("Successfully sent positionDemand by event publisher")
  }*/

  /*
    This function takes event input from EventSubscriber and if event is instance of
    SystemEvent it builds controlObject from systemEvent and sends this to HCD on commanService
    as a oneWayCommand.
   */
  /*private def sendEventByOneWayCommand(msg: Event, hcdLocation: Option[CommandService]): Future[_] = {

    log.info(
      s"*** Received positionDemand event: $msg to EventHandler OneWay ControlCommand ***"
    )
    msg match {
      case systemEvent: SystemEvent =>
        val event                          = systemEvent.add(EventHandlerConstants.ASSEMBLY_RECEIVAL_TIME_KEY.set(Instant.now()))
        val controlCommand: ControlCommand = eventTransformer.getOneWayCommandObject(event)
        hcdLocation match {
          case Some(commandService) =>
            val response = Await.result(commandService.oneway(controlCommand), 5.seconds)
            Future.successful(s"Successfully submitted positionDemand Event, response for the same is : $msg")
          case None =>
            Future.failed(new Exception("Unable to send event to assembly through oneWay command"))
        }
      case _ =>
        Future.failed(new Exception("Unable to send event to assembly through oneWay command"))
    }
  }*/
  /*private def publishDummyEventFromAssembly(): Unit = {

    log.info(msg = "Started publishing dummy Events from Assembly per 80 seconds")
    //new Thread(new Runnable { override def run(): Unit = sendDummyEvent }).start()

  }
  def sendDummyEvent(): Unit = {
    while (true) {
      Thread.sleep(80000)
      val event: SystemEvent = eventTransformer.getDummyAssemblyEvent()
      eventPublisher.publish(event, 80.seconds)
      log.info("Successfully published dummy event from assembly")
    }
  }*/
}
