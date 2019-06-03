package ${escapeKotlinIdentifiers(packageName)}

import ${getMaterialComponentName('android.support.test.runner.AndroidJUnit4', useAndroidX)}
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
    }
}
