package com.android.tools.profiler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import org.junit.Assert;

public class FakeAndroidDriver extends ProcessRunner {

    private static final String ART_PATH = ProcessRunner.getProcessPath("art.location");
    private int myCommunicationPort;
    private String myAddress;

    public FakeAndroidDriver(String address, int port, int communicationPort) {
        super(
            ART_PATH,
            "--64",
            "--verbose",
            String.format("-Dservice.address=%s:%d", address, port),
            String.format("-Dapp.communication.port=%d", communicationPort),
            "-Djava.library.path="
                + ProcessRunner.getProcessPath("agent.location")
                + ":"
                + ProcessRunner.getProcessPath("perfa.dir.location"),
            "-cp",
            ProcessRunner.getProcessPath("perfa.dex.location"),
            "-Xbootclasspath:"
                + ProcessRunner.getProcessPath("android-mock.dex.location")
                + ":"
                + String.format(
                "%s%s:",
                ProcessRunner.getProcessPath("art.deps.location"),
                "core-libart-hostdex.jar")
                + String.format(
                "%s%s",
                ProcessRunner.getProcessPath("art.deps.location"),
                "core-oj-hostdex.jar"),
            "-Xcompiler-option",
            "--debuggable",
            "-Ximage:"
                + ProcessRunner.getProcessPath("art.boot.location")
                + "core-core-libart-hostdex.art",
            "com.android.tools.profiler.FakeAndroid");
        myCommunicationPort = communicationPort;
        myAddress = address;
    }

    public void loadDex(String dexPath) {
        sendRequest("load-dex", dexPath);
    }

    public void launchActivity(String activityClass) {
        sendRequest("launch-activity", activityClass);

        //Block until we verify the activity has been created.
        assertTrue(waitForInput("ActivityThread Created"));
    }

    public void setProperty(String propertyKey, String propertyValue) {
        sendRequest("set-property", String.format("%s,%s", propertyKey, propertyValue));
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
            Assert.fail("Failed to start activity: " + ex);
        }
    }
}
