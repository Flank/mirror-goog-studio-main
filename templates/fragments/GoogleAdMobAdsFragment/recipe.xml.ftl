<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addAllKotlinDependencies />
    <dependency mavenUrl="com.google.android.gms:play-services-ads:+" />
    <dependency mavenUrl="com.android.support.constraint:constraint-layout:+" />

    <#assign escapedResOut="${escapeXmlAttribute(resOut)}">
    <#assign escapedSrcOut="${escapeXmlAttribute(srcOut)}">

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapedResOut}/values/strings.xml" />

    <merge from="root/res/values/dimens.xml.ftl"
             to="${escapedResOut}/values/dimens.xml" />

    <instantiate from="root/res/layout/fragment_admob.xml.ftl"
             to="${escapedResOut}/layout/${layoutName}.xml" />

<#if adFormat == "interstitial">
    <instantiate from="root/src/app_package/AdMobInterstitialAdFragment.${ktOrJavaExt}.ftl"
             to="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />
<#elseif adFormat == "banner">
    <instantiate from="root/src/app_package/AdMobBannerAdFragment.${ktOrJavaExt}.ftl"
             to="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />
</#if>

    <open file="${escapedSrcOut}/${fragmentClass}.${ktOrJavaExt}" />
    <open file="${escapedResOut}/layout/${layoutName}.xml" />
</recipe>
