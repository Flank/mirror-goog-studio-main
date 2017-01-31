/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "common.h"

#include <stdio.h>
#include <stdlib.h>

namespace slicer {

// Helper for the default CHECK() policy
void _checkFailed(const char* expr, int line, const char* file) {
  printf("\nCHECK failed [%s] at %s:%d\n\n", expr, file, line);
  abort();
}

} // namespace slicer

