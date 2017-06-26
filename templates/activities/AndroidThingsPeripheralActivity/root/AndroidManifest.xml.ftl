<#import "../../common/shared_manifest_macros.ftl" as manifestMacros>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

<#if integrateGps>
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
</#if>

    <application>

        <uses-library android:name="com.google.android.things"/>

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
            </#if>>
            <@manifestMacros.commonActivityBody />
            <#if isLauncher>
                <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <category android:name="android.intent.category.IOT_LAUNCHER" />
                    <category android:name="android.intent.category.DEFAULT" />
                </intent-filter>
            </#if>
        </activity>
    </application>
</manifest>
