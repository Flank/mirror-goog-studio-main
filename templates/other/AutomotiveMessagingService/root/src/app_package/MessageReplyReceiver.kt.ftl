package ${kotlinEscapedPackageName}

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ${getMaterialComponentName('android.support.v4.app.RemoteInput', useAndroidX)}
import android.util.Log

private const val TAG = "${truncate(replyReceiverName,23)}"

/**
 * A receiver that gets called when a reply is sent to a given conversationId
 */
class ${replyReceiverName} : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (REPLY_ACTION == intent.action) {
            val conversationId = intent.getIntExtra(CONVERSATION_ID, -1)
            val reply = getMessageText(intent)
            Log.d(TAG, "Got reply ($reply) for ConversationId $conversationId")
        }
    }

    /**
     * Get the message text from the intent.
     * Note that you should call `RemoteInput#getResultsFromIntent(intent)` to process
     * the RemoteInput.
     */
    private fun getMessageText(intent: Intent): CharSequence? {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        return remoteInput?.getCharSequence(EXTRA_VOICE_REPLY)
    }
}
