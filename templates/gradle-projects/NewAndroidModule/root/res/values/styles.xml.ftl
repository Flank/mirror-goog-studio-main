<resources>

    <!-- Base application theme. -->
    <style name="AppTheme" parent="Theme.AppCompat<#if
            minApiLevel gte 11>.Light</#if><#if
            minApiLevel gte 14>.DarkActionBar</#if>">
        <!-- Customize your theme here. -->
<#if (buildApi gte 22) >
        <item name="colorPrimary">@color/colorPrimary</item>
        <item name="colorPrimaryDark">@color/colorPrimaryDark</item>
        <item name="colorAccent">@color/colorAccent</item>
</#if>
    </style>

</resources>
