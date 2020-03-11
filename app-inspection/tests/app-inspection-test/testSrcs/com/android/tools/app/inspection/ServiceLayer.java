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

package com.android.tools.app.inspection;

import static com.google.common.truth.Truth.assertThat;

import androidx.annotation.NonNull;
import com.android.tools.app.inspection.AppInspection.AppInspectionCommand;
import com.android.tools.app.inspection.AppInspection.AppInspectionEvent;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport.ExecuteRequest;
import com.android.tools.profiler.proto.Transport.GetEventsRequest;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profiler.proto.TransportServiceGrpc.TransportServiceBlockingStub;
import com.android.tools.transport.TransportRule;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A utility class for wrapping useful app-inspection gRPC operations, spinning up a separate thread
 * to manage host / device communication.
 *
 * <p>While running, it polls the transport framework for events, which should be received after
 * calls to {@link #sendCommand(AppInspectionCommand)}. You can fetch those events using {@link
 * #sendCommandAndGetResponse(AppInspectionCommand)} instead, or by calling {@link
 * #consumeCollectedEvent()}.
 *
 * <p>{@link #shutdown()} must be called to ensure the thread can finish.
 */
final class ServiceLayer implements Runnable {
    // All asynchronous operations are expected to be subseconds. so we choose a relatively small
    // but still generous timeout. If a callback didn't occur during the timeout, we fail the test.
    static final long TIMEOUT_SECONDS = 10;

    @NonNull
    static ServiceLayer create(@NonNull TransportRule transportRule) {
        TransportServiceBlockingStub transportStub = TransportServiceGrpc.newBlockingStub(transportRule.getGrpc().getChannel());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        int pid = transportRule.getPid();
        ServiceLayer layer = new ServiceLayer(transportStub, executor, pid);
        executor.submit(layer);
        return layer;
    }

    private final TransportServiceBlockingStub transportStub;
    private final ExecutorService executor;
    private final int pid;

    private AtomicInteger nextCommandId = new AtomicInteger(1);
    private List<Common.Event> unexpectedResponses = new ArrayList<>();
    private ConcurrentHashMap<Integer, CompletableFuture<Common.Event>> commandIdToFuture =
            new ConcurrentHashMap<>();
    private LinkedBlockingQueue<Common.Event> events = new LinkedBlockingQueue<>();

    private ServiceLayer(
            @NonNull TransportServiceBlockingStub transportStub,
            @NonNull ExecutorService executor,
            int pid) {
        this.transportStub = transportStub;
        this.executor = executor;
        this.pid = pid;
    }

    boolean hasEventToCollect() {
        return events.size() > 0;
    }

    /** Pulls one event off the event queue, asserting if there are no events. */
    @NonNull
    AppInspectionEvent consumeCollectedEvent() throws Exception {
        Common.Event event = events.take();
        assertThat(event).isNotNull();
        assertThat(event.getPid()).isEqualTo(pid);
        assertThat(event.hasAppInspectionEvent()).isTrue();
        return event.getAppInspectionEvent();
    }

    /** Sends the inspection command and returns a non-null response. */
    @NonNull
    AppInspection.AppInspectionResponse sendCommandAndGetResponse(
            @NonNull AppInspectionCommand appInspectionCommand) throws Exception {
        CompletableFuture<Common.Event> local =
                commandIdToFuture.get(sendCommand(appInspectionCommand));
        Common.Event response = local.get();
        assertThat(response).isNotNull();
        assertThat(response.hasAppInspectionResponse()).isTrue();
        assertThat(response.getPid()).isEqualTo(pid);
        return response.getAppInspectionResponse();
    }

    /** Sends the inspection command and returns its generated id. */
    int sendCommand(@NonNull AppInspectionCommand appInspectionCommand) {
        int commandId = nextCommandId.getAndIncrement();
        AppInspectionCommand idAppInspectionCommand =
                appInspectionCommand.toBuilder().setCommandId(commandId).build();
        Commands.Command command =
                Commands.Command.newBuilder()
                        .setType(Commands.Command.CommandType.APP_INSPECTION)
                        .setAppInspectionCommand(idAppInspectionCommand)
                        .setStreamId(1234)
                        .setPid(pid)
                        .build();

        CompletableFuture<Common.Event> local = new CompletableFuture<>();
        commandIdToFuture.put(commandId, local);
        ExecuteRequest executeRequest = ExecuteRequest.newBuilder().setCommand(command).build();
        transportStub.execute(executeRequest);
        return commandId;
    }

    @Override
    public void run() {
        Iterator<Common.Event> iterator =
                transportStub.getEvents(GetEventsRequest.getDefaultInstance());
        while (iterator.hasNext()) {
            Common.Event event = iterator.next();
            if (event.hasAppInspectionEvent()) {
                events.offer(event);
            } else if (event.hasAppInspectionResponse()) {
                AppInspection.AppInspectionResponse inspectionResponse =
                        event.getAppInspectionResponse();
                int commandId = inspectionResponse.getCommandId();
                assertThat(commandId).isNotEqualTo(0);
                handleCommandResponse(commandId, event);
            }
        }
    }

    private void handleCommandResponse(int commandId, @NonNull Common.Event response) {
        CompletableFuture<Common.Event> localCommandCompleter = commandIdToFuture.get(commandId);
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
