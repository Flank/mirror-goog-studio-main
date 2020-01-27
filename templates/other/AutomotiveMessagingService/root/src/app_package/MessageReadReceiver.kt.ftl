package ${kotlinEscapedPackageName}

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ${getMaterialComponentName('android.support.v4.app.NotificationManagerCompat', useAndroidX)}
import android.util.Log

private const val TAG = "${truncate(readReceiverName,23)}"

class ${readReceiverName} : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (READ_ACTION == intent.action) {
            val conversationId = intent.getIntExtra(CONVERSATION_ID, -1)
            if (conversationId != -1) {
                Log.d(TAG, "Conversation $conversationId was read")
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.cancel(conversationId)
            }
        }
    }
}
