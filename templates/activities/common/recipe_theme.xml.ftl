<recipe folder="root://activities/common">
<#if !themeExists && appCompat>
    <merge from="root/res/values/theme_styles.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
</#if>
</recipe>
