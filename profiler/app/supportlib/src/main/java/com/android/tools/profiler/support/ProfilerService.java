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
package com.android.tools.profiler.support;

import android.os.Debug;
import com.android.tools.profiler.support.profilers.EventProfiler;
import com.android.tools.profiler.support.profilers.MemoryProfiler;
import com.android.tools.profiler.support.profilers.ProfilerComponent;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused") // This class is used via instrumentation
public class ProfilerService {

    private static ProfilerService sInstance;
    private final List<ProfilerComponent> mComponents;
    private static final String DEFAULT_SERVICE_ADDRESS = "127.0.0.1:12389";

    static {
        /**
         * Force the GC to run because Debug.startAllocCounting() is being called after the
         * application has already started. If GC isn't called, then it's very possible that
         * the subsequent GC of surplus live objects can cause the free count to exceed the
         * alloc count. Obviously, allocations in other threads can still cause phenomenon,
         * but at a much lower rate.
         */
        Runtime.getRuntime().gc();
        Debug.startAllocCounting();
        System.loadLibrary("supportjni");
    }

    /**
     * @param serviceAddress the IP address used to connect to perfd.
     * @param unifiedPipeline whether the profiler should use the new pipeline.
     */
    private native void initializeNative(String serviceAddress, boolean unifiedPipeline);

    /**
     * Initialization method called multiple times from many entry points in the application. Not
     * thread safe so, when instrumented, it needs to be added in the main thread.
     *
     * @param serviceAddressProperty to pull the the IP address from and used to connect to perfd.
     *     This adds an additional property to the property namespace because the service address is
     *     required to be configurable for test to run in parallel.
     */
    public static void initialize(String serviceAddressProperty, boolean unifiedPipeline) {
        if (sInstance != null) {
            return;
        }
        sInstance =
                new ProfilerService(
                        System.getProperty(serviceAddressProperty, DEFAULT_SERVICE_ADDRESS),
                        unifiedPipeline);
    }

    public ProfilerService(String serviceAddress, boolean unifiedPipeline) {
        // Use 0 to indicate that the service address is of type ip address and not
        // an abstract socket.
        initializeNative(serviceAddress, unifiedPipeline);
        mComponents = new ArrayList<ProfilerComponent>();
        mComponents.add(new EventProfiler());
        mComponents.add(new MemoryProfiler(true));

        // TODO handle shutdown properly
    }
}
