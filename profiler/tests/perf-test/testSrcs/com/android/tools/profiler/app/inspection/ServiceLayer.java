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

package com.android.tools.profiler.app.inspection;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.app.inspection.AppInspection.AppInspectionCommand;
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent;
import com.android.tools.profiler.PerfDriver;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.Transport.GetEventsRequest;
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceBlockingStub;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

class ServiceLayer implements Runnable {
    // all asynchronous operations are expected to be subseconds. so we choose a relatively small
    // but still generous timeout. If a callback didn't occur during the timeout, we fail the test.
    static final long TIMEOUT_SECONDS = 30;

    static ServiceLayer create(PerfDriver driver) {
        TransportServiceBlockingStub transportStub = driver.getGrpc().getTransportStub();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        int pid = driver.getSession().getPid();
        ServiceLayer layer = new ServiceLayer(transportStub, executor, pid);
        executor.submit(layer);
        return layer;
    }

    private final TransportServiceBlockingStub transportStub;
    private final ExecutorService executor;
    private final int pid;

    private AtomicInteger nextCommandId = new AtomicInteger(1);
    private List<AppInspectionEvent> unexpectedResponses = new ArrayList<>();
    private ConcurrentHashMap<Integer, CompletableFuture<AppInspectionEvent>> commandIdToFuture =
            new ConcurrentHashMap<>();
    private LinkedBlockingQueue<AppInspectionEvent> events = new LinkedBlockingQueue<>();

    private ServiceLayer(
            TransportServiceBlockingStub transportStub, ExecutorService executor, int pid) {
        this.transportStub = transportStub;
        this.executor = executor;
        this.pid = pid;
    }

    List<AppInspectionEvent> consumeCollectedEvents() {
        ArrayList<AppInspectionEvent> result = new ArrayList<>(events.size());
        events.drainTo(result);
        return result;
    }

    AppInspectionEvent sendCommand(AppInspectionCommand appInspectionCommand) throws Exception {
        int commandId = nextCommandId.getAndIncrement();
        Commands.Command command =
                Commands.Command.newBuilder()
                        .setType(Commands.Command.CommandType.APP_INSPECTION)
                        .setAndroidxInspectionCommand(appInspectionCommand)
                        .setStreamId(1234)
                        .setPid(pid)
                        .setCommandId(commandId)
                        .build();

        CompletableFuture<AppInspectionEvent> local = new CompletableFuture<>();
        commandIdToFuture.put(commandId, local);
        ExecuteRequest executeRequest = ExecuteRequest.newBuilder().setCommand(command).build();
        transportStub.execute(executeRequest);
        AppInspectionEvent appInspectionEvent = local.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(appInspectionEvent).isNotNull();
        return appInspectionEvent;
    }

    @Override
    public void run() {
        Iterator<Common.Event> iterator =
                transportStub.getEvents(GetEventsRequest.getDefaultInstance());
        while (iterator.hasNext()) {
            Common.Event event = iterator.next();
            if (!event.hasAppInspectionEvent()) {
                continue;
            }
            AppInspectionEvent inspectionEvent = event.getAppInspectionEvent();
            int commandId = event.getCommandId();
            if (commandId == 0) {
                events.offer(inspectionEvent);
            } else {
                handleCommandResponse(commandId, inspectionEvent);
            }
        }
    }

    private void handleCommandResponse(int commandId, AppInspectionEvent response) {
        CompletableFuture<AppInspectionEvent> localCommandCompleter =
                commandIdToFuture.get(commandId);
        if (localCommandCompleter == null) {
            unexpectedResponses.add(response);
        } else {
            localCommandCompleter.complete(response);
        }
    }

    void shutdown() {
        executor.shutdownNow();
        assertThat(unexpectedResponses).isEmpty();
        assertThat(events).isEmpty();
    }
}
