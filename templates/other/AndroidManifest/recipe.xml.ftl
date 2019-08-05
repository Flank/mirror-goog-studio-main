<?xml version="1.0"?>
<recipe>

    <#if remapFile>
        <merge from="root/AndroidManifest.xml.ftl"
                 to="${escapeXmlAttribute(projectOut)}/${escapeXmlAttribute(newLocation)}" />
        <sourceSet type="manifest" name="${sourceProviderName}" dir="${newLocation}" />
    <#else>
        <merge from="root/AndroidManifest.xml.ftl"
                 to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    </#if>

</recipe>
