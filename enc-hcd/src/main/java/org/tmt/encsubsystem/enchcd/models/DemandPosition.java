package org.tmt.encsubsystem.enchcd.models;

import java.time.Instant;

public class DemandPosition {
    private double base, cap;
    private Instant clientTime, assemblyTime, hcdTime;

    public DemandPosition(double base, double cap, Instant clientTime, Instant assemblyTime, Instant hcdTime) {
        this.base = base;
        this.cap = cap;
        this.clientTime = clientTime;
        this.assemblyTime = assemblyTime;
        this.hcdTime = hcdTime;
    }

    public double getBase() {
        return base;
    }

    public void setBase(double base) {
        this.base = base;
    }

    public double getCap() {
        return cap;
    }

    public void setCap(double cap) {
        this.cap = cap;
    }

    public Instant getClientTime() {
        return clientTime;
    }

    public void setClientTime(Instant clientTime) {
        this.clientTime = clientTime;
    }

    public Instant getAssemblyTime() {
        return assemblyTime;
    }

    public void setAssemblyTime(Instant assemblyTime) {
        this.assemblyTime = assemblyTime;
    }

    public Instant getHcdTime() {
        return hcdTime;
    }

    public void setHcdTime(Instant hcdTime) {
        this.hcdTime = hcdTime;
    }
}
