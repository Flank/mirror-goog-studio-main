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
package com.android.tools.deploy.swapper;

/**
 * Public API to do hotswapping.
 *
 * <p>A place holder for the class that performs hot swapping. This class with be the the public API
 * that the deployer is going to interact with. It will take in a dex file (potentially with
 * multiple Java classes) and invoke VM supported class redefinition APIs.
 *
 * <p>Note that it only be responsible for the current runnindg process and will not be responsible
 * for making the class swap persistent across application restarts.
 */
public class HotSwapManager {
    private final ClassRedefiner classRedefiner;

    public HotSwapManager(ClassRedefiner redefiner) {
        classRedefiner = redefiner;
    }
}
