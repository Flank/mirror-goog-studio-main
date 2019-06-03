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

package com.android.aaptcompiler

class Source(var path: String, private var line: Int? = null, private var archive: String? = null) {
  override fun toString(): String {
    var s = path
    if (archive != null) {
      s = "$archive@$s"
    }
    if (line != null) {
      s += ":$line"
    }
    return s
  }

  fun withLine(line: Int?) : Source {
    return Source(path, line, archive)
  }
}
