package com.android.tools.fakeandroid;

import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FakeAndroidDriver extends ProcessRunner {

    private static final String APP_LISTENING = "Test Framework Server Listening: ";
    private static final String ART_PATH = ProcessRunner.getProcessPath("art.location");
    private int myCommunicationPort;
    private final String myAddress;

    public FakeAndroidDriver(String address, int debuggerPort, String extraArtFlag, String[] env) {
        List<String> args = new ArrayList<>();

        args.add("bash");
        args.add(ART_PATH);
        args.add("--64");
        args.add("--verbose");
        {
            StringBuilder libraryPaths = new StringBuilder();
            libraryPaths.append(ProcessRunner.getProcessPath("art.lib64.location"));
            for (String path : resolvePropertyPaths("app.libs")) {
                libraryPaths.append(':');
                libraryPaths.append(path);
            }
            args.add("-Djava.library.path=" + libraryPaths.toString());
        }
        args.add("-cp");
        args.add(ProcessRunner.getProcessPath("perfa.dex.location"));
        args.add(
                "-Xbootclasspath:"
                        + ProcessRunner.getProcessPath("android-mock.dex.location")
                        + ":"
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "core-libart-hostdex.jar")
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "core-oj-hostdex.jar")
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "bouncycastle-hostdex.jar")
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "conscrypt-hostdex.jar")
                        + String.format(
                                "%s%s:",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "core-icu4j-hostdex.jar")
                        + String.format(
                                "%s%s",
                                ProcessRunner.getProcessPath("art.deps.location"),
                                "okhttp-hostdex.jar"));
        args.add("-Xcompiler-option");
        args.add("--debuggable");
        args.add(
                "-Ximage:"
                        + ProcessRunner.getProcessPath("art.boot.location")
                        + "core-core-libart-hostdex.art");

        if (debuggerPort > 0) {
            args.add(
                    "-Xplugin:"
                            + String.format(
                                    "%s/%s",
                                    ProcessRunner.getProcessPath("art.lib64.location"),
                                    "libopenjdkjvmti.so"));
            args.add(
                    "-agentpath:"
                            + String.format(
                                    "%s/%s=",
                                    ProcessRunner.getProcessPath("art.lib64.location"),
                                    "libjdwp.so")
                            + "transport=dt_socket,server=y,suspend=n,address="
                            + debuggerPort);
        }

        if (extraArtFlag != null) {
            args.add(extraArtFlag);
        }

        args.add("com.android.tools.applauncher.FakeAndroid");

        this.myAddress = address;
        myProcessArgs = args.toArray(new String[0]);
        myProcessEnv = env;
    }

    public FakeAndroidDriver(String address, int debuggerPort, String[] env) {
        this(address, debuggerPort, null, env);
    }

    public FakeAndroidDriver(String address, String[] env) {
        this(address, -1, null, env);
    }

    @Override
    public void start() throws IOException {
        super.start();
        Pattern pattern = Pattern.compile("(.*)(" + APP_LISTENING + ")(?<result>.*)");
        String port = waitForInput(pattern, ProcessRunner.LONG_TIMEOUT_MS);
        assertTrue(port != null && !port.isEmpty());
        myCommunicationPort = Integer.parseInt(port);
    }

    public int getCommunicationPort() {
        return myCommunicationPort;
    }

    public void loadDex(String dexPath) {
        sendRequest("load-dex", dexPath);
    }

    public void launchActivity(String activityClass) {
        sendRequest("launch-activity", activityClass);

        //Block until we verify the activity has been created.
        assertTrue(waitForInput("ActivityThread Created"));
    }

    public void triggerMethod(String activityClass, String methodName) {
        sendRequest("trigger-method", String.format("%s,%s", activityClass, methodName));
    }

    public void attachAgent(String loc) {
        attachAgent(loc, true);
    }

    public void attachAgent(String loc, boolean shouldSucceed) {
        sendRequest("attach-agent", loc);
        if (shouldSucceed) {
            waitForInput(String.format("%s %s", "attach-agent", loc));
        } else {
            waitForError("Failed to attach agent", SHORT_TIMEOUT_MS);
        }
    }

    public void setProperty(String propertyKey, String propertyValue) {
        sendRequest("set-property", String.format("%s,%s", propertyKey, propertyValue));
        waitForInput(String.format("%s=%s", propertyKey, propertyValue));
    }

    /**
     * Given a property which evaluates to zero or more relative paths to files (separated by ':'),
     * return the their absolute paths in a list.
     */
    private static List<String> resolvePropertyPaths(String propertyKey) {
        String relativePaths = System.getProperty(propertyKey);
        if (relativePaths == null) {
            return Collections.emptyList();
        }

        List<String> absolutePaths = new ArrayList<>();
        for (String path : relativePaths.split(":")) {
            File file = new File(ProcessRunner.getProcessPathRoot() + path);
            absolutePaths.add(file.getParent());
        }
        return absolutePaths;
    }

    private void sendRequest(String request, String value) {
        try {
            URL url =
                new URL(
                    String.format(
                        "http://%s:%s?%s=%s&",
                        myAddress,
                        myCommunicationPort,
                        request,
                        value));
            URLConnection conn = url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            assertEquals("SUCCESS", in.readLine());
            in.close();
        } catch (IOException ex) {
            Assert.fail(String.format("Failed to send request (%s): %s", request, ex));
        }
    }
}
