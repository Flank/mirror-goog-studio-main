package ${escapeKotlinIdentifiers(packageName)}

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class ${fragmentClass} : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}
