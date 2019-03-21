<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="${packageName}">

    <uses-feature
            android:name="android.hardware.type.automotive"
            android:required="true"/>

    <application
            android:allowBackup="true"
            android:label="@string/app_name"<#if copyIcons>
            android:icon="@mipmap/ic_launcher"
            android:roundIcon="@mipmap/ic_launcher_round"<#elseif assetName??>
            android:icon="@drawable/${assetName}"</#if>
            android:supportsRtl="true"
            android:theme="@style/AppTheme"/>

</manifest>
