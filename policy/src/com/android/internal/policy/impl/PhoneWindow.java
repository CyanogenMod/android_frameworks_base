/*
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

package com.android.internal.policy.impl;

import static android.view.View.MeasureSpec.AT_MOST;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.getMode;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR;
import static android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
import static android.view.WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;

import com.android.internal.view.RootViewSurfaceTaker;
import com.android.internal.view.StandaloneActionMode;
import com.android.internal.view.menu.ContextMenuBuilder;
import com.android.internal.view.menu.IconMenuPresenter;
import com.android.internal.view.menu.ListMenuPresenter;
import com.android.internal.view.menu.MenuBuilder;
import com.android.internal.view.menu.MenuDialogHelper;
import com.android.internal.view.menu.MenuPresenter;
import com.android.internal.view.menu.MenuView;
import com.android.internal.widget.ActionBarContainer;
import com.android.internal.widget.ActionBarContextView;
import com.android.internal.widget.ActionBarView;

import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.ActivityNotFoundException;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.util.AndroidRuntimeException;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.IRotationWatcher;
import android.view.IWindowManager;
import android.view.InputQueue;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.ViewParent;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.R;

/**
 * Android-specific Window.
 * <p>
 * todo: need to pull the generic functionality out into a base class
 * in android.widget.
 */
public class PhoneWindow extends Window implements MenuBuilder.Callback {

    private final static String TAG = "PhoneWindow";

    private final static boolean SWEEP_OPEN_MENU = false;

    /**
     * Simple callback used by the context menu and its submenus. The options
     * menu submenus do not use this (their behavior is more complex).
     */
    final DialogMenuCallback mContextMenuCallback = new DialogMenuCallback(FEATURE_CONTEXT_MENU);

    final TypedValue mMinWidthMajor = new TypedValue();
    final TypedValue mMinWidthMinor = new TypedValue();
    TypedValue mFixedWidthMajor;
    TypedValue mFixedWidthMinor;
    TypedValue mFixedHeightMajor;
    TypedValue mFixedHeightMinor;

    // This is the top-level view of the window, containing the window decor.
    private DecorView mDecor;

    // This is the view in which the window contents are placed. It is either
    // mDecor itself, or a child of mDecor where the contents go.
    private ViewGroup mContentParent;

    SurfaceHolder.Callback2 mTakeSurfaceCallback;
    
    InputQueue.Callback mTakeInputQueueCallback;
    
    private boolean mIsFloating;

    private LayoutInflater mLayoutInflater;

    private TextView mTitleView;
    
    private ActionBarView mActionBar;
    private ActionMenuPresenterCallback mActionMenuPresenterCallback;
    private PanelMenuPresenterCallback mPanelMenuPresenterCallback;

    private DrawableFeatureState[] mDrawables;

    private PanelFeatureState[] mPanels;

    /**
     * The panel that is prepared or opened (the most recent one if there are
     * multiple panels). Shortcuts will go to this panel. It gets set in
     * {@link #preparePanel} and cleared in {@link #closePanel}.
     */
    private PanelFeatureState mPreparedPanel;

    /**
     * The keycode that is currently held down (as a modifier) for chording. If
     * this is 0, there is no key held down.
     */
    private int mPanelChordingKey;

    private ImageView mLeftIconView;

    private ImageView mRightIconView;

    private ProgressBar mCircularProgressBar;

    private ProgressBar mHorizontalProgressBar;

    private int mBackgroundResource = 0;

    private Drawable mBackgroundDrawable;

    private int mFrameResource = 0;

    private int mTextColor = 0;

    private CharSequence mTitle = null;

    private int mTitleColor = 0;

    private boolean mAlwaysReadCloseOnTouchAttr = false;

    private boolean mEnableGestures;

    private Context mContext;
    private ContextMenuBuilder mContextMenu;
    private MenuDialogHelper mContextMenuHelper;
    private boolean mClosingActionMenu;

    private int mVolumeControlStreamType = AudioManager.USE_DEFAULT_STREAM_TYPE;

