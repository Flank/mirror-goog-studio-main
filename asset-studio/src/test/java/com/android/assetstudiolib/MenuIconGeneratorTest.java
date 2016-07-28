/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.assetstudiolib;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@RunWith(JUnit4.class)
public class MenuIconGeneratorTest {

    private static void checkGraphic(String baseName) throws IOException {
        MenuIconGenerator generator = new MenuIconGenerator();
        BitmapGeneratorTests.checkGraphic(
                4, "menus", baseName, generator, new GraphicGenerator.Options());
    }

    @Test
    public void testMenu() throws Exception {
        checkGraphic("ic_menu_1");
    }
}
