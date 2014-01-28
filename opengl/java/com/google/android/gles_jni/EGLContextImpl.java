/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.google.android.gles_jni;

import javax.microedition.khronos.egl.*;
import javax.microedition.khronos.opengles.GL;

public class EGLContextImpl extends EGLContext {
    private GLImpl mGLContext;
    int mEGLContext;

    public EGLContextImpl(int ctx) {
        mEGLContext = ctx;
        mGLContext = new GLImpl();
    }

    @Override
    public GL getGL() {
        return mGLContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EGLContextImpl that = (EGLContextImpl) o;

        return mEGLContext == that.mEGLContext;
    }

    @Override
    public int hashCode() {
        return mEGLContext;
    }
}
