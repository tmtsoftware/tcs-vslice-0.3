package org.tmt.tcs.mcs.MCShcd.org.tmt.tcs.mcs.MCShcd.workers
import akka.actor
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, BehaviorTestKit, TestInbox, TestProbe}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.{ActorContext, MutableBehavior}
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.{ActorSystem, typed}
import csw.messages.commands.{CommandName, ControlCommand, Setup}
import csw.messages.params.models.Prefix
import csw.services.location.commons.ActorSystemFactory
import csw.services.logging.scaladsl.Logger
import org.junit.Before
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.tmt.tcs.mcs.MCShcd.Protocol.SimpleSimMsg.ProcessCommand
import org.tmt.tcs.mcs.MCShcd.Protocol.ZeroMQMessage.SubmitCommand
import org.tmt.tcs.mcs.MCShcd.Protocol.{SimpleSimMsg, ZeroMQMessage}
import org.tmt.tcs.mcs.MCShcd.workers.StartupCmdActor

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await

class StartupCmdActorTest extends FunSuite with Matchers with BeforeAndAfterAll {

  trait MutableActorMock[T] { this: MutableBehavior[T] ⇒
    protected lazy val log: Logger = MockitoSugar.mock[Logger]
  }

  implicit val untypedSystem: ActorSystem       = ActorSystemFactory.remote("startupTest")
  implicit val system: typed.ActorSystem[_]     = untypedSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(system)

  //implicit val scheduler = system.scheduler

  private val mocks = new StartupCmdMocks()

  private val loggerFactory = mocks.loggerFactory
  private val log           = mocks.log

  @Before
  def setup(): Unit                       = {}
  override protected def afterAll(): Unit = Await.result(untypedSystem.terminate(), 5.seconds)

  test("Test for simple simulator mode ") {
    val prefix                                 = Prefix("tmt.tcs.McsAssembly-StartupTest")
    val setup                                  = Setup(prefix, CommandName("Startup"), None)
    val zeroMQActor                            = TestProbe[ZeroMQMessage]()
    val simpleSim                              = TestProbe[SimpleSimMsg]()
    val simpleSimActor: ActorRef[SimpleSimMsg] = simpleSim.ref
    val actorTestKit : ActorTestKit[ControlCommand] = ActorTestKit.
    /*val behaviorTestKit: BehaviorTestKit[ControlCommand] =
      BehaviorTestKit(
        StartupCmdActor
          .create(mocks.commandResponseManager, zeroMQActor.ref, simpleSimActor, "SimpleSimulator", mocks.loggerFactory)
      )

    behaviorTestKit.run(setup)
    simpleSim.expectMessage(ProcessCommand(setup, simpleSimActor))*/
    //zeroMQActor.expectMessage(Duration.create(30, TimeUnit.SECONDS), submitCommand)
    //val childInbox: TestInbox[SimpleSimMsg] = behaviorTestKit.childInbox("$a")

    // childInbox.expectMessage(submitCommand)
  }
  when(loggerFactory.getLogger).thenReturn(log)
  when(loggerFactory.getLogger(any[actor.ActorContext])).thenReturn(log)
  when(loggerFactory.getLogger(any[ActorContext[_]])).thenReturn(log)
}
