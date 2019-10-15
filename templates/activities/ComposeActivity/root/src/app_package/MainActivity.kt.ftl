package ${escapeKotlinIdentifiers(packageName)}

import ${getMaterialComponentName('android.support.v7.app.AppCompatActivity', useAndroidX)};
import android.os.Bundle
import androidx.compose.composer // BUG! Needs this, otherwise fails to compile
import androidx.ui.core.setContent

class ${activityClass} : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ${layoutClass}() }
    }
}
