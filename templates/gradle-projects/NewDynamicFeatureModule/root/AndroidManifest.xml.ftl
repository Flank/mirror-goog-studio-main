<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:dist="http://schemas.android.com/apk/distribution"
    package="${packageName}">
    <#if dynamicFeatureSupportsDynamicDelivery>
    <dist:module
        dist:instant="${isInstantModule?c}"
        dist:title="@string/title_${projectSimpleName}">
        <dist:delivery>
            <#if dynamicFeatureInstallTimeDelivery>
            <dist:install-time/>
            </#if>
            <#if dynamicFeatureInstallTimeWithConditionsDelivery>
            <dist:install-time>
                <dist:conditions>
                    <#if dynamicFeatureMinSdkDelivery!="">
                    <dist:min-sdk dist:value="${dynamicFeatureMinSdkDelivery}"/>
                    </#if>
                    <#list dynamicFeatureDeviceFeatureList as deviceFeature>
                    <dist:device-feature ${deviceFeature.deviceFeatureType().get().getDisplayName()}="${deviceFeature.deviceFeatureValue()}" />
                    </#list>
                </dist:conditions>
            </dist:install-time>
            </#if>
            <#if dynamicFeatureOnDemandDelivery>
            <dist:on-demand/>
            </#if>
        </dist:delivery>
        <dist:fusing dist:include="${dynamicFeatureFusing?c}" />
    </dist:module>
    <#else>
    <dist:module
        dist:onDemand="${dynamicFeatureOnDemand?c}"
        dist:instant="${isInstantModule?c}"
        dist:title="@string/title_${projectSimpleName}">
        <dist:fusing dist:include="${dynamicFeatureFusing?c}" />
    </dist:module>
    </#if>
</manifest>

