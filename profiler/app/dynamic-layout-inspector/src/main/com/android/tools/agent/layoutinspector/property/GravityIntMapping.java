package com.android.tools.agent.layoutinspector.property;

import android.view.Gravity;
import android.view.inspector.IntFlagMapping;
import java.util.Set;
import java.util.function.IntFunction;

public class GravityIntMapping implements IntFunction<Set<String>> {
    private IntFlagMapping gravityIntFlagMapping = new IntFlagMapping();

    public GravityIntMapping() {
        gravityIntFlagMapping.add(Gravity.TOP | Gravity.BOTTOM, Gravity.TOP, "top");
        gravityIntFlagMapping.add(Gravity.TOP | Gravity.BOTTOM, Gravity.BOTTOM, "bottom");
        gravityIntFlagMapping.add(Gravity.START | Gravity.END, Gravity.LEFT, "left");
        gravityIntFlagMapping.add(Gravity.START | Gravity.END, Gravity.RIGHT, "right");
        gravityIntFlagMapping.add(
                Gravity.FILL_VERTICAL, Gravity.CENTER_VERTICAL, "center_vertical");
        gravityIntFlagMapping.add(
                Gravity.FILL_HORIZONTAL, Gravity.CENTER_HORIZONTAL, "center_horizontal");
        gravityIntFlagMapping.add(Gravity.FILL, Gravity.CENTER, "center");
        gravityIntFlagMapping.add(Gravity.START | Gravity.END, Gravity.START, "start");
        gravityIntFlagMapping.add(Gravity.START | Gravity.END, Gravity.END, "end");
        gravityIntFlagMapping.add(Gravity.FILL_VERTICAL, Gravity.FILL_VERTICAL, "fill_vertical");
        gravityIntFlagMapping.add(
                Gravity.FILL_HORIZONTAL, Gravity.FILL_HORIZONTAL, "fill_horizontal");
        gravityIntFlagMapping.add(Gravity.FILL, Gravity.FILL, "fill");
        gravityIntFlagMapping.add(Gravity.CLIP_VERTICAL, Gravity.CLIP_VERTICAL, "clip_vertical");
        gravityIntFlagMapping.add(
                Gravity.CLIP_HORIZONTAL, Gravity.CLIP_HORIZONTAL, "clip_horizontal");
    }

    @Override
    public Set<String> apply(int value) {
        return gravityIntFlagMapping.get(value);
    }
}
