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
package com.android.tools.deployer.model;

import java.util.List;

public class Apk {
    public final String name;
    public final String checksum;
    public final String path;
    public final List<String> processes;

    public Apk(String name, String checksum, String path, List<String> processes) {
        this.name = name;
        this.checksum = checksum;
        this.path = path;
        this.processes = processes;
    }
}
