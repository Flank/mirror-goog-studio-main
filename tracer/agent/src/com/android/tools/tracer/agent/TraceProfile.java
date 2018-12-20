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

package com.android.tools.tracer.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class TraceProfile {

    private final MethodSet start;
    private final MethodSet trace;
    private final MethodSet flush;
    private String outputFile;
    private final Set<String> annotations;
    private String jvmArgs;
    private boolean traceAgent;

    /**
     * Creates a tracing profile bassed on a configuration file. The file can have the following
     *
     * <p>Please see tools/base/tracer/README.md for a full description of the file.
     */
    public TraceProfile(String configFile) {
        start = new MethodSet();
        trace = new MethodSet();
        flush = new MethodSet();
        outputFile = getDefaultOutputPath();
        jvmArgs = initJvmArgs(configFile);
        annotations = new HashSet<>();
        annotations.add("Lcom/android/annotations/Trace;");

        if (configFile == null || !Files.exists(Paths.get(configFile))) {
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                    continue;
                }
                int i = line.indexOf(':');
                if (i == -1) {
                    throw new IllegalArgumentException("Malformed line: " + line);
                }
                String key = line.substring(0, i);
                String value = line.substring(i + 1).trim();
                if (key.equals("Trace")) {
                    trace.add(value);
                } else if (key.equals("Flush")) {
                    trace.add(value);
                    flush.add(value);
                } else if (key.equals("Start")) {
                    trace.add(value);
                    start.add(value);
                } else if (key.equals("Output")) {
                    outputFile = value;
                } else if (key.equals("Annotation")) {
                    value = "L" + value.replaceAll("\\.", "/") + ";";
                    annotations.add(value);
                } else if (key.equals("Trace-Agent")) {
                    traceAgent = Boolean.valueOf(value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getDefaultOutputPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.indexOf("win") >= 0) {
            String tmp = System.getProperty("java.io.tmpdir");
            return new File(tmp, "report.json").getAbsolutePath();
        } else {
            return "/tmp/report.json";
        }
    }
    private static String initJvmArgs(String path) {
        // We assume that the agent is called "trace_agent.jar"
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        List<String> jvmArgs = runtimeMXBean.getInputArguments();
        for (String arg : jvmArgs) {
            // Parses the javaagent argument to make its paths absolute
            // Argument description here: https://docs.oracle.com/javase/7/docs/api/java/lang/instrument/package-summary.html
            if (arg.startsWith("-javaagent:") && arg.contains("trace_agent.jar")) {
                String value = arg.substring(11);
                int ix = value.indexOf('=');
                String agent = value;
                String param = null;
                if (ix != -1) {
                    agent = value.substring(0, ix);
                    param = value.substring(ix + 1);
                }
                if (Objects.equals(path, param)) {
                    if (param != null) {
                        param = "=" + Paths.get(param).toAbsolutePath();
                    } else {
                        param = "";
                    }
                    return "-javaagent:" + Paths.get(agent).toAbsolutePath() + param;
                }
            }
        }

        return "";
    }

    public boolean shouldInstrument(String annotation) {
        return annotations.contains(annotation);
    }

    public boolean shouldInstrument(String className, String method) {
        return trace.contains(className, method);
    }

    public boolean shouldFlush(String className, String method) {
        return flush.contains(className, method);
    }

    public boolean start(String className, String method) {
        return start.contains(className, method);
    }

    public String getOutputFile() {
        return outputFile;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public boolean traceAgent() {
        return traceAgent;
    }

    /**
     * A fast look up for method matching. Note that contains will get called for every method on
     * every loaded class.
     */
    private static class MethodSet {

        /** A map from class names to method names that need to be traced. */
        private final Map<String, Set<String>> classes;

        /** A map from package name to method name (usually "*") that needs to be traced. */
        private final Map<String, Set<String>> packages;

        public MethodSet() {
            packages = new HashMap<>();
            classes = new HashMap<>();
        }

        /**
         * Adds a group of methods to the set that conform the following spec:
         *
         * <p>package [.class | .*] [:: method | ::* ]
         *
         * <p>The spec represents all the methods named "method" or all if "::*" or omitted, that
         * belong to the specified class (or classes if ".*"
         *
         * <p>Eg:
         *
         * <p># All methods in the class com.google.Name com.google.Name or com.google.Name::*
         *
         * <p># All methods named x in the class com.google.Name com.google.Name::x
         *
         * <p># All methods named x in the classes that belong to the package com.google
         * com.google.*::x
         *
         * <p># All methods in all the classes that belong to the package com.google com.google.* or
         * com.google.*::*
         */
        void add(String spec) {
            int j = spec.indexOf("::");
            String className = spec;
            String methodName = "*";
            boolean exact = true;
            if (j != -1) {
                className = spec.substring(0, j);
                methodName = spec.substring(j + 2);
            }
            if (className.endsWith(".*")) {
                exact = false;
                className = className.substring(0, className.length() - 2);
            }
            className = className.replaceAll("\\.", "/");
            if (exact) {
                classes.computeIfAbsent(className, s -> new HashSet<>()).add(methodName);
            } else {
                packages.computeIfAbsent(className, s -> new HashSet<>()).add(methodName);
            }
        }

        boolean contains(String className, String methodName) {
            Set<String> classMethods = classes.get(className);
            if (classMethods != null
                    && (classMethods.contains(methodName) || classMethods.contains("*"))) {
                return true;
            }
            int ix = className.lastIndexOf('/');
            if (ix != -1) {
                String pkg = className.substring(0, ix);
                Set<String> packageMethods = packages.get(pkg);
                if (packageMethods != null
                        && (packageMethods.contains(methodName) || packageMethods.contains("*"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
