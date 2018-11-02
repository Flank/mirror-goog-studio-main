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
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
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
class JdiBasedClassRedefiner extends ClassRedefiner {

    private static final long DEBUGGER_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    private VirtualMachine vm;

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

    JdiBasedClassRedefiner(VirtualMachine vm) {
        this.vm = vm;
    }

    @Override
    public Deploy.SwapResponse redefine(Deploy.SwapRequest request) {
        Map<ReferenceType, byte[]> redefinitionRequest = new HashMap<>();

        for (Deploy.ClassDef redefinition : request.getClassesList()) {
            List<ReferenceType> classes = getReferenceTypeByName(redefinition.getName());
            for (ReferenceType classRef : classes) {
                redefinitionRequest.put(classRef, redefinition.getDex().toByteArray());
            }
        }

        Deploy.SwapResponse.Builder response = Deploy.SwapResponse.newBuilder();
        vm.redefineClasses(redefinitionRequest);
        response.setStatus(Deploy.SwapResponse.Status.OK);
        return response.build();
    }

    List<ReferenceType> getReferenceTypeByName(String name) {
        return vm.classesByName(name);
    }

    boolean hasRedefineClassesCapabilities() {
        return vm.canRedefineClasses();
    }
}
