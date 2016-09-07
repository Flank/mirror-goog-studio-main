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
import com.android.annotations.Nullable;
import com.android.ddmlib.adbserver.statechangehubs.StateChangeHandlerFactory.HandlerResult;

import java.util.HashMap;
import java.util.Map;

/**
 * This class is the base multiplexer for events that need to be propagated to existing
 * client/server connections.
 *
 * @param <FactoryType> This is the class type of the factory that will create the handlers that
 *                      this hub will serve.
 */
public abstract class StateChangeHub<FactoryType extends StateChangeHandlerFactory> {

    @NonNull
    protected final Map<StateChangeQueue, FactoryType> mHandlers = new HashMap<>();

    protected volatile boolean mStopped = false;

    /**
     * Cleanly shuts down the hub and closes all existing connections.
     */
    public void stop() {
        synchronized (mHandlers) {
            mStopped = true;
            mHandlers.forEach((stateChangeQueue, changeHandlerFactory) -> stateChangeQueue
                    .add(() -> new HandlerResult(false)));
        }
    }

    @Nullable
    public StateChangeQueue subscribe(@NonNull FactoryType handlerFactory) {
        synchronized (mHandlers) {
            if (mStopped) {
                return null;
            }

            StateChangeQueue queue = new StateChangeQueue();
            mHandlers.put(queue, handlerFactory);
            return queue;
        }
    }

    public void unsubscribe(@NonNull StateChangeQueue queue) {
        synchronized (mHandlers) {
            assert mHandlers.containsKey(queue);
            mHandlers.remove(queue);
        }
    }
}