    private AudioManager mAudioManager;
    private KeyguardManager mKeyguardManager;

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.ENABLE_STYLUS_GESTURES), false,
                    this);
            checkGestures();
        }

        void unobserve() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            checkGestures();
        }

        void checkGestures() {
            mEnableGestures = Settings.System.getInt(
                    mContext.getContentResolver(),
                    Settings.System.ENABLE_STYLUS_GESTURES, 0) == 1;
        }
    }

    private int mUiOptions = 0;

    private boolean mInvalidatePanelMenuPosted;
    private int mInvalidatePanelMenuFeatures;
    private final Runnable mInvalidatePanelMenuRunnable = new Runnable() {
        @Override public void run() {
            for (int i = 0; i <= FEATURE_MAX; i++) {
                if ((mInvalidatePanelMenuFeatures & 1 << i) != 0) {
                    doInvalidatePanelMenu(i);
                }
            }
            mInvalidatePanelMenuPosted = false;
            mInvalidatePanelMenuFeatures = 0;
        }
    };

    static class WindowManagerHolder {
        static final IWindowManager sWindowManager = IWindowManager.Stub.asInterface(
                ServiceManager.getService("window"));
    }

    static final RotationWatcher sRotationWatcher = new RotationWatcher();

    public PhoneWindow(Context context) {
        super(context);
        mContext = context;
        mLayoutInflater = LayoutInflater.from(context);
    }

    @Override
    public final void setContainer(Window container) {
        super.setContainer(container);
    }

    @Override
    public boolean requestFeature(int featureId) {
        if (mContentParent != null) {
            throw new AndroidRuntimeException("requestFeature() must be called before adding content");
        }
        final int features = getFeatures();
        if ((features != DEFAULT_FEATURES) && (featureId == FEATURE_CUSTOM_TITLE)) {

            /* Another feature is enabled and the user is trying to enable the custom title feature */
            throw new AndroidRuntimeException("You cannot combine custom titles with other title features");
        }
        if (((features & (1 << FEATURE_CUSTOM_TITLE)) != 0) &&
                (featureId != FEATURE_CUSTOM_TITLE) && (featureId != FEATURE_ACTION_MODE_OVERLAY)) {

            /* Custom title feature is enabled and the user is trying to enable another feature */
            throw new AndroidRuntimeException("You cannot combine custom titles with other title features");
        }
        if ((features & (1 << FEATURE_NO_TITLE)) != 0 && featureId == FEATURE_ACTION_BAR) {
            return false; // Ignore. No title dominates.
        }
        if ((features & (1 << FEATURE_ACTION_BAR)) != 0 && featureId == FEATURE_NO_TITLE) {
            // Remove the action bar feature if we have no title. No title dominates.
            removeFeature(FEATURE_ACTION_BAR);
        }
        return super.requestFeature(featureId);
    }

    @Override
    public void setUiOptions(int uiOptions) {
        mUiOptions = uiOptions;
    }

    @Override
    public void setUiOptions(int uiOptions, int mask) {
        mUiOptions = (mUiOptions & ~mask) | (uiOptions & mask);
    }

    @Override
    public void setContentView(int layoutResID) {
        if (mContentParent == null) {
            installDecor();
        } else {
            mContentParent.removeAllViews();
        }
        mLayoutInflater.inflate(layoutResID, mContentParent);
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
    }

    @Override
    public void setContentView(View view) {
        setContentView(view, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        if (mContentParent == null) {
            installDecor();
        } else {
            mContentParent.removeAllViews();
        }
        mContentParent.addView(view, params);
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (mContentParent == null) {
            installDecor();
        }
        mContentParent.addView(view, params);
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onContentChanged();
        }
    }

    @Override
    public View getCurrentFocus() {
        return mDecor != null ? mDecor.findFocus() : null;
    }

    @Override
    public void takeSurface(SurfaceHolder.Callback2 callback) {
        mTakeSurfaceCallback = callback;
    }
    
    public void takeInputQueue(InputQueue.Callback callback) {
        mTakeInputQueueCallback = callback;
    }
    
    @Override
    public boolean isFloating() {
        return mIsFloating;
    }

    /**
     * Return a LayoutInflater instance that can be used to inflate XML view layout
     * resources for use in this Window.
     *
     * @return LayoutInflater The shared LayoutInflater.
     */
    @Override
    public LayoutInflater getLayoutInflater() {
        return mLayoutInflater;
    }

    @Override
    public void setTitle(CharSequence title) {
        if (mTitleView != null) {
            mTitleView.setText(title);
        } else if (mActionBar != null) {
            mActionBar.setWindowTitle(title);
        }
        mTitle = title;
    }

    @Override
    public void setTitleColor(int textColor) {
        if (mTitleView != null) {
            mTitleView.setTextColor(textColor);
        }
        mTitleColor = textColor;
    }

    /**
     * Prepares the panel to either be opened or chorded. This creates the Menu
     * instance for the panel and populates it via the Activity callbacks.
     *
     * @param st The panel state to prepare.
     * @param event The event that triggered the preparing of the panel.
     * @return Whether the panel was prepared. If the panel should not be shown,
     *         returns false.
     */
    public final boolean preparePanel(PanelFeatureState st, KeyEvent event) {
        if (isDestroyed()) {
            return false;
        }

        // Already prepared (isPrepared will be reset to false later)
        if (st.isPrepared) {
            return true;
        }
        
        if ((mPreparedPanel != null) && (mPreparedPanel != st)) {
            // Another Panel is prepared and possibly open, so close it
            closePanel(mPreparedPanel, false);
        }

        final Callback cb = getCallback();

        if (cb != null) {
            st.createdPanelView = cb.onCreatePanelView(st.featureId);
        }

        if (st.createdPanelView == null) {
            // Init the panel state's menu--return false if init failed
            if (st.menu == null || st.refreshMenuContent) {
                if (st.menu == null) {
                    if (!initializePanelMenu(st) || (st.menu == null)) {
                        return false;
                    }
                }

                if (mActionBar != null) {
                    if (mActionMenuPresenterCallback == null) {
                        mActionMenuPresenterCallback = new ActionMenuPresenterCallback();
                    }
                    mActionBar.setMenu(st.menu, mActionMenuPresenterCallback);
                }

                // Call callback, and return if it doesn't want to display menu.

                // Creating the panel menu will involve a lot of manipulation;
                // don't dispatch change events to presenters until we're done.
                st.menu.stopDispatchingItemsChanged();
                if ((cb == null) || !cb.onCreatePanelMenu(st.featureId, st.menu)) {
                    // Ditch the menu created above
                    st.setMenu(null);

                    if (mActionBar != null) {
                        // Don't show it in the action bar either
                        mActionBar.setMenu(null, mActionMenuPresenterCallback);
                    }

                    return false;
                }
                
                st.refreshMenuContent = false;
            }

            // Callback and return if the callback does not want to show the menu

            // Preparing the panel menu can involve a lot of manipulation;
            // don't dispatch change events to presenters until we're done.
            st.menu.stopDispatchingItemsChanged();

            // Restore action view state before we prepare. This gives apps
            // an opportunity to override frozen/restored state in onPrepare.
            if (st.frozenActionViewState != null) {
                st.menu.restoreActionViewStates(st.frozenActionViewState);
                st.frozenActionViewState = null;
            }

            if (!cb.onPreparePanel(st.featureId, st.createdPanelView, st.menu)) {
                if (mActionBar != null) {
                    // The app didn't want to show the menu for now but it still exists.
                    // Clear it out of the action bar.
                    mActionBar.setMenu(null, mActionMenuPresenterCallback);
                }
                st.menu.startDispatchingItemsChanged();
                return false;
            }

            // Set the proper keymap
            KeyCharacterMap kmap = KeyCharacterMap.load(
                    event != null ? event.getDeviceId() : KeyCharacterMap.VIRTUAL_KEYBOARD);
            st.qwertyMode = kmap.getKeyboardType() != KeyCharacterMap.NUMERIC;
            st.menu.setQwertyMode(st.qwertyMode);
            st.menu.startDispatchingItemsChanged();
        }

        // Set other state
        st.isPrepared = true;
        st.isHandled = false;
        mPreparedPanel = st;

        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Action bars handle their own menu state
        if (mActionBar == null) {
            PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, false);
            if ((st != null) && (st.menu != null)) {
                if (st.isOpen) {
                    // Freeze state
                    final Bundle state = new Bundle();
                    if (st.iconMenuPresenter != null) {
                        st.iconMenuPresenter.saveHierarchyState(state);
                    }
                    if (st.listMenuPresenter != null) {
                        st.listMenuPresenter.saveHierarchyState(state);
                    }

                    // Remove the menu views since they need to be recreated
                    // according to the new configuration
                    clearMenuViews(st);

                    // Re-open the same menu
                    reopenMenu(false);

                    // Restore state
                    if (st.iconMenuPresenter != null) {
                        st.iconMenuPresenter.restoreHierarchyState(state);
                    }
                    if (st.listMenuPresenter != null) {
                        st.listMenuPresenter.restoreHierarchyState(state);
                    }

                } else {
                    // Clear menu views so on next menu opening, it will use
                    // the proper layout
                    clearMenuViews(st);
                }
            }
        }
    }

    private static void clearMenuViews(PanelFeatureState st) {
        // This can be called on config changes, so we should make sure
        // the views will be reconstructed based on the new orientation, etc.

        // Allow the callback to create a new panel view
        st.createdPanelView = null;

        // Causes the decor view to be recreated
        st.refreshDecorView = true;
        
        st.clearMenuPresenters();
    }

    @Override
    public final void openPanel(int featureId, KeyEvent event) {
        if (featureId == FEATURE_OPTIONS_PANEL && mActionBar != null &&
                mActionBar.isOverflowReserved()) {
            if (mActionBar.getVisibility() == View.VISIBLE) {
                mActionBar.showOverflowMenu();
            }
        } else {
            openPanel(getPanelState(featureId, true), event);
        }
    }

    private void openPanel(PanelFeatureState st, KeyEvent event) {
        // System.out.println("Open panel: isOpen=" + st.isOpen);

        // Already open, return
        if (st.isOpen || isDestroyed()) {
            return;
        }

        // Don't open an options panel for honeycomb apps on xlarge devices.
        // (The app should be using an action bar for menu items.)
        if (st.featureId == FEATURE_OPTIONS_PANEL) {
            Context context = getContext();
            Configuration config = context.getResources().getConfiguration();
            boolean isXLarge = (config.screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) ==
                    Configuration.SCREENLAYOUT_SIZE_XLARGE;
            boolean isHoneycombApp = context.getApplicationInfo().targetSdkVersion >=
                    android.os.Build.VERSION_CODES.HONEYCOMB;

            if (isXLarge && isHoneycombApp) {
                return;
            }
        }

        Callback cb = getCallback();
        if ((cb != null) && (!cb.onMenuOpened(st.featureId, st.menu))) {
            // Callback doesn't want the menu to open, reset any state
            closePanel(st, true);
            return;
        }

        final WindowManager wm = getWindowManager();
        if (wm == null) {
            return;
        }

        // Prepare panel (should have been done before, but just in case)
        if (!preparePanel(st, event)) {
            return;
        }

        int width = WRAP_CONTENT;
        if (st.decorView == null || st.refreshDecorView) {
            if (st.decorView == null) {
                // Initialize the panel decor, this will populate st.decorView
                if (!initializePanelDecor(st) || (st.decorView == null))
                    return;
            } else if (st.refreshDecorView && (st.decorView.getChildCount() > 0)) {
                // Decor needs refreshing, so remove its views
                st.decorView.removeAllViews();
            }

            // This will populate st.shownPanelView
            if (!initializePanelContent(st) || !st.hasPanelItems()) {
                return;
            }

            ViewGroup.LayoutParams lp = st.shownPanelView.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            }

            int backgroundResId;
            if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                // If the contents is fill parent for the width, set the
                // corresponding background
                backgroundResId = st.fullBackground;
                width = MATCH_PARENT;
            } else {
                // Otherwise, set the normal panel background
                backgroundResId = st.background;
            }
            st.decorView.setWindowBackground(getContext().getResources().getDrawable(
                    backgroundResId));

            ViewParent shownPanelParent = st.shownPanelView.getParent();
            if (shownPanelParent != null && shownPanelParent instanceof ViewGroup) {
                ((ViewGroup) shownPanelParent).removeView(st.shownPanelView);
            }
            st.decorView.addView(st.shownPanelView, lp);

            /*
             * Give focus to the view, if it or one of its children does not
             * already have it.
             */
            if (!st.shownPanelView.hasFocus()) {
                st.shownPanelView.requestFocus();
            }
        } else if (!st.isInListMode()) {
            width = MATCH_PARENT;
        } else if (st.createdPanelView != null) {
            // If we already had a panel view, carry width=MATCH_PARENT through
            // as we did above when it was created.
            ViewGroup.LayoutParams lp = st.createdPanelView.getLayoutParams();
            if (lp != null && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                width = MATCH_PARENT;
            }
        }

        st.isOpen = true;
        st.isHandled = false;

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                width, WRAP_CONTENT,
                st.x, st.y, WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG,
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                st.decorView.mDefaultOpacity);

        if (st.isCompact) {
            lp.gravity = getOptionsPanelGravity();
            sRotationWatcher.addWindow(this);
        } else {
            lp.gravity = st.gravity;
        }

        lp.windowAnimations = st.windowAnimations;
        
        wm.addView(st.decorView, lp);
        // Log.v(TAG, "Adding main menu to window manager.");
    }

    @Override
    public final void closePanel(int featureId) {
        if (featureId == FEATURE_OPTIONS_PANEL && mActionBar != null &&
                mActionBar.isOverflowReserved()) {
            mActionBar.hideOverflowMenu();
        } else if (featureId == FEATURE_CONTEXT_MENU) {
            closeContextMenu();
        } else {
            closePanel(getPanelState(featureId, true), true);
        }
    }

    /**
     * Closes the given panel.
     *
     * @param st The panel to be closed.
     * @param doCallback Whether to notify the callback that the panel was
     *            closed. If the panel is in the process of re-opening or
     *            opening another panel (e.g., menu opening a sub menu), the
     *            callback should not happen and this variable should be false.
     *            In addition, this method internally will only perform the
     *            callback if the panel is open.
     */
    public final void closePanel(PanelFeatureState st, boolean doCallback) {
        // System.out.println("Close panel: isOpen=" + st.isOpen);
        if (doCallback && st.featureId == FEATURE_OPTIONS_PANEL &&
                mActionBar != null && mActionBar.isOverflowMenuShowing()) {
            checkCloseActionMenu(st.menu);
            return;
        }

        final ViewManager wm = getWindowManager();
        if ((wm != null) && st.isOpen) {
            if (st.decorView != null) {
                wm.removeView(st.decorView);
                // Log.v(TAG, "Removing main menu from window manager.");
                if (st.isCompact) {
                    sRotationWatcher.removeWindow(this);
                }
            }

            if (doCallback) {
                callOnPanelClosed(st.featureId, st, null);
            }
        }

        st.isPrepared = false;
        st.isHandled = false;
        st.isOpen = false;

        // This view is no longer shown, so null it out
        st.shownPanelView = null;

        if (st.isInExpandedMode) {
            // Next time the menu opens, it should not be in expanded mode, so
            // force a refresh of the decor
            st.refreshDecorView = true;
            st.isInExpandedMode = false;
        }

        if (mPreparedPanel == st) {
            mPreparedPanel = null;
            mPanelChordingKey = 0;
        }
    }

    void checkCloseActionMenu(Menu menu) {
        if (mClosingActionMenu) {
            return;
        }

        mClosingActionMenu = true;
        mActionBar.dismissPopupMenus();
        Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            cb.onPanelClosed(FEATURE_ACTION_BAR, menu);
        }
        mClosingActionMenu = false;
    }

    @Override
    public final void togglePanel(int featureId, KeyEvent event) {
        PanelFeatureState st = getPanelState(featureId, true);
        if (st.isOpen) {
            closePanel(st, true);
        } else {
            openPanel(st, event);
        }
    }

    @Override
    public void invalidatePanelMenu(int featureId) {
        mInvalidatePanelMenuFeatures |= 1 << featureId;

        if (!mInvalidatePanelMenuPosted && mDecor != null) {
            mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
            mInvalidatePanelMenuPosted = true;
        }
    }

    void doInvalidatePanelMenu(int featureId) {
        PanelFeatureState st = getPanelState(featureId, true);
        Bundle savedActionViewStates = null;
        if (st.menu != null) {
            savedActionViewStates = new Bundle();
            st.menu.saveActionViewStates(savedActionViewStates);
            if (savedActionViewStates.size() > 0) {
                st.frozenActionViewState = savedActionViewStates;
            }
            // This will be started again when the panel is prepared.
            st.menu.stopDispatchingItemsChanged();
            st.menu.clear();
        }
        st.refreshMenuContent = true;
        st.refreshDecorView = true;
        
        // Prepare the options panel if we have an action bar
        if ((featureId == FEATURE_ACTION_BAR || featureId == FEATURE_OPTIONS_PANEL)
                && mActionBar != null) {
            st = getPanelState(Window.FEATURE_OPTIONS_PANEL, false);
            if (st != null) {
                st.isPrepared = false;
                preparePanel(st, null);
            }
        }
    }
    
    /**
     * Called when the panel key is pushed down.
     * @param featureId The feature ID of the relevant panel (defaults to FEATURE_OPTIONS_PANEL}.
     * @param event The key event.
     * @return Whether the key was handled.
     */
    public final boolean onKeyDownPanel(int featureId, KeyEvent event) {
        final int keyCode = event.getKeyCode();
        
        if (event.getRepeatCount() == 0) {
            // The panel key was pushed, so set the chording key
            mPanelChordingKey = keyCode;

            PanelFeatureState st = getPanelState(featureId, true);
            if (!st.isOpen) {
                return preparePanel(st, event);
            }
        }

        return false;
    }

    /**
     * Called when the panel key is released.
     * @param featureId The feature ID of the relevant panel (defaults to FEATURE_OPTIONS_PANEL}.
     * @param event The key event.
     */
    public final void onKeyUpPanel(int featureId, KeyEvent event) {
        // The panel key was released, so clear the chording key
        if (mPanelChordingKey != 0) {
            mPanelChordingKey = 0;

            if (event.isCanceled() || (mDecor != null && mDecor.mActionMode != null)) {
                return;
            }
            
            boolean playSoundEffect = false;
            final PanelFeatureState st = getPanelState(featureId, true);
            if (featureId == FEATURE_OPTIONS_PANEL && mActionBar != null &&
                    mActionBar.isOverflowReserved()) {
                if (mActionBar.getVisibility() == View.VISIBLE) {
                    if (!mActionBar.isOverflowMenuShowing()) {
                        if (!isDestroyed() && preparePanel(st, event)) {
                            playSoundEffect = mActionBar.showOverflowMenu();
                        }
                    } else {
                        playSoundEffect = mActionBar.hideOverflowMenu();
                    }
                }
            } else {
                if (st.isOpen || st.isHandled) {

                    // Play the sound effect if the user closed an open menu (and not if
                    // they just released a menu shortcut)
                    playSoundEffect = st.isOpen;

                    // Close menu
                    closePanel(st, true);

                } else if (st.isPrepared) {
                    boolean show = true;
                    if (st.refreshMenuContent) {
                        // Something may have invalidated the menu since we prepared it.
                        // Re-prepare it to refresh.
                        st.isPrepared = false;
                        show = preparePanel(st, event);
                    }

                    if (show) {
                        // Write 'menu opened' to event log
                        EventLog.writeEvent(50001, 0);

                        // Show menu
                        openPanel(st, event);

                        playSoundEffect = true;
                    }
                }
            }

            if (playSoundEffect) {
                AudioManager audioManager = (AudioManager) getContext().getSystemService(
                        Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);
                } else {
                    Log.w(TAG, "Couldn't get audio manager");
                }
            }
        }
    }

    @Override
    public final void closeAllPanels() {
        final ViewManager wm = getWindowManager();
        if (wm == null) {
            return;
        }

        final PanelFeatureState[] panels = mPanels;
        final int N = panels != null ? panels.length : 0;
        for (int i = 0; i < N; i++) {
            final PanelFeatureState panel = panels[i];
            if (panel != null) {
                closePanel(panel, true);
            }
        }

        closeContextMenu();
    }

    /**
     * Closes the context menu. This notifies the menu logic of the close, along
     * with dismissing it from the UI.
     */
    private synchronized void closeContextMenu() {
        if (mContextMenu != null) {
            mContextMenu.close();
            dismissContextMenu();
        }
    }

    /**
     * Dismisses just the context menu UI. To close the context menu, use
     * {@link #closeContextMenu()}.
     */
    private synchronized void dismissContextMenu() {
        mContextMenu = null;

        if (mContextMenuHelper != null) {
            mContextMenuHelper.dismiss();
            mContextMenuHelper = null;
        }
    }

    @Override
    public boolean performPanelShortcut(int featureId, int keyCode, KeyEvent event, int flags) {
        return performPanelShortcut(getPanelState(featureId, true), keyCode, event, flags);
    }

    private boolean performPanelShortcut(PanelFeatureState st, int keyCode, KeyEvent event,
            int flags) {
        if (event.isSystem() || (st == null)) {
            return false;
        }

        boolean handled = false;

        // Only try to perform menu shortcuts if preparePanel returned true (possible false
        // return value from application not wanting to show the menu).
        if ((st.isPrepared || preparePanel(st, event)) && st.menu != null) {
            // The menu is prepared now, perform the shortcut on it
            handled = st.menu.performShortcut(keyCode, event, flags);
        }

        if (handled) {
            // Mark as handled
            st.isHandled = true;

            // Only close down the menu if we don't have an action bar keeping it open.
            if ((flags & Menu.FLAG_PERFORM_NO_CLOSE) == 0 && mActionBar == null) {
                closePanel(st, true);
            }
        }

        return handled;
    }

    @Override
    public boolean performPanelIdentifierAction(int featureId, int id, int flags) {

        PanelFeatureState st = getPanelState(featureId, true);
        if (!preparePanel(st, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU))) {
            return false;
        }
        if (st.menu == null) {
            return false;
        }

        boolean res = st.menu.performIdentifierAction(id, flags);

        // Only close down the menu if we don't have an action bar keeping it open.
        if (mActionBar == null) {
            closePanel(st, true);
        }

        return res;
    }

    public PanelFeatureState findMenuPanel(Menu menu) {
        final PanelFeatureState[] panels = mPanels;
        final int N = panels != null ? panels.length : 0;
        for (int i = 0; i < N; i++) {
            final PanelFeatureState panel = panels[i];
            if (panel != null && panel.menu == menu) {
                return panel;
            }
        }
        return null;
    }

    public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
        final Callback cb = getCallback();
        if (cb != null && !isDestroyed()) {
            final PanelFeatureState panel = findMenuPanel(menu.getRootMenu());
            if (panel != null) {
                return cb.onMenuItemSelected(panel.featureId, item);
            }
        }
        return false;
    }

    public void onMenuModeChange(MenuBuilder menu) {
        reopenMenu(true);
    }

    private void reopenMenu(boolean toggleMenuMode) {
        if (mActionBar != null && mActionBar.isOverflowReserved()) {
            final Callback cb = getCallback();
            if (!mActionBar.isOverflowMenuShowing() || !toggleMenuMode) {
                if (cb != null && !isDestroyed() && mActionBar.getVisibility() == View.VISIBLE) {
                    final PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, true);

                    // If we don't have a menu or we're waiting for a full content refresh,
                    // forget it. This is a lingering event that no longer matters.
                    if (st.menu != null && !st.refreshMenuContent &&
                            cb.onPreparePanel(FEATURE_OPTIONS_PANEL, st.createdPanelView, st.menu)) {
                        cb.onMenuOpened(FEATURE_ACTION_BAR, st.menu);
                        mActionBar.showOverflowMenu();
                    }
                }
            } else {
                mActionBar.hideOverflowMenu();
                if (cb != null && !isDestroyed()) {
                    final PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, true);
                    cb.onPanelClosed(FEATURE_ACTION_BAR, st.menu);
                }
            }
            return;
        }

        PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, true);

        // Save the future expanded mode state since closePanel will reset it
        boolean newExpandedMode = toggleMenuMode ? !st.isInExpandedMode : st.isInExpandedMode;

        st.refreshDecorView = true;
        closePanel(st, false);

        // Set the expanded mode state
        st.isInExpandedMode = newExpandedMode;

        openPanel(st, null);
    }

    /**
     * Initializes the menu associated with the given panel feature state. You
     * must at the very least set PanelFeatureState.menu to the Menu to be
     * associated with the given panel state. The default implementation creates
     * a new menu for the panel state.
     *
     * @param st The panel whose menu is being initialized.
     * @return Whether the initialization was successful.
     */
    protected boolean initializePanelMenu(final PanelFeatureState st) {
        Context context = getContext();

        // If we have an action bar, initialize the menu with a context themed for it.
        if ((st.featureId == FEATURE_OPTIONS_PANEL || st.featureId == FEATURE_ACTION_BAR) &&
                mActionBar != null) {
            TypedValue outValue = new TypedValue();
            Resources.Theme currentTheme = context.getTheme();
            currentTheme.resolveAttribute(com.android.internal.R.attr.actionBarWidgetTheme,
                    outValue, true);
            final int targetThemeRes = outValue.resourceId;

            if (targetThemeRes != 0 && context.getThemeResId() != targetThemeRes) {
                context = new ContextThemeWrapper(context, targetThemeRes);
            }
        }

        final MenuBuilder menu = new MenuBuilder(context);

        menu.setCallback(this);
        st.setMenu(menu);

        return true;
    }

    /**
     * Perform initial setup of a panel. This should at the very least set the
     * style information in the PanelFeatureState and must set
     * PanelFeatureState.decor to the panel's window decor view.
     *
     * @param st The panel being initialized.
     */
    protected boolean initializePanelDecor(PanelFeatureState st) {
        st.decorView = new DecorView(getContext(), st.featureId);
        st.gravity = Gravity.CENTER | Gravity.BOTTOM;
        st.setStyle(getContext());

        return true;
    }

    /**
     * Determine the gravity value for the options panel. This can
     * differ in compact mode.
     *
     * @return gravity value to use for the panel window
     */
    private int getOptionsPanelGravity() {
        try {
            return WindowManagerHolder.sWindowManager.getPreferredOptionsPanelGravity();
        } catch (RemoteException ex) {
            Log.e(TAG, "Couldn't getOptionsPanelGravity; using default", ex);
            return Gravity.CENTER | Gravity.BOTTOM;
        }
    }

    void onOptionsPanelRotationChanged() {
        final PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, false);
        if (st == null) return;

        final WindowManager.LayoutParams lp = st.decorView != null ?
                (WindowManager.LayoutParams) st.decorView.getLayoutParams() : null;
        if (lp != null) {
            lp.gravity = getOptionsPanelGravity();
            final ViewManager wm = getWindowManager();
            if (wm != null) {
                wm.updateViewLayout(st.decorView, lp);
            }
        }
    }

    /**
     * Initializes the panel associated with the panel feature state. You must
     * at the very least set PanelFeatureState.panel to the View implementing
     * its contents. The default implementation gets the panel from the menu.
     *
     * @param st The panel state being initialized.
     * @return Whether the initialization was successful.
     */
    protected boolean initializePanelContent(PanelFeatureState st) {
        if (st.createdPanelView != null) {
            st.shownPanelView = st.createdPanelView;
            return true;
        }

        if (st.menu == null) {
            return false;
        }

        if (mPanelMenuPresenterCallback == null) {
            mPanelMenuPresenterCallback = new PanelMenuPresenterCallback();
        }

        MenuView menuView = st.isInListMode()
                ? st.getListMenuView(getContext(), mPanelMenuPresenterCallback)
                : st.getIconMenuView(getContext(), mPanelMenuPresenterCallback);

        st.shownPanelView = (View) menuView;

        if (st.shownPanelView != null) {
            // Use the menu View's default animations if it has any
            final int defaultAnimations = menuView.getWindowAnimations();
            if (defaultAnimations != 0) {
                st.windowAnimations = defaultAnimations;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean performContextMenuIdentifierAction(int id, int flags) {
        return (mContextMenu != null) ? mContextMenu.performIdentifierAction(id, flags) : false;
    }

    @Override
    public final void setBackgroundDrawable(Drawable drawable) {
        if (drawable != mBackgroundDrawable || mBackgroundResource != 0) {
            mBackgroundResource = 0;
            mBackgroundDrawable = drawable;
            if (mDecor != null) {
                mDecor.setWindowBackground(drawable);
            }
        }
    }

    @Override
    public final void setFeatureDrawableResource(int featureId, int resId) {
        if (resId != 0) {
            DrawableFeatureState st = getDrawableState(featureId, true);
            if (st.resid != resId) {
                st.resid = resId;
                st.uri = null;
                st.local = getContext().getResources().getDrawable(resId);
                updateDrawable(featureId, st, false);
            }
        } else {
            setFeatureDrawable(featureId, null);
        }
    }

    @Override
    public final void setFeatureDrawableUri(int featureId, Uri uri) {
        if (uri != null) {
            DrawableFeatureState st = getDrawableState(featureId, true);
            if (st.uri == null || !st.uri.equals(uri)) {
                st.resid = 0;
                st.uri = uri;
                st.local = loadImageURI(uri);
                updateDrawable(featureId, st, false);
            }
        } else {
            setFeatureDrawable(featureId, null);
        }
    }

    @Override
    public final void setFeatureDrawable(int featureId, Drawable drawable) {
        DrawableFeatureState st = getDrawableState(featureId, true);
        st.resid = 0;
        st.uri = null;
        if (st.local != drawable) {
            st.local = drawable;
            updateDrawable(featureId, st, false);
        }
    }

    @Override
    public void setFeatureDrawableAlpha(int featureId, int alpha) {
        DrawableFeatureState st = getDrawableState(featureId, true);
        if (st.alpha != alpha) {
            st.alpha = alpha;
            updateDrawable(featureId, st, false);
        }
    }

    protected final void setFeatureDefaultDrawable(int featureId, Drawable drawable) {
        DrawableFeatureState st = getDrawableState(featureId, true);
        if (st.def != drawable) {
            st.def = drawable;
            updateDrawable(featureId, st, false);
        }
    }

    @Override
    public final void setFeatureInt(int featureId, int value) {
        // XXX Should do more management (as with drawable features) to
        // deal with interactions between multiple window policies.
        updateInt(featureId, value, false);
    }

    /**
     * Update the state of a drawable feature. This should be called, for every
     * drawable feature supported, as part of onActive(), to make sure that the
     * contents of a containing window is properly updated.
     *
     * @see #onActive
     * @param featureId The desired drawable feature to change.
     * @param fromActive Always true when called from onActive().
     */
    protected final void updateDrawable(int featureId, boolean fromActive) {
        final DrawableFeatureState st = getDrawableState(featureId, false);
        if (st != null) {
            updateDrawable(featureId, st, fromActive);
        }
    }

    /**
     * Called when a Drawable feature changes, for the window to update its
     * graphics.
     *
     * @param featureId The feature being changed.
     * @param drawable The new Drawable to show, or null if none.
     * @param alpha The new alpha blending of the Drawable.
     */
    protected void onDrawableChanged(int featureId, Drawable drawable, int alpha) {
        ImageView view;
        if (featureId == FEATURE_LEFT_ICON) {
            view = getLeftIconView();
        } else if (featureId == FEATURE_RIGHT_ICON) {
            view = getRightIconView();
        } else {
            return;
        }

        if (drawable != null) {
            drawable.setAlpha(alpha);
            view.setImageDrawable(drawable);
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * Called when an int feature changes, for the window to update its
     * graphics.
     *
     * @param featureId The feature being changed.
     * @param value The new integer value.
     */
    protected void onIntChanged(int featureId, int value) {
        if (featureId == FEATURE_PROGRESS || featureId == FEATURE_INDETERMINATE_PROGRESS) {
            updateProgressBars(value);
        } else if (featureId == FEATURE_CUSTOM_TITLE) {
            FrameLayout titleContainer = (FrameLayout) findViewById(com.android.internal.R.id.title_container);
            if (titleContainer != null) {
                mLayoutInflater.inflate(value, titleContainer);
            }
        }
    }

    /**
     * Updates the progress bars that are shown in the title bar.
     *
     * @param value Can be one of {@link Window#PROGRESS_VISIBILITY_ON},
     *            {@link Window#PROGRESS_VISIBILITY_OFF},
     *            {@link Window#PROGRESS_INDETERMINATE_ON},
     *            {@link Window#PROGRESS_INDETERMINATE_OFF}, or a value
     *            starting at {@link Window#PROGRESS_START} through
     *            {@link Window#PROGRESS_END} for setting the default
     *            progress (if {@link Window#PROGRESS_END} is given,
     *            the progress bar widgets in the title will be hidden after an
     *            animation), a value between
     *            {@link Window#PROGRESS_SECONDARY_START} -
     *            {@link Window#PROGRESS_SECONDARY_END} for the
     *            secondary progress (if
     *            {@link Window#PROGRESS_SECONDARY_END} is given, the
     *            progress bar widgets will still be shown with the secondary
     *            progress bar will be completely filled in.)
     */
    private void updateProgressBars(int value) {
        ProgressBar circularProgressBar = getCircularProgressBar(true);
        ProgressBar horizontalProgressBar = getHorizontalProgressBar(true);

        final int features = getLocalFeatures();
        if (value == PROGRESS_VISIBILITY_ON) {
            if ((features & (1 << FEATURE_PROGRESS)) != 0) {
                int level = horizontalProgressBar.getProgress();
                int visibility = (horizontalProgressBar.isIndeterminate() || level < 10000) ?
                        View.VISIBLE : View.INVISIBLE;
                horizontalProgressBar.setVisibility(visibility);
            }
            if ((features & (1 << FEATURE_INDETERMINATE_PROGRESS)) != 0) {
                circularProgressBar.setVisibility(View.VISIBLE);
            }
        } else if (value == PROGRESS_VISIBILITY_OFF) {
            if ((features & (1 << FEATURE_PROGRESS)) != 0) {
                horizontalProgressBar.setVisibility(View.GONE);
            }
            if ((features & (1 << FEATURE_INDETERMINATE_PROGRESS)) != 0) {
                circularProgressBar.setVisibility(View.GONE);
            }
        } else if (value == PROGRESS_INDETERMINATE_ON) {
            horizontalProgressBar.setIndeterminate(true);
        } else if (value == PROGRESS_INDETERMINATE_OFF) {
            horizontalProgressBar.setIndeterminate(false);
        } else if (PROGRESS_START <= value && value <= PROGRESS_END) {
            // We want to set the progress value before testing for visibility
            // so that when the progress bar becomes visible again, it has the
            // correct level.
            horizontalProgressBar.setProgress(value - PROGRESS_START);

            if (value < PROGRESS_END) {
                showProgressBars(horizontalProgressBar, circularProgressBar);
            } else {
                hideProgressBars(horizontalProgressBar, circularProgressBar);
            }
        } else if (PROGRESS_SECONDARY_START <= value && value <= PROGRESS_SECONDARY_END) {
            horizontalProgressBar.setSecondaryProgress(value - PROGRESS_SECONDARY_START);

            showProgressBars(horizontalProgressBar, circularProgressBar);
        }

    }

    private void showProgressBars(ProgressBar horizontalProgressBar, ProgressBar spinnyProgressBar) {
        final int features = getLocalFeatures();
        if ((features & (1 << FEATURE_INDETERMINATE_PROGRESS)) != 0 &&
                spinnyProgressBar.getVisibility() == View.INVISIBLE) {
            spinnyProgressBar.setVisibility(View.VISIBLE);
        }
        // Only show the progress bars if the primary progress is not complete
        if ((features & (1 << FEATURE_PROGRESS)) != 0 &&
                horizontalProgressBar.getProgress() < 10000) {
            horizontalProgressBar.setVisibility(View.VISIBLE);
        }
    }

    private void hideProgressBars(ProgressBar horizontalProgressBar, ProgressBar spinnyProgressBar) {
        final int features = getLocalFeatures();
        Animation anim = AnimationUtils.loadAnimation(getContext(), com.android.internal.R.anim.fade_out);
        anim.setDuration(1000);
        if ((features & (1 << FEATURE_INDETERMINATE_PROGRESS)) != 0 &&
                spinnyProgressBar.getVisibility() == View.VISIBLE) {
            spinnyProgressBar.startAnimation(anim);
            spinnyProgressBar.setVisibility(View.INVISIBLE);
        }
        if ((features & (1 << FEATURE_PROGRESS)) != 0 &&
                horizontalProgressBar.getVisibility() == View.VISIBLE) {
            horizontalProgressBar.startAnimation(anim);
            horizontalProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Request that key events come to this activity. Use this if your activity
     * has no views with focus, but the activity still wants a chance to process
     * key events.
     */
    @Override
    public void takeKeyEvents(boolean get) {
        mDecor.setFocusable(get);
    }

    @Override
    public boolean superDispatchKeyEvent(KeyEvent event) {
        return mDecor.superDispatchKeyEvent(event);
    }

    @Override
    public boolean superDispatchKeyShortcutEvent(KeyEvent event) {
        return mDecor.superDispatchKeyShortcutEvent(event);
    }

    @Override
    public boolean superDispatchTouchEvent(MotionEvent event) {
        return mDecor.superDispatchTouchEvent(event);
    }

    @Override
    public boolean superDispatchTrackballEvent(MotionEvent event) {
        return mDecor.superDispatchTrackballEvent(event);
    }

    @Override
    public boolean superDispatchGenericMotionEvent(MotionEvent event) {
        return mDecor.superDispatchGenericMotionEvent(event);
    }

    /**
     * A key was pressed down and not handled by anything else in the window.
     *
     * @see #onKeyUp
     * @see android.view.KeyEvent
     */
    protected boolean onKeyDown(int featureId, int keyCode, KeyEvent event) {
        /* ****************************************************************************
         * HOW TO DECIDE WHERE YOUR KEY HANDLING GOES.
         *
         * If your key handling must happen before the app gets a crack at the event,
         * it goes in PhoneWindowManager.
         *
         * If your key handling should happen in all windows, and does not depend on
         * the state of the current application, other than that the current
         * application can override the behavior by handling the event itself, it
         * should go in PhoneFallbackEventHandler.
         *
         * Only if your handling depends on the window, and the fact that it has
         * a DecorView, should it go here.
         * ****************************************************************************/

        final KeyEvent.DispatcherState dispatcher =
                mDecor != null ? mDecor.getKeyDispatcherState() : null;
        //Log.i(TAG, "Key down: repeat=" + event.getRepeatCount()
        //        + " flags=0x" + Integer.toHexString(event.getFlags()));
        
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE: {
                // Similar code is in PhoneFallbackEventHandler in case the window
                // doesn't have one of these.  In this case, we execute it here and
                // eat the event instead, because we have mVolumeControlStreamType
                // and they don't.
                getAudioManager().handleKeyDown(event, mVolumeControlStreamType);
                return true;
            }

            case KeyEvent.KEYCODE_MENU: {
                onKeyDownPanel((featureId < 0) ? FEATURE_OPTIONS_PANEL : featureId, event);
                return true;
            }

            case KeyEvent.KEYCODE_BACK: {
                if (event.getRepeatCount() > 0) break;
                if (featureId < 0) break;
                // Currently don't do anything with long press.
                if (dispatcher != null) {
                    dispatcher.startTracking(event, this);
                }
                return true;
            }

        }

        return false;
    }

    private KeyguardManager getKeyguardManager() {
        if (mKeyguardManager == null) {
            mKeyguardManager = (KeyguardManager) getContext().getSystemService(
                    Context.KEYGUARD_SERVICE);
        }
        return mKeyguardManager;
    }

    AudioManager getAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
        }
        return mAudioManager;
    }

    /**
     * A key was released and not handled by anything else in the window.
     *
     * @see #onKeyDown
     * @see android.view.KeyEvent
     */
    protected boolean onKeyUp(int featureId, int keyCode, KeyEvent event) {
        final KeyEvent.DispatcherState dispatcher =
                mDecor != null ? mDecor.getKeyDispatcherState() : null;
        if (dispatcher != null) {
            dispatcher.handleUpEvent(event);
        }
        //Log.i(TAG, "Key up: repeat=" + event.getRepeatCount()
        //        + " flags=0x" + Integer.toHexString(event.getFlags()));
        
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_MUTE: {
                // Similar code is in PhoneFallbackEventHandler in case the window
                // doesn't have one of these.  In this case, we execute it here and
                // eat the event instead, because we have mVolumeControlStreamType
                // and they don't.
                getAudioManager().handleKeyUp(event, mVolumeControlStreamType);
                return true;
            }

            case KeyEvent.KEYCODE_MENU: {
                onKeyUpPanel(featureId < 0 ? FEATURE_OPTIONS_PANEL : featureId,
                        event);
                return true;
            }

            case KeyEvent.KEYCODE_BACK: {
                if (featureId < 0) break;
                if (event.isTracking() && !event.isCanceled()) {
                    if (featureId == FEATURE_OPTIONS_PANEL) {
                        PanelFeatureState st = getPanelState(featureId, false);
                        if (st != null && st.isInExpandedMode) {
                            // If the user is in an expanded menu and hits back, it
                            // should go back to the icon menu
                            reopenMenu(true);
                            return true;
                        }
                    }
                    closePanel(featureId);
                    return true;
                }
                break;
            }

            case KeyEvent.KEYCODE_SEARCH: {
                /*
                 * Do this in onKeyUp since the Search key is also used for
                 * chording quick launch shortcuts.
                 */
                if (getKeyguardManager().inKeyguardRestrictedInputMode()) {
                    break;
                }
                if (event.isTracking() && !event.isCanceled()) {
                    launchDefaultSearch();
                }
                return true;
            }
        }

        return false;
    }

    @Override
    protected void onActive() {
    }

    @Override
    public final View getDecorView() {
        if (mDecor == null) {
            installDecor();
        }
        return mDecor;
    }

    @Override
    public final View peekDecorView() {
        return mDecor;
    }

    static private final String FOCUSED_ID_TAG = "android:focusedViewId";
    static private final String VIEWS_TAG = "android:views";
    static private final String PANELS_TAG = "android:Panels";
    static private final String ACTION_BAR_TAG = "android:ActionBar";

    /** {@inheritDoc} */
    @Override
    public Bundle saveHierarchyState() {
        Bundle outState = new Bundle();
        if (mContentParent == null) {
            return outState;
        }

        SparseArray<Parcelable> states = new SparseArray<Parcelable>();
        mContentParent.saveHierarchyState(states);
        outState.putSparseParcelableArray(VIEWS_TAG, states);

        // save the focused view id
        View focusedView = mContentParent.findFocus();
        if (focusedView != null) {
            if (focusedView.getId() != View.NO_ID) {
                outState.putInt(FOCUSED_ID_TAG, focusedView.getId());
            } else {
                if (false) {
                    Log.d(TAG, "couldn't save which view has focus because the focused view "
                            + focusedView + " has no id.");
                }
            }
        }

        // save the panels
        SparseArray<Parcelable> panelStates = new SparseArray<Parcelable>();
        savePanelState(panelStates);
        if (panelStates.size() > 0) {
            outState.putSparseParcelableArray(PANELS_TAG, panelStates);
        }

        if (mActionBar != null) {
            SparseArray<Parcelable> actionBarStates = new SparseArray<Parcelable>();
            mActionBar.saveHierarchyState(actionBarStates);
            outState.putSparseParcelableArray(ACTION_BAR_TAG, actionBarStates);
        }

        return outState;
    }

    /** {@inheritDoc} */
    @Override
    public void restoreHierarchyState(Bundle savedInstanceState) {
        if (mContentParent == null) {
            return;
        }

        SparseArray<Parcelable> savedStates
                = savedInstanceState.getSparseParcelableArray(VIEWS_TAG);
        if (savedStates != null) {
            mContentParent.restoreHierarchyState(savedStates);
        }

        // restore the focused view
        int focusedViewId = savedInstanceState.getInt(FOCUSED_ID_TAG, View.NO_ID);
        if (focusedViewId != View.NO_ID) {
            View needsFocus = mContentParent.findViewById(focusedViewId);
            if (needsFocus != null) {
                needsFocus.requestFocus();
            } else {
                Log.w(TAG,
                        "Previously focused view reported id " + focusedViewId
                                + " during save, but can't be found during restore.");
            }
        }

        // restore the panels
        SparseArray<Parcelable> panelStates = savedInstanceState.getSparseParcelableArray(PANELS_TAG);
        if (panelStates != null) {
            restorePanelState(panelStates);
        }

        if (mActionBar != null) {
            SparseArray<Parcelable> actionBarStates =
                    savedInstanceState.getSparseParcelableArray(ACTION_BAR_TAG);
            if (actionBarStates != null) {
                mActionBar.restoreHierarchyState(actionBarStates);
            } else {
                Log.w(TAG, "Missing saved instance states for action bar views! " +
                        "State will not be restored.");
            }
        }
    }

    /**
     * Invoked when the panels should freeze their state.
     *
     * @param icicles Save state into this. This is usually indexed by the
     *            featureId. This will be given to {@link #restorePanelState} in the
     *            future.
     */
    private void savePanelState(SparseArray<Parcelable> icicles) {
        PanelFeatureState[] panels = mPanels;
        if (panels == null) {
            return;
        }

        for (int curFeatureId = panels.length - 1; curFeatureId >= 0; curFeatureId--) {
            if (panels[curFeatureId] != null) {
                icicles.put(curFeatureId, panels[curFeatureId].onSaveInstanceState());
            }
        }
    }

    /**
     * Invoked when the panels should thaw their state from a previously frozen state.
     *
     * @param icicles The state saved by {@link #savePanelState} that needs to be thawed.
     */
    private void restorePanelState(SparseArray<Parcelable> icicles) {
        PanelFeatureState st;
        int curFeatureId;
        for (int i = icicles.size() - 1; i >= 0; i--) {
            curFeatureId = icicles.keyAt(i);
            st = getPanelState(curFeatureId, false /* required */);
            if (st == null) {
                // The panel must not have been required, and is currently not around, skip it
                continue;
            }

            st.onRestoreInstanceState(icicles.get(curFeatureId));
            invalidatePanelMenu(curFeatureId);
        }

        /*
         * Implementation note: call openPanelsAfterRestore later to actually open the
         * restored panels.
         */
    }

    /**
     * Opens the panels that have had their state restored. This should be
     * called sometime after {@link #restorePanelState} when it is safe to add
     * to the window manager.
     */
    private void openPanelsAfterRestore() {
        PanelFeatureState[] panels = mPanels;

        if (panels == null) {
            return;
        }

        PanelFeatureState st;
        for (int i = panels.length - 1; i >= 0; i--) {
            st = panels[i];
            // We restore the panel if it was last open; we skip it if it
            // now is open, to avoid a race condition if the user immediately
            // opens it when we are resuming.
            if (st != null) {
                st.applyFrozenState();
                if (!st.isOpen && st.wasLastOpen) {
                    st.isInExpandedMode = st.wasLastExpanded;
                    openPanel(st, null);
                }
            }
        }
    }

    private class PanelMenuPresenterCallback implements MenuPresenter.Callback {
        @Override
        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            final Menu parentMenu = menu.getRootMenu();
            final boolean isSubMenu = parentMenu != menu;
            final PanelFeatureState panel = findMenuPanel(isSubMenu ? parentMenu : menu);
            if (panel != null) {
                if (isSubMenu) {
                    callOnPanelClosed(panel.featureId, panel, parentMenu);
                    closePanel(panel, true);
                } else {
                    // Close the panel and only do the callback if the menu is being
                    // closed completely, not if opening a sub menu
                    closePanel(panel, allMenusAreClosing);
                }
            }
        }

        @Override
        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            if (subMenu == null && hasFeature(FEATURE_ACTION_BAR)) {
                Callback cb = getCallback();
                if (cb != null && !isDestroyed()) {
                    cb.onMenuOpened(FEATURE_ACTION_BAR, subMenu);
                }
            }

            return true;
        }
    }

    private final class ActionMenuPresenterCallback implements MenuPresenter.Callback {
        @Override
        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            Callback cb = getCallback();
            if (cb != null) {
                cb.onMenuOpened(FEATURE_ACTION_BAR, subMenu);
                return true;
            }
            return false;
        }

        @Override
        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            checkCloseActionMenu(menu);
        }
    }

    private final class DecorView extends FrameLayout implements RootViewSurfaceTaker {
        /* package */int mDefaultOpacity = PixelFormat.OPAQUE;

        /** The feature ID of the panel, or -1 if this is the application's DecorView */
        private final int mFeatureId;

        private final Rect mDrawingBounds = new Rect();

        private final Rect mBackgroundPadding = new Rect();

        private final Rect mFramePadding = new Rect();

        private final Rect mFrameOffsets = new Rect();

        private boolean mChanging;

        private Drawable mMenuBackground;
        private boolean mWatchingForMenu;
        private int mDownY;

        private ActionMode mActionMode;
        private ActionBarContextView mActionModeView;
        private PopupWindow mActionModePopup;
        private Runnable mShowActionModePopup;

        private SettingsObserver mSettingsObserver;

        public DecorView(Context context, int featureId) {
            super(context);
            mFeatureId = featureId;
            mSettingsObserver = new SettingsObserver(new Handler());
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            final int keyCode = event.getKeyCode();
            final int action = event.getAction();
            final boolean isDown = action == KeyEvent.ACTION_DOWN;

            if (isDown && (event.getRepeatCount() == 0)) {
                // First handle chording of panel key: if a panel key is held
                // but not released, try to execute a shortcut in it.
                if ((mPanelChordingKey > 0) && (mPanelChordingKey != keyCode)) {
                    boolean handled = dispatchKeyShortcutEvent(event);
                    if (handled) {
                        return true;
                    }
                }

                // If a panel is open, perform a shortcut on it without the
                // chorded panel key
                if ((mPreparedPanel != null) && mPreparedPanel.isOpen) {
                    if (performPanelShortcut(mPreparedPanel, keyCode, event, 0)) {
                        return true;
                    }
                }
            }

            if (!isDestroyed()) {
                final Callback cb = getCallback();
                final boolean handled = cb != null && mFeatureId < 0 ? cb.dispatchKeyEvent(event)
                        : super.dispatchKeyEvent(event);
                if (handled) {
                    return true;
                }
            }

            return isDown ? PhoneWindow.this.onKeyDown(mFeatureId, event.getKeyCode(), event)
                    : PhoneWindow.this.onKeyUp(mFeatureId, event.getKeyCode(), event);
        }

        @Override
        public boolean dispatchKeyShortcutEvent(KeyEvent ev) {
            // If the panel is already prepared, then perform the shortcut using it.
            boolean handled;
            if (mPreparedPanel != null) {
                handled = performPanelShortcut(mPreparedPanel, ev.getKeyCode(), ev,
                        Menu.FLAG_PERFORM_NO_CLOSE);
                if (handled) {
                    if (mPreparedPanel != null) {
                        mPreparedPanel.isHandled = true;
                    }
                    return true;
                }
            }

            // Shortcut not handled by the panel.  Dispatch to the view hierarchy.
            final Callback cb = getCallback();
            handled = cb != null && !isDestroyed() && mFeatureId < 0
                    ? cb.dispatchKeyShortcutEvent(ev) : super.dispatchKeyShortcutEvent(ev);
            if (handled) {
                return true;
            }

            // If the panel is not prepared, then we may be trying to handle a shortcut key
            // combination such as Control+C.  Temporarily prepare the panel then mark it
            // unprepared again when finished to ensure that the panel will again be prepared
            // the next time it is shown for real.
            if (mPreparedPanel == null) {
                PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, true);
                preparePanel(st, ev);
                handled = performPanelShortcut(st, ev.getKeyCode(), ev,
                        Menu.FLAG_PERFORM_NO_CLOSE);
                st.isPrepared = false;
                if (handled) {
                    return true;
                }
            }
            return false;
        }

        private final StylusGestureFilter mStylusFilter = new StylusGestureFilter();

        private class StylusGestureFilter extends SimpleOnGestureListener {

            private final static int SWIPE_UP = 1;
            private final static int SWIPE_DOWN = 2;
            private final static int SWIPE_LEFT = 3;
            private final static int SWIPE_RIGHT = 4;
            private final static int PRESS_LONG = 5;
            private final static int TAP_DOUBLE = 6;
            private final static double SWIPE_MIN_DISTANCE = 25.0;
            private final static double SWIPE_MIN_VELOCITY = 50.0;
            private final static int KEY_NO_ACTION = 1000;
            private final static int KEY_HOME = 1001;
            private final static int KEY_BACK = 1002;
            private final static int KEY_MENU = 1003;
            private final static int KEY_SEARCH = 1004;
            private final static int KEY_RECENT = 1005;
            private final static int KEY_APP = 1006;
            private GestureDetector mDetector;
            private final static String TAG = "StylusGestureFilter";

            public StylusGestureFilter() {
                mDetector = new GestureDetector(this);
            }

            public boolean onTouchEvent(MotionEvent event) {
                return mDetector.onTouchEvent(event);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2,
                    float velocityX, float velocityY) {

                final float xDistance = Math.abs(e1.getX() - e2.getX());
                final float yDistance = Math.abs(e1.getY() - e2.getY());

                velocityX = Math.abs(velocityX);
                velocityY = Math.abs(velocityY);
                boolean result = false;

                if (velocityX > (SWIPE_MIN_VELOCITY * getResources().getDisplayMetrics().density)
                        && xDistance > (SWIPE_MIN_DISTANCE * getResources().getDisplayMetrics().density)
                        && xDistance > yDistance) {
                    if (e1.getX() > e2.getX()) { // right to left
                        // Swipe Left
                        dispatchStylusAction(SWIPE_LEFT);
                    } else {
                        // Swipe Right
                        dispatchStylusAction(SWIPE_RIGHT);
                    }
                    result = true;
                } else if (velocityY > (SWIPE_MIN_VELOCITY * getResources().getDisplayMetrics().density)
                        && yDistance > (SWIPE_MIN_DISTANCE * getResources().getDisplayMetrics().density)
                        && yDistance > xDistance) {
                    if (e1.getY() > e2.getY()) { // bottom to up
                        // Swipe Up
                        dispatchStylusAction(SWIPE_UP);
                    } else {
                        // Swipe Down
                        dispatchStylusAction(SWIPE_DOWN);
                    }
                    result = true;
                }
                return result;
            }

            @Override
            public boolean onDoubleTap(MotionEvent arg0) {
                dispatchStylusAction(TAP_DOUBLE);
                return true;
            }

            public void onLongPress(MotionEvent e) {
                dispatchStylusAction(PRESS_LONG);
            }

        }

        private void menuAction() {
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_MENU));
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_MENU));

        }

        private void backAction() {
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_BACK));
            dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_BACK));
        }

        private void dispatchStylusAction(int gestureAction) {
            final ContentResolver resolver = mContext.getContentResolver();
            boolean isSystemUI = mContext.getPackageName().equals("com.android.systemui");
            String setting = null;
            int dispatchAction = -1;
            switch (gestureAction) {
                case StylusGestureFilter.SWIPE_LEFT:
                    setting = Settings.System.getString(resolver,
                            Settings.System.GESTURES_LEFT_SWIPE);
                    break;
                case StylusGestureFilter.SWIPE_RIGHT:
                    setting = Settings.System.getString(resolver,
                            Settings.System.GESTURES_RIGHT_SWIPE);
                    break;
                case StylusGestureFilter.SWIPE_UP:
                    setting = Settings.System.getString(resolver,
                            Settings.System.GESTURES_UP_SWIPE);
                    break;
                case StylusGestureFilter.SWIPE_DOWN:
                    setting = Settings.System.getString(resolver,
                            Settings.System.GESTURES_DOWN_SWIPE);
                    break;
                case StylusGestureFilter.TAP_DOUBLE:
                    setting = Settings.System.getString(resolver,
                            Settings.System.GESTURES_DOUBLE_TAP);
                    break;
                case StylusGestureFilter.PRESS_LONG:
                    setting = Settings.System.getString(resolver,
                            Settings.System.GESTURES_LONG_PRESS);
                    break;
                default:
                    return;
            }

            try {
                int value = Integer.valueOf(setting);
                if (value == StylusGestureFilter.KEY_NO_ACTION) {
                    return;
                }
                dispatchAction = value;
            } catch (NumberFormatException e) {
                dispatchAction = StylusGestureFilter.KEY_APP;
            }

            // Dispatching action
            switch (dispatchAction) {
                case StylusGestureFilter.KEY_HOME:
                    Intent homeIntent = new Intent(Intent.ACTION_MAIN);
                    homeIntent.addCategory(Intent.CATEGORY_HOME);
                    homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(homeIntent);
                    break;
                case StylusGestureFilter.KEY_BACK:
                    backAction();
                    break;
                case StylusGestureFilter.KEY_MENU:
                    // Menu action on notificationbar / systemui will be converted
                    // to back action
                    if (isSystemUI) {
                        backAction();
                        break;
                    }
                    menuAction();
                    break;
                case StylusGestureFilter.KEY_SEARCH:
                    // Search action on notificationbar / systemui will be converted
                    // to back action
                    if (isSystemUI) {
                        backAction();
                        break;
                    }
                    launchDefaultSearch();
                    break;
                case StylusGestureFilter.KEY_RECENT:
                    IStatusBarService mStatusBarService = IStatusBarService.Stub
                            .asInterface(ServiceManager.getService("statusbar"));
                    try {
                        mStatusBarService.toggleRecentApps();
                    } catch (RemoteException e) {
                    }
                    break;
                case StylusGestureFilter.KEY_APP:
                    // Launching app on notificationbar / systemui will be preceded
                    // with a back Action
                    if (isSystemUI) {
                        backAction();
                    }
                    try {
                        final PackageManager pm = mContext.getPackageManager();
                        Intent launchIntent = pm.getLaunchIntentForPackage(setting);
                        if (launchIntent != null) {
                            mContext.startActivity(launchIntent);
                        }
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(mContext, mContext.getString(R.string.stylus_app_not_installed, setting),
                            Toast.LENGTH_LONG).show();
                    }
                    break;
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            // Stylus events with side button pressed are filtered and other
            // events are processed normally.
            if (mEnableGestures
                    && MotionEvent.BUTTON_SECONDARY == ev.getButtonState()) {
                mStylusFilter.onTouchEvent(ev);
                return false;
            }
            final Callback cb = getCallback();
            return cb != null && !isDestroyed() && mFeatureId < 0 ? cb.dispatchTouchEvent(ev)
                    : super.dispatchTouchEvent(ev);
        }

        @Override
        public boolean dispatchTrackballEvent(MotionEvent ev) {
            final Callback cb = getCallback();
            return cb != null && !isDestroyed() && mFeatureId < 0 ? cb.dispatchTrackballEvent(ev)
                    : super.dispatchTrackballEvent(ev);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent ev) {
            final Callback cb = getCallback();
            return cb != null && !isDestroyed() && mFeatureId < 0 ? cb.dispatchGenericMotionEvent(ev)
                    : super.dispatchGenericMotionEvent(ev);
        }

        public boolean superDispatchKeyEvent(KeyEvent event) {
            if (super.dispatchKeyEvent(event)) {
                return true;
            }

            // Not handled by the view hierarchy, does the action bar want it
            // to cancel out of something special?
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                final int action = event.getAction();
                // Back cancels action modes first.
                if (mActionMode != null) {
                    if (action == KeyEvent.ACTION_UP) {
                        mActionMode.finish();
                    }
                    return true;
                }

                // Next collapse any expanded action views.
                if (mActionBar != null && mActionBar.hasExpandedActionView()) {
                    if (action == KeyEvent.ACTION_UP) {
                        mActionBar.collapseActionView();
                    }
                    return true;
                }
            }

            return false;
        }

        public boolean superDispatchKeyShortcutEvent(KeyEvent event) {
            return super.dispatchKeyShortcutEvent(event);
        }

        public boolean superDispatchTouchEvent(MotionEvent event) {
            return super.dispatchTouchEvent(event);
        }

        public boolean superDispatchTrackballEvent(MotionEvent event) {
            return super.dispatchTrackballEvent(event);
        }

        public boolean superDispatchGenericMotionEvent(MotionEvent event) {
            return super.dispatchGenericMotionEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return onInterceptTouchEvent(event);
        }

        private boolean isOutOfBounds(int x, int y) {
            return x < -5 || y < -5 || x > (getWidth() + 5)
                    || y > (getHeight() + 5);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            int action = event.getAction();
            if (mFeatureId >= 0) {
                if (action == MotionEvent.ACTION_DOWN) {
                    int x = (int)event.getX();
                    int y = (int)event.getY();
                    if (isOutOfBounds(x, y)) {
                        closePanel(mFeatureId);
                        return true;
                    }
                }
            }

            if (!SWEEP_OPEN_MENU) {
                return false;
            }

            if (mFeatureId >= 0) {
                if (action == MotionEvent.ACTION_DOWN) {
                    Log.i(TAG, "Watchiing!");
                    mWatchingForMenu = true;
                    mDownY = (int) event.getY();
                    return false;
                }

                if (!mWatchingForMenu) {
                    return false;
                }

                int y = (int)event.getY();
                if (action == MotionEvent.ACTION_MOVE) {
                    if (y > (mDownY+30)) {
                        Log.i(TAG, "Closing!");
                        closePanel(mFeatureId);
                        mWatchingForMenu = false;
                        return true;
                    }
                } else if (action == MotionEvent.ACTION_UP) {
                    mWatchingForMenu = false;
                }

                return false;
            }

            //Log.i(TAG, "Intercept: action=" + action + " y=" + event.getY()
            //        + " (in " + getHeight() + ")");

            if (action == MotionEvent.ACTION_DOWN) {
                int y = (int)event.getY();
                if (y >= (getHeight()-5) && !hasChildren()) {
                    Log.i(TAG, "Watchiing!");
                    mWatchingForMenu = true;
                }
                return false;
            }

            if (!mWatchingForMenu) {
                return false;
            }

            int y = (int)event.getY();
            if (action == MotionEvent.ACTION_MOVE) {
                if (y < (getHeight()-30)) {
                    Log.i(TAG, "Opening!");
                    openPanel(FEATURE_OPTIONS_PANEL, new KeyEvent(
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
                    mWatchingForMenu = false;
                    return true;
                }
            } else if (action == MotionEvent.ACTION_UP) {
                mWatchingForMenu = false;
            }

            return false;
        }

        @Override
        public void sendAccessibilityEvent(int eventType) {
            if (!AccessibilityManager.getInstance(mContext).isEnabled()) {
                return;
            }
 
            // if we are showing a feature that should be announced and one child
            // make this child the event source since this is the feature itself
            // otherwise the callback will take over and announce its client
            if ((mFeatureId == FEATURE_OPTIONS_PANEL ||
                    mFeatureId == FEATURE_CONTEXT_MENU ||
                    mFeatureId == FEATURE_PROGRESS ||
                    mFeatureId == FEATURE_INDETERMINATE_PROGRESS)
                    && getChildCount() == 1) {
                getChildAt(0).sendAccessibilityEvent(eventType);
            } else {
                super.sendAccessibilityEvent(eventType);
            }
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            final Callback cb = getCallback();
            if (cb != null && !isDestroyed()) {
                if (cb.dispatchPopulateAccessibilityEvent(event)) {
                    return true;
                }
            }
            return super.dispatchPopulateAccessibilityEvent(event);
        }

        @Override
        protected boolean setFrame(int l, int t, int r, int b) {
            boolean changed = super.setFrame(l, t, r, b);
            if (changed) {
                final Rect drawingBounds = mDrawingBounds;
                getDrawingRect(drawingBounds);

                Drawable fg = getForeground();
                if (fg != null) {
                    final Rect frameOffsets = mFrameOffsets;
                    drawingBounds.left += frameOffsets.left;
                    drawingBounds.top += frameOffsets.top;
                    drawingBounds.right -= frameOffsets.right;
                    drawingBounds.bottom -= frameOffsets.bottom;
                    fg.setBounds(drawingBounds);
                    final Rect framePadding = mFramePadding;
                    drawingBounds.left += framePadding.left - frameOffsets.left;
                    drawingBounds.top += framePadding.top - frameOffsets.top;
                    drawingBounds.right -= framePadding.right - frameOffsets.right;
                    drawingBounds.bottom -= framePadding.bottom - frameOffsets.bottom;
                }

                Drawable bg = getBackground();
                if (bg != null) {
                    bg.setBounds(drawingBounds);
                }

                if (SWEEP_OPEN_MENU) {
                    if (mMenuBackground == null && mFeatureId < 0
                            && getAttributes().height
                            == WindowManager.LayoutParams.MATCH_PARENT) {
                        mMenuBackground = getContext().getResources().getDrawable(
                                com.android.internal.R.drawable.menu_background);
                    }
                    if (mMenuBackground != null) {
                        mMenuBackground.setBounds(drawingBounds.left,
                                drawingBounds.bottom-6, drawingBounds.right,
                                drawingBounds.bottom+20);
                    }
                }
            }
            return changed;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
            final boolean isPortrait = metrics.widthPixels < metrics.heightPixels;

            final int widthMode = getMode(widthMeasureSpec);
            final int heightMode = getMode(heightMeasureSpec);

            boolean fixedWidth = false;
            if (widthMode == AT_MOST) {
                final TypedValue tvw = isPortrait ? mFixedWidthMinor : mFixedWidthMajor;
                if (tvw != null && tvw.type != TypedValue.TYPE_NULL) {
                    final int w;
                    if (tvw.type == TypedValue.TYPE_DIMENSION) {
                        w = (int) tvw.getDimension(metrics);
                    } else if (tvw.type == TypedValue.TYPE_FRACTION) {
                        w = (int) tvw.getFraction(metrics.widthPixels, metrics.widthPixels);
                    } else {
                        w = 0;
                    }

                    if (w > 0) {
                        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                        widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                                Math.min(w, widthSize), EXACTLY);
                        fixedWidth = true;
                    }
                }
            }

            if (heightMode == AT_MOST) {
                final TypedValue tvh = isPortrait ? mFixedHeightMajor : mFixedHeightMinor;
                if (tvh != null && tvh.type != TypedValue.TYPE_NULL) {
                    final int h;
                    if (tvh.type == TypedValue.TYPE_DIMENSION) {
                        h = (int) tvh.getDimension(metrics);
                    } else if (tvh.type == TypedValue.TYPE_FRACTION) {
                        h = (int) tvh.getFraction(metrics.heightPixels, metrics.heightPixels);
                    } else {
                        h = 0;
                    }

                    if (h > 0) {
                        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(
                                Math.min(h, heightSize), EXACTLY);
                    }
                }
            }

            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int width = getMeasuredWidth();
            boolean measure = false;

            widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, EXACTLY);

            if (!fixedWidth && widthMode == AT_MOST) {
                final TypedValue tv = isPortrait ? mMinWidthMinor : mMinWidthMajor;
                if (tv.type != TypedValue.TYPE_NULL) {
                    final int min;
                    if (tv.type == TypedValue.TYPE_DIMENSION) {
                        min = (int)tv.getDimension(metrics);
                    } else if (tv.type == TypedValue.TYPE_FRACTION) {
                        min = (int)tv.getFraction(metrics.widthPixels, metrics.widthPixels);
                    } else {
                        min = 0;
                    }

                    if (width < min) {
                        widthMeasureSpec = MeasureSpec.makeMeasureSpec(min, EXACTLY);
                        measure = true;
                    }
                }
            }

            // TODO: Support height?

            if (measure) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);

            if (mMenuBackground != null) {
                mMenuBackground.draw(canvas);
            }
        }


        @Override
        public boolean showContextMenuForChild(View originalView) {
            // Reuse the context menu builder
            if (mContextMenu == null) {
                mContextMenu = new ContextMenuBuilder(getContext());
                mContextMenu.setCallback(mContextMenuCallback);
            } else {
                mContextMenu.clearAll();
            }

            final MenuDialogHelper helper = mContextMenu.show(originalView,
                    originalView.getWindowToken());
            if (helper != null) {
                helper.setPresenterCallback(mContextMenuCallback);
            }
            mContextMenuHelper = helper;
            return helper != null;
        }

        @Override
        public ActionMode startActionModeForChild(View originalView,
                ActionMode.Callback callback) {
            // originalView can be used here to be sure that we don't obscure
            // relevant content with the context mode UI.
            return startActionMode(callback);
        }

        @Override
        public ActionMode startActionMode(ActionMode.Callback callback) {
            if (mActionMode != null) {
                mActionMode.finish();
            }

            final ActionMode.Callback wrappedCallback = new ActionModeCallbackWrapper(callback);
            ActionMode mode = null;
            if (getCallback() != null && !isDestroyed()) {
                try {
                    mode = getCallback().onWindowStartingActionMode(wrappedCallback);
                } catch (AbstractMethodError ame) {
                    // Older apps might not implement this callback method.
                }
            }
            if (mode != null) {
                mActionMode = mode;
            } else {
                if (mActionModeView == null) {
                    if (isFloating()) {
                        mActionModeView = new ActionBarContextView(mContext);
                        mActionModePopup = new PopupWindow(mContext, null,
                                com.android.internal.R.attr.actionModePopupWindowStyle);
                        mActionModePopup.setLayoutInScreenEnabled(true);
                        mActionModePopup.setLayoutInsetDecor(true);
                        mActionModePopup.setWindowLayoutType(
                                WindowManager.LayoutParams.TYPE_APPLICATION);
                        mActionModePopup.setContentView(mActionModeView);
                        mActionModePopup.setWidth(MATCH_PARENT);

                        TypedValue heightValue = new TypedValue();
                        mContext.getTheme().resolveAttribute(
                                com.android.internal.R.attr.actionBarSize, heightValue, true);
                        final int height = TypedValue.complexToDimensionPixelSize(heightValue.data,
                                mContext.getResources().getDisplayMetrics());
                        mActionModeView.setContentHeight(height);
                        mActionModePopup.setHeight(WRAP_CONTENT);
                        mShowActionModePopup = new Runnable() {
                            public void run() {
                                mActionModePopup.showAtLocation(
                                        mActionModeView.getApplicationWindowToken(),
                                        Gravity.TOP | Gravity.FILL_HORIZONTAL, 0, 0);
                            }
                        };
                    } else {
                        ViewStub stub = (ViewStub) findViewById(
                                com.android.internal.R.id.action_mode_bar_stub);
                        if (stub != null) {
                            mActionModeView = (ActionBarContextView) stub.inflate();
                        }
                    }
                }

                if (mActionModeView != null) {
                    mActionModeView.killMode();
                    mode = new StandaloneActionMode(getContext(), mActionModeView, wrappedCallback,
                            mActionModePopup == null);
                    if (callback.onCreateActionMode(mode, mode.getMenu())) {
                        mode.invalidate();
                        mActionModeView.initForMode(mode);
                        mActionModeView.setVisibility(View.VISIBLE);
                        mActionMode = mode;
                        if (mActionModePopup != null) {
                            post(mShowActionModePopup);
                        }
                        mActionModeView.sendAccessibilityEvent(
                                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                    } else {
                        mActionMode = null;
                    }
                }
            }
            if (mActionMode != null && getCallback() != null && !isDestroyed()) {
                try {
                    getCallback().onActionModeStarted(mActionMode);
                } catch (AbstractMethodError ame) {
                    // Older apps might not implement this callback method.
                }
            }
            return mActionMode;
        }

        public void startChanging() {
            mChanging = true;
        }

        public void finishChanging() {
            mChanging = false;
            drawableChanged();
        }

        public void setWindowBackground(Drawable drawable) {
            if (getBackground() != drawable) {
                setBackgroundDrawable(drawable);
                if (drawable != null) {
                    drawable.getPadding(mBackgroundPadding);
                } else {
                    mBackgroundPadding.setEmpty();
                }
                drawableChanged();
            }
        }

        @Override
        public void setBackgroundDrawable(Drawable d) {
            super.setBackgroundDrawable(d);
            if (getWindowToken() != null) {
                updateWindowResizeState();
            }
        }

        public void setWindowFrame(Drawable drawable) {
            if (getForeground() != drawable) {
                setForeground(drawable);
                if (drawable != null) {
                    drawable.getPadding(mFramePadding);
                } else {
                    mFramePadding.setEmpty();
                }
                drawableChanged();
            }
        }

        @Override
        protected boolean fitSystemWindows(Rect insets) {
            mFrameOffsets.set(insets);
            if (getForeground() != null) {
                drawableChanged();
            }
            return super.fitSystemWindows(insets);
        }

        private void drawableChanged() {
            if (mChanging) {
                return;
            }

            setPadding(mFramePadding.left + mBackgroundPadding.left, mFramePadding.top
                    + mBackgroundPadding.top, mFramePadding.right + mBackgroundPadding.right,
                    mFramePadding.bottom + mBackgroundPadding.bottom);
            requestLayout();
            invalidate();

            int opacity = PixelFormat.OPAQUE;

            // Note: if there is no background, we will assume opaque. The
            // common case seems to be that an application sets there to be
            // no background so it can draw everything itself. For that,
            // we would like to assume OPAQUE and let the app force it to
            // the slower TRANSLUCENT mode if that is really what it wants.
            Drawable bg = getBackground();
            Drawable fg = getForeground();
            if (bg != null) {
                if (fg == null) {
                    opacity = bg.getOpacity();
                } else if (mFramePadding.left <= 0 && mFramePadding.top <= 0
                        && mFramePadding.right <= 0 && mFramePadding.bottom <= 0) {
                    // If the frame padding is zero, then we can be opaque
                    // if either the frame -or- the background is opaque.
                    int fop = fg.getOpacity();
                    int bop = bg.getOpacity();
                    if (false)
                        Log.v(TAG, "Background opacity: " + bop + ", Frame opacity: " + fop);
                    if (fop == PixelFormat.OPAQUE || bop == PixelFormat.OPAQUE) {
                        opacity = PixelFormat.OPAQUE;
                    } else if (fop == PixelFormat.UNKNOWN) {
                        opacity = bop;
                    } else if (bop == PixelFormat.UNKNOWN) {
                        opacity = fop;
                    } else {
                        opacity = Drawable.resolveOpacity(fop, bop);
                    }
                } else {
                    // For now we have to assume translucent if there is a
                    // frame with padding... there is no way to tell if the
                    // frame and background together will draw all pixels.
                    if (false)
                        Log.v(TAG, "Padding: " + mFramePadding);
                    opacity = PixelFormat.TRANSLUCENT;
                }
            }

            if (false)
                Log.v(TAG, "Background: " + bg + ", Frame: " + fg);
            if (false)
                Log.v(TAG, "Selected default opacity: " + opacity);

            mDefaultOpacity = opacity;
            if (mFeatureId < 0) {
                setDefaultWindowFormat(opacity);
            }
        }

        @Override
        public void onWindowFocusChanged(boolean hasWindowFocus) {
            super.onWindowFocusChanged(hasWindowFocus);

            // If the user is chording a menu shortcut, release the chord since
            // this window lost focus
            if (!hasWindowFocus && mPanelChordingKey != 0) {
                closePanel(FEATURE_OPTIONS_PANEL);
            }

            final Callback cb = getCallback();
            if (cb != null && !isDestroyed() && mFeatureId < 0) {
                cb.onWindowFocusChanged(hasWindowFocus);
            }
        }

        void updateWindowResizeState() {
            Drawable bg = getBackground();
            hackTurnOffWindowResizeAnim(bg == null || bg.getOpacity()
                    != PixelFormat.OPAQUE);
        }
        
        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            
            mSettingsObserver.observe();

            updateWindowResizeState();
            
            final Callback cb = getCallback();
            if (cb != null && !isDestroyed() && mFeatureId < 0) {
                cb.onAttachedToWindow();
            }

            if (mFeatureId == -1) {
                /*
                 * The main window has been attached, try to restore any panels
                 * that may have been open before. This is called in cases where
                 * an activity is being killed for configuration change and the
                 * menu was open. When the activity is recreated, the menu
                 * should be shown again.
                 */
                openPanelsAfterRestore();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            
            mSettingsObserver.unobserve();

            final Callback cb = getCallback();
            if (cb != null && mFeatureId < 0) {
                cb.onDetachedFromWindow();
            }

            if (mActionBar != null) {
                mActionBar.dismissPopupMenus();
            }

            if (mActionModePopup != null) {
                removeCallbacks(mShowActionModePopup);
                if (mActionModePopup.isShowing()) {
                    mActionModePopup.dismiss();
                }
                mActionModePopup = null;
            }

            PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, false);
            if (st != null && st.menu != null && mFeatureId < 0) {
                st.menu.close();
            }
        }

        @Override
        public void onCloseSystemDialogs(String reason) {
            if (mFeatureId >= 0) {
                closeAllPanels();
            }
        }

        public android.view.SurfaceHolder.Callback2 willYouTakeTheSurface() {
            return mFeatureId < 0 ? mTakeSurfaceCallback : null;
        }
        
        public InputQueue.Callback willYouTakeTheInputQueue() {
            return mFeatureId < 0 ? mTakeInputQueueCallback : null;
        }
        
        public void setSurfaceType(int type) {
            PhoneWindow.this.setType(type);
        }
        
        public void setSurfaceFormat(int format) {
            PhoneWindow.this.setFormat(format);
        }
        
        public void setSurfaceKeepScreenOn(boolean keepOn) {
            if (keepOn) PhoneWindow.this.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else PhoneWindow.this.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        /**
         * Clears out internal reference when the action mode is destroyed.
         */
        private class ActionModeCallbackWrapper implements ActionMode.Callback {
            private ActionMode.Callback mWrapped;

            public ActionModeCallbackWrapper(ActionMode.Callback wrapped) {
                mWrapped = wrapped;
            }

            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return mWrapped.onCreateActionMode(mode, menu);
            }

            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return mWrapped.onPrepareActionMode(mode, menu);
            }

            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return mWrapped.onActionItemClicked(mode, item);
            }

            public void onDestroyActionMode(ActionMode mode) {
                mWrapped.onDestroyActionMode(mode);
                if (mActionModePopup != null) {
                    removeCallbacks(mShowActionModePopup);
                    mActionModePopup.dismiss();
                } else if (mActionModeView != null) {
                    mActionModeView.setVisibility(GONE);
                }
                if (mActionModeView != null) {
                    mActionModeView.removeAllViews();
                }
                if (getCallback() != null && !isDestroyed()) {
                    try {
                        getCallback().onActionModeFinished(mActionMode);
                    } catch (AbstractMethodError ame) {
                        // Older apps might not implement this callback method.
                    }
                }
                mActionMode = null;
            }
        }
    }

    protected DecorView generateDecor() {
        return new DecorView(getContext(), -1);
    }

    protected void setFeatureFromAttrs(int featureId, TypedArray attrs,
            int drawableAttr, int alphaAttr) {
        Drawable d = attrs.getDrawable(drawableAttr);
        if (d != null) {
            requestFeature(featureId);
            setFeatureDefaultDrawable(featureId, d);
        }
        if ((getFeatures() & (1 << featureId)) != 0) {
            int alpha = attrs.getInt(alphaAttr, -1);
            if (alpha >= 0) {
                setFeatureDrawableAlpha(featureId, alpha);
            }
        }
    }

    protected ViewGroup generateLayout(DecorView decor) {
        // Apply data from current theme.

        TypedArray a = getWindowStyle();

        if (false) {
            System.out.println("From style:");
            String s = "Attrs:";
            for (int i = 0; i < com.android.internal.R.styleable.Window.length; i++) {
                s = s + " " + Integer.toHexString(com.android.internal.R.styleable.Window[i]) + "="
                        + a.getString(i);
            }
            System.out.println(s);
        }

        mIsFloating = a.getBoolean(com.android.internal.R.styleable.Window_windowIsFloating, false);
        int flagsToUpdate = (FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_INSET_DECOR)
                & (~getForcedWindowFlags());
        if (mIsFloating) {
            setLayout(WRAP_CONTENT, WRAP_CONTENT);
            setFlags(0, flagsToUpdate);
        } else {
            setFlags(FLAG_LAYOUT_IN_SCREEN|FLAG_LAYOUT_INSET_DECOR, flagsToUpdate);
        }

        if (a.getBoolean(com.android.internal.R.styleable.Window_windowNoTitle, false)) {
            requestFeature(FEATURE_NO_TITLE);
        } else if (a.getBoolean(com.android.internal.R.styleable.Window_windowActionBar, false)) {
            // Don't allow an action bar if there is no title.
            requestFeature(FEATURE_ACTION_BAR);
        }

        if (a.getBoolean(com.android.internal.R.styleable.Window_windowActionBarOverlay, false)) {
            requestFeature(FEATURE_ACTION_BAR_OVERLAY);
        }

        if (a.getBoolean(com.android.internal.R.styleable.Window_windowActionModeOverlay, false)) {
            requestFeature(FEATURE_ACTION_MODE_OVERLAY);
        }

        if (a.getBoolean(com.android.internal.R.styleable.Window_windowFullscreen, false)) {
            setFlags(FLAG_FULLSCREEN, FLAG_FULLSCREEN&(~getForcedWindowFlags()));
        }

        if (a.getBoolean(com.android.internal.R.styleable.Window_windowShowWallpaper, false)) {
            setFlags(FLAG_SHOW_WALLPAPER, FLAG_SHOW_WALLPAPER&(~getForcedWindowFlags()));
        }

        if (a.getBoolean(com.android.internal.R.styleable.Window_windowEnableSplitTouch,
                getContext().getApplicationInfo().targetSdkVersion
                        >= android.os.Build.VERSION_CODES.HONEYCOMB)) {
            setFlags(FLAG_SPLIT_TOUCH, FLAG_SPLIT_TOUCH&(~getForcedWindowFlags()));
        }

        a.getValue(com.android.internal.R.styleable.Window_windowMinWidthMajor, mMinWidthMajor);
        a.getValue(com.android.internal.R.styleable.Window_windowMinWidthMinor, mMinWidthMinor);
        if (a.hasValue(com.android.internal.R.styleable.Window_windowFixedWidthMajor)) {
            if (mFixedWidthMajor == null) mFixedWidthMajor = new TypedValue();
            a.getValue(com.android.internal.R.styleable.Window_windowFixedWidthMajor,
                    mFixedWidthMajor);
        }
        if (a.hasValue(com.android.internal.R.styleable.Window_windowFixedWidthMinor)) {
            if (mFixedWidthMinor == null) mFixedWidthMinor = new TypedValue();
            a.getValue(com.android.internal.R.styleable.Window_windowFixedWidthMinor,
                    mFixedWidthMinor);
        }
        if (a.hasValue(com.android.internal.R.styleable.Window_windowFixedHeightMajor)) {
            if (mFixedHeightMajor == null) mFixedHeightMajor = new TypedValue();
            a.getValue(com.android.internal.R.styleable.Window_windowFixedHeightMajor,
                    mFixedHeightMajor);
        }
        if (a.hasValue(com.android.internal.R.styleable.Window_windowFixedHeightMinor)) {
            if (mFixedHeightMinor == null) mFixedHeightMinor = new TypedValue();
            a.getValue(com.android.internal.R.styleable.Window_windowFixedHeightMinor,
                    mFixedHeightMinor);
        }

        final Context context = getContext();
        final int targetSdk = context.getApplicationInfo().targetSdkVersion;
        final boolean targetPreHoneycomb = targetSdk < android.os.Build.VERSION_CODES.HONEYCOMB;
        final boolean targetPreIcs = targetSdk < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH;
        final boolean targetHcNeedsOptions = context.getResources().getBoolean(
                com.android.internal.R.bool.target_honeycomb_needs_options_menu);
        final boolean noActionBar = !hasFeature(FEATURE_ACTION_BAR) || hasFeature(FEATURE_NO_TITLE);

        if (targetPreHoneycomb || (targetPreIcs && targetHcNeedsOptions && noActionBar)) {
            addFlags(WindowManager.LayoutParams.FLAG_NEEDS_MENU_KEY);
        } else {
            clearFlags(WindowManager.LayoutParams.FLAG_NEEDS_MENU_KEY);
        }
        
        if (mAlwaysReadCloseOnTouchAttr || getContext().getApplicationInfo().targetSdkVersion
                >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            if (a.getBoolean(
                    com.android.internal.R.styleable.Window_windowCloseOnTouchOutside,
                    false)) {
                setCloseOnTouchOutsideIfNotSet(true);
            }
        }
        
        WindowManager.LayoutParams params = getAttributes();

        if (!hasSoftInputMode()) {
            params.softInputMode = a.getInt(
                    com.android.internal.R.styleable.Window_windowSoftInputMode,
                    params.softInputMode);
        }

        if (a.getBoolean(com.android.internal.R.styleable.Window_backgroundDimEnabled,
                mIsFloating)) {
            /* All dialogs should have the window dimmed */
            if ((getForcedWindowFlags()&WindowManager.LayoutParams.FLAG_DIM_BEHIND) == 0) {
                params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            }
            if (!haveDimAmount()) {
                params.dimAmount = a.getFloat(
                        android.R.styleable.Window_backgroundDimAmount, 0.5f);
            }
        }

        if (params.windowAnimations == 0) {
            params.windowAnimations = a.getResourceId(
                    com.android.internal.R.styleable.Window_windowAnimationStyle, 0);
        }

        // The rest are only done if this window is not embedded; otherwise,
        // the values are inherited from our container.
        if (getContainer() == null) {
            if (mBackgroundDrawable == null) {
                if (mBackgroundResource == 0) {
                    mBackgroundResource = a.getResourceId(
                            com.android.internal.R.styleable.Window_windowBackground, 0);
                }
                if (mFrameResource == 0) {
                    mFrameResource = a.getResourceId(com.android.internal.R.styleable.Window_windowFrame, 0);
                }
                if (false) {
                    System.out.println("Background: "
                            + Integer.toHexString(mBackgroundResource) + " Frame: "
                            + Integer.toHexString(mFrameResource));
                }
            }
            mTextColor = a.getColor(com.android.internal.R.styleable.Window_textColor, 0xFF000000);
        }

        // Inflate the window decor.

        int layoutResource;
        int features = getLocalFeatures();
        // System.out.println("Features: 0x" + Integer.toHexString(features));
        if ((features & ((1 << FEATURE_LEFT_ICON) | (1 << FEATURE_RIGHT_ICON))) != 0) {
            if (mIsFloating) {
                TypedValue res = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        com.android.internal.R.attr.dialogTitleIconsDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else {
                layoutResource = com.android.internal.R.layout.screen_title_icons;
            }
            // XXX Remove this once action bar supports these features.
            removeFeature(FEATURE_ACTION_BAR);
            // System.out.println("Title Icons!");
        } else if ((features & ((1 << FEATURE_PROGRESS) | (1 << FEATURE_INDETERMINATE_PROGRESS))) != 0
                && (features & (1 << FEATURE_ACTION_BAR)) == 0) {
            // Special case for a window with only a progress bar (and title).
            // XXX Need to have a no-title version of embedded windows.
            layoutResource = com.android.internal.R.layout.screen_progress;
            // System.out.println("Progress!");
        } else if ((features & (1 << FEATURE_CUSTOM_TITLE)) != 0) {
            // Special case for a window with a custom title.
            // If the window is floating, we need a dialog layout
            if (mIsFloating) {
                TypedValue res = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        com.android.internal.R.attr.dialogCustomTitleDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else {
                layoutResource = com.android.internal.R.layout.screen_custom_title;
            }
            // XXX Remove this once action bar supports these features.
            removeFeature(FEATURE_ACTION_BAR);
        } else if ((features & (1 << FEATURE_NO_TITLE)) == 0) {
            // If no other features and not embedded, only need a title.
            // If the window is floating, we need a dialog layout
            if (mIsFloating) {
                TypedValue res = new TypedValue();
                getContext().getTheme().resolveAttribute(
                        com.android.internal.R.attr.dialogTitleDecorLayout, res, true);
                layoutResource = res.resourceId;
            } else if ((features & (1 << FEATURE_ACTION_BAR)) != 0) {
                if ((features & (1 << FEATURE_ACTION_BAR_OVERLAY)) != 0) {
                    layoutResource = com.android.internal.R.layout.screen_action_bar_overlay;
                } else {
                    layoutResource = com.android.internal.R.layout.screen_action_bar;
                }
            } else {
                layoutResource = com.android.internal.R.layout.screen_title;
            }
            // System.out.println("Title!");
        } else if ((features & (1 << FEATURE_ACTION_MODE_OVERLAY)) != 0) {
            layoutResource = com.android.internal.R.layout.screen_simple_overlay_action_mode;
        } else {
            // Embedded, so no decoration is needed.
            layoutResource = com.android.internal.R.layout.screen_simple;
            // System.out.println("Simple!");
        }

        mDecor.startChanging();

        View in = mLayoutInflater.inflate(layoutResource, null);
        decor.addView(in, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        ViewGroup contentParent = (ViewGroup)findViewById(ID_ANDROID_CONTENT);
        if (contentParent == null) {
            throw new RuntimeException("Window couldn't find content container view");
        }

        if ((features & (1 << FEATURE_INDETERMINATE_PROGRESS)) != 0) {
            ProgressBar progress = getCircularProgressBar(false);
            if (progress != null) {
                progress.setIndeterminate(true);
            }
        }

        // Remaining setup -- of background and title -- that only applies
        // to top-level windows.
        if (getContainer() == null) {
            Drawable drawable = mBackgroundDrawable;
            if (mBackgroundResource != 0) {
                drawable = getContext().getResources().getDrawable(mBackgroundResource);
            }
            mDecor.setWindowBackground(drawable);
            drawable = null;
            if (mFrameResource != 0) {
                drawable = getContext().getResources().getDrawable(mFrameResource);
            }
            mDecor.setWindowFrame(drawable);

            // System.out.println("Text=" + Integer.toHexString(mTextColor) +
            // " Sel=" + Integer.toHexString(mTextSelectedColor) +
            // " Title=" + Integer.toHexString(mTitleColor));

            if (mTitleColor == 0) {
                mTitleColor = mTextColor;
            }

            if (mTitle != null) {
                setTitle(mTitle);
            }
            setTitleColor(mTitleColor);
        }

        mDecor.finishChanging();

        return contentParent;
    }

    /** @hide */
    public void alwaysReadCloseOnTouchAttr() {
        mAlwaysReadCloseOnTouchAttr = true;
    }

    private void installDecor() {
        if (mDecor == null) {
            mDecor = generateDecor();
            mDecor.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            mDecor.setIsRootNamespace(true);
            if (!mInvalidatePanelMenuPosted && mInvalidatePanelMenuFeatures != 0) {
                mDecor.postOnAnimation(mInvalidatePanelMenuRunnable);
            }
        }
        if (mContentParent == null) {
            mContentParent = generateLayout(mDecor);

            // Set up decor part of UI to ignore fitsSystemWindows if appropriate.
            mDecor.makeOptionalFitsSystemWindows();

            mTitleView = (TextView)findViewById(com.android.internal.R.id.title);
            if (mTitleView != null) {
                mTitleView.setLayoutDirection(mDecor.getLayoutDirection());
                if ((getLocalFeatures() & (1 << FEATURE_NO_TITLE)) != 0) {
                    View titleContainer = findViewById(com.android.internal.R.id.title_container);
                    if (titleContainer != null) {
                        titleContainer.setVisibility(View.GONE);
                    } else {
                        mTitleView.setVisibility(View.GONE);
                    }
                    if (mContentParent instanceof FrameLayout) {
                        ((FrameLayout)mContentParent).setForeground(null);
                    }
                } else {
                    mTitleView.setText(mTitle);
                }
            } else {
                mActionBar = (ActionBarView) findViewById(com.android.internal.R.id.action_bar);
                if (mActionBar != null) {
                    mActionBar.setWindowCallback(getCallback());
                    if (mActionBar.getTitle() == null) {
                        mActionBar.setWindowTitle(mTitle);
                    }
                    final int localFeatures = getLocalFeatures();
                    if ((localFeatures & (1 << FEATURE_PROGRESS)) != 0) {
                        mActionBar.initProgress();
                    }
                    if ((localFeatures & (1 << FEATURE_INDETERMINATE_PROGRESS)) != 0) {
                        mActionBar.initIndeterminateProgress();
                    }

                    boolean splitActionBar = false;
                    final boolean splitWhenNarrow =
                            (mUiOptions & ActivityInfo.UIOPTION_SPLIT_ACTION_BAR_WHEN_NARROW) != 0;
                    if (splitWhenNarrow) {
                        splitActionBar = getContext().getResources().getBoolean(
                                com.android.internal.R.bool.split_action_bar_is_narrow);
                    } else {
                        splitActionBar = getWindowStyle().getBoolean(
                                com.android.internal.R.styleable.Window_windowSplitActionBar, false);
                    }
                    final ActionBarContainer splitView = (ActionBarContainer) findViewById(
                            com.android.internal.R.id.split_action_bar);
                    if (splitView != null) {
                        mActionBar.setSplitView(splitView);
                        mActionBar.setSplitActionBar(splitActionBar);
                        mActionBar.setSplitWhenNarrow(splitWhenNarrow);

                        final ActionBarContextView cab = (ActionBarContextView) findViewById(
                                com.android.internal.R.id.action_context_bar);
                        cab.setSplitView(splitView);
                        cab.setSplitActionBar(splitActionBar);
                        cab.setSplitWhenNarrow(splitWhenNarrow);
                    } else if (splitActionBar) {
                        Log.e(TAG, "Requested split action bar with " +
                                "incompatible window decor! Ignoring request.");
                    }

                    // Post the panel invalidate for later; avoid application onCreateOptionsMenu
                    // being called in the middle of onCreate or similar.
                    mDecor.post(new Runnable() {
                        public void run() {
                            // Invalidate if the panel menu hasn't been created before this.
                            PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, false);
                            if (!isDestroyed() && (st == null || st.menu == null)) {
                                invalidatePanelMenu(FEATURE_ACTION_BAR);
                            }
                        }
                    });
                }
            }
        }
    }

    private Drawable loadImageURI(Uri uri) {
        try {
            return Drawable.createFromStream(
                    getContext().getContentResolver().openInputStream(uri), null);
        } catch (Exception e) {
            Log.w(TAG, "Unable to open content: " + uri);
        }
        return null;
    }

    private DrawableFeatureState getDrawableState(int featureId, boolean required) {
        if ((getFeatures() & (1 << featureId)) == 0) {
            if (!required) {
                return null;
            }
            throw new RuntimeException("The feature has not been requested");
        }

        DrawableFeatureState[] ar;
        if ((ar = mDrawables) == null || ar.length <= featureId) {
            DrawableFeatureState[] nar = new DrawableFeatureState[featureId + 1];
            if (ar != null) {
                System.arraycopy(ar, 0, nar, 0, ar.length);
            }
            mDrawables = ar = nar;
        }

        DrawableFeatureState st = ar[featureId];
        if (st == null) {
            ar[featureId] = st = new DrawableFeatureState(featureId);
        }
        return st;
    }

    /**
     * Gets a panel's state based on its feature ID.
     *
     * @param featureId The feature ID of the panel.
     * @param required Whether the panel is required (if it is required and it
     *            isn't in our features, this throws an exception).
     * @return The panel state.
     */
    private PanelFeatureState getPanelState(int featureId, boolean required) {
        return getPanelState(featureId, required, null);
    }

    /**
     * Gets a panel's state based on its feature ID.
     *
     * @param featureId The feature ID of the panel.
     * @param required Whether the panel is required (if it is required and it
     *            isn't in our features, this throws an exception).
     * @param convertPanelState Optional: If the panel state does not exist, use
     *            this as the panel state.
     * @return The panel state.
     */
    private PanelFeatureState getPanelState(int featureId, boolean required,
            PanelFeatureState convertPanelState) {
        if ((getFeatures() & (1 << featureId)) == 0) {
            if (!required) {
                return null;
            }
            throw new RuntimeException("The feature has not been requested");
        }

        PanelFeatureState[] ar;
        if ((ar = mPanels) == null || ar.length <= featureId) {
            PanelFeatureState[] nar = new PanelFeatureState[featureId + 1];
            if (ar != null) {
                System.arraycopy(ar, 0, nar, 0, ar.length);
            }
            mPanels = ar = nar;
        }

        PanelFeatureState st = ar[featureId];
        if (st == null) {
            ar[featureId] = st = (convertPanelState != null)
                    ? convertPanelState
                    : new PanelFeatureState(featureId);
        }
        return st;
    }

    @Override
    public final void setChildDrawable(int featureId, Drawable drawable) {
        DrawableFeatureState st = getDrawableState(featureId, true);
        st.child = drawable;
        updateDrawable(featureId, st, false);
    }

    @Override
    public final void setChildInt(int featureId, int value) {
        updateInt(featureId, value, false);
    }

    @Override
    public boolean isShortcutKey(int keyCode, KeyEvent event) {
        PanelFeatureState st = getPanelState(FEATURE_OPTIONS_PANEL, true);
        return st.menu != null && st.menu.isShortcutKey(keyCode, event);
    }

    private void updateDrawable(int featureId, DrawableFeatureState st, boolean fromResume) {
        // Do nothing if the decor is not yet installed... an update will
        // need to be forced when we eventually become active.
        if (mContentParent == null) {
            return;
        }

        final int featureMask = 1 << featureId;

        if ((getFeatures() & featureMask) == 0 && !fromResume) {
            return;
        }

        Drawable drawable = null;
        if (st != null) {
            drawable = st.child;
            if (drawable == null)
                drawable = st.local;
            if (drawable == null)
                drawable = st.def;
        }
        if ((getLocalFeatures() & featureMask) == 0) {
            if (getContainer() != null) {
                if (isActive() || fromResume) {
                    getContainer().setChildDrawable(featureId, drawable);
                }
            }
        } else if (st != null && (st.cur != drawable || st.curAlpha != st.alpha)) {
            // System.out.println("Drawable changed: old=" + st.cur
            // + ", new=" + drawable);
            st.cur = drawable;
            st.curAlpha = st.alpha;
            onDrawableChanged(featureId, drawable, st.alpha);
        }
    }

    private void updateInt(int featureId, int value, boolean fromResume) {

        // Do nothing if the decor is not yet installed... an update will
        // need to be forced when we eventually become active.
        if (mContentParent == null) {
            return;
        }

        final int featureMask = 1 << featureId;

        if ((getFeatures() & featureMask) == 0 && !fromResume) {
            return;
        }

        if ((getLocalFeatures() & featureMask) == 0) {
            if (getContainer() != null) {
                getContainer().setChildInt(featureId, value);
            }
        } else {
            onIntChanged(featureId, value);
        }
    }

    private ImageView getLeftIconView() {
        if (mLeftIconView != null) {
            return mLeftIconView;
        }
        if (mContentParent == null) {
            installDecor();
        }
        return (mLeftIconView = (ImageView)findViewById(com.android.internal.R.id.left_icon));
    }

    private ProgressBar getCircularProgressBar(boolean shouldInstallDecor) {
        if (mCircularProgressBar != null) {
            return mCircularProgressBar;
        }
        if (mContentParent == null && shouldInstallDecor) {
            installDecor();
        }
        mCircularProgressBar = (ProgressBar) findViewById(com.android.internal.R.id.progress_circular);
        if (mCircularProgressBar != null) {
            mCircularProgressBar.setVisibility(View.INVISIBLE);
        }
        return mCircularProgressBar;
    }

    private ProgressBar getHorizontalProgressBar(boolean shouldInstallDecor) {
        if (mHorizontalProgressBar != null) {
            return mHorizontalProgressBar;
        }
        if (mContentParent == null && shouldInstallDecor) {
            installDecor();
        }
        mHorizontalProgressBar = (ProgressBar) findViewById(com.android.internal.R.id.progress_horizontal);
        if (mHorizontalProgressBar != null) {
            mHorizontalProgressBar.setVisibility(View.INVISIBLE);
        }
        return mHorizontalProgressBar;
    }

    private ImageView getRightIconView() {
        if (mRightIconView != null) {
            return mRightIconView;
        }
        if (mContentParent == null) {
            installDecor();
        }
        return (mRightIconView = (ImageView)findViewById(com.android.internal.R.id.right_icon));
    }

    /**
     * Helper method for calling the {@link Callback#onPanelClosed(int, Menu)}
     * callback. This method will grab whatever extra state is needed for the
     * callback that isn't given in the parameters. If the panel is not open,
     * this will not perform the callback.
     *
     * @param featureId Feature ID of the panel that was closed. Must be given.
     * @param panel Panel that was closed. Optional but useful if there is no
     *            menu given.
     * @param menu The menu that was closed. Optional, but give if you have.
     */
    private void callOnPanelClosed(int featureId, PanelFeatureState panel, Menu menu) {
        final Callback cb = getCallback();
        if (cb == null)
            return;

        // Try to get a menu
        if (menu == null) {
            // Need a panel to grab the menu, so try to get that
            if (panel == null) {
                if ((featureId >= 0) && (featureId < mPanels.length)) {
                    panel = mPanels[featureId];
                }
            }

            if (panel != null) {
                // menu still may be null, which is okay--we tried our best
                menu = panel.menu;
            }
        }

        // If the panel is not open, do not callback
        if ((panel != null) && (!panel.isOpen))
            return;

        if (!isDestroyed()) {
            cb.onPanelClosed(featureId, menu);
        }
    }

    /**
     * Helper method for adding launch-search to most applications. Opens the
     * search window using default settings.
     *
     * @return true if search window opened
     */
    private boolean launchDefaultSearch() {
        final Callback cb = getCallback();
        if (cb == null || isDestroyed()) {
            return false;
        } else {
            sendCloseSystemWindows("search");
            return cb.onSearchRequested();
        }
    }

    @Override
    public void setVolumeControlStream(int streamType) {
        mVolumeControlStreamType = streamType;
    }

    @Override
    public int getVolumeControlStream() {
        return mVolumeControlStreamType;
    }

    private static final class DrawableFeatureState {
        DrawableFeatureState(int _featureId) {
            featureId = _featureId;
        }

        final int featureId;

        int resid;

        Uri uri;

        Drawable local;

        Drawable child;

        Drawable def;

        Drawable cur;

        int alpha = 255;

        int curAlpha = 255;
    }

    private static final class PanelFeatureState {

        /** Feature ID for this panel. */
        int featureId;

        // Information pulled from the style for this panel.

        int background;

        /** The background when the panel spans the entire available width. */
        int fullBackground;

        int gravity;

        int x;

        int y;

        int windowAnimations;

        /** Dynamic state of the panel. */
        DecorView decorView;

        /** The panel that was returned by onCreatePanelView(). */
        View createdPanelView;

        /** The panel that we are actually showing. */
        View shownPanelView;

        /** Use {@link #setMenu} to set this. */
        MenuBuilder menu;

        IconMenuPresenter iconMenuPresenter;
        ListMenuPresenter listMenuPresenter;

        /** true if this menu will show in single-list compact mode */
        boolean isCompact;

        /** Theme resource ID for list elements of the panel menu */
        int listPresenterTheme;

        /**
         * Whether the panel has been prepared (see
         * {@link PhoneWindow#preparePanel}).
         */
        boolean isPrepared;

        /**
         * Whether an item's action has been performed. This happens in obvious
         * scenarios (user clicks on menu item), but can also happen with
         * chording menu+(shortcut key).
         */
        boolean isHandled;

        boolean isOpen;

        /**
         * True if the menu is in expanded mode, false if the menu is in icon
         * mode
         */
        boolean isInExpandedMode;

        public boolean qwertyMode;

        boolean refreshDecorView;

        boolean refreshMenuContent;
        
        boolean wasLastOpen;
        
        boolean wasLastExpanded;
        
        /**
         * Contains the state of the menu when told to freeze.
         */
        Bundle frozenMenuState;

        /**
         * Contains the state of associated action views when told to freeze.
         * These are saved across invalidations.
         */
        Bundle frozenActionViewState;

        PanelFeatureState(int featureId) {
            this.featureId = featureId;

            refreshDecorView = false;
        }

        public boolean isInListMode() {
            return isInExpandedMode || isCompact;
        }

        public boolean hasPanelItems() {
            if (shownPanelView == null) return false;
            if (createdPanelView != null) return true;

            if (isCompact || isInExpandedMode) {
                return listMenuPresenter.getAdapter().getCount() > 0;
            } else {
                return ((ViewGroup) shownPanelView).getChildCount() > 0;
            }
        }

        /**
         * Unregister and free attached MenuPresenters. They will be recreated as needed.
         */
        public void clearMenuPresenters() {
            if (menu != null) {
                menu.removeMenuPresenter(iconMenuPresenter);
                menu.removeMenuPresenter(listMenuPresenter);
            }
            iconMenuPresenter = null;
            listMenuPresenter = null;
        }

        void setStyle(Context context) {
            TypedArray a = context.obtainStyledAttributes(com.android.internal.R.styleable.Theme);
            background = a.getResourceId(
                    com.android.internal.R.styleable.Theme_panelBackground, 0);
            fullBackground = a.getResourceId(
                    com.android.internal.R.styleable.Theme_panelFullBackground, 0);
            windowAnimations = a.getResourceId(
                    com.android.internal.R.styleable.Theme_windowAnimationStyle, 0);
            isCompact = a.getBoolean(
                    com.android.internal.R.styleable.Theme_panelMenuIsCompact, false);
            listPresenterTheme = a.getResourceId(
                    com.android.internal.R.styleable.Theme_panelMenuListTheme,
                    com.android.internal.R.style.Theme_ExpandedMenu);
            a.recycle();
        }

        void setMenu(MenuBuilder menu) {
            if (menu == this.menu) return;

            if (this.menu != null) {
                this.menu.removeMenuPresenter(iconMenuPresenter);
                this.menu.removeMenuPresenter(listMenuPresenter);
            }
            this.menu = menu;
            if (menu != null) {
                if (iconMenuPresenter != null) menu.addMenuPresenter(iconMenuPresenter);
                if (listMenuPresenter != null) menu.addMenuPresenter(listMenuPresenter);
            }
        }

        MenuView getListMenuView(Context context, MenuPresenter.Callback cb) {
            if (menu == null) return null;

            if (!isCompact) {
                getIconMenuView(context, cb); // Need this initialized to know where our offset goes
            }

            if (listMenuPresenter == null) {
                listMenuPresenter = new ListMenuPresenter(
                        com.android.internal.R.layout.list_menu_item_layout, listPresenterTheme);
                listMenuPresenter.setCallback(cb);
                listMenuPresenter.setId(com.android.internal.R.id.list_menu_presenter);
                menu.addMenuPresenter(listMenuPresenter);
            }

            if (iconMenuPresenter != null) {
                listMenuPresenter.setItemIndexOffset(
                        iconMenuPresenter.getNumActualItemsShown());
            }
            MenuView result = listMenuPresenter.getMenuView(decorView);

            return result;
        }

        MenuView getIconMenuView(Context context, MenuPresenter.Callback cb) {
            if (menu == null) return null;

            if (iconMenuPresenter == null) {
                iconMenuPresenter = new IconMenuPresenter(context);
                iconMenuPresenter.setCallback(cb);
                iconMenuPresenter.setId(com.android.internal.R.id.icon_menu_presenter);
                menu.addMenuPresenter(iconMenuPresenter);
            }

            MenuView result = iconMenuPresenter.getMenuView(decorView);

            return result;
        }

        Parcelable onSaveInstanceState() {
            SavedState savedState = new SavedState();
            savedState.featureId = featureId;
            savedState.isOpen = isOpen;
            savedState.isInExpandedMode = isInExpandedMode;

            if (menu != null) {
                savedState.menuState = new Bundle();
                menu.savePresenterStates(savedState.menuState);
            }

            return savedState;
        }

        void onRestoreInstanceState(Parcelable state) {
            SavedState savedState = (SavedState) state;
            featureId = savedState.featureId;
            wasLastOpen = savedState.isOpen;
            wasLastExpanded = savedState.isInExpandedMode;
            frozenMenuState = savedState.menuState;

            /*
             * A LocalActivityManager keeps the same instance of this class around.
             * The first time the menu is being shown after restoring, the
             * Activity.onCreateOptionsMenu should be called. But, if it is the
             * same instance then menu != null and we won't call that method.
             * We clear any cached views here. The caller should invalidatePanelMenu.
             */
            createdPanelView = null;
            shownPanelView = null;
            decorView = null;
        }

        void applyFrozenState() {
            if (menu != null && frozenMenuState != null) {
                menu.restorePresenterStates(frozenMenuState);
                frozenMenuState = null;
            }
        }

        private static class SavedState implements Parcelable {
            int featureId;
            boolean isOpen;
            boolean isInExpandedMode;
            Bundle menuState;

            public int describeContents() {
                return 0;
            }

            public void writeToParcel(Parcel dest, int flags) {
                dest.writeInt(featureId);
                dest.writeInt(isOpen ? 1 : 0);
                dest.writeInt(isInExpandedMode ? 1 : 0);

                if (isOpen) {
                    dest.writeBundle(menuState);
                }
            }

            private static SavedState readFromParcel(Parcel source) {
                SavedState savedState = new SavedState();
                savedState.featureId = source.readInt();
                savedState.isOpen = source.readInt() == 1;
                savedState.isInExpandedMode = source.readInt() == 1;

                if (savedState.isOpen) {
                    savedState.menuState = source.readBundle();
                }

                return savedState;
            }

            public static final Parcelable.Creator<SavedState> CREATOR
                    = new Parcelable.Creator<SavedState>() {
                public SavedState createFromParcel(Parcel in) {
                    return readFromParcel(in);
                }

                public SavedState[] newArray(int size) {
                    return new SavedState[size];
                }
            };
        }

    }

    static class RotationWatcher extends IRotationWatcher.Stub {
        private Handler mHandler;
        private final Runnable mRotationChanged = new Runnable() {
            public void run() {
                dispatchRotationChanged();
            }
        };
        private final ArrayList<WeakReference<PhoneWindow>> mWindows =
                new ArrayList<WeakReference<PhoneWindow>>();
        private boolean mIsWatching;

        @Override
        public void onRotationChanged(int rotation) throws RemoteException {
            mHandler.post(mRotationChanged);
        }

        public void addWindow(PhoneWindow phoneWindow) {
            synchronized (mWindows) {
                if (!mIsWatching) {
                    try {
                        WindowManagerHolder.sWindowManager.watchRotation(this);
                        mHandler = new Handler();
                        mIsWatching = true;
                    } catch (RemoteException ex) {
                        Log.e(TAG, "Couldn't start watching for device rotation", ex);
                    }
                }
                mWindows.add(new WeakReference<PhoneWindow>(phoneWindow));
            }
        }

        public void removeWindow(PhoneWindow phoneWindow) {
            synchronized (mWindows) {
                int i = 0;
                while (i < mWindows.size()) {
                    final WeakReference<PhoneWindow> ref = mWindows.get(i);
                    final PhoneWindow win = ref.get();
                    if (win == null || win == phoneWindow) {
                        mWindows.remove(i);
                    } else {
                        i++;
                    }
                }
            }
        }

        void dispatchRotationChanged() {
            synchronized (mWindows) {
                int i = 0;
                while (i < mWindows.size()) {
                    final WeakReference<PhoneWindow> ref = mWindows.get(i);
                    final PhoneWindow win = ref.get();
                    if (win != null) {
                        win.onOptionsPanelRotationChanged();
                        i++;
                    } else {
                        mWindows.remove(i);
                    }
                }
            }
        }
    }

    /**
     * Simple implementation of MenuBuilder.Callback that:
     * <li> Opens a submenu when selected.
     * <li> Calls back to the callback's onMenuItemSelected when an item is
     * selected.
     */
    private final class DialogMenuCallback implements MenuBuilder.Callback, MenuPresenter.Callback {
        private int mFeatureId;
        private MenuDialogHelper mSubMenuHelper;

        public DialogMenuCallback(int featureId) {
            mFeatureId = featureId;
        }

        public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing) {
            if (menu.getRootMenu() != menu) {
                onCloseSubMenu(menu);
            }

            if (allMenusAreClosing) {
                Callback callback = getCallback();
                if (callback != null && !isDestroyed()) {
                    callback.onPanelClosed(mFeatureId, menu);
                }

                if (menu == mContextMenu) {
                    dismissContextMenu();
                }

                // Dismiss the submenu, if it is showing
                if (mSubMenuHelper != null) {
                    mSubMenuHelper.dismiss();
                    mSubMenuHelper = null;
                }
            }
        }

        public void onCloseSubMenu(MenuBuilder menu) {
            Callback callback = getCallback();
            if (callback != null && !isDestroyed()) {
                callback.onPanelClosed(mFeatureId, menu.getRootMenu());
            }
        }

        public boolean onMenuItemSelected(MenuBuilder menu, MenuItem item) {
            Callback callback = getCallback();
            return (callback != null && !isDestroyed())
                    && callback.onMenuItemSelected(mFeatureId, item);
        }

        public void onMenuModeChange(MenuBuilder menu) {
        }

        public boolean onOpenSubMenu(MenuBuilder subMenu) {
            if (subMenu == null) return false;

            // Set a simple callback for the submenu
            subMenu.setCallback(this);

            // The window manager will give us a valid window token
            mSubMenuHelper = new MenuDialogHelper(subMenu);
            mSubMenuHelper.show(null);

            return true;
        }
    }

    void sendCloseSystemWindows() {
        PhoneWindowManager.sendCloseSystemWindows(getContext(), null);
    }

    void sendCloseSystemWindows(String reason) {
        PhoneWindowManager.sendCloseSystemWindows(getContext(), reason);
    }
}
