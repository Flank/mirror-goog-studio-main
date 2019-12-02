<?xml version="1.0"?>
<!-- TODO: Add driver module template -->
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
    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
    <instantiate from="root://gradle-projects/NewAndroidModule/root/test/app_package/ExampleInstrumentedTest.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(testOut)}/ExampleInstrumentedTest.${ktOrJavaExt}" />
    <instantiate from="root://gradle-projects/NewAndroidModule/root/test/app_package/ExampleUnitTest.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(unitTestOut)}/ExampleUnitTest.${ktOrJavaExt}" />
    <dependency mavenUrl="junit:junit:4.12" gradleConfiguration="testCompile" />

    <dependency mavenUrl="com.android.support.test:runner:+" gradleConfiguration="androidTestCompile" />
    <dependency mavenUrl="com.android.support.test.espresso:espresso-core:+" gradleConfiguration="androidTestCompile" />
    <dependency mavenUrl="com.google.android.things:androidthings:+" gradleConfiguration="provided" />

    <mkdir at="${escapeXmlAttribute(srcOut)}" />

    <copy from="root://gradle-projects/common/gitignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />

    <#include "root://gradle-projects/common/proguard_recipe.xml.ftl"/>

    <instantiate from="root/res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    <copy from="root/res/values/colors.xml"
            to="${escapeXmlAttribute(resOut)}/values/colors.xml" />

    <#if generateKotlin && useAndroidX>
        <dependency mavenUrl="androidx.core:core-ktx:+" />
    </#if>
</recipe>
