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

#ifndef ANDROID_HWUI_DITHER_H
#define ANDROID_HWUI_DITHER_H

#include <GLES2/gl2.h>

#include "Program.h"

namespace android {
namespace uirenderer {

/**
 * Handles dithering for programs.
 */
class Dither {
public:
    Dither(): mInitialized(false), mDitherTexture(0) { }

    void clear();
    void setupProgram(Program* program, GLuint* textureUnit);

private:
    void bindDitherTexture();

    bool mInitialized;
    GLuint mDitherTexture;
};

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_DITHER_H
