/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks

import com.android.tools.lint.checks.ObjectAnimatorDetector.Companion.BROKEN_PROPERTY
import com.android.tools.lint.checks.ObjectAnimatorDetector.Companion.MISSING_KEEP
import com.android.tools.lint.detector.api.Detector

class ObjectAnimatorDetectorTest : AbstractCheckTest() {
    private val keepAnnotation = java(
        """
        package android.support.annotation;
        import java.lang.annotation.Retention;
        import java.lang.annotation.Target;
        import static java.lang.annotation.ElementType.*;
        import static java.lang.annotation.RetentionPolicy.CLASS;
        @Retention(CLASS)
        @Target({PACKAGE,TYPE,ANNOTATION_TYPE,CONSTRUCTOR,METHOD,FIELD})
        public @interface Keep {
        }
        """
    ).indented()

    override fun getDetector(): Detector {
        return ObjectAnimatorDetector()
    }

    fun testBasic() {
        val expected =
            """
            src/main/java/test/pkg/AnimatorTest.java:21: Error: The setter for this property does not match the expected signature (public void setProp2(int arg) [ObjectAnimatorBinding]
                    ObjectAnimator.ofInt(myObject, "prop2", 0, 1, 2, 5).start();
                                                   ~~~~~~~
                src/main/java/test/pkg/AnimatorTest.java:58: Property setter here
            src/main/java/test/pkg/AnimatorTest.java:24: Error: Could not find property setter method setUnknown on test.pkg.AnimatorTest.MyObject [ObjectAnimatorBinding]
                    ObjectAnimator.ofInt(myObject, "unknown", 0, 1, 2, 5).start();
                                                   ~~~~~~~~~
            src/main/java/test/pkg/AnimatorTest.java:27: Error: The setter for this property (test.pkg.AnimatorTest.MyObject.setProp3) should not be static [ObjectAnimatorBinding]
                    ObjectAnimator.ofInt(myObject, "prop3", 0, 1, 2, 5).start();
                                                   ~~~~~~~
                src/main/java/test/pkg/AnimatorTest.java:61: Property setter here
            src/main/java/test/pkg/AnimatorTest.java:40: Error: Could not find property setter method setAlpha2 on android.widget.Button [ObjectAnimatorBinding]
                    ObjectAnimator.ofArgb(button, "alpha2", 1, 5); // Missing
                                                  ~~~~~~~~
            src/main/java/test/pkg/AnimatorTest.java:55: Warning: This method is accessed from an ObjectAnimator so it should be annotated with @Keep to ensure that it is not discarded or renamed in release builds [AnimatorKeep]
                    public void setProp1(int x) {
                                ~~~~~~~~~~~~~~~
                src/main/java/test/pkg/AnimatorTest.java:15: ObjectAnimator usage here
            src/main/java/test/pkg/AnimatorTest.java:58: Warning: This method is accessed from an ObjectAnimator so it should be annotated with @Keep to ensure that it is not discarded or renamed in release builds [AnimatorKeep]
                    private void setProp2(float x) {
                                 ~~~~~~~~~~~~~~~~~
                src/main/java/test/pkg/AnimatorTest.java:47: ObjectAnimator usage here
            4 errors, 2 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;


                import android.animation.ObjectAnimator;
                import android.animation.PropertyValuesHolder;
                import android.support.annotation.Keep;
                import android.view.View;
                import android.widget.Button;
                import android.animation.FloatEvaluator;
                @SuppressWarnings("unused")
                public class AnimatorTest {

                    public void testObjectAnimator(Button button) {
                        Object myObject = new MyObject();
                        ObjectAnimator animator1 = ObjectAnimator.ofInt(myObject, "prop1", 0, 1, 2, 5);
                        animator1.setDuration(10);
                        animator1.start();


                        // Incorrect type (float parameter) warning
                        ObjectAnimator.ofInt(myObject, "prop2", 0, 1, 2, 5).start();

                        // Missing method warning
                        ObjectAnimator.ofInt(myObject, "unknown", 0, 1, 2, 5).start();

                        // Static method warning
                        ObjectAnimator.ofInt(myObject, "prop3", 0, 1, 2, 5).start();

                        // OK: Already marked @Keep
                        ObjectAnimator.ofInt(myObject, "prop4", 0, 1, 2, 5).start();

                        // OK: multi int
                        ObjectAnimator.ofMultiInt(myObject, "prop4", new int[0][]).start();

                        // OK: multi int
                        ObjectAnimator.ofMultiFloat(myObject, "prop5", new float[0][]).start();

                        // View stuff
                        ObjectAnimator.ofFloat(button, "alpha", 1, 5); // TODO: Warn about better method?, e.g. button.animate().alpha(...)
                        ObjectAnimator.ofArgb(button, "alpha2", 1, 5); // Missing
                    }

                    public void testPropertyHolder() {
                        Object myObject = new MyObject();

                        PropertyValuesHolder p1 = PropertyValuesHolder.ofInt("prop1", 50);
                        PropertyValuesHolder p2 = PropertyValuesHolder.ofFloat("prop2", 100f);
                        ObjectAnimator.ofPropertyValuesHolder(myObject, p1, p2).start();
                        ObjectAnimator.ofPropertyValuesHolder(myObject,
                                PropertyValuesHolder.ofInt("prop1", 50),
                                PropertyValuesHolder.ofFloat("prop2", 100f)).start();
                    }

                    private static class MyObject {
                        public void setProp1(int x) {
                        }

                        private void setProp2(float x) {
                        }

                        public static void setProp3(int x) {
                        }

                        @Keep
                        public void setProp4(int[] x) {
                        }

                        @Keep
                        public void setProp5(float[] x) {
                        }

                        @Keep
                        public void setProp4(int x) {
                        }

                        @Keep
                        public void setProp5(float x) {
                        }
                    }

                    public void testEvaluators() {
                        Object myObject = new MyObject();
                        PropertyValuesHolder p1 = PropertyValuesHolder.ofObject("prop5", new FloatEvaluator());
                        ObjectAnimator.ofPropertyValuesHolder(myObject, p1);
                        ObjectAnimator.ofObject(myObject, "prop5", new FloatEvaluator(), 1f, 2f);
                    }

                }
                """
            ).indented(),
            keepAnnotation,
            gradle(
                """
                android {
                    buildTypes {
                        release {
                            minifyEnabled true
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun testNotMinifying() {
        lint().files(
            java(
                """
                package test.pkg;


                import android.animation.ObjectAnimator;
                import android.animation.PropertyValuesHolder;
                import android.support.annotation.Keep;
                import android.view.View;
                import android.widget.Button;
                import android.animation.FloatEvaluator;
                @SuppressWarnings("unused")
                public class AnimatorTest {

                    public void testObjectAnimator(Button button) {
                        Object myObject = new MyObject();
                        ObjectAnimator animator1 = ObjectAnimator.ofInt(myObject, "prop1", 0, 1, 2, 5);
                        animator1.setDuration(10);
                        animator1.start();
                    }

                    private static class MyObject {
                        public void setProp1(int x) {
                        }
                    }

                }
                """
            ).indented(),
            java(
                """
                    package android.support.annotation;
                    import java.lang.annotation.Retention;
                    import java.lang.annotation.Target;
                    import static java.lang.annotation.ElementType.*;
                    import static java.lang.annotation.RetentionPolicy.CLASS;
                    @Retention(CLASS)
                    @Target({PACKAGE,TYPE,ANNOTATION_TYPE,CONSTRUCTOR,METHOD,FIELD})
                    public @interface Keep {
                    }
                    """
            ).indented(),
            gradle(
                """
                android {
                    buildTypes {
                        release {
                            minifyEnabled false
                        }
                    }
                }
                """
            ).indented()
        ).issues(MISSING_KEEP).run().expectClean()
    }

    fun testFlow() {
        val expected =
            """
            src/test/pkg/AnimatorFlowTest.java:10: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]
                    PropertyValuesHolder p1 = PropertyValuesHolder.ofInt("prop1", 50); // ERROR
                                                                         ~~~~~~~
                src/test/pkg/AnimatorFlowTest.java:37: Property setter here
            src/test/pkg/AnimatorFlowTest.java:14: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]
                private PropertyValuesHolder field = PropertyValuesHolder.ofInt("prop1", 50); // ERROR
                                                                                ~~~~~~~
                src/test/pkg/AnimatorFlowTest.java:37: Property setter here
            src/test/pkg/AnimatorFlowTest.java:21: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]
                    p1 = PropertyValuesHolder.ofInt("prop1", 50); // ERROR
                                                    ~~~~~~~
                src/test/pkg/AnimatorFlowTest.java:37: Property setter here
            src/test/pkg/AnimatorFlowTest.java:26: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]
                    PropertyValuesHolder p1 = PropertyValuesHolder.ofInt("prop1", 50); // ERROR
                                                                         ~~~~~~~
                src/test/pkg/AnimatorFlowTest.java:37: Property setter here
            src/test/pkg/AnimatorFlowTest.java:33: Error: The setter for this property does not match the expected signature (public void setProp1(int arg) [ObjectAnimatorBinding]
                    ObjectAnimator.ofPropertyValuesHolder(new MyObject(), PropertyValuesHolder.ofInt("prop1", 50)).start(); // ERROR
                                                                                                     ~~~~~~~
                src/test/pkg/AnimatorFlowTest.java:37: Property setter here
            5 errors, 0 warnings
            """
        lint().files(
            java(
                """
                package test.pkg;

                import android.animation.ObjectAnimator;
                import android.animation.PropertyValuesHolder;

                @SuppressWarnings("unused")
                public class AnimatorFlowTest {

                    public void testVariableInitializer() {
                        PropertyValuesHolder p1 = PropertyValuesHolder.ofInt("prop1", 50); // ERROR
                        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), p1).start();
                    }

                    private PropertyValuesHolder field = PropertyValuesHolder.ofInt("prop1", 50); // ERROR
                    public void testFieldInitializer() {
                        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), field).start();
                    }

                    public void testAssignment() {
                        PropertyValuesHolder p1;
                        p1 = PropertyValuesHolder.ofInt("prop1", 50); // ERROR
                        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), p1).start();
                    }

                    public void testReassignment() {
                        PropertyValuesHolder p1 = PropertyValuesHolder.ofInt("prop1", 50); // ERROR
                        PropertyValuesHolder p2 = p1;
                        p1 = null;
                        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), p2).start();
                    }

                    public void testInline() {
                        ObjectAnimator.ofPropertyValuesHolder(new MyObject(), PropertyValuesHolder.ofInt("prop1", 50)).start(); // ERROR
                    }

                    private static class MyObject {
                        public void setProp1(double z) { // ERROR
                        }
                    }
                }
                """
            ).indented()
        ).run().expect(expected)
    }

    fun test229545() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=229545
        lint().files(
            java(
                """
                package com.example.objectanimatorbinding;

                import android.animation.ArgbEvaluator;
                import android.animation.ObjectAnimator;
                import android.databinding.DataBindingUtil;
                import android.graphics.Color;
                import android.support.v7.app.AppCompatActivity;
                import android.os.Bundle;
                import android.support.v7.widget.CardView;
                import android.view.View;

                import com.example.objectanimatorbinding.databinding.ActivityMainBinding;

                public class MainActivity extends AppCompatActivity {

                    private ActivityMainBinding binding;

                    boolean isChecked = false;

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
                        binding.activityMain.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                animateColorChange(binding.activityMain, isChecked);
                                isChecked = !isChecked;
                            }
                        });
                    }

                    private void animateColorChange (CardView view, boolean isChecked){
                        ObjectAnimator backgroundColorAnimator = ObjectAnimator.ofObject(view,
                                "cardBackgroundColor",
                                new ArgbEvaluator(),
                                isChecked ? Color.BLUE : Color.GRAY,
                                isChecked ? Color.GRAY : Color.BLUE);
                        backgroundColorAnimator.setDuration(200);
                        backgroundColorAnimator.start();
                    }
                }
                """
            ).indented(),
            gradle(
                """
                android {
                    buildTypes {
                        release {
                            minifyEnabled true
                        }
                    }
                }
                """
            ).indented()
        ).issues(MISSING_KEEP).allowCompilationErrors().run().expectClean()
    }

    fun test230387() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=230387
        lint().files(
            java(
                """
                package test.pkg;

                import android.animation.ObjectAnimator;
                import android.animation.PropertyValuesHolder;
                import android.animation.ValueAnimator;
                import android.view.View;

                public class KeepTest {
                    public void test(View view) {
                        ValueAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
                                view,
                                PropertyValuesHolder.ofFloat("translationX", 0)
                        );
                    }
                }
                """
            ).indented(),
            gradle(
                """
                android {
                    buildTypes {
                        release {
                            minifyEnabled true
                        }
                    }
                }
                """
            ).indented()
        ).issues(MISSING_KEEP).run().expectClean()
    }

    fun testCreateValueAnimator() {
        // Regression test which makes sure that when we use ValueAnimator.ofPropertyValuesHolder
        // to create a property holder and we don't know the associated object, we don't falsely
        // report broken properties
        lint().files(
            java(
                """
                package test.pkg;

                import android.animation.PropertyValuesHolder;
                import android.animation.ValueAnimator;

                public class MyAndroidLibraryClass {

                    ValueAnimator create(float fromX, float toX, float fromY, float toY) {
                        final PropertyValuesHolder xHolder = PropertyValuesHolder.ofFloat("x", fromX, toX);
                        final PropertyValuesHolder yHolder = PropertyValuesHolder.ofFloat("y", fromY, toY);
                        return ValueAnimator.ofPropertyValuesHolder(xHolder, yHolder);
                    }
                }
                """
            ).indented()
        ).issues(BROKEN_PROPERTY).run().expectClean()
    }

    fun testSuppress() {
        // Regression test for https://code.google.com/p/android/issues/detail?id=232405
        // Ensure that we can suppress both types of issues by annotating either the
        // property binding site *or* the property declaration site
        lint().files(
            java(
                """
                package test.pkg;

                import android.animation.ObjectAnimator;
                import android.annotation.SuppressLint;
                import android.widget.Button;

                @SuppressWarnings("unused")
                public class AnimatorTest {

                    // Suppress at the binding site
                    @SuppressLint({"ObjectAnimatorBinding", "AnimatorKeep"})
                    public void testObjectAnimator02(Button button) {
                        Object myObject = new MyObject();

                        ObjectAnimator.ofInt(myObject, "prop0", 0, 1, 2, 5);
                        ObjectAnimator.ofInt(myObject, "prop2", 0, 1, 2, 5).start();
                    }

                    // Suppressed at the property site
                    public void testObjectAnimator13(Button button) {
                        Object myObject = new MyObject();

                        ObjectAnimator.ofInt(myObject, "prop1", 0, 1, 2, 5);
                        ObjectAnimator.ofInt(myObject, "prop3", 0, 1, 2, 5).start();
                    }

                    private static class MyObject {
                        public void setProp0(int x) {
                        }

                        @SuppressLint("AnimatorKeep")
                        public void setProp1(int x) {
                        }

                        private void setProp2(float x) {
                        }

                        @SuppressLint("ObjectAnimatorBinding")
                        private void setProp3(float x) {
                        }
                    }
                }
                """
            ).indented()
        ).issues(BROKEN_PROPERTY, MISSING_KEEP).run().expectClean()
    }

    fun test37136742() {
        // Regression test for 37136742
        lint().files(
            java(
                """
                package test.pkg;
                import android.animation.Keyframe;
                import android.animation.ObjectAnimator;
                import android.animation.PropertyValuesHolder;
                import android.app.Activity;
                import android.view.View;

                /** @noinspection ClassNameDiffersFromFileName*/   public class TestObjAnimator extends Activity {
                    public void animate(View target) {
                        Keyframe kf0 = Keyframe.ofFloat(0f, 0f);
                        Keyframe kf1 = Keyframe.ofFloat(.5f, 360f);
                        Keyframe kf2 = Keyframe.ofFloat(1f, 0f);
                        PropertyValuesHolder pvhRotation = PropertyValuesHolder.ofKeyframe("rotation", kf0, kf1, kf2);
                        ObjectAnimator rotationAnim = ObjectAnimator.ofPropertyValuesHolder(target, pvhRotation);
                        rotationAnim.setDuration(5000);
                    }
                }
                """
            ).indented()
        ).run().expectClean()
    }

    fun test137695423() {
        // Regression test for 137695423
        lint().files(
            java(
                """
                package test.pkg;

                import android.animation.ObjectAnimator;
                import android.support.annotation.Keep;

                @SuppressWarnings("unused")
                public class ObjAnimatorTest {
                    @SuppressWarnings("WeakerAccess")
                    private static class PlayheadPosition {
                        public float inPixel() {
                            return 0f;
                        }
                    }

                    private PlayheadPosition playheadPosition;

                    public void test(float targetPositionInPixel) {
                        Object myObject = new ObjAnimatorTest();
                        ObjectAnimator animaator = ObjectAnimator.ofFloat(
                                myObject,
                                "playheadPositionInPixelForAnimation",
                                playheadPosition.inPixel(),
                                targetPositionInPixel);

                    }

                    @Keep
                    public void setPlayheadPositionInPixelForAnimation(float arg) {
                    }
                }
                """
            ).indented(),
            keepAnnotation
        ).run().expectClean()
    }

    fun testMotionLayoutKeep() {
        lint().files(
            xml(
                "src/main/res/layout/mylayout.xml",
                """
                <android.support.constraint.motion.MotionLayout
                    xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto"
                    xmlns:tools="http://schemas.android.com/tools"
                    android:id="@+id/details_motion"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    app:layoutDescription="@xml/scene_show_details">

                    <test.pkg.TintingToolbarJava
                        android:id="@+id/details_toolbar"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:theme="@style/ThemeOverlay.AppCompat.ActionBar"
                        app:navigationIcon="?attr/homeAsUpIndicator" />

                    <test.pkg.TintingToolbarKotlin
                        android:id="@+id/details_toolbar"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:theme="@style/ThemeOverlay.AppCompat.ActionBar"
                        app:navigationIcon="?attr/homeAsUpIndicator" />

                    <View
                        android:id="@+id/details_status_bar_anchor"
                        android:layout_width="match_parent"
                        android:layout_height="24dp"
                        android:background="@color/status_bar_scrim_translucent_dark" />

                </android.support.constraint.motion.MotionLayout>
            """
            ).indented(),
            xml(
                "src/main/res/xml/scene_show_details.xml",
                """
                <MotionScene xmlns:android="http://schemas.android.com/apk/res/android"
                    xmlns:app="http://schemas.android.com/apk/res-auto">

                    <!-- Not a valid MotionScene file; just a snippet included here; lint
                    doesn't care about the whole structure -->

                    <Constraint
                        android:id="@id/details_backdrop_scrim"
                        app:layout_constraintBottom_toBottomOf="@id/details_backdrop"
                        app:layout_constraintEnd_toEndOf="@id/details_backdrop"
                        app:layout_constraintStart_toStartOf="@id/details_backdrop"
                        app:layout_constraintTop_toTopOf="@id/details_backdrop">

                        <CustomAttribute
                            app:attributeName="background"
                            app:customColorDrawableValue="@android:color/transparent" />

                        <CustomAttribute
                            app:attributeName="background"
                            app:customColorDrawableValue="@android:color/transparent" />

                    </Constraint>

                    <Constraint
                        android:id="@id/details_toolbar"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:elevation="0dp"
                        app:layout_constraintTop_toBottomOf="@id/details_status_bar_anchor">

                        <CustomAttribute
                            app:attributeName="iconTint1"
                            app:customColorValue="?android:attr/textColorPrimaryInverse" />

                        <CustomAttribute
                            app:attributeName="iconTint2"
                            app:customColorValue="?android:attr/textColorPrimaryInverse" />

                        <CustomAttribute
                            app:attributeName="iconTint3"
                            app:customColorValue="?android:attr/textColorPrimaryInverse" />

                        <CustomAttribute
                            app:attributeName="iconTint4"
                            app:customColorValue="?android:attr/textColorPrimaryInverse" />
                    </Constraint>

                </MotionScene>
            """
            ).indented(),
            java(
                """
                package test.pkg;
                import android.support.annotation.Keep;
                import android.animation.Keyframe;
                import android.animation.ObjectAnimator;
                import android.animation.PropertyValuesHolder;
                import android.app.Activity;
                import android.view.View;

                /** @noinspection ClassNameDiffersFromFileName, MethodMayBeStatic */
                public class TintingToolbarJava extends LinearLayout {
                    public TintingToolbarJava(Context context) { super(context); }
                    public int getIconTint1() { return 0; } // ERROR
                    public void setIconTint1(int value) { } // ERROR
                    @Keep
                    public int getIconTint2() { return 0; } // OK
                    @Keep
                    public void setIconTint2(int value) { } // OK
                }
                """
            ).indented(),
            kotlin(
                """
                package test.pkg

                import android.content.Context
                import android.support.annotation.Keep
                import android.util.AttributeSet
                import android.widget.LinearLayout

                class TintingToolbarKotlin @JvmOverloads constructor(
                    context: Context,
                    attrs: AttributeSet? = null,
                    defStyleAttr: Int = 0
                ) : LinearLayout(context, attrs, defStyleAttr) {
                    var iconTint3: Int = 0 // ERROR
                    @get:Keep
                    @set:Keep
                    var iconTint4: Int = 0 // OK
                }
                """
            ).indented(),
            gradle(
                """
                android {
                    buildTypes {
                        release {
                            minifyEnabled true
                        }
                    }
                }
                """
            ).indented(),
            keepAnnotation
        ).issues(MISSING_KEEP).run().expect(
            """
            src/main/res/xml/scene_show_details.xml:32: Warning: This attribute references a method or property in custom view test.pkg.TintingToolbarJava which is not annotated with @Keep; it should be annotated with @Keep to ensure that it is not discarded or renamed in release builds [AnimatorKeep]
                        app:attributeName="iconTint1"
                                           ~~~~~~~~~
                src/main/java/test/pkg/TintingToolbarJava.java:13: This method is accessed via reflection from a MotionScene (scene_show_details) so it should be annotated with @Keep to ensure that it is not discarded or renamed in release builds
            src/main/res/xml/scene_show_details.xml:40: Warning: This attribute references a method or property in custom view test.pkg.TintingToolbarKotlin which is not annotated with @Keep; it should be annotated with @Keep to ensure that it is not discarded or renamed in release builds [AnimatorKeep]
                        app:attributeName="iconTint3"
                                           ~~~~~~~~~
                src/main/kotlin/test/pkg/TintingToolbarKotlin.kt:13: This method is accessed via reflection from a MotionScene (scene_show_details) so it should be annotated with @Keep to ensure that it is not discarded or renamed in release builds
            0 errors, 2 warnings
            """
        )
    }
}
