/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef APP_INSPECTION_SERVICE_H_
#define APP_INSPECTION_SERVICE_H_

#include "jvmti.h"

namespace app_inspection {

class AppInspectionService {
 public:
  static AppInspectionService* create(JNIEnv* env);

 private:
  explicit AppInspectionService(jvmtiEnv* jvmti);
  // java object AppInspectionService that keeps reference to this object is
  // singleton, so no need to clean up
  ~AppInspectionService() = delete;

  jvmtiEnv* jvmti_;
};

}  // namespace app_inspection

#endif  // APP_INSPECTION_SERVICE_H_
