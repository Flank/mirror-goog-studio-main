package ${packageName};

import android.os.Bundle;
<#if includeCppSupport!false>
import android.widget.TextView;
</#if>
<#if hasAppBar>
import ${getMaterialComponentName('android.support.design.widget.FloatingActionButton', useMaterial2)};
import ${getMaterialComponentName('android.support.design.widget.Snackbar', useMaterial2)};
import ${getMaterialComponentName('android.support.v7.app.AppCompatActivity', useAndroidX)};
import ${getMaterialComponentName('android.support.v7.widget.Toolbar', useAndroidX)};
import android.view.View;
<#else>
import ${superClassFqcn};
</#if>
<#if isNewProject>
import android.view.Menu;
import android.view.MenuItem;
</#if>
<#if applicationPackage??>
import ${applicationPackage}.R;
</#if>

public class ${activityClass} extends ${superClass} {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.${layoutName});
<#if hasAppBar>
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

       FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
</#if>
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
