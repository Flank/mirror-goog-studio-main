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
import com.android.utils.FileUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
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
 * notify {@code InterProcessConcurrencyTester} that the process has started and the action is
 * running, as follows:
 *
 * <pre>{@code
 *     // The server socket port is added to the list of arguments by InterProcessConcurrencyTester
 *     // for the client process to communicate with the main process
 *     int serverSocketPort = Integer.valueOf(args[args.length - 1]);
 *     InterProcessConcurrencyTester.MainProcessNotifier notifier =
 *         new InterProcessConcurrencyTester.MainProcessNotifier(serverSocketPort);
 *     notifier.processStarted();
 *     notifier.actionStarted();
 *     ... // Do some action that needs concurrency test
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

    @NonNull private List<Class> classInvocationList = Lists.newLinkedList();

    @NonNull private List<String[]> argsList = Lists.newLinkedList();

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
        ServerSocket serverSocket = new ServerSocket(0);
        int serverSocketPort = serverSocket.getLocalPort();

        // Execute each invocation in a separate process. For each class under test, we create a new
        // thread that will launch a new process which will execute the main() method of that class.
        // The launched thread will block until the corresponding process exits.
        List<Runnable> runnables = Lists.newLinkedList();
        for (int i = 0; i < classInvocationList.size(); i++) {
            Class classUnderTest = classInvocationList.get(i);
            String[] args = argsList.get(i);
            runnables.add(() -> launchProcess(classUnderTest, args, serverSocketPort));
        }
        Map<Thread, Optional<Throwable>> threads = executeRunnablesInThreads(runnables);

        // Wait for all the processes to start execution
        LinkedList<Socket> startedProcesses = Lists.newLinkedList();
        serverSocket.setSoTimeout(0); // A timeout of 0 is interpreted as infinite timeout
        while (startedProcesses.size() < runnables.size()) {
            startedProcesses.add(serverSocket.accept());
        }
        while (startedProcesses.size() > 0) {
            closeSocket(startedProcesses.removeFirst());
        }

        // Begin to monitor how the actions are executed
        int remainingActions = classInvocationList.size();
        LinkedList<Socket> runningActions = Lists.newLinkedList();
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
            Socket startedAction = null;
            if (runningActions.isEmpty()) {
                serverSocket.setSoTimeout(0); // A timeout of 0 is interpreted as infinite timeout
                startedAction = serverSocket.accept();
            } else {
                serverSocket.setSoTimeout((int) timeoutToStartAction.toMillis());
                try {
                    startedAction = serverSocket.accept();
                } catch (SocketTimeoutException e) {
                    // This exception means no action has started, which we will handle below.
                }
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
                while (runningActions.size() > 0) {
                    closeSocket(runningActions.removeFirst());
                }
            }
        }

        // Let all the running actions finish
        while (runningActions.size() > 0) {
            closeSocket(runningActions.removeFirst());
        }

        // Wait for all the threads (and processes) to finish
        waitForThreadsToFinish(threads);

        // Close the server socket
        serverSocket.close();

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
     * Launches a new process to execute the class under test's main() method and blocks until the
     * process exits.
     *
     * @param classUnderTest the class under test whose main() method will be executed from a new
     *     process
     * @param args the arguments for the class under test's main() method
     * @param serverSocketPort the port of the server socket for the client process to communicate
     *     with the main process
     * @throws RuntimeException if any (checked or runtime) exception occurs or the process returns
     *     an exit value other than 0
     */
    private void launchProcess(
            @NonNull Class classUnderTest, String[] args, int serverSocketPort) {
        List<String> commandAndArgs = Lists.newLinkedList();
        commandAndArgs.add(FileUtils.join(System.getProperty("java.home"), "bin", "java"));
        commandAndArgs.add("-cp");
        commandAndArgs.add(System.getProperty("java.class.path"));
        commandAndArgs.add(classUnderTest.getName());
        commandAndArgs.addAll(Arrays.asList(args));
        // Add the server socket port to the list of arguments for the client process to communicate
        // with the main process
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
            throw new RuntimeException(e);
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException(
                    "Process returned non-zero exit value: " + process.exitValue());
        }
    }

    /**
     * Executes the runnables in separate threads and returns immediately after all the threads have
     * started execution (this method does not wait until all the threads have terminated).
     *
     * This methods returns a map from the threads to any exceptions thrown during the execution of
     * the threads. Note that the map's values (if any) are not available immediately but only after
     * the threads have terminated.
     *
     * @param runnables the runnables to be executed
     * @return a map from the threads to any exceptions thrown during the execution of the threads
     */
    @NonNull
    private Map<Thread, Optional<Throwable>> executeRunnablesInThreads(
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

    /**
     * Waits for all the threads to finish.
     */
    private void waitForThreadsToFinish(@NonNull Map<Thread, Optional<Throwable>> threads) {
        // Wait for the threads to finish
        for (Thread thread : threads.keySet()) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // Throw any exceptions that occurred during the execution of the threads
        for (Optional<Throwable> throwable : threads.values()) {
            if (throwable.isPresent()) {
                throw new RuntimeException(throwable.get());
            }
        }
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
         * method will block until the main process closes the corresponding socket.
         */
        public void processStarted() throws IOException {
            Socket socket = new Socket("localhost", serverSocketPort);
            new DataOutputStream(socket.getOutputStream()).writeUTF("PROCESS_STARTED");
            new DataInputStream(socket.getInputStream()).readUTF();
            socket.close();
        }

        /**
         * Notifies the main process that the current client process starts executing the action
         * under test. This method will block until the main process closes the corresponding
         * socket.
         */
        public void actionStarted() throws IOException {
            Socket socket = new Socket("localhost", serverSocketPort);
            new DataOutputStream(socket.getOutputStream()).writeUTF("ACTION_STARTED");
            new DataInputStream(socket.getInputStream()).readUTF();
            socket.close();
        }
    }

    /**
     * Closes a socket, thereby unblocking the corresponding client process and allowing it to
     * resume execution. See {@link MainProcessNotifier#processStarted()} and {@link
     * MainProcessNotifier#actionStarted()}.
     */
    private void closeSocket(@NonNull Socket socket) throws IOException {
        new DataInputStream(socket.getInputStream()).readUTF();
        new DataOutputStream(socket.getOutputStream()).writeUTF("OK");
        socket.close();
    }
}
