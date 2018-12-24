# TCS ENC Assembly POC (Java, CSW 0.6.0)

This project implements a TCS-ENC Assembly and ENC HCD using TMT Common Software

## Subprojects

* enc-assembly - a template assembly that implements several command types, monitors state, and loads configuration

* enc-hcd - an HCD that the assembly communicates with

* enc-deploy - for starting/deploying the Assembly and HCD, Client to submit commands to enc-assembly and log events.


### CSW Setup

#### Set up appropriate environment variables

Add the following lines to ~/.bashrc (on linux, or startup file appropriate to your linux shell):

`export interfaceName=machine interface name;`  
`export clusterSeeds=IP:5552`

The IP and interface name of your machine can be obtained by running: ifconfig

#### Download and Run CSW

Download CSW-APP to a directory of choice and extract  
https://github.com/tmtsoftware/csw/releases

Download and unzip csw app.  
For the first time start location service and configuration service using initRepo argument.  
`cd csw-apps-0.6.0/bin`  
`./csw-location-server --clusterPort 5552`  

Once location server is started, In a new terminal initialize configuration repo using  
`./bin/csw-config-server --initRepo`

If init repo does not work try deleting 'csw-config-svn' folder  
`cd /tmp`  
`rm -rf csw-config-svn`  

Now again try to initialize config repo.  

Once config server is initialized properly, later all csw services can be started or stopped using  
`./csw-services.sh start`  
`./csw-services.sh stop`  

####CSW Logs
To varify if csw services are working properly, csw logs can be check at  
`cd /tmp/csw`  

## Build and Running the Template

### Downloading the template

Clone or download tmtsoftware/tcs-vslice-0.2/enc to a directory of choice

### Building the template

`cd tcs-vslice-0.2/enc`  
`sbt stage publishLocal`  

### Populate configurations for Assembly and HCD
These Below steps needs to be done everytime cofig service is re-initialized due to any issue because initializing config server deletes all the config data.  

#### Create Assembly configuration

`cd enc-deploy/src/main/resources/`  
`curl -X  POST --data '@enc_assembly.conf' http://192.168.1.8:5000/config/org/tmt/tcs/enc/enc_assembly.conf`

#### Create HCD configuration

`cd enc-hcd/src/main/resources/`  
`curl -X  POST --data '@enc_hcd.conf' http://192.168.1.8:5000/config/org/tmt/tcs/enc/enc_hcd.conf`  

### Start the enc Assembly

`cd enc-deploy/target/universal/stage/bin`  
`./enc-container-cmd-app --local ../../../../src/main/resources/EncContainer.conf`

### Run the Client App

`cd enc-deploy/target/universal/stage/bin`  
`./enc-template-java-client`

The Client App accept user input on console. Following command can be submitted to assembly by typing their name on console.
[startup, invalidMove, move, follow, shutdown]

Or user can type 'exit' to stop client.

### Run Junit Tests
sbt test

## Examples in the ENC POC

This template shows working examples of:

1. Create typed actors for each of the internal components in the TCS architecture doc:
	Lifecycle Actor, Monitor Actor, Command Handler Actor, Event Handler Actor, State Publisher Actor

2. Move Command with parameters

	2.1 Parameter based validation inside 'onValidate'

	2.2 Subscribe to response for long running command

	2.3 FastMove command as child command to HCD

	2.4 Failing validation for invalid parameter

	2.5 State based validation using ask pattern, Accept command only if Operational state is ready.

	2.6 State transition to InPosition

3. Follow Command with parameters as Immediate command

	3.1 follow command to assembly then hcd

	3.2 Using ask pattern to implement immediate command

	3.3 State transition to Slewing

4. HCD to Assembly CurrentState publish/subscribe using Current State publisher

5. Lifecycle Commands(StartUp/Shutdown)

	5.1 Submit lifecycle command to assembly and hcd

	5.2 Transition assembly and hcd state from initialized to running and vice-versa

6. Loading and using configuration with the configuration service

7. Lifecycle and Operational states

	7.1 Transition between states

	7.2 Communicating states between actors

	7.3 Communicating states from hcd to assembly

8. Client app to submit commands to assembly

	8.1 Submit command by typing on console.

