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

package com.android.server.power;

import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES10;
import android.opengl.GLES11Ext;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.FloatMath;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.Surface.OutOfResourcesException;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceSession;

import com.android.server.display.DisplayManagerService;
import com.android.server.display.DisplayTransactionListener;

/**
 * Bzzzoooop!  *crackle*
 * <p>
 * Animates a screen transition from on to off or off to on by applying
 * some GL transformations to a screenshot.
 * </p><p>
 * This component must only be created or accessed by the {@link Looper} thread
 * that belongs to the {@link DisplayPowerController}.
 * </p>
 */
final class ElectronBeam {
    private static final String TAG = "ElectronBeam";

    private static final boolean DEBUG = false;

    // The layer for the electron beam surface.
    // This is currently hardcoded to be one layer above the boot animation.
    private static final int ELECTRON_BEAM_LAYER = 0x40000001;

    // The relative proportion of the animation to spend performing
    // the horizontal stretch effect.  The remainder is spent performing
    // the vertical stretch effect.
    private static final float HSTRETCH_DURATION = 0.5f;
    private static final float VSTRETCH_DURATION = 1.0f - HSTRETCH_DURATION;

    // The number of frames to draw when preparing the animation so that it will
    // be ready to run smoothly.  We use 3 frames because we are triple-buffered.
    // See code for details.
    private static final int DEJANK_FRAMES = 3;

    // Set to true when the animation context has been fully prepared.
    private boolean mPrepared;
    private int mMode;

    private final DisplayManagerService mDisplayManager;
    private int mDisplayLayerStack; // layer stack associated with primary display
    private int mDisplayWidth;      // real width, not rotated
    private int mDisplayHeight;     // real height, not rotated
    private SurfaceSession mSurfaceSession;
    private SurfaceControl mSurfaceControl;
    private Surface mSurface;
    private NaturalSurfaceLayout mSurfaceLayout;
    private EGLDisplay mEglDisplay;
    private EGLConfig mEglConfig;
    private EGLContext mEglContext;
    private EGLSurface mEglSurface;
    private boolean mSurfaceVisible;
    private float mSurfaceAlpha;
    private final int mHWRotation;
    private final boolean mSwapNeeded;

    // Texture names.  We only use one texture, which contains the screenshot.
    private final int[] mTexNames = new int[1];
    private boolean mTexNamesGenerated;
    private final float mTexMatrix[] = new float[16];

    // Vertex and corresponding texture coordinates.
    // We have 4 2D vertices, so 8 elements.  The vertices form a quad.
    private final FloatBuffer mVertexBuffer = createNativeFloatBuffer(8);
    private final FloatBuffer mTexCoordBuffer = createNativeFloatBuffer(8);

    /**
     * Animates an electron beam warming up.
     */
    public static final int MODE_WARM_UP = 0;

    /**
     * Animates an electron beam shutting off.
     */
    public static final int MODE_COOL_DOWN = 1;

    /**
     * Animates a simple dim layer to fade the contents of the screen in or out progressively.
     */
    public static final int MODE_FADE = 2;


    public ElectronBeam(DisplayManagerService displayManager) {
        mDisplayManager = displayManager;
        mHWRotation = Integer.parseInt(SystemProperties.get("ro.sf.hwrotation", "0")) / 90;
        mSwapNeeded = mHWRotation % 2 == 1;
    }

    /**
     * Warms up the electron beam in preparation for turning on or off.
     * This method prepares a GL context, and captures a screen shot.
     *
     * @param mode The desired mode for the upcoming animation.
     * @return True if the electron beam is ready, false if it is uncontrollable.
     */
    public boolean prepare(int mode) {
        if (DEBUG) {
            Slog.d(TAG, "prepare: mode=" + mode);
        }

        mMode = mode;

        // Get the display size and layer stack.
        // This is not expected to change while the electron beam surface is showing.
        DisplayInfo displayInfo = mDisplayManager.getDisplayInfo(Display.DEFAULT_DISPLAY);
        mDisplayLayerStack = displayInfo.layerStack;

        if (mSwapNeeded) {
            mDisplayWidth = displayInfo.getNaturalHeight();
            mDisplayHeight = displayInfo.getNaturalWidth();
        } else {
            mDisplayWidth = displayInfo.getNaturalWidth();
            mDisplayHeight = displayInfo.getNaturalHeight();
        }

        // Prepare the surface for drawing.
        if (!tryPrepare()) {
            dismiss();
            return false;
        }

        // Done.
        mPrepared = true;

        // Dejanking optimization.
        // Some GL drivers can introduce a lot of lag in the first few frames as they
        // initialize their state and allocate graphics buffers for rendering.
        // Work around this problem by rendering the first frame of the animation a few
        // times.  The rest of the animation should run smoothly thereafter.
        // The frames we draw here aren't visible because we are essentially just
        // painting the screenshot as-is.
        if (mode == MODE_COOL_DOWN) {
            for (int i = 0; i < DEJANK_FRAMES; i++) {
                draw(1.0f);
            }
        }
        return true;
    }

