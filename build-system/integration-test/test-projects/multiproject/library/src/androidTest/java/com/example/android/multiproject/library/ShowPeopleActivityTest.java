package com.example.android.multiproject.library;

import static org.junit.Assert.*;

import android.support.test.rule.ActivityTestRule;
import android.view.View;
import android.widget.LinearLayout;
import org.junit.Rule;
import org.junit.Test;

public class ShowPeopleActivityTest {
    @Rule
    public ActivityTestRule<ShowPeopleActivity> rule = new ActivityTestRule<>(ShowPeopleActivity.class);

    @Test
    public void testContentView() {
        ShowPeopleActivity activity = rule.getActivity();

        View view = activity.findViewById(R.id.rootView);

        assertTrue(view instanceof LinearLayout);
    }
}

