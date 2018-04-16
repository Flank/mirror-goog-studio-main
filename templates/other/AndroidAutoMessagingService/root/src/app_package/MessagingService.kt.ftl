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

package ${kotlinEscapedPackageName}

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.CarExtender
import android.support.v4.app.NotificationCompat.CarExtender.UnreadConversation
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput

const val READ_ACTION = "${packageName}.ACTION_MESSAGE_READ"
const val REPLY_ACTION = "${packageName}.ACTION_MESSAGE_REPLY"
const val CONVERSATION_ID = "conversation_id"
const val EXTRA_VOICE_REPLY = "extra_voice_reply"

class ${serviceName} : Service() {

    private val mMessenger = Messenger(IncomingHandler())
    private lateinit var mNotificationManager: NotificationManagerCompat

    override fun onCreate() {
        mNotificationManager = NotificationManagerCompat.from(applicationContext)
    }

    override fun onBind(intent: Intent): IBinder? {
        return mMessenger.binder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    private fun createIntent(conversationId: Int, action: String): Intent {
        return Intent().apply {
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                setAction(action)
                putExtra(CONVERSATION_ID, conversationId)
        }
    }

    private fun sendNotification(conversationId: Int,
                                 message: String,
                                 participant: String,
                                 timestamp: Long) {
        // A pending Intent for reads
        val readPendingIntent = PendingIntent.getBroadcast(applicationContext,
                conversationId,
                createIntent(conversationId, READ_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT)

        // Build a RemoteInput for receiving voice input in a Car Notification
        val remoteInput = RemoteInput.Builder(EXTRA_VOICE_REPLY)
                .setLabel("Reply by voice")
                .build()

        // Building a Pending Intent for the reply action to trigger
        val replyIntent = PendingIntent.getBroadcast(applicationContext,
                conversationId,
                createIntent(conversationId, REPLY_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT)

        // Create the UnreadConversation and populate it with the participant name,
        // read and reply intents.
        val unreadConversationBuilder = UnreadConversation.Builder(participant)
                .setLatestTimestamp(timestamp)
                .setReadPendingIntent(readPendingIntent)
                .setReplyAction(replyIntent, remoteInput)

        val builder = NotificationCompat.Builder(applicationContext)
                // Set the application notification icon:
                //.setSmallIcon(R.drawable.notification_icon)

                // Set the large icon, for example a picture of the other recipient of the message
                //.setLargeIcon(personBitmap)

                .setContentText(message)
                .setWhen(timestamp)
                .setContentTitle(participant)
                .setContentIntent(readPendingIntent)
                .extend(CarExtender()
                        .setUnreadConversation(unreadConversationBuilder.build()))

        mNotificationManager.notify(conversationId, builder.build())
    }

    /**
     * Handler of incoming messages from clients.
     */
    internal inner class IncomingHandler : Handler() {
        override fun handleMessage(msg: Message) {
            sendNotification(1, "This is a sample message", "John Doe", System.currentTimeMillis())
        }
    }
}
