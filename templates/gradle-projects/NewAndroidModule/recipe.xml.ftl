<?xml version="1.0"?>
<recipe>
    <mkdir at="${escapeXmlAttribute(projectOut)}/libs" />
    <mkdir at="${escapeXmlAttribute(resOut)}/drawable" />

    <merge from="root/settings.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/settings.gradle" />

    <instantiate from="root/build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <instantiate from="root/AndroidManifest.xml.ftl"
                   to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />
<#if hasInstantAppWrapper>
    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(baseFeatureResOut)}/values/strings.xml" />
<#else>
    <instantiate from="root/res/values/strings.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/strings.xml" />
</#if>
    <instantiate from="root/test/app_package/ExampleInstrumentedTest.java.ftl"
                   to="${escapeXmlAttribute(testOut)}/ExampleInstrumentedTest.java" />

<#if unitTestsSupported>
    <instantiate from="root/test/app_package/ExampleUnitTest.java.ftl"
                   to="${escapeXmlAttribute(unitTestOut)}/ExampleUnitTest.java" />
    <dependency mavenUrl="junit:junit:4.12" gradleConfiguration="testCompile" />
</#if>

<#if !createActivity>
    <mkdir at="${escapeXmlAttribute(srcOut)}" />
</#if>

<#if backwardsCompatibility!true>
    <dependency mavenUrl="com.android.support:appcompat-v7:${buildApi}.+" />
</#if>

<#if makeIgnore>
    <copy from="root/module_ignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />
</#if>

<#if enableProGuard>
    <instantiate from="root/proguard-rules.txt.ftl"
                   to="${escapeXmlAttribute(projectOut)}/proguard-rules.pro" />
</#if>

<#if hasMonolithicAppWrapper>
    <mkdir at="${monolithicAppOut}" />
    <instantiate from="root/monolithic-AndroidManifest.xml.ftl"
                   to="${monolithicAppOut}/src/main/AndroidManifest.xml" />
    <instantiate from="root/monolithic-build.gradle.ftl"
                   to="${monolithicAppOut}/build.gradle" />
    <#if makeIgnore>
        <copy from="root/module_ignore"
                to="${monolithicAppOut}/.gitignore" />
    </#if>
</#if>

<#if hasInstantAppWrapper>
    <mkdir at="${instantAppOut}" />
    <instantiate from="root/instantApp-build.gradle.ftl"
                   to="${instantAppOut}/build.gradle" />
    <#if makeIgnore>
        <copy from="root/module_ignore"
                to="${instantAppOut}/.gitignore" />
    </#if>

    <mkdir at="${baseFeatureOut}" />
    <instantiate from="root/baseFeature-AndroidManifest.xml.ftl"
                   to="${baseFeatureOut}/src/main/AndroidManifest.xml" />
    <instantiate from="root/baseFeature-build.gradle.ftl"
                   to="${baseFeatureOut}/build.gradle" />
    <#if makeIgnore>
        <copy from="root/module_ignore"
                to="${baseFeatureOut}/.gitignore" />
    </#if>
</#if>

<#macro copyIconCommands destination>
    <#if buildApi gte 25 && targetApi gte 25>
        <copy from="root/res/mipmap-hdpi/"
                to="${destination}/mipmap-hdpi/" />
        <copy from="root/res/mipmap-mdpi"
                to="${destination}/mipmap-mdpi/" />
        <copy from="root/res/mipmap-xhdpi"
                to="${destination}/mipmap-xhdpi/" />
        <copy from="root/res/mipmap-xxhdpi"
                to="${destination}/mipmap-xxhdpi/" />
        <copy from="root/res/mipmap-xxxhdpi"
                to="${destination}/mipmap-xxxhdpi/" />
    <#else>
        <copy from="root/res/mipmap-hdpi/ic_launcher.png"
                to="${destination}/mipmap-hdpi/ic_launcher.png" />
        <copy from="root/res/mipmap-mdpi/ic_launcher.png"
                to="${destination}/mipmap-mdpi/ic_launcher.png" />
        <copy from="root/res/mipmap-xhdpi/ic_launcher.png"
                to="${destination}/mipmap-xhdpi/ic_launcher.png" />
        <copy from="root/res/mipmap-xxhdpi/ic_launcher.png"
                to="${destination}/mipmap-xxhdpi/ic_launcher.png" />
        <copy from="root/res/mipmap-xxxhdpi/ic_launcher.png"
                to="${destination}/mipmap-xxxhdpi/ic_launcher.png" />
    </#if>
</#macro>

<#if copyIcons>
    <#if !isLibraryProject>
        <@copyIconCommands destination=escapeXmlAttribute(resOut)/>
    <#elseif hasInstantAppWrapper || isBaseFeature>
        <@copyIconCommands destination=escapeXmlAttribute(baseFeatureResOut)/>
    </#if>
</#if>

<#if !isLibraryProject>
    <instantiate from="root/res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(resOut)}/values/styles.xml" />
    <#if buildApi gte 22>
        <copy from="root/res/values/colors.xml"
                to="${escapeXmlAttribute(resOut)}/values/colors.xml" />
    </#if>
</#if>

<#if hasInstantAppWrapper || isBaseFeature>
    <instantiate from="root/res/values/styles.xml.ftl"
                   to="${escapeXmlAttribute(baseFeatureResOut)}/values/styles.xml" />
    <#if buildApi gte 22>
        <copy from="root/res/values/colors.xml"
                to="${escapeXmlAttribute(baseFeatureResOut)}/values/colors.xml" />
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
