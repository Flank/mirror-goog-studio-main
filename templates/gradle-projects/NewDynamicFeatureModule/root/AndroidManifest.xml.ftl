<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:dist="http://schemas.android.com/apk/distribution"
    package="${packageName}">
    <dist:module
        dist:onDemand="${dynamicFeatureOnDemand?c}"
	dist:instant="${dynamicFeatureInstant?c}"
        dist:title="@string/title_${projectSimpleName}">
        <dist:fusing include="${dynamicFeatureFusing?c}" />
    </dist:module>
</manifest>

