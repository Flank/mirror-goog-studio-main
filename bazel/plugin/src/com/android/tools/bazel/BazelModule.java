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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * A collection of modules with cyclic dependencies that form a strongly connected component.
 */
public class BazelModule {

    private List<Module> modules = new LinkedList<>();

    public ImlModule rule = null;

    public BazelModule() {
    }

    public void add(Module module) {
        modules.add(module);
    }

    private static final Function<Module, Integer> GET_NUM_ORDER_ENTRIES =
        module -> ModuleRootManager.getInstance(module).getOrderEntries().length;
    private static final Comparator<Module> BY_NUM_ORDER_ENTRIES =
        (m1, m2) -> GET_NUM_ORDER_ENTRIES.apply(m1) - GET_NUM_ORDER_ENTRIES.apply(m2);

    public String getName() {
        return (isSingle() ? "" : "MM_") + modules.stream().max(BY_NUM_ORDER_ENTRIES).get().getName();
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
}
