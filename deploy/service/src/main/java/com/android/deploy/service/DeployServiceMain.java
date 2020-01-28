/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.deploy.service;

import com.android.ddmlib.Log;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Blocking runner for creating a {@link DeployServer}. */
public class DeployServiceMain {

    /**
     * Blocking main function that starts a {@link DeployServer} if the required args are not
     * specified a message is printed and the program is exited.
     *
     * @param args The arguments required are port [port number to bind to] adbPath [path to adb
     *     executable]
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        // TODO (gijosh): Move args to gflags, first need to make iml module.
        Map<String, String> argsMap = mapArgs(args);
        if (!argsMap.containsKey("port") || !argsMap.containsKey("adbPath")) {
            printUsage();
            return;
        }
        int port = Integer.parseInt(argsMap.get("port"));
        String adbPath = argsMap.get("adbPath");

        DeployServer deployServer = new DeployServer();
        deployServer.start(port, adbPath);
    }

    private static void printUsage() {
        Log.e("DeployService", "DeployServiceMain --port [port number] --adbPath [/path/to/adb]");
    }

    private static Map<String, String> mapArgs(String[] args) {
        Map<String, String> argsMap = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                argsMap.put(args[i].substring(2), args[++i]);
            }
        }
        return argsMap;
    }
}
