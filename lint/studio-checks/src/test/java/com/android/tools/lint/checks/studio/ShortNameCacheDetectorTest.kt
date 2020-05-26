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

@file:Suppress("SpellCheckingInspection")

package com.android.tools.lint.checks.studio

import com.android.tools.lint.checks.infrastructure.TestFiles.java
import org.junit.Test

class ShortNameCacheDetectorTest {

    @Test
    fun testProblems() {
        studioLint()
            .files(
                java(
                    """
                    package test.pkg;

                    import com.intellij.psi.search.PsiShortNamesCache;

                    @SuppressWarnings({"ClassNameDiffersFromFileName", "MethodMayBeStatic",
                        "MismatchedReadAndWriteOfArray", "UnnecessaryLocalVariable",
                        "ConstantConditions"})
                    public class MlShortNamesCache extends PsiShortNamesCache {
                        @Override
                        public boolean processMethodsWithName(String name,
                                                              GlobalSearchScope scope,
                                                              Processor<? super PsiMethod> processor) {
                            return false; // ERROR
                        }

                        @Override
                        public boolean processAllMethodNames(Processor<? super String> processor, GlobalSearchScope scope, IdFilter filter) {
                            boolean retValue = false;
                            return retValue; // ERROR
                        }

                        @Override
                        public boolean processAllClassNames(Processor<? super String> processor, GlobalSearchScope scope, IdFilter filter) {
                            return super.processAllClassNames(processor, scope, filter); // OK
                        }

                        @Override
                        public boolean processAllFieldNames(Processor<? super String> processor, GlobalSearchScope scope, IdFilter filter) {
                            return true; // OK
                        }

                        private PsiShortNamesCache[] myCaches;

                        @Override
                        public boolean processMethodsWithName(String name,
                                                              Processor<? super PsiMethod> processor,
                                                              GlobalSearchScope scope,
                                                              IdFilter idFilter) {
                            for (PsiShortNamesCache cache : myCaches) {
                                if (!cache.processMethodsWithName(name, processor, scope, idFilter)) {
                                    return false; // OK
                                }
                            }
                            return true; // OK
                        }
                    }
                """
                ).indented(),
                // Stubs
                java(
                    """
                    package com.intellij.psi.search;

                    @SuppressWarnings("all")
                    public abstract class PsiShortNamesCache {
                      public boolean processAllClassNames(Processor<? super String> processor) {
                        return true;
                      }

                      public boolean processAllClassNames(Processor<? super String> processor, GlobalSearchScope scope, IdFilter filter) {
                        return true;
                      }

                      public abstract boolean processMethodsWithName(String name,
                                                                     GlobalSearchScope scope,
                                                                     Processor<? super PsiMethod> processor);

                      public boolean processMethodsWithName(String name,
                                                            final Processor<? super PsiMethod> processor,
                                                            GlobalSearchScope scope,
                                                            IdFilter filter) {
                        return processMethodsWithName(name, scope, method -> processor.process(method));
                      }

                      public boolean processAllMethodNames(Processor<? super String> processor, GlobalSearchScope scope, IdFilter filter) {
                        return true;
                      }

                      public boolean processAllFieldNames(Processor<? super String> processor, GlobalSearchScope scope, IdFilter filter) {
                        return true;
                      }

                      public boolean processFieldsWithName(String name,
                                                           Processor<? super PsiField> processor,
                                                           GlobalSearchScope scope,
                                                           IdFilter filter) {
                        return true;
                      }

                      public boolean processClassesWithName(String name,
                                                            Processor<? super PsiClass> processor,
                                                            GlobalSearchScope scope,
                                                            IdFilter filter) {
                        return true;
                      }

                      @FunctionalInterface
                      public interface Processor<T> {
                        boolean process(T t);
                      }

                      public abstract class GlobalSearchScope { }
                      public abstract class IdFilter { }
                      public interface PsiMethod { }
                      public interface PsiClass { }
                      public interface PsiField { }
                    }
                    """
                )
            )
            .issues(ShortNameCacheDetector.ISSUE)
            .run()
            .expect(
                """
                src/test/pkg/MlShortNamesCache.java:13: Error: Do not return false; this will mark processing as consumed for this element and other cache processors will not run. This can lead to bugs like b/152432842. [ShortNamesCache]
                        return false; // ERROR
                        ~~~~~~~~~~~~~~~~~~~~~~
                src/test/pkg/MlShortNamesCache.java:19: Error: Do not return false; this will mark processing as consumed for this element and other cache processors will not run. This can lead to bugs like b/152432842. [ShortNamesCache]
                        return retValue; // ERROR
                        ~~~~~~~~~~~~~~~~~~~~~~~~~
                2 errors, 0 warnings
                """
            )
    }
}
