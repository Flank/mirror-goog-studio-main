/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.deviceconfig;

import android.os.Bundle;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity implements OnClickListener {

    private static GLView mGl;

    /** Called when the activity is first created. */
    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        LinearLayout vg = findViewById(R.id.buttonHolder);
        if (vg == null) {
            return;
        }

        // Instantiate a GL surface view so we can get extensions information
        mGl = new GLView(this);
        // If we set the layout to be 0, it just won't render
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(1, 1);
        mGl.setLayoutParams(params);
        vg.addView(mGl);

        Button btn = findViewById(R.id.generateConfigButton);
        btn.setOnClickListener(this);
        Configuration config = getResources().getConfiguration();

        TextView tv = findViewById(R.id.keyboard_state_api);
        if (tv != null) {
            String separator = config.orientation == Configuration.ORIENTATION_PORTRAIT ? "\n" : "";
            String foo = "keyboardHidden=" + separator;
            if (config.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO) {
                foo += "EXPOSED";
            } else if (config.keyboardHidden == Configuration.KEYBOARDHIDDEN_YES) {
                foo += "HIDDEN";
            } else if (config.keyboardHidden == Configuration.KEYBOARDHIDDEN_UNDEFINED) {
                foo += "UNDEFINED";
            } else {
                foo += "?";
            }
            foo += "\nhardKeyboardHidden=" + separator;
            if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
                foo = foo + "EXPOSED";
            } else if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
                foo = foo + "HIDDEN";
            } else if (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_UNDEFINED) {
                foo = foo + "UNDEFINED";
            } else {
                foo = "?";
            }

            tv.setText(foo);
        }

        tv = findViewById(R.id.nav_state_api);
        if (tv != null) {
            if (config.navigationHidden == Configuration.NAVIGATIONHIDDEN_NO) {
                tv.setText("EXPOSED");
            } else if (config.navigationHidden == Configuration.NAVIGATIONHIDDEN_YES) {
                tv.setText("HIDDEN");
            } else if (config.navigationHidden == Configuration.NAVIGATIONHIDDEN_UNDEFINED) {
                tv.setText("UNDEFINED");
            } else {
                tv.setText("??");
            }
        }

        DisplayMetrics metrics = ConfigGenerator.getDisplayMetrics(this);

        tv = findViewById(R.id.size_api);
        if (tv != null) {
            WindowManager windowManager = getWindowManager();
            int widthPixels = ConfigGenerator.getScreenWidth(windowManager, metrics);
            int heightPixels = ConfigGenerator.getScreenHeight(windowManager, metrics);
            tv.setText(widthPixels + "x" + heightPixels);
        }

        tv = findViewById(R.id.xdpi);
        if (tv != null) {
            tv.setText(String.format(Locale.US, "%f", metrics.xdpi));
        }
        tv = findViewById(R.id.ydpi);
        if (tv != null) {
            tv.setText(String.format(Locale.US, "%f", metrics.ydpi));
        }

        tv = findViewById(R.id.scaled_density);
        if (tv != null) {
            tv.setText(String.format(Locale.US, "%f", metrics.scaledDensity));
        }

        tv = findViewById(R.id.font_scale);
        if (tv != null) {
            tv.setText(String.format(Locale.US, "%f", config.fontScale));
        }
    }

    public void onClick(View v) {
        ConfigGenerator configGen = new ConfigGenerator(this, mGl.getExtensions(), mGl.getGpuInfo());
        final String filename = configGen.generateConfig();
        if (filename != null) {
            Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
            emailIntent.setType("text/xml");
            File devicesXml = new File(filename);
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Device XML: " + devicesXml.getName());
            emailIntent.putExtra(Intent.EXTRA_TEXT, "Note: This is intended to generate a base "
                    + "XML description. After running this, you should double check the generated "
                    + "information and add all of the missing fields.");
            emailIntent.putExtra(Intent.EXTRA_STREAM,
                    Uri.parse("file://" + devicesXml.getAbsolutePath()));
            startActivity(emailIntent);
        }
    }

    private static class GLView extends GLSurfaceView {
        private GlRenderer mRenderer;

        public GLView(Context context) {
            super(context);
            setEGLContextClientVersion(2);
            mRenderer = new GlRenderer();
            setRenderer(mRenderer);
            requestRender();
        }

        public String getExtensions() {
            return mRenderer.extensions;
        }
        public String getGpuInfo() {
            return mRenderer.gpuInfo;
        }

    }

    private static class GlRenderer implements GLSurfaceView.Renderer {
        String extensions = "";
        String gpuInfo = "";

        public void onDrawFrame(GL10 gl) {
        }

        public void onSurfaceChanged(GL10 gl, int width, int height) {
            gl.glViewport(0, 0, 0, 0);
        }

        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            if (gpuInfo.isEmpty()) {
                String vendor   = gl.glGetString(GL10.GL_VENDOR);
                String renderer = gl.glGetString(GL10.GL_RENDERER);
                String version  = gl.glGetString(GL10.GL_VERSION);

                if (vendor != null) gpuInfo += vendor;
                gpuInfo += ", ";
                if (renderer != null) gpuInfo += renderer;
                gpuInfo += ", ";
                if (version != null) gpuInfo += version;
            }
            if (extensions.isEmpty()) {
                String extensions10 = gl.glGetString(GL10.GL_EXTENSIONS);
                if (extensions10 != null) {
                    extensions += extensions10;
                }
            }
        }
    }
}
