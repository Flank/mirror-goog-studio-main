package ${escapeKotlinIdentifiers(packageName)}

import ${superClassFqcn}
import android.os.Bundle

class ${activityClass} : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
<#if generateLayout>
        setContentView(R.layout.${layoutName})
</#if>
    }
}
