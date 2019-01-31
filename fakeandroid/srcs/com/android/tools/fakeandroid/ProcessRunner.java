package com.android.tools.fakeandroid;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessRunner {

    public static final int LONG_TIMEOUT_MS = 100000;
    public static final int SHORT_TIMEOUT_MS = 10000;
    protected String[] myProcessArgs;
    private final List<String> myInput = new ArrayList<>();
    private final List<String> myError = new ArrayList<>();
    private Process myProcess;
    private Thread myErrorListener;
    private Thread myInputListener;


    protected ProcessRunner(String... processArgs) {
        myProcessArgs = processArgs;
    }

    public static String getProcessPath(String property) {
        return System.getProperty("user.dir") + File.separator + System.getProperty(property);
    }

    public void start() throws IOException {
        myProcess = Runtime.getRuntime().exec(myProcessArgs);

        // Thread to capture the process output.
        myInputListener =
            new Thread(
                () -> {
                    listen("Input", myProcess.getInputStream(), myInput);
                });
        myInputListener.start();
        myErrorListener =
            new Thread(
                () -> {
                    listen("Error", myProcess.getErrorStream(), myError);
                });
        myErrorListener.start();
    }

    /**
     * @return true if the process is created and alive.
     */
    public boolean isAlive() {
        return myProcess != null && myProcess.isAlive();
    }

    protected int exitValue() {
        return myProcess.exitValue();
    }

    private void listen(String streamName, InputStream stream, List<String> storage) {
        try {
            InputStreamReader isr = new InputStreamReader(stream);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            String procname = myProcessArgs[0].substring(myProcessArgs[0].lastIndexOf("/") + 1);

            while (!br.ready()) {
                Thread.yield();
            }

            while ((line = br.readLine()) != null) {
                String output = String.format("[%s-%s]: %s", procname, streamName, line);
                synchronized (storage) {
                    storage.add(output);
                }
                System.out.println(output);
            }
        } catch (IOException ex) {
            // Will get stream closed exception upon completion of test.
        }
    }

    /**
     * Wait for a specific string to be retrieved from the server. This function waits forever if
     * given string statement has not been found.
     */
    public boolean waitForInput(String statement) {
        return containsStatement(myInput, statement, LONG_TIMEOUT_MS);
    }

    public boolean waitForInput(String statement, int timeout) {
        return containsStatement(myInput, statement, timeout);
    }

    public boolean waitForError(String statement, int timeout) {
        return containsStatement(myError, statement, timeout);
    }

    /**
     * @param statement that defines a pattern to match in the output. The pattern should define a
     *     group named [result] as the returned element from the input. Example Line: [art-input]
     *     profiler.service.address=127.0.0.1:34801 Pattern:
     *     (.*)(profiler.service.address=)(?<result>.*) Return: 127.0.0.1:34801
     * @return The value found in the result named group, or null if no value found.
     */
    public String waitForInput(Pattern statement) {
        return containsStatement(myInput, statement, LONG_TIMEOUT_MS);
    }

    public String waitForInput(Pattern statement, int timeout) {
        return containsStatement(myInput, statement, timeout);
    }

    private boolean containsStatement(List<String> storage, String statement, int timeout) {
        return containsStatement(
                        storage, Pattern.compile("(.*)(?<result>" + statement + ")(.*)"), timeout)
                != null;
    }

    private String containsStatement(List<String> storage, Pattern statement, int timeout) {
        boolean notFound = true;
        final long SLEEP_TIME_MS = 100;
        long time = System.currentTimeMillis();
        try {
            while (notFound) {
                synchronized (storage) {
                    for (int i = storage.size() - 1; i >= 0; i--) {
                        Matcher matcher = statement.matcher(storage.get(i));
                        if (matcher.matches()) {
                            return matcher.group("result");
                        }
                    }
                }
                if (System.currentTimeMillis() - time > timeout + SLEEP_TIME_MS) {
                    break;
                }
                Thread.sleep(SLEEP_TIME_MS);
            }

        } catch (InterruptedException ex) {
        }
        System.out.println(
                "Wait Time: "
                        + (System.currentTimeMillis() - time)
                        + "ms. Pattern: "
                        + statement.pattern());
        return null;
    }

    public void stop() {
        try {
            myProcess.destroy();
            myProcess.waitFor();
            myInputListener.join(100);
            myInputListener.interrupt();
            myErrorListener.join(100);
            myErrorListener.interrupt();
        } catch (InterruptedException ex) {
            // Do nothing.
        }
    }
}
