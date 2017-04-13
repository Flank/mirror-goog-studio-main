<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.instantappbundle'

<@shared.androidConfig />

dependencies {
    implementation project(':${projectName}')
    implementation project(':${baseLibName}')
}
