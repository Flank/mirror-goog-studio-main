/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.build.gradle.internal;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.Description;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Identifier;
import static org.gradle.internal.logging.text.StyledTextOutput.Style.Info;

import com.android.annotations.NonNull;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.dependency.level2.Dependency;
import com.android.builder.dependency.level2.DependencyContainer;
import com.android.builder.dependency.level2.DependencyNode;
import com.android.builder.dependency.level2.JavaDependency;
import com.android.builder.model.MavenCoordinates;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableList;

import java.util.Map;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.diagnostics.internal.TextReportRenderer;
import org.gradle.internal.graph.GraphRenderer;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.util.GUtil;

import java.io.IOException;
import java.util.List;

/**
 * android version of the AsciiReportRenderer that outputs Android Library dependencies.
 */
public class AndroidAsciiReportRenderer extends TextReportRenderer {
    private boolean hasConfigs;
    private boolean hasCyclicDependencies;
    private GraphRenderer renderer;
    private Project project;

    @Override
    public void startProject(Project project) {
        this.project = project;
        super.startProject(project);
        hasConfigs = false;
        hasCyclicDependencies = false;
    }

    @Override
    public void completeProject(Project project) {
        if (!hasConfigs) {
            getTextOutput().withStyle(Info).println("No dependencies");
        }
        super.completeProject(project);
    }

    public void startVariant(final BaseVariantData variantData) {
        if (hasConfigs) {
            getTextOutput().println();
        }
        hasConfigs = true;
        renderer = new GraphRenderer(getTextOutput());
        renderer.visit(new Action<StyledTextOutput>() {
            @Override
            public void execute(StyledTextOutput styledTextOutput) {
                getTextOutput().withStyle(Identifier).text(
                        variantData.getVariantConfiguration().getFullName());
                getTextOutput().withStyle(Description).text("");
            }
        }, true);
    }

    private String getDescription(Configuration configuration) {
        return GUtil.isTrue(
                configuration.getDescription()) ? " - " + configuration.getDescription() : "";
    }

    public void render(BaseVariantData variantData) throws IOException {
        VariantDependencies variantDependency = variantData.getVariantDependency();

        renderNow(variantDependency.getCompileDependencies());
    }

    void renderNow(@NonNull DependencyContainer compileDependencies) {
        final ImmutableList<DependencyNode> dependencies = compileDependencies.getDependencies();

        if (dependencies.isEmpty()) {
            getTextOutput().withStyle(Info).text("No dependencies");
            getTextOutput().println();
            return;
        }

        renderChildren(dependencies, compileDependencies.getDependencyMap());
    }

    @Override
    public void complete() {
        if (hasCyclicDependencies) {
            getTextOutput().withStyle(Info).println(
                    "\n(*) - dependencies omitted (listed previously)");
        }

        super.complete();
    }

    private void render(
            @NonNull final DependencyNode node,
            @NonNull Map<Object, Dependency> dependencyMap,
            boolean lastChild) {
        renderer.visit(styledTextOutput -> {
            String name = node.getAddress().toString();
            Dependency dependency = dependencyMap.get(name);

            if (dependency instanceof JavaDependency) {
                JavaDependency javaDependency = (JavaDependency) dependency;
                if (javaDependency.isLocal()) {
                    String path = FileUtils.relativePath(
                            javaDependency.getArtifactFile(), project.getProjectDir());
                    name = path;
                }
            }

            getTextOutput().text(name);
        }, lastChild);

        renderChildren(node.getDependencies(), dependencyMap);
    }

    private void renderChildren(
            @NonNull List<DependencyNode> dependencyNodes,
            @NonNull Map<Object, Dependency> dependencyMap) {
        renderer.startChildren();

        final int count = dependencyNodes.size();
        for (int i = 0; i < count; i++) {
            DependencyNode node = dependencyNodes.get(i);
            render(node, dependencyMap, i == count - 1);
        }
        renderer.completeChildren();
    }
}
