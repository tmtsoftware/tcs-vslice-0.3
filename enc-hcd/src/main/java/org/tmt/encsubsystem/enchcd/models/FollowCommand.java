package org.tmt.encsubsystem.enchcd.models;

/**
 * This class represent command submitted to subsystem to initialize it.
 */
public class FollowCommand {

    public FollowCommand() {
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
