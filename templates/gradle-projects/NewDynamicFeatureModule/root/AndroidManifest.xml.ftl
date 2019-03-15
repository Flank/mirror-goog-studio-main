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
                    <!-- To include or exclude this module by user countries, uncomment and update this section. -->
                    <!-- Learn more @ [https://d.android.com/r/studio-ui/dynamic-delivery/conditional-delivery] -->
                    <!--   <dist:user-countries dist:exclude="false"> -->
                    <!--     <dist:country dist:code="US" /> -->
                    <!--   </dist:user-countries> -->
                    <#list dynamicFeatureDeviceFeatureList as deviceFeature>
                        <#if deviceFeature.deviceFeatureType().get().getDisplayName() == "Name">
                            <dist:device-feature dist:name="${deviceFeature.deviceFeatureValue()}" />
                        <#elseif deviceFeature.deviceFeatureType().get().getDisplayName() == "OpenGL ES Version">
                            <dist:device-feature dist:name="android.hardware.opengles.version" dist:version="${deviceFeature.deviceFeatureValue()}" />
                        </#if>
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

