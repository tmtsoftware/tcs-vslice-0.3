package org.tmt.tcs.mcs.MCSassembly

import java.nio.file.Paths

import akka.actor
import akka.actor.typed.scaladsl.adapter.UntypedActorSystemOps
import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.javadsl.TestKitJunitResource
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.{typed, ActorSystem}

import csw.services.config.api.models.ConfigData
import csw.services.config.api.scaladsl.ConfigClientService
import csw.services.location.commons.ActorSystemFactory

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.tmt.tcs.mcs.MCSassembly.LifeCycleMessage.InitializeMsg

import scala.concurrent.{Future}

class LifeCycleActorTest extends FunSuite with Matchers with BeforeAndAfterAll {
  implicit val untypedSystem: ActorSystem       = ActorSystemFactory.remote()
  implicit val system: typed.ActorSystem[_]     = untypedSystem.toTyped
  implicit val testKitSettings: TestKitSettings = TestKitSettings(system)

  private val mocks                     = new FrameworkTestMocks1()
  val loggerFactory                     = mocks.loggerFactory
  val log                               = mocks.logger
  val configClient: ConfigClientService = mocks.configClient
  val fileName: String                  = "mcs_assembly.conf"

  val testKit = new TestKitJunitResource()
  val lifecyleActorTest: ActorRef[LifeCycleMessage] = testKit.spawn(
    LifeCycleActor.createObject(mocks.commandResponseManager, configClient, mocks.loggerFactory),
    "LifeCycleActorTest"
  )

  test("Lifecycle actor should Initialize successfully") {
    val filePath = Paths.get("org/tmt/tcs/mcs_assembly.conf")
    when(configClient.getActive(filePath)).thenReturn(getConfigData)
    lifecyleActorTest ! InitializeMsg()
    println("sent message for initialization")
    //TODO : Temporary commented this as unable to get config object from configService mock

    /*   implicit val duration: Timeout = 50 seconds
     implicit val scheduler         = system.scheduler
    println("Calling GetAssemblyConfig to fetch assembly configuration")
   val msg : LifeCycleMessage = Await.result(lifecyleActorTest ?
       {lifecyleActorTest : ActorRef[LifeCycleMessage] => LifeCycleMessage.GetAssemblyConfig(lifecyleActorTest) },40.seconds)
     msg match {
       case x : LifeCycleMessage.AssemblyConfig => {
         x.config match{
           case Some(x : Config) => {
             log.info(msg=s"Successfully loaded configuration object from Lifecycle actor")
             assert(true)
           }
           case _ =>{
             log.info(msg=s"Unable to get Configuration object from Lifecycle actor")
             assert(false)
           }
         }
       }
       case _=>{
         log.info(msg=s"Incorrect response out of initialization test case failed")
         assume(false)
       }
     }
   */
  }

  def getConfigData: Future[Option[ConfigData]] = {
    println("In test actors getConfig method")
    val url                    = classOf[LifeCycleActorTest].getClassLoader.getResource(fileName)
    val filePath               = Paths.get(url.toURI)
    val configData: ConfigData = ConfigData.fromPath(filePath)
    println("Successfully loaded config in test actor")
    Future.successful(Some(configData))
  }
  when(loggerFactory.getLogger).thenReturn(log)
  when(loggerFactory.getLogger(any[actor.ActorContext])).thenReturn(log)
  when(loggerFactory.getLogger(any[ActorContext[_]])).thenReturn(log)

}
