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

package com.android.build.gradle.internal.cxx.json;

import com.android.annotations.NonNull;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.Closeable;
import java.io.IOException;

/**
 * General purpose parser for android_build_gradle.json file. This parser is streaming so that the
 * entire Json file is never held in memory all at once.
 */
public abstract class AndroidBuildGradleJsonStreamingParser implements Closeable {

    @NonNull private final JsonReader reader;

    protected AndroidBuildGradleJsonStreamingParser(@NonNull JsonReader reader) {
        this.reader = reader;
    }

    /** Main entry point to the streaming parser. */
    public void parse() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "buildFiles":
                    parseBuildFiles();
                    break;
                case "cleanCommands":
                    parseCleanCommands();
                    break;
                case "cFileExtensions":
                    parseCFileExtensions();
                    break;
                case "cppFileExtensions":
                    parseCppFileExtensions();
                    break;
                case "libraries":
                    parseLibraries();
                    break;
                case "toolchains":
                    parseToolchains();
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endObject();
    }

    /**
     * The purpose of this logic is to read unrecognized Json sections. This should be an unusual
     * situation since this parser is supposed to fully recognized known Json. One situation may be
     * the case where an older gradle plugin finds itself trying to build or clean against a folder
     * created by a more recent gradle plugin.
     */
    private void parseUnknown() throws IOException {
        JsonToken peek = reader.peek();
        switch (peek) {
            case BEGIN_OBJECT:
                parseUnknownObject();
                break;
            case BEGIN_ARRAY:
                parseUnknownArray();
                break;
            case STRING:
                reader.nextString();
                break;
            case NAME:
                reader.nextName();
                break;
            case NULL:
                reader.nextNull();
                break;
            case NUMBER:
                reader.nextString();
                break;
            case BOOLEAN:
                reader.nextBoolean();
                break;
            default:
                // The switch statement is supposed to cover all JsonToken possible right after
                // parsing an earlier object/array/string. It doesn't include END_* tokens because
                // there should be no unmatched BEGIN_* tokens in the rest of this parser.
                throw new RuntimeException(
                        String.format(
                                "Unexpected: Saw Gson token '%s' while parsing "
                                        + "new and unrecognized Json section. ",
                                peek.toString()));
        }
    }

    private void parseUnknownArray() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            parseUnknown();
        }
        reader.endArray();
    }

    private void parseUnknownObject() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            parseUnknown();
        }
        reader.endObject();
    }

    private void parseLibraryObject() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "abi":
                    visitLibraryAbi(reader.nextString());
                    break;
                case "artifactName":
                    visitLibraryArtifactName(reader.nextString());
                    break;
                case "buildCommand":
                    visitLibraryBuildCommand(reader.nextString());
                    break;
                case "buildType":
                    visitLibraryBuildType(reader.nextString());
                    break;
                case "output":
                    visitLibraryOutput(reader.nextString());
                    break;
                case "toolchain":
                    visitLibraryToolchain(reader.nextString());
                    break;
                case "groupName":
                    visitLibraryGroupName(reader.nextString());
                    break;
                case "files":
                    parseLibraryFiles();
                    break;
                case "runtimeFiles":
                    parseLibraryRuntimeFiles();
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseToolchainObject() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "cCompilerExecutable":
                    visitToolchainCCompilerExecutable(reader.nextString());
                    break;
                case "cppCompilerExecutable":
                    visitToolchainCppCompilerExecutable(reader.nextString());
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseLibraryFileObject() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "flags":
                    visitLibraryFileFlags(reader.nextString());
                    break;
                case "src":
                    visitLibraryFileSrc(reader.nextString());
                    break;
                case "workingDirectory":
                    visitLibraryFileWorkingDirectory(reader.nextString());
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endObject();
    }

    private void parseBuildFiles() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            JsonToken peek = reader.peek();
            switch (reader.peek()) {
                case STRING:
                    visitBuildFile(reader.nextString());
                    break;
                case BEGIN_OBJECT:
                    reader.beginObject();
                    String name = reader.nextName();
                    switch (name) {
                        case "path":
                            visitBuildFile(reader.nextString());
                            break;
                        default:
                            parseUnknown();
                            break;
                    }
                    reader.endObject();
                    break;
                default:
                    parseUnknown();
                    break;
            }
        }
        reader.endArray();
    }

    private void parseCleanCommands() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            String value = reader.nextString();
            visitCleanCommands(value);
        }
        reader.endArray();
    }

    private void parseCFileExtensions() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            String value = reader.nextString();
            visitCFileExtensions(value);
        }
        reader.endArray();
    }

    private void parseCppFileExtensions() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            String value = reader.nextString();
            visitCppFileExtensions(value);
        }
        reader.endArray();
    }

    private void parseLibraries() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            beginLibrary(name);
            parseLibraryObject();
            endLibrary();
        }
        reader.endObject();
    }

    private void parseToolchains() throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            beginToolchain(name);
            parseToolchainObject();
            endToolchain();
        }
        reader.endObject();
    }

    private void parseLibraryFiles() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            beginLibraryFile();
            parseLibraryFileObject();
            endLibraryFile();
        }
        reader.endArray();
    }

    private void parseLibraryRuntimeFiles() throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            visitLibraryRuntimeFile(reader.nextString());
        }
        reader.endArray();
    }

    protected void beginLibrary(@NonNull String libraryName) {}

    protected void endLibrary() {}

    protected void beginLibraryFile() {}

    protected void endLibraryFile() {}

    protected void beginToolchain(@NonNull String toolchain) {}

    protected void endToolchain() {}

    protected void visitBuildFile(@NonNull String buildFile) {}

    protected void visitLibraryAbi(@NonNull String abi) {}

    protected void visitLibraryArtifactName(@NonNull String artifact) {}

    protected void visitLibraryBuildCommand(@NonNull String buildCommand) {}

    protected void visitLibraryBuildType(@NonNull String buildCommand) {}

    protected void visitLibraryOutput(@NonNull String output) {}

    protected void visitLibraryToolchain(@NonNull String toolchain) {}

    protected void visitLibraryGroupName(@NonNull String groupName) {}

    protected void visitToolchainCCompilerExecutable(@NonNull String executable) {}

    protected void visitToolchainCppCompilerExecutable(@NonNull String executable) {}

    protected void visitLibraryFileFlags(@NonNull String flags) {}

    protected void visitLibraryFileSrc(@NonNull String src) {}

    protected void visitLibraryFileWorkingDirectory(@NonNull String workingDirectory) {}

    protected void visitCleanCommands(@NonNull String cleanCommand) {}

    protected void visitCFileExtensions(@NonNull String buildFile) {}

    protected void visitCppFileExtensions(@NonNull String buildFile) {}

    protected void visitLibraryRuntimeFile(@NonNull String runtimeFile) {}

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
