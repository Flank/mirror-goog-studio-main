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
 *
 */
#include "perfetto.h"

#include <sys/system_properties.h>
namespace profiler {

bool Perfetto::EnableProfiling() {
  // By default, traced probes aren disabled. This enables it.
  // perfetto already has CTS tests ensuring the following command running
  // successfully.
  return __system_property_set("persist.traced.enable", "1") == 0;
}

}  //  namespace profiler