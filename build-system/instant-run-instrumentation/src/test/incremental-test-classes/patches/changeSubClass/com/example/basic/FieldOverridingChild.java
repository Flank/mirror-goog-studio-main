/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.example.basic;

import java.util.ArrayList;
import java.util.List;

/**
 * Child that redefines its parent private fields with the same name
 */
public class FieldOverridingChild extends FieldOverridingParent {

    private static Double staticField;
    private static List<String> staticCollectionField;

    private Double field = 12d;
    protected List<String> collectionField;

    public FieldOverridingChild() {
        staticField = 13d;
        collectionField = new ArrayList<String>();
        collectionField.add("modified child");

        staticCollectionField = new ArrayList<String>();
        staticCollectionField.add("modified static child");
    }

    public Double field() {
        return field;
    }

    public static Double staticField() {
        return staticField;
    }

    @Override
    public List<String> getCollection() {
        return collectionField;
    }

    @Override
    public List<String> getStaticCollection() {
        return staticCollectionField;
    }
}
