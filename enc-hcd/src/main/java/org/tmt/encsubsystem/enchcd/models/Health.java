package org.tmt.encsubsystem.enchcd.models;

public class Health {
    public enum HealthType {
        GOOD, ILL, BAD, INTERLOCKED, UNKNOWN
    }

    private HealthType health;
    private String reason;
    private long time;

    public Health(HealthType health, String reason, long time) {
        this.health = health;
        this.reason = reason;
        this.time = time;
    }

    public HealthType getHealth() {
        return health;
    }

    public void setHealth(HealthType health) {
        this.health = health;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
