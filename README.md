# TCS POC (Java, Scala, CSW 0.6.0)

This project implements a TCS-ENC Assembly and ENC HCD, TCS-MCS Assembly and MCS HCD, PK Assembly using TMT Common Software

## Subprojects

* enc-assembly - a template assembly that implements several command types, monitors state, and loads configuration

* enc-hcd - an HCD that the assembly communicates with

* MCS-assembly - a template assembly that implements several command types, monitors state, and loads configuration

* MCS-hcd - an HCD that the assembly communicates with

* pk-assembly - an assembly that talks to the tpk core libraries for mount, enclosure, m3 etc. demand generation

* MCSSubsystem - Real simulator implementation of MCS Subsystem based on 0MQ.

* tcs-client - client to send command to assemblies and subscribe to events from assemblies.

* tcs-deploy - deploy assemblies and hcd using different container configurations.

## Build and Running the POC

### Downloading the POC  
Clone or download tmtsoftware/tcs-vslice-0.3 to a directory of choice

### Building the template  
`cd tcs-vslice-0.3`  
`sbt stage`  


## Performance Measurement  
This guide is in continuation to the documentation of tcs-vsclice-0.2. Once all the steps are followed for performance measurement for other scenirios, Below measurement can be taken as well using this setup.

### Scenario I  
Single-Machine, Single Container with Simple Simulator.  

#### Step 1 - Start CSW services  
`./csw-services.sh start`  

#### JAVA 9  
As Java 1.8 does not support time capturing in microsecond, before starting any assembly PK or MCS, switch to JRE 9 by modifying PATH variable. This is required only for deployment and build should be done with java 8.  
`export PATH=/java-9-home-path-here/bin:$PATH`  

#### Step 2 - Start Container having PK + MCS + SimpleSimulator  
`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.3/tcs-deploy/target/universal/stage/bin`  
`./mcs-pk-single-container-cmd-app --local ../../../../../tcs-deploy/src/main/resources/McsPkSingleContainer.conf`  
 
#### Step 3 - Start Jconsole and connect to MCS Container process from it.
`jconsole`  

#### Step 4 - Start MCS Real Simulator 
No need to start real simulator for this scenario

#### Step 5 - Start Event generation in MCS  
By default the mode is set to simple simulator. Varify and if required Edit and rebuild mcs-main-app before executing below commands to use simple simulator mode. 

`export PATH=/java-9-home-path-here/bin:$PATH`  
`cd tcs-vsclice-0.3/tcs-client/target/universal/stage/bin`  
`./mcs-main-app`  

#### Step 7 - Start Demand generation in PK  
`cd tcs-vsclice-0.2/tcs-client/target/universal/stage/bin`  
`./pk-client-app`  


In around 15-20 min you will see measurment data is generated at location specified using environment variable 'LogFiles'. This will be in csv format.
Save the jconsole data as well.
Stop all the services and redo above steps to take another set of measurements.


