<#-- Some common elements used in multiple files -->
<#macro commonActivityBody>
    <#if isLauncher && (!(isLibraryProject!false))>
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </#if>
</#macro>
