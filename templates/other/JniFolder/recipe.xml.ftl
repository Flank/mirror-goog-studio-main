<?xml version="1.0"?>
<recipe>

  <#if remapFolder>
    <mkdir at="${escapeXmlAttribute(projectOut)}/${escapeXmlAttribute(newLocation)}" />
    <sourceSet type="jni" name="${sourceProviderName}" dir="src/${sourceProviderName}/jni" />
    <sourceSet type="jni" name="${sourceProviderName}" dir="${newLocation}" />
  <#else>
    <mkdir at="${escapeXmlAttribute(manifestOut)}/jni/" />
  </#if>

</recipe>
