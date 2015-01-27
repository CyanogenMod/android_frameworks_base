/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.systemui.recent;

import android.animation.Animator;
import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.TaskStackBuilder;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewRootImpl;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.PopupMenu;
import android.widget.TextView;


import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.StatusBarPanel;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import java.util.ArrayList;

public class RecentsPanelView extends FrameLayout implements OnItemClickListener, RecentsCallback,
        StatusBarPanel, Animator.AnimatorListener {
    static final String TAG = "RecentsPanelView";
    static final boolean DEBUG = PhoneStatusBar.DEBUG || false;
    private static final String  ANDROID_SETTINGS = "com.android.settings";
    private static final String ANDROID_PROTECTED_APPS =
            "com.android.settings.applications.ProtectedAppsActivity";
    private PopupMenu mPopup;
    private View mRecentsScrim;
    private View mRecentsNoApps;
    private RecentsScrollView mRecentsContainer;

    private boolean mShowing;
    private boolean mWaitingToShow;
    private ViewHolder mItemToAnimateInWhenWindowAnimationIsFinished;
    private boolean mAnimateIconOfFirstTask;
    private boolean mWaitingForWindowAnimation;
    private long mWindowAnimationStartTime;
    private boolean mCallUiHiddenBeforeNextReload;

    private RecentTasksLoader mRecentTasksLoader;
    private ArrayList<TaskDescription> mRecentTaskDescriptions;
    private TaskDescriptionAdapter mListAdapter;
    private int mThumbnailWidth;
    private boolean mFitThumbnailToXY;
    private int mRecentItemLayoutId;
    private boolean mHighEndGfx;
    private ImageView mClearRecents;
    private ImageView mProtectedApps;

    public static interface RecentsScrollView {
        public int numItemsInOneScreenful();
        public void setAdapter(TaskDescriptionAdapter adapter);
        public void setCallback(RecentsCallback callback);
        public void setMinSwipeAlpha(float minAlpha);
        public View findViewForTask(int persistentTaskId);
        public void drawFadedEdges(Canvas c, int left, int right, int top, int bottom);
        public void setOnScrollListener(Runnable listener);
        public void removeAllViewsInLayout();
    }

    private final class OnLongClickDelegate implements View.OnLongClickListener {
        View mOtherView;
        OnLongClickDelegate(View other) {
            mOtherView = other;
        }
        public boolean onLongClick(View v) {
            return mOtherView.performLongClick();
        }
    }

    /* package */ final static class ViewHolder {
        View thumbnailView;
        ImageView thumbnailViewImage;
        Drawable thumbnailViewDrawable;
        ImageView iconView;
        TextView labelView;
        TextView descriptionView;
        View calloutLine;
        TaskDescription taskDescription;
        boolean loadedThumbnailAndIcon;
    }

    /* package */ final class TaskDescriptionAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        public TaskDescriptionAdapter(Context context) {
            mInflater = LayoutInflater.from(context);
        }

        public int getCount() {
            return mRecentTaskDescriptions != null ? mRecentTaskDescriptions.size() : 0;
        }

        public Object getItem(int position) {
            return position; // we only need the index
        }

        public long getItemId(int position) {
            return position; // we just need something unique for this position
        }

        public View createView(ViewGroup parent) {
            View convertView = mInflater.inflate(mRecentItemLayoutId, parent, false);
            ViewHolder holder = new ViewHolder();
            holder.thumbnailView = convertView.findViewById(R.id.app_thumbnail);
            holder.thumbnailViewImage =
                    (ImageView) convertView.findViewById(R.id.app_thumbnail_image);
            // If we set the default thumbnail now, we avoid an onLayout when we update
            // the thumbnail later (if they both have the same dimensions)
            updateThumbnail(holder, mRecentTasksLoader.getDefaultThumbnail(), false, false);
            holder.iconView = (ImageView) convertView.findViewById(R.id.app_icon);
            holder.iconView.setImageDrawable(mRecentTasksLoader.getDefaultIcon());
            holder.labelView = (TextView) convertView.findViewById(R.id.app_label);
            holder.calloutLine = convertView.findViewById(R.id.recents_callout_line);
            holder.descriptionView = (TextView) convertView.findViewById(R.id.app_description);

            convertView.setTag(holder);
            return convertView;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = createView(parent);
            }
            final ViewHolder holder = (ViewHolder) convertView.getTag();

            // index is reverse since most recent appears at the bottom...
            final int index = mRecentTaskDescriptions.size() - position - 1;

            final TaskDescription td = mRecentTaskDescriptions.get(index);

            holder.labelView.setText(td.getLabel());
            holder.thumbnailView.setContentDescription(td.getLabel());
            holder.loadedThumbnailAndIcon = td.isLoaded();
            if (td.isLoaded()) {
                updateThumbnail(holder, td.getThumbnail(), true, false);
                updateIcon(holder, td.getIcon(), true, false);
            }
            if (index == 0) {
                if (mAnimateIconOfFirstTask) {
                    ViewHolder oldHolder = mItemToAnimateInWhenWindowAnimationIsFinished;
                    if (oldHolder != null) {
                        oldHolder.iconView.setAlpha(1f);
                        oldHolder.iconView.setTranslationX(0f);
                        oldHolder.iconView.setTranslationY(0f);
                        oldHolder.labelView.setAlpha(1f);
                        oldHolder.labelView.setTranslationX(0f);
                        oldHolder.labelView.setTranslationY(0f);
                        if (oldHolder.calloutLine != null) {
                            oldHolder.calloutLine.setAlpha(1f);
                            oldHolder.calloutLine.setTranslationX(0f);
                            oldHolder.calloutLine.setTranslationY(0f);
                        }
                    }
                    mItemToAnimateInWhenWindowAnimationIsFinished = holder;
                    int translation = -getResources().getDimensionPixelSize(
                            R.dimen.status_bar_recents_app_icon_translate_distance);
                    final Configuration config = getResources().getConfiguration();
                    if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
                        if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                            translation = -translation;
                        }
                        holder.iconView.setAlpha(0f);
                        holder.iconView.setTranslationX(translation);
                        holder.labelView.setAlpha(0f);
                        holder.labelView.setTranslationX(translation);
                        holder.calloutLine.setAlpha(0f);
                        holder.calloutLine.setTranslationX(translation);
                    } else {
                        holder.iconView.setAlpha(0f);
                        holder.iconView.setTranslationY(translation);
                    }
                    if (!mWaitingForWindowAnimation) {
                        animateInIconOfFirstTask();
                    }
                }
            }

            holder.thumbnailView.setTag(td);
            holder.thumbnailView.setOnLongClickListener(new OnLongClickDelegate(convertView));
            holder.taskDescription = td;
            return convertView;
        }

        public void recycleView(View v) {
            ViewHolder holder = (ViewHolder) v.getTag();
            updateThumbnail(holder, mRecentTasksLoader.getDefaultThumbnail(), false, false);
            holder.iconView.setImageDrawable(mRecentTasksLoader.getDefaultIcon());
            holder.iconView.setVisibility(INVISIBLE);
            holder.iconView.animate().cancel();
            holder.labelView.setText(null);
            holder.labelView.animate().cancel();
            holder.thumbnailView.setContentDescription(null);
            holder.thumbnailView.setTag(null);
            holder.thumbnailView.setOnLongClickListener(null);
            holder.thumbnailView.setVisibility(INVISIBLE);
            holder.iconView.setAlpha(1f);
            holder.iconView.setTranslationX(0f);
            holder.iconView.setTranslationY(0f);
            holder.labelView.setAlpha(1f);
            holder.labelView.setTranslationX(0f);
            holder.labelView.setTranslationY(0f);
            if (holder.calloutLine != null) {
                holder.calloutLine.setAlpha(1f);
                holder.calloutLine.setTranslationX(0f);
                holder.calloutLine.setTranslationY(0f);
                holder.calloutLine.animate().cancel();
            }
            holder.taskDescription = null;
            holder.loadedThumbnailAndIcon = false;
        }
    }

    public RecentsPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecentsPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateValuesFromResources();

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RecentsPanelView,
                defStyle, 0);

        mRecentItemLayoutId = a.getResourceId(R.styleable.RecentsPanelView_recentItemLayout, 0);
        mRecentTasksLoader = RecentTasksLoader.getInstance(context);
        a.recycle();
    }

    public int numItemsInOneScreenful() {
        return mRecentsContainer.numItemsInOneScreenful();
    }

    private boolean pointInside(int x, int y, View v) {
        final int l = v.getLeft();
        final int r = v.getRight();
        final int t = v.getTop();
        final int b = v.getBottom();
        return x >= l && x < r && y >= t && y < b;
    }

    public boolean isInContentArea(int x, int y) {
        return pointInside(x, y, (View) mRecentsContainer);
    }

    public void show(boolean show) {
        show(show, null, false, false);
    }

    public void show(boolean show, ArrayList<TaskDescription> recentTaskDescriptions,
            boolean firstScreenful, boolean animateIconOfFirstTask) {
        if (show && mCallUiHiddenBeforeNextReload) {
            onUiHidden();
            recentTaskDescriptions = null;
            mAnimateIconOfFirstTask = false;
            mWaitingForWindowAnimation = false;
        } else {
            mAnimateIconOfFirstTask = animateIconOfFirstTask;
            mWaitingForWindowAnimation = animateIconOfFirstTask;
        }
        if (show) {
            mWaitingToShow = true;
            refreshRecentTasksList(recentTaskDescriptions, firstScreenful);
            showIfReady();
        } else {
            showImpl(false);
        }
    }

    private void showIfReady() {
        // mWaitingToShow => there was a touch up on the recents button
        // mRecentTaskDescriptions != null => we've created views for the first screenful of items
        if (mWaitingToShow && mRecentTaskDescriptions != null) {
            showImpl(true);
        }
    }

    static void sendCloseSystemWindows(Context context, String reason) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(reason);
            } catch (RemoteException e) {
            }
        }
    }

    private void showImpl(boolean show) {
        sendCloseSystemWindows(mContext, BaseStatusBar.SYSTEM_DIALOG_REASON_RECENT_APPS);

        mShowing = show;

        if (show) {
            // if there are no apps, bring up a "No recent apps" message
            boolean noApps = mRecentTaskDescriptions != null
                    && (mRecentTaskDescriptions.size() == 0);
            mRecentsNoApps.setAlpha(1f);
            mRecentsNoApps.setVisibility(noApps ? View.VISIBLE : View.INVISIBLE);
            mClearRecents.setVisibility(noApps ? View.GONE : View.VISIBLE);
            mProtectedApps.setVisibility(noProtectedApps() ? View.GONE : View.VISIBLE);
            onAnimationEnd(null);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        } else {
            mWaitingToShow = false;
            // call onAnimationEnd() and clearRecentTasksList() in onUiHidden()
            mCallUiHiddenBeforeNextReload = true;
            if (mPopup != null) {
                mPopup.dismiss();
            }
        }
    }

    private boolean noProtectedApps() {
        String protectedComponents = Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.PROTECTED_COMPONENTS);
        protectedComponents = protectedComponents == null ? "" : protectedComponents;
        return (protectedComponents.equals(""));
    }

    protected void onAttachedToWindow () {
        super.onAttachedToWindow();
        final ViewRootImpl root = getViewRootImpl();
        if (root != null) {
            root.setDrawDuringWindowsAnimating(true);
        }
    }

    public void onUiHidden() {
        mCallUiHiddenBeforeNextReload = false;
        if (!mShowing && mRecentTaskDescriptions != null) {
            onAnimationEnd(null);
            clearRecentTasksList();
        }
    }

    public void dismiss() {
        ((RecentsActivity) mContext).dismissAndGoHome();
    }

    public void dismissAndGoBack() {
        ((RecentsActivity) mContext).dismissAndGoBack();
    }

    public void onAnimationCancel(Animator animation) {
    }

    public void onAnimationEnd(Animator animation) {
        if (mShowing) {
            final LayoutTransition transitioner = new LayoutTransition();
            ((ViewGroup)mRecentsContainer).setLayoutTransition(transitioner);
            createCustomAnimations(transitioner);
        } else {
            ((ViewGroup)mRecentsContainer).setLayoutTransition(null);
        }
    }

    public void onAnimationRepeat(Animator animation) {
    }

    public void onAnimationStart(Animator animation) {
    }

    @Override
    public boolean dispatchHoverEvent(MotionEvent event) {
        // Ignore hover events outside of this panel bounds since such events
        // generate spurious accessibility events with the panel content when
        // tapping outside of it, thus confusing the user.
        final int x = (int) event.getX();
        final int y = (int) event.getY();
        if (x >= 0 && x < getWidth() && y >= 0 && y < getHeight()) {
            return super.dispatchHoverEvent(event);
        }
        return true;
    }

    /**
     * Whether the panel is showing, or, if it's animating, whether it will be
     * when the animation is done.
     */
    public boolean isShowing() {
        return mShowing;
    }

    public void setRecentTasksLoader(RecentTasksLoader loader) {
        mRecentTasksLoader = loader;
    }

    public void updateValuesFromResources() {
        final Resources res = mContext.getResources();
        mThumbnailWidth = Math.round(res.getDimension(R.dimen.status_bar_recents_thumbnail_width));
        mFitThumbnailToXY = res.getBoolean(R.bool.config_recents_thumbnail_image_fits_to_xy);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mRecentsContainer = (RecentsScrollView) findViewById(R.id.recents_container);
        mRecentsContainer.setOnScrollListener(new Runnable() {
            public void run() {
                // need to redraw the faded edges
                invalidate();
            }
        });
        mListAdapter = new TaskDescriptionAdapter(mContext);
        mRecentsContainer.setAdapter(mListAdapter);
        mRecentsContainer.setCallback(this);

        mRecentsScrim = findViewById(R.id.recents_bg_protect);
        mRecentsNoApps = findViewById(R.id.recents_no_apps);

        mClearRecents = (ImageView) findViewById(R.id.recents_clear);
        if (mClearRecents != null){
            mClearRecents.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mRecentsContainer.removeAllViewsInLayout();
                    mClearRecents.setVisibility(View.INVISIBLE);
                }
            });
        }
        mProtectedApps = (ImageView) findViewById(R.id.protected_apps);
        if (mProtectedApps != null){
            mProtectedApps.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    dismiss();
                    // Launch protected components
                    Intent intent = new Intent();
                    intent.setClassName(ANDROID_SETTINGS, ANDROID_PROTECTED_APPS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    mContext.startActivityAsUser(intent, null,
                        new UserHandle(UserHandle.USER_CURRENT));
                }
            });
        }

        if (mRecentsScrim != null) {
            mHighEndGfx = ActivityManager.isHighEndGfx();
            if (!mHighEndGfx) {
                mRecentsScrim.setBackground(null);
            } else if (mRecentsScrim.getBackground() instanceof BitmapDrawable) {
                // In order to save space, we make the background texture repeat in the Y direction
                ((BitmapDrawable) mRecentsScrim.getBackground()).setTileModeY(TileMode.REPEAT);
            }
        }
    }

    public void setMinSwipeAlpha(float minAlpha) {
        mRecentsContainer.setMinSwipeAlpha(minAlpha);
    }

    private void createCustomAnimations(LayoutTransition transitioner) {
        transitioner.setDuration(200);
        transitioner.setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 0);
        transitioner.setAnimator(LayoutTransition.DISAPPEARING, null);
    }

    private void updateIcon(ViewHolder h, Drawable icon, boolean show, boolean anim) {
        if (icon != null) {
            h.iconView.setImageDrawable(icon);
            if (show && h.iconView.getVisibility() != View.VISIBLE) {
                if (anim) {
                    h.iconView.setAnimation(
                            AnimationUtils.loadAnimation(mContext, R.anim.recent_appear));
                }
                h.iconView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateThumbnail(ViewHolder h, Drawable thumbnail, boolean show, boolean anim) {
        if (thumbnail != null) {
            // Should remove the default image in the frame
            // that this now covers, to improve scrolling speed.
            // That can't be done until the anim is complete though.
            h.thumbnailViewImage.setImageDrawable(thumbnail);

            // scale the image to fill the full width of the ImageView. do this only if
            // we haven't set a bitmap before, or if the bitmap size has changed
            if (h.thumbnailViewDrawable == null ||
                h.thumbnailViewDrawable.getIntrinsicWidth() != thumbnail.getIntrinsicWidth() ||
                h.thumbnailViewDrawable.getIntrinsicHeight() != thumbnail.getIntrinsicHeight()) {
                if (mFitThumbnailToXY) {
                    h.thumbnailViewImage.setScaleType(ScaleType.FIT_XY);
                } else {
                    Matrix scaleMatrix = new Matrix();
                    float scale = mThumbnailWidth / (float) thumbnail.getIntrinsicWidth();
                    scaleMatrix.setScale(scale, scale);
                    h.thumbnailViewImage.setScaleType(ScaleType.MATRIX);
                    h.thumbnailViewImage.setImageMatrix(scaleMatrix);
                }
            }
            if (show && h.thumbnailView.getVisibility() != View.VISIBLE) {
                if (anim) {
                    h.thumbnailView.setAnimation(
                            AnimationUtils.loadAnimation(mContext, R.anim.recent_appear));
                }
                h.thumbnailView.setVisibility(View.VISIBLE);
            }
            h.thumbnailViewDrawable = thumbnail;
        }
    }

    void onTaskThumbnailLoaded(TaskDescription td) {
        synchronized (td) {
            if (mRecentsContainer != null) {
                ViewGroup container = (ViewGroup) mRecentsContainer;
                if (container instanceof RecentsScrollView) {
                    container = (ViewGroup) container.findViewById(
                            R.id.recents_linear_layout);
                }
                // Look for a view showing this thumbnail, to update.
                for (int i=0; i < container.getChildCount(); i++) {
                    View v = container.getChildAt(i);
                    if (v.getTag() instanceof ViewHolder) {
                        ViewHolder h = (ViewHolder)v.getTag();
                        if (!h.loadedThumbnailAndIcon && h.taskDescription == td) {
                            // only fade in the thumbnail if recents is already visible-- we
                            // show it immediately otherwise
                            //boolean animateShow = mShowing &&
                            //    mRecentsContainer.getAlpha() > ViewConfiguration.ALPHA_THRESHOLD;
                            boolean animateShow = false;
                            updateIcon(h, td.getIcon(), true, animateShow);
                            updateThumbnail(h, td.getThumbnail(), true, animateShow);
                            h.loadedThumbnailAndIcon = true;
                        }
                    }
                }
            }
        }
        showIfReady();
    }

    private void animateInIconOfFirstTask() {
        if (mItemToAnimateInWhenWindowAnimationIsFinished != null &&
                !mRecentTasksLoader.isFirstScreenful()) {
            int timeSinceWindowAnimation =
                    (int) (System.currentTimeMillis() - mWindowAnimationStartTime);
            final int minStartDelay = 150;
            final int startDelay = Math.max(0, Math.min(
                    minStartDelay - timeSinceWindowAnimation, minStartDelay));
            final int duration = 250;
            final ViewHolder holder = mItemToAnimateInWhenWindowAnimationIsFinished;
            final TimeInterpolator cubic = new DecelerateInterpolator(1.5f);
            FirstFrameAnimatorHelper.initializeDrawListener(holder.iconView);
            for (View v :
                new View[] { holder.iconView, holder.labelView, holder.calloutLine }) {
                if (v != null) {
                    ViewPropertyAnimator vpa = v.animate().translationX(0).translationY(0)
                            .alpha(1f).setStartDelay(startDelay)
                            .setDuration(duration).setInterpolator(cubic);
                    FirstFrameAnimatorHelper h = new FirstFrameAnimatorHelper(vpa, v);
                }
            }
            mItemToAnimateInWhenWindowAnimationIsFinished = null;
            mAnimateIconOfFirstTask = false;
        }
    }

    public void onWindowAnimationStart() {
        mWaitingForWindowAnimation = false;
        mWindowAnimationStartTime = System.currentTimeMillis();
        animateInIconOfFirstTask();
    }

    public void clearRecentTasksList() {
        // Clear memory used by screenshots
        if (mRecentTaskDescriptions != null) {
            mRecentTasksLoader.cancelLoadingThumbnailsAndIcons(this);
            onTaskLoadingCancelled();
        }
    }

    public void onTaskLoadingCancelled() {
        // Gets called by RecentTasksLoader when it's cancelled
        if (mRecentTaskDescriptions != null) {
            mRecentTaskDescriptions = null;
            mListAdapter.notifyDataSetInvalidated();
        }
    }

    public void refreshViews() {
        mListAdapter.notifyDataSetInvalidated();
        updateUiElements();
        showIfReady();
    }

    public void refreshRecentTasksList() {
        refreshRecentTasksList(null, false);
    }

    private void refreshRecentTasksList(
            ArrayList<TaskDescription> recentTasksList, boolean firstScreenful) {
        if (mRecentTaskDescriptions == null && recentTasksList != null) {
            onTasksLoaded(recentTasksList, firstScreenful);
        } else {
            mRecentTasksLoader.loadTasksInBackground();
        }
    }

    public void onTasksLoaded(ArrayList<TaskDescription> tasks, boolean firstScreenful) {
        if (mRecentTaskDescriptions == null) {
            mRecentTaskDescriptions = new ArrayList<TaskDescription>(tasks);
        } else {
            mRecentTaskDescriptions.addAll(tasks);
        }
        if (((RecentsActivity) mContext).isActivityShowing()) {
            refreshViews();
        }
    }

    private void updateUiElements() {
        final int items = mRecentTaskDescriptions != null
                ? mRecentTaskDescriptions.size() : 0;

        ((View) mRecentsContainer).setVisibility(items > 0 ? View.VISIBLE : View.GONE);

        // Set description for accessibility
        int numRecentApps = mRecentTaskDescriptions != null
                ? mRecentTaskDescriptions.size() : 0;
        String recentAppsAccessibilityDescription;
        if (numRecentApps == 0) {
            recentAppsAccessibilityDescription =
                getResources().getString(R.string.status_bar_no_recent_apps);
        } else {
            recentAppsAccessibilityDescription = getResources().getQuantityString(
                R.plurals.status_bar_accessibility_recent_apps, numRecentApps, numRecentApps);
        }
        setContentDescription(recentAppsAccessibilityDescription);
    }

    public boolean simulateClick(int persistentTaskId) {
        View v = mRecentsContainer.findViewForTask(persistentTaskId);
        if (v != null) {
            handleOnClick(v);
            return true;
        }
        return false;
    }

    public void handleOnClick(View view) {
        ViewHolder holder = (ViewHolder) view.getTag();
        TaskDescription ad = holder.taskDescription;
        final Context context = view.getContext();
        final ActivityManager am = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);

        Bitmap bm = null;
        boolean usingDrawingCache = true;
        if (holder.thumbnailViewDrawable instanceof BitmapDrawable) {
            bm = ((BitmapDrawable) holder.thumbnailViewDrawable).getBitmap();
            if (bm.getWidth() == holder.thumbnailViewImage.getWidth() &&
                    bm.getHeight() == holder.thumbnailViewImage.getHeight()) {
                usingDrawingCache = false;
            }
        }
        if (usingDrawingCache) {
            holder.thumbnailViewImage.setDrawingCacheEnabled(true);
            bm = holder.thumbnailViewImage.getDrawingCache();
        }
        Bundle opts = (bm == null) ?
                null :
                ActivityOptions.makeThumbnailScaleUpAnimation(
                        holder.thumbnailViewImage, bm, 0, 0, null).toBundle();

        show(false);
        if (ad.taskId >= 0) {
            // This is an active task; it should just go to the foreground.
            am.moveTaskToFront(ad.taskId, ActivityManager.MOVE_TASK_WITH_HOME,
                    opts);
        } else {
            Intent intent = ad.intent;
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                    | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            if (DEBUG) Log.v(TAG, "Starting activity " + intent);
            try {
                context.startActivityAsUser(intent, opts,
                        new UserHandle(UserHandle.USER_CURRENT));
            } catch (SecurityException e) {
                Log.e(TAG, "Recents does not have the permission to launch " + intent, e);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Error launching activity " + intent, e);
            }
        }
        if (usingDrawingCache) {
            holder.thumbnailViewImage.setDrawingCacheEnabled(false);
        }
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        handleOnClick(view);
    }

    public void handleSwipe(View view) {
        TaskDescription ad = ((ViewHolder) view.getTag()).taskDescription;
        if (ad == null) {
            Log.v(TAG, "Not able to find activity description for swiped task; view=" + view +
                    " tag=" + view.getTag());
            return;
        }
        if (DEBUG) Log.v(TAG, "Jettison " + ad.getLabel());
        mRecentTaskDescriptions.remove(ad);
        mRecentTasksLoader.remove(ad);

        // Handled by widget containers to enable LayoutTransitions properly
        // mListAdapter.notifyDataSetChanged();

        if (mRecentTaskDescriptions.size() == 0) {
            dismissAndGoBack();
        }

        // Currently, either direction means the same thing, so ignore direction and remove
        // the task.
        final ActivityManager am = (ActivityManager)
                mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            am.removeTask(ad.persistentTaskId, ActivityManager.REMOVE_TASK_KILL_PROCESS);

            // Accessibility feedback
            setContentDescription(
                    mContext.getString(R.string.accessibility_recents_item_dismissed, ad.getLabel()));
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
            setContentDescription(null);
        }
    }

    private void startApplicationDetailsActivity(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        TaskStackBuilder.create(getContext())
                .addNextIntentWithParentStack(intent).startActivities();
    }

    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mPopup != null) {
            return true;
        } else {
            return super.onInterceptTouchEvent(ev);
        }
    }

    public void handleLongPress(
            final View selectedView, final View anchorView, final View thumbnailView) {
        thumbnailView.setSelected(true);
        final PopupMenu popup =
            new PopupMenu(mContext, anchorView == null ? selectedView : anchorView);
        mPopup = popup;
        popup.getMenuInflater().inflate(R.menu.recent_popup_menu, popup.getMenu());

        final ContentResolver cr = mContext.getContentResolver();
        if (Settings.Secure.getInt(cr,
            Settings.Secure.DEVELOPMENT_SHORTCUT, 0) == 0) {
            popup.getMenu().findItem(R.id.recent_force_stop).setVisible(false);
            popup.getMenu().findItem(R.id.recent_wipe_app).setVisible(false);
            popup.getMenu().findItem(R.id.recent_uninstall).setVisible(false);
        } else {
            ViewHolder viewHolder = (ViewHolder) selectedView.getTag();
            if (viewHolder != null) {
                final TaskDescription ad = viewHolder.taskDescription;
                try {
                    PackageManager pm = (PackageManager) mContext.getPackageManager();
                    ApplicationInfo mAppInfo = pm.getApplicationInfo(ad.packageName, 0);
                    DevicePolicyManager mDpm = (DevicePolicyManager) mContext.
                            getSystemService(Context.DEVICE_POLICY_SERVICE);
                    if ((mAppInfo.flags&(ApplicationInfo.FLAG_SYSTEM
                          | ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA))
                          == ApplicationInfo.FLAG_SYSTEM
                          || mDpm.packageHasActiveAdmins(ad.packageName)) {
                        popup.getMenu()
                        .findItem(R.id.recent_wipe_app).setEnabled(false);
                        popup.getMenu()
                        .findItem(R.id.recent_uninstall).setEnabled(false);
                    } else {
                        Log.d(TAG, "Not a 'special' application");
                    }
                } catch (NameNotFoundException ex) {
                    Log.e(TAG, "Failed looking up ApplicationInfo for " + ad.packageName, ex);
                }
            }
        }
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.recent_remove_item) {
                    ((ViewGroup) mRecentsContainer).removeViewInLayout(selectedView);
                } else if (item.getItemId() == R.id.recent_inspect_item) {
                    ViewHolder viewHolder = (ViewHolder) selectedView.getTag();
                    if (viewHolder != null) {
                        final TaskDescription ad = viewHolder.taskDescription;
                        startApplicationDetailsActivity(ad.packageName);
                        show(false);
                    } else {
                        throw new IllegalStateException("Oops, no tag on view " + selectedView);
                    }
                } else if (item.getItemId() == R.id.recent_force_stop) {
                    ViewHolder viewHolder = (ViewHolder) selectedView.getTag();
                    if (viewHolder != null) {
                        final TaskDescription ad = viewHolder.taskDescription;
                        ActivityManager am = (ActivityManager)mContext.getSystemService(
                                Context.ACTIVITY_SERVICE);
                        am.forceStopPackage(ad.packageName);
                        ((ViewGroup) mRecentsContainer).removeViewInLayout(selectedView);
                    } else {
                        throw new IllegalStateException("Oops, no tag on view " + selectedView);
                    }
                } else if (item.getItemId() == R.id.recent_wipe_app) {
                    ViewHolder viewHolder = (ViewHolder) selectedView.getTag();
                    if (viewHolder != null) {
                        final TaskDescription ad = viewHolder.taskDescription;
                        ActivityManager am = (ActivityManager) mContext.
                                getSystemService(Context.ACTIVITY_SERVICE);
                        am.clearApplicationUserData(ad.packageName,
                                new FakeClearUserDataObserver());
                        ((ViewGroup) mRecentsContainer).removeViewInLayout(selectedView);
                    } else {
                        throw new IllegalStateException("Oops, no tag on view " + selectedView);
                    }
                } else if (item.getItemId() == R.id.recent_uninstall) {
                    ViewHolder viewHolder = (ViewHolder) selectedView.getTag();
                    if (viewHolder != null) {
                        final TaskDescription ad = viewHolder.taskDescription;
                        Uri packageURI = Uri.parse("package:"+ad.packageName);
                        Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageURI);
                        uninstallIntent.putExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, true);
                        mContext.startActivity(uninstallIntent);
                        ((ViewGroup) mRecentsContainer).removeViewInLayout(selectedView);
                    } else {
                        throw new IllegalStateException("Oops, no tag on view " + selectedView);
                    }
                } else {
                    return false;
                }
                return true;
            }
        });
        popup.setOnDismissListener(new PopupMenu.OnDismissListener() {
            public void onDismiss(PopupMenu menu) {
                thumbnailView.setSelected(false);
                mPopup = null;
            }
        });
        popup.show();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        int paddingLeft = mPaddingLeft;
        final boolean offsetRequired = isPaddingOffsetRequired();
        if (offsetRequired) {
            paddingLeft += getLeftPaddingOffset();
        }

        int left = mScrollX + paddingLeft;
        int right = left + mRight - mLeft - mPaddingRight - paddingLeft;
        int top = mScrollY + getFadeTop(offsetRequired);
        int bottom = top + getFadeHeight(offsetRequired);

        if (offsetRequired) {
            right += getRightPaddingOffset();
            bottom += getBottomPaddingOffset();
        }
        mRecentsContainer.drawFadedEdges(canvas, left, right, top, bottom);
    }

    @Override
    protected boolean fitSystemWindows(Rect insets) {
        if (mClearRecents != null) {
            MarginLayoutParams lp = (MarginLayoutParams) mClearRecents.getLayoutParams();
            lp.topMargin = insets.top;
            lp.rightMargin = insets.right;
            mClearRecents.setLayoutParams(lp);
        }
        if (mProtectedApps != null) {
            MarginLayoutParams lp = (MarginLayoutParams) mProtectedApps.getLayoutParams();
            lp.topMargin = insets.top;
            lp.rightMargin = insets.right;
            mProtectedApps.setLayoutParams(lp);
        }

        return super.fitSystemWindows(insets);
    }

    class FakeClearUserDataObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
        }
    }
}
