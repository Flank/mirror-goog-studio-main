/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.agent.layoutinspector;

import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Root view change detection.
 *
 * <p>This code will handle activity changes in an application.
 */
class DetectRootViewChange {
    private final long myCheckInterval = TimeUnit.SECONDS.toMillis(1);
    private final HandlerThread myThread;
    private final Handler myHandler;
    private final LayoutInspectorService myService;
    private final Runnable myCheckRoots = this::checkRoots;
    private final Object mySync = new Object();
    private final List<View> myRoots = new ArrayList<>();
    private boolean myServiceCancelled;
    private int myRetryLimit;

    /**
     * Start the root view change detection.
     *
     * @param service to affect
     * @param retryLimit if >= 0 stop after retryLimit times, otherwise continue until explicitly
     *     stopped.
     */
    public DetectRootViewChange(@NonNull LayoutInspectorService service, int retryLimit) {
        // ThreadWatcher accepts threads starting with "Studio:"
        myThread = new HandlerThread("Studio:LayInsp");
        myThread.start();
        myHandler = new Handler(myThread.getLooper());
        myService = service;
        myRoots.addAll(myService.getRootViews());
        myRetryLimit = retryLimit;
        myHandler.postDelayed(this::checkRoots, myCheckInterval);
    }

    public void cancel() {
        myHandler.removeCallbacksAndMessages(null);
        synchronized (mySync) {
            myServiceCancelled = true;
            myRoots.clear();
            myThread.quit();
        }
    }

    @VisibleForTesting
    Handler getHandler() {
        return myHandler;
    }

    @VisibleForTesting
    List<View> getRoots() {
        return myRoots;
    }

    private void checkRoots() {
        try {
            List<View> newRoots = new ArrayList<>(myService.getRootViews());
            if (!newRoots.equals(myRoots)) {
                List<View> newlyAdded = new ArrayList<>(newRoots);
                newlyAdded.removeAll(myRoots);
                myRoots.removeAll(newRoots);
                synchronized (mySync) {
                    if (myServiceCancelled) {
                        return;
                    }
                    for (View removed : myRoots) {
                        myService.stopCapturing(removed);
                    }
                    for (View added : newlyAdded) {
                        myService.startLayoutInspector(added);
                    }
                    // If we just removed a window without adding another, we need to trigger an
                    // update to the Studio side.
                    //  - If there are any windows left: do this by invalidating one of them.
                    //  - If no windows are left: send an empty tree event
                    if (newlyAdded.isEmpty()) {
                        if (!newRoots.isEmpty()) {
                            View root = newRoots.get(0);
                            root.post(root::invalidate);
                        } else {
                            myService.sendEmptyRootViews();
                        }
                    }
                }
                myRoots.clear();
                myRoots.addAll(newRoots);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (myRetryLimit < 0 || myRetryLimit-- > 0) {
                myHandler.postDelayed(myCheckRoots, myCheckInterval);
            }
        }
    }
}
