/*
 * Copyright (C) 2011 The Android Open Source Project
 * This code has been modified.  Portions copyright (C) 2010 ParanoidAndroid Project
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

package com.android.systemui.statusbar;

import android.app.ActionBar.Tab;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MotionEvent;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import com.android.systemui.statusbar.PieControlPanel;
import com.android.systemui.statusbar.view.PieItem;
import com.android.systemui.statusbar.view.PieMenu;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller for Quick Controls pie menu
 */
public class PieControl implements OnClickListener {
    public static final String BACK_BUTTON = "##back##";
    public static final String HOME_BUTTON = "##home##";
    public static final String MENU_BUTTON = "##menu##";
    public static final String SEARCH_BUTTON = "##search##";
    public static final String RECENT_BUTTON = "##recent##";
    public static final String APP_WINDOW_BUTTON = "##appwindow##";
    public static final String ACT_NOTIF_BUTTON = "##actnotif##";
    public static final String ACT_QS_BUTTON = "##actqs##";
    public static final String LAST_APP_BUTTON = "##lastapp##";
    public static final String KILL_TASK_BUTTON = "##killtask##";
    public static final String POWER_BUTTON = "##power##";
    public static final String SCREENSHOT_BUTTON = "##screenshot##";
    public static final String TORCH_BUTTON = "##torch##";
    public static final String GESTURE_BUTTON = "##gesture##";

    protected Context mContext;
    protected PieMenu mPie;
    protected int mItemSize;
    protected TextView mTabsCount;
    private PieItem mBack;
    private PieItem mHome;
    private PieItem mMenu;
    private PieItem mRecent;
    private PieItem mAppWindow;
    private PieItem mActNotif;
    private PieItem mActQs;
    private PieItem mLastApp;
    private PieItem mKillTask;
    private PieItem mPower;
    private PieItem mSearch;
    private PieItem mScreenShot;
    private PieItem mTorch;
    private PieItem mGesture;
    private OnNavButtonPressedListener mListener;
    private PieControlPanel mPanel;

    private boolean mIsAssistantAvailable;

    public PieControl(Context context, PieControlPanel panel) {
        mContext = context;
        mPanel = panel;
        mItemSize = (int) context.getResources().getDimension(R.dimen.pie_item_size);
    }

    public PieMenu getPieMenu() {
        return mPie;
    }

    public void init() {
        mPie.init();
    }

    public void onPieConfigurationChanged() {
        if (mPie != null) mPie.onPieConfigurationChanged();
    }

    public void attachToContainer(FrameLayout container) {
        if (mPie == null) {
            mPie = new PieMenu(mContext, mPanel);
            LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            mPie.setLayoutParams(lp);
            populateMenu();
        }
        container.addView(mPie);
    }

    public void removeFromContainer(FrameLayout container) {
        container.removeView(mPie);
    }

    public void forceToTop(FrameLayout container) {
        if (mPie.getParent() != null) {
            container.removeView(mPie);
            container.addView(mPie);
        }
    }

    protected void setIsAssistantAvailable(boolean isAvailable) {
        mIsAssistantAvailable = isAvailable;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return mPie.onTouchEvent(event);
    }

    public void populateMenu() {
        mBack = makeItem(R.drawable.ic_sysbar_back, 1, BACK_BUTTON, false);
        mHome = makeItem(R.drawable.ic_sysbar_home, 1, HOME_BUTTON, false);
        mRecent = makeItem(R.drawable.ic_sysbar_recent, 1, RECENT_BUTTON, false);
        mMenu = makeItem(R.drawable.ic_sysbar_menu, 1, MENU_BUTTON, true);
        mActNotif = makeItem(R.drawable.ic_sysbar_notifications_pie, 1, ACT_NOTIF_BUTTON, true);
        mActQs = makeItem(R.drawable.ic_sysbar_quicksettings_pie, 1, ACT_QS_BUTTON, true);
        mAppWindow = makeItem(R.drawable.ic_sysbar_appwindow_pie, 1, APP_WINDOW_BUTTON, true);
        mLastApp = makeItem(R.drawable.ic_sysbar_lastapp_side, 1, LAST_APP_BUTTON, true);
        mKillTask = makeItem(R.drawable.ic_sysbar_killtask_pie, 1, KILL_TASK_BUTTON, true);
        mPower = makeItem(R.drawable.ic_sysbar_power, 1, POWER_BUTTON, true);
        mScreenShot = makeItem(R.drawable.ic_sysbar_screenshot_pie, 1, SCREENSHOT_BUTTON, true);
        mTorch = makeItem(R.drawable.ic_sysbar_torch, 1, TORCH_BUTTON, true);
        mGesture = makeItem(R.drawable.ic_sysbar_gesture, 1, GESTURE_BUTTON, true);
        mSearch = makeItem(R.drawable.ic_sysbar_search_side, 1, SEARCH_BUTTON, true);
        mPie.addItem(mMenu);  //Right End
        mPie.addItem(mSearch);
        mPie.addItem(mTorch);
        mPie.addItem(mActNotif);
        mPie.addItem(mActQs);
        mPie.addItem(mAppWindow);
        mPie.addItem(mKillTask);
        mPie.addItem(mLastApp);
        mPie.addItem(mPower);
        mPie.addItem(mScreenShot);
        mPie.addItem(mGesture);
        mPie.addItem(mRecent);
        mPie.addItem(mHome);
        mPie.addItem(mBack); // Left End
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onNavButtonPressed((String) v.getTag());
        }
    }

    protected PieItem makeItem(int image, int l, String name, boolean lesser) {
        ImageView view = new ImageView(mContext);
        view.setImageResource(image);
        view.setMinimumWidth(mItemSize);
        view.setMinimumHeight(mItemSize);
        view.setScaleType(ScaleType.CENTER);
        LayoutParams lp = new LayoutParams(mItemSize, mItemSize);
        view.setLayoutParams(lp);
        view.setOnClickListener(this);
        return new PieItem(view, mContext, l, name, lesser);
    }

    public void show(boolean show) {
        mPie.show(show);
    }

    public void setCenter(int x, int y) {
        mPie.setCenter(x, y);
    }

    public void setOnNavButtonPressedListener(OnNavButtonPressedListener listener) {
        mListener = listener;
    }

    public interface OnNavButtonPressedListener {
        public void onNavButtonPressed(String buttonName);
    }

}
