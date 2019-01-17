package org.tmt.tcs.mcs.MCShcd

import java.time.Instant

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.event.api.scaladsl.EventService
import csw.framework.CurrentStatePublisher
import csw.logging.scaladsl.LoggerFactory
import csw.params.core.generics.Parameter
import csw.params.core.states.CurrentState
import csw.params.events.{Event, SystemEvent}
import org.tmt.tcs.mcs.MCShcd.EventMessage._
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg.{ProcCurrStateDemand, ProcEventDemand}
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.{PublishCurrStateToZeroMQ, PublishEvent, StartSimulEventSubscr}
import org.tmt.tcs.mcs.MCShcd.constants.{Commands, EventConstants}
import org.tmt.tcs.mcs.MCShcd.msgTransformers.ParamSetTransformer

import scala.concurrent.{ExecutionContextExecutor, Future}

sealed trait EventMessage
object EventMessage {
  case class HCDOperationalStateChangeMsg(operationalState: HCDOperationalState.operationalState) extends EventMessage

  case class StateChangeMsg(lifeCycleState: HCDLifeCycleState.lifeCycleState,
                            oerationalState: HCDOperationalState.operationalState)
      extends EventMessage

  case class GetCurrentState(sender: ActorRef[EventMessage]) extends EventMessage
  case class HcdCurrentState(lifeCycleState: HCDLifeCycleState.lifeCycleState,
                             operationalState: HCDOperationalState.operationalState)
      extends EventMessage
  // case class StartPublishing() extends EventMessage
  /* case class StartEventSubscription(zeroMQProtoActor: ActorRef[ZeroMQMessage], simpleSimActor: ActorRef[SimpleSimMsg])
      extends EventMessage*/
  case class PublishState(currentState: CurrentState)        extends EventMessage
  case class AssemblyStateChange(currentState: CurrentState) extends EventMessage
  case class SimulationModeChange(simMode: String, simpleSimActor: ActorRef[SimpleSimMsg], zeroMQActor: ActorRef[ZeroMQMessage])
      extends EventMessage

}

object HCDLifeCycleState extends Enumeration {
  type lifeCycleState = Value
  val Off, Ready, Loaded, Initialized, Running = Value
}
object HCDOperationalState extends Enumeration {
  type operationalState = Value
  val DrivePowerOff, ServoOffDrivePowerOn, ServoOffDatumed, PointingDrivePowerOn, PointingDatumed, Following, StowingDrivePowerOn,
  StowingDatumed, Degraded, Disconnected, Faulted = Value
}
object StatePublisherActor {
  def createObject(currentStatePublisher: CurrentStatePublisher,
                   lifeCycleState: HCDLifeCycleState.lifeCycleState,
                   operationalState: HCDOperationalState.operationalState,
                   eventService: EventService,
                   simulatorMode: String,
                   zeroMQActor: ActorRef[ZeroMQMessage],
                   simpleSimActor: ActorRef[SimpleSimMsg],
                   loggerFactory: LoggerFactory): Behavior[EventMessage] =
    Behaviors.setup(
      ctx =>
        StatePublisherActor(ctx,
                            currentStatePublisher,
                            lifeCycleState,
                            operationalState,
                            eventService,
                            simulatorMode,
                            zeroMQActor,
                            simpleSimActor,
                            loggerFactory)
    )

}

/*
This actor is responsible for publishing state, events for MCS to assembly it dervies HCD states from
MCS state and  events received
 */
