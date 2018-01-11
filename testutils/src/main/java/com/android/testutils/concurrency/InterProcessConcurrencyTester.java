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

package com.android.testutils.concurrency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.testutils.TestUtils;
import com.google.common.base.Preconditions;
import com.google.common.base.Verify;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import org.junit.Assert;

/**
 * Utility class to test inter-process concurrency.
 *
 * <p>This class is used to check whether or not a set of processes violate a concurrency contract
 * (e.g., whether they run concurrently when they are not allowed to, or vice versa).
 *
 * <p>The usage and implementation of this class resemble those of {@link ConcurrencyTester}, except
 * that {@code ConcurrencyTester} supports concurrency testing for threads within the same process
 * whereas this class supports concurrency testing for different processes.
 *
 * <p>The following is a usage scenario of this class. Suppose we have a class whose main() method
 * executes an action (some piece of code) exactly once. When several processes are concurrently
 * calling the main() method (possibly with different parameter values), the class under test may
 * make a contract that all the processes can execute the corresponding actions concurrently, or it
 * may make a contract that all the processes cannot execute the actions concurrently. To check
 * whether the class under test meets the concurrency contract, we can write a test as follows.
 *
 * <pre>{@code
 * InterProcessConcurrencyTester tester = new InterProcessConcurrencyTester();
 * for (...) {
 *     tester.addClassInvocationFromNewProcess(ClassUnderTest.class, new String[] {...});
 * }
 * }</pre>
 *
 * <p>In the class under test's main() method, before calling the action under test, it needs to
 * notify {@code InterProcessConcurrencyTester} when the process has started and when the action is
 * running as follows (so that the tester can perform necessary concurrency checks on these events):
 *
 * <pre>{@code
 * // The server socket port is added to the list of arguments by InterProcessConcurrencyTester for
 * // the client process to communicate with the main process
 * int serverSocketPort = Integer.valueOf(args[args.length - 1]);
 * InterProcessConcurrencyTester.MainProcessNotifier notifier =
 *     new InterProcessConcurrencyTester.MainProcessNotifier(serverSocketPort);
 *
 * notifier.processStarted(); // Notify the main process that it has started execution
 *
 * ... // Start of the synchronized region (e.g., lock.lock())
 *
 * notifier.actionStarted(); // Notify that it starts executing the action under test
 *
 * ... // The action under test (which has a concurrency requirement)
 *
 * ... // End of the synchronized region (e.g., lock.unlock())
 * }</pre>
 *
 * <p>Then, if the actions are allowed to run concurrently, we can make the following assertion:
 *
 * <pre>{@code
 * tester.assertThatActionsCanRunConcurrently();
 * }</pre>
 *
 * <p>If the actions are not allowed to run concurrently, we can make the following assertion:
 *
 * <pre>{@code
 * tester.assertThatActionsCannotRunConcurrently();
 * }</pre>
 */
public final class InterProcessConcurrencyTester {

    /**
     * The timeout for the main process to wait for a new action to start in the case that there are
     * one or more actions currently running and the actions are expected to run concurrently. As
     * this timeout increases, the chance of a falsely failing test (or flaky test) is reduced;
     * however, a truly failing test (or non-flaky test) will take longer to run. Since it is okay
     * for a failing test to take a long time to run (it does not happen frequently), we set a large
     * timeout to prevent flaky tests.
     */
    @NonNull private static final Duration TIMEOUT_TO_START_ACTION_WHEN_CONCURRENCY_EXPECTED =
            Duration.ofSeconds(60);

    /**
     * The timeout for the main process to wait for a new action to start in the case that there are
     * one or more actions currently running and the actions are not expected to run concurrently.
     * As this timeout increases, the chance of a falsely passing test is reduced; however, a truly
     * passing test will take longer to run. Therefore, we set a small timeout to prevent
     * long-running passing tests, but make sure it is still large enough for the tests to be
     * effective.
     */
    @NonNull private static final Duration TIMEOUT_TO_START_ACTION_WHEN_NO_CONCURRENCY_EXPECTED =
            Duration.ofSeconds(2);

    /** The running pattern of a set of actions. */
    private enum RunningPattern {

        /** All actions run concurrently. */
        CONCURRENT,

        /** All actions run sequentially. */
        SEQUENTIAL,

        /** More than one but not all actions run concurrently. */
        MIXED
    }

