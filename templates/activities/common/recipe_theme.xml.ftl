<recipe folder="root://activities/common">
<#if !themeExists >
    <#if isDynamicFeature>
    <merge from="root/res/values/theme_styles.xml.ftl"
             to="${escapeXmlAttribute(baseFeatureResOut)}/values/styles.xml" />
    <#else>
    <merge from="root/res/values/theme_styles.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    </#if>
</#if>
</recipe>
