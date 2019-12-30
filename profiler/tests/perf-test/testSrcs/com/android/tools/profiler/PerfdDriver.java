package com.android.tools.profiler;

import com.android.tools.fakeandroid.ProcessRunner;
import java.io.IOException;
import java.util.regex.Pattern;
import org.junit.Assert;

public class PerfdDriver extends ProcessRunner {

    private static final String PERFD_PATH = ProcessRunner.getProcessPath("perfd.location");
    private static final Pattern SERVER_LISTENING =
            Pattern.compile("(.*)(Server listening on.*port:)(?<result>.*)");
    private int myPort = 0;

    public PerfdDriver(String configFilePath) {
        super(PERFD_PATH, "--config_file=" + configFilePath, "--profiler_test=true");
    }

    @Override
    public void start() throws IOException {
        myPort = 0;
        super.start();
        if (!isAlive()) {
            Assert.fail("Failed to start daemon. Exit code: " + exitValue());
        }
        //Block until we are in a state for the test to continue.
        String port = waitForInput(SERVER_LISTENING, SHORT_TIMEOUT_MS);
        if (port == null || port.isEmpty()) {
            Assert.fail("Failed to start daemon." + (isAlive() ? "" : "Exit code: " + exitValue()));
        }
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
