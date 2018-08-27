/*
 * Copyright (C) 2016 - 2018 The Android Open Source Project
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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.infrastructure.ProjectDescription;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;

public class ConstraintLayoutDetectorTest extends AbstractCheckTest {

    private static final String NO_WARNING = "No warnings.";
    public void testMissingConstraints() {
        String expected =
                ""
                        + "res/layout/layout1.xml:13: Error: This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints [MissingConstraints]\n"
                        + "    <TextView\n"
                        + "     ~~~~~~~~\n"
                        + "res/layout/layout1.xml:19: Error: This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints [MissingConstraints]\n"
                        + "    <TextView\n"
                        + "     ~~~~~~~~\n"
                        + "res/layout/layout1.xml:43: Error: This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint [MissingConstraints]\n"
                        + "    <TextView\n"
                        + "     ~~~~~~~~\n"
                        + "res/layout/layout1.xml:53: Error: This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint [MissingConstraints]\n"
                        + "    <TextView\n"
                        + "     ~~~~~~~~\n"
                        + "4 errors, 0 warnings";

        ProjectDescription project =
                project()
                        .files(
                                xml(
                                        "res/layout/layout1.xml",
                                        ""
                                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                                + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                                                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                                + "    android:id=\"@+id/activity_main\"\n"
                                                + "    android:layout_width=\"match_parent\"\n"
                                                + "    android:layout_height=\"match_parent\"\n"
                                                + "    tools:layout_editor_absoluteX=\"0dp\"\n"
                                                + "    tools:layout_editor_absoluteY=\"81dp\"\n"
                                                + "    tools:context=\"com.example.tnorbye.myapplication.MainActivity\"\n"
                                                + "    tools:ignore=\"HardcodedText\">\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/textView\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:text=\"Not constrained and no designtime positions\" />\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/textView2\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:text=\"Not constrained\"\n"
                                                + "        tools:layout_editor_absoluteX=\"21dp\"\n"
                                                + "        tools:layout_editor_absoluteY=\"23dp\" />\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/textView3\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:text=\"Constrained both\"\n"
                                                + "        app:layout_constraintBottom_creator=\"2\"\n"
                                                + "        app:layout_constraintBottom_toBottomOf=\"@+id/activity_main\"\n"
                                                + "        app:layout_constraintLeft_creator=\"2\"\n"
                                                + "        app:layout_constraintLeft_toLeftOf=\"@+id/activity_main\"\n"
                                                + "        app:layout_constraintRight_creator=\"2\"\n"
                                                + "        app:layout_constraintRight_toRightOf=\"@+id/activity_main\"\n"
                                                + "        app:layout_constraintTop_creator=\"2\"\n"
                                                + "        app:layout_constraintTop_toTopOf=\"@+id/activity_main\"\n"
                                                + "        tools:layout_editor_absoluteX=\"139dp\"\n"
                                                + "        tools:layout_editor_absoluteY=\"247dp\" />\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/textView4\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:text=\"Constrained Horizontally\"\n"
                                                + "        app:layout_constraintLeft_creator=\"0\"\n"
                                                + "        app:layout_constraintLeft_toLeftOf=\"@+id/textView3\"\n"
                                                + "        tools:layout_editor_absoluteX=\"139dp\"\n"
                                                + "        tools:layout_editor_absoluteY=\"270dp\" />\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/textView5\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:text=\"Constrained Vertically\"\n"
                                                + "        app:layout_constraintBaseline_creator=\"2\"\n"
                                                + "        app:layout_constraintBaseline_toBaselineOf=\"@+id/textView4\"\n"
                                                + "        tools:layout_editor_absoluteX=\"306dp\"\n"
                                                + "        tools:layout_editor_absoluteY=\"270dp\" />\n"
                                                + "\n"
                                                + "    <android.support.constraint.Guideline\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:id=\"@+id/android.support.constraint.Guideline\"\n"
                                                + "        app:orientation=\"vertical\"\n"
                                                + "        tools:layout_editor_absoluteX=\"20dp\"\n"
                                                + "        tools:layout_editor_absoluteY=\"0dp\"\n"
                                                + "        app:relativeBegin=\"20dp\" />\n"
                                                + "\n"
                                                + "</android.support.constraint.ConstraintLayout>\n"),
                                gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.support.constraint:constraint-layout:1.0.0-alpha3\"'\n"
                                                + "}"));

        lint().projects(project).checkMessage(this::checkReportedError).run().expect(expected);
    }

    public void testBarrierMissingConstraint() {
        String expected =
                "res/layout/layout1.xml:50: Error: This view is not constrained. It only has designtime positions, so it will jump to (0,0) at runtime unless you add the constraints [MissingConstraints]\n"
                        + "    <android.support.constraint.Barrier\n"
                        + "     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "1 errors, 0 warnings";

        ProjectDescription project =
                project()
                        .files(
                                xml(
                                        "res/layout/layout1.xml",
                                        ""
                                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                                + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                                                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                                + "    android:id=\"@+id/activity_main_barriers\"\n"
                                                + "    android:layout_width=\"match_parent\"\n"
                                                + "    android:layout_height=\"match_parent\"\n"
                                                + "    app:layout_editor_absoluteX=\"0dp\"\n"
                                                + "    app:layout_editor_absoluteY=\"80dp\"\n"
                                                + "    tools:layout_editor_absoluteX=\"0dp\"\n"
                                                + "    tools:layout_editor_absoluteY=\"80dp\">\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/settingsLabel\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:layout_marginBottom=\"254dp\"\n"
                                                + "        android:layout_marginTop=\"31dp\"\n"
                                                + "        android:labelFor=\"@+id/settings\"\n"
                                                + "        android:text=\"@string/settings\"\n"
                                                + "        app:layout_constraintBaseline_creator=\"1\"\n"
                                                + "        app:layout_constraintBottom_toBottomOf=\"parent\"\n"
                                                + "        app:layout_constraintLeft_creator=\"1\"\n"
                                                + "        app:layout_constraintLeft_toLeftOf=\"@+id/activity_main_barriers\"\n"
                                                + "        app:layout_constraintTop_toBottomOf=\"@+id/cameraLabel\"\n"
                                                + "        app:layout_constraintVertical_bias=\"0.65999997\"\n"
                                                + "        app:layout_editor_absoluteX=\"16dp\"\n"
                                                + "        app:layout_editor_absoluteY=\"238dp\"\n"
                                                + "        tools:layout_constraintBaseline_creator=\"0\"\n"
                                                + "        tools:layout_constraintLeft_creator=\"0\" />\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/cameraLabel\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:layout_marginBottom=\"31dp\"\n"
                                                + "        android:layout_marginTop=\"189dp\"\n"
                                                + "        android:labelFor=\"@+id/cameraType\"\n"
                                                + "        android:text=\"@string/camera\"\n"
                                                + "        app:layout_constraintBaseline_creator=\"1\"\n"
                                                + "        app:layout_constraintBottom_toTopOf=\"@+id/settingsLabel\"\n"
                                                + "        app:layout_constraintLeft_creator=\"1\"\n"
                                                + "        app:layout_constraintStart_toStartOf=\"parent\"\n"
                                                + "        app:layout_constraintTop_toTopOf=\"parent\"\n"
                                                + "        app:layout_editor_absoluteX=\"16dp\"\n"
                                                + "        app:layout_editor_absoluteY=\"189dp\"\n"
                                                + "        tools:layout_constraintBaseline_creator=\"0\"\n"
                                                + "        tools:layout_constraintLeft_creator=\"0\" />\n"
                                                + "\n"
                                                + "    <android.support.constraint.Barrier\n"
                                                + "        android:id=\"@+id/barrier2\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        app:constraint_referenced_ids=\"settingsLabel,cameraLabel\"\n"
                                                + "        tools:layout_editor_absoluteX=\"99dp\" />\n"
                                                + "\n"
                                                + "</android.support.constraint.ConstraintLayout>\n"),
                                gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.support.constraint:constraint-layout:1.0.0-alpha3\"'\n"
                                                + "}"));

        lint().projects(project).checkMessage(this::checkReportedError).run().expect(expected);
    }

    public void testBarrierHasConstraint() {
        ProjectDescription project =
                project()
                        .files(
                                xml(
                                        "res/layout/layout1.xml",
                                        ""
                                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                                                + "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n"
                                                + "    xmlns:tools=\"http://schemas.android.com/tools\"\n"
                                                + "    android:id=\"@+id/activity_main_barriers\"\n"
                                                + "    android:layout_width=\"match_parent\"\n"
                                                + "    android:layout_height=\"match_parent\"\n"
                                                + "    app:layout_editor_absoluteX=\"0dp\"\n"
                                                + "    app:layout_editor_absoluteY=\"80dp\"\n"
                                                + "    tools:layout_editor_absoluteX=\"0dp\"\n"
                                                + "    tools:layout_editor_absoluteY=\"80dp\">\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/settingsLabel\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:layout_marginBottom=\"254dp\"\n"
                                                + "        android:layout_marginTop=\"31dp\"\n"
                                                + "        android:labelFor=\"@+id/settings\"\n"
                                                + "        android:text=\"@string/settings\"\n"
                                                + "        app:layout_constraintBaseline_creator=\"1\"\n"
                                                + "        app:layout_constraintBottom_toBottomOf=\"parent\"\n"
                                                + "        app:layout_constraintLeft_creator=\"1\"\n"
                                                + "        app:layout_constraintLeft_toLeftOf=\"@+id/activity_main_barriers\"\n"
                                                + "        app:layout_constraintTop_toBottomOf=\"@+id/cameraLabel\"\n"
                                                + "        app:layout_constraintVertical_bias=\"0.65999997\"\n"
                                                + "        app:layout_editor_absoluteX=\"16dp\"\n"
                                                + "        app:layout_editor_absoluteY=\"238dp\"\n"
                                                + "        tools:layout_constraintBaseline_creator=\"0\"\n"
                                                + "        tools:layout_constraintLeft_creator=\"0\" />\n"
                                                + "\n"
                                                + "    <TextView\n"
                                                + "        android:id=\"@+id/cameraLabel\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        android:layout_marginBottom=\"31dp\"\n"
                                                + "        android:layout_marginTop=\"189dp\"\n"
                                                + "        android:labelFor=\"@+id/cameraType\"\n"
                                                + "        android:text=\"@string/camera\"\n"
                                                + "        app:layout_constraintBaseline_creator=\"1\"\n"
                                                + "        app:layout_constraintBottom_toTopOf=\"@+id/settingsLabel\"\n"
                                                + "        app:layout_constraintLeft_creator=\"1\"\n"
                                                + "        app:layout_constraintStart_toStartOf=\"parent\"\n"
                                                + "        app:layout_constraintTop_toTopOf=\"parent\"\n"
                                                + "        app:layout_editor_absoluteX=\"16dp\"\n"
                                                + "        app:layout_editor_absoluteY=\"189dp\"\n"
                                                + "        tools:layout_constraintBaseline_creator=\"0\"\n"
                                                + "        tools:layout_constraintLeft_creator=\"0\" />\n"
                                                + "\n"
                                                + "    <android.support.constraint.Barrier\n"
                                                + "        android:id=\"@+id/barrier2\"\n"
                                                + "        android:layout_width=\"wrap_content\"\n"
                                                + "        android:layout_height=\"wrap_content\"\n"
                                                + "        app:barrierDirection=\"end\"\n"
                                                + "        app:constraint_referenced_ids=\"settingsLabel,cameraLabel\"\n"
                                                + "        tools:layout_editor_absoluteX=\"99dp\" />\n"
                                                + "\n"
                                                + "</android.support.constraint.ConstraintLayout>\n"),
                                gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.support.constraint:constraint-layout:1.0.0-alpha3\"'\n"
                                                + "}"));

        lint().projects(project).checkMessage(this::checkReportedError).run().expect(NO_WARNING);
    }

    public void testWidthHeightMatchParent() {
        ProjectDescription project =
                project()
                        .files(
                                xml(
                                        "res/layout/layout1.xml",
                                        ""
                                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                                                + "   xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                                                + "   xmlns:tools=\"http://schemas.android.com/tools\""
                                                + "   android:layout_width=\"match_parent\""
                                                + "   android:layout_height=\"match_parent\">"
                                                + "\n"
                                                + "    <Button\n"
                                                + "        android:id=\"@+id/button\"\n"
                                                + "        android:layout_width=\"match_parent\"\n"
                                                + "        android:layout_height=\"match_parent\"\n /> "
                                                + "\n"
                                                + "</android.support.constraint.ConstraintLayout>\n"),
                                gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.support.constraint:constraint-layout:1.0.0-alpha3\"'\n"
                                                + "}"));

        lint().projects(project).checkMessage(this::checkReportedError).run().expect(NO_WARNING);
    }

    public void testWidthMatchParentOnlyError() {
        String expected =
                "res/layout/layout1.xml:3: Error: This view is not constrained vertically: at runtime it will jump to the top unless you add a vertical constraint [MissingConstraints]\n"
                        + "    <Button\n"
                        + "     ~~~~~~\n"
                        + "1 errors, 0 warnings";

        ProjectDescription project =
                project()
                        .files(
                                xml(
                                        "res/layout/layout1.xml",
                                        ""
                                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                                                + "   xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                                                + "   xmlns:tools=\"http://schemas.android.com/tools\""
                                                + "   android:layout_width=\"match_parent\""
                                                + "   android:layout_height=\"match_parent\">"
                                                + "\n"
                                                + "    <Button\n"
                                                + "        android:id=\"@+id/button\"\n"
                                                + "        android:layout_width=\"match_parent\"\n"
                                                + "        android:layout_height=\"100dp\"\n /> "
                                                + "\n"
                                                + "</android.support.constraint.ConstraintLayout>\n"),
                                gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.support.constraint:constraint-layout:1.0.0-alpha3\"'\n"
                                                + "}"));

        lint().projects(project).checkMessage(this::checkReportedError).run().expect(expected);
    }

    public void testHeightMatchParentOnlyError() {
        String expected =
                "res/layout/layout1.xml:3: Error: This view is not constrained horizontally: at runtime it will jump to the left unless you add a horizontal constraint [MissingConstraints]\n"
                        + "    <Button\n"
                        + "     ~~~~~~\n"
                        + "1 errors, 0 warnings";

        ProjectDescription project =
                project()
                        .files(
                                xml(
                                        "res/layout/layout1.xml",
                                        ""
                                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                                                + "   xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                                                + "   xmlns:tools=\"http://schemas.android.com/tools\""
                                                + "   android:layout_width=\"match_parent\""
                                                + "   android:layout_height=\"match_parent\">"
                                                + "\n"
                                                + "    <Button\n"
                                                + "        android:id=\"@+id/button\"\n"
                                                + "        android:layout_width=\"100dp\"\n"
                                                + "        android:layout_height=\"match_parent\"\n /> "
                                                + "\n"
                                                + "</android.support.constraint.ConstraintLayout>\n"),
                                gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.support.constraint:constraint-layout:1.0.0-alpha3\"'\n"
                                                + "}"));

        lint().projects(project).checkMessage(this::checkReportedError).run().expect(expected);
    }

    public void testWidthMatchParentHeightConstraint() {
        ProjectDescription project =
                project()
                        .files(
                                xml(
                                        "res/layout/layout1.xml",
                                        ""
                                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                                                + "   xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                                                + "   xmlns:tools=\"http://schemas.android.com/tools\""
                                                + "   android:layout_width=\"match_parent\""
                                                + "   android:layout_height=\"match_parent\">"
                                                + "\n"
                                                + "    <Button\n"
                                                + "        android:id=\"@+id/button\"\n"
                                                + "        android:layout_width=\"match_parent\"\n"
                                                + "        android:layout_height=\"100dp\"\n "
                                                + "        app:layout_constraintTop_toTopOf=\"parent\" /> "
                                                + "\n"
                                                + "</android.support.constraint.ConstraintLayout>\n"),
                                gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.support.constraint:constraint-layout:1.0.0-alpha3\"'\n"
                                                + "}"));

        lint().projects(project).checkMessage(this::checkReportedError).run().expect(NO_WARNING);
    }

    public void testHeightMatchParentWidthConstraint() {
        ProjectDescription project =
                project()
                        .files(
                                xml(
                                        "res/layout/layout1.xml",
                                        ""
                                                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                                                + "<android.support.constraint.ConstraintLayout xmlns:android=\"http://schemas.android.com/apk/res/android\""
                                                + "   xmlns:app=\"http://schemas.android.com/apk/res-auto\""
                                                + "   xmlns:tools=\"http://schemas.android.com/tools\""
                                                + "   android:layout_width=\"match_parent\""
                                                + "   android:layout_height=\"match_parent\">"
                                                + "\n"
                                                + "    <Button\n"
                                                + "        android:id=\"@+id/button\"\n"
                                                + "        android:layout_width=\"100dp\"\n"
                                                + "        android:layout_height=\"match_parent\"\n "
                                                + "        app:layout_constraintEnd_toEndOf=\"parent\" /> "
                                                + "\n"
                                                + "</android.support.constraint.ConstraintLayout>\n"),
                                gradle(
                                        ""
                                                + "apply plugin: 'com.android.application'\n"
                                                + "\n"
                                                + "dependencies {\n"
                                                + "    compile 'com.android.support.constraint:constraint-layout:1.0.0-alpha3\"'\n"
                                                + "}"));

        lint().projects(project).checkMessage(this::checkReportedError).run().expect(NO_WARNING);
    }

    @Override
    protected void checkReportedError(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @NonNull Location location,
            @NonNull String message,
            @Nullable LintFix fixData) {
        if (issue == GradleDetector.DEPENDENCY) {
            // Check for AndroidLintGradleDependencyInspection
            assertTrue(fixData instanceof LintFix.DataMap);
            LintFix.DataMap map = (LintFix.DataMap) fixData;
            assertNotNull(map.get(ConstraintLayoutDetector.class));
        }
    }

    @Override
    protected Detector getDetector() {
        return new ConstraintLayoutDetector();
    }
}
