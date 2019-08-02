<#import "../shared_manifest_macros.ftl" as manifestMacros>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application>
        <activity android:name="${packageName}.${activityClass}"
            <#if generateActivityTitle!true>
                <#if isNewModule>
                    android:label="@string/app_name"
                <#else>
                    android:label="@string/title_${activityToLayout(activityClass)}"
                </#if>
            </#if>
            <#if hasNoActionBar>
                android:theme="@style/${themeNameNoActionBar}"
            <#elseif (requireTheme!false) && !hasApplicationTheme >
                android:theme="@style/${themeName}"
            </#if>>
            <@manifestMacros.commonActivityBody />
        </activity>
    </application>
</manifest>
