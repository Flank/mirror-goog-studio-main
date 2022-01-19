/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.manifmerger.testutils

import com.google.common.truth.IterableSubject
import com.google.common.truth.Truth.assertThat
import org.w3c.dom.Node
import org.w3c.dom.NodeList

operator fun NodeList.get(index: Int) = item(index)

fun NodeList.asSequence(): Sequence<Node> {
    var i = 0
    return generateSequence { if (i < length) item(i++) else null }
}

fun assertThatNodeList(nodeList: NodeList): IterableSubject =
    assertThat(nodeList.asSequence().toList())

fun Node.getNamedAttributeAndroidNS(localName: String) =
    attributes.getNamedItemNS("http://schemas.android.com/apk/res/android", localName)
