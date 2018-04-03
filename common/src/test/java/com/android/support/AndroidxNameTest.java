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
package com.android.support;

import static org.junit.Assert.*;

import org.junit.Test;

public class AndroidxNameTest {

    @Test
    public void className() {
        AndroidxName pkgName =
                AndroidxName.of("android.support.design.widget.", "FloatingActionButton");
        assertEquals("android.support.design.widget.FloatingActionButton", pkgName.oldName());
        assertEquals("com.google.android.material.widget.FloatingActionButton", pkgName.newName());

        // Test a non-existent class name
        pkgName = AndroidxName.of("android.support.wear.", "TestClassName");
        assertEquals("android.support.wear.TestClassName", pkgName.oldName());
        assertEquals("androidx.wear.TestClassName", pkgName.newName());

        // Test a non-existent class name with a subpackage
        pkgName = AndroidxName.of("android.support.wear.subpackage.", "TestClassName");
        assertEquals("android.support.wear.subpackage.TestClassName", pkgName.oldName());
        assertEquals("androidx.wear.subpackage.TestClassName", pkgName.newName());

        pkgName = AndroidxName.of("android.support.v4.widget.", "TestClassName");
        assertEquals("android.support.v4.widget.TestClassName", pkgName.oldName());
        assertEquals("androidx.core.widget.TestClassName", pkgName.newName());

        pkgName = AndroidxName.of("android.support.v4.widget.", "TestClassName");
        assertEquals("android.support.v4.widget.TestClassName", pkgName.oldName());
        assertEquals("androidx.core.widget.TestClassName", pkgName.newName());
    }

    @Test
    public void specificClass() {
        AndroidxName className = AndroidxName.of("android.support.v4.view.", "PagerTabStrip");
        assertEquals("android.support.v4.view.PagerTabStrip", className.oldName());
        assertEquals("androidx.viewpager.widget.PagerTabStrip", className.newName());
    }

    @Test
    public void pkgName() {
        AndroidxName pkgName = AndroidxName.of("android.support.v4.app.");
        assertEquals("android.support.v4.app.", pkgName.oldName());
        assertEquals("androidx.core.app.", pkgName.newName());

        pkgName = AndroidxName.of("android.arch.persistence.room.");
        assertEquals("android.arch.persistence.room.", pkgName.oldName());
        assertEquals("androidx.room.", pkgName.newName());

        // Test a non-existent name
        pkgName = AndroidxName.of("android.support.wear.test.");
        assertEquals("android.support.wear.test.", pkgName.oldName());
        assertEquals("androidx.wear.test.", pkgName.newName());
    }

    @Test
    public void getNewName() {
        assertEquals(
                "com.google.android.material.widget.FloatingActionButton",
                AndroidxNameUtils.getNewName("android.support.design.widget.FloatingActionButton"));
        assertEquals(
                "unknown.package.FloatingActionButton",
                AndroidxNameUtils.getNewName("unknown.package.FloatingActionButton"));
        assertEquals("FloatingActionButton", AndroidxNameUtils.getNewName("FloatingActionButton"));
    }

    @Test
    public void innerClassHandling() {
        AndroidxName className =
                AndroidxName.of("android.support.v7.widget.", "RecyclerView$Adapter");
        assertEquals("androidx.recyclerview.widget.RecyclerView$Adapter", className.newName());
    }
}
