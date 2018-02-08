package com.android.tests.basic;

import java.lang.reflect.Method;
import junit.framework.TestCase;

public class StringProviderTest extends TestCase {

    public void testNonObfuscatedMethod() {
        // this should not be obfuscated
        String className = "com.android.tests.basic.StringProvider";
        String methodName = "getString";

        searchMethod(className, methodName, true);
    }

    public void testObduscatedMethod() {
        // this should not be obfuscated, main sources are not obfuscated for library test APK
        String className = "com.android.tests.basic.StringProvider";
        String methodName = "getStringInternal";

        searchMethod(className, methodName, true);
    }

    private void searchMethod(String className, String methodName, boolean shouldExist) {
        try {
            Class<?> theClass = Class.forName(className);
            Method method = theClass.getDeclaredMethod(methodName, int.class);
            if (!shouldExist) {
                fail("Found " + className + "." + methodName);
            }
        } catch (ClassNotFoundException e) {
            fail("Failed to find com.android.tests.basic.StringGetter");
        } catch (NoSuchMethodException e) {
            if (shouldExist) {
                fail("Did not find " + className + "." + methodName);
            }
        }
    }

}

