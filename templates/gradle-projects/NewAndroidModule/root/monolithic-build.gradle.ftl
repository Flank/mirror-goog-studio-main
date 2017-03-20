<#import "./shared_macros.ftl" as shared>
apply plugin: 'com.android.application'

<@shared.androidConfig hasApplicationId=true/>

dependencies {
    compile project(':${projectName}')
}
