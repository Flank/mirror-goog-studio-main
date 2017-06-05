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
package com.android.tools.apk.analyzer.dex;

import static org.junit.Assert.assertEquals;

import com.android.annotations.NonNull;
import com.android.tools.apk.analyzer.dex.tree.DexElementNode;
import java.io.IOException;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.immutable.reference.ImmutableTypeReference;
import org.jf.dexlib2.util.ReferenceUtil;
import org.junit.Test;

public class DexReferencesTest {
    @Test
    public void createReferenceTree() throws IOException {
        DexBackedDexFile dexFile =
                PackageTreeCreatorTest.getTestDexFile(
                        PackageTreeCreatorTest.getDexPath("Test2.dex"));
        DexReferences references = new DexReferences(new DexBackedDexFile[] {dexFile});
        DexElementNode root = references.getReferenceTreeFor(new ImmutableTypeReference("La;"));
        StringBuffer sb = new StringBuffer();
        dumpTree(sb, root, 0);
        assertEquals(
                "La;: \n"
                        + "  LTest2;-><init>()V: \n"
                        + "    LTestSubclass;-><init>()V: \n"
                        + "  LTest2;->aClassField:La;: \n"
                        + "    LTest2;-><init>()V: \n"
                        + "      LTestSubclass;-><init>()V: \n",
                sb.toString());
    }

    private static void dumpTree(StringBuffer sb, @NonNull DexElementNode node, int depth) {
        for (int i = 0; i < depth * 2; i++) {
            sb.append(' ');
        }
        sb.append(ReferenceUtil.getReferenceString(node.getReference()));
        sb.append(": ");
        sb.append('\n');

        for (int i = 0; i < node.getChildCount(); i++) {
            dumpTree(sb, node.getChildAt(i), depth + 1);
        }
    }
}