case class StatePublisherActor(ctx: ActorContext[EventMessage],
                               currentStatePublisher: CurrentStatePublisher,
                               lifeCycleState: HCDLifeCycleState.lifeCycleState,
                               operationalState: HCDOperationalState.operationalState,
                               eventService: EventService,
                               simulatorMode: String,
                               zeroMQActor: ActorRef[ZeroMQMessage],
                               simpleSimActor: ActorRef[SimpleSimMsg],
                               loggerFactory: LoggerFactory)
    extends AbstractBehavior[EventMessage] {
  private val log                                      = loggerFactory.getLogger
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)

  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  /*
    This function is used in case of EventPublisher for publishing demands to MCS Simulator
   */
  private def processEvent(event: Event): Future[_] = {
    event match {
      case systemEvent: SystemEvent =>
        val sysEvent = systemEvent.add(EventConstants.HcdReceivalTime_Key.set(Instant.now()))
        simulatorMode match {
          case Commands.REAL_SIMULATOR   => zeroMQActor ! PublishEvent(sysEvent)
          case Commands.SIMPLE_SIMULATOR => simpleSimActor ! ProcEventDemand(sysEvent)
        }
        Future.successful("Successfully sent Assembly position demands to MCS ZeroMQActor")
    }
  }
  /*
       This function performs following tasks:
       - On receipt of StartEventSubscription message this function starts subscribing to positionDemand event
        from MCS Assembly using CSW EventService's default EventSubscriber.
       - On receipt of StateChangeMsg message this function publishes HCD's Lifecycle state to Assembly using
          CSW CurrentStatePublisher
       - On receipt of HCDOperationalStateChangeMsg it simply changes Actor's behavior to changed operational state
       and does not publish anything
       - On receipt of GetCurrentState msg it returns current lifecycle and operational state of HCD to caller

   */
  override def onMessage(msg: EventMessage): Behavior[EventMessage] = {
    msg match {
      /* case msg: StartEventSubscription =>
        val eventSubscriber = eventService.defaultSubscriber
        zeroMQActor = msg.zeroMQProtoActor
        simpleSimActor = msg.simpleSimActor
        log.info(msg = s"Starting subscribing to events from MCS Assembly in StatePublisherActor via EventSubscriber")
        eventSubscriber.subscribeCallback(EventConstants.AssemblyPositionDemandKey, event => processEvent(event))
        Behavior.same*/
      case msg: StateChangeMsg =>
        val currLifeCycleState = msg.lifeCycleState
        val state              = currLifeCycleState.toString
        log.info(msg = s"Changed lifecycle state of MCS HCD is : $state and publishing the same to the MCS Assembly")
        val currentState: CurrentState = paramSetTransformer.getHCDState(state)
        currentStatePublisher.publish(currentState)
        log.info(msg = s"Successfully published state :$currentState to ASSEMBLY")
        StatePublisherActor.createObject(currentStatePublisher,
                                         currLifeCycleState,
                                         msg.oerationalState,
                                         eventService,
                                         simulatorMode,
                                         zeroMQActor,
                                         simpleSimActor,
                                         loggerFactory)
      case msg: PublishState =>
        simulatorMode match {
          case Commands.SIMPLE_SIMULATOR =>
            val hcdReceivalTime: Parameter[Instant] = EventConstants.hcdEventReceivalTime_Key.set(Instant.now())
            currentStatePublisher.publish(msg.currentState.add(hcdReceivalTime))
            Behavior.same
          case Commands.REAL_SIMULATOR =>
            currentStatePublisher.publish(msg.currentState)
            Behavior.same
        }
      case msg: HCDOperationalStateChangeMsg =>
        val currOperationalState = msg.operationalState
        log.info(msg = s"Changing current operational state of MCS HCD to: $currOperationalState")
        StatePublisherActor.createObject(currentStatePublisher,
                                         lifeCycleState,
                                         currOperationalState,
                                         eventService,
                                         simulatorMode,
                                         zeroMQActor,
                                         simpleSimActor,
                                         loggerFactory)
      case msg: SimulationModeChange =>
        StatePublisherActor.createObject(currentStatePublisher,
                                         lifeCycleState,
                                         operationalState,
                                         eventService,
                                         msg.simMode,
                                         msg.zeroMQActor,
                                         msg.simpleSimActor,
                                         loggerFactory)
      case msg: AssemblyStateChange =>
        val currentState = msg.currentState
        val currState    = currentState.add(EventConstants.HcdReceivalTime_Key.set(Instant.now()))
        simulatorMode match {
          case Commands.REAL_SIMULATOR   => zeroMQActor ! PublishCurrStateToZeroMQ(currState)
          case Commands.SIMPLE_SIMULATOR => simpleSimActor ! ProcCurrStateDemand(currState)
        }
        Behavior.same

      case msg: GetCurrentState =>
        msg.sender ! HcdCurrentState(lifeCycleState, operationalState)
        Behavior.same
      case _ =>
        log.error(msg = s"Unknown $msg is sent to EventHandlerActor")
        Behavior.unhandled
    }
  }
}
