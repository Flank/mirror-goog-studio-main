package com.android.tools.agent.layoutinspector.property;

import android.view.ViewGroup.LayoutParams;
import android.view.inspector.InspectionCompanion;
import android.view.inspector.InspectionCompanionProvider;
import android.view.inspector.StaticInspectionCompanionProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a tree of {@link LayoutParamsType}s.
 *
 * <p>A caller can add a LayoutParamsType to the tree by calling {@link #typeOf}. All super classes
 * of this LayoutParams up to android.view.ViewGroup.LayoutParams will be included.
 */
public class LayoutParamsTypeTree {
    private InspectionCompanionProvider inspectionCompanionProvider =
            new StaticInspectionCompanionProvider();
    private Map<Class<? extends LayoutParams>, LayoutParamsType<? extends LayoutParams>> typeMap =
            new HashMap<>();

    @SuppressWarnings("unchecked")
    public <L extends LayoutParams> LayoutParamsType<L> typeOf(L layoutParams) {
        return typeOf((Class<L>) layoutParams.getClass());
    }

    public <L extends LayoutParams> LayoutParamsType<L> typeOf(Class<L> layoutParamsClass) {
        return innerTypeOf(layoutParamsClass);
    }

    private <L extends LayoutParams> LayoutParamsType<L> innerTypeOf(Class<L> layoutParamsClass) {
        @SuppressWarnings("unchecked")
        LayoutParamsType<L> type = (LayoutParamsType<L>) typeMap.get(layoutParamsClass);
        if (type != null) {
            return type;
        }

        InspectionCompanion inspectionCompanion = loadInspectionCompanion(layoutParamsClass);
        @SuppressWarnings("unchecked")
        LayoutParamsType<? extends LayoutParams> superType =
                !layoutParamsClass.getCanonicalName().equals("android.view.ViewGroup.LayoutParams")
                        ? innerTypeOf(
                                (Class<? extends LayoutParams>) layoutParamsClass.getSuperclass())
                        : null;
        List<InspectionCompanion> companions = new ArrayList<>();
        if (superType != null) {
            companions.addAll(superType.getInspectionCompanions());
        }
        if (inspectionCompanion != null) {
            companions.add(inspectionCompanion);
        }

        List<PropertyType> properties = new ArrayList<>();
        String nodeName = layoutParamsClass.getSimpleName();
        if (superType != null) {
            properties.addAll(superType.getProperties());
        }
        if (inspectionCompanion != null) {
            PropertyTypeMapper mapper = new PropertyTypeMapper(properties);
            inspectionCompanion.mapProperties(mapper);
            properties = mapper.getProperties();
        }

        //noinspection unchecked
        type =
                new LayoutParamsType(
                        nodeName, layoutParamsClass.getCanonicalName(), properties, companions);
        typeMap.put(layoutParamsClass, type);
        return type;
    }

    private <L extends LayoutParams> InspectionCompanion<L> loadInspectionCompanion(
            Class<L> javaClass) {
        return inspectionCompanionProvider.provide(javaClass);
    }
}
