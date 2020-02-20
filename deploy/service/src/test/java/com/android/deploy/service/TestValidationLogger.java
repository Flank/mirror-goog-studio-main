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
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class TestValidationLogger implements Log.ILogOutput {
    public class LogOutput {
        Log.LogLevel logLevel;
        String tag;
        String message;

        public LogOutput(Log.LogLevel logLevel, String tag, String message) {
            this.logLevel = logLevel;
            this.tag = tag;
            this.message = message;
        }
    }

    private Queue<LogOutput> myLogQueue = new LinkedBlockingQueue<>();

    @Override
    public void printLog(Log.LogLevel logLevel, String tag, String message) {
        myLogQueue.offer(new LogOutput(logLevel, tag, message));
    }

    @Override
    public void printAndPromptLog(Log.LogLevel logLevel, String tag, String message) {
        myLogQueue.offer(new LogOutput(logLevel, tag, message));
    }

    public boolean hasNextLine() {
        return !myLogQueue.isEmpty();
    }

    public LogOutput getNextLogLine() {
        return myLogQueue.poll();
    }
}
