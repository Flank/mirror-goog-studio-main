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
        assertEquals("ValidModel", MlkitNames.computeModelClassName("validModel.tflite"));
        assertEquals("ValidModel", MlkitNames.computeModelClassName("validModel.tflite"));
    }

    @Test
    public void computeModelClassName_nameWithInvalidCharacters_correctIt() {
        assertEquals("ValidModel", MlkitNames.computeModelClassName(" valid_model%$.tflite"));
        assertEquals("ValidModel", MlkitNames.computeModelClassName("valid_model.tflite"));
        assertEquals("ValidModel", MlkitNames.computeModelClassName("valid-model.tflite"));
        assertEquals("ValidModel", MlkitNames.computeModelClassName("valid-_model.tflite"));
        assertEquals("ValidModel", MlkitNames.computeModelClassName("valid model.tflite"));
    }

    @Test
    public void computeModelClassName_nameAllWithInvalidCharacters_returnModelWithHashcode() {
        assertEquals("AutoModel40", MlkitNames.computeModelClassName(" %$.tflite"));
    }

    @Test
    public void computeModelClassName_nameStartWithDigit_correctIt() {
        assertEquals("AutoModel012", MlkitNames.computeModelClassName("012.tflite"));
    }

    @Test
    public void computeIdentifierName_nameStartWithDigit_returnHashedName() {
        assertEquals("name250", MlkitNames.computeIdentifierName("012abc"));
    }

    @Test
    public void computeIdentifierName_nameIsKeyword_returnHashedName() {
        assertEquals("name158", MlkitNames.computeIdentifierName("class"));
    }

    @Test
    public void computeIdentifierNameWithDefault_nameIsKeyword_returnDefaultName() {
        assertEquals("defaultName", MlkitNames.computeIdentifierName("class", "defaultName"));
    }

    @Test
    public void computeIdentifierName_nameWithInvalidCharacters_correctIt() {
        assertEquals("tensorName", MlkitNames.computeIdentifierName("%tensorName"));
        assertEquals("tensorName", MlkitNames.computeIdentifierName("tensor name"));
        assertEquals("tensorName", MlkitNames.computeIdentifierName("tensor-name"));
        assertEquals("tensorName", MlkitNames.computeIdentifierName("tensor_name"));
    }

    @Test
    public void computeIdentifierName_nameValid_returnName() {
        assertEquals("tensorName", MlkitNames.computeIdentifierName("tensorName"));
    }

    @Test
    public void computeModelClassName_sameFileInDifferentDir_returnDifferentName() {
        assertNotEquals(
                MlkitNames.computeModelClassName("dir1/model.tflite"),
                MlkitNames.computeModelClassName("dir2/model.tflite"));
    }
}
