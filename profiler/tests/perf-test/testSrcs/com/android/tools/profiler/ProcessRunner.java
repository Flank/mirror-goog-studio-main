package com.android.tools.profiler;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ProcessRunner {

    private static final int DEFAULT_INPUT_TIMEOUT = 5000;
    private String[] myProcessArgs;
    private Process myProcess;
    private Thread myErrorListener;
    private Thread myInputListener;
    private List<String> myInput = new ArrayList<>();
    private List<String> myError = new ArrayList<>();


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

    public void listen(String streamName, InputStream stream, List<String> storage) {
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
     * Wait for a specific string to be retrieved from the server. This function uses a
     * default timeout and will return false if a string is not found in thst time.
     */
    public boolean waitForInput(String statement) {
        return findStatement(myInput, statement, DEFAULT_INPUT_TIMEOUT);
    }

    public boolean waitForInput(String statement, int timeout) {
        return findStatement(myInput, statement, timeout);
    }

    public boolean waitForError(String statement, int timeout) {
        return findStatement(myError, statement, timeout);
    }

    private boolean findStatement(List<String> storage, String statement, int timeout) {
        boolean notFound = true;
        final long SLEEP_TIME_MS = 100;
        long time = System.currentTimeMillis();
        try {
            while (notFound) {
                synchronized (storage) {
                    for (int i = storage.size() - 1; i >= 0; i--) {
                        if (storage.get(i).contains(statement)) {
                            return true;
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
        System.out.println("Wait Time: " + (System.currentTimeMillis() - time));
        return false;
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
