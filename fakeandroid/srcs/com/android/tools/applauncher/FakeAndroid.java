/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.tools.applauncher;

import android.app.Activity;
import android.app.ActivityThread;
import android.tools.SimpleWebServer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Main class that mimics the android framework. This class handles setting up a communication
 * socket for the test framework to issue commands to the android framework. This class also is
 * responsible for loading dex files and initializing the android activity.
 */
public class FakeAndroid implements SimpleWebServer.RequestHandler {

    private String myAttachAgentPath = null;
    private List<ClassLoader> myClassLoaders = new ArrayList<>();

    // For the most part, the context class loader will be the class loader that loaded the dex
    // file. There are some rare instances such process-sharing applications where this is not
    // the case. However, we probably don't need complete re-implementation of the Android
    // Framework  here.
    private ClassLoader currentActivityClassLoader = null;

    public static void main(String[] args) {
        FakeAndroid app = new FakeAndroid();
        SimpleWebServer webServer = new SimpleWebServer(app);
        webServer.start();
        app.block();

        //Prevent the test from shutting down to early.
        webServer.join();
    }

    private synchronized void block() {
        try {
            wait();

            ClassLoader curClassLoader = null;
            Thread curThread = null;
            if (currentActivityClassLoader != null) {
                curClassLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(currentActivityClassLoader);
            }

            // To simulate cmd activity attach-agent we need to call direclty into the VM and
            // attach the agent manually.
            Class vmDebugClazz = Class.forName("dalvik.system.VMDebug");
            Method attachAgentMethod = vmDebugClazz.getMethod("attachAgent", String.class);
            attachAgentMethod.setAccessible(true);
            attachAgentMethod.invoke(null, myAttachAgentPath);
            System.out.println("attach-agent " + myAttachAgentPath);

            if (curClassLoader != null) {
                Thread.currentThread().setContextClassLoader(curClassLoader);
            }

        } catch (InterruptedException ex) {
            System.err.println("Failed to block for webrequest: " + ex);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InvocationTargetException
                | IllegalAccessException ex) {
            System.err.println("Failed to attach agent: " + ex);
        }
    }

    /**
     * Function to load a dex into the class loader. The class loaders are loaded using this class
     * loader as a parent for the first class loader, then using the first class loader in the set
     * of loaded dex files as the parent. This is done so we can load our app dex and load the app
     * dex dependencies in the proper order.
     */
    private synchronized void loadDex(String dexPath) {
        try {
            // To load dex files, we need to load the dex using the expected class loader.
            // The art runtime uses the PathClassLoader under the dalvik namespace.
            // This project doesn't depend on the art runtime directly so we use
            // refelection to get the PathClassLoadder runtime.'
            Class clazz = Class.forName("dalvik.system.PathClassLoader");
            Constructor ctor = clazz.getConstructor(String.class, ClassLoader.class);
            ClassLoader parent =
                    myClassLoaders.size() == 0
                            ? getClass().getClassLoader()
                            : myClassLoaders.get(0);
            currentActivityClassLoader = (ClassLoader) ctor.newInstance(dexPath, parent);
            myClassLoaders.add(currentActivityClassLoader);
            System.out.println("Dex file loaded: " + dexPath);
        } catch (ClassNotFoundException
                | InstantiationException
                | InvocationTargetException
                | NoSuchMethodException
                | IllegalAccessException ex) {
            System.err.println("Failed to attach agent: " + ex);
        }
    }

    /**
     * This funciton handles the callback for the request to attach our agent. This callback is not
     * called on the main thread. This can cause potential issues while loading the agent as such we
     * notify the main thread to unblock.
     *
     * @param key value pair of query parameters.
     * @return message we want to return to the caller, in this case it is the path passed in for
     *     debugging.
     */
    @Override
    public synchronized String onRequest(List<SimpleWebServer.QueryParam> params) {
        for (SimpleWebServer.QueryParam param : params) {
            if (param.getKey().equals("attach-agent")) {
                myAttachAgentPath = param.getValue();
                notify();
            }
            if (param.getKey().equals("set-property")) {
                String[] property = param.getValue().split(",");
                String propertyKey = property[0];
                String propertyValue = property[1];
                System.setProperty(propertyKey, propertyValue);
                System.out.printf("%s=%s\n", propertyKey, propertyValue);
            }
            if (param.getKey().equals("load-dex")) {
                loadDex(param.getValue());
            }
            if (param.getKey().equals("trigger-method")) {
                String[] data = param.getValue().split(",");
                Activity activity = ActivityThread.currentActivityThread().getActivity(data[0]);
                if (activity != null) {
                    try {
                        activity.getClass().getMethod(data[1]).invoke(activity);
                    } catch (NoSuchMethodException
                            | InvocationTargetException
                            | IllegalAccessException ex) {
                        ex.printStackTrace(System.out);
                        return ex.toString();
                    }
                } else {
                    return "Activity not found: " + param.getValue();
                }
            }
            if (param.getKey().equals("launch-activity")) {
                try {
                    boolean found = false;
                    for (ClassLoader loader : myClassLoaders) {
                        try {
                            Class clazz = Class.forName(param.getValue(), true, loader);
                            if (clazz != null && Activity.class.isAssignableFrom(clazz)) {
                                ActivityThread.currentActivityThread()
                                        .putActivity((Activity) clazz.newInstance(), false);
                                found = true;
                                currentActivityClassLoader = loader;
                            }
                        } catch (ClassNotFoundException ex) {

                        }
                    }
                    if (!found) {
                        return "Class not found: " + param.getValue();
                    }
                } catch (InstantiationException | IllegalAccessException ex) {
                    return ex.toString();
                }
            }
        }
        return "SUCCESS";
    }
}
