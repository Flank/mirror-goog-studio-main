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
package com.android.ide.common.resources

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.ANDROID
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import com.android.resources.ResourceType.COLOR
import com.android.resources.ResourceType.STRING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ResourceResolverNamespacesTest {

    private val app = ResourceNamespace.fromPackageName("com.example.app")
    private val localLib = ResourceNamespace.fromPackageName("com.example.common")
    private val supportLib = ResourceNamespace.fromPackageName("com.android.support")

    private val resolver: ResourceResolver = ResourceResolver.create(
        mapOf(
            ANDROID to mapOf(
                COLOR to ResourceValueMap.create().also { map ->
                    map.put(ResourceValue(ANDROID, COLOR, "black", "#000000"))
                }
            ),
            supportLib to mapOf(
                COLOR to ResourceValueMap.create().also { map ->
                    listOf(
                        ResourceValue(
                            supportLib,
                            COLOR,
                            "material_blue",
                            "#0000ff"
                        )
                    ).forEach(map::put)
                }
            ),
            localLib to mapOf(
                STRING to ResourceValueMap.create().also { map ->
                    listOf(
                        ResourceValue(
                            localLib,
                            STRING,
                            "company_name",
                            "Example"
                        ),
                        ResourceValue(
                            localLib,
                            STRING,
                            "header",
                            "@string/company_name"
                        ),
                        ResourceValue(
                            localLib,
                            STRING,
                            "header_explicit",
                            "@com.example.common:string/company_name"
                        ),
                        ResourceValue(
                            localLib,
                            STRING,
                            "wrong_ref",
                            "@string/made_up"
                        )
                    ).forEach(map::put)
                },
                COLOR to ResourceValueMap.create().also { map ->
                    listOf(
                        ResourceValue(
                            localLib,
                            COLOR,
                            "logo_color",
                            "@android:color/black"
                        ),
                        ResourceValue(
                            localLib,
                            STRING,
                            "missing_ns",
                            "@support:color/material_blue"
                        )
                    ).forEach(map::put)
                }
            ),
            app to mapOf(
                COLOR to ResourceValueMap.create().also { map ->
                    val namespaces = mapOf(
                        "common" to localLib.xmlNamespaceUri,
                        "support" to supportLib.xmlNamespaceUri
                    )

                    listOf(
                        ResourceValue(
                            app,
                            COLOR,
                            "image_color",
                            "@common:color/logo_color"
                        ),
                        ResourceValue(
                            app,
                            COLOR,
                            "title_bar_color",
                            "@support:color/material_blue"
                        ),
                        ResourceValue(
                            app,
                            COLOR,
                            "broken_chain_color",
                            "@common:color/missing_ns"
                        )
                    ).forEach {
                        it.setNamespaceLookup(namespaces::get)
                        map.put(it)
                    }
                },
                STRING to ResourceValueMap.create().also { map ->
                    listOf(
                        ResourceValue(
                            app,
                            STRING,
                            "title_text",
                            "@com.example.common:string/header"
                        ),
                        ResourceValue(
                            app,
                            STRING,
                            "title_text_explicit",
                            "@com.example.common:string/header_explicit"
                        )
                    ).forEach(map::put)
                }
            )
        ),
        "AppTheme",
        true
    )

    private fun check(
        namespace: ResourceNamespace,
        type: ResourceType,
        name: String,
        resolvesTo: String
    ) {
        val ref = ResourceReference(namespace, type, name)
        assertNotNull(resolver.getUnresolvedResource(ref))
        val resolved = resolver.getResolvedResource(ref)
        assertNotNull(resolved)
        assertEquals(resolvesTo, resolved!!.value)
    }

    @Test
    fun referencesWithinLib() {
        check(localLib, STRING, "company_name", resolvesTo = "Example")

        val header = ResourceReference(localLib, STRING, "header")
        assertEquals("@string/company_name", resolver.getUnresolvedResource(header)?.value)
        resolver.getResolvedResource(header)!!.let { resolved ->
            assertEquals("Example", resolved.value)
            assertEquals("company_name", resolved.name)
        }

        val headerExplicit = ResourceReference(localLib, STRING, "header_explicit")
        resolver.getResolvedResource(headerExplicit)!!.let { resolved ->
            assertEquals("Example", resolved.value)
            assertEquals("company_name", resolved.name)
        }

        check(localLib, STRING, "wrong_ref", resolvesTo = "@string/made_up")
    }

    @Test
    fun referencesAcrossNamespaces() {
        check(app, COLOR, "image_color", resolvesTo = "#000000")
        check(app, COLOR, "title_bar_color", resolvesTo = "#0000ff")
        check(app, STRING, "title_text", resolvesTo = "Example")
        check(app, STRING, "title_text_explicit", resolvesTo = "Example")

        // The "support" prefix is defined in the context of `app` resources (using
        // setNamespaceLookup to simulate the xmlns syntax), but not in the context of `localLib`
        // (which is assumed to use fully qualified package names in XML). Below we make sure that
        // the resolution process doesn't "leak" the defined prefixes as it walks the graph.
        check(app, COLOR, "broken_chain_color", resolvesTo = "@support:color/material_blue")
    }
}
