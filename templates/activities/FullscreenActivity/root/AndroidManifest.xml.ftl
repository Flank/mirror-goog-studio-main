<#import "../../common/shared_manifest_macros.ftl" as manifestMacros>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <activity android:name="${packageName}.${activityClass}"
            <#if isNewModule>
            android:label="@string/app_name"
            <#else>
            android:label="@string/title_${simpleName}"
            </#if>
            android:configChanges="orientation|keyboardHidden|screenSize"
            android:theme="@style/FullscreenTheme">
            <@manifestMacros.commonActivityBody />
        </activity>
    </application>

</manifest>
