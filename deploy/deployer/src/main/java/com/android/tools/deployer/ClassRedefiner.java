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
package com.android.tools.deployer;

import com.android.tools.deploy.proto.Deploy;

/** Responsible for invoking the corresponding API to redefine a class in ART. */
public interface ClassRedefiner {
    Deploy.SwapResponse redefine(Deploy.SwapRequest request) throws DeployerException;

    /**
     * This class's role strictly to deal with the lack of RedefineClasses capabilities in O.
     *
     * <p>To avoid back-and-forth interaction between the two redefiners, we are going to do a
     * single computation of a state that represented by this class and the two redefiner will act
     * accordingly.
     *
     * <p>The drawback of this is that it is possible that the Android application's state changes
     * between the creation of this object. A breakpoint might have been triggered, a thread might
     * have been paused..etc. Such case is probably rare should it arise, either one of the definer
     * would notice it and throw an exception.
     */
    class RedefineClassSupportState {
        public final RedefineClassSupport support;

        // The name of the thread that should invoke attach agent. In mose cases, it should be main. However, if main is not in a good
        // state to load an agent, we can still rely on another thread if it finds any.
        public final String targetThread;

        public RedefineClassSupportState(RedefineClassSupport support, String targetThread) {
            this.support = support;
            this.targetThread = targetThread;
        }
    }

    enum RedefineClassSupport {
        // This state represents the debugger is able to fully perform code swap. That basically means we are at least Pie and up.
        // When we decide not to support O. We should just assume support is always FULL and delete code accordingly.
        FULL,

        // The main thread is not suspended at all. This mean we can just ask A.M. to attach agent without using the debugger at all.
        // The JDI redefiner therefor becomes no-op.
        MAIN_THREAD_RUNNING,

        // This represent the case where:
        // 1) main thread is on a breakpoint
        // 2) main thread is suspended (otherwise we should always prioritize MAIN_THREAD_RUNNING) but some other thread is on a breakpoint.
        // In both cases, the debugger will attach an agent on that thread but relies on the installer + agent server to start normally
        // and wait for it.
        NEEDS_AGENT_SERVER,

        // There is no possible ways to do a code swap.
        NONE,
    }

    RedefineClassSupportState canRedefineClass();
}
