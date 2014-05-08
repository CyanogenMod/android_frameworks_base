/*
 * Copyright (C) 2010 ParanoidAndroid Project
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

package com.android.systemui.statusbar.view;

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.ScrollView;

import java.util.ArrayList;

import com.android.systemui.R;
import com.android.systemui.statusbar.NotificationData;
import com.android.systemui.statusbar.PieControlPanel;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.NotificationRowLayout;

public class PieStatusPanel {

    public static final int NOTIFICATIONS_PANEL = 0;
    public static final int QUICK_SETTINGS_PANEL = 1;

    private static Context mContext;
    private static View mContentHeader;
    private static ScrollView mScrollView;
    private View mClearButton;
    private View mContentFrame;
    private View mHaloButton;
    private View mQuickSettingsButton;
    private View mNotificationButton;
    private static QuickSettingsContainerView mQS;
    private static NotificationRowLayout mNotificationPanel;
    private static PieControlPanel mPanel;
    private static ViewGroup[] mPanelParents = new ViewGroup[2];

    private Handler mHandler = new Handler();
    private NotificationData mNotificationData;
    private Runnable mPostCollapseCleanup = null;

    private boolean mHaloActive, mHaloEnabled;

    private static int mCurrentViewState = -1;
    private int mFlipViewState = -1;

    public PieStatusPanel(Context context, PieControlPanel panel) {
        mContext = context;
        mPanel = panel;

        mNotificationPanel = mPanel.getBar().getNotificationRowLayout();
        mNotificationPanel.setTag(NOTIFICATIONS_PANEL);
        mQS = mPanel.getBar().getQuickSettingsPanel();
        mQS.setTag(QUICK_SETTINGS_PANEL);

        mPanelParents[NOTIFICATIONS_PANEL] = (ViewGroup) mNotificationPanel.getParent();
        mPanelParents[QUICK_SETTINGS_PANEL] = (ViewGroup) mQS.getParent();

        mContentHeader = (View) mPanel.getBar().mContainer.findViewById(R.id.content_header);

        mContentFrame = (View) mPanel.getBar().mContainer.findViewById(R.id.content_frame);
        mScrollView = (ScrollView) mPanel.getBar().mContainer.findViewById(R.id.content_scroll);
        mScrollView.setOnTouchListener(new ViewOnTouchListener());
        mContentFrame.setOnTouchListener(new ViewOnTouchListener());

        mNotificationData = mPanel.getBar().getNotificationData();
        mClearButton = (ImageView) mPanel.getBar().mContainer.findViewById(R.id.clear_all_button);
        mClearButton.setOnClickListener(mClearButtonListener);

        mHaloButton = (ImageView) mPanel.getBar().mContainer.findViewById(R.id.halo_button);
        if (mHaloButton != null) {
            mHaloButton.setOnClickListener(mHaloButtonListener);
        }

        mQuickSettingsButton = (ImageView) mPanel.getBar().mContainer.findViewById(R.id.quicksettings_button);
        if (mQuickSettingsButton != null) {
            mQuickSettingsButton.setOnClickListener(mQuickSettingsButtonListener);
        }

        mNotificationButton = (ImageView) mPanel.getBar().mContainer.findViewById(R.id.notification_button);
        if (mNotificationButton != null) {
            mNotificationButton.setOnClickListener(mNotificationButtonListener);
        }

        // Listen for HALO state for PIE
        mContext.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.HALO_ACTIVE), false, new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                showHaloButton(true);
            }});

        mPanel.getBar().mContainer.setVisibility(View.GONE);
    }

    private View.OnClickListener mHaloButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            // Activate HALO
            Settings.System.putIntForUser(mContext.getContentResolver(),
                    Settings.System.HALO_ACTIVE, 1, UserHandle.USER_CURRENT);
            hidePanels(true);
        }
    };

    private View.OnClickListener mQuickSettingsButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            setCurrentViewState(PieStatusPanel.QUICK_SETTINGS_PANEL);
            showTilesPanel();
            mNotificationButton.setVisibility(View.VISIBLE);
        }
    };


    private View.OnClickListener mNotificationButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            setCurrentViewState(PieStatusPanel.NOTIFICATIONS_PANEL);
            showNotificationsPanel();
            mQuickSettingsButton.setVisibility(View.VISIBLE);
        }
    };

    protected void showHaloButton(boolean show) {
        if (mHaloButton != null) {
            if(show) {
                mHaloActive = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.HALO_ACTIVE, 0, UserHandle.USER_CURRENT) == 1;
                mHaloEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.HALO_ENABLED, 0, UserHandle.USER_CURRENT) == 1;
                mHaloButton.setVisibility(!mHaloActive && mHaloEnabled ? View.VISIBLE : View.GONE);
            } else {
                mHaloButton.setVisibility(View.GONE);
            }
        }
    }

    protected void showQuickSettingsButton(boolean show) {
        if (mQuickSettingsButton != null) {
            mQuickSettingsButton.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (mNotificationButton != null) {
            mNotificationButton.setVisibility(!show ? View.VISIBLE : View.GONE);
        }
    }

    protected void showNotificationButton(boolean show) {
        if (mNotificationButton != null) {
            mNotificationButton.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (mQuickSettingsButton != null) {
            mQuickSettingsButton.setVisibility(!show ? View.VISIBLE : View.GONE);
        }
    }

    class ViewOnTouchListener implements OnTouchListener {
        final int SCROLLING_DISTANCE_TRIGGER = 100;
            float scrollX;
            float scrollY;
            boolean hasScrolled;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        scrollX = event.getX();
                        scrollY = event.getY();
                        hasScrolled = false;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float distanceY = Math.abs(event.getY() - scrollY);
                        float distanceX = Math.abs(event.getX() - scrollX);
                        if(distanceY > SCROLLING_DISTANCE_TRIGGER ||
                            distanceX > SCROLLING_DISTANCE_TRIGGER) {
                            hasScrolled = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if(!hasScrolled) {
                            ResetPanels(true);
                        }
                        break;
                }
                return false;
            }
    }

    private View.OnClickListener mClearButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            synchronized (mNotificationData) {
                // animate-swipe all dismissable notifications, then animate the shade closed
                int numChildren = mNotificationPanel.getChildCount();

                int scrollTop = mScrollView.getScrollY();
                int scrollBottom = scrollTop + mScrollView.getHeight();
                final ArrayList<View> snapshot = new ArrayList<View>(numChildren);
                for (int i=0; i<numChildren; i++) {
                    final View child = mNotificationPanel.getChildAt(i);
                    if (mNotificationPanel.canChildBeDismissed(child) && child.getBottom() > scrollTop &&
                            child.getTop() < scrollBottom) {
                        snapshot.add(child);
                    }
                }
                if (snapshot.isEmpty()) {
                    return;
                }
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        // Decrease the delay for every row we animate to give the sense of
                        // accelerating the swipes
                        final int ROW_DELAY_DECREMENT = 10;
                        int currentDelay = 140;
                        int totalDelay = 0;

                        // Set the shade-animating state to avoid doing other work during
                        // all of these animations. In particular, avoid layout and
                        // redrawing when collapsing the shade.
                        mNotificationPanel.setViewRemoval(false);

                        mPostCollapseCleanup = new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mNotificationPanel.setViewRemoval(true);
                                    mPanel.getBar().getService().onClearAllNotifications();
                                } catch (Exception ex) { }
                            }
                        };

                        View sampleView = snapshot.get(0);
                        int width = sampleView.getWidth();
                        final int velocity = width * 8; // 1000/8 = 125 ms duration
                        for (final View _v : snapshot) {
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    mNotificationPanel.dismissRowAnimated(_v, velocity);
                                }
                            }, totalDelay);
                            currentDelay = Math.max(50, currentDelay - ROW_DELAY_DECREMENT);
                            totalDelay += currentDelay;
                        }
                        // Delay the collapse animation until after all swipe animations have
                        // finished. Provide some buffer because there may be some extra delay
                        // before actually starting each swipe animation. Ideally, we'd
                        // synchronize the end of those animations with the start of the collaps
                        // exactly.
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mPostCollapseCleanup.run();
                                hidePanels(true);
                            }
                        }, totalDelay + 225);
                    }
                }).start();
            }
        }
    };

    public int getFlipViewState() {
        return mFlipViewState;
    }

    public void setFlipViewState(int state) {
        mFlipViewState = state;
    }

    public int getCurrentViewState() {
        return mCurrentViewState;
    }

    public void setCurrentViewState(int state) {
        mCurrentViewState = state;
    }

    public void hidePanels(boolean reset) {
        if (mCurrentViewState == NOTIFICATIONS_PANEL) {
            hidePanel(mNotificationPanel);
        } else if (mCurrentViewState == QUICK_SETTINGS_PANEL) {
            hidePanel(mQS);
        }
        if (reset) mCurrentViewState = -1;
    }

    public static void ResetPanels(boolean reset) {
        if (mNotificationPanel != null && mQS != null) {
            hidePanel(mNotificationPanel);
            hidePanel(mQS);
        }
        if (reset) mCurrentViewState = -1;
    }

    public void swapPanels() {
        hidePanels(false);
        if (mCurrentViewState == NOTIFICATIONS_PANEL) {
            mCurrentViewState = QUICK_SETTINGS_PANEL;
            showPanel(mQS);
        } else if (mCurrentViewState == QUICK_SETTINGS_PANEL) {
            mCurrentViewState = NOTIFICATIONS_PANEL;
            showPanel(mNotificationPanel);
        }
    }

    private static ViewGroup getPanelParent(View panel) {
        if (((Integer)panel.getTag()).intValue() == NOTIFICATIONS_PANEL) {
            return mPanelParents[NOTIFICATIONS_PANEL];
        } else {
            return mPanelParents[QUICK_SETTINGS_PANEL];
        }
    }

    public void showTilesPanel() {
        showPanel(mQS);
        ShowClearAll(false);
        showHaloButton(false);
        showNotificationButton(true);
    }

    public void showNotificationsPanel() {
        showPanel(mNotificationPanel);
        //check show ClearAll Button
        boolean any = mNotificationData.size() > 0;
        boolean clearable = any && mNotificationData.hasClearableItems();
        ShowClearAll(clearable);
        showHaloButton(true);
        showQuickSettingsButton(true);
    }

    public void hideTilesPanel() {
        hidePanel(mQS);
    }

    public void hideNotificationsPanel() {
        hidePanel(mNotificationPanel);
    }

    private void showPanel(View panel) {
        mContentFrame.setBackgroundColor(0);
        ValueAnimator alphAnimation = ValueAnimator.ofInt(0, 1);
        alphAnimation.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mScrollView.setX(-(int)((1-animation.getAnimatedFraction()) * mPanel.getWidth() * 1.5));
                mContentFrame.setBackgroundColor((int)(animation.getAnimatedFraction() * 0xEE) << 24);
                mPanel.invalidate();
            }
        });
        alphAnimation.setDuration(600);
        alphAnimation.setInterpolator(new DecelerateInterpolator());
        alphAnimation.start();

        AlphaAnimation alphaUp = new AlphaAnimation(0, 1);
        alphaUp.setFillAfter(true);
        alphaUp.setDuration(1000);
        mContentHeader.startAnimation(alphaUp);

        ViewGroup parent = getPanelParent(panel);
        parent.removeAllViews();
        mScrollView.removeAllViews();
        mScrollView.addView(panel);
        updateContainer(true);
    }

    public static void hidePanel(View panel) {
        ViewGroup parent = getPanelParent(panel);
        mScrollView.removeAllViews();
        parent.removeAllViews();
        parent.addView(panel, panel.getLayoutParams());
        updateContainer(false);
    }

    private static void updateContainer(boolean visible) {
        mPanel.getBar().mContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        updatePanelConfiguration();
    }

    public static void updatePanelConfiguration() {
        int padding = mContext.getResources().getDimensionPixelSize(R.dimen.pie_panel_padding);
        mScrollView.setPadding(padding,0,padding,0);
        mContentHeader.setPadding(padding,0,padding,0);
    }

    private void ShowClearAll(boolean show){
        mClearButton.setAlpha(show ? 1.0f : 0.0f);
        mClearButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public static WindowManager.LayoutParams getFlipPanelLayoutParams() {
        return new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
    }
}
