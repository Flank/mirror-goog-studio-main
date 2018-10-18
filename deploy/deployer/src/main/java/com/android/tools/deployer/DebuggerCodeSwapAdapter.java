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
import com.sun.jdi.VirtualMachine;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * This class acts as a connection between the UI and the Deployer when debugger is attached.
 *
 * <p>The intellij.android.deploy module contains all the IntelliJ logic while the
 * android.sdktools.deployer module contains all the backend logic such as the swap request
 * protobuffer etc.
 *
 * <p>In order to perform a code swap in the debugger, that operation requires knowledge of both the
 * IntelliJ UI / Debugger as well as all the the backend stuff. This creates a circular dependencies
 * between the two modules. To break this up, the Deployer will basically set a Function to be ran
 * within this object. After that, the UI will use this project call {@link
 * #performSwap(VirtualMachine)} in the appropriate thread.
 */
public abstract class DebuggerCodeSwapAdapter {

    private final List<Integer> attachedPids = new LinkedList<>();

    private Deploy.SwapRequest request;

    protected Function<VirtualMachine, Boolean> task;

    public void setTask(Function<VirtualMachine, Boolean> task) {
        this.task = task;
    }

    public void setRequest(Deploy.SwapRequest request) {
        this.request = request;
    }

    /**
     * Specify a list of pids on the device that has a debugger attched.
     *
     * @param pid
     */
    public void addAttachedPid(int pid) {
        attachedPids.add(pid);
    }

    public List<Integer> getPids() {
        return attachedPids;
    }

    public void performSwapImpl(VirtualMachine vm) {
        new JdiBasedClassRedefiner(vm).redefine(request);
    }

    public abstract void performSwap();

    public abstract void disableBreakPoints();

    public abstract void enableBreakPoints();
}
