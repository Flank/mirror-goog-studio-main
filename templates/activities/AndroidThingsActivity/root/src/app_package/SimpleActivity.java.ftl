package ${packageName};

import ${superClassFqcn};
import android.os.Bundle;
<#if includeCppSupport!false>
import android.widget.TextView;
</#if>

/**
 * Skeleton of an Android Things activity.
 *
 * Android Things peripheral APIs are accessible through the PeripheralManager
 * For example, the snippet below will open a GPIO pin and set it to HIGH:
 *
 * PeripheralManager manager = PeripheralManager.getInstance();
 * try {
 *     Gpio gpio = manager.openGpio("BCM6");
 *     gpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 *     gpio.setValue(true);
 * } catch (IOException e) {
 *     Log.e(TAG, "Unable to access GPIO");
 * }
 *
 * You can find additional examples on GitHub: https://github.com/androidthings
 */
public class ${activityClass} extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
<#if generateLayout>
        setContentView(R.layout.${layoutName});
</#if>
<#include "../../../../common/jni_code_usage.java.ftl">
    }
<#include "../../../../common/jni_code_snippet.java.ftl">
}
