<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          <#if isInstantApp!false>xmlns:instant="http://schemas.android.com/instantapps"</#if>>

    <application>
        <activity android:name="${relativePackage}.${activityClass}"
            <#if generateActivityTitle!true>
                <#if isNewProject>
                    android:label="@string/app_name"
                <#else>
                    android:label="@string/title_${activityToLayout(activityClass)}"
                </#if>
            </#if>
            <#if hasNoActionBar>
                android:theme="@style/${themeNameNoActionBar}"
            <#elseif requireTheme!false && !hasApplicationTheme && appCompat>
                android:theme="@style/${themeName}"
            </#if>
            <#if buildApi gte 16 && parentActivityClass != "">
                android:parentActivityName="${parentActivityClass}"
            </#if>>
            <#if parentActivityClass != "">
                <meta-data android:name="android.support.PARENT_ACTIVITY"
                    android:value="${parentActivityClass}" />
            </#if>
            <#if isInstantApp>
                <intent-filter xmlns:instant="http://schemas.android.com/instantapps" 
                    instant:order="${instantAppActivityOrder}">
                    <action android:name="android.intent.action.VIEW" />
                    <category android:name="android.intent.category.BROWSABLE" />
                    <category android:name="android.intent.category.DEFAULT" />
                    <data
                        android:host="${instantAppActivityHost}"
                        android:${instantAppActivityRouteType}="${instantAppActivityRoute}"
                        android:scheme="https" />
                </intent-filter>
            </#if>
            <#if isLauncher && (!(isLibraryProject!false) || isInstantApp)>
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.LAUNCHER" />
                </intent-filter>
            </#if>
        </activity>
    </application>
</manifest>
