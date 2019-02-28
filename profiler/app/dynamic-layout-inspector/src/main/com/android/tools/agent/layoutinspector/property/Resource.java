package com.android.tools.agent.layoutinspector.property;

import java.util.Map;

/**
 * Holds a snapshot of a ResourceReference.
 *
 * <p>In Android a resource id is a simple integer. This class holds the namespace, type, and name
 * of such a resource id. e.g. "@android:id/textView2" The 3 strings are kept as integers into the
 * generated string map.
 */
public class Resource {
    private final int mType;
    private final int mNamespace;
    private final int mName;

    Resource(int type, int namespace, int name) {
        mType = type;
        mNamespace = namespace;
        mName = name;
    }

    public int getType() {
        return mType;
    }

    public int getNamespace() {
        return mNamespace;
    }

    public int getName() {
        return mName;
    }

    @Override
    public String toString() {
        return String.format("resource:(%d, %d, %d)", mType, mNamespace, mName);
    }

    private String toString(Map<Integer, String> map) {
        return String.format(
                "resource:(@%s:%s/%s)", map.get(mNamespace), map.get(mType), map.get(mName));
    }

    public static String toString(Resource resource, Map<Integer, String> map) {
        map.entrySet().iterator();
        return resource != null ? resource.toString(map) : "";
    }
}
