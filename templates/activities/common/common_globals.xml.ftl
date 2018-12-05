<globals>
    <#assign theme=getApplicationTheme()!{ "name": "AppTheme"}>
    <#assign themeName=theme.name!'AppTheme'>
    <#assign themeNameNoActionBar=theme.nameNoActionBar!'AppTheme.NoActionBar'>
    <#assign espresso=hasDependency('com.android.support.test.espresso:espresso-core', 'androidTestCompile')>
    <#assign useAndroidX=isAndroidxEnabled()>
    <#assign useMaterial2=useAndroidX || hasDependency('com.google.android.material:material')>
    <#assign supportRunner=hasDependency('com.android.support.test:runner', 'androidTestCompile')>
    <#assign testSupportLib=espresso && supportRunner>

    <global id="themeName" type="string" value="${themeName}" />
    <global id="themeExists" type="boolean" value="${(theme.exists!false)?string}" />
    <global id="implicitParentTheme" type="boolean" value="${(themeNameNoActionBar?starts_with(themeName+'.'))?string}" />
    <global id="themeNameNoActionBar" type="string" value="${themeNameNoActionBar}" />
    <global id="themeExistsNoActionBar" type="boolean" value="${(theme.existsNoActionBar!false)?string}" />
    <global id="themeNameAppBarOverlay" type="string" value="${theme.nameAppBarOverlay!'AppTheme.AppBarOverlay'}" />
    <global id="themeExistsAppBarOverlay" type="boolean" value="${(theme.existsAppBarOverlay!false)?string}" />
    <global id="themeNamePopupOverlay" type="string" value="${theme.namePopupOverlay!'AppTheme.PopupOverlay'}" />
    <global id="themeExistsPopupOverlay" type="boolean" value="${(theme.existsPopupOverlay!false)?string}" />
    <global id="hasApplicationTheme" type="boolean" value="${(hasApplicationTheme!true)?string}" />

    <global id="useMaterial2" type="boolean" value="${useMaterial2?string}" />
    <global id="useAndroidX" type="boolean" value="${useAndroidX?string}" />
    <global id="hasNoActionBar" type="boolean" value="true" /> <#-- It's overridden in each template if necessary -->
    <global id="testSupportLib" type="boolean" value="${testSupportLib?string}" />

    <global id="isInstantApp" type="boolean" value="false" />
    <global id="instantAppActivityOrder" type="string" value="1" />
    <global id="instantAppActivityHost" type="string" value="instantapp.example.com" />
    <global id="instantAppActivityRoute" type="string" value="" />
    <global id="instantAppActivityRouteType" type="string" value="path" />
    <global id="includeInstantAppUrl" type="boolean" value="false" />
    <global id="baseFeatureOut" type="string" value="${escapeXmlAttribute(baseFeatureDir!'.')}" />
    <global id="baseFeatureResOut" type="string" value="${escapeXmlAttribute(baseFeatureResDir!'./src/main/res')}" />

    <global id="isDynamicFeature" type="boolean" value="false" />

    <global id="manifestOut" value="${manifestDir}" />
    <global id="buildVersion" value="${buildApi}" />
    <global id="buildApiRevision" type="integer" value="${buildApiRevision!0}" />

    <global id="superClass" type="string" value="AppCompatActivity"/>
    <global id="superClassFqcn" type="string" value="${getMaterialComponentName('android.support.v7.app.AppCompatActivity', useAndroidX)}"/>
    <global id="Support" value="Support" />
    <global id="actionBarClassFqcn" type = "string" value="${getMaterialComponentName('android.support.v7.app.ActionBar', useAndroidX)}" />
    <global id="kotlinActionBar" type="string" value="supportActionBar" />
    <global id="kotlinFragmentManager" type="string" value="supportFragmentManager" />

    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="menuName" value="${classToResource(activityClass!'')}" />
    <global id="simpleName" value="${activityToLayout(activityClass!'')}" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
