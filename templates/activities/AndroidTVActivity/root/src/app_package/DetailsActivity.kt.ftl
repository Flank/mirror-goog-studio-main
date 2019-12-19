package ${escapeKotlinIdentifiers(packageName)}

import android.app.Activity
import android.os.Bundle

/**
 * Details activity class that loads [VideoDetailsFragment] class.
 */
class ${detailsActivity} : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.${detailsLayoutName})
    }

    companion object {
        const val SHARED_ELEMENT_NAME = "hero"
        const val MOVIE = "Movie"
    }
}
