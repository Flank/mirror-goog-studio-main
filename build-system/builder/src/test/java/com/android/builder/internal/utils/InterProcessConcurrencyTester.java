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

package com.android.builder.internal.utils;

import com.android.annotations.NonNull;
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.Assert;

/**
 * Utility class to test inter-process concurrency.
 *
 * <p>The usage and implementation of this class resemble those of {@link ConcurrencyTester}, except
 * that {@code ConcurrencyTester} supports concurrency testing for threads within the same process
 * whereas this class supports concurrency testing for threads across different processes.
 *
 * <p>See {@link ConcurrencyTester} for some concepts (e.g., method under test, action under test)
 * that are used in this class.
 */
public final class InterProcessConcurrencyTester {

    /** The running pattern of a set of actions. */
    private enum RunningPattern {

        /** All actions run concurrently. */
        CONCURRENT,

        /** All actions run sequentially. */
        SEQUENTIAL,

        /** More than one but not all actions run concurrently. */
        MIXED
    }

    @NonNull private List<Class> mMainMethodInvocationList = Lists.newLinkedList();

    @NonNull private List<String[]> mArgsList = Lists.newLinkedList();

    /**
     * Adds a new invocation of the main method under test to this {@link
     * InterProcessConcurrencyTester} instance. The {@code InterProcessConcurrencyTester} will
     * execute each invocation in a separate process and check whether the corresponding actions
     * under test meet the concurrency requirement.
     *
     * @param mainMethodInvocation the main method invocation to be executed from a new process
     */
    public void addMainMethodInvocationFromNewProcess(
            @NonNull Class mainMethodInvocation, @NonNull String[] args) {
        mMainMethodInvocationList.add(mainMethodInvocation);
        mArgsList.add(args);
    }

