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

package com.android.tools.kotlin;

import com.android.tools.utils.JarOutputCompiler;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import org.jetbrains.kotlin.cli.common.CLICompiler;
import org.jetbrains.kotlin.cli.common.ExitCode;
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler;


/**
 * A wrapper for the Kotlin compiler.
 */
public class KotlinCompiler extends JarOutputCompiler {

    private String moduleName;
    private String jvmTarget = "1.8";
    private List<String> friends = new LinkedList<>();
    private boolean noJdk = false;

    protected KotlinCompiler() {
        super("kotlinc");
        moduleName = "dummy";
    }

    public static void main(String[] args) throws IOException {
        System.exit(new KotlinCompiler().run(Arrays.asList(args)));
    }

    @Override
    protected List<String> filterOptions(List<String> args) {
        LinkedList<String> filtered = new LinkedList<>();
        Iterator<String> it = args.iterator();
        while (it.hasNext()) {
            String arg = it.next();
            if (arg.equals("--module_name") && it.hasNext()) {
                moduleName = it.next();
            } else if (arg.equals("--friend_dir") && it.hasNext()) {
                friends.add(it.next());
            } else if (arg.equals("--jvm-target") && it.hasNext()) {
                jvmTarget = it.next();
            } else if (arg.equals("--no-jdk")) {
                noJdk = true;
            } else {
                filtered.add(arg);
            }
        }
        return filtered;
    }

    @Override
    protected boolean compile(List<String> files, String classPath, File outDir) {
        // Extracted from CLICompiler.java:
        // We depend on swing (indirectly through PSI or something), so we want to declare headless mode,
        // to avoid accidentally starting the UI thread
        System.setProperty("java.awt.headless", "true");
        List<String> args = new ArrayList<>(files.size() + 10);

        try {
            File xml = new File(outDir.getParentFile(), outDir.getName() + ".xml");
            try (PrintWriter writer = new PrintWriter(xml)) {
                writer.println("<modules>");
                writer.print(
                        "<module name=\""
                                + moduleName
                                + "\" type=\"java-production\" outputDir=\"");
                writer.print(outDir.getAbsolutePath());
                writer.println("\">");

                for (String file : files) {
                    String dir = file;
                    String prefix = null;
                    if (file.contains(":")) {
                        String[] split = file.split(":");
                        dir = split[0];
                        prefix = split[1];
                    }
                    File dirFile = new File(dir);
                    if (dirFile.exists()) {
                        // Workaround KT-27775: list all kt files in the directory
                        Files.walk(dirFile.toPath())
                                .filter(f -> isKt(f))
                                .forEach(f -> writeSourcePath(writer, f));
                        writer.print("<javaSourceRoots path=\"");
                        writer.print(new File(dir).getAbsolutePath());
                        if (prefix != null) {
                            writer.print("\" packagePrefix=\"");
                            writer.print(prefix);
                        }
                        writer.println("\"/>");
                    }
                }
                for (String friend : friends) {
                    File friendDir = new File(friend);
                    if (friendDir.exists()) {
                        writer.print("<friendDir path=\"");
                        writer.print(friendDir.getAbsolutePath());
                        writer.println("\"/>");
                    }
                }
                String[] cp = classPath.split(":");
                for (String s : cp) {
                    writer.print("<classpath path=\"");
                    writer.print(new File(s).getAbsolutePath());
                    writer.println("\"/>");
                }
                writer.println("</module>");
                writer.println("</modules>");
            }

            args.add("-Xbuild-file=" + xml.getAbsolutePath());
            if (jvmTarget != null) {
                args.add("-jvm-target");
                args.add(jvmTarget);
            }
            if (noJdk) {
                args.add("-no-jdk");
            }

            ExitCode exit =
                    CLICompiler.doMainNoExit(new K2JVMCompiler(), args.toArray(new String[] {}));
            xml.delete();
            ensureManifestFile(outDir);
            return exit == ExitCode.OK;

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean isKt(Path f) {
        return Files.isRegularFile(f) && f.toString().endsWith(".kt");
    }

    private void writeSourcePath(PrintWriter writer, Path p) {
        writer.print("<sources path=\"");
        writer.print(p.toAbsolutePath());
        writer.println("\"/>");
    }

    private void ensureManifestFile(File outDir) throws FileNotFoundException {
        File manifest = new File(outDir, "META-INF/MANIFEST.MF");
        if (!manifest.exists()) {
            manifest.getParentFile().mkdirs();
            try(PrintWriter writer = new PrintWriter(manifest)) {
                writer.println("Manifest-Version: 1.0");
            }
        }
    }
}
