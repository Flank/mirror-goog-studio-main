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

package com.android.build.gradle.internal.dependency

import com.android.builder.model.Version

/** Contains the mappings from old dependencies to AndroidX dependencies. */
class AndroidXMapping {

    companion object {

        @JvmField
        val MAPPINGS: Map<String, String> =
            mapOf(
                "com.android.support:animated-vector-drawable" to "androidx.vectordrawable:vectordrawable-animated:1.0.0-alpha1",
                "com.android.support:appcompat-v7" to "androidx.appcompat:appcompat:1.0.0-alpha1",
                "com.android.support:cardview-v7" to "androidx.cardview:cardview:1.0.0-alpha1",
                "com.android.support:customtabs" to "androidx.browser:browser:1.0.0-alpha1",
                "com.android.support:design" to "com.google.android.material:material:1.0.0-alpha1",
                "com.android.support:exifinterface" to "androidx.exifinterface:exifinterface:1.0.0-alpha1",
                "com.android.support:gridlayout-v7" to "androidx.gridlayout:gridlayout:1.0.0-alpha1",
                "com.android.support:leanback-v17" to "androidx.leanback:leanback:1.0.0-alpha1",
                "com.android.support:mediarouter-v7" to "androidx.mediarouter:mediarouter:1.0.0-alpha1",
                "com.android.support:multidex" to "androidx.multidex:multidex:2.0.0",
                "com.android.support:multidex-instrumentation" to "androidx.multidex:multidex-instrumentation:2.0.0",
                "com.android.support:palette-v7" to "androidx.palette:palette:1.0.0-alpha1",
                "com.android.support:percent" to "androidx.percentlayout:percentlayout:1.0.0-alpha1",
                "com.android.support:preference-leanback-v17" to "androidx.leanback:leanback-preference:1.0.0-alpha1",
                "com.android.support:preference-v14" to "androidx.legacy:legacy-preference-v14:1.0.0-alpha1",
                "com.android.support:preference-v7" to "androidx.preference:preference:1.0.0-alpha1",
                "com.android.support:recommendation" to "androidx.recommendation:recommendation:1.0.0-alpha1",
                "com.android.support:recyclerview-v7" to "androidx.recyclerview:recyclerview:1.0.0-alpha1",
                "com.android.support:support-annotations" to "androidx.annotation:annotation:1.0.0-alpha1",
                "com.android.support:support-compat" to "androidx.core:core:1.0.0-alpha1",
                "com.android.support:support-content" to "androidx.contentpager:contentpager:1.0.0-alpha1",
                "com.android.support:support-core-ui" to "androidx.legacy:legacy-support-core-ui:1.0.0-alpha1",
                "com.android.support:support-core-utils" to "androidx.legacy:legacy-support-core-utils:1.0.0-alpha1",
                "com.android.support:support-dynamic-animation" to "androidx.dynamicanimation:dynamicanimation:1.0.0-alpha1",
                "com.android.support:support-emoji" to "androidx.emoji:emoji:1.0.0-alpha1",
                "com.android.support:support-emoji-appcompat" to "androidx.emoji:emoji-appcompat:1.0.0-alpha1",
                "com.android.support:support-emoji-bundled" to "androidx.emoji:emoji-bundled:1.0.0-alpha1",
                "com.android.support:support-fragment" to "androidx.fragment:fragment:1.0.0-alpha1",
                "com.android.support:support-media-compat" to "androidx.media:media:1.0.0-alpha1",
                "com.android.support:support-tv-provider" to "androidx.tvprovider:tvprovider:1.0.0-alpha1",
                "com.android.support:support-v13" to "androidx.legacy:legacy-support-v13:1.0.0-alpha1",
                "com.android.support:support-v4" to "androidx.legacy:legacy-support-v4:1.0.0-alpha1",
                "com.android.support:support-vector-drawable" to "androidx.vectordrawable:vectordrawable:1.0.0-alpha1",
                "com.android.support:textclassifier" to "androidx.textclassifier:textclassifier:1.0.0-alpha1",
                "com.android.support:transition" to "androidx.transition:transition:1.0.0-alpha1",
                "com.android.support:wear" to "androidx.wear:wear:1.0.0-alpha1",
                "com.android.support:asynclayoutinflater" to "androidx.asynclayoutinflater:asynclayoutinflater:1.0.0-alpha1",
                "com.android.support:collections" to "androidx.collection:collection:1.0.0-alpha1",
                "com.android.support:coordinatorlayout" to "androidx.coordinatorlayout:coordinatorlayout:1.0.0-alpha1",
                "com.android.support:cursoradapter" to "androidx.cursoradapter:cursoradapter:1.0.0-alpha1",
                "com.android.support:customview" to "androidx.customview:customview:1.0.0-alpha1",
                "com.android.support:documentfile" to "androidx.documentfile:documentfile:1.0.0-alpha1",
                "com.android.support:drawerlayout" to "androidx.drawerlayout:drawerlayout:1.0.0-alpha1",
                "com.android.support:interpolator" to "androidx.interpolator:interpolator:1.0.0-alpha1",
                "com.android.support:loader" to "androidx.loader:loader:1.0.0-alpha1",
                "com.android.support:localbroadcastmanager" to "androidx.localbroadcastmanager:localbroadcastmanager:1.0.0-alpha1",
                "com.android.support:print" to "androidx.print:print:1.0.0-alpha1",
                "com.android.support:slidingpanelayout" to "androidx.slidingpanelayout:slidingpanelayout:1.0.0-alpha1",
                "com.android.support:swiperefreshlayout" to "androidx.swiperefreshlayout:swiperefreshlayout:1.0.0-alpha1",
                "com.android.support:viewpager" to "androidx.viewpager:viewpager:1.0.0-alpha1",
                "android.arch.core:common" to "androidx.arch.core:core-common:2.0.0-alpha1",
                "android.arch.core:core" to "androidx.arch.core:core:2.0.0-alpha1",
                "android.arch.core:core-testing" to "androidx.arch.core:core-testing:2.0.0-alpha1",
                "android.arch.core:runtime" to "androidx.arch.core:core-runtime:2.0.0-alpha1",
                "android.arch.lifecycle:common" to "androidx.lifecycle:lifecycle-common:2.0.0-alpha1",
                "android.arch.lifecycle:common-java8" to "androidx.lifecycle:lifecycle-common-java8:2.0.0-alpha1",
                "android.arch.lifecycle:compiler" to "androidx.lifecycle:lifecycle-compiler:2.0.0-alpha1",
                "android.arch.lifecycle:extensions" to "androidx.lifecycle:lifecycle-extensions:2.0.0-alpha1",
                "android.arch.lifecycle:reactivestreams" to "androidx.lifecycle:lifecycle-reactivestreams:2.0.0-alpha1",
                "android.arch.lifecycle:runtime" to "androidx.lifecycle:lifecycle-runtime:2.0.0-alpha1",
                "android.arch.lifecycle:viewmodel" to "androidx.lifecycle:lifecycle-viewmodel:2.0.0-alpha1",
                "android.arch.lifecycle:livedata" to "androidx.lifecycle:lifecycle-livedata:2.0.0-alpha1",
                "android.arch.lifecycle:livedata-core" to "androidx.lifecycle:lifecycle-livedata-core:2.0.0-alpha1",
                "android.arch.paging:common" to "androidx.paging:paging-common:2.0.0-alpha1",
                "android.arch.paging:runtime" to "androidx.paging:paging-runtime:2.0.0-alpha1",
                "android.arch.persistence:db" to "androidx.sqlite:sqlite:2.0.0-alpha1",
                "android.arch.persistence:db-framework" to "androidx.sqlite:sqlite-framework:2.0.0-alpha1",
                "android.arch.persistence.room:common" to "androidx.room:room-common:2.0.0-alpha1",
                "android.arch.persistence.room:compiler" to "androidx.room:room-compiler:2.0.0-alpha1",
                "android.arch.persistence.room:migration" to "androidx.room:room-migration:2.0.0-alpha1",
                "android.arch.persistence.room:runtime" to "androidx.room:room-runtime:2.0.0-alpha1",
                "android.arch.persistence.room:rxjava2" to "androidx.room:room-rxjava2:2.0.0-alpha1",
                "android.arch.persistence.room:testing" to "androidx.room:room-testing:2.0.0-alpha1",
                "android.arch.persistence.room:guava" to "androidx.room:room-guava:2.0.0-alpha1",
                "com.android.support.constraint:constraint-layout" to "androidx.constraintlayout:constraintlayout:1.1.0",
                "com.android.support.constraint:constraint-layout-solver" to "androidx.constraintlayout:constraintlayout-solver:1.1.0",
                "com.android.support.test:orchestrator" to "androidx.test:orchestrator:1.1.0-alpha1",
                "com.android.support.test:rules" to "androidx.test:rules:1.1.0-alpha1",
                "com.android.support.test:runner" to "androidx.test:runner:1.1.0-alpha1",
                "com.android.support.test:monitor" to "androidx.test:monitor:1.1.0-alpha1",
                "com.android.support.test.espresso:espresso-accessibility" to "androidx.test.espresso:espresso-accessibility:3.1.0-alpha1",
                "com.android.support.test.espresso:espresso-contrib" to "androidx.test.espresso:espresso-contrib:3.1.0-alpha1",
                "com.android.support.test.espresso:espresso-core" to "androidx.test.espresso:espresso-core:3.1.0-alpha1",
                "com.android.support.test.espresso:espresso-idling-resource" to "androidx.test.espresso:espresso-idling-resource:3.1.0-alpha1",
                "com.android.support.test.espresso:espresso-intents" to "androidx.test.espresso:espresso-intents:3.1.0-alpha1",
                "com.android.support.test.espresso:espresso-web" to "androidx.test.espresso:espresso-web:3.1.0-alpha1",
                "com.android.support.test.espresso.idling:idling-concurrent" to "androidx.test.espresso.idling:idling-concurrent:3.1.0-alpha1",
                "com.android.support.test.espresso.idling:idling-net" to "androidx.test.espresso.idling:idling-net:3.1.0-alpha1",
                "com.android.support.test.janktesthelper:janktesthelper-v23" to "androidx.test.jank:janktesthelper-v23:1.0.1-alpha1",
                "com.android.support.test.services:test-services" to "androidx.test:test-services:1.1.0-alpha1",
                "com.android.support.test.uiautomator:uiautomator-v18" to "androidx.test.uiautomator:uiautomator-v18:2.2.0-alpha1",
                "com.android.support:car" to "androidx.car:car:1.0.0-alpha1",
                "com.android.support:slices-core" to "androidx.slice:slice-core:1.0.0-alpha1",
                "com.android.support:slices-builders" to "androidx.slice:slice-builders:1.0.0-alpha1",
                "com.android.support:slices-view" to "androidx.slice:slice-view:1.0.0-alpha1",
                "com.android.support:heifwriter" to "androidx.heifwriter:heifwriter:1.0.0-alpha1",
                "com.android.support:recyclerview-selection" to "androidx.recyclerview:recyclerview-selection:1.0.0-alpha1",
                "com.android.support:webkit" to "androidx.webkit:webkit:1.0.0-alpha1",
                "com.android.databinding:adapters" to "androidx.databinding:databinding-adapters:${Version.ANDROID_GRADLE_PLUGIN_VERSION}",
                // "com.android.databinding:baseLibrary" should not be replaced with
                // "androidx.databinding:databinding-common" in some cases (see
                // JetifyTransform.bypassDependencySubstitution()). Therefore, we don't have that
                // mapping here.
                "com.android.databinding:compiler" to "androidx.databinding:databinding-compiler:${Version.ANDROID_GRADLE_PLUGIN_VERSION}",
                "com.android.databinding:compilerCommon" to "androidx.databinding:databinding-compiler-common:${Version.ANDROID_GRADLE_PLUGIN_VERSION}",
                "com.android.databinding:library" to "androidx.databinding:databinding-runtime:${Version.ANDROID_GRADLE_PLUGIN_VERSION}"
        )
    }
}