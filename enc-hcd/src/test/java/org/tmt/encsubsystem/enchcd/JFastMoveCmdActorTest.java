package org.tmt.encsubsystem.enchcd;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import csw.command.client.CommandResponseManager;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.models.Prefix;
import csw.params.javadsl.JKeyType;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

import static org.mockito.Mockito.verify;

public class JFastMoveCmdActorTest {
    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();
    @Mock
    JCswContext cswCtx;
    @Mock
    CommandResponseManager commandResponseManager;

    JLoggerFactory jLoggerFactory;
    TestProbe<JStatePublisherActor.StatePublisherMessage> statePublisherMessageTestProbe;
    ActorRef<ControlCommand> fastMoveCmdActor;

    private Key<Double> azKey = JKeyType.DoubleKey().make("base");
    private Key<Double> elKey = JKeyType.DoubleKey().make("cap");
    private Key<String> mode = JKeyType.StringKey().make("mode");
    private Key<String> operation = JKeyType.StringKey().make("operation");

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        statePublisherMessageTestProbe = testKit.createTestProbe();
        fastMoveCmdActor = testKit.spawn(JFastMoveCmdActor.behavior(cswCtx, statePublisherMessageTestProbe.getRef()));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the HCD fastMove command actor is initialized, subsystem is also running
     * when message having valid fastMove command in it, is send
     * then it should  update command response manager that command successfully completed
     */
    @Test
    public void fastMoveCommandCompletion() throws InterruptedException {
        Setup setup = new Setup(new Prefix("enc.enc-test"), new CommandName("fastMove"), Optional.empty())
                .add(azKey.set(2.60))
                .add(elKey.set(1.4))
                .add(mode.set("fast"))
                .add(operation.set("On"));
        fastMoveCmdActor.tell(setup);
        Thread.sleep(TestConstants.ACTOR_MESSAGE_PROCESSING_DELAY);
        verify(commandResponseManager).addOrUpdateCommand(new CommandResponse.Completed(setup.runId()));
    }
}