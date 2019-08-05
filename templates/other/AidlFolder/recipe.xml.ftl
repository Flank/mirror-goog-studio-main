<?xml version="1.0"?>
<recipe>

  <#if remapFolder>
    <mkdir at="${escapeXmlAttribute(projectOut)}/${escapeXmlAttribute(newLocation)}" />
    <sourceSet type="aidl" name="${sourceProviderName}" dir="src/${sourceProviderName}/aidl" />
    <sourceSet type="aidl" name="${sourceProviderName}" dir="${newLocation}" />
  <#else>
      <mkdir at="${escapeXmlAttribute(manifestOut)}/aidl/" />
  </#if>

</recipe>
