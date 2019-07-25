<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:layout_marginLeft="16dp"
    android:layout_marginRight="16dp"
    android:baselineAligned="false"
    android:divider="?android:attr/dividerHorizontal"
    android:orientation="horizontal"
    android:showDividers="middle"
    tools:context="${packageName}.${CollectionName}Activity">

    <!--
    This layout is a two-pane layout for the ${objectKindPlural} master/detail flow.
    -->

    <${getMaterialComponentName('android.support.v7.widget.RecyclerView', useAndroidX)} xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        android:id="@+id/${collection_name}"
        android:name="${packageName}.${CollectionName}Fragment"
        android:layout_width="@dimen/item_width"
        android:layout_height="match_parent"
        android:layout_marginLeft="16dp"
        android:layout_marginRight="16dp"
        app:layoutManager="LinearLayoutManager"
        tools:context="${packageName}.${CollectionName}Activity"
        tools:listitem="@layout/${item_list_content_layout}" />

    <FrameLayout
        android:id="@+id/${detail_name}_container"
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="3" />

</LinearLayout>
