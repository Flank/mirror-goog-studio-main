package ${packageName};

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import ${getMaterialComponentName('android.support.v4.app.RemoteInput', useAndroidX)};
import android.util.Log;

/**
 * A receiver that gets called when a reply is sent to a given conversationId
 */
public class ${replyReceiverName} extends BroadcastReceiver {

    private static final String TAG = ${replyReceiverName}.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (${serviceName}.REPLY_ACTION.equals(intent.getAction())) {
            int conversationId = intent.getIntExtra(${serviceName}.CONVERSATION_ID, -1);
            CharSequence reply = getMessageText(intent);
            Log.d(TAG, "Got reply (" + reply + ") for ConversationId " + conversationId);
        }
    }

    /**
     * Get the message text from the intent.
     * Note that you should call {@code RemoteInput#getResultsFromIntent(intent)} to process
     * the RemoteInput.
     */
    private CharSequence getMessageText(Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput != null) {
            return remoteInput.getCharSequence(${serviceName}.EXTRA_VOICE_REPLY);
        }
        return null;
    }
}
