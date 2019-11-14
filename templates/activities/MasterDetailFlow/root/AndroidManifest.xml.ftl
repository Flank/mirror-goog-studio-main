<#import "../../common/shared_manifest_macros.ftl" as manifestMacros>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <activity
            android:name="${packageName}.${CollectionName}Activity"
            <#if isNewModule>
            android:label="@string/app_name"
            <#else>
            android:label="@string/title_${collection_name}"
            </#if>
            android:theme="@style/${themeNameNoActionBar}">
            <@manifestMacros.commonActivityBody />
        </activity>

        <activity android:name="${packageName}.${DetailName}Activity"
            android:label="@string/title_${detail_name}"
            android:theme="@style/${themeNameNoActionBar}"
            android:parentActivityName="${packageName}.${CollectionName}Activity">
            <meta-data android:name="android.support.PARENT_ACTIVITY"
                android:value="${packageName}.${CollectionName}Activity" />
        </activity>
    </application>

</manifest>
