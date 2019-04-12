package com.android.tools.checker;

import com.android.tools.checker.agent.Baseline;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

@SuppressWarnings("unused") // Called via reflection
public class Assertions {
    private static final Logger LOGGER = Logger.getLogger(Assertions.class.getName());

    private Assertions() {}

    /**
     * Whether the stacktrace is consumed by the {@link Baseline}, which can happen either:
     *
     * <p>1) The stacktrace is whitelisted therefore we should skip the assertion; or 2) We are in
     * generating baseline mode so we shouldn't throw assertions, but add the stacktrace to the
     * baseline instead.
     */
    private static boolean consumedByBaseline(StackTraceElement[] stackTrace) {
        if (Baseline.getInstance().isGeneratingBaseline()) {
            Baseline.getInstance().whitelistStackTrace(stackTrace);
            return true;
        }
        return Baseline.getInstance().isWhitelisted(stackTrace);
    }

    public static void assertIsNotEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            if (consumedByBaseline(Thread.currentThread().getStackTrace())) {
                return;
            }

            StackTraceMethod currentMethod = StackTraceMethod.getCurrentStackTraceMethod();
            LOGGER.severe(
                    String.format(
                            "%s.%s method called from EDT",
                            currentMethod.getClassName(), currentMethod.getMethodName()));
            throw new RuntimeException("This method should not be called on the EDT");
        }
    }

    public static void assertIsEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            if (consumedByBaseline(Thread.currentThread().getStackTrace())) {
                return;
            }

            StackTraceMethod currentMethod = StackTraceMethod.getCurrentStackTraceMethod();
            LOGGER.severe(
                    String.format(
                            "%s.%s method called from [%s] instead of EDT",
                            currentMethod.getClassName(),
                            currentMethod.getMethodName(),
                            currentMethod.getThreadName()));
            throw new RuntimeException("This method should be called on the EDT");
        }
    }

    public static void warnIsNotEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            LOGGER.warning("This method should not be called on the EDT");
        }
    }

    public static void warnIsEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            LOGGER.warning("This method should be called on the EDT");
        }
    }

    public static void warn() {
        StackTraceMethod currentMethod = StackTraceMethod.getCurrentStackTraceMethod();
        LOGGER.warning(
                String.format(
                        "[%s] %s.%s method called",
                        currentMethod.getThreadName(),
                        currentMethod.getClassName(),
                        currentMethod.getMethodName()));
    }

    public static void dumpStackTrace() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new Throwable().printStackTrace(new PrintStream(baos));
            LOGGER.warning(baos.toString());
        } catch (IOException e) {
            LOGGER.throwing(Assertions.class.getSimpleName(), "dumpStackTrace", e);
        }
    }

    private static class StackTraceMethod {

        private final String className;
        private final String methodName;
        private final String threadName;

        private StackTraceMethod(String className, String methodName, String threadName) {
            this.className = className;
            this.methodName = methodName;
            this.threadName = threadName;
        }

        public String getClassName() {
            return className;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getThreadName() {
            return threadName;
        }

        /**
         * Returns the fourth method in the top of the current stacktrace, therefore ignoring
         * Thread.currentThread().getStackTrace(), this helper method and its caller. Also returns
         * the method's class and the current thread name.
         */
        public static StackTraceMethod getCurrentStackTraceMethod() {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            return new StackTraceMethod(
                    stackTrace[3].getClassName(),
                    stackTrace[3].getMethodName(),
                    Thread.currentThread().getName());
        }
    }
}
