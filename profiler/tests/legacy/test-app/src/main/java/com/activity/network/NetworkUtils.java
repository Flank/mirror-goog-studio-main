/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.activity.network;

import android.tools.SimpleWebServer;
import android.tools.SimpleWebServer.RequestHandler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

// API methods are public to express intention, even if package private technically works
@SuppressWarnings("WeakerAccess")
public final class NetworkUtils {
    @SuppressWarnings("FieldCanBeLocal") // Constant, for readability
    private static final String LOCAL_HOST = "http://127.0.0.1";
    private static final long SLEEP_MS = 200;

    public interface ServerTest {
        void runWith(SimpleWebServer server) throws Exception;
    }

    /**
     * Start a new server to run a single test against. The test can then send requests to it.
     *
     * <p>The server will automatically be started before the test begins and stopped on its
     * completion. As server starting is in its own thread, test waits until server started.
     */
    public static void runWithServer(RequestHandler handler, ServerTest serverTest)
            throws Exception {
        SimpleWebServer server = new SimpleWebServer(handler);
        server.start();
        try {
            while (server.getPort() == 0) {
                Thread.sleep(SLEEP_MS);
            }
            serverTest.runWith(server);
        } finally {
            server.stop();
        }
    }

    public static String getUrl(int port, String paramKey, String paramValue) {
        return String.format("%s:%d?%s=%s", LOCAL_HOST, port, paramKey, paramValue);
    }

    /**
     * Consume an {@link InputStream} associated with a network response and print it to {@link
     * System#out}.
     *
     * <p>Some of our network instrumentation code relies on the user's code actually consuming the
     * response in order to generate profiling events for them.
     *
     * <p>Also, writing to {@link System#out} allows this test app to communicate with the test
     * runner, which will look through the response output for expected strings.
     */
    public static void printAndCloseResponse(InputStream inputStream) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        try {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.print(line);
            }
        } finally {
            System.out.println();
            in.close();
        }
    }
}
