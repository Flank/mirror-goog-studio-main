<?xml version="1.0"?>
<recipe>

  <#if remapFolder>
    <mkdir at="${escapeXmlAttribute(projectOut)}/${escapeXmlAttribute(newLocation)}" />
    <sourceSet type="java" name="${sourceProviderName}" dir="src/${sourceProviderName}/java" />
    <sourceSet type="java" name="${sourceProviderName}" dir="${newLocation}" />
  <#else>
      <mkdir at="${escapeXmlAttribute(manifestOut)}/java/" />
  </#if>

</recipe>
