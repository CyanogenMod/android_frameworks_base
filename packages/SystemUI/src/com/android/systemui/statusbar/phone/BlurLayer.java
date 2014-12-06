/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.systemui.statusbar.phone;

import android.graphics.PixelFormat;
import android.util.Slog;
import android.view.SurfaceControl;
import android.view.SurfaceSession;

import java.io.PrintWriter;

public class BlurLayer {
    private static final String TAG = "BlurLayer";
    private static final boolean DEBUG = true;
    private SurfaceControl mBlurSurface;
    private int mLayer = -1;
    private float mAlpha = 0;
    private float mBlur = 0;
    private int mX, mY;
    private int mW, mH;
    private boolean mIsShow;

    public BlurLayer(SurfaceSession mFxSession, int w, int h, String tag) {
        this(mFxSession, 0, 0, w, h, tag);
    }

    public BlurLayer(SurfaceSession mFxSession, int x, int y, int w, int h, String tag) {
        mX = x;
        mY = y;
        mW = w;
        mH = h;
        mIsShow = false;

        SurfaceControl.openTransaction();
        try {
            mBlurSurface = new SurfaceControl(mFxSession, TAG+"_"+tag, 16, 16, PixelFormat.OPAQUE,
                SurfaceControl.FX_SURFACE_BLUR | SurfaceControl.HIDDEN);
            mBlurSurface.setLayerStack(0);
            mBlurSurface.setPosition(mX, mY);
            mBlurSurface.setSize(mW, mH);
        } catch (Exception e) {
            Slog.e(TAG, "Exception creating BlurLayer surface", e);
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    public void setSize(int w, int h) {
        if (mBlurSurface != null && (mW != w || mH != h) ) {
            SurfaceControl.openTransaction();
            try {
                mBlurSurface.setSize(w, h);
                mW = w;
                mH = h;
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure setting setSize immediately", e);
            } catch (Exception e) {
                Slog.e(TAG, "Exception setSize", e);
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    public void setPosition(int x, int y) {
        if (mBlurSurface != null && (mX != x || mY != y) ) {
            SurfaceControl.openTransaction();
            try {
                mBlurSurface.setPosition(x, y);
                mX = x;
                mY = y;
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure setting setPosition immediately", e);
            } catch (Exception e) {
                Slog.e(TAG, "Exception setPosition", e);
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    public void setLayer(int layer) {
        if (mBlurSurface != null && mLayer != layer) {
            SurfaceControl.openTransaction();
            try {
                mBlurSurface.setLayer(layer);
                mLayer = layer;
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure setting setLayer immediately", e);
            } catch (Exception e) {
                Slog.e(TAG, "Exception setLayer", e);
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    public void setAlpha(float alpha){
        if(mBlurSurface != null && mAlpha != alpha){
            SurfaceControl.openTransaction();
            try {
                mBlurSurface.setAlpha(alpha);
                mAlpha = alpha;
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure setting alpha immediately", e);
            } catch (Exception e) {
                Slog.e(TAG, "Exception setAlpha", e);
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    public void setBlur(float blur){
        if(mBlurSurface != null &&  mBlur != blur ){
            SurfaceControl.openTransaction();
            try {
                mBlurSurface.setBlur(blur);
                mBlur = blur;
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure setting blur immediately", e);
            } catch (Exception e) {
                Slog.e(TAG, "Exception setBlur", e);
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    public void show() {
        if(mBlurSurface != null &&  !mIsShow ){
            try {
                mBlurSurface.show();
                mIsShow = true;
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure show()", e);
            } catch (Exception e) {
                Slog.e(TAG, "Exception show()", e);
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    public void hide(){
        if(mBlurSurface != null &&  mIsShow ){
            try {
                mBlurSurface.hide();
                mIsShow = false;
            } catch (RuntimeException e) {
                Slog.w(TAG, "Failure hide()", e);
            } catch (Exception e) {
                Slog.e(TAG, "Exception hide()", e);
            } finally {
                SurfaceControl.closeTransaction();
            }
        }
    }

    public void destroySurface() {
        if (DEBUG) Slog.v(TAG, "destroySurface.");
        if (mBlurSurface != null) {
            mBlurSurface.destroy();
            mBlurSurface = null;
        }
    }

}

