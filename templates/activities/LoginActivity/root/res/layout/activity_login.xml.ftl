<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center_horizontal"
    android:orientation="vertical"
    android:paddingBottom="@dimen/activity_vertical_margin"
    android:paddingLeft="@dimen/activity_horizontal_margin"
    android:paddingRight="@dimen/activity_horizontal_margin"
    android:paddingTop="@dimen/activity_vertical_margin"
    tools:context="${packageName}.${activityClass}">

    <!-- Login progress -->
    <ProgressBar
        android:id="@+id/login_progress"
        style="?android:attr/progressBarStyleLarge"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="8dp"
        android:visibility="gone"/>

    <ScrollView
        android:id="@+id/login_form"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <LinearLayout
            android:id="@+id/email_login_form"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

<#if (buildApi gte 22) && appCompat>
            <${getMaterialComponentName('android.support.design.widget.TextInputLayout', useMaterial2)}
                android:layout_width="match_parent"
                android:layout_height="wrap_content">

</#if>
            <AutoCompleteTextView
                android:id="@+id/email"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/prompt_email"
                android:inputType="textEmailAddress"
                android:maxLines="1"
                android:singleLine="true"/>

<#if (buildApi gte 22) && appCompat>
            </${getMaterialComponentName('android.support.design.widget.TextInputLayout', useMaterial2)}>

            <${getMaterialComponentName('android.support.design.widget.TextInputLayout', useMaterial2)}
                android:layout_width="match_parent"
                android:layout_height="wrap_content">

</#if>
            <EditText
                android:id="@+id/password"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="@string/prompt_password"
                android:imeActionId="6"
                android:imeActionLabel="@string/action_sign_in_short"
                android:imeOptions="actionUnspecified"
                android:inputType="textPassword"
                android:maxLines="1"
                android:singleLine="true"/>

<#if (buildApi gte 22) && appCompat>
            </${getMaterialComponentName('android.support.design.widget.TextInputLayout', useMaterial2)}>

</#if>
            <Button
                android:id="@+id/email_sign_in_button"
                style="?android:textAppearanceSmall"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="16dp"
                android:text="@string/action_sign_in"
                android:textStyle="bold"/>

        </LinearLayout>
    </ScrollView>
</LinearLayout>
