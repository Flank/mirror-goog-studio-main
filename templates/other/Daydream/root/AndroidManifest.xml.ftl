<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="${packageName}">

    <application>

<#if configurable>
        <activity
            android:name="${packageName}.${settingsClassName}" />
</#if>

        <!-- This service is only used on devices with API v17+ -->
        <service
            android:name="${packageName}.${className}"
            android:exported="true" >
            <intent-filter>
                <action android:name="android.service.dreams.DreamService" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
<#if configurable>

            <!-- Point to additional information for this dream -->
            <meta-data
                android:name="android.service.dream"
                android:resource="@xml/${info_name}" />
</#if>
        </service>
    </application>

</manifest>
