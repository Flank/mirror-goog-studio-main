/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.common.utils;

import com.android.build.gradle.integration.common.fixture.GradleTestProject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.jacoco.agent.AgentJar;

/**
 * Utility to setup for Jacoco agent.
 */
public class JacocoAgent {
    public static boolean isJacocoEnabled() {
        String attachJacoco = System.getenv("ATTACH_JACOCO_AGENT");
        return attachJacoco != null;
    }

    public static String getJvmArg() {
        File buildDir = GradleTestProject.BUILD_DIR;
        File jacocoAgent = new File(buildDir, "jacoco/agent.jar");
        if (!jacocoAgent.isFile()) {
            try {
                AgentJar.extractTo(jacocoAgent);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return "-javaagent:" + jacocoAgent.toString() + "=destfile=" + buildDir + "/jacoco/test.exec";
    }
}