    @NonNull private List<Class> classInvocationList = new LinkedList<>();

    @NonNull private List<String[]> argsList = new LinkedList<>();

    /**
     * Adds a new invocation of the class under test's main() method to this {@link
     * InterProcessConcurrencyTester} instance. The {@code InterProcessConcurrencyTester} will
     * execute each invocation in a separate process and check whether the corresponding actions
     * under test meet the concurrency requirement.
     *
     * @param classUnderTest the class under test whose main() method will be executed from a new
     *     process
     * @param args the arguments for the class under test's main() method
     */
    public void addClassInvocationFromNewProcess(
            @NonNull Class classUnderTest, @NonNull String[] args) {
        classInvocationList.add(classUnderTest);
        argsList.add(args);
    }

    /**
     * Executes the invocations of the class under test's main() method in separate processes and
     * asserts that all the actions ran concurrently. Note that a failed assertion means that either
     * the actions were not allowed to run concurrently (which violates the concurrency requirement)
     * or the actions accidentally ran sequentially. However, while the latter case is possible, the
     * implementation of this method makes sure that it is unlikely to happen.
     */
    public void assertThatActionsCanRunConcurrently() throws IOException {
        Preconditions.checkArgument(
                classInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");
        Assert.assertTrue(
                "Two or more actions ran sequentially"
                        + " while all the actions were expected to run concurrently.",
                executeActionsAndGetRunningPattern(
                                TIMEOUT_TO_START_ACTION_WHEN_CONCURRENCY_EXPECTED)
                        == RunningPattern.CONCURRENT);
    }

    /**
     * Executes the invocations of the class under test's main() method in separate processes and
     * asserts that all the actions ran sequentially. Note that a successful assertion means that
     * either the actions were not allowed to run concurrently (which meets the concurrency
     * requirement) or the actions accidentally ran sequentially. However, while the latter case is
     * possible, the implementation of this method makes sure that it is unlikely to happen.
     */
    public void assertThatActionsCannotRunConcurrently() throws IOException {
        Preconditions.checkArgument(
                classInvocationList.size() >= 2,
                "There must be at least 2 actions for concurrency checks.");
        Assert.assertTrue(
                "Two or more actions ran concurrently"
                        + " while all the actions were expected to run sequentially.",
                executeActionsAndGetRunningPattern(
                                TIMEOUT_TO_START_ACTION_WHEN_NO_CONCURRENCY_EXPECTED)
                        == RunningPattern.SEQUENTIAL);
    }

    /**
     * Executes the invocations of the class under test's main() method in separate processes and
     * returns the running pattern of their actions.
     *
     * @param timeoutToStartAction the timeout for the main thread to wait for a new action to start
     *     in the case that there are one or more actions currently running
     * @return the running pattern of the actions
     */
    private RunningPattern executeActionsAndGetRunningPattern(
            @NonNull Duration timeoutToStartAction) throws IOException {
        // We use sockets to synchronize processes in a similar manner that latches synchronize
        // threads within the same process. The main process opens a ServerSocket and waits for
        // client processes to connect. When a client process has started execution or when it
        // starts executing the action under test, it opens a socket with the server and blocks
        // until the server closes that socket.

        // First, open the server socket
        ServerSocket serverSocket = openServerSocket();

        // Execute each invocation in a separate process. For each class under test, we create a new
        // thread that will launch a new process which will execute the main() method of that class.
        // The launched thread will block until the corresponding process exits.
        List<Runnable> runnables = new LinkedList<>();
        for (int i = 0; i < classInvocationList.size(); i++) {
            Class classUnderTest = classInvocationList.get(i);
            String[] args = argsList.get(i);
            // Add the server socket port to the list of arguments for the client process to communicate
            // with the main process
            String[] allArgs = Arrays.copyOf(args, args.length + 1);
            allArgs[args.length] = String.valueOf(serverSocket.getLocalPort());

            runnables.add(() -> TestUtils.launchProcess(classUnderTest, allArgs));
        }
        Map<Thread, Optional<Throwable>> threads = executeRunnablesInThreads(runnables);

        // Wait for all the processes to start execution
        Queue<Socket> startedProcesses = new LinkedList<>();
        while (startedProcesses.size() < runnables.size()) {
            startedProcesses.add(acceptSocketOnEvent(serverSocket, ProcessEvent.PROCESS_STARTED));
        }
        while (!startedProcesses.isEmpty()) {
            processCanResume(startedProcesses.remove());
        }

        // Begin to monitor how the actions are executed
        int remainingActions = classInvocationList.size();
        Queue<Socket> runningActions = new LinkedList<>();
        int maxConcurrentActions = 0;

        // To prevent the actions from *accidentally* running sequentially, when an action is going
        // to finish, we don't let it finish immediately but try waiting for the next action to
        // start. The following loop aims to "force" the actions to run concurrently. If it
        // succeeds, it means that the actions are able to run concurrently. If it doesn't succeed,
        // it means that either the actions are not able to run concurrently, or the actions take
        // too long to start.
        while (remainingActions > 0) {
            // Wait for a new action to start. If there are currently no running actions, let's wait
            // for the new action without a timeout. If there are currently one or more running
            // actions, the running actions could block new actions and prevent them from starting
            // (e.g., when the actions are not allowed to run concurrently). To avoid waiting
            // indefinitely, we need to set a timeout.
            Socket startedAction;
            if (runningActions.isEmpty()) {
                // The method below always returns a non-null object, blocking to wait if necessary
                startedAction = acceptSocketOnEvent(serverSocket, ProcessEvent.ACTION_STARTED);
            } else {
                // The method below returns null if no action has started after the specified time
                startedAction =
                        acceptSocketOnEvent(
                                serverSocket,
                                ProcessEvent.ACTION_STARTED,
                                (int) timeoutToStartAction.toMillis());
            }

            // If a new action has started, do not let it finish. Instead, we keep waiting for more
            // actions to start (repeat the loop).
            if (startedAction != null) {
                remainingActions--;
                runningActions.add(startedAction);
                if (runningActions.size() > maxConcurrentActions) {
                    maxConcurrentActions = runningActions.size();
                }
            } else {
                // If no action has started (which implies that there are currently one or more
                // running actions and the timeout expired), it could be that either the running
                // actions are blocking the new actions, or the new actions are taking too long to
                // start. Since we cannot distinguish these two cases, we let all the running
                // actions finish and repeat the loop.
                while (!runningActions.isEmpty()) {
                    processCanResume(runningActions.remove());
                }
            }
        }

        // Let all the running actions finish
        while (!runningActions.isEmpty()) {
            processCanResume(runningActions.remove());
        }

        // Wait for all the threads (and processes) to finish
        waitForThreadsToFinish(threads);

        // Close the server socket
        closeServerSocket(serverSocket);

        // Determine the running pattern based on maxConcurrentActions
        Preconditions.checkState(
                maxConcurrentActions >= 1 && maxConcurrentActions <= runnables.size());
        if (maxConcurrentActions == 1) {
            return RunningPattern.SEQUENTIAL;
        } else if (maxConcurrentActions == runnables.size()) {
            return RunningPattern.CONCURRENT;
        } else {
            return RunningPattern.MIXED;
        }
    }

    /**
     * Executes the runnables in separate threads and returns immediately after all the threads have
     * started execution (this method does not wait until all the threads have terminated).
     *
     * <p>This methods returns a map from the threads to any exceptions thrown during the execution
     * of the threads. Note that the map's values (if any) are not available immediately but only
     * after the threads have terminated.
     *
     * @param runnables the runnables to be executed
     * @return a map from the threads to any exceptions thrown during the execution of the threads
     */
    @NonNull
    private static Map<Thread, Optional<Throwable>> executeRunnablesInThreads(
            @NonNull List<Runnable> runnables) {
        ConcurrentMap<Thread, Optional<Throwable>> threads = new ConcurrentHashMap<>();
        CountDownLatch allThreadsStartedLatch = new CountDownLatch(runnables.size());

        for (Runnable runnable : runnables) {
            Thread thread = new Thread(() -> {
                allThreadsStartedLatch.countDown();
                runnable.run();
            });
            threads.put(thread, Optional.empty());
            thread.setUncaughtExceptionHandler(
                    (aThread, throwable) -> threads.put(aThread, Optional.of(throwable)));
        }

        for (Thread thread : threads.keySet()) {
            thread.start();
        }

        // Wait for all the threads to start execution
        try {
            allThreadsStartedLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return threads;
    }

    /** Waits for all the threads to finish. */
    private static void waitForThreadsToFinish(@NonNull Map<Thread, Optional<Throwable>> threads) {
        // Wait for the threads to finish
        for (Thread thread : threads.keySet()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // Throw any exceptions that occurred during the execution of the threads
        //noinspection OptionalUsedAsFieldOrParameterType
        for (Optional<Throwable> throwable : threads.values()) {
            if (throwable.isPresent()) {
                throw new RuntimeException(throwable.get());
            }
        }
    }

    /**
     * Events sent between client processes (which are executing the actions under test) and the
     * main process.
     */
    private enum ProcessEvent {

        /** A client process notifies the main process that it has started execution. */
        PROCESS_STARTED,

        /**
         * A client process notifies the main process that it starts executing the action under
         * test.
         */
        ACTION_STARTED,

        /** The main process notifies a client process that it can resume execution. */
        PROCESS_CAN_RESUME,
    }

    /**
     * Class to be used by client processes (which are executing the actions under test) to notify
     * the main process of events happening in the client processes.
     */
    public static class MainProcessNotifier {

        private final int serverSocketPort;

        /**
         * Creates a new instance of {@code MainProcessNotifier}.
         *
         * @param serverSocketPort the port of the server socket for client processes to communicate
         *     with the main process
         */
        public MainProcessNotifier(int serverSocketPort) {
            this.serverSocketPort = serverSocketPort;
        }

        /**
         * Notifies the main process that the current client process has started execution. This
         * method will block until the main process notifies the client process that it can resume
         * execution.
         */
        public void processStarted() throws IOException {
            notifyMainProcess(ProcessEvent.PROCESS_STARTED);
        }

        /**
         * Notifies the main process that the current client process starts executing the action
         * under test. This method will block until the main process notifies the client process
         * that it can resume execution.
         */
        public void actionStarted() throws IOException {
            notifyMainProcess(ProcessEvent.ACTION_STARTED);
        }

        private void notifyMainProcess(@NonNull ProcessEvent processEvent) throws IOException {
            try (Socket socket = new Socket("localhost", serverSocketPort);
                    DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
                    DataInputStream inputStream = new DataInputStream(socket.getInputStream())) {
                outputStream.writeUTF(processEvent.name());

                // The method call below will block
                Preconditions.checkState(
                        ProcessEvent.valueOf(inputStream.readUTF())
                                == ProcessEvent.PROCESS_CAN_RESUME);
            }
        }
    }

    /** Opens a server socket for client processes to communicate with the main process. */
    @NonNull
    private static ServerSocket openServerSocket() throws IOException {
        return new ServerSocket(0);
    }

    /**
     * Accepts a socket opened by a client process on the given event, blocking to wait until there
     * is one.
     */
    @NonNull
    private static Socket acceptSocketOnEvent(
            @NonNull ServerSocket serverSocket, @NonNull ProcessEvent processEvent)
            throws IOException {
        // A timeout of 0 is interpreted as infinite timeout
        return Verify.verifyNotNull(acceptSocketOnEvent(serverSocket, processEvent, 0));
    }

    /**
     * Accepts a socket opened by a client process on the given event, returning null if there is
     * none after the specified time.
     */
    @SuppressWarnings({
        "resource",
        "IOResourceOpenedButNotSafelyClosed",
        "SocketOpenedButNotSafelyClosed"
    })
    @Nullable
    private static Socket acceptSocketOnEvent(
            @NonNull ServerSocket serverSocket,
            @NonNull ProcessEvent processEvent,
            int millisTimeout)
            throws IOException {
        serverSocket.setSoTimeout(millisTimeout);
        Socket socket;
        try {
            socket = serverSocket.accept();
        } catch (SocketTimeoutException e) {
            return null;
        }

        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
        Preconditions.checkState(ProcessEvent.valueOf(inputStream.readUTF()) == processEvent);

        return socket;
    }

    /** Notifies the client process that it can resume execution. Also closes the client socket. */
    private static void processCanResume(@NonNull Socket socket) throws IOException {
        try (DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
            outputStream.writeUTF(ProcessEvent.PROCESS_CAN_RESUME.name());
        }
        socket.close();
    }

    /** Closes the server socket. */
    private static void closeServerSocket(@NonNull ServerSocket serverSocket) throws IOException {
        serverSocket.close();
    }
}