    /**
     * Executes the main method under test in separate processes and returns {@code true} if all the
     * actions ran concurrently, and {@code false} otherwise. Note that a {@code false} returned
     * value means that either the actions were not allowed to run concurrently (which violates the
     * concurrency requirement) or the actions took too long to start and accidentally ran
     * sequentially (although the latter case is possible, the implementation of this method makes
     * sure that it is unlikely to happen).
     */
    public void assertThatActionsCanRunConcurrently() throws IOException {
        Preconditions.checkArgument(
                mMainMethodInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");
        Assert.assertTrue(
                "Two or more actions ran sequentially"
                        + " even though all the actions were expected to run concurrently.",
                executeActionsAndGetRunningPattern() == RunningPattern.CONCURRENT);
    }

    /**
     * Executes the main method under test in separate processes and returns {@code true} if all the
     * actions ran sequentially, and {@code false} otherwise. Note that a {@code true} returned
     * value means that either the actions were not allowed to run concurrently (which meets the
     * concurrency requirement) or the actions took too long to start and accidentally ran
     * sequentially (although the latter case is possible, the implementation of this method makes
     * sure that it is unlikely to happen).
     */
    public void assertThatActionsCannotRunConcurrently() throws IOException {
        Preconditions.checkArgument(
                mMainMethodInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");
        Assert.assertTrue(
                "Two or more actions ran concurrently"
                        + " even though all the actions were expected to run sequentially.",
                executeActionsAndGetRunningPattern() == RunningPattern.SEQUENTIAL);
    }

    /**
     * Executes the main method under test in separate processes and returns the running pattern of
     * their actions.
     *
     * @return the running pattern of the actions
     */
    private RunningPattern executeActionsAndGetRunningPattern() throws IOException {
        // We use sockets to synchronize processes in a similar manner that latches synchronize
        // threads within the same process. The main process opens a ServerSocket and waits for
        // client processes to connect. When a client process starts executing an action, we let it
        // start a Socket with the server and block the client process until the server closes that
        // socket.

        // First, open the server socket
        ServerSocket serverSocket = new ServerSocket(0);
        int serverSocketPort = serverSocket.getLocalPort();

        // Execute the main methods. For each main method, we create a new thread that will launch
        // a new process which will execute the main method. The thread will block until the
        // corresponding process exits.
        List<IOExceptionRunnable> runnables = Lists.newLinkedList();
        for (int i = 0; i < mMainMethodInvocationList.size(); i++) {
            Class mainMethodInvocation = mMainMethodInvocationList.get(i);
            String[] args = mArgsList.get(i);
            runnables.add(() -> {
                launchProcess(mainMethodInvocation, args, serverSocketPort);
            });
        }
        Map<Thread, Optional<Throwable>> threads = executeRunnablesInThreads(runnables);

        int remainingActions = mMainMethodInvocationList.size();
        LinkedList<Socket> runningActions = Lists.newLinkedList();
        int maxConcurrentActions = 0;

        // To prevent the actions from *accidentally* running sequentially, when an action is going
        // to finish, we don't let it finish immediately but try waiting for the next action to
        // start. The following loop aims to "force" the actions to run concurrently. If it
        // succeeds, it means that the actions are allowed to run concurrently. If it doesn't
        // succeed, it means that either the actions are not allowed to run concurrently, or the
        // actions take too long to start.
        while (remainingActions > 0) {
            Socket startedAction = null;
            try {
                // Wait for a new action to start with a timeout
                serverSocket.setSoTimeout(2000);
                startedAction = serverSocket.accept();
            } catch (SocketTimeoutException e) {
            }
            // If a new action has started, keep waiting for more actions to start (repeat the loop)
            if (startedAction != null) {
                remainingActions--;
                runningActions.add(startedAction);
                if (runningActions.size() > maxConcurrentActions) {
                    maxConcurrentActions = runningActions.size();
                }
            } else {
                // If no other action has started and there are one or more running actions, it
                // could be either because the running actions are blocking a new action to start
                // (i.e., the actions are not allowed to run concurrently), or because the actions
                // are taking too long to start. Since we cannot distinguish these two cases, we let
                // all the running actions finish and repeat the loop.
                if (!runningActions.isEmpty()) {
                    while (runningActions.size() > 0) {
                        closeSocket(runningActions.removeFirst());
                    }
                } else {
                    // If no other action has started and there are no running actions, it
                    // means that the actions are taking too long to start. Let's quit.
                    for (Optional<Throwable> throwable : threads.values()) {
                        if (throwable.isPresent()) {
                            interruptThreadsAndProcesses(threads.keySet());
                            throw new RuntimeException(throwable.get());
                        }
                    }
                    interruptThreadsAndProcesses(threads.keySet());
                    throw new RuntimeException("Actions are taking too long to start");
                }
            }
        }

        // Let all the running actions finish
        while (runningActions.size() > 0) {
            closeSocket(runningActions.removeFirst());
        }

        // Wait for the threads and processes to finish
        waitForThreadsAndProcessesToFinish(threads);

        // Close the server socket
        serverSocket.close();

        // Determine the running pattern based on maxConcurrentActions
        assert maxConcurrentActions >= 1 && maxConcurrentActions <= runnables.size();
        if (maxConcurrentActions == 1) {
            return RunningPattern.SEQUENTIAL;
        } else if (maxConcurrentActions == runnables.size()) {
            return RunningPattern.CONCURRENT;
        } else {
            return RunningPattern.MIXED;
        }
    }

    /**
     * Launches a new process to execute a main method and blocks until the process exits.
     *
     * @param mainMethodInvocation the main method to be executed
     * @param args the arguments to the main method invocation
     * @param serverSocketPort the port of the server socket for the client process to communicate
     *     with the main process
     * @throws RuntimeException if a checked exception occurs or the process returns an exit value
     *     other than 0
     */
    private void launchProcess(
            @NonNull Class mainMethodInvocation, String[] args, int serverSocketPort) {
        List<String> commandAndArgs = Lists.newLinkedList();
        commandAndArgs.add(FileUtils.join(System.getProperty("java.home"), "bin", "java"));
        commandAndArgs.add("-cp");
        commandAndArgs.add(System.getProperty("java.class.path"));
        commandAndArgs.add(mainMethodInvocation.getName());
        commandAndArgs.addAll(Arrays.asList(args));
        commandAndArgs.add(String.valueOf(serverSocketPort));

        Process process;
        try {
            process = new ProcessBuilder(commandAndArgs).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw new RuntimeException(e);
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Process returned exitValue " + process.exitValue());
        }
    }

    /**
     * Executes the runnables in separate threads and returns the threads together with any
     * exceptions that were thrown during the execution of the threads.
     *
     * @param runnables the runnables to be executed
     * @return map from a thread to a {@code Throwable} (if any)
     */
    private Map<Thread, Optional<Throwable>> executeRunnablesInThreads(
            @NonNull List<IOExceptionRunnable> runnables) {
        ConcurrentMap<Thread, Optional<Throwable>> threads = new ConcurrentHashMap<>();
        for (IOExceptionRunnable runnable : runnables) {
            Thread thread =
                    new Thread(() -> {
                        try {
                            runnable.run();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            thread.setDaemon(true);

            threads.put(thread, Optional.empty());
            thread.setUncaughtExceptionHandler(
                    (aThread, throwable) -> {
                        threads.put(aThread, Optional.of(throwable));
                    });
        }
        for (Thread thread : threads.keySet()) {
            thread.start();
        }
        return threads;
    }

    /**
     * Waits for all the threads and processes to finish.
     *
     * @throws RuntimeException if a timeout or interrupt occurs while waiting for the threads and
     *     processes to finish or an exception was thrown during the execution of the threads
     */
    private void waitForThreadsAndProcessesToFinish(
            @NonNull Map<Thread, Optional<Throwable>> threads) {
        // Wait for the threads to finish
        for (Thread thread : threads.keySet()) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                interruptThreadsAndProcesses(threads.keySet());
                throw new RuntimeException(e);
            }
        }

        // If a thread is still running, throw an exception
        for (Thread thread : threads.keySet()) {
            if (thread.isAlive()) {
                interruptThreadsAndProcesses(threads.keySet());
                throw new RuntimeException("Actions are taking too long to finish");
            }
        }

        // If the threads have all terminated, throw any exceptions that occurred during the
        // execution of the threads
        for (Optional<Throwable> throwable : threads.values()) {
            if (throwable.isPresent()) {
                throw new RuntimeException(throwable.get());
            }
        }
    }

    /**
     * Interrupts the threads so that they can kill their respective processes. This method should
     * be called before the main thread terminates.
     */
    private void interruptThreadsAndProcesses(@NonNull Set<Thread> threads) {
        for (Thread thread : threads) {
            thread.interrupt();
            // Wait for the thread to kill its associated process
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    /**
     * Executes the action under test.
     *
     * @param args the first argument is the port of the server socket for the client process to
     *     communicate with the main process
     */
    public void runActionUnderTest(String[] args) throws IOException {
        // Let the server know that we're running the action under test
        int serverSocketPort = Integer.valueOf(args[0]);
        notifyServerOfActionRunning(serverSocketPort);
    }

    /**
     * Notifies the main process that the current client process is executing the action.
     *
     * @param serverSocketPort the port of the server socket for the client process to communicate
     *     with the main process
     */
    private void notifyServerOfActionRunning(int serverSocketPort) throws IOException {
        Socket socket = new Socket("localhost", serverSocketPort);
        new DataOutputStream(socket.getOutputStream()).writeUTF("STARTED");
        new DataInputStream(socket.getInputStream()).readUTF();
        socket.close();
    }

    /**
     * Closes a socket.
     */
    private void closeSocket(@NonNull Socket socket) throws IOException {
        new DataInputStream(socket.getInputStream()).readUTF();
        new DataOutputStream(socket.getOutputStream()).writeUTF("OK");
        socket.close();
    }
}
