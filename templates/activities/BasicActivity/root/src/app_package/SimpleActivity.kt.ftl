package ${escapeKotlinIdentifiers(packageName)}

import android.os.Bundle
<#if hasAppBar>
import ${getMaterialComponentName('android.support.design.widget.Snackbar', useMaterial2)}
<#if navigationType == "Navigation Drawer">
import ${getMaterialComponentName('android.support.v7.app', useAndroidX)}.ActionBarDrawerToggle
</#if>
import ${getMaterialComponentName('android.support.v7.app.AppCompatActivity', useAndroidX)}
<#else>
import ${superClassFqcn}
</#if>
import ${getMaterialComponentName('android.databinding', useAndroidX)}.DataBindingUtil
<#if isNewProject>
import android.view.Menu
import android.view.MenuItem
</#if>
<#if applicationPackage??>
import ${applicationPackage}.R
</#if>
<#--
Keeping it not to break the eixisting templates. Remove synthetic properties when databinding
is available in all templates
-->
import kotlinx.android.synthetic.main.${layoutName}.*
<#if includeCppSupport!false>
<#if useFragment!false>
import kotlinx.android.synthetic.main.${fragmentLayoutName}.*
<#else>
import kotlinx.android.synthetic.main.${simpleLayoutName}.*
</#if>
</#if>
<#if navigationType == "Navigation Drawer">
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
</#if>
import ${packageName}.databinding.${underscoreToCamelCase(layoutName)}Binding

class ${activityClass} : ${superClass}() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ${underscoreToCamelCase(layoutName)}Binding =
                DataBindingUtil.setContentView(this, R.layout.${layoutName})
<#if navigationType == "Navigation Drawer">
        <#--  When navigationType is navigation drawer, the layout becomes one level deeper.  -->
        val toolbar = binding.appbar.toolbar
        val fab = binding.appbar.fab
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        ) 
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        NavigationUI.setupWithNavController(
            binding.navView, Navigation.findNavController(this, R.id.nav_host_fragment)
        )
<#else>
        val toolbar = binding.toolbar
        val fab = binding.fab
</#if>

<#if hasAppBar>
<#if navigationType != "Navigation Drawer">
        setSupportActionBar(toolbar)
</#if>
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
</#if>
<#if parentActivityClass?has_content>
        ${kotlinActionBar}?.setDisplayHomeAsUpEnabled(true)
</#if>
<#include "../../../../common/jni_code_usage.kt.ftl">
    }

<#if isNewProject>
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.${menuName}, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when(item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
</#if>
<#include "../../../../common/jni_code_snippet.kt.ftl">
}
