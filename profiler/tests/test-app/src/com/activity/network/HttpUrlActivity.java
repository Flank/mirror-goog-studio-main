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

import android.app.Activity;
import android.tools.SimpleWebServer;
import android.tools.SimpleWebServer.QueryParam;
import android.tools.SimpleWebServer.RequestHandler;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

public final class HttpUrlActivity extends Activity {
    public HttpUrlActivity() {
        super("HttpUrl Activity");
    }

    private static final String GET = "HttpUrlGet";
    private static final String POST = "HttpUrlPost";
    private static final RequestHandler HANDLER =
            new RequestHandler() {
                @Override
                public String onRequest(List<QueryParam> queryParams) {
                    for (QueryParam param : queryParams) {
                        if (param.getKey().equals("activity")) {
                            if (param.getValue().equals(GET)) {
                                return GET + " SUCCESS";
                            }
                            if (param.getValue().equals(POST)) {
                                return POST + " SUCCESS";
                            }
                        }
                    }
                    return "FAILURE";
                }
            };

    public void runGet() {
        SimpleWebServer server = startServer();
        InputStream inputStream = null;
        try {
            URL url = new URL(Utils.getUrl(server.getPort(), "activity", GET));
            URLConnection connection = url.openConnection();
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            System.out.println("Error in connection " + e.toString());
        }
        System.out.println(Utils.readResponse(inputStream));
        server.stop();
    }

    public void runPost() {
        SimpleWebServer server = startServer();
        InputStream inputStream = null;
        try {
            URL url = new URL(Utils.getUrl(server.getPort(), "activity", POST));
            URLConnection connection = url.openConnection();
            connection.setDoOutput(true);
            String requestBody = "TestRequestBody";
            connection.getOutputStream().write(requestBody.getBytes());
            inputStream = connection.getInputStream();
        } catch (IOException e) {
            System.out.println("Error in connection " + e.toString());
        }
        System.out.println(Utils.readResponse(inputStream));
        server.stop();
    }

    private static SimpleWebServer startServer() {
        SimpleWebServer server = new SimpleWebServer(Utils.getAvailablePort(), HANDLER);
        server.start();
        return server;
    }
}
