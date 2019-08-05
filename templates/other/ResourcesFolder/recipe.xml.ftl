<?xml version="1.0"?>
<recipe>

  <#if remapFolder>
    <mkdir at="${escapeXmlAttribute(projectOut)}/${escapeXmlAttribute(newLocation)}" />
    <sourceSet type="resources" name="${sourceProviderName}" dir="src/${sourceProviderName}/resources" />
    <sourceSet type="resources" name="${sourceProviderName}" dir="${newLocation}" />
  <#else>
    <mkdir at="${escapeXmlAttribute(manifestOut)}/resources/" />
  </#if>

</recipe>
