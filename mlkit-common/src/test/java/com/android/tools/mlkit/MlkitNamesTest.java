/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.mlkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

public class MlkitNamesTest {

    @Test
    public void computeModelClassName_validName_justReturn() {
        assertEquals(MlkitNames.computeModelClassName("validModel.tflite"), "ValidModel");
        assertEquals(MlkitNames.computeModelClassName("validModel.tflite"), "ValidModel");
    }

    @Test
    public void computeModelClassName_nameWithInvalidCharacters_correctIt() {
        assertEquals(MlkitNames.computeModelClassName(" valid_model%$.tflite"), "ValidModel");
        assertEquals(MlkitNames.computeModelClassName("valid_model.tflite"), "ValidModel");
        assertEquals(MlkitNames.computeModelClassName("valid-model.tflite"), "ValidModel");
        assertEquals(MlkitNames.computeModelClassName("valid-_model.tflite"), "ValidModel");
        assertEquals(MlkitNames.computeModelClassName("valid model.tflite"), "ValidModel");
    }

    @Test
    public void computeModelClassName_nameAllWithInvalidCharacters_returnModelWithHashcode() {
        assertEquals(MlkitNames.computeModelClassName(" %$.tflite"), "AutoModel40");
    }

    @Test
    public void computeModelClassName_nameStartWithDigit_correctIt() {
        assertEquals(MlkitNames.computeModelClassName("012.tflite"), "AutoModel012");
    }

    @Test
    public void computeIdentifierName_nameStartWithDigit_returnHashedName() {
        assertEquals(MlkitNames.computeIdentifierName("012abc"), "name250");
    }

    @Test
    public void computeIdentifierName_nameIsKeyword_returnHashedName() {
        assertEquals(MlkitNames.computeIdentifierName("class"), "name158");
    }

    @Test
    public void computeIdentifierNameWithDefault_nameIsKeyword_returnDefaultName() {
        assertEquals(MlkitNames.computeIdentifierName("class", "defaultName"), "defaultName");
    }

    @Test
    public void computeIdentifierName_nameWithInvalidCharacters_correctIt() {
        assertEquals(MlkitNames.computeIdentifierName("%tensorName"), "tensorName");
        assertEquals(MlkitNames.computeIdentifierName("tensor name"), "tensorName");
        assertEquals(MlkitNames.computeIdentifierName("tensor-name"), "tensorName");
        assertEquals(MlkitNames.computeIdentifierName("tensor_name"), "tensorName");
    }

    @Test
    public void computeIdentifierName_nameValid_returnName() {
        assertEquals(MlkitNames.computeIdentifierName("tensorName"), "tensorName");
    }

    @Test
    public void computeModelClassName_sameFileInDifferentDir_returnDifferentName() {
        assertNotEquals(
                MlkitNames.computeModelClassName("dir1/model.tflite"),
                MlkitNames.computeModelClassName("dir2/model.tflite"));
    }
}
