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

import android.os.AsyncTask;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AsyncTask for detecting when root view changes.
 *
 * <p>This code will handle activity changes in an application.
 */
class DetectRootViewChange extends AsyncTask<Void, Void, Void> {
    @VisibleForTesting long myCheckInterval = TimeUnit.SECONDS.toMillis(1);
    private final LayoutInspectorService myService;
    private List<View> myRoots;

    public DetectRootViewChange(LayoutInspectorService service) {
        myService = service;
    }

    public void start(@NonNull List<View> roots) {
        myRoots = roots;
        execute();
    }

    @Override
    protected Void doInBackground(Void... arg0) {
        while (!isCancelled()) {
            try {
                List<View> newRoots = new ArrayList<>(myService.getRootViews());
                if (!newRoots.equals(myRoots)) {
                    List<View> newlyAdded = new ArrayList<>(newRoots);
                    newlyAdded.removeAll(myRoots);
                    myRoots.removeAll(newRoots);
                    for (View removed : myRoots) {
                        myService.stopCapturing(removed);
                    }
                    for (View added : newlyAdded) {
                        myService.startLayoutInspector(added);
                    }
                    // If we just removed a window make sure we send an update so the new window
                    // list is captured. Otherwise if none of the other windows happen to be updated
                    // the removed window will still be shown.
                    if (!myRoots.isEmpty() && newlyAdded.isEmpty()) {
                        View root = newRoots.get(0);
                        root.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        root.invalidate();
                                    }
                                });
                    }
                    myRoots = newRoots;
                }
                Thread.sleep(myCheckInterval);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }
}
