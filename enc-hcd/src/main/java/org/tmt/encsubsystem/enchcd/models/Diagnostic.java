package org.tmt.encsubsystem.enchcd.models;

public class Diagnostic {
    private Byte[] dummyDiagnostic;
    private long time;

    public Diagnostic(Byte[] dummyDiagnostic, long time) {
        this.dummyDiagnostic = dummyDiagnostic;
        this.time = time;
    }

    public Byte[] getDummyDiagnostic() {
        return dummyDiagnostic;
    }

    public void setDummyDiagnostic(Byte[] dummyDiagnostic) {
        this.dummyDiagnostic = dummyDiagnostic;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
