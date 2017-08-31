/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.profiler.support.io.scanner;

import com.android.tools.profiler.support.io.IoTracker;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * This class wraps {@link Scanner} class read methods, these methods are called via reflection
 * instead of the original ones, each method collects info about reading operations then calls the
 * original method.
 */
public class ScannerWrapper {

    /**
     * Contains for each {@link Scanner} object that is used to read from a file, the
     * {@link IoTracker} object of it.
     */
    private static final Map<Scanner, IoTracker> ourFileRelatedScannersTrackers = new HashMap<>();

    /**
     * Gets the {@link IoTracker} object of the {@link Scanner} object.
     *
     * @param scanner The object to get the file path.
     * @return the {@link IoTracker} object if the scanner is reading from a file, or null
     * otherwise.
     */
    private static IoTracker getIoTracker(Scanner scanner) {
        return ourFileRelatedScannersTrackers.get(scanner);
    }

    /**
     * Creates a new tracker for the {@link Scanner} object.
     *
     * @param scanner The object that is reading from a file.
     * @param filePath the path of the file that {@param scanner} is reading from.
     * @return The passed object.
     */
    private static Scanner startNewFileScannerSession(Scanner scanner, String filePath) {
        IoTracker ioTracker = new IoTracker();
        ourFileRelatedScannersTrackers.put(scanner, ioTracker);
        ioTracker.trackNewFileSession(filePath);
        return scanner;
    }

    /**
     * Removes the {@link Scanner} object from {@link #ourFileRelatedScannersTrackers}
     *
     * @param scanner The object that is reading from a file.
     */
    private static void endFileScannerSession(Scanner scanner) {
        IoTracker ioTracker = getIoTracker(scanner);
        if (ioTracker == null) {
            return;
        }
        ourFileRelatedScannersTrackers.remove(scanner);
        ioTracker.trackTerminatingFileSession();
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static Scanner wrapConstructor(File source) throws FileNotFoundException {
        return startNewFileScannerSession(new Scanner(source), source.getPath());
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static Scanner wrapConstructor(File source, String charsetName)
        throws FileNotFoundException {
        return startNewFileScannerSession(new Scanner(source, charsetName), source.getPath());
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static Scanner wrapConstructor(Path source) throws IOException {
        return startNewFileScannerSession(new Scanner(source), source.toString());
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static Scanner wrapConstructor(Path source, String charsetName) throws IOException {
        return startNewFileScannerSession(new Scanner(source, charsetName), source.toString());
    }

    private static void track(Scanner scanner, int numberOfBytesRead, long startTimestamp) {
        IoTracker ioTracker = getIoTracker(scanner);
        if (ioTracker != null) {
            ioTracker.trackIoCall(numberOfBytesRead, startTimestamp, true);
        }
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static String wrapNext(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        String s = scanner.next();
        track(scanner, s.length() * Character.BYTES, startTimestamp);
        return s;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static String wrapNext(Scanner scanner, Pattern pattern) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        String s = scanner.next(pattern);
        track(scanner, s.length() * Character.BYTES, startTimestamp);
        return s;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static String wrapNext(Scanner scanner, String pattern) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        String s = scanner.next(pattern);
        track(scanner, s.length() * Character.BYTES, startTimestamp);
        return s;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static BigDecimal wrapNextBigDecimal(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        BigDecimal bigDecimal = scanner.nextBigDecimal();
        track(scanner, bigDecimal.unscaledValue().toByteArray().length, startTimestamp);
        return bigDecimal;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static BigInteger wrapNextBigInteger(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        BigInteger bigInteger = scanner.nextBigInteger();
        track(scanner, bigInteger.toByteArray().length, startTimestamp);
        return bigInteger;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static BigInteger wrapNextBigInteger(Scanner scanner, int radix) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        BigInteger bigInteger = scanner.nextBigInteger(radix);
        track(scanner, bigInteger.toByteArray().length, startTimestamp);
        return bigInteger;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static boolean wrapNextBoolean(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        boolean b = scanner.nextBoolean();
        track(scanner, Byte.BYTES, startTimestamp);
        return b;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static byte wrapNextByte(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        byte b = scanner.nextByte();
        track(scanner, Byte.BYTES, startTimestamp);
        return b;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static byte wrapNextByte(Scanner scanner, int radix) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        byte b = scanner.nextByte(radix);
        track(scanner, Byte.BYTES, startTimestamp);
        return b;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static double wrapNextDouble(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        double d = scanner.nextDouble();
        track(scanner, Double.BYTES, startTimestamp);
        return d;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static float wrapNextFloat(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        float f = scanner.nextFloat();
        track(scanner, Float.BYTES, startTimestamp);
        return f;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static int wrapNextInt(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int i = scanner.nextInt();
        track(scanner, Integer.BYTES, startTimestamp);
        return i;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static int wrapNextInt(Scanner scanner, int radix) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        int i = scanner.nextInt(radix);
        track(scanner, Integer.BYTES, startTimestamp);
        return i;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static String wrapNextLine(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        String s = scanner.nextLine();
        track(scanner, s.length() * Character.BYTES, startTimestamp);
        return s;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static long wrapNextLong(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        long l = scanner.nextLong();
        track(scanner, Long.BYTES, startTimestamp);
        return l;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static long wrapNextLong(Scanner scanner, int radix) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        long l = scanner.nextLong(radix);
        track(scanner, Long.BYTES, startTimestamp);
        return l;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static short wrapNextShort(Scanner scanner) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        short s = scanner.nextShort();
        track(scanner, Short.BYTES, startTimestamp);
        return s;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static short wrapNextShort(Scanner scanner, int radix) throws IOException {
        long startTimestamp = IoTracker.getTimeInNanos();
        short s = scanner.nextShort(radix);
        track(scanner, Short.BYTES, startTimestamp);
        return s;
    }

    @SuppressWarnings("unused") // Called in the ProfilerPlugin via reflection
    public static void wrapClose(Scanner scanner) throws IOException {
        scanner.close();
        endFileScannerSession(scanner);
    }
}
