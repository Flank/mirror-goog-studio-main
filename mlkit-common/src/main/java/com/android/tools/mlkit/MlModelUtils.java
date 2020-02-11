package com.android.tools.mlkit;

import com.android.ide.common.repository.GradleCoordinate;
import java.util.ArrayList;
import java.util.List;

public class MlModelUtils {

    /** Return dependencies that needed for mlkit generated code. */
    public static List<GradleCoordinate> calculateRequiredDependencies() {
        List<GradleCoordinate> gradleCoordinates = new ArrayList<>();
        gradleCoordinates.add(
                GradleCoordinate.parseCoordinateString("org.apache.commons:commons-compress:1.19"));
        gradleCoordinates.add(
                GradleCoordinate.parseCoordinateString("org.tensorflow:tensorflow-lite:1.13.1"));
        gradleCoordinates.add(
                GradleCoordinate.parseCoordinateString(
                        "org.tensorflow:tensorflow-lite-support:0.0.0-nightly"));
        return gradleCoordinates;
    }
}
