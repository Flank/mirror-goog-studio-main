/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.wizard.template.impl.activities.cppGameActivity.src

fun utilityCpp(): String {
    return """
#include "Utility.h"
#include "AndroidOut.h"

#include <GLES3/gl3.h>

#define CHECK_ERROR(e) case e: aout << "GL Error: "#e << std::endl; break;

bool Utility::checkAndLogGlError(bool alwaysLog) {
    GLenum error = glGetError();
    if (error == GL_NO_ERROR) {
        if (alwaysLog) {
            aout << "No GL error" << std::endl;
        }
        return true;
    } else {
        switch (error) {
            CHECK_ERROR(GL_INVALID_ENUM);
            CHECK_ERROR(GL_INVALID_VALUE);
            CHECK_ERROR(GL_INVALID_OPERATION);
            CHECK_ERROR(GL_INVALID_FRAMEBUFFER_OPERATION);
            CHECK_ERROR(GL_OUT_OF_MEMORY);
            default:
                aout << "Unknown GL error: " << error << std::endl;
        }
        return false;
    }
}

float *
Utility::buildOrthographicMatrix(float *outMatrix, float halfHeight, float aspect, float near,
                                 float far) {
    float halfWidth = halfHeight * aspect;

    // column 1
    outMatrix[0] = 1.f / halfWidth;
    outMatrix[1] = 0.f;
    outMatrix[2] = 0.f;
    outMatrix[3] = 0.f;

    // column 2
    outMatrix[4] = 0.f;
    outMatrix[5] = 1.f / halfHeight;
    outMatrix[6] = 0.f;
    outMatrix[7] = 0.f;

    // column 3
    outMatrix[8] = 0.f;
    outMatrix[9] = 0.f;
    outMatrix[10] = -2.f / (far - near);
    outMatrix[11] = -(far + near) / (far - near);

    // column 4
    outMatrix[12] = 0.f;
    outMatrix[13] = 0.f;
    outMatrix[14] = 0.f;
    outMatrix[15] = 1.f;

    return outMatrix;
}

float *Utility::buildIdentityMatrix(float *outMatrix) {
    // column 1
    outMatrix[0] = 1.f;
    outMatrix[1] = 0.f;
    outMatrix[2] = 0.f;
    outMatrix[3] = 0.f;

    // column 2
    outMatrix[4] = 0.f;
    outMatrix[5] = 1.f;
    outMatrix[6] = 0.f;
    outMatrix[7] = 0.f;

    // column 3
    outMatrix[8] = 0.f;
    outMatrix[9] = 0.f;
    outMatrix[10] = 1.f;
    outMatrix[11] = 0.f;

    // column 4
    outMatrix[12] = 0.f;
    outMatrix[13] = 0.f;
    outMatrix[14] = 0.f;
    outMatrix[15] = 1.f;

    return outMatrix;
}
"""
}

fun utilityH(): String {
    return """
#ifndef ANDROIDGLINVESTIGATIONS_UTILITY_H
#define ANDROIDGLINVESTIGATIONS_UTILITY_H


#include <cassert>

class Utility {
public:
    static bool checkAndLogGlError(bool alwaysLog = false);

    static inline void assertGlError() { assert(checkAndLogGlError()); }

    /**
     * Generates an orthographic projection matrix given the half height, aspect ratio, near, and far
     * planes
     *
     * @param outMatrix the matrix to write into
     * @param halfHeight half of the height of the screen
     * @param aspect the width of the screen divided by the height
     * @param near the distance of the near plane
     * @param far the distance of the far plane
     * @return the generated matrix, this will be the same as @a outMatrix so you can chain calls
     *     together if needed
     */
    static float *buildOrthographicMatrix(
            float *outMatrix,
            float halfHeight,
            float aspect,
            float near,
            float far);

    static float *buildIdentityMatrix(float *outMatrix);
};


#endif //ANDROIDGLINVESTIGATIONS_UTILITY_H
"""
}
