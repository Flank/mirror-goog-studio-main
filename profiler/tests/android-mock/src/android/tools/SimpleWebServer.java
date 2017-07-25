/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of a very basic HTTP server. The contents are loaded from the assets folder. This
 * server handles one request at a time. It only supports GET method.
 */
//TODO: Convert this to a GRPC
public class SimpleWebServer implements Runnable {

    public class QueryParam {

        private String key;
        private String value;

        public void setValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setKey(String value) {
            key = value;
        }

        public String getKey() {
            return key;
        }

        public QueryParam(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    /** The port number we listen to */
    private final int port;

    /** True if the server is running. */
    private boolean isRunning;

    /** The {@link java.net.ServerSocket} that we listen to. */
    private ServerSocket serverSocket;

    private RequestHandler handler;

    public interface RequestHandler {

        String onRequest(List<QueryParam> queryParams);
    }

    /** WebServer constructor. */
    public SimpleWebServer(int port, RequestHandler handler) {
        this.port = port;
        this.handler = handler;
    }

    /** This method starts the web server listening to the specified port. */
    public void start() {
        isRunning = true;
        new Thread(this).start();
    }

    /** This method stops the web server */
    public void stop() {
        try {
            isRunning = false;
            if (null != serverSocket) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException e) {
            System.err.println("Error closing the server socket." + e);
        }
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Test Framework Server Listening");
            while (isRunning) {
                Socket socket = serverSocket.accept();
                handle(socket);
                socket.close();
            }
        } catch (SocketException e) {
            // The server was stopped; ignore.
        } catch (IOException e) {
            System.err.println("Web server error." + e);
        }
    }

    /**
     * Respond to a request from a client.
     *
     * @param socket The client socket.
     */
    private void handle(Socket socket) throws IOException {
        BufferedReader reader = null;
        PrintStream output = null;
        try {
            List<QueryParam> argPair = new ArrayList<>();

            // Read HTTP headers and parse out the route.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("GET /")) {
                    int argStart = line.indexOf('?');
                    int argEnd = line.indexOf(' ', argStart);
                    if (argStart == -1) {
                        break;
                    }
                    String[] argList = line.substring(argStart + 1, argEnd).split("&");
                    for (String arg : argList) {
                        int keyIndex = arg.indexOf("=");
                        argPair.add(
                                new QueryParam(
                                        arg.substring(0, keyIndex), arg.substring(keyIndex + 1)));
                    }
                    break;
                }
            }

            // Output stream that we send the response to
            output = new PrintStream(socket.getOutputStream());

            // Prepare the content to send.
            if (argPair.size() == 0) {
                output.println("HTTP/1.0 500 No Query Params specified");
                output.flush();
                return;
            }
            String response = handler.onRequest(argPair);

            // Send out the content.
            output.println("HTTP/1.0 200 OK");
            output.println("Content-Type: text/text");
            output.println("Content-Length: " + response.length());
            output.println();
            output.println(response);
            output.flush();
        } finally {
            if (null != output) {
                output.close();
            }
            if (null != reader) {
                reader.close();
            }
        }
    }
}
