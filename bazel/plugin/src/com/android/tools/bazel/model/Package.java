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

package com.android.tools.bazel.model;

import com.android.tools.bazel.parser.BuildParser;
import com.android.tools.bazel.parser.Tokenizer;
import com.android.tools.bazel.parser.ast.Build;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class Package {
    private static final Logger LOG = Logger.getInstance(BuildParser.class);

    private final String name;
    private final Workspace workspace;
    private Map<String, BazelRule> rules = Maps.newLinkedHashMap();
    private Build buildFile;

    public Package(Workspace workspace, String name) {
        this.workspace = workspace;
        this.name = name;
    }

    public Build getBuildFile() throws IOException {
        if (buildFile == null) {
            File file = buildFile(getPackageDir());
            Tokenizer tokenizer = new Tokenizer(file);
            BuildParser parser = new BuildParser(tokenizer);
            buildFile = parser.parse();
            buildFile.hideManagedStatements();
        }
        return buildFile;
    }

    @NotNull
    private File buildFile(@NotNull File dir) {
        File buildDotBazelFile = new File(dir, "BUILD.bazel");
        return buildDotBazelFile.isFile() ? buildDotBazelFile : new File(dir, "BUILD");
    }

    public void generate(PrintWriter progress) throws IOException {
        if (workspace == null) return;

        boolean hasRules = false;
        for (BazelRule rule : rules.values()) {
            if (rule.isExport()) {
                hasRules = true;
                break;
            }
        }
        if (!hasRules) return;

        File dir = getPackageDir();
        File build = buildFile(dir);
        for (BazelRule rule : rules.values()) {
            if (!rule.isEmpty() && rule.isExport() && rule.shouldUpdate()) {
                rule.update();
            }
        }

        if (buildFile != null) {
            File tmp = File.createTempFile("BUILD", "test");
            try (FileOutputStream fileOutputStream = new FileOutputStream(tmp);
                 PrintWriter writer = new PrintWriter(fileOutputStream)) {
                buildFile.write(writer);
            }
            String before = Files.toString(build, StandardCharsets.UTF_8);
            String after = Files.toString(tmp, StandardCharsets.UTF_8);
            if (!before.equals(after)) {
                progress.append("Updated " + name + "/BUILD\n");
                LOG.info("Build file changed: " + name);
                LOG.info("idea diff " + build.getAbsolutePath() + " " + tmp.getAbsolutePath());
                Files.copy(tmp, build);
            }
        }
    }

    public BazelRule getRule(String name) {
        return rules.get(name.toLowerCase());
    }

    public ImmutableSet<BazelRule> getRules() {
        return ImmutableSet.copyOf(rules.values());
    }

    @NotNull
    public File getPackageDir() {
        return new File(workspace.getDirectory(), name);
    }

    public String getName() {
        return name;
    }

    public void addRule(BazelRule rule) {
        if (rules.get(rule.getName().toLowerCase()) != null) {
            throw new IllegalStateException("Duplicated rule " + rule.getName());
        }
        rules.put(rule.getName().toLowerCase(), rule);
    }
}
