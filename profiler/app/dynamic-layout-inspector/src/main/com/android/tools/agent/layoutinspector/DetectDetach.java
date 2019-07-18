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
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.concurrent.TimeUnit;

/**
 * OnWindowAttachListener for detecting when the root view is being detached.
 *
 * <p>When the root is detached, wait until a new root is found. Then connect to that new root. This
 * code will handle activity changes in an application.
 */
class DetectDetach implements ViewTreeObserver.OnWindowAttachListener {
    private static final long ONE_SECOND = TimeUnit.SECONDS.toMillis(1);

    private final LayoutInspectorService myService;
    private final View myRoot;
    private final Handler handler = new Handler();
    private final Runnable checkForNewRootView =
            new Runnable() {
                @Override
                public void run() {
                    View newRoot = myService.getRootView();
                    if (newRoot != myRoot) {
                        myService.onStartLayoutInspectorCommand();
                    } else {
                        handler.postDelayed(this, ONE_SECOND);
                    }
                }
            };

    public DetectDetach(LayoutInspectorService service, View root) {
        myService = service;
        myRoot = root;
    }

    @Override
    public void onWindowAttached() {}

    @Override
    public void onWindowDetached() {
        handler.postDelayed(checkForNewRootView, ONE_SECOND);
    }
}
