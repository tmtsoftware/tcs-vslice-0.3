package org.tmt.tcs.mcs.MCShcd

import java.time.Instant

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import csw.framework.scaladsl.ComponentHandlers
import csw.command.client.messages.TopLevelActorMessage
import csw.params.commands._
import org.tmt.tcs.mcs.MCShcd.EventMessage._
import org.tmt.tcs.mcs.MCShcd.LifeCycleMessage.ShutdownMsg
import org.tmt.tcs.mcs.MCShcd.constants.{Commands, EventConstants}
import akka.actor.typed.scaladsl.AskPattern._
import csw.command.api.CurrentStateSubscription
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.framework.models.CswContext
import csw.location.api.models.{AkkaLocation, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.params.commands.CommandIssue.{UnsupportedCommandIssue, WrongInternalStateIssue, WrongNumberOfParametersIssue}
import csw.params.commands.CommandResponse._
import csw.params.core.generics.Parameter
import csw.params.core.models.{Prefix, Subsystem}
import org.tmt.tcs.mcs.MCShcd.HCDCommandMessage.{submitCommand, ImmediateCommandResponse}
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.{Disconnect, StartSimulEventSubscr}
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, SimpleSimulator, ZeroMQMessage, ZeroMQProtocolActor}
import org.tmt.tcs.mcs.MCShcd.msgTransformers.ParamSetTransformer
import org.tmt.tcs.mcs.MCShcd.workers.PositionDemandActor

import scala.concurrent.Await
import scala.concurrent.duration._
//import akka.pattern.ask
import scala.concurrent.{ExecutionContextExecutor, Future}

/**
 * Domain specific logic should be written in below handlers.
 * This handlers gets invoked when component receives messages/commands from other component/entity.
 * For example, if one component sends Submit(Setup(args)) command to McsHcd,
 * This will be first validated in the supervisor and then forwarded to Component TLA which first invokes validateCommand hook
 * and if validation is successful, then onSubmit hook gets invoked.
 * You can find more information on this here : https://tmtsoftware.github.io/csw-prod/framework.html
 */
class McsHcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext) {
  import cswCtx._
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  private val log                           = loggerFactory.getLogger
  private var simulatorMode: String         = Commands.REAL_SIMULATOR

  private val lifeCycleActor: ActorRef[LifeCycleMessage] =
    ctx.spawn(LifeCycleActor.createObject(commandResponseManager, locationService, loggerFactory), "LifeCycleActor")

  // private val simulator: SimpleSimulator = SimpleSimulator.create(loggerFactory)
  private val statePublisherActor: ActorRef[EventMessage] = ctx.spawn(
    StatePublisherActor.createObject(currentStatePublisher,
                                     HCDLifeCycleState.Off,
                                     HCDOperationalState.DrivePowerOff,
                                     eventService,
                                     simulatorMode,
                                     loggerFactory),
    "StatePublisherActor"
  )

  private val zeroMQProtoActor: ActorRef[ZeroMQMessage] =
    ctx.spawn(ZeroMQProtocolActor.create(statePublisherActor, loggerFactory), "ZeroMQActor")
  private val simpleSimActor: ActorRef[SimpleSimMsg] =
    ctx.spawn(SimpleSimulator.create(loggerFactory, statePublisherActor), "SimpleSimulator")

