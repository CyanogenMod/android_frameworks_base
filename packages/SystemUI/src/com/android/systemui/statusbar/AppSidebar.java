/*
 * Copyright (C) 2013 The ChameleonOS Project
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

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils.TruncateAt;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.sidebar.*;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AppSidebar extends FrameLayout {
    private static final String TAG = "AppSidebar";
    private static final boolean DEBUG_LAYOUT = false;
    private static final long AUTO_HIDE_DELAY = 3000;

    // Sidebar positions
    public static final int SIDEBAR_POSITION_LEFT = 0;
    public static final int SIDEBAR_POSITION_RIGHT = 1;

    private static final String ACTION_HIDE_APP_CONTAINER
            = "com.android.internal.policy.statusbar.HIDE_APP_CONTAINER";

    public static final String ACTION_SIDEBAR_ITEMS_CHANGED
            = "com.android.internal.policy.statusbar.SIDEBAR_ITEMS_CHANGED";

    private static enum SIDEBAR_STATE { OPENING, OPENED, CLOSING, CLOSED };
    private SIDEBAR_STATE mState = SIDEBAR_STATE.CLOSED;

    private static final LinearLayout.LayoutParams SCROLLVIEW_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1.0f    );

    private static LinearLayout.LayoutParams ITEM_LAYOUT_PARAMS = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1.0f
    );

    private int mTriggerWidth;
    private int mTriggerTop;
    private int mTriggerBottom;
    private int mTriggerColor;
    private LinearLayout mAppContainer;
    private SnappingScrollView mScrollView;
    private List<View> mContainerItems;
    private Rect mIconBounds;
    private int mItemTextSize;
    private float mBarAlpha = 1f;
    private int mFolderWidth;
    private Folder mFolder;
    private boolean mFirstTouch = false;
    private boolean mHideTextLabels = false;
    private boolean mUseTab = false;
    private int mPosition = SIDEBAR_POSITION_RIGHT;
    private int mBarHeight;

    private TranslateAnimation mSlideIn;
    private TranslateAnimation mSlideOut;

    private Context mContext;
    private SettingsObserver mSettingsObserver;
    private PackageManager mPm;
    private WindowManager mWm;

    public AppSidebar(Context context) {
        this(context, null);
    }

    public AppSidebar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppSidebar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTriggerWidth = context.getResources().getDimensionPixelSize(R.dimen.config_app_sidebar_trigger_width);
        mContext = context;
        Resources resources = context.getResources();
        mItemTextSize = resources.getDimensionPixelSize(R.dimen.item_title_text_size);
        mFolderWidth = resources.getDimensionPixelSize(R.dimen.folder_width);
        int iconSize = resources.getDimensionPixelSize(R.dimen.app_sidebar_item_size) - mItemTextSize;
        mIconBounds = new Rect(0, 0, iconSize, iconSize);
        mTriggerColor = resources.getColor(R.color.trigger_region_color);
        mPm = context.getPackageManager();
        mWm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mBarHeight = getWindowHeight();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (DEBUG_LAYOUT)
            setBackgroundColor(0xffff0000);
        setupAppContainer();
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_HIDE_APP_CONTAINER);
        filter.addAction(ACTION_SIDEBAR_ITEMS_CHANGED);
        getContext().registerReceiver(mBroadcastReceiver, filter);
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        getContext().registerReceiver(mAppChangeReceiver, filter);
        createSidebarAnimations(mPosition);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
        mContext.unregisterReceiver(mBroadcastReceiver);
        mContext.unregisterReceiver(mAppChangeReceiver);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
                if (mState == SIDEBAR_STATE.OPENED)
                    showAppContainer(false);
                break;
            case MotionEvent.ACTION_DOWN:
                if (isKeyguardEnabled())
                    return false;
                if (ev.getX() <= mTriggerWidth && mState == SIDEBAR_STATE.CLOSED) {
                    showAppContainer(true);
                    cancelAutoHideTimer();
                    mScrollView.onTouchEvent(ev);
                    mFirstTouch = true;
                } else
                    updateAutoHideTimer(AUTO_HIDE_DELAY);
                break;
            case MotionEvent.ACTION_MOVE:
                cancelAutoHideTimer();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateAutoHideTimer(AUTO_HIDE_DELAY);
                if (mState != SIDEBAR_STATE.CLOSED)
                    mState = SIDEBAR_STATE.OPENED;
                if (mFirstTouch) {
                    mFirstTouch = false;
                    return true;
                }
                break;
        }
        return false;
    }

    private void showTriggerRegion() {
        setBackgroundResource(R.drawable.trigger_region);
    }

    private void hideTriggerRegion() {
        setBackgroundColor(0x00000000);
    }

    private void setTopPercentage(float value) {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams)this.getLayoutParams();
        mTriggerTop = (int)(mBarHeight * value);
        params.y = mTriggerTop;
        params.height = mTriggerBottom;
        try {
            mWm.updateViewLayout(this, params);
        } catch (Exception e) {
        }
    }

    private void setBottomPercentage(float value) {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams)this.getLayoutParams();
        mTriggerBottom = (int)(mBarHeight * value);
        params.height = mTriggerBottom;
        try {
            mWm.updateViewLayout(this, params);
        } catch (Exception e) {
        }
    }

    private void setTriggerWidth(int value) {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams)this.getLayoutParams();
        mTriggerWidth = value;
        params.width = mTriggerWidth;
        try {
            mWm.updateViewLayout(this, params);
        } catch (Exception e) {
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_OUTSIDE:
                if (mState == SIDEBAR_STATE.OPENED)
                    showAppContainer(false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                updateAutoHideTimer(AUTO_HIDE_DELAY);
                break;
            case MotionEvent.ACTION_MOVE:
            default:
                cancelAutoHideTimer();
        }
        return mScrollView.onTouchEvent(ev);
    }

    private void createSidebarAnimations(int position) {
        if (position == SIDEBAR_POSITION_LEFT) {
            mSlideIn = new TranslateAnimation(
                    Animation.RELATIVE_TO_PARENT, -1.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

            mSlideOut = new TranslateAnimation(
                    Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, -1.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
        } else {
            mSlideIn = new TranslateAnimation(
                    Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

            mSlideOut = new TranslateAnimation(
                    Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f,
                    Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);
        }
        mSlideIn.setDuration(300);
        mSlideIn.setInterpolator(new DecelerateInterpolator());
        mSlideIn.setFillAfter(true);
        mSlideIn.setAnimationListener(mAnimListener);
        mSlideOut.setDuration(300);
        mSlideOut.setInterpolator(new DecelerateInterpolator());
        mSlideOut.setFillAfter(true);
        mSlideOut.setAnimationListener(mAnimListener);
    }

    private void showAppContainer(boolean show) {
        if (mScrollView == null)
            return;
        mState = show ? SIDEBAR_STATE.OPENING : SIDEBAR_STATE.CLOSING;
        if (show) {
            mScrollView.setVisibility(View.VISIBLE);
            expandFromTriggerRegion();
        } else {
            cancelAutoHideTimer();
            dismissFolderView();
        }
        mScrollView.startAnimation(show ? mSlideIn : mSlideOut);
    }

    private Animation.AnimationListener mAnimListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            animation.cancel();
            mScrollView.clearAnimation();
            switch (mState) {
                case CLOSING:
                    mState = SIDEBAR_STATE.CLOSED;
                    mScrollView.setVisibility(View.GONE);
                    reduceToTriggerRegion();
                    break;
                case OPENING:
                    mState = SIDEBAR_STATE.OPENED;
                    mScrollView.setVisibility(View.VISIBLE);
                    break;
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    private boolean isKeyguardEnabled() {
        KeyguardManager km = (KeyguardManager)mContext.getSystemService(Context.KEYGUARD_SERVICE);
        return km.inKeyguardRestrictedInputMode();
    }

    public void updateAutoHideTimer(long delay) {
        Context ctx = getContext();
        AlarmManager am = (AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_HIDE_APP_CONTAINER);

        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis() + delay);
        am.set(AlarmManager.RTC, time.getTimeInMillis(), pi);
    }

    public void cancelAutoHideTimer() {
        Context ctx = getContext();
        AlarmManager am = (AlarmManager)ctx.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_HIDE_APP_CONTAINER);

        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
    }

    private final BroadcastReceiver mAppChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_PACKAGE_ADDED.equals(action) ||
                    Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                setupAppContainer();
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_HIDE_APP_CONTAINER.equals(action)) {
                dismissFolderView();
                showAppContainer(false);
            } else if (ACTION_SIDEBAR_ITEMS_CHANGED.equals(action)) {
                if (mContainerItems != null) {
                    mContainerItems.clear();
                    setupAppContainer();
                }
            }
        }
    };

    private int enableKeyEvents() {
        return (0
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
    }

    private int disableKeyEvents() {
        return (0
                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH);
    }

    private void expandFromTriggerRegion() {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
        params.y = 0;
        Rect r = new Rect();
        getWindowVisibleDisplayFrame(r);
        mBarHeight = r.bottom - r.top;
        params.height = mBarHeight;
        params.width = LayoutParams.WRAP_CONTENT;
        params.flags = enableKeyEvents();
        mWm.updateViewLayout(this, params);
    }

    private void reduceToTriggerRegion() {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
        params.y = mTriggerTop;
        params.height = mTriggerBottom;
        params.width = mTriggerWidth;
        params.flags = disableKeyEvents();
        mWm.updateViewLayout(this, params);
    }

    private void setupAppContainer() {
        post(new Runnable() {
            public void run() {
                mContainerItems = new ArrayList<View>();
                loadSidebarContents();
                layoutItems();
            }
        });
    }

    private int getWindowHeight() {
        Rect r = new Rect();
        getWindowVisibleDisplayFrame(r);
        return r.bottom - r.top;
    }

    private void layoutItems() {
        int windowHeight = getWindowHeight();
        if (mScrollView != null)
            removeView(mScrollView);

        // create a linearlayout to hold our items
        if (mAppContainer == null) {
            mAppContainer = new LinearLayout(mContext);
            mAppContainer.setOrientation(LinearLayout.VERTICAL);
            mAppContainer.setGravity(Gravity.CENTER);
        }
        mAppContainer.removeAllViews();

        // set the layout height based on the item height we would like and the
        // number of items that would fit at on screen at once given the height
        // of the app sidebar
        int padding = mContext.getResources().getDimensionPixelSize(R.dimen.app_sidebar_item_padding);
        int desiredHeight = mContext.getResources().
                getDimensionPixelSize(R.dimen.app_sidebar_item_size) +
                padding * 2;
        int numItems = (int)Math.floor(windowHeight / desiredHeight);
        ITEM_LAYOUT_PARAMS.height = windowHeight / numItems;
        ITEM_LAYOUT_PARAMS.width = desiredHeight;

        for (View icon : mContainerItems) {
            ItemInfo ai = (ItemInfo)icon.getTag();
            if (ai instanceof AppItemInfo) {
                icon.setOnClickListener(mItemClickedListener);
                if (mHideTextLabels)
                    ((TextView)icon).setTextSize(0);
            } else {
                icon.setOnClickListener(new FolderClickListener());
                if (mHideTextLabels) {
                    ((FolderIcon)icon).setTextVisible(false);
                    ((FolderIcon)icon).setPreviewSize(mIconBounds.right);
                }
            }
            icon.setClickable(true);
            icon.setPadding(0, padding, 0, padding);
            mAppContainer.addView(icon, ITEM_LAYOUT_PARAMS);
        }

        // we need our horizontal scroll view to wrap the linear layout
        if (mScrollView == null) {
            mScrollView = new SnappingScrollView(mContext);
            // make the fading edge the size of a button (makes it more noticible that we can scroll
            mScrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            mScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            mScrollView.setBackgroundResource(R.drawable.app_sidebar_background);
        }
        mScrollView.removeAllViews();
        mScrollView.addView(mAppContainer, SCROLLVIEW_LAYOUT_PARAMS);
        addView(mScrollView, SCROLLVIEW_LAYOUT_PARAMS);
        mScrollView.setAlpha(mBarAlpha);
        mScrollView.setVisibility(View.GONE);
        mAppContainer.setFocusable(true);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KEYCODE_BACK && event.getAction() == ACTION_DOWN &&
                mState == SIDEBAR_STATE.OPENED)
            showAppContainer(false);
        return super.dispatchKeyEventPreIme(event);
    }

    private void launchApplication(AppItemInfo ai) {
        dismissFolderView();
        updateAutoHideTimer(500);
        ComponentName cn = new ComponentName(ai.packageName, ai.className);
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(cn);
        mContext.startActivity(intent);
    }

    private OnClickListener mItemClickedListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mState != SIDEBAR_STATE.OPENED || mFirstTouch) {
                mFirstTouch = false;
                return;
            }

            launchApplication((AppItemInfo)view.getTag());
        }
    };

    class SnappingScrollView extends ScrollView {

        private boolean mSnapTrigger = false;

        public SnappingScrollView(Context context) {
            super(context);
        }

        Runnable mSnapRunnable = new Runnable(){
            @Override
            public void run() {
                int mSelectedItem = ((getScrollY() + (ITEM_LAYOUT_PARAMS.height / 2)) / ITEM_LAYOUT_PARAMS.height);
                int scrollTo = mSelectedItem * ITEM_LAYOUT_PARAMS.height;
                smoothScrollTo(0, scrollTo);
                mSnapTrigger = false;
            }
        };

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (Math.abs(oldt - t) <= 1 && mSnapTrigger) {
                updateAutoHideTimer(AUTO_HIDE_DELAY);
                removeCallbacks(mSnapRunnable);
                postDelayed(mSnapRunnable, 100);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            int action = ev.getAction();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mSnapTrigger = true;
                mFirstTouch = false;
                if (mState != SIDEBAR_STATE.OPENED)
                    return false;
            } else if (action == MotionEvent.ACTION_DOWN) {
                mSnapTrigger = false;
                cancelAutoHideTimer();
            }
            return super.onTouchEvent(ev);
        }
    }

    public void setPosition(int position) {
        mPosition = position;
        createSidebarAnimations(position);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_ENABLED), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_TRANSPARENCY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_POSITION), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_DISABLE_LABELS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_TRIGGER_WIDTH), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_TRIGGER_TOP), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_TRIGGER_HEIGHT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.APP_SIDEBAR_SHOW_TRIGGER), false, this);
            update();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            boolean enabled = Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_ENABLED, 0) == 1;
            setVisibility(enabled ? View.VISIBLE : View.GONE);

            float barAlpha = (float)(100 - Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_TRANSPARENCY, 0)) / 100f;
            if (barAlpha != mBarAlpha) {
                if (mScrollView != null)
                    mScrollView.setAlpha(barAlpha);
                mBarAlpha = barAlpha;
            }

            int position = Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_POSITION, SIDEBAR_POSITION_LEFT);
            if (position != mPosition) {
                mPosition = position;
                createSidebarAnimations(position);
            }

            boolean hideLabels = Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_DISABLE_LABELS, 0) == 1;
            if (hideLabels != mHideTextLabels) {
                mHideTextLabels = hideLabels;
                if (mScrollView != null)
                    setupAppContainer();
            }

            int width = Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_TRIGGER_WIDTH, 10);
            if (mTriggerWidth != width)
                setTriggerWidth(width);
            setTopPercentage(Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_TRIGGER_TOP, 0) / 100f);
            setBottomPercentage(Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_TRIGGER_HEIGHT, 100) / 100f);
            if (Settings.System.getInt(
                    resolver, Settings.System.APP_SIDEBAR_SHOW_TRIGGER, 0) == 1)
                showTriggerRegion();
            else
                hideTriggerRegion();
        }
    }

    private final class FolderClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            if (mFirstTouch)
                return;
            if (mFolder != null) {
                dismissFolderView();
                return;
            }
            final Folder folder = mFolder = ((FolderIcon)v).getFolder();
            int iconY = v.getTop() - mScrollView.getScrollY();
            mWm.addView(mFolder, getFolderLayoutParams(iconY, folder.getHeight()));
            mFolder.setVisibility(View.VISIBLE);
            ArrayList<View> items = folder.getItemsInReadingOrder();
            updateAutoHideTimer(AUTO_HIDE_DELAY);
            for (View item : items)
                item.setOnClickListener(mItemClickedListener);
            folder.setOnTouchListener(new OnTouchListener() {

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                        dismissFolderView();
                        return true;
                    }
                    return false;
                }
            });
        }
    }

    private void dismissFolderView() {
        if (mFolder != null) {
            mWm.removeView(mFolder);
            mFolder = null;
            updateAutoHideTimer(AUTO_HIDE_DELAY);
        }
    }

    private void loadSidebarContents() {
        String[] projection = {
                SidebarTable.COLUMN_ITEM_ID,
                SidebarTable.COLUMN_ITEM_TYPE,
                SidebarTable.COLUMN_CONTAINER,
                SidebarTable.COLUMN_TITLE,
                SidebarTable.COLUMN_COMPONENT
        };
        ArrayList<ItemInfo> items = new ArrayList<ItemInfo>();
        Cursor cursor = mContext.getContentResolver().query(SidebarContentProvider.CONTENT_URI,
                projection, null, null, null);
        while (cursor.moveToNext()) {
            ItemInfo item;
            int type = cursor.getInt(cursor.getColumnIndex(SidebarTable.COLUMN_ITEM_TYPE));
            if (type == ItemInfo.TYPE_APPLICATION) {
                item = new AppItemInfo();
                String component = cursor.getString(4);
                ComponentName cn = ComponentName.unflattenFromString(component);
                ((AppItemInfo)item).packageName = cn.getPackageName();
                ((AppItemInfo)item).className = cn.getClassName();
            } else {
                item = new FolderInfo();
            }
            item.id = cursor.getInt(0);
            item.itemType = type;
            item.container = cursor.getInt(2);
            item.title = cursor.getString(3);
            if (item.container == ItemInfo.CONTAINER_SIDEBAR) {
                if (item instanceof AppItemInfo) {
                    TextView tv = createAppItem((AppItemInfo) item);
                    mContainerItems.add(tv);
                } else {
                    FolderIcon icon = FolderIcon.fromXml(R.layout.folder_icon,
                            mAppContainer, null, (FolderInfo)item, mContext, true);
                    mContainerItems.add(icon);
                }
            } else {
                try {
                    ((AppItemInfo)item).setIcon(mPm.getActivityIcon(
                            new ComponentName(((AppItemInfo)item).packageName, ((AppItemInfo)item).className)));
                } catch (NameNotFoundException e) {
                    ((AppItemInfo)item).setIcon(mPm.getDefaultActivityIcon());
                }
                ((AppItemInfo)item).icon.setBounds(mIconBounds);
                FolderInfo info = (FolderInfo) items.get(item.container);
                info.add((AppItemInfo) item);
            }
            items.add(item);
        }
    }

    private TextView createAppItem(AppItemInfo info) {
        TextView tv = new TextView(mContext);
        try {
            info.setIcon(mPm.getActivityIcon(new ComponentName(info.packageName, info.className)));
        } catch (NameNotFoundException e) {
            info.setIcon(mPm.getDefaultActivityIcon());
        }
        info.icon.setBounds(mIconBounds);
        tv.setCompoundDrawables(null, info.icon, null, null);
        tv.setTag(info);
        tv.setText(info.title);
        tv.setSingleLine(true);
        tv.setEllipsize(TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mItemTextSize);

        return tv;
    }

    private WindowManager.LayoutParams getFolderLayoutParams(int iconY, int folderHeight) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mFolderWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_STATUS_BAR_SUB_PANEL,
                0
                | WindowManager.LayoutParams.FLAG_TOUCHABLE_WHEN_WAKING
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        if (mPosition == SIDEBAR_POSITION_LEFT)
            lp.x = getWidth();
        else
            lp.x = mWm.getDefaultDisplay().getWidth() - getWidth() - mFolderWidth;
        if (iconY < 0)
            lp.y = 0;
        else {
            if (iconY + folderHeight < getHeight())
                lp.y = iconY;
            else
                lp.y = iconY - (getHeight() - (iconY + folderHeight));
        }
        lp.setTitle("SidebarFolder");
        return lp;
    }
}
