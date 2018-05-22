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

package com.android.build.gradle.internal

import com.google.common.reflect.ClassPath
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Destroys
import java.lang.reflect.Modifier
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.SkipWhenEmpty
import org.junit.Test
import java.lang.reflect.AnnotatedElement

class GradleAnnotationsTest {

    @Test
    fun `check for non-public methods with gradle input or output annotations`() {
        val classPath = ClassPath.from(this.javaClass.classLoader)
        val nonPublicMethods =
            classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map { classInfo -> classInfo.load() as Class<*> }
                .flatMap { it.declaredMethods.asIterable() }
                .filter {
                    it.hasGradleInputOrOutputAnnotation() && !Modifier.isPublic(it.modifiers)
                }

        if (nonPublicMethods.isEmpty()) {
            return
        }

        // Otherwise generate a descriptive error message.
        val error =
            StringBuilder().append("The following gradle-annotated methods are not public:\n")
        for (nonPublicMethod in nonPublicMethods) {
            error.append(nonPublicMethod.declaringClass.toString().substringAfter(" "))
                .append("::${nonPublicMethod.name}\n")
        }
        throw AssertionError(error.toString())
    }

    @Test
    fun `check for fields with gradle input or output annotations`() {
        val classPath = ClassPath.from(this.javaClass.classLoader)
        val annotatedFields =
            classPath
                .getTopLevelClassesRecursive("com.android.build")
                .map { classInfo -> classInfo.load() as Class<*> }
                .flatMap { it.declaredFields.asIterable() }
                .filter { it.hasGradleInputOrOutputAnnotation() }

        if (annotatedFields.isEmpty()) {
            return
        }

        // Otherwise generate a descriptive error message.
        val error =
            StringBuilder().append(
                "The following fields are annotated with gradle input/output annotations, which "
                        + "should only be used on methods (e.g., the corresponding getters):\n")
        for (annotatedField in annotatedFields) {
            error.append(annotatedField.declaringClass.toString().substringAfter(" "))
                .append(".${annotatedField.name}\n")
        }
        throw AssertionError(error.toString())
    }

    private fun AnnotatedElement.hasGradleInputOrOutputAnnotation(): Boolean {
        // look for all org.gradle.api.tasks annotations, except @CacheableTask, @Internal, and
        // @TaskAction.
        return getAnnotation(Classpath::class.java) != null
                || getAnnotation(CompileClasspath::class.java) != null
                || getAnnotation(Console::class.java) != null
                || getAnnotation(Destroys::class.java) != null
                || getAnnotation(Input::class.java) != null
                || getAnnotation(InputDirectory::class.java) != null
                || getAnnotation(InputFile::class.java) != null
                || getAnnotation(InputFiles::class.java) != null
                || getAnnotation(LocalState::class.java) != null
                || getAnnotation(Nested::class.java) != null
                || getAnnotation(Optional::class.java) != null
                || getAnnotation(OutputDirectories::class.java) != null
                || getAnnotation(OutputDirectory::class.java) != null
                || getAnnotation(OutputFile::class.java) != null
                || getAnnotation(OutputFiles::class.java) != null
                || getAnnotation(PathSensitive::class.java) != null
                || getAnnotation(SkipWhenEmpty::class.java) != null
    }
}
