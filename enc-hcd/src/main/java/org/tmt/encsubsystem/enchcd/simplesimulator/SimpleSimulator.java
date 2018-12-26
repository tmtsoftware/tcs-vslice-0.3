package org.tmt.encsubsystem.enchcd.simplesimulator;

import org.tmt.encsubsystem.enchcd.models.*;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * This is a simple simulator for subsystem
 *
 */
public class SimpleSimulator {

    public static final int COMMAND_PROCESSING_DELAY_MILLIS = 1;

    public static final int CURRENT_POSITION_CHANGE_DELAY= 100;
    public static final int AZ_EL_DECIMAL_PLACES= 2;
    public static final double AZ_EL_PRECISION= .01;

    private static SimpleSimulator INSTANCE;

    private CurrentPosition currentPosition;
    private Health health;
    private Diagnostic diagnostic;
    private DemandPosition demandPosition;

    private boolean following=false;

    private PrintStream printStream;

    private SimpleSimulator() {
        this.currentPosition = new CurrentPosition(0.12, 0.06, Instant.now());
        this.demandPosition = new DemandPosition(0.0,0.0, Instant.now(), Instant.now(), Instant.now());
        this.health = new Health(Health.HealthType.GOOD, "good", Instant.now().toEpochMilli());
        Byte[] dummyData = {5,6,7,8,9,5,3,2,1,2,3,5,6,7,8};
        this.diagnostic = new Diagnostic(dummyData, Instant.now().toEpochMilli());
        try {
            File file = new File("ENC_DemandPosition_SimpleSimulator_Logs_"+Instant.now().toString()+"__.txt");
            boolean created = file.createNewFile();
            System.out.println("file created - "  +created);
            this.printStream = new PrintStream(new FileOutputStream(file));
            this.printStream.println("PK publish Timestamp, Assembly receive timestamp, hcd receive timestamp, simulator receive timestamp");
            System.out.println("ok");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static SimpleSimulator getInstance(){
        if(INSTANCE ==null){
            INSTANCE= new SimpleSimulator();
        }
        return INSTANCE;
    }

    /**
     * this method simulates move command processing.
     * current Az El of Enclosure will slowly adjust towards submitted demand.
     * @param cmd
     * @return
     */
    public FastMoveCommand.Response sendCommand(FastMoveCommand cmd) {

        System.out.println("target position- " + cmd);
        CompletableFuture.runAsync(() -> {
            double diffAz = Util.diff(cmd.getBase(), currentPosition.getBase(), AZ_EL_DECIMAL_PLACES);
            double diffEl = Util.diff(cmd.getCap(), currentPosition.getCap(), AZ_EL_DECIMAL_PLACES);
            //System.out.println("diffAz - " + diffAz + " ,  diffEl - " + diffEl);
            while (diffAz != 0 || diffEl != 0) {
                if (diffAz != 0) {
                    double changeAz = ((diffAz / Math.abs(diffAz)) * AZ_EL_PRECISION);
                    //System.out.println("changeAz - " + changeAz);
                    currentPosition.setBase(Util.round(currentPosition.getBase() + changeAz, AZ_EL_DECIMAL_PLACES));
                }


                if (diffEl != 0) {
                    double changeEl = ((diffEl / Math.abs(diffEl)) * AZ_EL_PRECISION);
                    //System.out.println("changeEl - " + changeEl);
                    currentPosition.setCap(Util.round(currentPosition.getCap() + changeEl, AZ_EL_DECIMAL_PLACES));
                }

                diffAz = Util.diff(cmd.getBase(), currentPosition.getBase(), AZ_EL_DECIMAL_PLACES);
                diffEl = Util.diff(cmd.getCap(), currentPosition.getCap(), AZ_EL_DECIMAL_PLACES);
                System.out.println("diffAz - " + diffAz + " ,  diffEl - " + diffEl);
                // System.out.println("current position - " + currentPosition+"  diff in Az - " + diffAz + "   diff in El - " + diffEl);
                try {
                    Thread.sleep(CURRENT_POSITION_CHANGE_DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("target reached");
        });
        FastMoveCommand.Response response = new FastMoveCommand.Response();
        response.setDesc("Completed");
        response.setStatus(FastMoveCommand.Response.Status.OK);
        return response;
    }

    /**
     * This method simulated initialization of subsystem when startup command is submitted
     * @param cmd
     * @return
     */
    public StartupCommand.Response sendCommand(StartupCommand cmd) {
        System.out.println("startup command - " +cmd);
        StartupCommand.Response response= new StartupCommand.Response();
        response.setDesc("Completed");
        response.setStatus(StartupCommand.Response.Status.OK);
        return response;
    }
    /**
     * This method simulated shutdown of subsystem when shutdown command is submitted
     * @param cmd
     * @return
     */
    public ShutdownCommand.Response sendCommand(ShutdownCommand cmd) {
        System.out.println("shutdown command - " +cmd);
        ShutdownCommand.Response response= new ShutdownCommand.Response();
        response.setDesc("Completed");
        response.setStatus(ShutdownCommand.Response.Status.OK);
        return response;
    }
    /**
     * This method simulated follow command execution in subsystem
     * @param cmd
     * @return
     */
    public FollowCommand.Response sendCommand(FollowCommand cmd) {
        System.out.println("follow command - " +cmd);
        this.currentPosition = new CurrentPosition(demandPosition.getBase(), demandPosition.getCap(), Instant.now());
        FollowCommand.Response response= new FollowCommand.Response();
        response.setDesc("Completed");
        response.setStatus(FollowCommand.Response.Status.OK);
        return response;
    }


    /**
     * This method provides current position of enc subsystem to hcd.
     * @return
     */
    public CurrentPosition getCurrentPosition() {
        currentPosition.setTime(Instant.now());
        return currentPosition;
    }

    /**
     * This method provides health of enc subsystem to hcd.
     * @return
     */
    public Health getHealth() {
        health.setTime(Instant.now().toEpochMilli());
        return health;
    }
    /**
     * This method provides diagnostic of enc subsystem to hcd.
     * @return
     */
    public Diagnostic getDiagnostic() {
        diagnostic.setTime(Instant.now().toEpochMilli());
        return diagnostic;
    }

    public void setDemandPosition(DemandPosition demandPosition) {
        this.demandPosition = demandPosition;
        Instant subsystemTime = Instant.now();
        long clientToAssemblyDuration = Duration.between(demandPosition.getClientTime(), demandPosition.getAssemblyTime()).toNanos();
        long clientToHcdDuration = Duration.between(demandPosition.getClientTime(), demandPosition.getHcdTime()).toNanos();
        long clientTosubsystemDuration = Duration.between(demandPosition.getClientTime(), subsystemTime).toNanos();
        long assemblyToHcdDuration = Duration.between(demandPosition.getAssemblyTime(), demandPosition.getHcdTime()).toNanos();
        //this.printStream.println("Event=Demand Position "+", "+ "Base="+demandPosition.getBase() + ", "+ "Cap="+demandPosition.getCap() + ", " + "Pointing Kernel Time="+demandPosition.getClientTime() + ", " +"Assembly Time=" + demandPosition.getAssemblyTime()+ ", " +"HCD Time="+ demandPosition.getHcdTime() + ", " + "Subsystem Time ="+ subsystemTime + ", " + "Duration(PKA to ENCA in ms)= "+ clientToAssemblyDuration + ", " + "Duration(PKA to HCD in ms)= "+ clientToHcdDuration + ", " + "Duration(PKA to Subsystem in ms) = "+ clientTosubsystemDuration + ", " + "Duration(Assembly to HCD in ms)= " + assemblyToHcdDuration);
        this.printStream.println(demandPosition.getClientTime() + ", " + demandPosition.getAssemblyTime()+ ", " + demandPosition.getHcdTime() + ", " + subsystemTime);
      //  System.out.println("Event=Demand Position "+", "+ "Base="+demandPosition.getBase() + ", "+ "Cap="+demandPosition.getCap() + ", " + "Pointing Kernel Time="+demandPosition.getClientTime() + ", " +"Assembly Time=" + demandPosition.getAssemblyTime()+ ", " +"HCD Time="+ demandPosition.getHcdTime() + ", " + "Subsystem Time ="+ subsystemTime + ", " + "Duration(PKA to ENCA in ms)= "+ clientToAssemblyDuration + ", " + "Duration(PKA to HCD in ms)= "+ clientToHcdDuration + ", " + "Duration(PKA to Subsystem in ms) = "+ clientTosubsystemDuration + ", " + "Duration(Assembly to HCD in ms)= " + assemblyToHcdDuration);

    }
}
