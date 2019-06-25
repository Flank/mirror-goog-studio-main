<PreferenceScreen
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <Preference
        app:key="messages_header"
        app:title="@string/messages_header"
        app:icon="@drawable/messages"
        app:fragment="${packageName}.${activityClass}$MessagesFragment"/>

    <Preference
        app:key="sync_header"
        app:title="@string/sync_header"
        app:icon="@drawable/sync"
        app:fragment="${packageName}.${activityClass}$SyncFragment"/>

</PreferenceScreen>
