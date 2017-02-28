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

package com.android.fakeadbserver.statechangehubs;

import com.android.annotations.NonNull;
import com.android.fakeadbserver.statechangehubs.StateChangeHandlerFactory.HandlerResult;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * This queue is the primary message pump for listening threads to know when events have
 * arrived, as well as what to do for such an event.
 */
public final class StateChangeQueue {

    private LinkedBlockingQueue<Callable<HandlerResult>> mQueue = new LinkedBlockingQueue<>();

    public Callable<HandlerResult> take() throws InterruptedException {
        return mQueue.take();
    }

    public void add(@NonNull Callable<HandlerResult> handler) {
        mQueue.add(handler);
    }
}
