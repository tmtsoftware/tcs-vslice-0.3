package org.tmt.tcs.mcs.MCShcd.workers

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import csw.logging.scaladsl.LoggerFactory
import csw.params.commands.ControlCommand
import csw.params.core.generics.Parameter
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg.ProcOneWayDemand
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.PublishEvent
import org.tmt.tcs.mcs.MCShcd.constants.Commands
import org.tmt.tcs.mcs.MCShcd.msgTransformers.{MCSPositionDemand, ParamSetTransformer}

object PositionDemandActor {

  def create(loggerFactory: LoggerFactory,
             zeroMQProtoActor: ActorRef[ZeroMQMessage],
             simpleSimActor: ActorRef[SimpleSimMsg],
             simulatorMode: String,
             paramSetTransformer: ParamSetTransformer): Behavior[ControlCommand] =
    Behaviors.setup(
      ctx => PositionDemandActor(ctx, loggerFactory, zeroMQProtoActor, simpleSimActor, simulatorMode, paramSetTransformer)
    )
}

/*
This actor will receive position demands a as control command from MCSHandler,
It converts control command into MCSPositionDemands and sends the same to the ZeroMQActor for publishing

 */
case class PositionDemandActor(ctx: ActorContext[ControlCommand],
                               loggerFactory: LoggerFactory,
                               zeroMQProtoActor: ActorRef[ZeroMQMessage],
                               simpleSimActor: ActorRef[SimpleSimMsg],
                               simulatorMode: String,
                               paramSetTransformer: ParamSetTransformer)
    extends AbstractBehavior[ControlCommand] {
  private val log = loggerFactory.getLogger
  override def onMessage(msg: ControlCommand): Behavior[ControlCommand] = {
    //log.info(s"Sending position demands: ${msg} to ZeroMQActor for publishing")
    msg.commandName.name match {
      case Commands.SET_SIMULATION_MODE => {
        val modeParam: Parameter[_] = msg.paramSet.find(msg => msg.keyName == Commands.SIMULATION_MODE).get
        val param: Any              = modeParam.head
        log.info(s"Changing simulation mode from $simulatorMode to ${param.toString}")
        PositionDemandActor.create(loggerFactory, zeroMQProtoActor, simpleSimActor, param.toString, paramSetTransformer)
      }
      case Commands.POSITION_DEMANDS => {
        processPositionDemands(msg)
        Behavior.same
      }
    }
  }
  private def processPositionDemands(msg: ControlCommand) = {
    simulatorMode match {
      case Commands.REAL_SIMULATOR => zeroMQProtoActor ! PublishEvent(paramSetTransformer.getMountDemandPositions(msg))

      case Commands.SIMPLE_SIMULATOR => simpleSimActor ! ProcOneWayDemand(msg)
    }
  }
}