    private boolean tryPrepare() {
        if (createSurface()) {
            if (mMode == MODE_FADE) {
                return true;
            }
            return createEglContext()
                    && createEglSurface()
                    && captureScreenshotTextureAndSetViewport();
        }
        return false;
    }

    /**
     * Dismisses the electron beam animation surface and cleans up.
     *
     * To prevent stray photons from leaking out after the electron beam has been
     * turned off, it is a good idea to defer dismissing the animation until the
     * electron beam has been turned back on fully.
     */
    public void dismiss() {
        if (DEBUG) {
            Slog.d(TAG, "dismiss");
        }

        destroyScreenshotTexture();
        destroyEglSurface();
        destroySurface();
        mPrepared = false;
    }

    /**
     * Draws an animation frame showing the electron beam activated at the
     * specified level.
     *
     * @param level The electron beam level.
     * @return True if successful.
     */
    public boolean draw(float level) {
        if (DEBUG) {
            Slog.d(TAG, "drawFrame: level=" + level);
        }

        if (!mPrepared) {
            return false;
        }

        if (mMode == MODE_FADE) {
            return showSurface(1.0f - level);
        }

        if (!attachEglContext()) {
            return false;
        }
        try {
            // Clear frame to solid black.
            GLES10.glClearColor(0f, 0f, 0f, 1f);
            GLES10.glClear(GLES10.GL_COLOR_BUFFER_BIT);

            // Draw the frame.
            if (level < HSTRETCH_DURATION) {
                drawHStretch(1.0f - (level / HSTRETCH_DURATION));
            } else {
                drawVStretch(1.0f - ((level - HSTRETCH_DURATION) / VSTRETCH_DURATION));
            }
            if (checkGlErrors("drawFrame")) {
                return false;
            }

            EGL14.eglSwapBuffers(mEglDisplay, mEglSurface);
        } finally {
            detachEglContext();
        }
        return showSurface(1.0f);
    }

    /**
     * Draws a frame where the content of the electron beam is collapsing inwards upon
     * itself vertically with red / green / blue channels dispersing and eventually
     * merging down to a single horizontal line.
     *
     * @param stretch The stretch factor.  0.0 is no collapse, 1.0 is full collapse.
     */
    private void drawVStretch(float stretch) {
        // compute interpolation scale factors for each color channel
        final float ar = scurve(stretch, 7.5f);
        final float ag = scurve(stretch, 8.0f);
        final float ab = scurve(stretch, 8.5f);
        if (DEBUG) {
            Slog.d(TAG, "drawVStretch: stretch=" + stretch
                    + ", ar=" + ar + ", ag=" + ag + ", ab=" + ab);
        }

        // set blending
        GLES10.glBlendFunc(GLES10.GL_ONE, GLES10.GL_ONE);
        GLES10.glEnable(GLES10.GL_BLEND);

        // bind vertex buffer
        GLES10.glVertexPointer(2, GLES10.GL_FLOAT, 0, mVertexBuffer);
        GLES10.glEnableClientState(GLES10.GL_VERTEX_ARRAY);

        // set-up texturing
        GLES10.glDisable(GLES10.GL_TEXTURE_2D);
        GLES10.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);

