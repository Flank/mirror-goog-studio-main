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

package com.android.tools.bazel;

import com.google.common.collect.ImmutableSet;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import gnu.trove.TIntArrayList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension;
import org.jetbrains.jps.model.java.JpsJavaDependencyScope;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleDependency;

/**
 * A graph of jps modules that can return transitive closures and strongly connected components.
 */
class JpsGraph {

    private final LinkedHashMap<JpsModule, Set<JpsModule>> closures;
    private final List<List<JpsModule>> components;

    public JpsGraph(JpsProject project, ImmutableSet<JpsJavaDependencyScope> scope) {
        closures = new LinkedHashMap<>();
        components = new ArrayList<>();

        Graph<JpsModule> graph = createGraph(project, scope);
        DFSTBuilder<JpsModule> builder = new DFSTBuilder<>(graph);
        // Loops through the module in reverse topological order and build the transitive closure
        TIntArrayList scCs = builder.getSCCs();
        int k = 0;
        for (int i = 0; i < scCs.size(); i++) {
            int s = scCs.get(i);
            LinkedHashSet<JpsModule> closure = new LinkedHashSet<>();
            List<JpsModule> component = new ArrayList<>(s);
            for (int j = 0; j < s; j++) {
                component.add(builder.getNodeByTNumber(k + j));
            }
            components.add(component);
            closure.addAll(component);
            for (JpsModule module : component) {
                Iterator<JpsModule> it = graph.getIn(module);
                while (it.hasNext()) {
                    JpsModule dependency = it.next();
                    if (!closure.contains(dependency)) {
                        closure.add(dependency);
                        closure.addAll(closures.get(dependency));
                    }
                }
            }
            for (JpsModule module : component) {
                closures.put(module, closure);
            }
            k += s;
        }
    }

    private static Graph<JpsModule> createGraph(JpsProject project, Set<JpsJavaDependencyScope> scopes) {
        return GraphGenerator.create(new GraphGenerator.SemiGraph<JpsModule>() {
            @Override
            public Collection<JpsModule> getNodes() {
                return project.getModules();
            }

            @Override
            public Iterator<JpsModule> getIn(JpsModule jpsModule) {
                List<JpsDependencyElement> deps = jpsModule.getDependenciesList().getDependencies();
                List<JpsModule> ins = new ArrayList<>();
                for (JpsDependencyElement dep : deps) {
                    JpsJavaDependencyExtension extension = JpsJavaExtensionService.getInstance()
                            .getDependencyExtension(dep);
                    if (dep instanceof JpsModuleDependency && extension != null &&
                            scopes.contains(extension.getScope())) {
                        ins.add(((JpsModuleDependency) dep).getModule());
                    }
                }
                return ins.iterator();
            }
        });
    }

    public Set<JpsModule> getModules() {
        return closures.keySet();
    }

    public Set<JpsModule> getClosure(JpsModule module) {
        return closures.get(module);
    }

    public List<List<JpsModule>> getConnectedComponents() {
        return components;
    }
}
