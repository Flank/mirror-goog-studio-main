<#import "../../common/shared_manifest_macros.ftl" as manifestMacros>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name=".ui.login.${activityClass}"
            <#if isNewModule>
            android:label="@string/app_name"
            <#else>
            android:label="@string/title_${simpleName}"
            </#if>
            <#if hasNoActionBar>
            android:theme="@style/${themeNameNoActionBar}"
            <#elseif !(hasApplicationTheme!false)>
            android:theme="@style/${themeName}"
            </#if>>
            <@manifestMacros.commonActivityBody />
        </activity>
    </application>
</manifest>
