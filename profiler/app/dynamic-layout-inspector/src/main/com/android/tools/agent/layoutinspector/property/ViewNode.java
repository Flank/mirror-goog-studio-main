package com.android.tools.agent.layoutinspector.property;

import android.content.res.Resources;
import android.graphics.Color;
import android.view.View;
import android.view.inspector.PropertyReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Holds the properties (including their values) of a View. */
public class ViewNode<V extends View> {
    private ViewType<V> mType;
    private List<Property> mProperties;

    public ViewNode(ViewType<V> type) {
        mType = type;
        mProperties = new ArrayList<>();
        for (PropertyType propertyType : type.getProperties()) {
            mProperties.add(new Property(propertyType));
        }
    }

    public ViewType<V> getType() {
        return mType;
    }

    public List<Property> getProperties() {
        return mProperties;
    }

    public void readProperties(V view, Map<String, Integer> stringMap) {
        PropertyReader reader = createPropertyReader(view, stringMap);
        mType.readProperties(view, reader);
    }

    public Resource getLayoutResource(V view, Map<String, Integer> stringMap) {
        SimplePropertyReader reader = createPropertyReader(view, stringMap);
        return reader.getResourceValue(getSourceLayoutResId(view));
    }

    private SimplePropertyReader createPropertyReader(V view, Map<String, Integer> stringMap) {
        int layoutId = getSourceLayoutResId(view);
        Map<Integer, Integer> resourceMap = Collections.emptyMap();
        if (layoutId != 0) {
            resourceMap = getAttributeSourceResourceMap(view);
        }
        return new SimplePropertyReader(stringMap, resourceMap, view.getResources());
    }

    private int getSourceLayoutResId(V view) {
        try {
            // TODO: Call this method directly when we compile against android-Q
            Method method = View.class.getDeclaredMethod("getSourceLayoutResId");
            Integer layoutId = (Integer) method.invoke(view);
            return layoutId != null ? layoutId : 0;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return 0;
        }
    }

    private Map<Integer, Integer> getAttributeSourceResourceMap(V view) {
        try {
            // TODO: Call this method directly when we compile against android-Q
            Method method = View.class.getDeclaredMethod("getAttributeSourceResourceMap");
            //noinspection unchecked
            return (Map<Integer, Integer>) method.invoke(view);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            return Collections.emptyMap();
        }
    }

    private class SimplePropertyReader implements PropertyReader {
        private final Map<String, Integer> mStringMap;
        private final Map<Integer, Integer> mResourceMap;
        private final Resources mResources;

        private SimplePropertyReader(
                Map<String, Integer> stringMap,
                Map<Integer, Integer> resourceMap,
                Resources resources) {
            mStringMap = stringMap;
            mResourceMap = resourceMap;
            mResources = resources;
        }

        @Override
        public void readBoolean(int id, boolean b) {
            readAny(id, b ? 1 : 0);
        }

        @Override
        public void readByte(int id, byte b) {
            readAny(id, (int) b);
        }

        @Override
        public void readChar(int id, char c) {
            readAny(id, (int) c);
        }

        @Override
        public void readDouble(int id, double d) {
            readAny(id, d);
        }

        @Override
        public void readFloat(int id, float f) {
            readAny(id, f);
        }

        @Override
        public void readInt(int id, int i) {
            readAny(id, i);
        }

        @Override
        public void readLong(int id, long l) {
            readAny(id, l);
        }

        @Override
        public void readShort(int id, short s) {
            readAny(id, (int) s);
        }

        @Override
        public void readObject(int id, Object o) {
            if (o instanceof String) {
                readAny(id, generateStringId((String) o));
                mProperties.get(id).setType(ValueType.STRING);
            } else {
                readAny(id, null);
            }
        }

        @Override
        public void readColor(int id, int color) {
            readAny(id, color);
        }

        @Override
        public void readColor(int id, long color) {
            readAny(id, color);
        }

        @Override
        public void readColor(int id, Color color) {
            readAny(id, color);
        }

        @Override
        public void readGravity(int id, int value) {
            readAny(id, value);
        }

        @Override
        public void readIntEnum(int id, int value) {
            readAny(id, value);
        }

        @Override
        public void readIntFlag(int id, int value) {
            readAny(id, value);
        }

        @Override
        public void readResourceId(int id, int value) {
            readAny(id, getResourceValue(value));
        }

        private void readAny(int id, Object value) {
            Property property = mProperties.get(id);
            PropertyType type = property.getPropertyType();
            property.setNameId(generateStringId(type.getName()));
            property.setValue(value);
            property.setSource(getResourceValueOfAttribute(type.getAttributeId()));
        }

        private Resource getResourceValueOfAttribute(int attributeId) {
            Integer resourceId = mResourceMap.get(attributeId);
            return resourceId != null ? getResourceValue(resourceId) : null;
        }

        private Resource getResourceValue(int resourceId) {
            if (resourceId <= 0) {
                return null;
            }
            try {
                String type = mResources.getResourceTypeName(resourceId);
                String packageName = mResources.getResourcePackageName(resourceId);
                String name = mResources.getResourceEntryName(resourceId);
                return new Resource(
                        generateStringId(type),
                        generateStringId(packageName),
                        generateStringId(name));
            } catch (Resources.NotFoundException ex) {
                return null;
            }
        }

        private int generateStringId(String str) {
            if (str == null || str.isEmpty()) {
                return 0;
            }
            Integer id = mStringMap.get(str);
            if (id != null) {
                return id;
            }
            int newId = mStringMap.size() + 1;
            mStringMap.put(str, newId);
            return newId;
        }
    }
}
