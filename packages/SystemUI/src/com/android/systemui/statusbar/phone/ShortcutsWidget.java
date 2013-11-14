/*
 * Copyright (C) 2013 SlimRoms (blk_jack)
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

package com.android.systemui.statusbar.phone;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.internal.util.slim.ButtonsHelper;
import com.android.internal.util.slim.ButtonConfig;
import com.android.internal.util.slim.ButtonsConstants;
import com.android.internal.util.slim.ImageHelper;

import com.android.systemui.R;

import java.io.File;
import java.util.ArrayList;

public class ShortcutsWidget extends LinearLayout {
    static final String TAG = "ShortcutsWidget";

    private int mViewWidth = 0;
    private int mTmpMargin = 0;
    private int mOldMargin = 0;
    private int mGetPxPadding;
    private int mDefaultMargin = 5; //px
    private int mDefaultChildSizeDp = 46; //dp
    private int mDefaultChildSizePx; //px
    private int mDefaultChildWidth; //px

    private int mChildCount;
    private boolean mActive;
    private int mColorizeMode;
    private int mColor;

    private boolean mOverflow;

    private Context mContext;
    private Handler mHandler;
    private LayoutInflater mInflater;

    private ArrayList<ButtonConfig> mButtonsConfig;

    private View.OnClickListener mExternalClickListener;
    private View.OnLongClickListener mExternalLongClickListener;

    private final OnGlobalLayoutListener mOnGlobalLayoutListener = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            modifyShortcutLayout();
            ShortcutsWidget.this.getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
    };

    public ShortcutsWidget(Context context) {
        super(context);
    }

    public ShortcutsWidget(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mHandler = new Handler();
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mDefaultChildSizePx = (int) convertDpToPixel(mDefaultChildSizeDp, mContext);
        mDefaultChildWidth = mDefaultChildSizePx + (mDefaultMargin * 2); //px

        updateShortcuts();
    }

    public void updateShortcuts() {
        ContentResolver resolver = mContext.getContentResolver();

        mButtonsConfig = ButtonsHelper.getNotificationsShortcutConfig(mContext);
        mChildCount = mButtonsConfig.size();
        mActive = mChildCount > 0;

        if (mActive) {
            mColor = Settings.System.getIntForUser(resolver,
                    Settings.System.NOTIFICATION_SHORTCUTS_COLOR, -2,
                    UserHandle.USER_CURRENT);
            if (mColor == -2) {
                mColor = mContext.getResources().getColor(R.color.notification_shortcut_color);
            }
            mColorizeMode = Settings.System.getIntForUser(resolver,
                    Settings.System.NOTIFICATION_SHORTCUTS_COLOR_MODE,
                    0, UserHandle.USER_CURRENT);
            recreateShortcutLayout();
        } else {
            removeAllViews();
        }
    }

    public boolean hasAppBinded() {
        return mActive;
    }

    private void modifyShortcutLayout() {
        // Check if width has changed
        int tmpWidth = getContWidth();
        if (mChildCount == 0 || tmpWidth == 0) {
            return;
        } else if (mViewWidth != tmpWidth) {
            mViewWidth = tmpWidth;
            if (mActive) {
                if (determineMargins()) {
                    modifyMargins();
                } else {
                    recreateShortcutLayout();
                }
            } else {
                removeAllViews();
            }
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
    }

    public void setupShortcuts() {
        destroyShortcuts();
        recreateShortcutLayout();
    }

    private void destroyShortcuts() {
        try {
            removeAllViews();
        } catch (Exception e) {
        }
    }

    private static float convertDpToPixel(float dp, Context context){
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float px = dp * (metrics.densityDpi / 160f);
        return px;
    }

    private int getContWidth() {
        HorizontalScrollView shortcutScroll = (HorizontalScrollView) ShortcutsWidget.this.getParent();
        if (shortcutScroll != null) {
            return shortcutScroll.getWidth();
        }
        return 0;
    }

    private boolean determineMargins() {
        int scrollViewWidth = getContWidth();
        if (scrollViewWidth == 0 || mChildCount == 0) {
            return false;
        }

        // Divide width of container by children
        int shortcutWidth = scrollViewWidth / mChildCount;
        // If this number is less than the minimum child width..
        if (shortcutWidth < mDefaultChildWidth) {
            mOverflow = true;
            // Uh oh, we have overscroll!
            // Round down to figure out how many can fit.
            int tmpCount = scrollViewWidth / mDefaultChildWidth;
            // Get padding for each side
            mTmpMargin = ((scrollViewWidth - (tmpCount * mDefaultChildWidth)) / tmpCount) / 2;
        } else {
            mOverflow = false;
            mTmpMargin = (shortcutWidth - mDefaultChildWidth) / 2;
        }
        return true;
    }

    private void modifyMargins() {
        if (mOldMargin == mTmpMargin) {
            return;
        }
        for (int j = 0; j < mChildCount; j++) {
            View iv = getChildAt(j);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
            lp.setMargins(mDefaultMargin + mTmpMargin, 0,
                mDefaultMargin + mTmpMargin, mGetPxPadding);
        }
        mOldMargin = mTmpMargin;
        invalidate();
        requestLayout();
    }

    public void recreateShortcutLayout() {
        removeAllViews();
        determineMargins();
        buildShortcuts();
    }

    private void buildShortcuts() {
        ButtonConfig buttonConfig;
        for (int i = 0; i < mChildCount; i++) {
            buttonConfig = mButtonsConfig.get(i);

            try {
                final Intent in = Intent.parseUri(buttonConfig.getClickAction(), 0);
                Drawable front = ButtonsHelper.getButtonIconImage(
                    mContext, buttonConfig.getClickAction(), buttonConfig.getIcon());

                // Draw ImageView
                ImageView iv = new ImageView(mContext);

                // colorize system and gallery icons always and app icons only if toggled
                if (((buttonConfig.getIcon() != null
                            && buttonConfig.getIcon()
                            .startsWith(ButtonsConstants.SYSTEM_ICON_IDENTIFIER))
                        || mColorizeMode == 0
                        || (buttonConfig.getIcon() != null
                            && !buttonConfig.getIcon().equals(ButtonsConstants.ICON_EMPTY)
                            && mColorizeMode != 1))
                        && mColorizeMode != 3) {
                        iv.setImageBitmap(ImageHelper.getColoredBitmap(front, mColor));
                    } else {
                        iv.setImageDrawable(front);
                    }

                mGetPxPadding = (int) convertDpToPixel(5, mContext);
                iv.setPadding(mGetPxPadding, mGetPxPadding, mGetPxPadding, mGetPxPadding);
                LinearLayout.LayoutParams layoutParams =
                    new LinearLayout.LayoutParams(mDefaultChildSizePx, mDefaultChildSizePx);
                int determinedMargin = mDefaultMargin + mTmpMargin;
                layoutParams.setMargins(determinedMargin, 0, determinedMargin, mGetPxPadding);

                iv.setLayoutParams(layoutParams);
                iv.setBackgroundResource(R.drawable.notification_shortcut_bg);
                iv.setLongClickable(true);
                iv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                        } catch (RemoteException e) {
                        }
                        if (mExternalClickListener != null) {
                            mExternalClickListener.onClick(v);
                        }
                        try {
                            Intent i = in;
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            v.getContext().startActivity(i);
                        } catch (Exception e) {
                        }
                    }
                });
                iv.setOnLongClickListener(mShortcutLongClickListener);
                addView(iv);
            } catch (Exception e) {
            }
        }
    }

    private View.OnLongClickListener mShortcutLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            // disabled temporaly will be replace with another feature later
            return true;
        }
    };

    public void setGlobalButtonOnClickListener(View.OnClickListener listener) {
        mExternalClickListener = listener;
    }

    public void setGlobalButtonOnLongClickListener(View.OnLongClickListener listener) {
        mExternalLongClickListener = listener;
    }
}
