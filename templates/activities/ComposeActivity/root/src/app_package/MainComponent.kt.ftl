package ${escapeKotlinIdentifiers(packageName)}

import androidx.compose.Composable
import androidx.compose.composer // Hack for plugin exception b/132509394
import androidx.ui.core.Text

@Composable
fun ${layoutClass}() {
    Text(text="Hello World!")
}
