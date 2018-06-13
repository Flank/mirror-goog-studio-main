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

#include <jni.h>
#include <cmath>
#include <chrono>

const double MATH_E = 2.71828182845904523536;

void DoExpensiveFpuCalculation(int iterations) {
    double value = MATH_E;
    for (int i = 0; i < iterations; i++) {
        value += sin(value) + cos(value);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_android_com_java_profilertester_taskcategory_CpuTaskCategory_fpuCalc(JNIEnv *env,
                                                                          jobject instance,
                                                                          jint run_at_least_ms) {
  using namespace std::chrono;
  auto start = system_clock::now();
  while (duration_cast<milliseconds>(system_clock::now() - start).count() < run_at_least_ms) {
      DoExpensiveFpuCalculation(10000);
  }
}

