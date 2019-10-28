<globals>
    <#assign theme=applicationTheme!{ "name": "AppTheme"}>
    <#assign themeName=theme.name!'AppTheme'>
    <#assign themeNameNoActionBar=theme.nameNoActionBar!'AppTheme.NoActionBar'>
    <#assign useAndroidX=addAndroidXSupport!false>
    <#assign useMaterial2=useAndroidX || hasDependency('com.google.android.material:material')>

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

    <global id="baseFeatureOut" type="string" value="${escapeXmlAttribute(baseFeatureDir!'.')}" />
    <global id="baseFeatureResOut" type="string" value="${escapeXmlAttribute(baseFeatureResDir!'./src/main/res')}" />

    <global id="isDynamicFeature" type="boolean" value="false" />

    <global id="manifestOut" value="${manifestDir}" />

    <global id="superClassFqcn" type="string" value="${getMaterialComponentName('android.support.v7.app.AppCompatActivity', useAndroidX)}"/>
    <global id="actionBarClassFqcn" type = "string" value="${getMaterialComponentName('android.support.v7.app.ActionBar', useAndroidX)}" />

    <global id="srcOut" value="${srcDir}/${slashedPackageName(packageName)}" />
    <global id="resOut" value="${resDir}" />
    <global id="menuName" value="${classToResource(activityClass!'')}" />
    <global id="simpleName" value="${activityToLayout(activityClass!'')}" />
    <#include "root://activities/common/kotlin_globals.xml.ftl" />
</globals>
