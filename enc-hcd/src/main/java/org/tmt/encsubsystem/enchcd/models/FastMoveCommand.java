package org.tmt.encsubsystem.enchcd.models;

/**
 * This command is submitted to SimpleSimulator to move enclosure at specified position.
 * Example - communicator.sendCommand(new FastMoveCommand(3.2, 6.5))
 */
public class FastMoveCommand {
    private double base, cap;

    public FastMoveCommand(double base, double cap) {
        this.base = base;
        this.cap = cap;
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

    public static final class Response{
        public enum Status{
            OK, ERROR
        }

        private Status status;

        private String desc;

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }


    }
}
