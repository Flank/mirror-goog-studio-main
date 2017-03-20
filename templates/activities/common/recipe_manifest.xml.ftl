<recipe folder="root://activities/common">

    <#if requireTheme!false>
    <#include "recipe_theme.xml.ftl" />
    </#if>

    <#if isFeatureSplit>
        <merge from="root/AndroidManifest.xml.ftl"
                 to="${escapeXmlAttribute(baseSplitManifestOut)}/AndroidManifest.xml" />
    <#else>
        <merge from="root/AndroidManifest.xml.ftl"
                 to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    </#if>
    <merge from="root/res/values/manifest_strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

</recipe>