9. Other Enhancements

	9.1 Updated code to work with latest csw version (29th June).
	
	9.2 Current state name.
	
	9. Configuration usage - Loading vent opening percentage value and passing to command handler for use in command.
	
	94 Simulator -Real vs Simple(TBD).

## Assembly Junit Test Cases

### Command Handler Actor
     
     1.	Move Command - Given Assembly is running,
     when move command as message is send to CommandHandlerActor,
     then one Command Worker Actor (MoveCmdActor) should be created
     and command should be send to newly created actor to process.
     
     2. Follow Command - Given Assembly is running,
     when follow command as message is send to CommandHandlerActor,
     then one Command Worker Actor (JFollowCmdActor) should be created
     and command should be send to newly created actor to process.
     
### Lifecycle Actor
	
	1. Initialization - Given lifecycle actor is created,
     	when Initialize message is send to lifecycle actor as part of framework initialization activity,,
     	then it should load configuration using configuration service
     	and mark initialization complete.
	
	2. Startup Command - given assembly is initialized,
     	when startup command as message is send to LifecycleActor,
     	then one Command Worker Actor (JStartUpCmdActor) should be created
     	and command should be send to newly created actor to process.

	3. Shutdown Command - given assembly is initialized,
     	when shutdown command as message is send to LifecycleActor,
     	then one Command Worker Actor (JShutdownCmdActor) should be created
     	and command should be send to newly created actor to process.
	
### Command Worker Actor

	1. Follow Command - given the Assembly is running,
   	when valid follow command is send to command worker actor
     	then worker actor submit command to HCD,
     	and response is send back sender immediately.
	
	2. Startup  Command - When Assembly is initialized, subsystem is initialized
     	when valid startup command is send to command worker actor
     	then worker actor submit command to HCD and update command response in command response manager. 
	
	3. Shutdown Command - Wiven the Assembly is running, subsystem is running
     	when valid shutdown command is send to command worker actor
     	then worker actor submit command to HCD and update command response in command response manager.
	
	4. Move Command - Given the Assembly is running,
     	when valid move command is send to command worker actor
     	then worker actor create and submit sub command to HCD,
     	join sub command with actual command
     	and update command response in command response manager.
	
### Monitor Actor
	
	1. HCD Connection Failure -  Given Assembly is initialized or running or ready
     	when hcd connection is lost
     	then monitor actor should transition assembly to faulted state
	
### Assembly Handler

	1. State Validation(Move command) - Given assembly is in idle state,
       when move command is submitted
       then command should fail and invalid state command response should be returned.

    2.  Parameter Validation Test(Move Command ) - Given assembly is in ready to accept commands,
               when invalid move command is submitted
               then command should be rejected.

    3. Faulted State (HCD Connection issue) Test - Given Assembly is in ready state,
            when connection to hcd become unavailable,
            then submitted command should fail due to faulted state issue.

    4. Validation Accepted(Move Command) - Given assembly is in ready state,
            * when move command is submitted
            * then validation should be successfull and accepted response should be returned.

    5. Immediate Command(follow Command) - Given assembly is in ready state,
            * when follow command is submitted
            * then validation should be successful and completed response should be returned.
	
## HCD Junit Test Cases

### Command Handler Actor
     
     1.	Fast Move Command - Given HCD is running,
     	when fastMove command as message is send to CommandHandlerActor,
     	then one Command Worker Actor (JFastMoveCmdActor) should be created
     	and command should be send to newly created actor to process.
     
     2. Follow Command - Given HCD is running,
     	when follow command as message is send to CommandHandlerActor,
     	then one Command Worker Actor (JFollowCmdActor) should be created
     	and command should be send to newly created actor to process.
     
     3. TrackOff Command - Given HCD is running,
     	when trackOff command as message is send to CommandHandlerActor,
     	then one Command Worker Actor (JTrackOffCmdActor) should be created
     	and command should be send to newly created actor to process.
     
