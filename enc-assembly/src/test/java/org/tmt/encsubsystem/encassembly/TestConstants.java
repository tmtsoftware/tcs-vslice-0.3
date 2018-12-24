package org.tmt.encsubsystem.encassembly;

import csw.params.commands.CommandName;
import csw.params.commands.Setup;
import csw.params.core.models.Prefix;
import csw.params.core.states.CurrentState;
import csw.params.core.states.StateName;
import csw.params.javadsl.JKeyType;
import csw.params.javadsl.JUnits;
import org.tmt.encsubsystem.encassembly.model.HCDState;

import java.util.Optional;

public class TestConstants {
    /**
     * Ref -
     * How to test if actor has executed some function?
     * https://stackoverflow.com/questions/27091629/akka-test-that-function-from-test-executed?answertab=oldest#tab-top
     */
    public static final int ACTOR_MESSAGE_PROCESSING_DELAY = 10000;



    public static Setup moveCommand(){
        Long[] timeDurationValue = new Long[1];
        timeDurationValue[0] = 10L;
        Setup moveCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("move"), Optional.empty())
                .add(JKeyType.StringKey().make("operation").set("On"))
                .add(JKeyType.DoubleKey().make("base").set(2.34))
                .add(JKeyType.DoubleKey().make("cap").set(5.76))
                .add(JKeyType.StringKey().make("mode").set("fast"))
                .add(JKeyType.LongKey().make("timeDuration").set(timeDurationValue, JUnits.second));

        return moveCommand;
    }

    public static Setup invalidMoveCommand(){
        Long[] timeDurationValue = new Long[1];
        timeDurationValue[0] = 10L;
        Setup moveCommand = new Setup(new Prefix("enc.enc-test"), new CommandName("move"), Optional.empty())
                .add(JKeyType.StringKey().make("operation").set("On"))
                .add(JKeyType.DoubleKey().make("base").set(2.34))
                .add(JKeyType.DoubleKey().make("cap").set(5.76))
                .add(JKeyType.LongKey().make("timeDuration").set(timeDurationValue, JUnits.second));

        return moveCommand;
    }

    public static CurrentState getReadyState(){
        CurrentState state = new CurrentState(new Prefix("tmt.tcs.ecs"), new StateName("HcdState"))
                .add(JKeyType.StringKey().make("LifecycleState").set(HCDState.LifecycleState.Running.name()))
                .add(JKeyType.StringKey().make("OperationalState").set(HCDState.OperationalState.Ready.name()));
        return state;

    }
}