  private val commandHandlerActor: ActorRef[HCDCommandMessage] =
    ctx.spawn(
      CommandHandlerActor.createObject(commandResponseManager,
                                       lifeCycleActor,
                                       zeroMQProtoActor,
                                       simpleSimActor,
                                       simulatorMode,
                                       loggerFactory),
      "CommandHandlerActor"
    )
  private val paramSetTransformer: ParamSetTransformer = ParamSetTransformer.create(loggerFactory)
  private val positionDemandActor: ActorRef[ControlCommand] =
    ctx.spawn(PositionDemandActor.create(loggerFactory, zeroMQProtoActor, simpleSimActor, simulatorMode, paramSetTransformer),
              "PositionDemandEventActor")
  /*
  This function initializes HCD, uses configuration object to initialize Protocol and
  sends updated states tp state publisher actor for publishing
   */
  override def initialize(): Future[Unit] = Future {
    log.info(msg = "Initializing MCS HCD")

    implicit val duration: Timeout = 20 seconds

    implicit val scheduler = ctx.system.scheduler

    val lifecycleMsg = Await.result(lifeCycleActor ? { ref: ActorRef[LifeCycleMessage] =>
      LifeCycleMessage.InitializeMsg(ref)
    }, 10.seconds)
    //TODO : Commenting this for testing oneWayCommandExecution and CurrentStatePublisher

    if (connectToSimulator(lifecycleMsg)) {
      //statePublisherActor ! StartEventSubscription(zeroMQProtoActor, simpleSimActor)
      statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Initialized, HCDOperationalState.DrivePowerOff)
    } else {
      log.error(msg = s"Unable to connect with MCS Simulator")
      statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Initialized, HCDOperationalState.Disconnected)
    }

  }

  private def connectToSimulator(lifecycleMsg: LifeCycleMessage): Boolean = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler
    lifecycleMsg match {
      case x: LifeCycleMessage.HCDConfig => {
        //  log.info(msg = s"Sending initialize message to zeroMQActor, config from configuration service is : ${x.config}")

        val zeroMQMsg = Await.result(zeroMQProtoActor ? { ref: ActorRef[ZeroMQMessage] =>
          ZeroMQMessage.InitializeSimulator(ref, x.config)
        }, 10.seconds)

        zeroMQMsg match {
          case msg: ZeroMQMessage.SimulatorConnResponse => msg.connected
          case _                                        => false
        }

      }
      case _ =>
        log.error("Unable to get Config object from configuration service for connecting with ZeroMQ")
        false
    }
  }

  var assemblyDemandsSubscriber: Option[CurrentStateSubscription] = None
  var assemblyLocation: Option[CommandService]                    = None

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    // log.error(msg = "** Assembly Location changed **")
    trackingEvent match {
      case LocationUpdated(location) => {
        assemblyLocation = Some(CommandServiceFactory.make(location.asInstanceOf[AkkaLocation])(ctx.system))
        assemblyDemandsSubscriber = Some(
          assemblyLocation.get.subscribeCurrentState(
            statePublisherActor ! AssemblyStateChange(zeroMQProtoActor, simpleSimActor, _)
          )
        )
      }
      case LocationRemoved(_) => {
        assemblyLocation = None
        log.error(s"Removing Assembly Location registered with HCD")
      }
    }
  }

  override def validateCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    // log.info(msg = s" validating command ----> ${controlCommand.commandName}")
    controlCommand.commandName.name match {
      case Commands.DATUM        => validateDatumCommand(controlCommand)
      case Commands.FOLLOW       => validateFollowCommand(controlCommand)
      case Commands.POINT        => validatePointCommand(controlCommand)
      case Commands.POINT_DEMAND => validatePointDemandCommand(controlCommand)
      // position demands command is used during one way command tpk position demands
      case Commands.POSITION_DEMANDS    => Accepted(controlCommand.runId)
      case Commands.STARTUP             => Accepted(controlCommand.runId)
      case Commands.SHUTDOWN            => Accepted(controlCommand.runId)
      case Commands.SET_SIMULATION_MODE => Accepted(controlCommand.runId)
      case x                            => Invalid(controlCommand.runId, UnsupportedCommandIssue(s"Command $x is not supported"))
    }
  }

  /*
     This functions validates point demand command based upon paramters and hcd state
   */
  private def validatePointDemandCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(msg = s"validating point demand command in HCD")

    def validateParams: Boolean = {
      val azParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "AZ").get
      val param1                = azParam.head
      val elParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "EL").get
      val param2                = elParam.head
      log.info(msg = s"In Point Demand command az value is : $param1 and el value is : $param2")
      if (param1 == null || param2 == null) {
        return false
      }
      true
    }
    def validateHCDState: Boolean = {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler

      val hcdCurrentState = Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
        EventMessage.GetCurrentState(ref)
      }, 3.seconds)
      hcdCurrentState match { //TODO : here should be the logic to change assembly states based on current state
        case x: EventMessage.HcdCurrentState =>
          x.lifeCycleState match {
            case HCDLifeCycleState.Running
                if x.operationalState.equals(HCDOperationalState.PointingDrivePowerOn) ||
                x.operationalState.equals(HCDOperationalState.PointingDatumed) =>
              return true
            case _ =>
              log.error(
                msg = s" to execute pointind demand command HCD Lifecycle state must be running and operational state must " +
                s"be ${HCDOperationalState.PointingDatumed} or ${HCDOperationalState.PointingDrivePowerOn}"
              )
              return false
          }
        case _ =>
          log.error(msg = s"Incorrect state is sent to HCD handler by state publisher actor")
          return false
      }
      false
    }
    if (validateParams) {
      if (validateHCDState) {
        CommandResponse.Accepted(controlCommand.runId)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          WrongInternalStateIssue(
            s" MCS HCD and subsystem is not in running state " +
            s"and operational state must be PointingDatumed or PointingDrivePowerOn to process pointDemand command"
          )
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId,
                              WrongNumberOfParametersIssue(s" az and el parameter is not provided for point demand command"))
    }

  }
  /*
       This functions validates point  command based upon paramters and hcd state
   */
  private def validatePointCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info(msg = "Validating point command in HCD")
    def validateParams: Boolean = {
      val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
      val param1                  = axesParam.head
      if (param1 == "BOTH" || param1 == "AZ" || param1 == "EL") {
        return true
      }
      false
    }
    def validateHCDState: Boolean = {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler

      val hcdCurrentState = Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
        EventMessage.GetCurrentState(ref)
      }, 3.seconds)
      hcdCurrentState match {
        case x: EventMessage.HcdCurrentState =>
          x.lifeCycleState match {
            case HCDLifeCycleState.Running
                if x.operationalState.equals(HCDOperationalState.ServoOffDrivePowerOn) ||
                x.operationalState.equals(HCDOperationalState.ServoOffDatumed) =>
              return true

            case _ =>
              log.error(
                msg = s" to execute pointing demand command HCD Lifecycle state must be running and operational state must " +
                s"be ${HCDOperationalState.ServoOffDrivePowerOn} or ${HCDOperationalState.ServoOffDatumed}"
              )
              return false
          }
        case _ =>
          log.error(msg = s"Incorrect state is sent to HCD handler by state publisher actor")
          return false
      }
      false
    }
    val paramValidation = validateParams
    paramValidation match {
      case true =>
        val hcdStateValidate = validateHCDState
        hcdStateValidate match {
          case true => Accepted(controlCommand.runId)
          case false =>
            CommandResponse.Invalid(
              controlCommand.runId,
              WrongInternalStateIssue(
                s" MCS HCD and subsystem must be in ${HCDLifeCycleState.Running} state" +
                s"and operational state must be ${HCDOperationalState.ServoOffDrivePowerOn} " +
                s"or ${HCDOperationalState.ServoOffDatumed} to process point  command"
              )
            )
        }
      case false =>
        CommandResponse.Invalid(controlCommand.runId,
                                WrongNumberOfParametersIssue(s" axes parameter is not provided for point command"))
    }
  }
  /*
       This functions validates datum  command based upon paramters and hcd state
   */
  private def validateDatumCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    log.info("Validating Datum command in HCD")
    def validateParams: Boolean = {
      val axesParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == "axes").get
      val param1                  = axesParam.head
      if (param1 == "BOTH" || param1 == "AZ" || param1 == "EL") {
        return true
      }
      false
    }
    def validateHCDState: Boolean = {
      implicit val duration: Timeout = 20 seconds
      implicit val scheduler         = ctx.system.scheduler

      val hcdCurrentState = Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
        EventMessage.GetCurrentState(ref)
      }, 3.seconds)
      hcdCurrentState match {
        case x: EventMessage.HcdCurrentState =>
          x.lifeCycleState match {
            case HCDLifeCycleState.Running if x.operationalState.equals(HCDOperationalState.DrivePowerOff) => return true
            case _ =>
              log.error(
                msg =
                  s" to execute datum command HCD Lifecycle state must be running and operational state must be ${HCDOperationalState.DrivePowerOff}"
              )
              return false
          }
        case _ =>
          log.error(msg = s"Incorrect state is sent to HCD handler by state publisher actor")
          return false
      }
      false
    }
    val paramsBool = validateParams
    paramsBool match {
      case true =>
        val hcdState = validateHCDState
        hcdState match {
          case true => Accepted(controlCommand.runId)
          case false =>
            Invalid(
              controlCommand.runId,
              WrongInternalStateIssue(
                s" MCS HCD and subsystem must be  in ${HCDLifeCycleState.Running} state " +
                s"and operational state must be ${HCDOperationalState.ServoOffDrivePowerOn} or  ${HCDOperationalState.ServoOffDatumed} to process datum command"
              )
            )
        }
      case false =>
        Invalid(controlCommand.runId, WrongNumberOfParametersIssue(s" axes parameter is not provided for datum command"))
    }
  }

  private def getHCDCurrentState(): EventMessage = {
    implicit val duration: Timeout = 20 seconds
    implicit val scheduler         = ctx.system.scheduler

    Await.result(statePublisherActor ? { ref: ActorRef[EventMessage] =>
      EventMessage.GetCurrentState(ref)
    }, 3.seconds)
  }
  /*
       This functions validates follow  command based upon parameters and hcd state
       It has 2 internal functions 1 is for validating parameterSet and 1 is for
       validating HCDCurrentState.
       If both functions returns positive response then only it processes command else rejects
       command execution.
       If validation function response is successful then it executes follow command and sends follow commamnd
       execution response to the caller.

   */
  private def validateFollowCommand(controlCommand: ControlCommand): ValidateCommandResponse = {
    //log.info("Validating follow command in HCD")
    def validateParamset: Boolean = controlCommand.paramSet.isEmpty
    def validateHCDState: Boolean = {
      val hcdCurrentState = getHCDCurrentState()
      hcdCurrentState match {
        case x: EventMessage.HcdCurrentState =>
          x.lifeCycleState match {
            case HCDLifeCycleState.Running if x.operationalState.equals(HCDOperationalState.ServoOffDatumed) =>
              return true
            case _ =>
              log.error(
                msg =
                  s" to execute Follow command HCD Lifecycle state must be running and operational state must be ${HCDOperationalState.ServoOffDatumed}"
              )
              return false
          }
        case _ =>
          log.error(msg = s"Incorrect state is sent to HCD handler by state publisher actor")
          return false
      }
      false
    }
    if (validateParamset) {
      if (validateHCDState) {
        Accepted(controlCommand.runId)
      } else {
        CommandResponse.Invalid(
          controlCommand.runId,
          WrongInternalStateIssue(
            s" MCS HCD and subsystem must be in ${HCDLifeCycleState.Running} state " +
            s"and operational state must be in ${HCDOperationalState.ServoOffDatumed} to process follow command"
          )
        )
      }
    } else {
      CommandResponse.Invalid(controlCommand.runId, WrongNumberOfParametersIssue("Follow command should not have any parameters"))
    }
  }

  /*
  This function executes follow command and sends follow command execution
  response to caller, if follow command execution response is successful
  then it changes state of statePublisherActor to Following

   */
  private def executeFollowCommandAndSendResponse(controlCommand: ControlCommand): SubmitResponse = {
    implicit val duration: Timeout = 1 seconds
    implicit val scheduler         = ctx.system.scheduler
    val immediateResponse: HCDCommandMessage = Await.result(commandHandlerActor ? { ref: ActorRef[HCDCommandMessage] =>
      HCDCommandMessage.ImmediateCommand(ref, controlCommand)
    }, 1 seconds)
    immediateResponse match {
      case msg: ImmediateCommandResponse =>
        msg.submitResponse match {
          case _: Completed           => statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.Following)
          case _: CompletedWithResult => statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.Following)
          case _: Error               => log.error("Error occurred while processing follow command")
        }
        msg.submitResponse
      case _ =>
        CommandResponse.Invalid(
          controlCommand.runId,
          CommandIssue.WrongInternalStateIssue(" Follow Command is not allowed if hcd is not in proper state")
        )
    }
  }
  private def execSimModeCmdAndSendResp(controlCommand: ControlCommand): SubmitResponse = {
    val modeParam: Parameter[_] = controlCommand.paramSet.find(msg => msg.keyName == Commands.SIMULATION_MODE).get
    val param                   = modeParam.head
    param match {
      case Commands.REAL_SIMULATOR   => simulatorMode = Commands.REAL_SIMULATOR
      case Commands.SIMPLE_SIMULATOR => simulatorMode = Commands.SIMPLE_SIMULATOR
    }
    commandHandlerActor ! submitCommand(controlCommand)
    statePublisherActor ! SimulationModeChange(simulatorMode, simpleSimActor, zeroMQProtoActor)
    positionDemandActor ! controlCommand
    Completed(controlCommand.runId)
  }
  /*
       This functions routes all commands to commandhandler actor and bsed upon command execution updates states of HCD by sending it
       to StatePublisher actor
   */
  override def onSubmit(controlCommand: ControlCommand): SubmitResponse = {

    controlCommand.commandName.name match {
      case Commands.STARTUP =>
        commandHandlerActor ! HCDCommandMessage.submitCommand(controlCommand)
        statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Running, HCDOperationalState.DrivePowerOff)
        log.info("On receipt of startup command changing MCS HCD state to Running")
        Started(controlCommand.runId)
      case Commands.SHUTDOWN =>
        commandHandlerActor ! HCDCommandMessage.submitCommand(controlCommand)
        log.info("On receipt of shutdown command changing MCS HCD state to Disconnected")
        statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Off, HCDOperationalState.Disconnected)
        Started(controlCommand.runId)
      case Commands.DATUM =>
        commandHandlerActor ! HCDCommandMessage.submitCommand(controlCommand)
        log.info("changing HCD's operational state to ServoOffDatumed")
        statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.ServoOffDatumed)
        Started(controlCommand.runId)
      case Commands.FOLLOW =>
        executeFollowCommandAndSendResponse(controlCommand)
      case Commands.POINT | Commands.POINT_DEMAND =>
        commandHandlerActor ! HCDCommandMessage.submitCommand(controlCommand)
        log.info("changing HCD's operational state to pointing")
        statePublisherActor ! HCDOperationalStateChangeMsg(HCDOperationalState.PointingDatumed)
        //statePublisherActor ! publishCurrentPosition()
        Started(controlCommand.runId)
      case Commands.SET_SIMULATION_MODE =>
        execSimModeCmdAndSendResp(controlCommand)
    }
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    // log.info(msg = s"*** Received position Demands : ${controlCommand} to HCD at : ${System.currentTimeMillis()} *** ")
    val hcdRecTime      = Instant.now
    val setup           = Setup(Prefix(Subsystem.MCS.toString), CommandName(Commands.POSITION_DEMANDS), None)
    val azPosParam      = controlCommand.paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_AZ_POS).get
    val elPosParam      = controlCommand.paramSet.find(msg => msg.keyName == EventConstants.POINTING_KERNEL_EL_POS).get
    val timeStamp       = controlCommand.paramSet.find(msg => msg.keyName == EventConstants.TIMESTAMP).get
    val assemblyRecTime = controlCommand.paramSet.find(msg => msg.keyName == EventConstants.ASSEMBLY_RECEIVAL_TIME).get
    val hcdRecParam     = EventConstants.HcdReceivalTime_Key.set(hcdRecTime)
    val cmd             = setup.add(azPosParam).add(elPosParam).add(timeStamp).add(assemblyRecTime).add(hcdRecParam)
    positionDemandActor ! cmd
  }

  override def onShutdown(): Future[Unit] = Future {
    log.info(msg = "Shutting down MCS HCD")
    zeroMQProtoActor ! Disconnect()
    lifeCycleActor ! ShutdownMsg()
    statePublisherActor ! StateChangeMsg(HCDLifeCycleState.Off, HCDOperationalState.Disconnected)
  }

  override def onGoOffline(): Unit = {
    log.info(msg = "MCS HCD going offline")
  }

  override def onGoOnline(): Unit = {
    log.info(msg = "MCS HCD going online")
  }

}
