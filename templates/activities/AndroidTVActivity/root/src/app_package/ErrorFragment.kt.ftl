package ${escapeKotlinIdentifiers(packageName)}

import android.os.Bundle
import ${getMaterialComponentName('android.support.v4.content.ContextCompat', useAndroidX)}
import android.view.View

/**
 * This class demonstrates how to extend [${getMaterialComponentName('android.support.v17.leanback.app.ErrorFragment', useAndroidX)}].
 */
class ErrorFragment : ${getMaterialComponentName('android.support.v17.leanback.app.ErrorFragment', useAndroidX)}() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = resources.getString(R.string.app_name)
    }

    internal fun setErrorContent() {
        imageDrawable = ContextCompat.getDrawable(<#if minApiLevel lt 23>activity<#else>context</#if>, R.drawable.lb_ic_sad_cloud)
        message = resources.getString(R.string.error_fragment_message)
        setDefaultBackground(TRANSLUCENT)

        buttonText = resources.getString(R.string.dismiss_error)
        buttonClickListener = View.OnClickListener {
            fragmentManager.beginTransaction().remove(this@ErrorFragment).commit()
        }
    }

    companion object {
        private val TRANSLUCENT = true
    }
}
