package ${escapeKotlinIdentifiers(packageName)}

import android.app.Activity
import android.os.Bundle

/**
 * Loads [${mainFragment}].
 */
class ${activityClass} : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${layoutName})
    }
}
