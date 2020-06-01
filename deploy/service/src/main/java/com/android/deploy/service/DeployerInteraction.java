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

import com.android.ddmlib.IDevice;
import com.android.tools.deployer.UIService;
import com.android.utils.ILogger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * The deployer requires a UIService to be provided. This UIService acts as a way for the Deployer
 * to communicate with the user. An example is prompting the user to re-install the APK when the APK
 * versions do not match.
 *
 * <p>Before {@link com.android.tools.deployer.DeployerRunner#run(IDevice, String[], ILogger)} is
 * called {@link #setPromptResponses(List)} should be set to clear previous run results and setup
 * prompt responses. If no prompt responses are set the default response will be false.
 */
public class DeployerInteraction implements UIService {

    private final List<String> myPrompts = new ArrayList<>();
    private final Queue<Boolean> myResponses = new LinkedList<>();
    private final List<String> myMessages = new ArrayList<>();

    public void clear() {
        myResponses.clear();
        myPrompts.clear();
        myMessages.clear();
    }

    public void setPromptResponses(List<Boolean> responses) {
        clear();
        myResponses.addAll(responses);
    }

    public List<String> getPrompts() {
        return myPrompts;
    }

    public List<String> getMessages() {
        return myMessages;
    }

    @Override
    public boolean prompt(String result) {
        myPrompts.add(result);
        if (!myResponses.isEmpty()) {
            return myResponses.poll();
        }
        return false;
    }

    @Override
    public void message(String message) {
        myMessages.add(message);
    }
}
