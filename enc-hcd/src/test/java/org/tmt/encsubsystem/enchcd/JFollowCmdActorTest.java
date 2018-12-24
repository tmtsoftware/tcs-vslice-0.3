package org.tmt.encsubsystem.enchcd;

import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.testkit.typed.javadsl.TestProbe;
import akka.actor.typed.ActorRef;
import csw.command.client.CommandResponseManager;
import csw.framework.models.JCswContext;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.Setup;
import csw.params.core.models.Prefix;
import org.junit.*;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;

/**
 * This is an Actor Level Test.
 * <p>
 * Test case can be -
 * Handler level
 * Actor level
 * <p>
 * Actor Level -
 * Sending different message and checking if
 * it respond with a message
 * it create any child actor
 * it send message to any other actor
 * it changes its state
 * it crashes
 * <p>
 * Handler Level -
 * validation tests
 * failed validation and successful validation
 * <p>
 * command tests
 * test for immediate command
 * test for long running command
 */
public class JFollowCmdActorTest {

    @ClassRule
    public static final TestKitJunitResource testKit = new TestKitJunitResource();

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    CommandResponseManager commandResponseManager;
    @Mock
    JCswContext cswCtx;
    JLoggerFactory jLoggerFactory;
    TestProbe<JStatePublisherActor.StatePublisherMessage> statePublisherMessageTestProbe;
    ActorRef<JFollowCmdActor.FollowMessage> followCmdActor;

    @Before
    public void setUp() throws Exception {
        jLoggerFactory = new JLoggerFactory("enc-test-logger");
        statePublisherMessageTestProbe = testKit.createTestProbe();
        followCmdActor = testKit.spawn(JFollowCmdActor.behavior(cswCtx, statePublisherMessageTestProbe.getRef()));
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * given the HCD follow command actor is running, subsystem is also running
     * when valid follow message having follow command in it, is send
     * then it should reply with command successfully completed and
     * state publisher actor should receive state change message.
     */
    @Test
    public void followCommandCompletion() {
        Setup setup = new Setup(new Prefix("enc.enc-test"), new CommandName("follow"), Optional.empty());
        TestProbe<JCommandHandlerActor.ImmediateResponseMessage> immediateResponseMessageTestProbe = testKit.createTestProbe();
        followCmdActor.tell(new JFollowCmdActor.FollowCommandMessage(setup, immediateResponseMessageTestProbe.getRef()));
        //checking if command completed message is received by test probe(replyTo actor)
         immediateResponseMessageTestProbe.expectMessage(new JCommandHandlerActor.ImmediateResponseMessage(new CommandResponse.Completed(setup.runId())));
        //checking if statePublisher Actor received state change message
        statePublisherMessageTestProbe.expectMessage(new JStatePublisherActor.FollowCommandCompletedMessage());
    }
}