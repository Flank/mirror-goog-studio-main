package com.example.android.multiproject.library.base.test;

import static org.junit.Assert.*;

import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import com.sample.android.multiproject.library.PersonView;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TestActivityTest {
    @Rule
    public ActivityTestRule<TestActivity> rule = new ActivityTestRule<>(TestActivity.class);

    @Test
    public void testPreconditions() {
        TestActivity activity = rule.getActivity();
        PersonView view = (PersonView) activity.findViewById(R.id.view);

        assertNotNull(view);
        assertEquals(20.0f, view.getTextSize(), 0.1);
    }
}
