/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.StatusBarManager;
import android.animation.LayoutTransition;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.widget.ImageView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.LinearLayout;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;

import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DelegateViewHelper;
import com.android.systemui.recent.RecentsPanelView;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    final static boolean DEBUG_DEADZONE = false;

    final static boolean NAVBAR_ALWAYS_AT_RIGHT = true;

    final static boolean ANIMATE_HIDE_TRANSITION = false; // turned off because it introduces unsightly delay when videos goes to full screen
    final static String NAVBAR_EDIT = "android.intent.action.NAVBAR_EDIT";

    private static boolean EDIT_MODE;
    private NavbarEditor mEditBar;
    private NavBarReceiver mNavBarReceiver;
    private OnClickListener mRecentsClickListener;
    private RecentsPanelView mRecentsPanel;
    private OnTouchListener mHomeSearchActionListener;
    private boolean mIs600dp;

    protected IStatusBarService mBarService;
    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    int mBarSize;
    boolean mVertical;

    boolean mHidden, mLowProfile, mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private Drawable mBackIcon, mBackLandIcon, mBackAltIcon, mBackAltLandIcon;
    
    private DelegateViewHelper mDelegateHelper;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Slog.w(TAG, String.format(
                            "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                            how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    public void setDelegateView(View view) {
        mDelegateHelper.setDelegateView(view);
    }

    public void setBar(BaseStatusBar phoneStatusBar) {
        mDelegateHelper.setBar(phoneStatusBar);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDelegateHelper != null) {
            mDelegateHelper.onInterceptTouchEvent(event);
        }
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mDelegateHelper.onInterceptTouchEvent(event);
    }

    private H mHandler = new H();

    public static boolean getEditMode() {
        return EDIT_MODE;
    }

    protected void setListener(OnClickListener RecentsClickListener, RecentsPanelView RecentsPanel, OnTouchListener HomeSearchActionListener) {
        mRecentsClickListener = RecentsClickListener;
        mRecentsPanel = RecentsPanel;
        mHomeSearchActionListener = HomeSearchActionListener;
    }

    protected void toggleButtonListener(boolean enable) {
        View recentView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_RECENT);
        if (recentView != null) {
            recentView.setOnClickListener(enable ? mRecentsClickListener : null);
            recentView.setOnTouchListener(enable ? mRecentsPanel : null);
        }
        View homeView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_HOME);
        if (homeView != null) {
            homeView.setOnTouchListener(enable ? mHomeSearchActionListener : null);
        }
    }

    private void setButtonWithTagVisibility(String string, int visibility) {
        View findView = mCurrentView.findViewWithTag(string);
        if (findView != null) {
            findView.setVisibility(visibility);
        }
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mHidden = false;

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        final Resources res = mContext.getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        //Silly way to identify 600dp
        //TODO come up with a better way
        mIs600dp = res.getInteger(R.integer.notification_panel_layout_gravity) == 49;
        mDelegateHelper = new DelegateViewHelper(this);
        updateResources();

        mNavBarReceiver = new NavBarReceiver();
        mContext.registerReceiver(mNavBarReceiver, new IntentFilter(NAVBAR_EDIT));
    }

    protected void updateResources() {
        final Resources res = mContext.getResources();
        mBackIcon = res.getDrawable(R.drawable.ic_sysbar_back);
        mBackLandIcon = res.getDrawable(R.drawable.ic_sysbar_back_land);
        mBackAltIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime);
        mBackAltLandIcon = res.getDrawable(R.drawable.ic_sysbar_back_ime);
    }

    public class NavBarReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean edit = intent.getBooleanExtra("edit", false);
            boolean save = intent.getBooleanExtra("save", false);
            if (edit != EDIT_MODE) {
                EDIT_MODE = edit;
                if (EDIT_MODE) {
                    toggleButtonListener(false);
                    mEditBar.setupListeners();
                    mEditBar.updateKeys();
                } else {
                    mEditBar.dismissDialog();
                    if (save) {
                        mEditBar.saveKeys();
                    }
                    mEditBar.reInflate();
                    mEditBar = new NavbarEditor((ViewGroup) mCurrentView.findViewById(R.id.container), mVertical);
                    mEditBar.updateKeys();
                    toggleButtonListener(true);
                    if (save) {
                        mEditBar.updateLowLights(mCurrentView);
                    }
                    ((ViewGroup) mCurrentView.findViewById(R.id.mid_nav_buttons)).setLayoutTransition(
                            new LayoutTransition());
                }
            }
        }
    }

    View.OnTouchListener mLightsOutListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                // even though setting the systemUI visibility below will turn these views
                // on, we need them to come up faster so that they can catch this motion
                // event
                setLowProfile(false, false, false);

                try {
                    mBarService.setSystemUiVisibility(0, View.SYSTEM_UI_FLAG_LOW_PROFILE);
                } catch (android.os.RemoteException ex) {
                }
            }
            return false;
        }
    };

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;

        if (DEBUG) {
            android.widget.Toast.makeText(mContext,
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;

        View button = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_HOME);
        if (button != null) {
            button.setAlpha(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_HOME_NOP)) ? 0.5f : 1.0f);
        }
        button = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_RECENT);
        if (button != null) {
            button.setAlpha(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_RECENT_NOP)) ? 0.5f : 1.0f);
        }
        button = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_BACK);
        if (button != null) {
            button.setAlpha(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_BACK_NOP)) ? 0.5f : 1.0f);
            ((ImageView)button).setImageDrawable(
                    (0 != (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT))
                    ? (mVertical ? mBackAltLandIcon : mBackAltIcon)
                            : (mVertical ? mBackLandIcon : mBackIcon));
        }
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0);

        setSlippery(disableHome && disableRecent && disableBack);

        setButtonWithTagVisibility(NavbarEditor.NAVBAR_BACK, disableBack ? View.INVISIBLE : View.VISIBLE);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_HOME, disableHome ? View.INVISIBLE : View.VISIBLE);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_RECENT, disableRecent ? View.INVISIBLE : View.VISIBLE);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_RECENT, disableRecent ? View.INVISIBLE : View.VISIBLE);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_ALWAYS_MENU, disableRecent ? View.INVISIBLE : View.VISIBLE);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_MENU_BIG, disableRecent ? View.INVISIBLE : View.VISIBLE);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_SEARCH, disableRecent ? View.INVISIBLE : View.VISIBLE);

    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManagerImpl.getDefault().updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        mShowMenu = show;

        setButtonWithTagVisibility(NavbarEditor.NAVBAR_CONDITIONAL_MENU, mShowMenu ? View.VISIBLE : View.INVISIBLE);
    }

    public void setLowProfile(final boolean lightsOut) {
        setLowProfile(lightsOut, true, false);
    }

    public void setLowProfile(final boolean lightsOut, final boolean animate, final boolean force) {
        if (!force && lightsOut == mLowProfile) return;

        mLowProfile = lightsOut;

        if (DEBUG) Slog.d(TAG, "setting lights " + (lightsOut?"out":"on"));

        final View navButtons = mCurrentView.findViewById(R.id.nav_buttons);
        final View lowLights = mCurrentView.findViewById(R.id.lights_out);

        // ok, everyone, stop it right there
        navButtons.animate().cancel();
        lowLights.animate().cancel();

        if (!animate) {
            navButtons.setAlpha(lightsOut ? 0f : 1f);

            lowLights.setAlpha(lightsOut ? 1f : 0f);
            lowLights.setVisibility(lightsOut ? View.VISIBLE : View.GONE);
        } else {
            navButtons.animate()
                .alpha(lightsOut ? 0f : 1f)
                .setDuration(lightsOut ? 750 : 250)
                .start();

            lowLights.setOnTouchListener(mLightsOutListener);
            if (lowLights.getVisibility() == View.GONE) {
                lowLights.setAlpha(0f);
                lowLights.setVisibility(View.VISIBLE);
            }
            lowLights.animate()
                .alpha(lightsOut ? 1f : 0f)
                .setDuration(lightsOut ? 750 : 250)
                .setInterpolator(new AccelerateInterpolator(2.0f))
                .setListener(lightsOut ? null : new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator _a) {
                        lowLights.setVisibility(View.GONE);
                    }
                })
                .start();
        }
    }

    public void setHidden(final boolean hide) {
        if (hide == mHidden) return;

        mHidden = hide;
        Slog.d(TAG,
            (hide ? "HIDING" : "SHOWING") + " navigation bar");

        // bring up the lights no matter what
        setLowProfile(false);
    }

    @Override
    public void onFinishInflate() {
        mRotatedViews[Surface.ROTATION_0] = 
        mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);

        mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);
        
        mRotatedViews[Surface.ROTATION_270] = NAVBAR_ALWAYS_AT_RIGHT
                                                ? findViewById(R.id.rot90)
                                                : findViewById(R.id.rot270);

        mCurrentView = mRotatedViews[Surface.ROTATION_0];
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i=0; i<4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }
        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);
        if (!mIs600dp) {
            mVertical = (rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270);
        } else {
            mVertical = getWidth() > 0 && getHeight() > getWidth();
        }
        mEditBar = new NavbarEditor((ViewGroup) mCurrentView.findViewById(R.id.container), mVertical);
        mEditBar.updateKeys();
        mEditBar.updateLowLights(mCurrentView);
        toggleButtonListener(true);

        // force the low profile & disabled states into compliance
        setLowProfile(mLowProfile, false, true /* force */);
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);
        if (DEBUG_DEADZONE) {
            mCurrentView.findViewById(R.id.deadzone).setBackgroundColor(0x808080FF);
        }

        if (DEBUG) {
            Slog.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        setNavigationIconHints(mNavigationIconHints, true);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        ViewGroup mid_nav = (ViewGroup) mCurrentView.findViewById(R.id.mid_nav_buttons);
        View vViews[] = new View[mid_nav.getChildCount()];
        for (int cc = 0;cc < mid_nav.getChildCount(); cc++) {
            vViews[cc] = mid_nav.getChildAt(cc);
        }
        mDelegateHelper.setInitialTouchRegion(vViews);
    }



    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Slog.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)", 
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Slog.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */
        

    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > mDisplay.getRawWidth()
            || r.bottom > mDisplay.getRawHeight();
        pw.println("      window: " 
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s hidden=%s low=%s menu=%s",
                        mDisabledFlags,
                        mVertical ? "true" : "false",
                        mHidden ? "true" : "false",
                        mLowProfile ? "true" : "false",
                        mShowMenu ? "true" : "false"));

        final View back = mCurrentView.findViewWithTag("back");
        final View home = mCurrentView.findViewWithTag("home");
        final View recent = mCurrentView.findViewWithTag("recent");

        pw.println("      back: "
                + PhoneStatusBar.viewInfo(back)
                + " " + visibilityToString(back.getVisibility())
                );
        pw.println("      home: "
                + PhoneStatusBar.viewInfo(home)
                + " " + visibilityToString(home.getVisibility())
                );
        pw.println("      rcnt: "
                + PhoneStatusBar.viewInfo(recent)
                + " " + visibilityToString(recent.getVisibility())
                );
        pw.println("    }");
    }

}
