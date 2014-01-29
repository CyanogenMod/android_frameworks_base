/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 CyanogenMod Project
 * Copyright (C) 2013 The SlimRoms Project
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

package com.android.systemui.quicksettings;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.Animator.AnimatorListener;
import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class QuickSettingsTile implements OnClickListener {

    protected final Context mContext;
    protected QuickSettingsContainerView mContainer;
    protected QuickSettingsTileView mTile;
    protected OnClickListener mOnClick;
    protected OnLongClickListener mOnLongClick;
    protected final int mTileLayout;
    protected int mDrawable;
    protected String mLabel;
    protected int mTileTextSize;
    protected int mTileTextColor;
    protected int mTileTextPadding;
    protected Drawable mRealDrawable;
    protected boolean mGenericCollapse;

    protected PhoneStatusBar mStatusbarService;
    protected QuickSettingsController mQsc;
    protected SharedPreferences mPrefs;

    private Handler mHandler = new Handler();

    public QuickSettingsTile(Context context, QuickSettingsController qsc) {
        this(context, qsc, R.layout.quick_settings_tile_basic);
    }

    public QuickSettingsTile(Context context, QuickSettingsController qsc, int layout) {
        mContext = context;
        mGenericCollapse = true;
        mDrawable = R.drawable.ic_notifications;
        mRealDrawable = null;
        mLabel = mContext.getString(R.string.quick_settings_label_enabled);
        mStatusbarService = qsc.mStatusBarService;
        mQsc = qsc;
        mTileLayout = layout;
        mPrefs = mContext.getSharedPreferences("quicksettings", Context.MODE_PRIVATE);
    }

    public void setupQuickSettingsTile(LayoutInflater inflater,
            QuickSettingsContainerView container) {
        container.updateResources();
        mTileTextSize = container.getTileTextSize();
        mTileTextColor = container.getTileTextColor();
        mTileTextPadding = container.getTileTextPadding();

        mTile = (QuickSettingsTileView) inflater.inflate(
                R.layout.quick_settings_tile, container, false);
        mTile.setContent(mTileLayout, inflater);
        mContainer = container;
        mContainer.addView(mTile);
        onPostCreate();
        updateQuickSettings();
        mTile.setOnClickListener(this);
        mTile.setOnLongClickListener(mOnLongClick);
    }

    void onPostCreate() {}

    public void onDestroy() {}

    public void onReceive(Context context, Intent intent) {}

    public void onChangeUri(ContentResolver resolver, Uri uri) {}

    public void updateResources() {
        if (mTile != null) {
            updateQuickSettings();
        }
    }

    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        if (tv != null) {
            tv.setText(mLabel);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTileTextSize);
            tv.setPadding(0, mTileTextPadding, 0, 0);
            if (mTileTextColor != -2) {
                tv.setTextColor(mTileTextColor);
            }
        }
        ImageView image = (ImageView) mTile.findViewById(R.id.image);
        if (image != null) {
            if (mRealDrawable == null) {
                image.setImageResource(mDrawable);
            } else {
                image.setImageDrawable(mRealDrawable);
            }
        }
    }

    public boolean isFlipTilesEnabled() {
        return (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QUICK_SETTINGS_TILES_FLIP, 1) == 1);
    }

    public void flipTile(int delay){
        final AnimatorSet anim = (AnimatorSet) AnimatorInflater.loadAnimator(
                mContext, R.anim.flip_right);
        anim.setTarget(mTile);
        anim.setDuration(200);
        anim.addListener(new AnimatorListener(){

            @Override
            public void onAnimationEnd(Animator animation) {}
            @Override
            public void onAnimationStart(Animator animation) {}
            @Override
            public void onAnimationCancel(Animator animation) {}
            @Override
            public void onAnimationRepeat(Animator animation) {}

        });

        Runnable doAnimation = new Runnable(){
            @Override
            public void run() {
                anim.start();
            }
        };

        mHandler.postDelayed(doAnimation, delay);
    }

    void startSettingsActivity(String action) {
        Intent intent = new Intent(action);
        startSettingsActivity(intent);
    }

    void startSettingsActivity(Intent intent) {
        startSettingsActivity(intent, true);
    }

    private void startSettingsActivity(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !mStatusbarService.isDeviceProvisioned()) return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        mStatusbarService.animateCollapsePanels();
    }

    @Override
    public void onClick(View v) {
        if (mOnClick != null) {
            mOnClick.onClick(v);
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            boolean shouldCollapse = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QS_COLLAPSE_PANEL, 0, UserHandle.USER_CURRENT) == 1;
            // mGenericCollapse overrides this method on tiles
            // where collapsing on click should not be optional
            if (shouldCollapse && mGenericCollapse) {
                mQsc.mBar.collapseAllPanels(true);
            }
        }
    }
}
