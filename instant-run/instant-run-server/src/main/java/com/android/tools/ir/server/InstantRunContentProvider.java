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

package com.android.tools.ir.server;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

/**
 * Content Provider that abuses a quirk of early Android initialization to start the instant run
 * service very early, before Application.onCreate(): content providers get initialized before
 * Application.onCreate() is called.
 *
 * ActivityThread initializes the application by creating and binding the Application object,
 * which we don't want to replace right now, creating content providers, and _then_ calling
 * Application.onCreate(), so by starting the IR server in the ContentProvider, we initialize
 * early without risking the ANR we'd get by doing an explicit service start.
 *
 * A ContentProvider also simplifies the client code, which doesn't have to do anything special
 * (like am startservice) in order to cause the instant run server to start on
 * application startup.
 */
public final class InstantRunContentProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        if (isMainProcess()) {
            Log.i(Logging.LOG_TAG, "starting instant run server: is main process");
            Server.create(getContext());
        } else {
            Log.i(Logging.LOG_TAG, "not starting instant run server: not main process");
        }
        return true;
    }

    /**
     * Determine whether this process is the "main process" for instant run purposes.
     * @return whether we're the main process for instant run purposes
     */
    private boolean isMainProcess() {
        // TODO(dancol): unconditionally start the IR server for _every_ process and just make
        // the host decide the process to which it wants to talk.  That way, it's more regular,
        // and we don't need to schlep over to ActivityManagerService via binder just for
        // getRunningAppProcesses().

        boolean isMainProcess = false;
        if (AppInfo.applicationId != null) {
            boolean foundPackage = false;
            int pid = Process.myPid();
            ActivityManager manager = (ActivityManager) getContext().getSystemService(
                Context.ACTIVITY_SERVICE);
            for (RunningAppProcessInfo processInfo : manager.getRunningAppProcesses()) {
                if (AppInfo.applicationId.equals(processInfo.processName)) {
                    foundPackage = true;
                    if (processInfo.pid == pid) {
                        isMainProcess = true;
                        break;
                    }
                }
            }
            if (!isMainProcess && !foundPackage) {
                // If for some reason we didn't even find the main package, consider this
                // process the main process anyway. This safeguards against apps doing
                // strange things with the process name.
                isMainProcess = true;
                Log.w(
                        Logging.LOG_TAG,
                        "considering this process main process:"
                                + "no process with this package found?!");
            }
        }
        return isMainProcess;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("not a real content provider");
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException("not a real content provider");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("not a real content provider");
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        throw new UnsupportedOperationException("not a real content provider");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("not a real content provider");
    }

}
