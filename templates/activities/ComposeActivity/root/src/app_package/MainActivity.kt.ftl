package ${escapeKotlinIdentifiers(packageName)}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.ui.core.Text
import androidx.ui.core.setContent
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview

class ${activityClass} : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ${greeting}("Android")
            }
        }
    }
}

@Composable
fun ${greeting}(name: String) {
    Text(text = "Hello $name!")
}

@Preview
@Composable
fun ${defaultPreview}() {
    MaterialTheme {
        ${greeting}("Android")
    }
}
