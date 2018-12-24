package org.tmt.encsubsystem.encassembly;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import csw.alarm.api.javadsl.IAlarmService;
import csw.command.api.CurrentStateSubscription;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandResponseManager;
import csw.command.client.messages.TopLevelActorMessage;
import csw.command.client.models.framework.ComponentInfo;
import csw.config.api.javadsl.IConfigClientService;
import csw.config.client.javadsl.JConfigClientFactory;
import csw.event.api.javadsl.IEventService;
import csw.framework.CurrentStatePublisher;
import csw.framework.models.JCswContext;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.models.AkkaLocation;
import csw.location.api.models.Connection;
import csw.location.api.models.LocationRemoved;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.commands.CommandIssue;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.Setup;
import csw.params.core.models.Prefix;
import csw.params.core.states.CurrentState;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;


/**
 * Tests in this class are Asynchronous. All Actor are created using akka test kit.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({JConfigClientFactory.class, AkkaLocation.class, Optional.class})
public class JEncAssemblyHandlersTest {
    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Captor
    private ArgumentCaptor<Consumer<CurrentState>> captor;

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();
    @Mock
    ActorContext<TopLevelActorMessage> ctx;

    ComponentInfo componentInfo;

    @Mock
    JCswContext cswCtx;

    @Mock
    CommandResponseManager commandResponseManager;

    @Mock
    CurrentStatePublisher currentStatePublisher;
    @Mock
    CurrentStateSubscription currentStateSubscription;

    @Mock
    ILocationService locationService;

    @Mock
    ICommandService hcdService;
  //  Optional<ICommandService> enclowCommandOpt;

    @Mock
    IEventService eventService;
    @Mock
    IAlarmService alarmService;


    @Mock
    IConfigClientService configClientApi;

    @Mock
    Connection connection;

    AkkaLocation location;

    JLoggerFactory jLoggerFactory;

    JEncAssemblyHandlers assemblyHandlers;


    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        location = PowerMockito.mock(AkkaLocation.class);
        PowerMockito.mockStatic(JConfigClientFactory.class);
        when(JConfigClientFactory.clientApi(any(), any())).thenReturn(configClientApi);
        when(ctx.getSystem()).thenReturn(testKit.system());
        when(ctx.spawnAnonymous(any(Behavior.class))).thenAnswer(i->{
            return testKit.spawn(i.getArgument(0));
        });
        JEncAssemblyBehaviorFactory factory = new JEncAssemblyBehaviorFactory();
        assemblyHandlers = (JEncAssemblyHandlers)factory.jHandlers(ctx, cswCtx);
    }

    @After
    public void tearDown() {
    }

    /**
     * State Validation(Move command) - Given assembly is in idle state,
     * when move command is submitted
     * then command should fail and invalid state command response should be returned.
     */
    @Test
    public void stateValidationTest(){
        Setup moveCommand = TestConstants.moveCommand();
        CommandResponse commandResponse=assemblyHandlers.validateCommand(moveCommand);
        assertEquals(commandResponse, new CommandResponse.Invalid(moveCommand.runId(), new CommandIssue.WrongInternalStateIssue("Assembly is not in valid operational state")));
    }


    /**
        Parameter Validation Test(Move Command ) - Given assembly is in ready to accept commands,
        when invalid move command is submitted
        then command should be rejected
     */
    @Test
    public void parameterValidationTest(){
        Setup moveCommand = TestConstants.invalidMoveCommand();
        CommandResponse commandResponse=assemblyHandlers.validateCommand(moveCommand);
        assertEquals(commandResponse,  new CommandResponse.Invalid(moveCommand.runId(), new CommandIssue.MissingKeyIssue("Move command is missing mode parameter")));
    }

    /**
     * Faulted State (HCD Connection issue) Test - Given Assembly is in ready state,
     * when connection to hcd become unavailable,
     * then submitted command should fail due to faulted state issue.
     */

    @Test
    public void hcdConnectionFaultedTest(){

        Setup moveCommand = TestConstants.moveCommand();

        assemblyHandlers.onLocationTrackingEvent(new LocationRemoved(connection));
        CommandResponse commandResponse=assemblyHandlers.validateCommand(moveCommand);
        assertEquals(commandResponse, new CommandResponse.Invalid(moveCommand.runId(), new CommandIssue.WrongInternalStateIssue("Assembly is not in valid operational state")));
    }

    /**
     * Validation Accepted(Move Command) - Given assembly is in ready state,
     * when move command is submitted
     * then validation should be successfull and accepted response should be returned.
     */
    @Test
    public void moveCommandTest(){
      //  PowerMockito.mockStatic(Optional.class);
       // when(Optional.of(any(ICommandService.class))).thenReturn(enclowCommandOpt);
       // assemblyHandlers.onLocationTrackingEvent(new LocationUpdated(location));
        assemblyHandlers.getMonitorActor().tell(new JMonitorActor.CurrentStateMessage(TestConstants.getReadyState()));
        Setup moveCommand = TestConstants.moveCommand();
        CommandResponse commandResponse=assemblyHandlers.validateCommand(moveCommand);
    }

    /**
     * Immediate Command(follow Command) - Given assembly is in ready state,
     * when follow command is submitted
     * then validation should be successful and completed response should be returned.
     */
    @Test
    public void followCommandTest() throws InterruptedException {
        //  PowerMockito.mockStatic(Optional.class);
        // when(Optional.of(any(ICommandService.class))).thenReturn(enclowCommandOpt);
        // assemblyHandlers.onLocationTrackingEvent(new LocationUpdated(location));
        Setup followCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("follow"), Optional.empty());
        when(hcdService.submit(any(), any())).thenReturn(CompletableFuture.completedFuture(new CommandResponse.Completed(followCommand.runId())));
        //assemblyHandlers.getMonitorActor().tell(new JMonitorActor.LocationEventMessage(Optional.of(hcdService)));
        assemblyHandlers.getCommandHandlerActor().tell(new JCommandHandlerActor.UpdateTemplateHcdMessage(Optional.of(hcdService)));
        assemblyHandlers.getMonitorActor().tell(new JMonitorActor.InitializedMessage());
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        CommandResponse commandResponse=assemblyHandlers.validateCommand(followCommand);
        assertEquals(commandResponse, new CommandResponse.Completed(followCommand.runId()));
    }


}