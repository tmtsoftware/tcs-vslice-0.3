package org.tmt.encsubsystem.encassembly;

import csw.params.core.generics.Key;
import csw.params.core.models.ArrayData;
import csw.params.javadsl.JKeyType;

import java.time.Instant;

public class Constants {
    //simulates delay in processing of command
    public static final int COMMAND_PROCESSING_DELAY_MILLIS = 10;

    //name, keys, frequency for assembly and hcd state
    public static final String ASSEMBLY_STATE = "assemblyState";
    public static final  String HCD_STATE = "HcdState";
    public static final Key<String> LIFECYCLE_KEY = JKeyType.StringKey().make("LifecycleState");
    public static final Key<String> OPERATIONAL_KEY = JKeyType.StringKey().make("OperationalState");
    public static final Key<Instant> ASSEMBLY_STATE_TIME_KEY = JKeyType.TimestampKey().make("assemblyStateTimeKey");
    public  static final int ASSEMBLY_STATE_EVENT_FREQUENCY_IN_HERTZ = 20;

    //name, keys for current position
    public static final  String CURRENT_POSITION = "currentPosition";
    public static final Key<Double> BASE_POS_KEY = JKeyType.DoubleKey().make("basePosKey");
    public static final Key<Double> CAP_POS_KEY = JKeyType.DoubleKey().make("capPosKey");


    //name, keys for health
    public static final  String HEALTH = "health";
    public static final Key<String> HEALTH_KEY = JKeyType.StringKey().make("healthKey");
    public static final Key<String> HEALTH_REASON_KEY = JKeyType.StringKey().make("healthReasonKey");
    public static final Key<Instant> HEALTH_TIME_KEY = JKeyType.TimestampKey().make("healthTimeKey");

    //name, keys for diagnostic
    public static final  String DIAGNOSTIC = "diagnostic";
    public static final  Key<ArrayData<Byte>> DIAGNOSTIC_KEY = JKeyType.ByteArrayKey().make("diagnosticBytesKey");
    public static final  Key<Instant> DIAGNOSTIC_TIME_KEY = JKeyType.TimestampKey().make("diagnosticTimeKey");

    //name, keys for demand positions
    public static final String DEMAND_POSITIONS_PUBLISHER_PREFIX = "tcs.pk";
    public static final String DEMAND_POSITIONS = "encdemandpositions";
    public static final String DEMAND_POSITIONS_BASE_KEY = "ecs.base";
    public static final String DEMAND_POSITIONS_CAP_KEY = "ecs.cap";





    //keys to hold timestamps. this will hold timestamp when was the event processed by any component.
    //this is the time when ENC Subsystem generated/sampled given information
    public static final Key<Instant> SUBSYSTEM_TIMESTAMP_KEY = JKeyType.TimestampKey().make("subsystemTimestampKey");
    //this is the time when ENC HCD processed any event
    public static final Key<Instant> HCD_TIMESTAMP_KEY = JKeyType.TimestampKey().make("hcdTimestampKey");
    //this is the time when Assembly processed any event
    public static final Key<Instant> ASSEMBLY_TIMESTAMP_KEY = JKeyType.TimestampKey().make("assemblyTimestampKey");
    //this is the time when client processed any event
    public static final Key<Instant> CLIENT_TIMESTAMP_KEY = JKeyType.TimestampKey().make("clientTimestampKey");

}
