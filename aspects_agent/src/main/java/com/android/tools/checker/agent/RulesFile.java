package com.android.tools.checker.agent;

import com.android.annotations.NonNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

class RulesFile {
    @NonNull
    static Map<String, String> parserRulesFile(
            @NonNull String filePath,
            @NonNull Function<String, String> aspectKeyProcessor,
            @NonNull Function<String, String> aspectValueProcessor)
            throws IOException {
        // Lines are expected to have the format "com.intellij.openapi.vfs.VfsUtil.copy=#warn"
        return Files.readAllLines(new File(filePath).toPath())
                .stream()
                .map(String::trim)
                // Ignore comments and lines not containing "="
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(
                        Collectors.toMap(
                                line ->
                                        aspectKeyProcessor.apply(
                                                line.substring(0, line.indexOf('='))),
                                line ->
                                        aspectValueProcessor.apply(
                                                line.substring(line.indexOf('=') + 1))));
    }

    @NonNull
    static Map<String, String> parserRulesFile(@NonNull String filePath) throws IOException {
        return parserRulesFile(filePath, Function.identity(), Function.identity());
    }
}
