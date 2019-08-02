package ${escapeKotlinIdentifiers(packageName)}

import android.os.Bundle
import ${getMaterialComponentName('android.support.design.widget.Snackbar', useMaterial2)}
import ${getMaterialComponentName('android.support.v7.app.AppCompatActivity', useAndroidX)}
<#if isNewModule>
import android.view.Menu
import android.view.MenuItem
</#if>
<#if applicationPackage??>
import ${applicationPackage}.R
</#if>

import kotlinx.android.synthetic.main.${layoutName}.*

class ${activityClass} : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${layoutName})
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }
    }

<#if isNewModule>
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
}
