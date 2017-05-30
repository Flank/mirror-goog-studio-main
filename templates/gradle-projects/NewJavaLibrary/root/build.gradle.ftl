apply plugin: 'java'

dependencies {
    ${getConfigurationName("compile")} fileTree(dir: 'libs', include: ['*.jar'])
}

<#if javaVersion??>
sourceCompatibility = "${javaVersion}"
targetCompatibility = "${javaVersion}"
</#if>
