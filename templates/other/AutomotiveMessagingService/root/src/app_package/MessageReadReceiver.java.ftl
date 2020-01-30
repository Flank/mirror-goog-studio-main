package ${packageName};

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import ${getMaterialComponentName('android.support.v4.app.NotificationManagerCompat', useAndroidX)};
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
