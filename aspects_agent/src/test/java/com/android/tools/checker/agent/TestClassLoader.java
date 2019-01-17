package com.android.tools.checker.agent;

class TestClassLoader extends ClassLoader {
    TestClassLoader(ClassLoader parent) {
        super(parent);
    }

    public Class<?> defineClass(String name, byte[] buffer) {
        return defineClass(name, buffer, 0, buffer.length);
    }
}
