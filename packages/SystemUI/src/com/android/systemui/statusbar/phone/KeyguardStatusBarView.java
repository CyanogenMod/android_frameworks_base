/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.systemui.BatteryMeterDrawable;
import com.android.systemui.BatteryMeterView;
import com.android.systemui.Interpolators;
import com.android.systemui.R;
import com.android.systemui.qs.QSPanel;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.KeyguardUserSwitcher;
import com.android.systemui.statusbar.policy.UserInfoController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.tuner.TunerService;

import cyanogenmod.providers.CMSettings;

import java.text.NumberFormat;

/**
 * The header group on Keyguard.
 */
public class KeyguardStatusBarView extends RelativeLayout
        implements BatteryController.BatteryStateChangeCallback, TunerService.Tunable {

    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT =
            "cmsystem:" + CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT;
    private static final String STATUS_BAR_BATTERY_STYLE =
            "cmsystem:" + CMSettings.System.STATUS_BAR_BATTERY_STYLE;

    private boolean mBatteryCharging;
    private boolean mKeyguardUserSwitcherShowing;
    private boolean mBatteryListening;

    private TextView mCarrierLabel;
    private View mSystemIconsSuperContainer;
    private MultiUserSwitch mMultiUserSwitch;
    private ImageView mMultiUserAvatar;
    private TextView mBatteryLevel;

    private BatteryController mBatteryController;
    private KeyguardUserSwitcher mKeyguardUserSwitcher;
    private UserSwitcherController mUserSwitcherController;

    private int mSystemIconsSwitcherHiddenExpandedMargin;
    private int mSystemIconsBaseMargin;
    private View mSystemIconsContainer;

    private boolean mShowBatteryText;
    private Boolean mForceBatteryText;

    public KeyguardStatusBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSystemIconsSuperContainer = findViewById(R.id.system_icons_super_container);
        mSystemIconsContainer = findViewById(R.id.system_icons_container);
        mMultiUserSwitch = (MultiUserSwitch) findViewById(R.id.multi_user_switch);
        mMultiUserAvatar = (ImageView) findViewById(R.id.multi_user_avatar);
        mBatteryLevel = (TextView) findViewById(R.id.battery_level);
        mCarrierLabel = (TextView) findViewById(R.id.keyguard_carrier_text);
        loadDimens();
        updateUserSwitcher();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        MarginLayoutParams lp = (MarginLayoutParams) mMultiUserAvatar.getLayoutParams();
        lp.width = lp.height = getResources().getDimensionPixelSize(
                R.dimen.multi_user_avatar_keyguard_size);
        mMultiUserAvatar.setLayoutParams(lp);

        lp = (MarginLayoutParams) mMultiUserSwitch.getLayoutParams();
        lp.width = getResources().getDimensionPixelSize(
                R.dimen.multi_user_switch_width_keyguard);
        lp.setMarginEnd(getResources().getDimensionPixelSize(
                R.dimen.multi_user_switch_keyguard_margin));
        mMultiUserSwitch.setLayoutParams(lp);

        lp = (MarginLayoutParams) mSystemIconsSuperContainer.getLayoutParams();
        lp.height = getResources().getDimensionPixelSize(
                R.dimen.status_bar_header_height);
        lp.setMarginStart(getResources().getDimensionPixelSize(
                R.dimen.system_icons_super_container_margin_start));
        mSystemIconsSuperContainer.setLayoutParams(lp);
        mSystemIconsSuperContainer.setPaddingRelative(mSystemIconsSuperContainer.getPaddingStart(),
                mSystemIconsSuperContainer.getPaddingTop(),
                getResources().getDimensionPixelSize(R.dimen.system_icons_keyguard_padding_end),
                mSystemIconsSuperContainer.getPaddingBottom());

        lp = (MarginLayoutParams) mSystemIconsContainer.getLayoutParams();
        lp.height = getResources().getDimensionPixelSize(
                R.dimen.status_bar_height);
        mSystemIconsContainer.setLayoutParams(lp);

        lp = (MarginLayoutParams) mBatteryLevel.getLayoutParams();
        lp.setMarginStart(
                getResources().getDimensionPixelSize(R.dimen.header_battery_margin_keyguard));
        mBatteryLevel.setLayoutParams(lp);
        mBatteryLevel.setPaddingRelative(mBatteryLevel.getPaddingStart(),
                mBatteryLevel.getPaddingTop(),
                getResources().getDimensionPixelSize(R.dimen.battery_level_padding_end),
                mBatteryLevel.getPaddingBottom());
        mBatteryLevel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.battery_level_text_size));

        // Respect font size setting.
        mCarrierLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(
                        com.android.internal.R.dimen.text_size_small_material));
        lp = (MarginLayoutParams) mCarrierLabel.getLayoutParams();
        lp.setMarginStart(
                getResources().getDimensionPixelSize(R.dimen.keyguard_carrier_text_margin));
        mCarrierLabel.setLayoutParams(lp);

        lp = (MarginLayoutParams) getLayoutParams();
        lp.height =  getResources().getDimensionPixelSize(
                R.dimen.status_bar_header_height_keyguard);
        setLayoutParams(lp);
    }

    private void loadDimens() {
        Resources res = getResources();
        mSystemIconsSwitcherHiddenExpandedMargin = res.getDimensionPixelSize(
                R.dimen.system_icons_switcher_hidden_expanded_margin);
        mSystemIconsBaseMargin = res.getDimensionPixelSize(
                R.dimen.system_icons_super_container_avatarless_margin_end);
    }

    private void updateVisibilities() {
        if (mMultiUserSwitch.getParent() != this && !mKeyguardUserSwitcherShowing) {
            if (mMultiUserSwitch.getParent() != null) {
                getOverlay().remove(mMultiUserSwitch);
            }
            addView(mMultiUserSwitch, 0);
        } else if (mMultiUserSwitch.getParent() == this && mKeyguardUserSwitcherShowing) {
            removeView(mMultiUserSwitch);
        }
        if (mKeyguardUserSwitcher == null) {
            // If we have no keyguard switcher, the screen width is under 600dp. In this case,
            // we don't show the multi-user avatar unless there is more than 1 user on the device.
            if (mUserSwitcherController != null
                    && mUserSwitcherController.getSwitchableUserCount() > 1) {
                mMultiUserSwitch.setVisibility(View.VISIBLE);
            } else {
                mMultiUserSwitch.setVisibility(View.GONE);
            }
        }
        if (mForceBatteryText != null) {
            mBatteryLevel.setVisibility(mForceBatteryText ? View.VISIBLE : View.GONE);
        } else {
            mBatteryLevel.setVisibility(
                    mBatteryCharging || mShowBatteryText ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSystemIconsLayoutParams() {
        RelativeLayout.LayoutParams lp =
                (LayoutParams) mSystemIconsSuperContainer.getLayoutParams();
        // If the avatar icon is gone, we need to have some end margin to display the system icons
        // correctly.
        int baseMarginEnd = mMultiUserSwitch.getVisibility() == View.GONE
                ? mSystemIconsBaseMargin
                : 0;
        int marginEnd = mKeyguardUserSwitcherShowing ? mSystemIconsSwitcherHiddenExpandedMargin :
                baseMarginEnd;
        if (marginEnd != lp.getMarginEnd()) {
            lp.setMarginEnd(marginEnd);
            mSystemIconsSuperContainer.setLayoutParams(lp);
        }
    }

    public void setListening(boolean listening) {
        if (listening == mBatteryListening) {
            return;
        }
        mBatteryListening = listening;
        if (mBatteryListening) {
            TunerService.get(getContext()).addTunable(this,
                    STATUS_BAR_SHOW_BATTERY_PERCENT, STATUS_BAR_BATTERY_STYLE);
            mBatteryController.addStateChangedCallback(this);
        } else {
            mBatteryController.removeStateChangedCallback(this);
            TunerService.get(getContext()).removeTunable(this);
        }
    }

    private void updateUserSwitcher() {
        boolean keyguardSwitcherAvailable = mKeyguardUserSwitcher != null;
        mMultiUserSwitch.setClickable(keyguardSwitcherAvailable);
        mMultiUserSwitch.setFocusable(keyguardSwitcherAvailable);
        mMultiUserSwitch.setKeyguardMode(keyguardSwitcherAvailable);
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        ((BatteryMeterView) findViewById(R.id.battery)).setBatteryController(batteryController);
    }

    public void setUserSwitcherController(UserSwitcherController controller) {
        mUserSwitcherController = controller;
        mMultiUserSwitch.setUserSwitcherController(controller);
    }

    public void setUserInfoController(UserInfoController userInfoController) {
        userInfoController.addListener(new UserInfoController.OnUserInfoChangedListener() {
            @Override
            public void onUserInfoChanged(String name, Drawable picture) {
                mMultiUserAvatar.setImageDrawable(picture);
            }
        });
    }

    public void setQSPanel(QSPanel qsp) {
        mMultiUserSwitch.setQsPanel(qsp);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        String percentage = NumberFormat.getPercentInstance().format((double) level / 100.0);
        mBatteryLevel.setText(percentage);
        boolean changed = mBatteryCharging != charging;
        mBatteryCharging = charging;
        if (changed) {
            updateVisibilities();
        }
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        // could not care less
    }

    public void setKeyguardUserSwitcher(KeyguardUserSwitcher keyguardUserSwitcher) {
        mKeyguardUserSwitcher = keyguardUserSwitcher;
        mMultiUserSwitch.setKeyguardUserSwitcher(keyguardUserSwitcher);
        updateUserSwitcher();
    }

    public void setKeyguardUserSwitcherShowing(boolean showing, boolean animate) {
        mKeyguardUserSwitcherShowing = showing;
        if (animate) {
            animateNextLayoutChange();
        }
        updateVisibilities();
        updateSystemIconsLayoutParams();
    }

    private void animateNextLayoutChange() {
        final int systemIconsCurrentX = mSystemIconsSuperContainer.getLeft();
        final boolean userSwitcherVisible = mMultiUserSwitch.getParent() == this;
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);
                boolean userSwitcherHiding = userSwitcherVisible
                        && mMultiUserSwitch.getParent() != KeyguardStatusBarView.this;
                mSystemIconsSuperContainer.setX(systemIconsCurrentX);
                mSystemIconsSuperContainer.animate()
                        .translationX(0)
                        .setDuration(400)
                        .setStartDelay(userSwitcherHiding ? 300 : 0)
                        .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                        .start();
                if (userSwitcherHiding) {
                    getOverlay().add(mMultiUserSwitch);
                    mMultiUserSwitch.animate()
                            .alpha(0f)
                            .setDuration(300)
                            .setStartDelay(0)
                            .setInterpolator(Interpolators.ALPHA_OUT)
                            .withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    mMultiUserSwitch.setAlpha(1f);
                                    getOverlay().remove(mMultiUserSwitch);
                                }
                            })
                            .start();

                } else {
                    mMultiUserSwitch.setAlpha(0f);
                    mMultiUserSwitch.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .setStartDelay(200)
                            .setInterpolator(Interpolators.ALPHA_IN);
                }
                return true;
            }
        });

    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != View.VISIBLE) {
            mSystemIconsSuperContainer.animate().cancel();
            mMultiUserSwitch.animate().cancel();
            mMultiUserSwitch.setAlpha(1f);
        } else {
            updateVisibilities();
            updateSystemIconsLayoutParams();
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case STATUS_BAR_SHOW_BATTERY_PERCENT:
                mShowBatteryText = newValue != null && Integer.parseInt(newValue) == 2;
                break;
            case STATUS_BAR_BATTERY_STYLE:
                if (newValue != null) {
                    final int value = Integer.parseInt(newValue);
                    if (value == BatteryMeterDrawable.BATTERY_STYLE_TEXT) {
                        mForceBatteryText = true;
                    } else if (value == BatteryMeterDrawable.BATTERY_STYLE_HIDDEN) {
                        mForceBatteryText = false;
                    } else {
                        mForceBatteryText = null;
                    }
                }
                break;
        }
        updateVisibilities();
    }
}
