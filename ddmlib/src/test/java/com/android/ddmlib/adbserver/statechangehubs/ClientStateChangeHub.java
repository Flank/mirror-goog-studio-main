/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.ddmlib.adbserver.statechangehubs;

import com.android.annotations.NonNull;
import com.android.ddmlib.adbserver.ClientState;

import java.util.Collection;

/**
 * This class is the primary class that effects the changes to client states and propagates the
 * changes to existing, registered monitoring connections. It acts mainly as a multiplexer for
 * for events to existing client/server connections.
 */
public class ClientStateChangeHub extends StateChangeHub<ClientStateChangeHandlerFactory> {

    public void clientListChanged(@NonNull Collection<ClientState> clientList) {
        synchronized (mHandlers) {
            mHandlers.forEach(
                    (stateChangeQueue, clientStateChangeHandlerFactory) -> stateChangeQueue
                            .add(clientStateChangeHandlerFactory
                                    .createClientListChangedHandler(clientList)));
        }
    }

    public void logcatMessageAdded(@NonNull String message) {
        synchronized (mHandlers) {
            mHandlers.forEach(
                    ((stateChangeQueue, clientStateChangeHandlerFactory) -> stateChangeQueue
                            .add(clientStateChangeHandlerFactory
                                    .createLogcatMessageAdditionHandler(message))));
        }
    }
}
