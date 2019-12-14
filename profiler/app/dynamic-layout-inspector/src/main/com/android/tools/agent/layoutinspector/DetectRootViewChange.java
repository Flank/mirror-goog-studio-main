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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AsyncTask for detecting when root view changes.
 *
 * <p>This code will handle activity changes in an application.
 */
class DetectRootViewChange extends AsyncTask<Void, Void, Void> {
    private static final long ONE_SECOND = TimeUnit.SECONDS.toMillis(1);
    private final LayoutInspectorService myService;
    private List<View> myRoots;
    private boolean myQuit;

    public DetectRootViewChange(LayoutInspectorService service) {
        myService = service;
    }

    public void start(@NonNull List<View> roots) {
        myRoots = roots;
        myQuit = false;
        execute();
    }

    public void stop() {
        myQuit = true;
    }

    @Override
    protected Void doInBackground(Void... arg0) {
        while (!myQuit) {
            try {
                Thread.sleep(ONE_SECOND);
                List<View> newRoots = myService.getRootViews();
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
                    myRoots = newRoots;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return null;
    }
}
