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

package android.os;

public class Handler {

    private final Looper mLooper;

    public Handler() {
        this(null);
    }

    public Handler(Looper looper) {
        mLooper = looper;
    }

    public boolean post(Runnable runnable) {
        if (mLooper instanceof HandlerThread.ExecutorLooper) {
            ((HandlerThread.ExecutorLooper) mLooper)
                    .getExecutor()
                    .execute(
                            () -> {
                                // have to go through dispatchMessage, because it is overridden in
                                // app inspection.
                                Message message = new Message();
                                message.callback = runnable;
                                dispatchMessage(message);
                            });
            return true;
        }
        throw new IllegalStateException(
                "Fake implementation of Handler.post works only with Looper created by "
                        + "fake implementation of HandlerThread");
    }

    public void dispatchMessage(Message msg) {
        msg.callback.run();
    }
}
