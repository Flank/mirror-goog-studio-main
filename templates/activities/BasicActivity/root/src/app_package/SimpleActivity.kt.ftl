<#import "root://activities/common/kotlin_macros.ftl" as kt>
package ${packageName}

import android.os.Bundle
<#if includeCppSupport!false>
import android.widget.TextView
</#if>
<#if hasAppBar>
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
<#else>
import ${superClassFqcn}
</#if>
<#if isNewProject>
import android.view.Menu
import android.view.MenuItem
</#if>
<#if applicationPackage??>
import ${applicationPackage}.R
</#if>

class ${activityClass} : ${superClass}() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${layoutName})
<#if hasAppBar>
        val toolbar = <@kt.findViewById id="R.id.toolbar" type="Toolbar"/>
        setSupportActionBar(toolbar)

        val fab = <@kt.findViewById id="R.id.fab" type="FloatingActionButton"/>
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
</#if>
<#if parentActivityClass?has_content>
        ${kotlinActionBar}!!.setDisplayHomeAsUpEnabled(true)
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
        val id = item.itemId

        if (id == R.id.action_settings) {
            return true
        }

        return super.onOptionsItemSelected(item)
    }
</#if>
<#include "../../../../common/jni_code_snippet.java.ftl">
}
