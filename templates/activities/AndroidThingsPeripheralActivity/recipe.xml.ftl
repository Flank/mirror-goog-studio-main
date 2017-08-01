<?xml version="1.0"?>
<recipe>

  <#if requireTheme!false>
  <#include "../common/recipe_theme.xml.ftl" />
  </#if>

  <merge from="root/AndroidManifest.xml.ftl"
           to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
  <merge from="root/build.gradle.ftl"
           to="${escapeXmlAttribute(projectOut)}/build.gradle" />

<#if generateKotlin>
    <instantiate from="root/src/app_package/SimpleActivity.kt.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.kt" />
    <#if integrateAccelerometer>
      <instantiate from="root/src/app_package/AccelerometerService.kt.ftl"
                     to="${escapeXmlAttribute(srcOut)}/${accelerometerServiceClass}.kt" />
      <open file="${escapeXmlAttribute(srcOut)}/${accelerometerServiceClass}.java" />
    </#if>
    <#if integrateGps>
      <instantiate from="root/src/app_package/GpsService.kt.ftl"
                     to="${escapeXmlAttribute(srcOut)}/${gpsServiceClass}.kt" />
      <open file="${escapeXmlAttribute(srcOut)}/${gpsServiceClass}.kt" />
    </#if>
    <#if integrateTemperaturePressureSensor>
      <instantiate from="root/src/app_package/TemperaturePressureService.kt.ftl"
                     to="${escapeXmlAttribute(srcOut)}/${temperaturePressureServiceClass}.kt" />
      <open file="${escapeXmlAttribute(srcOut)}/${temperaturePressureServiceClass}.kt" />
    </#if>
<#else>
    <instantiate from="root/src/app_package/SimpleActivity.java.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
    <#if integrateAccelerometer>
      <instantiate from="root/src/app_package/AccelerometerService.java.ftl"
                     to="${escapeXmlAttribute(srcOut)}/${accelerometerServiceClass}.java" />
      <open file="${escapeXmlAttribute(srcOut)}/${accelerometerServiceClass}.java" />
    </#if>
    <#if integrateGps>
      <instantiate from="root/src/app_package/GpsService.java.ftl"
                     to="${escapeXmlAttribute(srcOut)}/${gpsServiceClass}.java" />
      <open file="${escapeXmlAttribute(srcOut)}/${gpsServiceClass}.java" />
    </#if>
    <#if integrateTemperaturePressureSensor>
      <instantiate from="root/src/app_package/TemperaturePressureService.java.ftl"
                     to="${escapeXmlAttribute(srcOut)}/${temperaturePressureServiceClass}.java" />
      <open file="${escapeXmlAttribute(srcOut)}/${temperaturePressureServiceClass}.java" />
    </#if>
</#if>

</recipe>
