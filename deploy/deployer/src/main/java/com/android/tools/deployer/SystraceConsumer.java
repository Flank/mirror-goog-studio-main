/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.deployer;

import com.android.utils.ILogger;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;

public class SystraceConsumer implements Trace.TraceConsumer {

    private final String filepath;
    private final ILogger logger;
    private PrintWriter writer;

    public SystraceConsumer(String filepath, ILogger logger) {
        this.filepath = filepath;
        this.logger = logger;
    }

    @Override
    public void onStart() {
        try {
            writer = new PrintWriter(filepath, "UTF-8");
            logger.info(
                    "%s",
                    "Created systrace json at location: '"
                            + Paths.get(filepath).toAbsolutePath().toString()
                            + "'");
        } catch (FileNotFoundException | UnsupportedEncodingException e) {
            throw new DeployerException(e);
        }
        writer.println("[{}");
    }

    @Override
    public void onBegin(Trace.Event event) {
        String pid = event.pid != 0 ? "Device-" + Long.toString(event.pid) : "Android Studio";
        writer.println(
                String.format(
                        ", {\"ts\" : \"%d\", \"ph\" : \"B\" , \"pid\" : \"%s\" , \"tid\" : \"%d\", \"name\" : \"%s\"}",
                        event.timestamp_ns / 1000, pid, event.tid, event.text));
    }

    @Override
    public void onEnd(Trace.Event event) {
        String pid = event.pid != 0 ? "Device-" + Long.toString(event.pid) : "Android Studio";
        writer.println(
                String.format(
                        ", {\"ts\" : \"%d\", \"ph\" : \"E\" , \"pid\" : \"%s\" , \"tid\" : \"%d\", \"name\" : \"%s\"}",
                        event.timestamp_ns / 1000, pid, event.tid, ""));
    }

    @Override
    public void onFinish() {
        writer.println("]");
        writer.close();
    }
}
