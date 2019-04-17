<recipe folder="root://gradle-projects/common">

    <instantiate from="proguard-rules.txt.ftl"
                   to="${escapeXmlAttribute(projectOut)}/proguard-rules.pro" />

    <#if (isLibraryProject!false) >
    <instantiate from="consumer-rules.pro.ftl"
                   to="${escapeXmlAttribute(projectOut)}/consumer-rules.pro" />
    </#if>
</recipe>
