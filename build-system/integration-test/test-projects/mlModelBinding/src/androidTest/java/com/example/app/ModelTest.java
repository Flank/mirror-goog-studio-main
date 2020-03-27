package com.example.app;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.app.ml.Model;
import java.io.IOException;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tensorflow.lite.support.image.TensorImage;

/** Instrumented test, which will execute on an Android device. */
@RunWith(AndroidJUnit4.class)
public class ModelTest {
    @Test
    public void verifyImageClassificationModel() throws IOException {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        Model model = Model.newInstance(appContext);
        TensorImage tensorImage = new TensorImage();
        tensorImage.load(
                BitmapFactory.decodeResource(appContext.getResources(), R.drawable.flower));
        Model.Outputs outputs = model.process(tensorImage);
        Map<String, Float> probabilityMap =
                outputs.getProbabilityAsTensorLabel().getMapWithFloatValue();

        float prob = 0;
        String result = "";
        for (Map.Entry<String, Float> entry : probabilityMap.entrySet()) {
            if (entry.getValue() > prob) {
                prob = entry.getValue();
                result = entry.getKey();
            }
        }
        assertEquals("Top1 label in image classification model is wrong.", "daisy", result);
    }
}
