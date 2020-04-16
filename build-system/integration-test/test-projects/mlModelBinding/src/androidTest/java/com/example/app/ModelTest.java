package com.example.app;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.app.ml.MobilenetQuantMetadata;
import com.example.app.ml.StylePredictQuantMetadata;
import com.example.app.ml.StyleTransferQuantMetadata;
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

        MobilenetQuantMetadata model = MobilenetQuantMetadata.newInstance(appContext);
        TensorImage tensorImage = new TensorImage();
        tensorImage.load(
                BitmapFactory.decodeResource(appContext.getResources(), R.drawable.flower));
        MobilenetQuantMetadata.Outputs outputs = model.process(tensorImage);
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

    @Test
    public void verifyStyleTransferModel() throws IOException {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        // First get style array
        StylePredictQuantMetadata stylePredictQuantized256 =
                StylePredictQuantMetadata.newInstance(appContext);
        TensorImage flower = new TensorImage();
        flower.load(BitmapFactory.decodeResource(appContext.getResources(), R.drawable.flower));
        StylePredictQuantMetadata.Outputs predictOutputs = stylePredictQuantized256.process(flower);
        assertNotNull(predictOutputs.getStyleBottleneckAsTensorBuffer().getBuffer());

        // Pass in image and style array to get styled image
        StyleTransferQuantMetadata styleTransferQuantized384 =
                StyleTransferQuantMetadata.newInstance(appContext);
        TensorImage tower = new TensorImage();
        tower.load(
                BitmapFactory.decodeResource(appContext.getResources(), R.drawable.eiffel_tower));
        StyleTransferQuantMetadata.Outputs transferOutputs =
                styleTransferQuantized384.process(
                        tower, predictOutputs.getStyleBottleneckAsTensorBuffer());

        Bitmap bitmap = transferOutputs.getStyledImageAsTensorImage().getBitmap();
        assertNotNull(bitmap);
        assertEquals("Bitmap width should be 384.", bitmap.getWidth(), 384);
        assertEquals("Bitmap height should be 384.", bitmap.getHeight(), 384);
    }
}
