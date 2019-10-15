<#import "./shared_macros.ftl" as shared>
<#import "root://activities/common/kotlin_macros.ftl" as kt>
<#if isLibraryProject>
apply plugin: 'com.android.library'
<#elseif isDynamicFeature>
apply plugin: 'com.android.dynamic-feature'
<#else>
apply plugin: 'com.android.application'
</#if>
<@kt.addKotlinPlugins />

<@shared.androidConfig hasApplicationId=isApplicationProject applicationId=packageName hasTests=true canHaveCpp=true canUseProguard=isApplicationProject||isLibraryProject />

<#if isCompose!false>
android {
    Configuration kotlinPluginConfiguration = configurations.create("kotlinPlugin")
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach { compile ->
        compile.dependsOn(kotlinPluginConfiguration)
        compile.doFirst {
            kotlinOptions {
                useIR = true
                freeCompilerArgs = [
                        "-Xplugin=$<#-- -->{kotlinPluginConfiguration.files.first()}"
                ]
            }
        }
    }
}
</#if>
dependencies {
<#if isCompose!false>
    kotlinPlugin "androidx.compose:compose-compiler:+"
</#if>
    ${getConfigurationName("compile")} fileTree(dir: 'libs', include: ['*.jar'])
    <#if !improvedTestDeps>
    ${getConfigurationName("androidTestCompile")}('${resolveDependency("com.android.support.test.espresso:espresso-core:+")}', {
        exclude group: 'com.android.support', module: 'support-annotations'
    })
    </#if>
    <@kt.addKotlinDependencies />
<#if isDynamicFeature>
  implementation project(':${baseFeatureName}')
<#elseif (WearprojectName?has_content) && (Mobileincluded!false) && (Wearincluded!false)>
  wearApp project(':${WearprojectName}')
</#if>
}
