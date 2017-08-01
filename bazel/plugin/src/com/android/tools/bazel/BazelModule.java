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

package com.android.tools.bazel;

import com.android.tools.bazel.model.ImlModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.util.*;

/**
 * A collection of modules with cyclic dependencies that form a strongly connected component.
 */
public class BazelModule {

    private static final Comparator<Module> BY_NAME = Comparator.comparing(Module::getName);

    private static final Comparator<Module> BY_NUM_ORDER_ENTRIES =
            Comparator.comparingInt(
                    module -> ModuleRootManager.getInstance(module).getOrderEntries().length);

    private SortedSet<Module> modules = new TreeSet<>(BY_NAME);

    public ImlModule rule = null;

    public BazelModule() {
    }

    public void add(Module module) {
        modules.add(module);
    }

    public String getName() {
        return modules.stream().max(BY_NUM_ORDER_ENTRIES).get().getName()
            + (isSingle() ? "" : "_and_others");
    }

    public boolean isSingle() {
        return modules.size() == 1;
    }

    public File getBaseDir() {
        List<VirtualFile> vfs = new LinkedList<>();
        for (Module module : modules) {
            VirtualFile vf = VfsUtil.findFileByIoFile(new File(module.getModuleFilePath()).getParentFile(), true);
            vfs.add(vf);
        }
        VirtualFile file = VfsUtil.getCommonAncestor(vfs);
        return VfsUtil.virtualToIoFile(file);
    }

    public List<ContentEntry> getContentEntries() {
        List<ContentEntry> entries = new LinkedList<>();
        for (Module module : modules) {
            final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            Collections.addAll(entries, rootManager.getContentEntries());
        }
        return entries;

    }

    public List<OrderEntry> getOrderEntries() {
        List<OrderEntry> entries = new LinkedList<>();
        for (Module module : modules) {
            final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            Collections.addAll(entries, rootManager.getOrderEntries());
        }
        return entries;
    }

    public Set<Module> getModules() {
        return modules;
    }
}
