package com.android.tools.tracer;

public class Manual {
    public Manual() {
        Trace.begin("manual trace");
        Trace.end();
    }

    public void customEvents() {
        Trace.begin("custom events");

        Trace.begin(1, 2, 3, "custom");
        Trace.end(1, 2, 4);

        Trace.end();
    }
}
