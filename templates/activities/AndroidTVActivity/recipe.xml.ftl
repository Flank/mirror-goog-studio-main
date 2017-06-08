<?xml version="1.0"?>
<recipe>

    <dependency mavenUrl="com.android.support:appcompat-v7:+"/>
    <dependency mavenUrl="com.android.support:leanback-v17:+"/>
    <dependency mavenUrl="com.github.bumptech.glide:glide:3.8.0"/>

    <merge from="root/AndroidManifest.xml.ftl"
             to="${escapeXmlAttribute(manifestOut)}/AndroidManifest.xml" />

    <merge from="root/res/values/strings.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/strings.xml" />

    <merge from="root/res/values/colors.xml.ftl"
             to="${escapeXmlAttribute(resOut)}/values/colors.xml" />

    <copy from="root/res/drawable"
                to="${escapeXmlAttribute(resOut)}/drawable" />
    
    <instantiate from="root/res/layout/activity_main.xml.ftl"
                  to="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />

    <instantiate from="root/res/layout/activity_details.xml.ftl"
                  to="${escapeXmlAttribute(resOut)}/layout/${detailsLayoutName}.xml" />

    <instantiate from="root/src/app_package/MainActivity.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />

    <instantiate from="root/src/app_package/MainFragment.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/${mainFragment}.java" />

    <instantiate from="root/src/app_package/DetailsActivity.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/${detailsActivity}.java" />

    <instantiate from="root/src/app_package/VideoDetailsFragment.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/${detailsFragment}.java" />

    <instantiate from="root/src/app_package/Movie.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/Movie.java" />

    <instantiate from="root/src/app_package/MovieList.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/MovieList.java" />

    <instantiate from="root/src/app_package/CardPresenter.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/CardPresenter.java" />

    <instantiate from="root/src/app_package/DetailsDescriptionPresenter.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/DetailsDescriptionPresenter.java" />

    <instantiate from="root/src/app_package/PlaybackActivity.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/PlaybackActivity.java" />

    <instantiate from="root/src/app_package/PlaybackVideoFragment.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/PlaybackVideoFragment.java" />

    <instantiate from="root/src/app_package/ErrorFragment.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/ErrorFragment.java" />

    <instantiate from="root/src/app_package/BrowseErrorActivity.java.ftl"
                  to="${escapeXmlAttribute(srcOut)}/BrowseErrorActivity.java" />

    <open file="${escapeXmlAttribute(srcOut)}/${activityClass}.java" />
    <open file="${escapeXmlAttribute(resOut)}/layout/${layoutName}.xml" />
</recipe>
