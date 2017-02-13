<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <activity
            android:name="${relativePackage}.${CollectionName}Activity"
            <#if isNewProject>
            android:label="@string/app_name"
            <#else>
            android:label="@string/title_${collection_name}"
            </#if>
            <#if hasAppBar>
            android:theme="@style/${themeNameNoActionBar}"
            <#elseif !hasApplicationTheme>
            android:theme="@style/${themeName}"
            </#if>
            <#if buildApi gte 16 && parentActivityClass != "">android:parentActivityName="${parentActivityClass}"</#if>>
            <#if parentActivityClass != "">
            <meta-data android:name="android.support.PARENT_ACTIVITY"
                android:value="${parentActivityClass}" />
            </#if>
            <#if isLauncher && !(isLibraryProject!false)>
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            </#if>
        </activity>

        <activity android:name="${relativePackage}.${DetailName}Activity"
            android:label="@string/title_${detail_name}"
            <#if hasAppBar>
            android:theme="@style/${themeNameNoActionBar}"
            <#elseif !hasApplicationTheme>
            android:theme="@style/${themeName}"
            </#if>
            <#if buildApi gte 16>android:parentActivityName="${relativePackage}.${CollectionName}Activity"</#if>>
            <meta-data android:name="android.support.PARENT_ACTIVITY"
                android:value="${relativePackage}.${CollectionName}Activity" />
        </activity>
    </application>

</manifest>
