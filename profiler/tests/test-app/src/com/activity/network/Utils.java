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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;

public final class Utils {
    public static String LOCAL_HOST = "http://127.0.0.1";

    public static int getAvailablePort() {
        int port = -1;
        try {
            ServerSocket socket = new ServerSocket(0);
            port = socket.getLocalPort();
            socket.close();
        } catch (IOException ex) {
            System.out.println("Unable to find available port: " + ex);
        }
        return port;
    }

    public static String getUrl(int port, String paramKey, String paramValue) {
        return String.format("%s:%d?%s=%s", LOCAL_HOST, port, paramKey, paramValue);
    }

    public static String readResponse(InputStream inputStream) {
        if (inputStream == null) {
            return null;
        }
        String response = null;
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            response = in.readLine();
        } catch (IOException ex) {
            System.out.println("Failed to read input stream");
        }
        // Close input stream no matter whether read is successful.
        try {
            inputStream.close();
        } catch (IOException ex) {
            System.out.println("Failed to close InputStream");
        }
        return response;
    }
}
