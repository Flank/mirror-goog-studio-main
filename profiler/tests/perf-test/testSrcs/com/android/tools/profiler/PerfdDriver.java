package com.android.tools.profiler;

import static org.junit.Assert.assertTrue;

import com.android.tools.fakeandroid.ProcessRunner;
import java.io.IOException;
import java.util.regex.Pattern;

public class PerfdDriver extends ProcessRunner {

    private static final String PERFD_PATH = ProcessRunner.getProcessPath("perfd.location");
    private static final Pattern SERVER_LISTENING =
            Pattern.compile("(.*)(Server listening on.*port:)(?<result>.*)");
    private int myPort = 0;

    public PerfdDriver(String configFilePath) {
        super(PERFD_PATH, "-config_file=" + configFilePath, "-profiler_test");
    }

    @Override
    public void start() throws IOException {
        super.start();
        //Block until we are in a state for the test to continue.
        String port = waitForInput(SERVER_LISTENING, ProcessRunner.NO_TIMEOUT);
        assertTrue(port != null && !port.isEmpty());
        myPort = Integer.parseInt(port);
        // Stop the process if Perfd does not bind successfully to the port.
        if (myPort == 0) {
            stop();
        }
    }

    public int getPort() {
        return myPort;
    }
}
