<?xml version="1.0"?>
<recipe>
    <merge from="root/base_build.gradle.ftl"
           to="${escapeXmlAttribute(projectLocation)}/build.gradle" />

    <merge from="root/settings.gradle.ftl"
             to="${escapeXmlAttribute(topOut)}/settings.gradle" />
    <copy from="root/benchmark-proguard-rules.pro"
          to="${escapeXmlAttribute(projectOut)}/benchmark-proguard-rules.pro" />

    <instantiate from="root/build.gradle.ftl"
                 to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <dependency mavenUrl="androidx.test:runner:+" gradleConfiguration="androidTestImplementation" />
    <dependency mavenUrl="androidx.test.ext:junit:+" gradleConfiguration="androidTestImplementation" />
    <dependency mavenUrl="junit:junit:4.12" gradleConfiguration="androidTestImplementation" />
    <dependency mavenUrl="androidx.benchmark:benchmark:1.0.0-alpha01-SNAPSHOT01" gradleConfiguration="androidTestImplementation" />

    <instantiate from="root/src/main/AndroidManifest.xml.ftl"
                 to="${escapeXmlAttribute(projectOut)}/src/main/AndroidManifest.xml" />
    <instantiate from="root/src/androidTest/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(projectOut)}/src/androidTest/AndroidManifest.xml" />

<#if generateKotlin>
    <instantiate from="root/src/androidTest/module_package/ExampleBenchmark.kt.ftl"
                 to="${escapeXmlAttribute(testOut)}/ExampleBenchmark.kt" />
<#else>
    <instantiate from="root/src/androidTest/module_package/ExampleBenchmark.java.ftl"
                 to="${escapeXmlAttribute(testOut)}/ExampleBenchmark.java" />
</#if>
</recipe>
