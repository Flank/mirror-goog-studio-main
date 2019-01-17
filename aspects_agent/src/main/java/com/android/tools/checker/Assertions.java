package com.android.tools.checker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

@SuppressWarnings("unused") // Called via reflection
public class Assertions {
    private static final Logger LOGGER = Logger.getLogger(Assertions.class.getName());

    private Assertions() {}

    public static void assertIsNotEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("This method should not be called on the EDT thread");
        }
    }

    public static void assertIsEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new RuntimeException("This method should be called on the EDT thread");
        }
    }

    public static void warnIsNotEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            LOGGER.warning("This method should not be called on the EDT thread");
        }
    }

    public static void warnIsEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            LOGGER.warning("This method should be called on the EDT thread");
        }
    }

    public static void warn() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // We show the third element in the stack ignoring the two firsts which are:
        // Thread.currentThread().getStackTrace() and Assertions.warn()
        LOGGER.warning(
                String.format(
                        "[%s] %s.%s method called",
                        Thread.currentThread().getName(),
                        stackTrace[2].getClassName(),
                        stackTrace[2].getMethodName()));
    }

    public static void dumpStackTrace() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new Throwable().printStackTrace(new PrintStream(baos));
            LOGGER.warning(baos.toString());
        } catch (IOException e) {
            LOGGER.throwing(Assertions.class.getSimpleName(), "dumpStackTrace", e);
        }
    }
}
