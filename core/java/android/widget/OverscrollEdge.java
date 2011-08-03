/*
 * Copyright (C) 2010 The Android Open Source Project
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
package android.widget;

import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.android.internal.R;

/**
 * This class override the glow effect color used at the edges of scrollable widgets.
 * @hide
 */
public class OverscrollEdge extends EdgeGlow{
    WeakReference<Context> mContext;
    private static int mOverscrollEffect;

    public OverscrollEdge(Drawable edge, Drawable glow, Context context) {
        super(edge,glow);
        mContext = new WeakReference<Context>(context);
        registerReceivers();
    }

    private void registerReceivers() {
        Context cContext = mContext.get();
        if (cContext!=null){
            Uri uri = Settings.System.getUriFor(Settings.System.OVERSCROLL_COLOR);
            cContext.getContentResolver().registerContentObserver(uri, true, mContentObserverColor);
            mContentObserverColor.onChange(true);
            uri = Settings.System.getUriFor(Settings.System.OVERSCROLL_EFFECT);
            cContext.getContentResolver().registerContentObserver(uri, true, mContentObserverEffect);
            mContentObserverEffect.onChange(true);
        }
    }

    public void unregisterReceivers() {
        Context cContext = mContext.get();
        if (cContext!=null){
            cContext.getContentResolver().unregisterContentObserver(mContentObserverColor);
            cContext.getContentResolver().unregisterContentObserver(mContentObserverEffect);
        }
    }

    public static int getmOverscrollEffect() {
        return mOverscrollEffect;
    }

    private ContentObserver mContentObserverColor = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Context cContext = mContext.get();
            if (cContext != null){
                Resources res = cContext.getResources();
                int mOverscrollColor = Settings.System.getInt(cContext.getContentResolver(),
                        Settings.System.OVERSCROLL_COLOR,0);
                if (mOverscrollColor != 0){
                    mEdge = res.getDrawable(R.drawable.overscroll_edge_white);
                    mGlow = res.getDrawable(R.drawable.overscroll_glow_white);
                    mEdge.setColorFilter(mOverscrollColor, Mode.MULTIPLY);
                    mGlow.setColorFilter(mOverscrollColor, Mode.MULTIPLY);
                }else{
                    mEdge = res.getDrawable(R.drawable.overscroll_edge);
                    mGlow = res.getDrawable(R.drawable.overscroll_glow);
                }
            }
        }
    };

    private ContentObserver mContentObserverEffect = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            Context cContext = mContext.get();
            if (cContext != null){
                mOverscrollEffect = Settings.System.getInt(cContext.getContentResolver(),
                        Settings.System.OVERSCROLL_EFFECT, View.OVER_SCROLL_SETTING_EDGEGLOW);
            }
        }
    };
}

