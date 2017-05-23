<?xml version="1.0"?>
<!-- TODO: check include Cpp support; add driver module template -->
<recipe>
    <mkdir at="${escapeXmlAttribute(projectOut)}/libs" />
    <mkdir at="${escapeXmlAttribute(resOut)}/drawable" />

    <merge from="root/settings.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/settings.gradle" />

    <instantiate from="root/build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <instantiate from="root/AndroidManifest.xml.ftl"
                   to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
<#if generateKotlin>
    <instantiate from="root/test/app_package/ExampleInstrumentedTest.kt.ftl"
                   to="${escapeXmlAttribute(testOut)}/ExampleInstrumentedTest.kt" />
<#else>
    <instantiate from="root/test/app_package/ExampleInstrumentedTest.java.ftl"
                   to="${escapeXmlAttribute(testOut)}/ExampleInstrumentedTest.java" />
</#if>

<#if unitTestsSupported>
<#if generateKotlin>
    <instantiate from="root/test/app_package/ExampleUnitTest.kt.ftl"
                   to="${escapeXmlAttribute(unitTestOut)}/ExampleUnitTest.kt" />
<#else>
    <instantiate from="root/test/app_package/ExampleUnitTest.java.ftl"
                   to="${escapeXmlAttribute(unitTestOut)}/ExampleUnitTest.java" />
</#if>
    <dependency mavenUrl="junit:junit:4.12" gradleConfiguration="testCompile" />
</#if>

<#if !createActivity>
    <mkdir at="${escapeXmlAttribute(srcOut)}" />
</#if>

<#if makeIgnore>
    <copy from="root/module_ignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />
</#if>

<#if enableProGuard>
    <instantiate from="root/proguard-rules.txt.ftl"
                   to="${escapeXmlAttribute(projectOut)}/proguard-rules.pro" />
</#if>

<#if !isLibraryProject && generateLayout!false>
    <instantiate from="root/res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    <#if buildApi gte 22>
        <copy from="root/res/values/colors.xml"
                to="${escapeXmlAttribute(resOut)}/values/colors.xml" />
    </#if>
</#if>

<#if includeCppSupport>
    <mkdir at="${escapeXmlAttribute(nativeSrcOut)}" />

    <instantiate from="root/CMakeLists.txt.ftl"
                   to="${escapeXmlAttribute(projectOut)}/CMakeLists.txt" />
    <instantiate from="root/native-lib.cpp.ftl"
                   to="${escapeXmlAttribute(nativeSrcOut)}/native-lib.cpp" />
</#if>

</recipe>
