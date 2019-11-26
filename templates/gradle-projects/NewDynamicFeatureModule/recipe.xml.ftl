<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addKotlinToBaseProject />
    <mkdir at="${escapeXmlAttribute(projectOut)}" />
    <mkdir at="${escapeXmlAttribute(srcOut)}" />
    <mkdir at="${escapeXmlAttribute(resOut)}/drawable" />
    <addIncludeToSettings />
    <addDynamicFeature name="${projectName}"
	                 to="${baseFeatureDir}" />
    <#if isInstantModule>
      <merge from="root/base-AndroidManifest.xml.ftl"
               to="${escapeXmlAttribute(baseFeatureDir)}/src/main/AndroidManifest.xml" />
    </#if>
    <merge from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(baseFeatureResDir)}/values/strings.xml" />
    <instantiate from="root/build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <instantiate from="root/AndroidManifest.xml.ftl"
                   to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    <copy from="root://gradle-projects/common/gitignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />

    <instantiate from="root/test/dynamicfeature_package/ExampleInstrumentedTest.${ktOrJavaExt}.ftl"
             to="${escapeXmlAttribute(testOut)}/ExampleInstrumentedTest.${ktOrJavaExt}" />

    <instantiate from="root://gradle-projects/NewAndroidModule/root/test/app_package/ExampleUnitTest.${ktOrJavaExt}.ftl"
                 to="${escapeXmlAttribute(unitTestOut)}/ExampleUnitTest.${ktOrJavaExt}" />
    <dependency mavenUrl="junit:junit:4.12" gradleConfiguration="testCompile" />

    <#if improvedTestDeps>
    <dependency mavenUrl="com.android.support.test:runner:+" gradleConfiguration="androidTestCompile" />
    <dependency mavenUrl="com.android.support.test.espresso:espresso-core:+" gradleConfiguration="androidTestCompile" />
    <!--The following dependency is added to pass UI tests in AddDynamicFeatureTest. b/123781255-->
    <dependency mavenUrl="com.android.support:support-annotations:${buildApi}.+" gradleConfiguration="androidTestCompile" />
    </#if>

    <#if generateKotlin && useAndroidX>
    <dependency mavenUrl="androidx.core:core-ktx:+" />
    </#if>
</recipe>
