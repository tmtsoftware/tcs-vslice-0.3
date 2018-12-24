package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import csw.framework.scaladsl.ComponentHandlers
import org.tmt.tcs.mcs.MCSassembly.CommandMessage._
import org.tmt.tcs.mcs.MCSassembly.Constants.Commands
import org.tmt.tcs.mcs.MCSassembly.LifeCycleMessage.{InitializeMsg, ShutdownMsg}
import org.tmt.tcs.mcs.MCSassembly.MonitorMessage._
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps

import scala.concurrent.duration._
import akka.util.Timeout

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import csw.command.api.CurrentStateSubscription
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.config.api.scaladsl.ConfigClientService
import csw.config.client.scaladsl.ConfigClientFactory
import csw.framework.models.CswContext
import csw.location.api.models.{AkkaLocation, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.params.commands.CommandIssue.{UnsupportedCommandInStateIssue, UnsupportedCommandIssue, WrongNumberOfParametersIssue}
import csw.params.commands.CommandResponse._
import csw.params.commands.{CommandResponse, ControlCommand}
import csw.params.core.generics.Parameter
import org.tmt.tcs.mcs.MCSassembly.EventMessage.{hcdLocationChanged, StartEventSubscription, StartPublishingDummyEvent}
import org.tmt.tcs.mcs.MCSassembly.msgTransformer.EventTransformerHelper

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to McsHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
class McsAssemblyHandlers(
    ctx: ActorContext[TopLevelActorMessage],
    cswCtx: CswContext
) extends ComponentHandlers(ctx, cswCtx: CswContext) {
  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger

  private val configClient: ConfigClientService = ConfigClientFactory.clientApi(ctx.system.toUntyped, locationService)

  var hcdStateSubscriber: Option[CurrentStateSubscription] = None
  var hcdLocation: Option[CommandService]                  = None

  val lifeCycleActor: ActorRef[LifeCycleMessage] =
    ctx.spawn(LifeCycleActor.createObject(commandResponseManager, configClient, loggerFactory), "LifeCycleActor")

  private val eventTransformer: EventTransformerHelper = EventTransformerHelper.create(loggerFactory)

  val eventHandlerActor: ActorRef[EventMessage] =
    ctx.spawn(EventHandlerActor.createObject(eventService, hcdLocation, eventTransformer, currentStatePublisher, loggerFactory),
              name = "EventHandlerActor")

  val monitorActor: ActorRef[MonitorMessage] = ctx.spawn(
    MonitorActor.createObject(AssemblyLifeCycleState.Initalized,
                              AssemblyOperationalState.Ready,
                              eventHandlerActor,
                              eventTransformer,
                              loggerFactory),
    name = "MonitorActor"
  )
  val commandHandlerActor: ActorRef[CommandMessage] = ctx.spawn(
    CommandHandlerActor.createObject(commandResponseManager, isOnline = true, hcdLocation, loggerFactory),
    "CommandHandlerActor"
  )

  /*
  This function is CSW in built initalization function
  1. It sends initializeMsg() to  LifecycleActor
  2. sends Initalized state msg to MonitorActor
  3. sends  StartPublishingEvents  and StartEventSubscription msg to EventHandlerActor
   */
  override def initialize(): Future[Unit] = Future {
    log.info(msg = "Initializing MCS Assembly")
    lifeCycleActor ! InitializeMsg()
    //eventHandlerActor ! StartPublishingDummyEvent()
    eventHandlerActor ! StartEventSubscription()
    monitorActor ! AssemblyLifeCycleStateChangeMsg(AssemblyLifeCycleState.Initalized)
  }
  /*
  This function sends shutdown msg to lifecycle actor and updates Monitor actor status to shutdown
   */
  override def onShutdown(): Future[Unit] = Future {
    log.info(msg = "Shutting down MCS Assembly")
    monitorActor ! AssemblyLifeCycleStateChangeMsg(AssemblyLifeCycleState.Shutdown)
    lifeCycleActor ! ShutdownMsg()
  }
  /*
    This component tracks for updated hcd locations on command service and accordingly updates
    command handler actor and monitor actor
   */
  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    trackingEvent match {
      case LocationUpdated(location) =>
        hcdLocation = Some(CommandServiceFactory.make(location.asInstanceOf[AkkaLocation])(ctx.system))
        hcdStateSubscriber = Some(hcdLocation.get.subscribeCurrentState(monitorActor ! currentStateChangeMsg(_)))
      case LocationRemoved(_) =>
        hcdLocation = None
        log.error(s"Removing HCD Location registered with assembly")
    }
    monitorActor ! LocationEventMsg(hcdLocation)
    commandHandlerActor ! updateHCDLocation(hcdLocation)
    log.error(s"Sending hcdLocation:$hcdLocation to eventHandlerActor")
    eventHandlerActor ! hcdLocationChanged(hcdLocation)
    eventHandlerActor ! StartEventSubscription()
  }

  override def validateCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(msg = s" validating command ----> ${controlCommand.commandName}")
    controlCommand.commandName.name match {

      case Commands.FOLLOW              => validateFollowCommand(controlCommand)
      case Commands.MOVE                => validateMoveCommand(controlCommand)
      case Commands.DATUM               => validateDatumCommand(controlCommand)
      case Commands.DUMMY_IMMEDIATE     => executeDummyImmediateCommand(controlCommand)
      case Commands.DUMMY_LONG          => Accepted(controlCommand.runId)
      case Commands.STARTUP             => Accepted(controlCommand.runId)
      case Commands.SHUTDOWN            => Accepted(controlCommand.runId)
      case Commands.SET_SIMULATION_MODE => Accepted(controlCommand.runId)
      case x                            => Invalid(controlCommand.runId, UnsupportedCommandIssue(s"Command $x is not supported"))
    }
  }
  def executeDummyImmediateCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(msg = s"Executing Dummy Immediate command  : $controlCommand")
    CommandResponse.Accepted(controlCommand.runId)
  }
  /*
  This function validates follow command based on  assembly state received from MonitorActor
  if validation successful then returns command execution response else invalid command response
  is sent to caller

   */
  private def validateFollowCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    val assemblyCurrentState = getCurrentAssemblyState
    log.info(msg = s"Monitor Actor's current state while validating Follow command is  : $assemblyCurrentState")
    if (validateAssemblyState(assemblyCurrentState)) {
      Accepted(controlCommand.runId)
    } else {
      CommandResponse.Invalid(
        controlCommand.runId,
        UnsupportedCommandInStateIssue(s" Follow command is not allowed if assembly is in $assemblyCurrentState")
      )
    }
  }

  private def executeSimModeAndSendResp(controlCommand: ControlCommand): SubmitResponse = {
    implicit val duration: Timeout = 4 seconds
    implicit val scheduler         = ctx.system.scheduler
    val immediateResponse: CommandMessage = Await.result(commandHandlerActor ? { ref: ActorRef[CommandMessage] =>
      CommandMessage.ImmediateCommand(ref, controlCommand)
    }, 3.seconds)
    immediateResponse match {
      case msg: ImmediateCommandResponse => msg.submitResponse
      case _                             => Invalid(controlCommand.runId, UnsupportedCommandInStateIssue(s" Unable to setup simulation mode."))
    }
  }
  /*
    This function executes follow command by sending msg to commandhandler and sends response of commandhandler to
    the caller
   */
  private def executeFollowCommandAndSendResponse(controlCommand: ControlCommand): SubmitResponse = {
    implicit val duration: Timeout = 4 seconds
    implicit val scheduler         = ctx.system.scheduler
    val immediateResponse: CommandMessage = Await.result(commandHandlerActor ? { ref: ActorRef[CommandMessage] =>
      CommandMessage.ImmediateCommand(ref, controlCommand)
    }, 2.seconds)
    immediateResponse match {
      case msg: ImmediateCommandResponse =>
        msg.submitResponse match {
          case _: Completed           => monitorActor ! AssemblyOperationalStateChangeMsg(AssemblyOperationalState.Slewing)
          case _: CompletedWithResult => monitorActor ! AssemblyOperationalStateChangeMsg(AssemblyOperationalState.Slewing)
          case _: Error               => log.error("Error occurred while processing follow command")
        }
        log.info(s"Follow command response is : ${msg.submitResponse}")
        msg.submitResponse
      case _ =>
        CommandResponse.Invalid(
          controlCommand.runId,
          UnsupportedCommandInStateIssue(s" Follow command is not allowed if assembly is not in Running state")
        )
    }
  }
  /*
    This function fetches current state of Monitor Actor using akka ask pattern
   */
  private def getCurrentAssemblyState(): MonitorMessage = {
    implicit val duration: Timeout = 1 seconds
    implicit val scheduler         = ctx.system.scheduler
    Await.result(monitorActor ? { ref: ActorRef[MonitorMessage] =>
      MonitorMessage.GetCurrentState(ref)
    }, 1.seconds)
  }
  /*
    This function checks whether assembly state is running or not
   */
  private def validateAssemblyState(assemblyCurrentState: MonitorMessage): Boolean = {
    assemblyCurrentState match {
      case x: MonitorMessage.AssemblyCurrentState =>
        log.info(msg = s"Assembly current state from monitor actor  is : $x")
        x.lifeCycleState.toString match {
          case "Running" if x.operationalState.toString.equals("Running") => true
          case _                                                          => false
        }
      case _ =>
        log.error(msg = s"Incorrect current state is provided to assembly by monitor actor")
        false
    }
  }
  /*
  This function checks whether axes parameters are provided or not
   */
  private def validateParams(controlCommand: ControlCommand): Boolean = {
    val axes: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
    val param              = axes.head
    if (param == "BOTH" || param == "AZ" || param == "EL") {
      return true
    }
    false
  }
  /*
    This function validates move command based on parameters and state
   */
  private def validateMoveCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    val paramsValidate: Boolean = validateParams(controlCommand)
    paramsValidate match {
      case true =>
        implicit val duration: Timeout = 20 seconds
        implicit val scheduler         = ctx.system.scheduler
        val assemblyCurrentState = Await.result(monitorActor ? { ref: ActorRef[MonitorMessage] =>
          MonitorMessage.GetCurrentState(ref)
        }, 3.seconds)
        log.info(msg = s"Response from monitor actor is : $assemblyCurrentState")
        val assemblyState: Boolean = validateAssemblyState(assemblyCurrentState)
        assemblyState match {
          case true =>
            monitorActor ! AssemblyOperationalStateChangeMsg(AssemblyOperationalState.Inposition)
            CommandResponse.Accepted(controlCommand.runId)
          case false =>
            CommandResponse.Invalid(
              controlCommand.runId,
              UnsupportedCommandInStateIssue(
                s" Move command is not allowed as monitor actor's current state is : $assemblyCurrentState"
              )
            )
        }
      case false =>
        CommandResponse.Invalid(controlCommand.runId,
                                WrongNumberOfParametersIssue(s" axes parameter is not provided for move command"))
    }
  }
  /*
  This function validates datum command based on parameters and state
   */
  private def validateDatumCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    // check hcd is in running state
    val validateParamsBool: Boolean = validateParams(controlCommand)
    validateParamsBool match {
      case true =>
        implicit val duration: Timeout = 10 seconds
        implicit val scheduler         = ctx.system.scheduler
        val assemblyCurrentState = Await.result(monitorActor ? { ref: ActorRef[MonitorMessage] =>
          MonitorMessage.GetCurrentState(ref)
        }, 5.seconds)
        log.info(msg = s"Response from monitor actor, while validating datum command  is : $assemblyCurrentState")
        val assemblyStateBool = validateAssemblyState(assemblyCurrentState)
        assemblyStateBool match {
          case true => CommandResponse.Accepted(controlCommand.runId)
          case false =>
            log.error(s"Unable to pass datum command as assembly current state is : $assemblyCurrentState")
            CommandResponse.Invalid(
              controlCommand.runId,
              UnsupportedCommandInStateIssue(s" Datum command is not allowed if assembly is not in Running state")
            )
        }
      case false =>
        log.error(s"Incorrect parameters provided for Datum command ")
        CommandResponse.Invalid(controlCommand.runId,
                                WrongNumberOfParametersIssue(s" axes parameter is not provided for datum command"))
    }
  }

  override def onSubmit(controlCommand: ControlCommand): SubmitResponse = {
    controlCommand.commandName.name match {
      case Commands.FOLLOW              => executeFollowCommandAndSendResponse(controlCommand)
      case Commands.SET_SIMULATION_MODE => executeSimModeAndSendResp(controlCommand)
      case _ =>
        commandHandlerActor ! submitCommandMsg(controlCommand)
        Started(controlCommand.runId)
    }
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    log.info(msg = "executing one way command")
  }

  //TODO : GoOnlineMSg is operational command..? why GoOnline and GoOffline messages are going to commandHandlerActor
  // If lifecycle commands then what they are supposed to do in LifecycleActor
  override def onGoOffline(): Unit = {
    log.info(msg = "MCS Assembly going down")
    commandHandlerActor ! GoOfflineMsg()
  }

  override def onGoOnline(): Unit = {
    log.info(msg = "MCS Assembly going online")
    commandHandlerActor ! GoOnlineMsg()
  }

}
