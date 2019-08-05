<?xml version="1.0"?>
<recipe>

  <#if remapFolder>
    <mkdir at="${escapeXmlAttribute(projectOut)}/${escapeXmlAttribute(newLocation)}" />
    <sourceSet type="res" name="${sourceProviderName}" dir="src/${sourceProviderName}/res" />
    <sourceSet type="res" name="${sourceProviderName}" dir="${newLocation}" />
  <#else>
    <mkdir at="${escapeXmlAttribute(manifestOut)}/res/" />
  </#if>

</recipe>
