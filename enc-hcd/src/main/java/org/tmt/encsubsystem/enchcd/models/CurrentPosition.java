package org.tmt.encsubsystem.enchcd.models;

import java.time.Instant;

/**
 * This is a POJO Class to represent current position of enclosure.
 */
public class CurrentPosition {
    private double base, cap;
    private Instant time;

    public CurrentPosition(double base, double cap, Instant time) {
        this.base = base;
        this.cap = cap;
        this.time = time;
    }

    @Override
    public String toString() {
        return "CurrentPosition{" +
                "base=" + base +
                ", cap=" + cap +
                ", time=" + time +
                '}';
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

    public Instant getTime() {
        return time;
    }

    public void setTime(Instant time) {
        this.time = time;
    }
}
