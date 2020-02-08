<#import "root://activities/common/kotlin_macros.ftl" as kt>
apply plugin: 'java-library'
<#if generateKotlin>
apply plugin: 'kotlin'
</#if>

dependencies {
    <@kt.addKotlinDependencies />
}

<#if javaVersion??>
sourceCompatibility = "${javaVersion}"
targetCompatibility = "${javaVersion}"
</#if>