        // bind texture and set blending for drawing planes
        GLES10.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexNames[0]);
        GLES10.glTexEnvx(GLES10.GL_TEXTURE_ENV, GLES10.GL_TEXTURE_ENV_MODE,
                mMode == MODE_WARM_UP ? GLES10.GL_MODULATE : GLES10.GL_REPLACE);
        GLES10.glTexParameterx(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES10.GL_TEXTURE_MAG_FILTER, GLES10.GL_LINEAR);
        GLES10.glTexParameterx(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES10.GL_TEXTURE_MIN_FILTER, GLES10.GL_LINEAR);
        GLES10.glTexParameterx(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES10.GL_TEXTURE_WRAP_S, GLES10.GL_CLAMP_TO_EDGE);
        GLES10.glTexParameterx(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES10.GL_TEXTURE_WRAP_T, GLES10.GL_CLAMP_TO_EDGE);
        GLES10.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        GLES10.glTexCoordPointer(2, GLES10.GL_FLOAT, 0, mTexCoordBuffer);
        GLES10.glEnableClientState(GLES10.GL_TEXTURE_COORD_ARRAY);

        // draw the red plane
        setVStretchQuad(mVertexBuffer, mDisplayWidth, mDisplayHeight, ar, mSwapNeeded);
        GLES10.glColorMask(true, false, false, true);
        GLES10.glDrawArrays(GLES10.GL_TRIANGLE_FAN, 0, 4);

        // draw the green plane
        setVStretchQuad(mVertexBuffer, mDisplayWidth, mDisplayHeight, ag, mSwapNeeded);
        GLES10.glColorMask(false, true, false, true);
        GLES10.glDrawArrays(GLES10.GL_TRIANGLE_FAN, 0, 4);

        // draw the blue plane
        setVStretchQuad(mVertexBuffer, mDisplayWidth, mDisplayHeight, ab, mSwapNeeded);
        GLES10.glColorMask(false, false, true, true);
        GLES10.glDrawArrays(GLES10.GL_TRIANGLE_FAN, 0, 4);

        // clean up after drawing planes
        GLES10.glDisable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        GLES10.glDisableClientState(GLES10.GL_TEXTURE_COORD_ARRAY);
        GLES10.glColorMask(true, true, true, true);

        // draw the white highlight (we use the last vertices)
        if (mMode == MODE_COOL_DOWN) {
            GLES10.glColor4f(ag, ag, ag, 1.0f);
            GLES10.glDrawArrays(GLES10.GL_TRIANGLE_FAN, 0, 4);
        }

        // clean up
        GLES10.glDisableClientState(GLES10.GL_VERTEX_ARRAY);
        GLES10.glDisable(GLES10.GL_BLEND);
    }

    /**
     * Draws a frame where the electron beam has been stretched out into
     * a thin white horizontal line that fades as it collapses inwards.
     *
     * @param stretch The stretch factor.  0.0 is maximum stretch / no fade,
     * 1.0 is collapsed / maximum fade.
     */
    private void drawHStretch(float stretch) {
        // compute interpolation scale factor
        final float ag = scurve(stretch, 8.0f);
        if (DEBUG) {
            Slog.d(TAG, "drawHStretch: stretch=" + stretch + ", ag=" + ag);
        }

        if (stretch < 1.0f) {
            // bind vertex buffer
            GLES10.glVertexPointer(2, GLES10.GL_FLOAT, 0, mVertexBuffer);
            GLES10.glEnableClientState(GLES10.GL_VERTEX_ARRAY);

            // draw narrow fading white line
            setHStretchQuad(mVertexBuffer, mDisplayWidth, mDisplayHeight, ag, mSwapNeeded);
            GLES10.glColor4f(1.0f - ag*0.75f, 1.0f - ag*0.75f, 1.0f - ag*0.75f, 1.0f);
            GLES10.glDrawArrays(GLES10.GL_TRIANGLE_FAN, 0, 4);

            // clean up
            GLES10.glDisableClientState(GLES10.GL_VERTEX_ARRAY);
        }
    }

    private static void setVStretchQuad(FloatBuffer vtx, float dw, float dh, float a,
            boolean swap) {
        final float w;
        final float h;
        if (swap) {
            w = dw - (dw * a);
            h = dh + (dh * a);
        } else {
            w = dw + (dw * a);
            h = dh - (dh * a);
        }
        final float x = (dw - w) * 0.5f;
        final float y = (dh - h) * 0.5f;
        setQuad(vtx, x, y, w, h);
    }

    private static void setHStretchQuad(FloatBuffer vtx, float dw, float dh, float a,
            boolean swap) {
        final float w;
        final float h;
        if (swap) {
            w = 1.0f;
            h = 2 * dh * (1.0f - a);
        } else {
            w = 2 * dw * (1.0f - a);
            h = 1.0f;
        }
        final float x = (dw - w) * 0.5f;
        final float y = (dh - h) * 0.5f;
        setQuad(vtx, x, y, w, h);
    }

    private static void setQuad(FloatBuffer vtx, float x, float y, float w, float h) {
        if (DEBUG) {
            Slog.d(TAG, "setQuad: x=" + x + ", y=" + y + ", w=" + w + ", h=" + h);
        }
        vtx.put(0, x);
        vtx.put(1, y);
        vtx.put(2, x);
        vtx.put(3, y + h);
        vtx.put(4, x + w);
        vtx.put(5, y + h);
        vtx.put(6, x + w);
        vtx.put(7, y);
    }

    private boolean captureScreenshotTextureAndSetViewport() {
        if (!attachEglContext()) {
            return false;
        }
        try {
            if (!mTexNamesGenerated) {
                GLES10.glGenTextures(1, mTexNames, 0);
                if (checkGlErrors("glGenTextures")) {
                    return false;
                }
                mTexNamesGenerated = true;
            }

            final SurfaceTexture st = new SurfaceTexture(mTexNames[0]);
            final Surface s = new Surface(st);
            try {
                SurfaceControl.screenshot(SurfaceControl.getBuiltInDisplay(
                        SurfaceControl.BUILT_IN_DISPLAY_ID_MAIN), s);
            } finally {
                s.release();
            }

            st.updateTexImage();
            st.getTransformMatrix(mTexMatrix);

            // Set up texture coordinates for a quad.
            // We might need to change this if the texture ends up being
            // a different size from the display for some reason.
            mTexCoordBuffer.put(0, 0f); mTexCoordBuffer.put(1, 0f);
            mTexCoordBuffer.put(2, 0f); mTexCoordBuffer.put(3, 1f);
            mTexCoordBuffer.put(4, 1f); mTexCoordBuffer.put(5, 1f);
            mTexCoordBuffer.put(6, 1f); mTexCoordBuffer.put(7, 0f);

            // Set up our viewport.
            GLES10.glViewport(0, 0, mDisplayWidth, mDisplayHeight);
            GLES10.glMatrixMode(GLES10.GL_PROJECTION);
            GLES10.glLoadIdentity();
            GLES10.glOrthof(0, mDisplayWidth, 0, mDisplayHeight, 0, 1);
            GLES10.glMatrixMode(GLES10.GL_MODELVIEW);
            GLES10.glLoadIdentity();
            GLES10.glMatrixMode(GLES10.GL_TEXTURE);
            GLES10.glLoadIdentity();
            GLES10.glLoadMatrixf(mTexMatrix, 0);
        } finally {
            detachEglContext();
        }
        return true;
    }

    private void destroyScreenshotTexture() {
        if (mTexNamesGenerated) {
            mTexNamesGenerated = false;
            if (attachEglContext()) {
                try {
                    GLES10.glDeleteTextures(1, mTexNames, 0);
                    checkGlErrors("glDeleteTextures");
                } finally {
                    detachEglContext();
                }
            }
        }
    }

    private boolean createEglContext() {
        if (mEglDisplay == null) {
            mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (mEglDisplay == EGL14.EGL_NO_DISPLAY) {
                logEglError("eglGetDisplay");
                return false;
            }

            int[] version = new int[2];
            if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
                mEglDisplay = null;
                logEglError("eglInitialize");
                return false;
            }
        }

        if (mEglConfig == null) {
            int[] eglConfigAttribList = new int[] {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE
            };
            int[] numEglConfigs = new int[1];
            EGLConfig[] eglConfigs = new EGLConfig[1];
            if (!EGL14.eglChooseConfig(mEglDisplay, eglConfigAttribList, 0,
                    eglConfigs, 0, eglConfigs.length, numEglConfigs, 0)) {
                logEglError("eglChooseConfig");
                return false;
            }
            mEglConfig = eglConfigs[0];
        }

        if (mEglContext == null) {
            int[] eglContextAttribList = new int[] {
                    EGL14.EGL_NONE
            };
            mEglContext = EGL14.eglCreateContext(mEglDisplay, mEglConfig,
                    EGL14.EGL_NO_CONTEXT, eglContextAttribList, 0);
            if (mEglContext == null) {
                logEglError("eglCreateContext");
                return false;
            }
        }
        return true;
    }

    /* not used because it is too expensive to create / destroy contexts all of the time
    private void destroyEglContext() {
        if (mEglContext != null) {
            if (!EGL14.eglDestroyContext(mEglDisplay, mEglContext)) {
                logEglError("eglDestroyContext");
            }
            mEglContext = null;
        }
    }*/

    private boolean createSurface() {
        if (mSurfaceSession == null) {
            mSurfaceSession = new SurfaceSession();
        }

        SurfaceControl.openTransaction();
        try {
            if (mSurfaceControl == null) {
                try {
                    int flags;
                    if (mMode == MODE_FADE) {
                        flags = SurfaceControl.FX_SURFACE_DIM | SurfaceControl.HIDDEN;
                    } else {
                        flags = SurfaceControl.OPAQUE | SurfaceControl.HIDDEN;
                    }
                    mSurfaceControl = new SurfaceControl(mSurfaceSession,
                            "ElectronBeam", mDisplayWidth, mDisplayHeight,
                            PixelFormat.OPAQUE, flags);
                } catch (OutOfResourcesException ex) {
                    Slog.e(TAG, "Unable to create surface.", ex);
                    return false;
                }
            }

            mSurfaceControl.setLayerStack(mDisplayLayerStack);
            mSurfaceControl.setSize(mDisplayWidth, mDisplayHeight);
            mSurface = new Surface();
            mSurface.copyFrom(mSurfaceControl);

            mSurfaceLayout = new NaturalSurfaceLayout(mDisplayManager, mSurfaceControl,
                    mHWRotation);
            mSurfaceLayout.onDisplayTransaction();
        } finally {
            SurfaceControl.closeTransaction();
        }
        return true;
    }

    private boolean createEglSurface() {
        if (mEglSurface == null) {
            int[] eglSurfaceAttribList = new int[] {
                    EGL14.EGL_NONE
            };
            // turn our SurfaceControl into a Surface
            mEglSurface = EGL14.eglCreateWindowSurface(mEglDisplay, mEglConfig, mSurface,
                    eglSurfaceAttribList, 0);
            if (mEglSurface == null) {
                logEglError("eglCreateWindowSurface");
                return false;
            }
        }
        return true;
    }

    private void destroyEglSurface() {
        if (mEglSurface != null) {
            if (!EGL14.eglDestroySurface(mEglDisplay, mEglSurface)) {
                logEglError("eglDestroySurface");
            }
            mEglSurface = null;
        }
    }

    private void destroySurface() {
        if (mSurfaceControl != null) {
            mSurfaceLayout.dispose();
            mSurfaceLayout = null;
            SurfaceControl.openTransaction();
            try {
                mSurfaceControl.destroy();
                mSurface.release();
            } finally {
                SurfaceControl.closeTransaction();
            }
            mSurfaceControl = null;
            mSurfaceVisible = false;
            mSurfaceAlpha = 0f;
        }
    }

    private boolean showSurface(float alpha) {
        if (!mSurfaceVisible || mSurfaceAlpha != alpha) {
            SurfaceControl.openTransaction();
            try {
                mSurfaceControl.setLayer(ELECTRON_BEAM_LAYER);
                mSurfaceControl.setAlpha(alpha);
                mSurfaceControl.show();
            } finally {
                SurfaceControl.closeTransaction();
            }
            mSurfaceVisible = true;
            mSurfaceAlpha = alpha;
        }
        return true;
    }

    private boolean attachEglContext() {
        if (mEglSurface == null) {
            return false;
        }
        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            logEglError("eglMakeCurrent");
            return false;
        }
        return true;
    }

    private void detachEglContext() {
        if (mEglDisplay != null) {
            EGL14.eglMakeCurrent(mEglDisplay,
                    EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        }
    }

    /**
     * Interpolates a value in the range 0 .. 1 along a sigmoid curve
     * yielding a result in the range 0 .. 1 scaled such that:
     * scurve(0) == 0, scurve(0.5) == 0.5, scurve(1) == 1.
     */
    private static float scurve(float value, float s) {
        // A basic sigmoid has the form y = 1.0f / FloatMap.exp(-x * s).
        // Here we take the input datum and shift it by 0.5 so that the
        // domain spans the range -0.5 .. 0.5 instead of 0 .. 1.
        final float x = value - 0.5f;

        // Next apply the sigmoid function to the scaled value
        // which produces a value in the range 0 .. 1 so we subtract
        // 0.5 to get a value in the range -0.5 .. 0.5 instead.
        final float y = sigmoid(x, s) - 0.5f;

        // To obtain the desired boundary conditions we need to scale
        // the result so that it fills a range of -1 .. 1.
        final float v = sigmoid(0.5f, s) - 0.5f;

        // And finally remap the value back to a range of 0 .. 1.
        return y / v * 0.5f + 0.5f;
    }

    private static float sigmoid(float x, float s) {
        return 1.0f / (1.0f + FloatMath.exp(-x * s));
    }

    private static FloatBuffer createNativeFloatBuffer(int size) {
        ByteBuffer bb = ByteBuffer.allocateDirect(size * 4);
        bb.order(ByteOrder.nativeOrder());
        return bb.asFloatBuffer();
    }

    private static void logEglError(String func) {
        Slog.e(TAG, func + " failed: error " + EGL14.eglGetError(), new Throwable());
    }

    private static boolean checkGlErrors(String func) {
        return checkGlErrors(func, true);
    }

    private static boolean checkGlErrors(String func, boolean log) {
        boolean hadError = false;
        int error;
        while ((error = GLES10.glGetError()) != GLES10.GL_NO_ERROR) {
            if (log) {
                Slog.e(TAG, func + " failed: error " + error, new Throwable());
            }
            hadError = true;
        }
        return hadError;
    }

    public void dump(PrintWriter pw) {
        pw.println();
        pw.println("Electron Beam State:");
        pw.println("  mPrepared=" + mPrepared);
        pw.println("  mMode=" + mMode);
        pw.println("  mDisplayLayerStack=" + mDisplayLayerStack);
        pw.println("  mDisplayWidth=" + mDisplayWidth);
        pw.println("  mDisplayHeight=" + mDisplayHeight);
        pw.println("  mSurfaceVisible=" + mSurfaceVisible);
        pw.println("  mSurfaceAlpha=" + mSurfaceAlpha);
    }

    /**
     * Keeps a surface aligned with the natural orientation of the device.
     * Updates the position and transformation of the matrix whenever the display
     * is rotated.  This is a little tricky because the display transaction
     * callback can be invoked on any thread, not necessarily the thread that
     * owns the electron beam.
     */
    private static final class NaturalSurfaceLayout implements DisplayTransactionListener {
        private final DisplayManagerService mDisplayManager;
        private SurfaceControl mSurfaceControl;
        private final int mHWRotation;

        public NaturalSurfaceLayout(DisplayManagerService displayManager,
                SurfaceControl surfaceControl, int hwRotation) {
            mDisplayManager = displayManager;
            mSurfaceControl = surfaceControl;
            mDisplayManager.registerDisplayTransactionListener(this);
            mHWRotation = hwRotation;
        }

        public void dispose() {
            synchronized (this) {
                mSurfaceControl = null;
            }
            mDisplayManager.unregisterDisplayTransactionListener(this);
        }

        @Override
        public void onDisplayTransaction() {
            synchronized (this) {
                if (mSurfaceControl == null) {
                    return;
                }

                DisplayInfo displayInfo = mDisplayManager.getDisplayInfo(Display.DEFAULT_DISPLAY);
                switch ((displayInfo.rotation + mHWRotation) % 4) {
                    case Surface.ROTATION_0:
                        mSurfaceControl.setPosition(0, 0);
                        mSurfaceControl.setMatrix(1, 0, 0, 1);
                        break;
                    case Surface.ROTATION_90:
                        mSurfaceControl.setPosition(0, displayInfo.logicalHeight);
                        mSurfaceControl.setMatrix(0, -1, 1, 0);
                        break;
                    case Surface.ROTATION_180:
                        mSurfaceControl.setPosition(displayInfo.logicalWidth, displayInfo.logicalHeight);
                        mSurfaceControl.setMatrix(-1, 0, 0, -1);
                        break;
                    case Surface.ROTATION_270:
                        mSurfaceControl.setPosition(displayInfo.logicalWidth, 0);
                        mSurfaceControl.setMatrix(0, 1, -1, 0);
                        break;
                }
            }
        }
    }
}
