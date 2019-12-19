<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
<@kt.addKotlinToBaseProject />

    <mkdir at="${escapeXmlAttribute(srcOut)}" />
    <#if generateKotlin && useAndroidX>
        <dependency mavenUrl="androidx.core:core-ktx:+" />
    </#if>
    <dependency mavenUrl="com.google.android.support:wearable:+" />
    <dependency mavenUrl="com.google.android.gms:play-services-wearable:+" />
    <dependency mavenUrl="com.android.support:percent:+" />
    <dependency mavenUrl="com.android.support:support-v4:+" />
    <dependency mavenUrl="com.android.support:recyclerview-v7:+" />

    <mkdir at="${escapeXmlAttribute(projectOut)}/libs" />

    <addIncludeToSettings />
    <instantiate from="root/build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <instantiate from="root/AndroidManifest.xml.ftl"
                   to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <copy from="root://gradle-projects/common/gitignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />
    <#include "root://gradle-projects/common/proguard_recipe.xml.ftl"/>
    <mkdir  at="${escapeXmlAttribute(resOut)}/drawable" />
    <copy from="root/res/mipmap-hdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-hdpi" />
    <copy from="root/res/mipmap-mdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-mdpi" />
    <copy from="root/res/mipmap-xhdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-xhdpi" />
    <copy from="root/res/mipmap-xxhdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-xxhdpi" />
    <copy from="root/res/mipmap-xxxhdpi"
            to="${escapeXmlAttribute(resOut)}/mipmap-xxxhdpi" />

    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
</recipe>
