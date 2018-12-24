package org.tmt.encsubsystem.enchcd.models;

public class HCDState {
    // what should be the initial state when hcd is just deployed, even before the onInitialize hook get called.
    public enum LifecycleState {
        Initialized, Running
    }

    public enum OperationalState {
        Idle, Ready, Following, InPosition, Faulted, Degraded
    }

    public HCDState(LifecycleState lifecycleState, OperationalState operationalState) {
        this.operationalState = operationalState;
        this.lifecycleState = lifecycleState;
    }

    private OperationalState operationalState;
    private LifecycleState lifecycleState;

    public OperationalState getOperationalState() {
        return operationalState;
    }

    public void setOperationalState(OperationalState operationalState) {
        this.operationalState = operationalState;
    }

    public LifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(LifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }
}
