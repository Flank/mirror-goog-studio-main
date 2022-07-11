package com.android.tools.gradle;

import com.android.annotations.NonNull;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

class GradleW {

    public static void main(String[] args) throws Exception {
        System.exit(new GradleW().run(Arrays.asList(args)));
    }

    private int run(List<String> args) throws Exception {
        Path logFile = Files.createTempFile("gradle", ".log");
        File gradleFile = null;
        File distribution = null;
        LinkedList<File> repos = new LinkedList<>();
        LinkedList<String> tasks = new LinkedList<>();
        LinkedList<OutputFileEntry> outputFiles = new LinkedList<>();
        List<String> gradleArgs = new ArrayList<>();
        String testOutputDir = null;

        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("--output") && it.hasNext()) {
                String from = it.next();
                if (!it.hasNext()) {
                    throw new IllegalArgumentException("--output requires two arguments.");
                }
                String to = it.next();
                outputFiles.add(new OutputFileEntry(from, to));
            } else if (arg.equals("--gradle_file") && it.hasNext()) {
                gradleFile = new File(it.next());
            } else if (arg.equals("--distribution") && it.hasNext()) {
                distribution = new File(it.next());
            } else if (arg.equals("--repo") && it.hasNext()) {
                repos.add(new File(it.next()));
            } else if (arg.equals("--task") && it.hasNext()) {
                tasks.add(it.next());
            } else if (arg.equals("--log_file") && it.hasNext()) {
                logFile = Paths.get(it.next());
            } else if (arg.equals("--test_output_dir") && it.hasNext()) {
                testOutputDir = it.next();
            } else if (arg.equals("--max_workers") && it.hasNext()) {
                gradleArgs.add("--max-workers");
                gradleArgs.add(it.next());
            } else if (arg.startsWith("-P")) {
                gradleArgs.add(arg);
            } else {
                throw new IllegalArgumentException("Unknown argument '" + arg + "'.");
            }
        }
        File outDir =
                logFile.getParent().resolve(logFile.getFileName().toString() + ".temp").toFile();
        Files.createDirectories(outDir.toPath());
        try (Gradle gradle = new Gradle(gradleFile.getParentFile(), outDir, distribution);
                OutputStream log = new BufferedOutputStream(Files.newOutputStream(logFile))) {
            for (File repo : repos) {
                gradle.addRepo(repo);
            }
            gradle.addArgument("-Pkotlin.compiler.execution.strategy=in-process");
            gradleArgs.forEach(gradle::addArgument);
            OutputStream out = new TeeOutputStream(System.out, log);
            OutputStream err = new TeeOutputStream(System.err, log);

            try {
                gradle.run(tasks, out, err);
                for (OutputFileEntry outputFile : outputFiles) {
                    outputFile.collect(gradle.getBuildDir().toPath(), gradle.getLocalMavenRepo());
                }
            } finally {
                if (testOutputDir != null) {
                    String xml = System.getenv("XML_OUTPUT_FILE");
                    if (xml == null) {
                        throw new IllegalStateException(
                                "XML_OUTPUT_FILE is not declared and test results were expected");
                    }
                    aggregateTestResults(
                            new File(gradle.getBuildDir(), testOutputDir).toPath(), Paths.get(xml));
                }
            }
        }
        return 0;
    }

    /** A simple heuristic to convert gradle testsuite xmls, into a bazel testsuite one. */
    private void aggregateTestResults(Path testResultsDir, Path output) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(output.toFile())) {
            fos.write(
                    "<?xml version='1.0' encoding='UTF-8'?>\n<testsuites>\n"
                            .getBytes(StandardCharsets.UTF_8));
            List<Path> files = Files.list(testResultsDir).collect(Collectors.toList());
            for (Path file : files) {
                if (!file.toString().endsWith(".xml")) {
                    continue;
                }
                try (BufferedInputStream is =
                        new BufferedInputStream(new FileInputStream(file.toFile()))) {
                    // Find the <testsuite> tag
                    String tag = "testsuite ";
                    byte[] buf = new byte[tag.getBytes(StandardCharsets.UTF_8).length];
                    boolean found = false;
                    int c;
                    while ((c = is.read()) != -1) {
                        if (c == '<') {
                            is.mark(1024);
                            ByteStreams.readFully(is, buf);
                            is.reset();
                            if (tag.equals(new String(buf, StandardCharsets.UTF_8))) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (found) {
                        fos.write('<');
                        ByteStreams.copy(is, fos);
                    }
                }
            }
            fos.write("</testsuites>\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private static class OutputFileEntry {

        private enum Type {
            OUTPUT_FILE("build/"),
            LOCAL_MAVEN_FILE("m2/");

            final String prefix;

            Type(String prefix) {
                this.prefix = prefix;
            }
        }

        final String from;
        final Type type;
        final Path to;

        OutputFileEntry(String from, String to) {
            this.to = Paths.get(to);
            for (Type value : Type.values()) {
                if (from.startsWith(value.prefix)) {
                    this.type = value;
                    this.from = from.substring(value.prefix.length());
                    return;
                }
            }
            throw new RuntimeException("Unknown output entry type for " + from);
        }

        void collect(Path outputDir, Path m2Repository) throws IOException {
            Path searchDir = getSearchDir(outputDir, m2Repository);
            PathMatcher pathMatcher = searchDir.getFileSystem().getPathMatcher("glob:" + from);

            PathFinder pathFinder = new PathFinder(searchDir, pathMatcher);
            Files.walkFileTree(searchDir, pathFinder);
            Preconditions.checkNotNull(
                    pathFinder.result, "Output file %s not found in %s", from, searchDir);
            Files.copy(pathFinder.result, to);
        }

        private class PathFinder extends SimpleFileVisitor<Path> {

            private final Path searchDir;
            private final PathMatcher search;
            Path result = null;

            private PathFinder(Path searchDir, PathMatcher search) {
                this.searchDir = searchDir;
                this.search = search;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (search.matches(searchDir.relativize(file))) {
                    if (result != null) {
                        throw new IOException(
                                "Multiple matches for "
                                        + from
                                        + " in dir "
                                        + searchDir
                                        + "\n 1. "
                                        + result
                                        + "\n 2. "
                                        + file);
                    }
                    result = file;
                }
                return FileVisitResult.CONTINUE;
            }
        }

        private Path getSearchDir(Path outputDir, Path m2Repository) {
            switch (type) {
                case OUTPUT_FILE:
                    return outputDir;
                case LOCAL_MAVEN_FILE:
                    return m2Repository;
            }
            throw new IllegalStateException("Unknown output entry type " + type.toString());
        }
    }

    private static class TeeOutputStream extends OutputStream {

        private final ImmutableList<OutputStream> delegates;

        TeeOutputStream(ImmutableList<OutputStream> delegates) {
            this.delegates = delegates;
        }

        TeeOutputStream(OutputStream... delegates) {
            this(ImmutableList.copyOf(delegates));
        }

        @Override
        public void write(int b) throws IOException {
            for (OutputStream delegate : delegates) {
                delegate.write(b);
            }
        }

        @Override
        public void write(@NonNull byte[] b) throws IOException {
            for (OutputStream delegate : delegates) {
                delegate.write(b);
            }
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            for (OutputStream delegate : delegates) {
                delegate.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            for (OutputStream delegate : delegates) {
                delegate.flush();
            }
        }

        @Override
        public void close() throws IOException {
            for (OutputStream delegate : delegates) {
                delegate.close();
            }
        }
    }
}
