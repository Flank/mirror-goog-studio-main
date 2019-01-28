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
import com.google.common.collect.Lists;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.tools.jdi.SocketAttachingConnector;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of the {@link ClassRedefiner} that invoke the Android Virtual Machine's class
 * redefinition API by using JDWP's RedefineClasses command.
 */
public class JdiBasedClassRedefiner implements ClassRedefiner {

    private static final long DEBUGGER_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    private final VirtualMachine vm;
    private final RedefineClassSupportState redefineSupportState;

    /**
     * Attach the debugger to a virtual machine.
     *
     * @param hostname Host name of the host that has the targetted device attached.
     * @param portNumber This is the port number of the socket on the host that the debugger should
     *     attach to. Generally, it should be the host port where ADB forwards to the device's JDWP
     *     port number. This can also be the port number that the {@link
     *     com.android.ddmlib.Debugger} is listening to.
     * @return JDI Virtual Machine representation of the debugger or null if connection was not
     *     successful.
     */
    static VirtualMachine attach(String hostname, int portNumber)
            throws IOException, IllegalConnectorArgumentsException {
        VirtualMachineManager manager = Bootstrap.virtualMachineManager();
        for (AttachingConnector connector : manager.attachingConnectors()) {
            if (connector instanceof SocketAttachingConnector) {
                HashMap<String, Connector.Argument> args =
                        new HashMap(connector.defaultArguments());
                args.get("timeout").setValue(String.valueOf(DEBUGGER_TIMEOUT_MS));
                args.get("hostname").setValue(hostname);
                args.get("port").setValue("" + portNumber);
                return connector.attach(args);
            }
        }
        return null;
    }

    public JdiBasedClassRedefiner(VirtualMachine vm) {
        this(vm, new RedefineClassSupportState(RedefineClassSupport.FULL, null));
    }

    public JdiBasedClassRedefiner(
            VirtualMachine vm, RedefineClassSupportState redefineSupportState) {
        this.vm = vm;
        this.redefineSupportState = redefineSupportState;
    }

    @Override
    public Deploy.SwapResponse redefine(Deploy.SwapRequest request) throws DeployerException {
        Map<ReferenceType, byte[]> redefinitionRequest = new HashMap<>();

        for (Deploy.ClassDef redefinition : request.getClassesList()) {
            List<ReferenceType> classes = getReferenceTypeByName(redefinition.getName());
            for (ReferenceType classRef : classes) {
                redefinitionRequest.put(classRef, redefinition.getDex().toByteArray());
            }
        }

        Deploy.SwapResponse.Builder response = Deploy.SwapResponse.newBuilder();
        switch (redefineSupportState.support) {
            case FULL:
                try {
                    vm.redefineClasses(redefinitionRequest);
                } catch (Throwable t) {
                    throw DeployerException.swapFailed(t.getMessage());
                }
                break;
            case MAIN_THREAD_RUNNING:
                // Nothing to do. The installer + agent will perform the swap for us.
                break;
            case NEEDS_AGENT_SERVER:
                List<ReferenceType> debugList = vm.classesByName("dalvik.system.VMDebug");
                ClassType debug = (ClassType) debugList.get(0);
                List<ThreadReference> allThreads = vm.allThreads();
                for (ThreadReference thread : allThreads) {
                    if (thread.name().equals(redefineSupportState.targetThread)) {
                        Method attachAgentMethod =
                                debug.concreteMethodByName("attachAgent", "(Ljava/lang/String;)V");
                        if (attachAgentMethod == null) {
                            // This should not happen.
                            throw DeployerException.swapFailed(
                                    "dalvik.system.VMDebug does not contain proper attachAgent method");
                        } else {
                            String agentLoc =
                                    "/data/data/"
                                            + request.getPackageName()
                                            + "/code_cache/.studio/agent-"
                                            + Version.hash()
                                            + ".so=irsocket";
                            try {
                                // The only time we are allowed to invoke a method like this is on a thread that is suspended by an event
                                // generated by itself. This is basically the same mechanism used for the debugger UI to print variable /
                                // expressions. The most likely case of this is when a thread is suspended because it arrived on a
                                // breakpoint. This will fail (with an exception that is caught below) should for cases such as a
                                // "suspend all" command.
                                debug.invokeMethod(
                                        thread,
                                        attachAgentMethod,
                                        Lists.newArrayList(vm.mirrorOf(agentLoc)),
                                        ObjectReference.INVOKE_SINGLE_THREADED);
                            } catch (Exception e) {
                                try {
                                    // TODO: We can split up the agent extraction + server so we don't need to do this.
                                    // We can tell the installer only swap and wait for us and don't install yet, we would no longer have
                                    // to have do a sleep and wait here.

                                    // Re-try once more.
                                    Thread.sleep(1000);
                                    debug.invokeMethod(
                                            thread,
                                            attachAgentMethod,
                                            Lists.newArrayList(vm.mirrorOf(agentLoc)),
                                            ObjectReference.INVOKE_SINGLE_THREADED);
                                } catch (Exception e1) {
                                    throw DeployerException.swapFailed(
                                            "Debugger attachAgent invocation failed.");
                                }
                            }
                        }
                    }
                }
                break;
            default:
                // This should not happen.
                throw DeployerException.swapFailed("Invalid Redefinition State.");
        }

        response.setStatus(Deploy.SwapResponse.Status.OK);
        return response.build();
    }

    @Override
    public RedefineClassSupportState canRedefineClass() {
        return redefineSupportState;
    }

    List<ReferenceType> getReferenceTypeByName(String name) {
        return vm.classesByName(name);
    }

    boolean hasRedefineClassesCapabilities() {
        return vm.canRedefineClasses();
    }
}
