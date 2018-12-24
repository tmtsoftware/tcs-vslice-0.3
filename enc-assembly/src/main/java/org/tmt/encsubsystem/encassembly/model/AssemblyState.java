package org.tmt.encsubsystem.encassembly.model;

public class AssemblyState {
    // what should be the initial state when hcd is just deployed, even before the onInitialize hook get called.
    public enum LifecycleState {
        Initialized, Running, Offline, Online
    }

    public enum OperationalState {
        Idle, Ready, Slewing, Tracking, InPosition, Halted, Faulted, Degraded
    }

    public AssemblyState(AssemblyState.LifecycleState lifecycleState, AssemblyState.OperationalState operationalState) {
        this.operationalState = operationalState;
        this.lifecycleState = lifecycleState;
    }

    @Override
    public String toString() {
        return "AssemblyState{" +
                "operationalState=" + operationalState +
                ", lifecycleState=" + lifecycleState +
                '}';
    }

    private AssemblyState.OperationalState operationalState;
    private AssemblyState.LifecycleState lifecycleState;

    public AssemblyState.OperationalState getOperationalState() {
        return operationalState;
    }

    public void setOperationalState(AssemblyState.OperationalState operationalState) {
        this.operationalState = operationalState;
    }

    public AssemblyState.LifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(AssemblyState.LifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }
}
