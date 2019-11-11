<?xml version="1.0"?>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<recipe>
    <@kt.addKotlinToBaseProject />

    <addIncludeToSettings />
    <instantiate from="root/build.gradle.ftl"
                   to="${escapeXmlAttribute(projectOut)}/build.gradle" />
    <instantiate from="root/src/library_package/Placeholder.${ktOrJavaExt}.ftl"
                   to="${escapeXmlAttribute(srcOut)}/${className}.${ktOrJavaExt}" />
    <copy from="root://gradle-projects/common/gitignore"
            to="${escapeXmlAttribute(projectOut)}/.gitignore" />

    <mkdir at="${escapeXmlAttribute(projectOut)}/libs" />
</recipe>
