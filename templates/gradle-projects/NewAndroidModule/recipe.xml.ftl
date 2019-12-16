<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addKotlinToBaseProject />

    <mkdir at="${escapeXmlAttribute(projectOut)}/libs" />
    <mkdir at="${escapeXmlAttribute(resOut)}/drawable" />

    <addIncludeToSettings />

    <instantiate from="root/build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />

    <instantiate from="root/AndroidManifest.xml.ftl"
	    to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <copy from="root://gradle-projects/common/gitignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />

<#if !isLibraryProject>
    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
</#if>
    <instantiate from="root/test/app_package/ExampleInstrumentedTest.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(testOut)}/ExampleInstrumentedTest.${ktOrJavaExt}" />
    <instantiate from="root/test/app_package/ExampleUnitTest.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(unitTestOut)}/ExampleUnitTest.${ktOrJavaExt}" />
    <dependency mavenUrl="junit:junit:4.12" gradleConfiguration="testCompile" />

<#if improvedTestDeps>
    <dependency mavenUrl="com.android.support.test:runner:+" gradleConfiguration="androidTestCompile" />
    <dependency mavenUrl="com.android.support.test.espresso:espresso-core:+" gradleConfiguration="androidTestCompile" />
</#if>

    <mkdir at="${escapeXmlAttribute(srcOut)}" />

    <#include "root://gradle-projects/common/proguard_recipe.xml.ftl"/>

<#macro copyIconCommands destination>
    <#-- Copy adaptive-icons -->
    <copy from="root/res/mipmap-anydpi-v26/ic_launcher.xml"
            to="${destination}/mipmap-anydpi-v26/ic_launcher.xml" />
    <copy from="root/res/drawable/ic_launcher_background.xml"
            to="${destination}/drawable/ic_launcher_background.xml" />
    <copy from="root/res/drawable-v24/ic_launcher_foreground.xml"
            to="${destination}/drawable-v24/ic_launcher_foreground.xml" />
    <copy from="root/res/mipmap-anydpi-v26/ic_launcher_round.xml"
            to="${destination}/mipmap-anydpi-v26/ic_launcher_round.xml" />
    <@copyMipmap destination=escapeXmlAttribute(destination)
                        icon="ic_launcher_round.png" />

    <@copyMipmap destination=escapeXmlAttribute(destination) icon="ic_launcher.png" />
</#macro>

<#macro copyMipmap destination icon>
    <copy from="root/res/mipmap-hdpi/${icon}"
            to="${destination}/mipmap-hdpi/${icon}" />
    <copy from="root/res/mipmap-mdpi/${icon}"
            to="${destination}/mipmap-mdpi/${icon}" />
    <copy from="root/res/mipmap-xhdpi/${icon}"
            to="${destination}/mipmap-xhdpi/${icon}" />
    <copy from="root/res/mipmap-xxhdpi/${icon}"
            to="${destination}/mipmap-xxhdpi/${icon}" />
    <copy from="root/res/mipmap-xxxhdpi/${icon}"
            to="${destination}/mipmap-xxxhdpi/${icon}" />
</#macro>

<#if !isLibraryProject>
  <@copyIconCommands destination=escapeXmlAttribute(resOut)/>
</#if>

<#if !isLibraryProject>
    <instantiate from="root/res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    <copy from="root/res/values/colors.xml"
            to="${escapeXmlAttribute(resOut)}/values/colors.xml" />
</#if>

<#if includeCppSupport>
    <mkdir at="${escapeXmlAttribute(nativeSrcOut)}" />

    <instantiate from="root/CMakeLists.txt.ftl"
                   to="${escapeXmlAttribute(nativeSrcOut)}/CMakeLists.txt" />
</#if>

<#if (Mobileincluded!false) && (Wearincluded!false)>
    <dependency mavenUrl="com.google.android.gms:play-services-wearable:+" gradleConfiguration="compile" />
</#if>

<#if generateKotlin && useAndroidX>
    <dependency mavenUrl="androidx.core:core-ktx:+" />
</#if>
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+" />
</recipe>
