package ${packageName};

import android.os.Bundle;
<#if includeCppSupport!false>
import android.widget.TextView;
</#if>
<#if hasAppBar>
import ${getMaterialComponentName('android.support.design.widget.Snackbar', useMaterial2)};
<#if navigationType == "Navigation Drawer">
import ${getMaterialComponentName('android.support.v7.app', useAndroidX)}.ActionBarDrawerToggle;
</#if>
import ${getMaterialComponentName('android.support.v7.app.AppCompatActivity', useAndroidX)};
import ${getMaterialComponentName('android.support.v7.widget', useAndroidX)}.Toolbar;
import android.view.View;
<#else>
import ${superClassFqcn};
</#if>
import ${getMaterialComponentName('android.databinding', useAndroidX)}.DataBindingUtil;
<#if isNewProject>
import android.view.Menu;
import android.view.MenuItem;
</#if>
<#if applicationPackage??>
import ${applicationPackage}.R;
</#if>
<#if navigationType == "Navigation Drawer" || navigationType == "Bottom Navigation">
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
</#if>
import ${packageName}.databinding.${underscoreToCamelCase(layoutName)}Binding;

public class ${activityClass} extends ${superClass} {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ${underscoreToCamelCase(layoutName)}Binding binding =
                DataBindingUtil.setContentView(this, R.layout.${layoutName});

<#if navigationType == "Navigation Drawer">
        <#--  When navigationType is navigation drawer, the layout becomes one level deeper.  -->
        Toolbar toolbar = binding.appbar.toolbar;
        View fab = binding.appbar.fab;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
            this, binding.drawerLayout, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        NavigationUI.setupWithNavController(
            binding.navView, Navigation.findNavController(this, R.id.nav_host_fragment)
        );
<#elseif navigationType == "Bottom Navigation">
        Toolbar toolbar = binding.toolbar;
        NavigationUI.setupWithNavController(
            binding.contentMain.navView, Navigation.findNavController(this, R.id.nav_host_fragment)
        );
<#else>
        Toolbar toolbar = binding.toolbar;
        View fab = binding.fab;
</#if>
<#if hasAppBar>
<#if navigationType != "Navigation Drawer">
        setSupportActionBar(toolbar);
</#if>
<#if navigationType != "Bottom Navigation" >
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
</#if> <#-- navigationType != "Bottom Navigation" -->
</#if> <#-- hasAppBar -->
<#if parentActivityClass != "">
        get${Support}ActionBar().setDisplayHomeAsUpEnabled(true);
</#if>
<#include "../../../../common/jni_code_usage.java.ftl">
    }

<#if isNewProject>
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.${menuName}, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
</#if>
<#include "../../../../common/jni_code_snippet.java.ftl">
}
