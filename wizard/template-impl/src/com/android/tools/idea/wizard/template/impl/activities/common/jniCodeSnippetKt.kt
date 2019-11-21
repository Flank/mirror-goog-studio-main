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
package com.android.tools.idea.wizard.template.impl.activities.common

import com.android.tools.idea.wizard.template.renderIf

fun jniCodeSnippet(includeCppSupport: Boolean): String = renderIf(includeCppSupport) {
  """
/**
 * A native method that is implemented by the 'native-lib' native library,
 * which is packaged with this application.
 */
external fun stringFromJNI(): String

companion object {

  // Used to load the 'native-lib' library on application startup.
  init {
    System.loadLibrary("native-lib"
  }
}
"""
}
