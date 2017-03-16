<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.atom'

<@shared.androidConfig />

dependencies {
    compile project(':${projectName}')
    compile project(':${baseSplitName}')
}
