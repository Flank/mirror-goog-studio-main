package com.example.app;

import static org.junit.Assert.assertEquals;

import com.example.bytecode.App;
import com.example.bytecode.Lib;
import com.example.bytecode.Test;

public class ExampleUnitTest {
    @org.junit.Test
    public void test_works() throws Exception {
        // test the generated test class is available
        Test test = new Test("stuff");
        assertEquals("stuff", test.getName());
    }

    @org.junit.Test
    public void app_works() throws Exception {
        // test the bytecode of the tested app is present
        App app = new App("stuff");
        assertEquals("stuff", app.getName());
    }

    @org.junit.Test
    public void lib_works() throws Exception {
        // test the bytecode of the tested app's dependencies is present
        Lib lib = new Lib("stuff");
        assertEquals("stuff", lib.getName());
    }
}
