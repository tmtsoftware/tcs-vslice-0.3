package org.tmt.tcs.mcs.MCSassembly

import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.{typed, ActorSystem}
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, TestProbe}
import akka.actor.typed.scaladsl.{Behaviors, MutableBehavior}
import akka.util.Timeout
import csw.messages.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.messages.params.generics.{Key, KeyType, Parameter}
import csw.messages.params.models.{Prefix, Subsystem}
import csw.services.location.commons.ActorSystemFactory
import csw.services.logging.scaladsl.{GenericLoggerFactory, Logger, LoggerFactory}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import csw.services.command.scaladsl.CommandService
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class DatumActorTest1 extends FunSuite with Matchers with BeforeAndAfterAll {

  trait MutablActorMock[T] {
    this: MutableBehavior[T] =>
    protected lazy val log: Logger = MockitoSugar.mock[Logger]
  }
  implicit val untypedSystem: ActorSystem       = ActorSystemFactory.remote("test-1")
  implicit val system: typed.ActorSystem[_]     = untypedSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(system)

  val log = GenericLoggerFactory.getLogger

  private val mocks = new FrameworkTestMocks()

  private val commandServiceProbe = TestProbe[CommandService]

  def createDatumActorBehavior(): BehaviorTestKit[ControlCommand] =
    BehaviorTestKit(DatumCommandActor.createObject(mocks.commandResponseManager, Some(mocks.commandService), mocks.loggerFactory))

  override protected def afterAll(): Unit = Await.result(untypedSystem.terminate(), 5.seconds)

  test("should be able to process datum command.") {
    log.info(msg = s"Testing datum command worker actor for correct input")

    val controlCommand                                    = getControlCommand
    val datumCmdBehavior: BehaviorTestKit[ControlCommand] = createDatumActorBehavior()
    log.info(msg = s"Successfully created datumCmdBehavior testKit")
    mocks.setControlCommand(controlCommand)(10.seconds)
    datumCmdBehavior.run(controlCommand)
    log.info(msg = s"Successfully submitted control command instance to datum command actor")

  }

  private def getControlCommand(): ControlCommand = {
    val mcsHCDPrefix                  = Prefix(Subsystem.MCS.toString)
    val axesKey: Key[String]          = KeyType.StringKey.make("axes")
    val datumParam: Parameter[String] = axesKey.set("BOTH")
    val setup                         = Setup(mcsHCDPrefix, CommandName("Datum"), None).add(datumParam)
    setup
  }

}
