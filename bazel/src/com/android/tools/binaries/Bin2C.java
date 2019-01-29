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

package com.android.tools.binaries;

import com.google.common.collect.Lists;
import com.google.common.hash.Hashing;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.List;

class Bin2C {
    private static final int LINE_SIZE = 12;

    interface OptionProcessor {
        void process(String value);
    }

    private Hashtable<String, OptionProcessor> optionDispatcher = null;

    enum Language {
        JAVA,
        CXX,
        RAW
    }

    private Language language = Language.CXX;
    private String output = null;
    private String header = null;
    private String variableBaseName = null;
    private String namespace = null;
    private final List<String> inputs = Lists.newArrayList();
    private boolean embed = true;

    public static Language lookupLanguage(String lan) {
        for (Language language : Language.values()) {
            if (language.name().equalsIgnoreCase(lan)) {
                return language;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            Bin2C bin2C = new Bin2C(args);
            bin2C.run();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Bin2C(String[] args) {
        optionDispatcher = new Hashtable<>();
        optionDispatcher.put("lang", v -> language = lookupLanguage(v));
        optionDispatcher.put("output", v -> output = v);
        optionDispatcher.put("header", v -> header = v);
        optionDispatcher.put("variable", v -> parseVariable(v));
        optionDispatcher.put("embed", v -> embed = Boolean.parseBoolean(v));
        parseOptions(args);
    }

    private void parseVariable(String value) {
        String separator;
        if (value.contains("::")) {
            separator = "::";
        } else if (value.contains(".")) {
            separator = ".";
        } else {
            namespace = "";
            variableBaseName = value;
            return;
        }
        int cutIndex = value.lastIndexOf(separator);
        int separatorSize = separator.length();
        namespace = value.substring(0, cutIndex);
        variableBaseName = value.substring(cutIndex + separatorSize);
    }

    private void parseOptions(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            // Input files
            if (arg.charAt(0) != '-') {
                inputs.add(arg);
            } else {
                String[] tokens = arg.split("=");
                if (tokens.length != 2) {
                    throw new RuntimeException("Option '" + arg + "' is malformed.");
                }
                String name = tokens[0].substring(1);
                String value = tokens[1];
                if (!optionDispatcher.containsKey(name)) {
                    printUsage();
                    throw new RuntimeException("Unknown option name '" + name + "'.");
                } else {
                    optionDispatcher.get(name).process(value);
                }
            }
        }
    }

    private void printUsage() {
        System.err.println("Usage bin2c [parameters] input_files");
        System.err.println("List of parameters:");
        System.err.println("    -lang=X        : language [java, cxx, raw]. Default is 'cxx'.");
        System.err.println("    -output=X      : The generated cc file");
        System.err.println("    -header=X      : An optional header file, only used in CC");
        System.err.println("    -variable=X    : Details how to generate source code");
        System.err.println("                     Formats: | my::name::space::variable_name");
        System.err.println("                              | my.package.name.VariableName");
        System.err.println("    -embed=X       : Embed file content in source code (java not yet)");
        System.err.println("                     Default: true");
    }

    void run() throws IOException {
        if (language == Language.JAVA && embed) {
            throw new RuntimeException("Embedding data in java source is not implemented.");
        }

        File outputFile = new File(output);
        File parentFile = outputFile.getParentFile();
        if (parentFile != null) {
            outputFile.getParentFile().mkdirs();
        }

        File headerFile = null;
        if (header != null) {
            headerFile = new File(header);
            headerFile.getParentFile().mkdirs();
        }

        if (inputs.isEmpty()) {
            throw new RuntimeException("No input files");
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (String input : inputs) {
            bos.write(Files.readAllBytes(Paths.get(input)));
        }
        bos.close();

        switch (language) {
            case CXX:
                generateCXX(outputFile, headerFile, bos.toByteArray());
                break;
            case JAVA:
                generateJava(outputFile, bos.toByteArray());
                break;
            case RAW:
                generateRaw(outputFile, bos.toByteArray());
                break;
        }
    }

    void generateCXX(File outputFile, File headerFile, byte[] buffer) throws IOException {
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            String hash = toHexHash(buffer);
            writer.println(String.format("const char* %s_hash = \"%s\";", variableBaseName, hash));
            if (!embed) {
                return;
            }

            writer.print("unsigned char " + variableBaseName + "[] = {");
            for (int i = 0; i < buffer.length; i++) {
                if (i % LINE_SIZE == 0) {
                    writer.println();
                }
                writer.write(String.format("0x%02x, ", buffer[i]));
            }
            writer.println("};");

            writer.println(
                    String.format("long long %s_len = %d;", variableBaseName, buffer.length));
        }

        if (headerFile != null) {
            try (PrintWriter writer = new PrintWriter(headerFile)) {
                writer.println(String.format("extern const char* %s_hash;", variableBaseName));
                if (!embed) {
                    return;
                }
                writer.println("extern unsigned char " + variableBaseName + "[];");
                writer.println(String.format("extern long long %s_len;", variableBaseName));
            }
        }
    }

    void generateJava(File outputFile, byte[] buffer) throws IOException {
        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println(String.format("package %s;", namespace));
            writer.println();
            writer.println(String.format("public class %s {", variableBaseName));
            String hash = toHexHash(buffer);
            writer.println(
                    String.format("    public static String hash() { return \"%s\"; }", hash));
            writer.println("}");
        }
    }

    void generateRaw(File outputfile, byte[] buffer) throws IOException {
        try (PrintWriter writer = new PrintWriter(outputfile)) {
            writer.print(toHexHash(buffer));
        }
    }

    String toHexHash(byte[] buffer) {
        return Hashing.sha256().hashBytes(buffer).toString().substring(0, 8);
    }
}
