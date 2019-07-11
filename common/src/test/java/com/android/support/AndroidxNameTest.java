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

import static org.junit.Assert.assertEquals;

import com.google.common.base.Splitter;
import org.junit.Test;

public class AndroidxNameTest {

    @Test
    public void className() {
        AndroidxName pkgName =
                AndroidxName.of("android.support.design.widget.", "FloatingActionButton");
        assertEquals("android.support.design.widget.FloatingActionButton", pkgName.oldName());
        assertEquals(
                "com.google.android.material.floatingactionbutton.FloatingActionButton",
                pkgName.newName());

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
                "com.google.android.material.floatingactionbutton.FloatingActionButton",
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

    /**
     * Verify that all the documented mappings in <a
     * href="https://developer.android.com/jetpack/androidx/migrate">ttps://developer.android.com/jetpack/androidx/migrate</a>
     * are correctly handled.
     */
    @Test
    public void documentedMappings() {
        // You can copy and paste this list directly from the artifact mappings table at:
        // https://developer.android.com/jetpack/androidx/migrate#artifact_mappings
        final String documentedMappings =
                "android.arch.core:common\tandroidx.arch.core:core-common:2.0.0-rc01\n"
                        + "android.arch.core:core\tandroidx.arch.core:core:2.0.0-rc01\n"
                        + "android.arch.core:core-testing\tandroidx.arch.core:core-testing:2.0.0-rc01\n"
                        + "android.arch.core:runtime\tandroidx.arch.core:core-runtime:2.0.0-rc01\n"
                        + "android.arch.lifecycle:common\tandroidx.lifecycle:lifecycle-common:2.0.0-rc01\n"
                        + "android.arch.lifecycle:common-java8\tandroidx.lifecycle:lifecycle-common-java8:2.0.0-rc01\n"
                        + "android.arch.lifecycle:compiler\tandroidx.lifecycle:lifecycle-compiler:2.0.0-rc01\n"
                        + "android.arch.lifecycle:extensions\tandroidx.lifecycle:lifecycle-extensions:2.0.0-rc01\n"
                        + "android.arch.lifecycle:livedata\tandroidx.lifecycle:lifecycle-livedata:2.0.0-rc01\n"
                        + "android.arch.lifecycle:livedata-core\tandroidx.lifecycle:lifecycle-livedata-core:2.0.0-rc01\n"
                        + "android.arch.lifecycle:reactivestreams\tandroidx.lifecycle:lifecycle-reactivestreams:2.0.0-rc01\n"
                        + "android.arch.lifecycle:runtime\tandroidx.lifecycle:lifecycle-runtime:2.0.0-rc01\n"
                        + "android.arch.lifecycle:viewmodel\tandroidx.lifecycle:lifecycle-viewmodel:2.0.0-rc01\n"
                        + "android.arch.paging:common\tandroidx.paging:paging-common:2.0.0-rc01\n"
                        + "android.arch.paging:runtime\tandroidx.paging:paging-runtime:2.0.0-rc01\n"
                        + "android.arch.paging:rxjava2\tandroidx.paging:paging-rxjava2:2.0.0-rc01\n"
                        + "android.arch.persistence.room:common\tandroidx.room:room-common:2.0.0-rc01\n"
                        + "android.arch.persistence.room:compiler\tandroidx.room:room-compiler:2.0.0-rc01\n"
                        + "android.arch.persistence.room:guava\tandroidx.room:room-guava:2.0.0-rc01\n"
                        + "android.arch.persistence.room:migration\tandroidx.room:room-migration:2.0.0-rc01\n"
                        + "android.arch.persistence.room:runtime\tandroidx.room:room-runtime:2.0.0-rc01\n"
                        + "android.arch.persistence.room:rxjava2\tandroidx.room:room-rxjava2:2.0.0-rc01\n"
                        + "android.arch.persistence.room:testing\tandroidx.room:room-testing:2.0.0-rc01\n"
                        + "android.arch.persistence:db\tandroidx.sqlite:sqlite:2.0.0-rc01\n"
                        + "android.arch.persistence:db-framework\tandroidx.sqlite:sqlite-framework:2.0.0-rc01\n"
                        + "com.android.support.constraint:constraint-layout\tandroidx.constraintlayout:constraintlayout:1.1.2\n"
                        + "com.android.support.constraint:constraint-layout-solver\tandroidx.constraintlayout:constraintlayout-solver:1.1.2\n"
                        + "com.android.support.test.espresso.idling:idling-concurrent\tandroidx.test.espresso.idling:idling-concurrent:3.1.0\n"
                        + "com.android.support.test.espresso.idling:idling-net\tandroidx.test.espresso.idling:idling-net:3.1.0\n"
                        + "com.android.support.test.espresso:espresso-accessibility\tandroidx.test.espresso:espresso-accessibility:3.1.0\n"
                        + "com.android.support.test.espresso:espresso-contrib\tandroidx.test.espresso:espresso-contrib:3.1.0\n"
                        + "com.android.support.test.espresso:espresso-core\tandroidx.test.espresso:espresso-core:3.1.0\n"
                        + "com.android.support.test.espresso:espresso-idling-resource\tandroidx.test.espresso:espresso-idling-resource:3.1.0\n"
                        + "com.android.support.test.espresso:espresso-intents\tandroidx.test.espresso:espresso-intents:3.1.0\n"
                        + "com.android.support.test.espresso:espresso-remote\tandroidx.test.espresso:espresso-remote:3.1.0\n"
                        + "com.android.support.test.espresso:espresso-web\tandroidx.test.espresso:espresso-web:3.1.0\n"
                        + "com.android.support.test.janktesthelper:janktesthelper\tandroidx.test.jank:janktesthelper:1.0.1\n"
                        + "com.android.support.test.services:test-services\tandroidx.test:test-services:1.1.0\n"
                        + "com.android.support.test.uiautomator:uiautomator\tandroidx.test.uiautomator:uiautomator:2.2.0\n"
                        + "com.android.support.test:monitor\tandroidx.test:monitor:1.1.0\n"
                        + "com.android.support.test:orchestrator\tandroidx.test:orchestrator:1.1.0\n"
                        + "com.android.support.test:rules\tandroidx.test:rules:1.1.0\n"
                        + "com.android.support.test:runner\tandroidx.test:runner:1.1.0\n"
                        + "com.android.support:animated-vector-drawable\tandroidx.vectordrawable:vectordrawable-animated:1.0.0\n"
                        + "com.android.support:appcompat-v7\tandroidx.appcompat:appcompat:1.0.0\n"
                        + "com.android.support:asynclayoutinflater\tandroidx.asynclayoutinflater:asynclayoutinflater:1.0.0\n"
                        + "com.android.support:car\tandroidx.car:car:1.0.0-alpha5\n"
                        + "com.android.support:cardview-v7\tandroidx.cardview:cardview:1.0.0\n"
                        + "com.android.support:collections\tandroidx.collection:collection:1.0.0\n"
                        + "com.android.support:coordinatorlayout\tandroidx.coordinatorlayout:coordinatorlayout:1.0.0\n"
                        + "com.android.support:cursoradapter\tandroidx.cursoradapter:cursoradapter:1.0.0\n"
                        + "com.android.support:customtabs\tandroidx.browser:browser:1.0.0\n"
                        + "com.android.support:customview\tandroidx.customview:customview:1.0.0\n"
                        + "com.android.support:design\tcom.google.android.material:material:1.0.0-rc01\n"
                        + "com.android.support:documentfile\tandroidx.documentfile:documentfile:1.0.0\n"
                        + "com.android.support:drawerlayout\tandroidx.drawerlayout:drawerlayout:1.0.0\n"
                        + "com.android.support:exifinterface\tandroidx.exifinterface:exifinterface:1.0.0\n"
                        + "com.android.support:gridlayout-v7\tandroidx.gridlayout:gridlayout:1.0.0\n"
                        + "com.android.support:heifwriter\tandroidx.heifwriter:heifwriter:1.0.0\n"
                        + "com.android.support:interpolator\tandroidx.interpolator:interpolator:1.0.0\n"
                        + "com.android.support:leanback-v17\tandroidx.leanback:leanback:1.0.0\n"
                        + "com.android.support:loader\tandroidx.loader:loader:1.0.0\n"
                        + "com.android.support:localbroadcastmanager\tandroidx.localbroadcastmanager:localbroadcastmanager:1.0.0\n"
                        + "com.android.support:media2\tandroidx.media2:media2:1.0.0-alpha03\n"
                        + "com.android.support:media2-exoplayer\tandroidx.media2:media2-exoplayer:1.0.0-alpha01\n"
                        + "com.android.support:mediarouter-v7\tandroidx.mediarouter:mediarouter:1.0.0\n"
                        + "com.android.support:multidex\tandroidx.multidex:multidex:2.0.0\n"
                        + "com.android.support:multidex-instrumentation\tandroidx.multidex:multidex-instrumentation:2.0.0\n"
                        + "com.android.support:palette-v7\tandroidx.palette:palette:1.0.0\n"
                        + "com.android.support:percent\tandroidx.percentlayout:percentlayout:1.0.0\n"
                        + "com.android.support:preference-leanback-v17\tandroidx.leanback:leanback-preference:1.0.0\n"
                        + "com.android.support:preference-v14\tandroidx.legacy:legacy-preference-v14:1.0.0\n"
                        + "com.android.support:preference-v7\tandroidx.preference:preference:1.0.0\n"
                        + "com.android.support:print\tandroidx.print:print:1.0.0\n"
                        + "com.android.support:recommendation\tandroidx.recommendation:recommendation:1.0.0\n"
                        + "com.android.support:recyclerview-selection\tandroidx.recyclerview:recyclerview-selection:1.0.0\n"
                        + "com.android.support:recyclerview-v7\tandroidx.recyclerview:recyclerview:1.0.0\n"
                        + "com.android.support:slices-builders\tandroidx.slice:slice-builders:1.0.0\n"
                        + "com.android.support:slices-core\tandroidx.slice:slice-core:1.0.0\n"
                        + "com.android.support:slices-view\tandroidx.slice:slice-view:1.0.0\n"
                        + "com.android.support:slidingpanelayout\tandroidx.slidingpanelayout:slidingpanelayout:1.0.0\n"
                        + "com.android.support:support-annotations\tandroidx.annotation:annotation:1.0.0\n"
                        + "com.android.support:support-compat\tandroidx.core:core:1.0.0\n"
                        + "com.android.support:support-content\tandroidx.contentpager:contentpager:1.0.0\n"
                        + "com.android.support:support-core-ui\tandroidx.legacy:legacy-support-core-ui:1.0.0\n"
                        + "com.android.support:support-core-utils\tandroidx.legacy:legacy-support-core-utils:1.0.0\n"
                        + "com.android.support:support-dynamic-animation\tandroidx.dynamicanimation:dynamicanimation:1.0.0\n"
                        + "com.android.support:support-emoji\tandroidx.emoji:emoji:1.0.0\n"
                        + "com.android.support:support-emoji-appcompat\tandroidx.emoji:emoji-appcompat:1.0.0\n"
                        + "com.android.support:support-emoji-bundled\tandroidx.emoji:emoji-bundled:1.0.0\n"
                        + "com.android.support:support-fragment\tandroidx.fragment:fragment:1.0.0\n"
                        + "com.android.support:support-media-compat\tandroidx.media:media:1.0.0\n"
                        + "com.android.support:support-tv-provider\tandroidx.tvprovider:tvprovider:1.0.0\n"
                        + "com.android.support:support-v13\tandroidx.legacy:legacy-support-v13:1.0.0\n"
                        + "com.android.support:support-v4\tandroidx.legacy:legacy-support-v4:1.0.0\n"
                        + "com.android.support:support-vector-drawable\tandroidx.vectordrawable:vectordrawable:1.0.0\n"
                        + "com.android.support:swiperefreshlayout\tandroidx.swiperefreshlayout:swiperefreshlayout:1.0.0\n"
                        + "com.android.support:textclassifier\tandroidx.textclassifier:textclassifier:1.0.0\n"
                        + "com.android.support:transition\tandroidx.transition:transition:1.0.0\n"
                        + "com.android.support:versionedparcelable\tandroidx.versionedparcelable:versionedparcelable:1.0.0\n"
                        + "com.android.support:viewpager\tandroidx.viewpager:viewpager:1.0.0\n"
                        + "com.android.support:wear\tandroidx.wear:wear:1.0.0\n"
                        + "com.android.support:webkit\tandroidx.webkit:webkit:1.0.0";

        for (String mapping : Splitter.on('\n').split(documentedMappings)) {
            int separator = mapping.indexOf('\t');
            String oldCoordinate = mapping.substring(0, separator);
            String newCoordinate = mapping.substring(separator + 1);
            // Remove version since they might not be completely up to date
            newCoordinate = newCoordinate.substring(0, newCoordinate.lastIndexOf(':'));
            assertEquals(newCoordinate, AndroidxNameUtils.getCoordinateMapping(oldCoordinate));
        }
    }
}
