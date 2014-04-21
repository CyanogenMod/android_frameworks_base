package com.android.systemui.statusbar.appcirclesidebar;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.*;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.Calendar;

public class AppCircleSidebar extends FrameLayout implements PackageAdapter.OnCircleItemClickListener,
                            CircleListView.OnItemCenteredListener {
    private static final String TAG = "AppCircleSidebar";
    private static final boolean DEBUG_LAYOUT = false;
    private static final long AUTO_HIDE_DELAY = 3000;

    private static final String ACTION_HIDE_APP_CONTAINER
            = "com.android.internal.policy.statusbar.HIDE_APP_CONTAINER";

    private static enum SIDEBAR_STATE { OPENING, OPENED, CLOSING, CLOSED };
    private SIDEBAR_STATE mState = SIDEBAR_STATE.CLOSED;

    private static final String DRAG_LABEL_SHORTCUT = "Dragging shortcut";

    private int mTriggerWidth;
    private CircleListView mCircleListView;
    private PackageAdapter mPackageAdapter;
    private Context mContext;
    private boolean mFirstTouch = false;
    private SettingsObserver mSettingsObserver;

    private PopupMenu mPopup;
    private WindowManager mWM;

    public AppCircleSidebar(Context context) {
        this(context, null);
    }

    public AppCircleSidebar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppCircleSidebar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mTriggerWidth = context.getResources().getDimensionPixelSize(R.dimen.app_sidebar_trigger_width);
        mContext = context;
        mWM = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mAM = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(ACTION_HIDE_APP_CONTAINER);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mAppChangeReceiver, filter);

        mCircleListView = (CircleListView) findViewById(R.id.circle_list);
        mPackageAdapter = new PackageAdapter(mContext);
        mPackageAdapter.setOnCircleItemClickListener(this);

        mCircleListView.setAdapter(mPackageAdapter);
        mCircleListView.setViewModifier(new CircularViewModifier());
        mCircleListView.setOnItemCenteredListener(this);
        mCircleListView.setVisibility(View.GONE);
        createAnimatimations();
        mSettingsObserver = new SettingsObserver(new Handler());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSettingsObserver != null) {
            mSettingsObserver.observe();
        }
        if (mPackageAdapter != null) {
            mPackageAdapter.reloadApplications();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mSettingsObserver != null) {
            mSettingsObserver.unobserve();
        }
    }

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ENABLE_APP_CIRCLE_BAR), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.WHITELIST_APP_CIRCLE_BAR), false, this);
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
            final ContentResolver resolver = mContext.getContentResolver();
            setAppBarVisibility(Settings.System.getIntForUser(
                    resolver, Settings.System.ENABLE_APP_CIRCLE_BAR, 0,
                    UserHandle.USER_CURRENT_OR_SELF) == 1);
            String includedApps = Settings.System.getStringForUser(resolver,
                    Settings.System.WHITELIST_APP_CIRCLE_BAR,
                    UserHandle.USER_CURRENT_OR_SELF);
            if (mPackageAdapter != null) {
                mPackageAdapter.createIncludedAppsSet(includedApps);
                mPackageAdapter.reloadApplications();
            }
        }
    }

    private void setAppBarVisibility(boolean enabled) {
        setVisibility(enabled ? View.VISIBLE : View.GONE);
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
                    mCircleListView.onTouchEvent(ev);
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
        return mCircleListView.onTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getKeyCode() == KEYCODE_BACK && event.getAction() == ACTION_DOWN &&
                mState == SIDEBAR_STATE.OPENED)
            showAppContainer(false);
        return super.dispatchKeyEventPreIme(event);
    }

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

    private int getWindowHeight() {
        Rect r = new Rect();
        getWindowVisibleDisplayFrame(r);
        return r.bottom - r.top;
    }

    private void expandFromRegion() {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
        params.y = 0;
        params.height = getWindowHeight();
        params.width = LayoutParams.WRAP_CONTENT;
        params.flags = enableKeyEvents();
        mWM.updateViewLayout(this, params);
    }

    private void reduceToRegion() {
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) getLayoutParams();
        params.y = 0;
        params.height = (getWindowHeight() / 2);
        params.width = mTriggerWidth;
        params.flags = disableKeyEvents();
        mWM.updateViewLayout(this, mLayoutParams);
    }

    private TranslateAnimation mSlideOut = new TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

    private TranslateAnimation mSlideIn = new TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

    private void createAnimatimations() {
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
        mState = show ? SIDEBAR_STATE.OPENING : SIDEBAR_STATE.CLOSING;
        if (show) {
            mCircleListView.setVisibility(View.VISIBLE);
            expandFromRegion();
        } else {
            if (mPopup != null) {
                mPopup.dismiss();
            }
            cancelAutoHideTimer();
        }
        mCircleListView.startAnimation(show ? mSlideIn : mSlideOut);
    }

    private Animation.AnimationListener mAnimListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            animation.cancel();
            mCircleListView.clearAnimation();
            switch (mState) {
                case CLOSING:
                    mState = SIDEBAR_STATE.CLOSED;
                    mCircleListView.setVisibility(View.GONE);
                    reduceToRegion();
                    break;
                case OPENING:
                    mState = SIDEBAR_STATE.OPENED;
                    mCircleListView.setVisibility(View.VISIBLE);
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

    private void updateAutoHideTimer(long delay) {
        Intent i = new Intent(ACTION_HIDE_APP_CONTAINER);

        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            mAM.cancel(pi);
        } catch (Exception e) {
        }
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis() + delay);
        mAM.set(AlarmManager.RTC, time.getTimeInMillis(), pi);
    }

    private void cancelAutoHideTimer() {
        Intent i = new Intent(ACTION_HIDE_APP_CONTAINER);

        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            mAM.cancel(pi);
        } catch (Exception e) {
        }
    }

    private final BroadcastReceiver mAppChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_PACKAGE_ADDED)
                    || action.equals(Intent.ACTION_PACKAGE_REMOVED)
                    || action.equals(Intent.ACTION_PACKAGE_CHANGED)) {
                if (mPackageAdapter != null) {
                    mPackageAdapter.reloadApplications();
                }
            }
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_HIDE_APP_CONTAINER)) {
                showAppContainer(false);
            } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                showAppContainer(false);
            }
        }
    };

    private void launchApplication(String packageName, String className) {
        updateAutoHideTimer(500);
        ComponentName cn = new ComponentName(packageName, className);
        Intent intent = Intent.makeMainActivity(cn);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void launchApplicationFromHistory(String packageName, String className) {
        updateAutoHideTimer(500);
        ComponentName cn = new ComponentName(packageName, className);
        Intent intent = Intent.makeMainActivity(cn);
        intent.setFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                           | Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    private void killApp(String packageName) {
       final ActivityManager am = (ActivityManager) mContext
                .getSystemService(Activity.ACTIVITY_SERVICE);
       am.forceStopPackage(packageName);
    }

    public void onItemCentered(View v) {
        updateAutoHideTimer(AUTO_HIDE_DELAY);
        /*if (v != null) {
            final int position = (Integer) v.getTag(R.id.key_position);
            final ResolveInfo info = (ResolveInfo) mPackageAdapter.getItem(position);
            if (info != null) {
                String packageName = info.activityInfo.packageName;
                launchApplicationFromHistory(info.activityInfo.packageName, info.activityInfo.name);
            }
        }*/
    }

    @Override
    public boolean onItemTouchCenteredEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mFirstTouch = false;
            if (mState != SIDEBAR_STATE.OPENED)
                return false;
        } else if (action == MotionEvent.ACTION_DOWN) {
            cancelAutoHideTimer();
        }
        return true;
    }

    @Override
    public void onClick(final View v, final BaseAdapter adapter) {

        final int position = (Integer) v.getTag(R.id.key_position);
        final ResolveInfo info = (ResolveInfo) adapter.getItem(position);

        if (v.equals(mCircleListView.findViewAtCenter())) {
            if (info != null) {
                launchApplication(info.activityInfo.packageName, info.activityInfo.name);
            }
        } else {
            mCircleListView.smoothScrollToView(v);
        }
    }

    @Override
    public void onLongClick(final View v, final BaseAdapter adapter) {

        final int position = (Integer) v.getTag(R.id.key_position);
        final ResolveInfo info = (ResolveInfo) adapter.getItem(position);
        if (info != null) {
            final String packageName = info.activityInfo.packageName;
            final PopupMenu popup = new PopupMenu(mContext, v);
            mPopup = popup;
            popup.getMenuInflater().inflate(R.menu.sidebar_popup_menu,
                   popup.getMenu());
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    } else if (item.getItemId() == R.id.sidebar_inspect_item) {
                        startApplicationDetailsActivity(packageName);
                    } else if (item.getItemId() == R.id.sidebar_stop_item) {
                        killApp(packageName);
                    } else {
                        return false;
                    }
                    return true;
                }
            });
            popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
                public void onDismiss(PopupMenu menu) {
                    mPopup = null;
                }
            });
            popup.show();
        }
    }

    private void startApplicationDetailsActivity(String packageName) {
        updateAutoHideTimer(500);
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts(
                        "package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(mContext)
                .addNextIntentWithParentStack(intent).startActivities();
        showAppContainer(false);
    }
}
