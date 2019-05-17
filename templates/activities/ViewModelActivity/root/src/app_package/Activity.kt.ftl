package ${escapeKotlinIdentifiers(packageName)}

import ${superClassFqcn}
import android.os.Bundle
import ${escapeKotlinIdentifiers(packageName)}.${escapeKotlinIdentifiers(fragmentPackage)}.${fragmentClass}

class ${activityClass} : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${activityLayout})
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ${fragmentClass}.newInstance())
                .commitNow()
        }
    }
}
