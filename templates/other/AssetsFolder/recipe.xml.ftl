<?xml version="1.0"?>
<recipe>

  <#if remapFolder>
    <mkdir at="${escapeXmlAttribute(projectOut)}/${escapeXmlAttribute(newLocation)}" />
    <sourceSet type="assets" name="${sourceProviderName}" dir="src/${sourceProviderName}/assets" />
    <sourceSet type="assets" name="${sourceProviderName}" dir="${newLocation}" />
  <#else>
      <mkdir at="${escapeXmlAttribute(manifestOut)}/assets/" />
  </#if>

</recipe>
