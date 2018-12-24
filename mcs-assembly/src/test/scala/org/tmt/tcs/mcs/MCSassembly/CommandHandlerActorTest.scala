package org.tmt.tcs.mcs.MCSassembly

import akka.actor
import akka.actor.{typed, ActorSystem}
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestInbox}
import akka.actor.typed.scaladsl.ActorContext
import csw.services.location.commons.ActorSystemFactory
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import csw.messages.commands.{CommandName, ControlCommand, Setup}
import csw.messages.params.models.{Prefix, Subsystem}
import csw.services.command.CommandResponseManager
import csw.services.command.scaladsl.CommandService
import csw.services.logging.scaladsl.{Logger, LoggerFactory}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.tmt.tcs.mcs.MCSassembly.CommandMessage.submitCommandMsg

class CommandHandlerActorTest extends FunSuite with Matchers with BeforeAndAfterAll with MockitoSugar {
  implicit val untypedSystem: ActorSystem       = ActorSystemFactory.remote()
  implicit val system: typed.ActorSystem[_]     = untypedSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(system)

  val commandResponseManager: CommandResponseManager = mock[CommandResponseManager]
  val loggerFactory                                  = mock[LoggerFactory]
  val log: Logger                                    = mock[Logger]

  val commandService                      = mock[CommandService]
  val hcdLocation: Option[CommandService] = Some(commandService)
  //val testInbox: TestInbox[ControlCommand] = TestInbox[ControlCommand]()
  /*
    This test case tests behavior of CommandHandlerActor for follow command,
    on response to follow control command, CommandHandlerActor
     will generate follow command worker actor and sends controlcommand
    message to it.
   */
  test("Process Follow command") {
    println("testing command handler actor behavior for follow command")
    val prefix                                           = Prefix(Subsystem.MCS.toString)
    val setup                                            = Setup(prefix, CommandName("Follow"), None)
    val msg                                              = submitCommandMsg(setup)
    val behaviorTestKit: BehaviorTestKit[CommandMessage] = createBehavior()
    behaviorTestKit.run(msg)
    val testInbox: TestInbox[ControlCommand] = behaviorTestKit.childInbox("FollowCommandActor")
    testInbox.expectMessage(setup)
    println("Successfully tested command handler actor behavior for follow command")
  }

  def createBehavior(): BehaviorTestKit[CommandMessage] =
    BehaviorTestKit(CommandHandlerActor.createObject(commandResponseManager, true, hcdLocation, loggerFactory))

  when(loggerFactory.getLogger).thenReturn(log)
  when(loggerFactory.getLogger(any[actor.ActorContext])).thenReturn(log)
  when(loggerFactory.getLogger(any[ActorContext[_]])).thenReturn(log)
}
