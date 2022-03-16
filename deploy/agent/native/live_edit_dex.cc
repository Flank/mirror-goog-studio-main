/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "tools/base/deploy/agent/native/live_edit_dex.h"

#include <fcntl.h>
#include <unistd.h>

#include "tools/base/deploy/agent/native/class_finder.h"
#include "tools/base/deploy/agent/native/jni/jni_class.h"
#include "tools/base/deploy/agent/native/jni/jni_util.h"
#include "tools/base/deploy/agent/native/live_edit.dex.cc"
#include "tools/base/deploy/common/io.h"
#include "tools/base/deploy/common/log.h"
#include "tools/base/deploy/sites/sites.h"

namespace deploy {

namespace {
bool is_dex_set_up = false;
}

// Extracts the dex containing the LiveEdit implementations of Lambda,
// SuspendLambda, and RestrictedSuspendLambda, and loads it into the application
// class loader.
//
// These classes must be added to the app class loader as they extend Kotlin
// base classes that are present in the app loader.
bool SetUpLiveEditDex(jvmtiEnv* jvmti, JNIEnv* jni,
                      const std::string& package_name) {
  if (is_dex_set_up) {
    return true;
  }

  std::string dex_path = Sites::AppStudio(package_name) + "live_edit.dex";

  if (IO::access(dex_path, F_OK) == 0 && IO::unlink(dex_path) != 0) {
    return false;
  }
  int fd = IO::open(dex_path, O_WRONLY | O_CREAT, S_IRUSR | S_IWUSR);
  if (fd == -1) {
    return false;
  }
  size_t count = 0;
  while (count < live_edit_dex_len) {
    ssize_t len = write(fd, live_edit_dex + count, live_edit_dex_len - count);
    if (len < 0) {
      close(fd);
      return false;
    }
    count += len;
  }

  if (close(fd) == -1) {
    return false;
  }

  auto app_loader = ClassFinder(jvmti, jni).GetApplicationClassLoader();

  jstring dex_path_str = jni->NewStringUTF(dex_path.c_str());
  JniObject(jni, app_loader)
      .CallVoidMethod("addDexPath", "(Ljava/lang/String;)V", dex_path_str);
  if (jni->ExceptionCheck()) {
    jni->ExceptionClear();
    return false;
  }
  is_dex_set_up = true;

  return true;
}

}  // namespace deploy
