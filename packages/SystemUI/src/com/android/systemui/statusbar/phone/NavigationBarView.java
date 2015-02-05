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

import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DelegateViewHelper;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.KeyButtonView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    final static String NAVBAR_EDIT_ACTION = "android.intent.action.NAVBAR_EDIT";

    private boolean mInEditMode;
    private NavbarEditor mEditBar;
    private NavBarReceiver mNavBarReceiver;
    private OnClickListener mRecentsClickListener;
    private OnTouchListener mRecentsPreloadListener;
    private OnTouchListener mHomeSearchActionListener;
    private OnLongClickListener mRecentsBackListener;

    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];

    int mBarSize;
    boolean mVertical;
    boolean mScreenOn;
    boolean mLeftInLandscape;

    boolean mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private BackButtonDrawable mBackIcon, mBackLandIcon;
    private Drawable mRecentIcon;
    private Drawable mRecentLandIcon;
    private Drawable mHomeIcon, mHomeLandIcon;

    private NavigationBarViewTaskSwitchHelper mTaskSwitchHelper;
    private DelegateViewHelper mDelegateHelper;
    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;

    // Visibility of R.id.one view prior to swapping it for a left arrow key
    public int mSlotOneVisibility = -1;

    // Visibility of R.id.six view prior to swapping it for a right arrow key
    public int mSlotSixVisibility = -1;

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private Resources mThemedResources;

    private OnVerticalChangedListener mOnVerticalChangedListener;
    private boolean mIsLayoutRtl;
    private boolean mDelegateIntercepted;

    private SettingsObserver mSettingsObserver;
    private boolean mShowDpadArrowKeys;

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (NavbarEditor.NAVBAR_BACK.equals(view.getTag())) {
                mBackTransitioning = true;
            } else if (NavbarEditor.NAVBAR_HOME.equals(view.getTag()) && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = true;
                mStartDelay = transition.getStartDelay(transitionType);
                mDuration = transition.getDuration(transitionType);
                mInterpolator = transition.getInterpolator(transitionType);
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                View view, int transitionType) {
            if (NavbarEditor.NAVBAR_BACK.equals(view.getTag())) {
                mBackTransitioning = false;
            } else if (NavbarEditor.NAVBAR_HOME.equals(view.getTag()) && transitionType == LayoutTransition.APPEARING) {
                mHomeAppearing = false;
            }
        }

        public void onBackAltCleared() {
            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (!mBackTransitioning && getBackButton().getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton().getAlpha() == 0) {
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(getBackButton(), "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    private final OnClickListener mImeSwitcherClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            ((InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE))
                    .showInputMethodPicker();
        }
    };

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
                        Log.w(TAG, String.format(
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

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDisplay = ((WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        final Resources res = getContext().getResources();
        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        mDelegateHelper = new DelegateViewHelper(this);
        mTaskSwitchHelper = new NavigationBarViewTaskSwitchHelper(context);

        getIcons(res);

        mBarTransitions = new NavigationBarTransitions(this);

        mNavBarReceiver = new NavBarReceiver();
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        mContext.registerReceiverAsUser(mNavBarReceiver, UserHandle.ALL,
                new IntentFilter(NAVBAR_EDIT_ACTION), null, null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
        mContext.unregisterReceiver(mNavBarReceiver);
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setDelegateView(View view) {
        mDelegateHelper.setDelegateView(view);
    }

    public void setBar(BaseStatusBar phoneStatusBar) {
        mTaskSwitchHelper.setBar(phoneStatusBar);
        mDelegateHelper.setBar(phoneStatusBar);
    }

    public void setOnVerticalChangedListener(OnVerticalChangedListener onVerticalChangedListener) {
        mOnVerticalChangedListener = onVerticalChangedListener;
        notifyVerticalChangedListener(mVertical);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        initDownStates(event);
        if (!mDelegateIntercepted && mTaskSwitchHelper.onTouchEvent(event)) {
            return true;
        }
        if (mDeadZone != null && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mDeadZone.poke(event);
        }
        if (mDelegateHelper != null && mDelegateIntercepted) {
            boolean ret = mDelegateHelper.onInterceptTouchEvent(event);
            if (ret) return true;
        }
        return super.onTouchEvent(event);
    }

    private void initDownStates(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mDelegateIntercepted = false;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        initDownStates(event);
        boolean intercept = mTaskSwitchHelper.onInterceptTouchEvent(event);
        if (!intercept || mInEditMode) {
            mDelegateIntercepted = mDelegateHelper.onInterceptTouchEvent(event);
            intercept = mDelegateIntercepted;
        } else {
            MotionEvent cancelEvent = MotionEvent.obtain(event);
            cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
            mDelegateHelper.onInterceptTouchEvent(cancelEvent);
            cancelEvent.recycle();
        }
        return intercept;
    }

    private H mHandler = new H();

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getRecentsButton() {
        return mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_RECENT);
    }

    public View getMenuButton() {
        return mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_CONDITIONAL_MENU);
    }

    public View getBackButton() {
        return mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_BACK);
    }

    public View getHomeButton() {
        return mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_HOME);
    }

    public View getImeSwitchButton() {
        return mCurrentView.findViewById(R.id.ime_switcher);
    }

    private void getIcons(Resources res) {
        mBackIcon = new BackButtonDrawable(res.getDrawable(R.drawable.ic_sysbar_back));
        mBackLandIcon = new BackButtonDrawable(res.getDrawable(R.drawable.ic_sysbar_back_land));
        mRecentIcon = res.getDrawable(R.drawable.ic_sysbar_recent);
        mRecentLandIcon = res.getDrawable(R.drawable.ic_sysbar_recent_land);
        mHomeIcon = res.getDrawable(R.drawable.ic_sysbar_home);
        mHomeLandIcon = res.getDrawable(R.drawable.ic_sysbar_home_land);
    }

    public void updateResources(Resources res) {
        mThemedResources = res;
        getIcons(mThemedResources);
        mBarTransitions.updateResources(res);
        for (int i = 0; i < mRotatedViews.length; i++) {
            ViewGroup container = (ViewGroup) mRotatedViews[i];
            if (container != null) {
                updateLightsOutResources(container);
            }
        }
    }

    private void updateLightsOutResources(ViewGroup container) {
        ViewGroup lightsOut = (ViewGroup) container.findViewById(R.id.lights_out);
        if (lightsOut != null) {
            final int nChildren = lightsOut.getChildCount();
            for (int i = 0; i < nChildren; i++) {
                final View child = lightsOut.getChildAt(i);
                if (child instanceof ImageView) {
                    final ImageView iv = (ImageView) child;
                    // clear out the existing drawable, this is required since the
                    // ImageView keeps track of the resource ID and if it is the same
                    // it will not update the drawable.
                    iv.setImageDrawable(null);
                    iv.setImageDrawable(mThemedResources.getDrawable(
                            R.drawable.ic_sysbar_lights_out_dot_large));
                }
            }
        }
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        getIcons(mThemedResources != null ? mThemedResources : getContext().getResources());

        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        final boolean backAlt = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;
        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !backAlt) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(getContext(),
                "Navigation icon hints = " + hints,
                500).show();
        }

        mNavigationIconHints = hints;

        ((ImageView)getBackButton()).setImageDrawable(null);
        ((ImageView)getBackButton()).setImageDrawable(mVertical ? mBackLandIcon : mBackIcon);
        mBackLandIcon.setImeVisible(backAlt);
        mBackIcon.setImeVisible(backAlt);

        ((ImageView)getRecentsButton()).setImageDrawable(mVertical ? mRecentLandIcon : mRecentIcon);
        ((ImageView)getHomeButton()).setImageDrawable(mVertical ? mHomeLandIcon : mHomeIcon);

        final boolean showImeButton = ((hints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) != 0)
                && !mShowDpadArrowKeys;
        getImeSwitchButton().setVisibility(showImeButton ? View.VISIBLE : View.INVISIBLE);

        setDisabledFlags(mDisabledFlags, true);

        if (mShowDpadArrowKeys) { // overrides IME button
            final boolean showingIme = ((mNavigationIconHints
                    & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0);

            setVisibleOrGone(getCurrentView().findViewById(R.id.dpad_left), showingIme);
            setVisibleOrGone(getCurrentView().findViewById(R.id.dpad_right), showingIme);

            View one = getCurrentView().findViewById(mVertical ? R.id.six : R.id.one);
            View six = getCurrentView().findViewById(mVertical ? R.id.one : R.id.six);
            if (showingIme) {
                mSlotOneVisibility = one.getVisibility();
                mSlotSixVisibility = six.getVisibility();
                setVisibleOrGone(one, false);
                setVisibleOrGone(six, false);
            } else {
                if (mSlotOneVisibility != -1) {
                    one.setVisibility(mSlotOneVisibility);
                    mSlotOneVisibility = -1;
                }
                if (mSlotSixVisibility != -1) {
                    six.setVisibility(mSlotSixVisibility);
                    mSlotSixVisibility = -1;
                }
            }
        }
        // Update menu button in case the IME state has changed.
        setMenuVisibility(mShowMenu, true);
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }

        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
                if (!mScreenOn && mCurrentView != null) {
                    lt.disableTransitionType(
                            LayoutTransition.CHANGE_APPEARING |
                            LayoutTransition.CHANGE_DISAPPEARING |
                            LayoutTransition.APPEARING |
                            LayoutTransition.DISAPPEARING);
                }
            }
        }

        setButtonWithTagVisibility(NavbarEditor.NAVBAR_BACK, !disableBack);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_HOME, !disableHome);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_RECENT, !disableRecent);

        mBarTransitions.applyBackButtonQuiescentAlpha(mBarTransitions.getMode(), true /*animate*/);
    }

    private void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
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
            WindowManager wm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        mShowMenu = show;

        // Only show Menu if IME switcher not shown.
        final boolean shouldShow = mShowMenu &&
                ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0);
        final boolean shouldShowAlwaysMenu = (mNavigationIconHints &
                StatusBarManager.NAVIGATION_HINT_IME_SHOWN) == 0;
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_ALWAYS_MENU, shouldShowAlwaysMenu);
        setButtonWithTagVisibility(NavbarEditor.NAVBAR_CONDITIONAL_MENU, shouldShow);
    }

    @Override
    public void onFinishInflate() {
        mRotatedViews[Configuration.ORIENTATION_PORTRAIT] = findViewById(R.id.rot0);
        mRotatedViews[Configuration.ORIENTATION_LANDSCAPE] = findViewById(R.id.rot90);
        mCurrentView = mRotatedViews[mContext.getResources().getConfiguration().orientation];

        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        updateRTLOrder();
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void setLeftInLandscape(boolean leftInLandscape) {
        mLeftInLandscape = leftInLandscape;
        mDeadZone.setStartFromRight(leftInLandscape);
    }

    public void reorient() {
        int orientation = mContext.getResources().getConfiguration().orientation;
        mRotatedViews[Configuration.ORIENTATION_PORTRAIT].setVisibility(View.GONE);
        mRotatedViews[Configuration.ORIENTATION_LANDSCAPE].setVisibility(View.GONE);
        mCurrentView = mRotatedViews[orientation];
        mCurrentView.setVisibility(View.VISIBLE);
        if (NavbarEditor.isDevicePhone(mContext)) {
            int rotation = mDisplay.getRotation();
            mVertical = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        } else {
            mVertical = getWidth() > 0 && getHeight() > getWidth();
        }
        mEditBar = new NavbarEditor(mCurrentView, mVertical, mIsLayoutRtl);
        updateSettings();
        getImeSwitchButton().setOnClickListener(mImeSwitcherClickListener);

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);
        mDeadZone.setStartFromRight(mLeftInLandscape);

        // force the low profile & disabled states into compliance
        mBarTransitions.init(mVertical);
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        // swap to x coordinate if orientation is not in vertical
        if (mDelegateHelper != null) {
            mDelegateHelper.setSwapXY(mVertical);
        }
        updateTaskSwitchHelper();

        setNavigationIconHints(mNavigationIconHints, true);
    }

    private void updateTaskSwitchHelper() {
        boolean isRtl = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        mTaskSwitchHelper.setBarState(mVertical, isRtl);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mDelegateHelper.setInitialTouchRegion(getHomeButton(), getBackButton(), getRecentsButton());
        ViewGroup midNavButtons = (ViewGroup) mCurrentView.findViewById(R.id.mid_nav_buttons);
        int count = midNavButtons.getChildCount();
        View buttons[] = new View[count];

        for (int i = 0; i < count; i++) {
            buttons[i] = midNavButtons.getChildAt(i);
        }

        mDelegateHelper.setInitialTouchRegion(buttons);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
            notifyVerticalChangedListener(newVertical);
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    private void notifyVerticalChangedListener(boolean newVertical) {
        if (mOnVerticalChangedListener != null) {
            mOnVerticalChangedListener.onVerticalChanged(newVertical);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateRTLOrder();
        updateTaskSwitchHelper();
    }

    /**
     * In landscape, the LinearLayout is not auto mirrored since it is vertical. Therefore we
     * have to do it manually
     */
    private void updateRTLOrder() {
        boolean isLayoutRtl = getResources().getConfiguration()
                .getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (mIsLayoutRtl != isLayoutRtl) {
            mIsLayoutRtl = isLayoutRtl;
            reorient();
        }
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = getContext().getResources();
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
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                        + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                        getResourceName(mCurrentView.getId()),
                        mCurrentView.getWidth(), mCurrentView.getHeight(),
                        visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                        mDisabledFlags,
                        mVertical ? "true" : "false",
                        mShowMenu ? "true" : "false"));

        dumpButton(pw, "back", getBackButton());
        dumpButton(pw, "home", getHomeButton());
        dumpButton(pw, "rcnt", getRecentsButton());
        dumpButton(pw, "menu", getMenuButton());

        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, View button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(PhoneStatusBar.viewInfo(button)
                    + " " + visibilityToString(button.getVisibility())
                    + " alpha=" + button.getAlpha()
                    );
            if (button instanceof KeyButtonView) {
                pw.print(" drawingAlpha=" + ((KeyButtonView)button).getDrawingAlpha());
                pw.print(" quiescentAlpha=" + ((KeyButtonView)button).getQuiescentAlpha());
            }
        }
        pw.println();
    }

    public interface OnVerticalChangedListener {
        void onVerticalChanged(boolean isVertical);
    }

    void setListeners(OnClickListener recentsClickListener, OnTouchListener recentsPreloadListener,
                      OnLongClickListener recentsBackListener, OnTouchListener homeSearchActionListener) {
        mRecentsClickListener = recentsClickListener;
        mRecentsPreloadListener = recentsPreloadListener;
        mHomeSearchActionListener = homeSearchActionListener;
        mRecentsBackListener = recentsBackListener;
        updateButtonListeners();
    }

    private void removeButtonListeners() {
        ViewGroup container = (ViewGroup) mCurrentView.findViewById(R.id.container);
        int viewCount = container.getChildCount();
        for (int i = 0; i < viewCount; i++) {
            View button = container.getChildAt(i);
            if (button instanceof KeyButtonView) {
                button.setOnClickListener(null);
                button.setOnTouchListener(null);
            }
        }
    }

    protected void updateButtonListeners() {
        View recentView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_RECENT);
        if (recentView != null) {
            recentView.setOnClickListener(mRecentsClickListener);
            recentView.setOnTouchListener(mRecentsPreloadListener);
            recentView.setLongClickable(true);
            recentView.setOnLongClickListener(mRecentsBackListener);
        }
        View backView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_BACK);
        if (backView != null) {
            backView.setLongClickable(true);
            backView.setOnLongClickListener(mRecentsBackListener);
        }
        View homeView = mCurrentView.findViewWithTag(NavbarEditor.NAVBAR_HOME);
        if (homeView != null) {
            homeView.setOnTouchListener(mHomeSearchActionListener);
        }
    }

    public boolean isInEditMode() {
        return mInEditMode;
    }

    private void setButtonWithTagVisibility(Object tag, boolean visible) {
        View findView = mCurrentView.findViewWithTag(tag);
        if (findView == null) {
            return;
        }
        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        if (mSlotOneVisibility != -1 && findView.getId() == R.id.one) {
            mSlotOneVisibility = visibility;
        } else if (mSlotSixVisibility != -1 && findView.getId() == R.id.six) {
            mSlotSixVisibility = visibility;
        } else {
            findView.setVisibility(visibility);
        }
    }

    // TODO LINK TO THIS ONCE THEMES GOES IN
    protected void updateResources() {
        getIcons(mContext.getResources());
    }

    public class NavBarReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean edit = intent.getBooleanExtra("edit", false);
            boolean save = intent.getBooleanExtra("save", false);
            if (edit != mInEditMode) {
                mInEditMode = edit;
                if (edit) {
                    removeButtonListeners();
                    mEditBar.setEditMode(true);
                } else {
                    if (save) {
                        mEditBar.saveKeys();
                    }
                    mEditBar.setEditMode(false);
                    updateSettings();
                }
            }
        }
    }

    public void updateSettings() {
        mEditBar.updateKeys();
        removeButtonListeners();
        updateButtonListeners();
        setDisabledFlags(mDisabledFlags, true /* force */);
        setMenuVisibility(mShowMenu, true);
    }

    private class SettingsObserver extends UserContentObserver {

        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_MENU_ARROW_KEYS),
                    false, this, UserHandle.USER_ALL);

            // intialize mModlockDisabled
            onChange(false);
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        protected void update() {
            mShowDpadArrowKeys = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.NAVIGATION_BAR_MENU_ARROW_KEYS, 0, UserHandle.USER_CURRENT) != 0;
            mSlotOneVisibility = -1;
            mSlotSixVisibility = -1;
            setNavigationIconHints(mNavigationIconHints, true);
        }
    }
}
