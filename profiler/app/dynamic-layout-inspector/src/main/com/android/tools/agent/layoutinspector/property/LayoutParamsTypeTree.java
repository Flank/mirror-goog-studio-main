package com.android.tools.agent.layoutinspector.property;

import android.view.ViewGroup.LayoutParams;
import android.view.inspector.InspectionCompanion;
import android.view.inspector.InspectionCompanionProvider;
import android.view.inspector.StaticInspectionCompanionProvider;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private Map<Class<? extends LayoutParams>, LayoutParamsType> typeMap = new HashMap<>();

    @NonNull
    public <L extends LayoutParams> LayoutParamsType typeOf(@NonNull L layoutParams) {
        return typeOf(layoutParams.getClass());
    }

    @NonNull
    public <L extends LayoutParams> LayoutParamsType typeOf(@NonNull Class<L> layoutParamsClass) {
        return innerTypeOf(layoutParamsClass);
    }

    @NonNull
    private <L extends LayoutParams> LayoutParamsType innerTypeOf(
            @NonNull Class<L> layoutParamsClass) {
        LayoutParamsType type = typeMap.get(layoutParamsClass);
        if (type != null) {
            return type;
        }

        InspectionCompanion<LayoutParams> inspectionCompanion =
                loadInspectionCompanion(layoutParamsClass);
        @SuppressWarnings("unchecked")
        LayoutParamsType superType =
                !layoutParamsClass.getCanonicalName().equals("android.view.ViewGroup.LayoutParams")
                        ? innerTypeOf(
                                (Class<? extends LayoutParams>) layoutParamsClass.getSuperclass())
                        : null;
        List<InspectionCompanion<LayoutParams>> companions = new ArrayList<>();
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

        type =
                new LayoutParamsType(
                        nodeName, layoutParamsClass.getCanonicalName(), properties, companions);
        typeMap.put(layoutParamsClass, type);
        return type;
    }

    @Nullable
    private <L extends LayoutParams> InspectionCompanion<LayoutParams> loadInspectionCompanion(
            @NonNull Class<L> javaClass) {
        //noinspection unchecked
        return (InspectionCompanion<LayoutParams>) inspectionCompanionProvider.provide(javaClass);
    }
}
