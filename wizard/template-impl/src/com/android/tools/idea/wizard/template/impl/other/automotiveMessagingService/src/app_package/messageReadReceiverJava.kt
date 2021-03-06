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

package com.android.tools.idea.wizard.template.impl.other.automotiveMessagingService.src.app_package

import com.android.tools.idea.wizard.template.getMaterialComponentName

fun messageReadReceiverJava(
  packageName: String,
  readReceiverName: String,
  serviceName: String,
  useAndroidX: Boolean
) = """
package ${packageName};

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import ${getMaterialComponentName("android.support.v4.app.NotificationManagerCompat", useAndroidX)};
import android.util.Log;

public class ${readReceiverName} extends BroadcastReceiver {
    private static final String TAG = ${readReceiverName}.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (${serviceName}.READ_ACTION.equals(intent.getAction())) {
          int conversationId = intent.getIntExtra(${serviceName}.CONVERSATION_ID, -1);
          if (conversationId != -1) {
            Log.d(TAG, "Conversation " + conversationId + " was read");
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.cancel(conversationId);
          }
        }
    }
}
"""
