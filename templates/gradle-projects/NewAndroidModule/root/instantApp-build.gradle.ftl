<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.instantapp'

<@shared.androidConfig />

dependencies {
    compile project(':${splitName}')
}