### Lifecycle Actor
	
	1. Initialization - Given lifecycle actor is created,
     	when Initialize message is send to lifecycle actor as part of framework initialization activity,,
     	then it should load configuration using configuration service,
     	tell state publisher actor to start publishing current states
     	and mark complete the completableFuture.
	
	2. Shutdown Hook - Given lifecycle actor is created, initialized,
     	when Shutdown message is send to lifecycle actor as part of framework shutdown activity,
     	then it should release resources, disconnect with subsystem,
     	tell state publisher actor to stop publishing current states.
	
	2. Startup Command - given HCD is initialized,
     	when startup command as message is send to LifecycleActor,
     	then one Command Worker Actor (JStartUpCmdActor) should be created
     	and command should be send to newly created actor to process

	3. Shutdown Command - given HCD is initialized,
     	when shutdown command as message is send to LifecycleActor,
     	then one Command Worker Actor (JShutdownCmdActor) should be created
     	and command should be send to newly created actor to process.
	
### Command Worker Actor

	1. Follow Command - Given the HCD follow command actor is running, subsystem is also running
     	when valid follow message having follow command in it, is send
     	then it should reply with command successfully completed and
     	state publisher actor should receive state change message.
	
	2. Startup  Command - Given the HCD is initialized, subsystem is initialized
     	when valid startup command is send
     	then command should successfully complete and state should transition to running,
     	also state publisher actor should  get state change message.
	
	3. Shutdown Command - given the HCD is running, subsystem is running
     	when valid shutdown command is send
     	then command should successfully complete and state should transition to initialized,
     	also state publisher actor should  get state change message.
	
	4. Fast Move Command - Given the HCD fastMove command actor is initialized, subsystem is also running
     	when message having valid fastMove command in it, is send
     	then it should  update command response manager that command successfully completed and
     	state publisher actor should receive state change message.
	
### State Publisher Actor
	
	1. State Change -   given hcd is initialized,
     	when state publisher actor received state change message
     	then it should publish change to assembly using current state publisher.
	
	2. Current state - given hcd is initialized,
     	when state publisher actor received publish message
     	then it should publish current position of enclosure using current state publisher

##  Documentation

### Creating Typed Actors
The template code creates Typed Actors for the following assembly subcomponents:
Lifecycle Actor, Monitor Actor, Command Handler Actor and EventPublisher Actor.  
Also actors for each command:  Move, Track, FastMove, TrackOff

#### Lifecycle Actor
The lifecycle actor contains all lifecycle related functions: functions that are performed at startup and shutdown.  Loading configuration and connecting to HCDs and other Assemblies as needed.

#### Monitor Actor
Health monitoring for the assembly.  Tracks dependency location changes and monitors health and state of the assembly.

#### Command Handler Actor
Directs submit commands to appropriate workers.  Handles onGoOnline and onGoOffline actions (for now, going offline means ignoring incoming commands)

#### Event Handler Actor
Event Handler Actor will receive Current States changes from Monitor actor, then convert them to events to publish using CSW Event Service.

#### State Publisher Actor
State Publisher Actor in HCD publishes Current State to Assembly by using Current State Publisher of CSW.
Current State can be current position of enclosure base and cap, it can be lifecycle state or operational state.

#### MoveCmdActor
This command demonstrates how command aggregation, validation and long running command can be implemented. 

FastMove command is submit to HCD.

Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;move&quot;), None).add(operation).add(az).add(el).add(mode).add(timeduration)

Parameter Types:

operation : string

az: double

el: double

mode: string

timeduration: long

#### MoveCmdActor
HCD Actor to handle incoming command

Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None).add(operation).add(az).add(el).add(mode)

Parameter Types:

 operation : string
 az: double
 el: double
 mode: string

#### FollowCmdActor
This command demonstrates immediate command implementation. In this command completion or error status is return from 'OnValidate' hook and 'OnSubmit' hook will not be called.

Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None)

#### TrackOffCmdActor
HCD Actor to handle incoming command
Setup(Prefix(&quot;tcs.encA&quot;), CommandName(&quot;follow&quot;), None)


###Analysis I5

    event.jGet(Key) method not available. This method is available in CurrentState and Command.

    DemandState - Could not use it for DemandPosition forwarding assembly to HCD.

    Passing of event/state data among actors - Event and CurrentState contains set of parameters,
       when one actor needs to pass this information to other actor what would be the best way like creating pojo for each type of information or passing set<Parameter> directly.

    CurrentState to Event transformation - any short way if one just want to create Event with same parameters as CurrentState

    Should HCD repeat validations which are already done at assembly?

