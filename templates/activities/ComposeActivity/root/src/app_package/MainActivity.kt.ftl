package ${escapeKotlinIdentifiers(packageName)}

import android.support.v7.app.AppCompatActivity
import android.os.Bundle

class ${activityClass} : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(${layoutClass}.createInstance(this))
    }
}
