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
import android.app.INotificationManager;
import android.app.TaskStackBuilder;
import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.Paint;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.PorterDuffXfermode;
import android.graphics.Bitmap.Config;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.IWindowManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
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
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.StatusBarPanel;
import com.android.systemui.statusbar.phone.PhoneStatusBar;

import com.android.internal.util.MemInfoReader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.Runtime;

public class RecentsPanelView extends FrameLayout implements OnItemClickListener, RecentsCallback,
        StatusBarPanel, Animator.AnimatorListener {
    static final String TAG = "RecentsPanelView";
    static final boolean DEBUG = PhoneStatusBar.DEBUG || false;
    private PopupMenu mPopup;
    private View mRecentsScrim;
    private View mRecentsNoApps;
    //private View mRecentsRamBar;
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
    private static Set<Integer> sLockedTasks = new HashSet<Integer>();
    private TaskDescriptionAdapter mListAdapter;
    private int mThumbnailWidth;
    private boolean mFitThumbnailToXY;
    private int mRecentItemLayoutId;
    private boolean mHighEndGfx;
    private ImageView mClearRecents;
    private int mCustomRecent;
    private RecentsActivity mRecentsActivity;
    private INotificationManager mNotificationManager;

    private LinearColorBar mRamUsageBar;

    private long mFreeMemory;
    private long mTotalMemory;
    private long mCachedMemory;
    private long mActiveMemory;

    TextView mUsedMemText;
    TextView mFreeMemText;
    TextView mRamText;

    MemInfoReader mMemInfoReader = new MemInfoReader();
   
    private int mDragPositionX;
    private int mDragPositionY;

    public static interface RecentsScrollView {
        public int numItemsInOneScreenful();
        public void setAdapter(TaskDescriptionAdapter adapter);
        public void setCallback(RecentsCallback callback);
        public void setMinSwipeAlpha(float minAlpha);
        public View findViewForTask(int persistentTaskId);
        public void drawFadedEdges(Canvas c, int left, int right, int top, int bottom);
        public void setOnScrollListener(Runnable listener);
        public boolean isConfirmationDialogAnswered();
        public void setDismissAfterConfirmation(boolean dismiss);

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

    /* package */ public final static class ViewHolder {
        View thumbnailView;
        ImageView thumbnailViewImage;
        Drawable thumbnailViewDrawable;
        Drawable thumbnailDrawable;
        ImageView iconView;
        ImageView lockedIcon;
        TextView labelView;
        TextView descriptionView;
        View calloutLine;
        public TaskDescription taskDescription;
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
            holder.lockedIcon = (ImageView) convertView.findViewById(R.id.locked);
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
            if (sLockedTasks.contains(td.persistentTaskId)) { //lock
                td.setLocked(true);
                sLockedTasks.remove(td.persistentTaskId);
            }
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
                        oldHolder.lockedIcon.setAlpha(1f);
                        oldHolder.lockedIcon.setTranslationX(0f);
                        oldHolder.lockedIcon.setTranslationY(0f);
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
                        holder.lockedIcon.setAlpha(0f);
                        holder.lockedIcon.setTranslationX(translation);
                    } else {
                        holder.iconView.setAlpha(0f);
                        holder.iconView.setTranslationY(translation);
                    }
                    if (!mWaitingForWindowAnimation) {
                        animateInIconOfFirstTask();
                    }
                }
            }
            holder.lockedIcon.setImageResource(td.isLocked()?R.drawable.ic_recent_app_locked:R.drawable.ic_recent_app_unlock);
            holder.thumbnailView.setTag(td);
            holder.thumbnailView.setOnLongClickListener(new OnLongClickDelegate(convertView));
            holder.thumbnailView.setOnTouchListener(new OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent m) {
                    return handleThumbnailTouch(m, holder.thumbnailView);
                }
            });
            holder.taskDescription = td;
            holder.lockedIcon.setTag(td);
            holder.lockedIcon.setOnClickListener(onClickListener);
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
            holder.lockedIcon.setAlpha(1f);
            holder.lockedIcon.setTranslationX(0f);
            holder.lockedIcon.setTranslationY(0f);
            if (holder.calloutLine != null) {
                holder.calloutLine.setAlpha(1f);
                holder.calloutLine.setTranslationX(0f);
                holder.calloutLine.setTranslationY(0f);
                holder.calloutLine.animate().cancel();
            }
            holder.taskDescription = null;
            holder.loadedThumbnailAndIcon = false;
        }
        private OnClickListener onClickListener=new OnClickListener() {//lock
            @Override
            public void onClick(View v) {
                TaskDescription taskDescription= (TaskDescription) v.getTag();
                if (taskDescription != null) {
                  if (taskDescription.isLocked()) {
                      taskDescription.setLocked(false);
                      ((ImageView)v).setImageResource(R.drawable.ic_recent_app_unlock);
                  } else {
                      taskDescription.setLocked(true);
                      ((ImageView)v).setImageResource(R.drawable.ic_recent_app_locked);
                  }
                }
            }
        };
    }

    public RecentsPanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecentsPanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources res = context.getResources();
        int mCustomRecent = Settings.System.getIntForUser(mContext.getContentResolver(), 
                        Settings.System.RECENTS_STYLE, 0, UserHandle.USER_CURRENT);

        if (mCustomRecent == 4 || mCustomRecent == 5) {
            mFitThumbnailToXY = res.getBoolean(R.bool.config_recents_thumbnail_image_fits_to_xy_sense);
            mThumbnailWidth = Math.round(res.getDimension(R.dimen.status_bar_recents_thumbnail_width_sense));
        } else {
            mThumbnailWidth = Math.round(res.getDimension(R.dimen.status_bar_recents_thumbnail_width));
            mFitThumbnailToXY = res.getBoolean(R.bool.config_recents_thumbnail_image_fits_to_xy);
        }

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RecentsPanelView,
                defStyle, 0);

        mRecentItemLayoutId = a.getResourceId(R.styleable.RecentsPanelView_recentItemLayout, 0);
        mRecentTasksLoader = RecentTasksLoader.getInstance(context);
        mRecentsActivity = (RecentsActivity) context;
        a.recycle();
        mNotificationManager = INotificationManager.Stub.asInterface(
            ServiceManager.getService(Context.NOTIFICATION_SERVICE));
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

    public void dismissContextMenuIfAny() {
        if(mPopup != null) {
            mPopup.dismiss();
        }
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

    private void showImpl(boolean show) {
        if (ActivityManagerNative.isSystemReady()) {
            try {
                ActivityManagerNative.getDefault().closeSystemDialogs(BaseStatusBar.SYSTEM_DIALOG_REASON_RECENT_APPS);
            } catch (RemoteException e) {
            }
        }

        mShowing = show;

        if (show) {
            // if there are no apps, bring up a "No recent apps" message
            boolean noApps = mRecentTaskDescriptions != null
                    && (mRecentTaskDescriptions.size() == 0);
            mRecentsNoApps.setAlpha(1f);
            mRecentsNoApps.setVisibility(noApps ? View.VISIBLE : View.INVISIBLE);
            mClearRecents.setVisibility(noApps ? View.GONE : View.VISIBLE);

            boolean showClearAllButton = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.SHOW_CLEAR_RECENTS_BUTTON, 1, UserHandle.USER_CURRENT) == 1;

            if (showClearAllButton) {
                mClearRecents.setVisibility(noApps ? View.GONE : View.VISIBLE);
                int clearAllButtonLocation = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.CLEAR_RECENTS_BUTTON_LOCATION, Constants.CLEAR_ALL_BUTTON_BOTTOM_LEFT, UserHandle.USER_CURRENT);
                int ClearButtonColor = Settings.System.getIntForUser(mContext.getContentResolver(),
                        Settings.System.CLEAR_RECENTS_BUTTON_COLOR, 0xffffffff, UserHandle.USER_CURRENT);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) mClearRecents.getLayoutParams();
                switch (clearAllButtonLocation) {
                    case Constants.CLEAR_ALL_BUTTON_TOP_LEFT:
                        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                        break;
                    case Constants.CLEAR_ALL_BUTTON_TOP_RIGHT:
                        layoutParams.gravity = Gravity.TOP | Gravity.RIGHT;
                        break;
                    case Constants.CLEAR_ALL_BUTTON_BOTTOM_RIGHT:
                        layoutParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                        break;
                    case Constants.CLEAR_ALL_BUTTON_BOTTOM_LEFT:
                    default:
                        layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
                        break;
                }
                mClearRecents.setLayoutParams(layoutParams);
                mClearRecents.setColorFilter(null);
                mClearRecents.setColorFilter(ClearButtonColor, Mode.MULTIPLY);
            } else {
                mClearRecents.setVisibility(View.GONE);
            }

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

    private boolean handleThumbnailTouch(MotionEvent m, View thumb) {
        // If we have two touches, let user snap on top or bottom
        int pointerCount = m.getPointerCount();
        if (pointerCount == 2) {
            int action = m.getActionMasked();
            int currX = (int) m.getX(1);
            int currY = (int) m.getY(1);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    mDragPositionX = currX;
                    mDragPositionY = currY;
                    break;

                case MotionEvent.ACTION_UP:
                    handleThumbnailDragRelease(thumb);
                    break;

                case MotionEvent.ACTION_MOVE:
                    int diffX = currX - mDragPositionX;
                    int diffY = currY - mDragPositionY;
                    thumb.setTranslationX(thumb.getTranslationX() + diffX);
                    thumb.setTranslationY(thumb.getTranslationY() + diffY);
                    mDragPositionX = currX;
                    mDragPositionY = currY;
                    break;
            }

            return true;
        } else {
            mDragPositionX = 0;
            mDragPositionY = 0;
            return false;
        }
    }

    private void handleThumbnailDragRelease(View view) {
        ViewHolder holder = (ViewHolder) view.getTag();
        WindowManager wm = (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;
        if (mDragPositionY < height/2) {
            openInSplitView(holder, 0);
        } else {
            openInSplitView(holder, 1);
        }
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
        mRecentsActivity.dismissAndGoHome();
    }

    public void dismissAndGoBack() {
        mRecentsActivity.dismissAndGoBack();
    }

    public void dismissAndDoNothing() {
        mRecentsActivity.dismissAndDoNothing();
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
	    //mRecentsRamBar = findViewById(R.id.recents_ram_bar);

        mClearRecents = (ImageView) findViewById(R.id.recents_clear);
        if (mClearRecents != null){
            mClearRecents.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    //((ViewGroup) mRecentsContainer).removeAllViewsInLayout();
                    clearAllNonLocked();
                    mClearRecents.setVisibility(View.INVISIBLE);
                }
            });
            mClearRecents.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    clearAllNonLocked();
                    try {
                        ProcessBuilder pb = new ProcessBuilder("su", "-c", "/system/bin/sh");
                        OutputStreamWriter osw = new OutputStreamWriter(pb.start().getOutputStream());
                        osw.write("sync" + "\n" + "echo 3 > /proc/sys/vm/drop_caches" + "\n");
                        osw.write("\nexit\n");
                        osw.flush();
                        osw.close();
                    } catch (Exception e) {
                        Log.d(TAG, "Flush caches failed!");
                    }
                    return true;
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
    	updateRamBar();
    }

    /**
     * Iterates over all the children in the recents scroll view linear layout and does not
     * remove a view if isLocked is true.
     */
    private void clearAllNonLocked() {
        int count = 0;
        if (mRecentsContainer instanceof RecentsVerticalScrollView) {
            count = ((RecentsVerticalScrollView) mRecentsContainer).getLinearLayoutChildCount();
            for (int i = 0; i < count; i++) {
                final View child = ((RecentsVerticalScrollView) mRecentsContainer)
                        .getLinearLayoutChildAt(i);
                ViewHolder holder = (ViewHolder) child.getTag();
                if (holder == null || !holder.taskDescription.isLocked()) {
                    ((RecentsVerticalScrollView) mRecentsContainer).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ((RecentsVerticalScrollView) mRecentsContainer).removeViewInLayout(child);
                        }
                    }, i * 150);
                }
            }
        } else if (mRecentsContainer instanceof RecentsHorizontalScrollView) {
            count = ((RecentsHorizontalScrollView) mRecentsContainer).getLinearLayoutChildCount();
            for (int i = 0; i < count; i++) {
                final View child = ((RecentsHorizontalScrollView) mRecentsContainer)
                        .getLinearLayoutChildAt(i);
                ViewHolder holder = (ViewHolder) child.getTag();
                if (holder == null || !holder.taskDescription.isLocked()) {
                    ((RecentsHorizontalScrollView) mRecentsContainer).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            ((RecentsHorizontalScrollView) mRecentsContainer).removeViewInLayout(child);
                        }
                    }, i * 150);
                    }
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

    /**
     * Convert drawable to bitmap.
     *
     * @param drawable Drawable object to be converted.
     * @return converted bitmap.
     */
    private Bitmap drawableToBitmap(Drawable drawable) {

            Bitmap thumbnail;

            if(drawable instanceof BitmapDrawable) {
                thumbnail = ((BitmapDrawable) drawable).getBitmap();
            } else {
                Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),drawable.getIntrinsicHeight(), Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                thumbnail = bitmap;
            }
             
            final int reflectionGap = 4;
            int width = thumbnail.getWidth();
            int height = thumbnail.getHeight();

            Matrix matrix = new Matrix();
            matrix.preScale(1, -1);

            Bitmap reflectionImage = Bitmap.createBitmap(thumbnail, 0, height * 2 / 3, width, height/3, matrix, false);
            Bitmap bitmapWithReflection = Bitmap.createBitmap(width, (height + height/3), Config.ARGB_8888);

            Canvas canvas = new Canvas(bitmapWithReflection);
            canvas.drawBitmap(thumbnail, 0, 0, null);
            Paint defaultPaint = new Paint();
            canvas.drawRect(0, height, width, height + reflectionGap, defaultPaint);
            canvas.drawBitmap(reflectionImage, 0, height + reflectionGap, null);

            Paint paint = new Paint();
            LinearGradient shader = new LinearGradient(0, thumbnail.getHeight(), 0,
                bitmapWithReflection.getHeight() + reflectionGap, 0x70ffffff, 0x00ffffff,
                TileMode.CLAMP);
            paint.setShader(shader);
            paint.setXfermode(new PorterDuffXfermode(Mode.DST_IN));
            canvas.drawRect(0, height, width,
                bitmapWithReflection.getHeight() + reflectionGap, paint);

            return bitmapWithReflection;
    }

    private void updateThumbnail(ViewHolder h, Drawable thumbnail, boolean show, boolean anim) {
        if (thumbnail != null) {
            // Should remove the default image in the frame
            // that this now covers, to improve scrolling speed.
            // That can't be done until the anim is complete though.
            int mCustomRecent = Settings.System.getIntForUser(mContext.getContentResolver(), 
                        Settings.System.RECENTS_STYLE, 0, UserHandle.USER_CURRENT);

            if(mCustomRecent == 3 || mCustomRecent == 4 || mCustomRecent == 5) {
                h.thumbnailDrawable = new BitmapDrawable(drawableToBitmap(thumbnail));
                if (h.thumbnailDrawable != null)
                    thumbnail = h.thumbnailDrawable; 
            }

            h.thumbnailViewImage.setImageDrawable(thumbnail);

            // scale the image to fill the full width of the ImageView. do this only if
            // we haven't set a bitmap before, or if the bitmap size has changed
            if (h.thumbnailViewDrawable == null ||
                h.thumbnailViewDrawable.getIntrinsicWidth() != thumbnail.getIntrinsicWidth() ||
                h.thumbnailViewDrawable.getIntrinsicHeight() != thumbnail.getIntrinsicHeight()) {
                if (mFitThumbnailToXY) {
                    h.thumbnailViewImage.setScaleType(ScaleType.FIT_XY);
                    if(mCustomRecent == 3 || mCustomRecent == 4 || mCustomRecent == 5) {
                        h.thumbnailViewImage.setRotationY(25.0f);
                    }
                } else {
                    if(mCustomRecent == 3 || mCustomRecent == 4 || mCustomRecent == 5) {
                        h.thumbnailViewImage.setScaleType(ScaleType.FIT_CENTER);
                        h.thumbnailViewImage.setRotationY(25.0f);
                        if (DEBUG) Log.d(TAG, "thumbnail.getHeight(): " + thumbnail.getIntrinsicHeight());
                        if (DEBUG) Log.d(TAG, "thumbnail.getWidth(): " + thumbnail.getIntrinsicWidth());
                    } else {
                        Matrix scaleMatrix = new Matrix();
                        float scale = mThumbnailWidth / (float) thumbnail.getIntrinsicWidth();
                        scaleMatrix.setScale(scale, scale);
                        h.thumbnailViewImage.setScaleType(ScaleType.MATRIX);
                        h.thumbnailViewImage.setImageMatrix(scaleMatrix);
                    }
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
                new View[] { holder.iconView, holder.labelView, holder.calloutLine,holder.lockedIcon }) {
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

    public void setColor() {
        int color = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.RECENTS_PANEL_STOCK_COLOR, 0xe0000000, UserHandle.USER_CURRENT);

        if (mRecentsScrim != null) {
            mHighEndGfx = ActivityManager.isHighEndGfx();
            if (color == 0xe0000000) {
                if (!mHighEndGfx) {
                    mRecentsScrim.setBackground(null);
                } else if (mRecentsScrim.getBackground() instanceof BitmapDrawable) {
                    // In order to save space, we make the background texture repeat in the Y direction
                    ((BitmapDrawable) mRecentsScrim.getBackground()).setTileModeY(TileMode.REPEAT);
                }
            } else {
                mRecentsScrim.setBackgroundColor(color);
            }
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
	updateRamBar();
    }

    public void onTaskLoadingCancelled() {
        // Gets called by RecentTasksLoader when it's cancelled
        if (mRecentTaskDescriptions != null) {
            mRecentTaskDescriptions = null;
            mListAdapter.notifyDataSetInvalidated();
        }
	updateRamBar();
    }

    public void refreshViews() {
        mListAdapter.notifyDataSetInvalidated();
        updateUiElements();
        showIfReady();
	updateRamBar();
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
        Intent intent = ad.intent;
        boolean floating = (intent.getFlags() & Intent.FLAG_FLOATING_WINDOW) == Intent.FLAG_FLOATING_WINDOW;
        if (ad.taskId >= 0 && !floating) {
            // This is an active task; it should just go to the foreground.
            // If that task was split viewed, a normal press wil resume it to
            // normal fullscreen view
            IWindowManager wm = (IWindowManager) WindowManagerGlobal.getWindowManagerService();
            try {
                if (DEBUG) Log.v(TAG, "Restoring window full screen after split, because of normal tap");
                wm.setTaskSplitView(ad.taskId, false);
            } catch (RemoteException e) {
                Log.e(TAG, "Could not setTaskSplitView to fullscreen", e);
            }

            am.moveTaskToFront(ad.taskId, ActivityManager.MOVE_TASK_WITH_HOME,
                    opts);
        } else {
            if (floating) {
                if (DEBUG) Log.v(TAG, "Starting floating activity " + intent);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_FLOATING_WINDOW);
                try {
                    context.startActivityAsUser(intent, opts,
                        new UserHandle(UserHandle.USER_CURRENT));
                } catch (SecurityException e) {
                    Log.e(TAG, "Recents does not have the permission to launch " + intent, e);
                }
            } else {
                boolean backPressed = mRecentsActivity != null && mRecentsActivity.mBackPressed;
                if (!floating  || !backPressed) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
                            | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                if (DEBUG) Log.v(TAG, "Starting activity " + intent);
                try {
                    context.startActivityAsUser(intent, opts,
                            new UserHandle(UserHandle.USER_CURRENT));
                    if (floating && mRecentsActivity != null) {
                        mRecentsActivity.finish();
                    }
                } catch (SecurityException e) {
                    Log.e(TAG, "Recents does not have the permission to launch " + intent, e);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "Error launching activity " + intent, e);
                }
            }
        }
        if (usingDrawingCache) {
            holder.thumbnailViewImage.setDrawingCacheEnabled(false);
        }
        RecentTasksLoader.getInstance(mContext).cancelPreloadingFirstTask();
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

        if (mRecentTaskDescriptions != null) {
            mRecentTaskDescriptions.remove(ad);
            mRecentTasksLoader.remove(ad);

            // Handled by widget containers to enable LayoutTransitions properly
            // mListAdapter.notifyDataSetChanged();

            if (mRecentTaskDescriptions.size() == 0) {
                // Instruct (possibly) running on-the-spot dialog to dismiss recents
                mRecentsContainer.setDismissAfterConfirmation(true);
                if (mRecentsContainer.isConfirmationDialogAnswered()) {
                    // No on-the-spot dialog running, safe to dismiss now
                    dismissAndGoBack();
                }
            }
        } else {
            // Instruct (possibly) running on-the-spot dialog to dismiss recents
            mRecentsContainer.setDismissAfterConfirmation(true);
            if (mRecentsContainer.isConfirmationDialogAnswered()) {
                // No on-the-spot dialog running, safe to dismiss now
                dismissAndGoBack();
            }
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
    	updateRamBar();
    }

    public void handleFloat(View view) {
        launchFloating(view);
    }

    private void launchFloating(View view) {
        ViewHolder viewHolder = (ViewHolder) view.getTag();
        if (viewHolder != null) {
            final TaskDescription ad = viewHolder.taskDescription;
            if (ad == null) {
                Log.v(TAG, "Not able to find activity description for floating task; view=" + view +
                        " tag=" + view.getTag());
                return;
            }
            
            String currentViewPackage = ad.packageName;
            boolean allowed = true; // default on
            try {
                // preloaded apps are added to the blacklist array when is recreated, handled in the notification manager
                allowed = mNotificationManager.isPackageAllowedForFloatingMode(currentViewPackage);
            } catch (android.os.RemoteException ex) {
                // System is dead
            }
            if (!allowed) {
                dismissAndGoBack();
                String text = mContext.getResources().getString(R.string.floating_mode_blacklisted_app);
                int duration = Toast.LENGTH_LONG;
                Toast.makeText(mContext, text, duration).show();
                return;
            } else {
                dismissAndGoBack();
            }
            view.post(new Runnable() {
                @Override
                public void run() {
                    Intent intent = ad.intent;
                    intent.setFlags(Intent.FLAG_FLOATING_WINDOW
                                    | Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                }
            });
        }
    }

    private void startApplicationDetailsActivity(String packageName) {
        dismissAndGoBack();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setComponent(intent.resolveActivity(mContext.getPackageManager()));
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
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

    public void openInSplitView(ViewHolder holder, int location) {
        if (holder != null) {
            final Context context = holder.thumbnailView.getContext();
            final ActivityManager am = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
            final IWindowManager wm = (IWindowManager) WindowManagerGlobal.getWindowManagerService();

            TaskDescription ad = holder.taskDescription;

            show(false);
            dismissAndDoNothing();

            // If we weren't on the homescreen, resize the previous activity (if not already split)
            final List<ActivityManager.RecentTaskInfo> recentTasks =
                am.getRecentTasks(20, ActivityManager.RECENT_IGNORE_UNAVAILABLE);

            if (recentTasks != null && recentTasks.size() > 0) {
                final PackageManager pm = mContext.getPackageManager();
                ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .resolveActivityInfo(pm, 0);
                int taskInt = 0;
                ActivityManager.RecentTaskInfo taskInfo = recentTasks.get(1);
                Log.e("XPLOD", "Resizing previous activity " + taskInfo.baseIntent);
                Intent intent = new Intent(taskInfo.baseIntent);
                if (taskInfo.origActivity != null) {
                    intent.setComponent(taskInfo.origActivity);
                }

                ComponentName component = intent.getComponent();

                if (homeInfo == null
                    || !homeInfo.packageName.equals(component.getPackageName())
                    || !homeInfo.name.equals(component.getClassName())) {
                    Log.e("XPLOD", "not home intent, splitting");
                    // This is not the home activity, so split it
                    try {
                        wm.setTaskSplitView(taskInfo.persistentId, true);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Could not set previous task to split view", e);
                    }

                    // We move this to front first, then our activity, so it updates
                    am.moveTaskToFront(taskInfo.persistentId, 0, null);
                }
            }

            if (ad.taskId >= 0) {
                // The task is already launched. The Activity will pull its split
                // information from WindowManagerService once it resumes, so we
                // set its state here.
                try {
                    wm.setTaskSplitView(ad.taskId, true);
                } catch (RemoteException e) {
                    Log.e(TAG, "Could not setTaskSplitView", e);
                }
                am.moveTaskToFront(ad.taskId, 0, null);
            } else {
                // The app has been killed (we have no taskId for it), so we start
                // a new one with the SPLIT_VIEW flag
                Intent intent = ad.intent;
                intent.addFlags(Intent.FLAG_ACTIVITY_SPLIT_VIEW
                    | Intent.FLAG_ACTIVITY_NEW_TASK);

                if (DEBUG) Log.v(TAG, "Starting split view activity " + intent);

                try {
                    context.startActivityAsUser(intent, null,
                            new UserHandle(UserHandle.USER_CURRENT));
                } catch (SecurityException e) {
                    Log.e(TAG, "Recents does not have the permission to launch " + intent, e);
                }
            }

            try {
                ActivityManagerNative.getDefault().notifySplitViewLayoutChanged();
            } catch (RemoteException e) {
                Log.e(TAG, "Could not notify split view layout", e);
            }
        } else {
            throw new IllegalStateException("Oops, no tag on view to split!");
        }
    }

    public void handleLongPress(
            final View selectedView, final View anchorView, final View thumbnailView) {
        if(mPopup != null) {
            mPopup.dismiss();
        }
        thumbnailView.setSelected(true);
        final PopupMenu popup =
            new PopupMenu(mContext, anchorView == null ? selectedView : anchorView);
        mPopup = popup;
        popup.getMenuInflater().inflate(R.menu.recent_popup_menu, popup.getMenu());

        final ViewHolder viewHolder = (ViewHolder) selectedView.getTag();
        if(viewHolder != null)
        {
            TaskDescription ad = viewHolder.taskDescription;
            if(ad != null && ad.isLocked())
            {
                popup.getMenu().removeItem(R.id.recent_remove_item);
            }
        }
        final ContentResolver cr = mContext.getContentResolver();
        if (Settings.Secure.getInt(cr,
            Settings.Secure.DEVELOPMENT_SHORTCUT, 0) == 0) {
            popup.getMenu().findItem(R.id.recent_force_stop).setVisible(false);
            popup.getMenu().findItem(R.id.recent_wipe_app).setVisible(false);
            popup.getMenu().findItem(R.id.recent_uninstall).setVisible(false);
        } else {
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
                    if (viewHolder != null) {
                        final TaskDescription ad = viewHolder.taskDescription;
                        startApplicationDetailsActivity(ad.packageName);
                        show(false);
                    } else {
                        throw new IllegalStateException("Oops, no tag on view " + selectedView);
                    }
                } else if (item.getItemId() == R.id.recent_force_stop) {
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
                } else if (item.getItemId() == R.id.recent_launch_floating) {
                    launchFloating(selectedView);
                } else if (item.getItemId() == R.id.recent_add_split_view) {
                    // Either start a new activity in split view, or move the current task
                    // to front, but resized
                    openInSplitView(viewHolder, -1);
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

    public void saveLockedTasks() {
        final int count;
        sLockedTasks.clear();
        if (mRecentsContainer instanceof RecentsVerticalScrollView) {
            count = ((RecentsVerticalScrollView) mRecentsContainer).getLinearLayoutChildCount();
            for (int i = 0; i < count; i++) {
                final View child = ((RecentsVerticalScrollView) mRecentsContainer)
                        .getLinearLayoutChildAt(i);
                final ViewHolder holder = (ViewHolder) child.getTag();
                if (holder != null && holder.taskDescription.isLocked()) {
                    sLockedTasks.add(holder.taskDescription.persistentTaskId);
                }
            }
        } else if (mRecentsContainer instanceof RecentsHorizontalScrollView) {
            count = ((RecentsHorizontalScrollView) mRecentsContainer).getLinearLayoutChildCount();
            for (int i = 0; i < count; i++) {
                final View child = ((RecentsHorizontalScrollView) mRecentsContainer)
                        .getLinearLayoutChildAt(i);
                ViewHolder holder = (ViewHolder) child.getTag();
                if (holder != null && holder.taskDescription.isLocked()) {
                    sLockedTasks.add(holder.taskDescription.persistentTaskId);
                }
            }
        }
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

    private void updateRamBar() {
        mRamUsageBar = (LinearColorBar) findViewById(R.id.ram_usage_bar);

        int mRamBarMode = (Settings.System.getIntForUser(mContext.getContentResolver(),
                             Settings.System.RECENTS_RAM_BAR_MODE, 0, UserHandle.USER_CURRENT));

        if (mRamBarMode != 0 && mRamUsageBar != null) {

            long usedMem = 0;
            long freeMem = 0;

                mRamUsageBar.setVisibility(View.VISIBLE);
                updateMemoryInfo();
   
                switch (mRamBarMode) {
                    case 1:
                        usedMem = mActiveMemory;
                        freeMem = mTotalMemory - mActiveMemory;
                        break;
                    case 2:
                        usedMem = mActiveMemory + mCachedMemory;
                        freeMem = mTotalMemory - mActiveMemory - mCachedMemory;
                        break;
                    case 3:
                        usedMem = mTotalMemory - mFreeMemory;
                        freeMem = mFreeMemory;
                        break;
                }

            mUsedMemText = (TextView)findViewById(R.id.usedMemText);
            mFreeMemText = (TextView)findViewById(R.id.freeMemText);
            mRamText = (TextView)findViewById(R.id.ramText);
            mUsedMemText.setText(getResources().getString(
                    R.string.service_used_mem, usedMem + " MB"));
            mFreeMemText.setText(getResources().getString(
                    R.string.service_free_mem, freeMem + " MB"));
            mRamText.setText(getResources().getString(
                    R.string.memory));
            float totalMem = mTotalMemory;
            float totalShownMem = (mTotalMemory - mFreeMemory - mCachedMemory - mActiveMemory)/ totalMem;
            float totalActiveMem = mActiveMemory / totalMem;
            float totalCachedMem = mCachedMemory / totalMem;
            mRamUsageBar.setRatios(totalShownMem, totalCachedMem, totalActiveMem);

            mRamUsageBar.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(
                            "com.android.settings",
                            "com.android.settings.RunningServices"));

                    try {
                        // Dismiss the lock screen when Settings starts.
                        ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                    } catch (RemoteException e) {
                    }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            | Intent.FLAG_ACTIVITY_NO_HISTORY);
                    mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                }
            });

            mRamUsageBar.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(
                            "com.android.settings",
                            "com.android.settings.Settings$ASSRamBarActivity"));

                    try {
                        // Dismiss the lock screen when Settings starts.
                        ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                    } catch (RemoteException e) {
                    }
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                            | Intent.FLAG_ACTIVITY_NO_HISTORY);
                    mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
                    return true;
                }
            });

        } else if (mRamUsageBar != null) {
            mRamUsageBar.setVisibility(View.GONE);
        }
    }

    private void updateMemoryInfo() {
        long result = 0;
        try {
            String firstLine = readLine("/proc/meminfo", 1);
            if (firstLine != null) {
                String parts[] = firstLine.split("\\s+");
                if (parts.length == 3) {
                    result = Long.parseLong(parts[1])/1024;
                }
            }
        } catch (IOException e) {}
        mTotalMemory = result;

        try {
            String firstLine = readLine("/proc/meminfo", 2);
            if (firstLine != null) {
                String parts[] = firstLine.split("\\s+");
                if (parts.length == 3) {
                    result = Long.parseLong(parts[1])/1024;
                }
            }
        } catch (IOException e) {}
        mFreeMemory = result;

        try {
            String firstLine = readLine("/proc/meminfo", 6);
            if (firstLine != null) {
                String parts[] = firstLine.split("\\s+");
                if (parts.length == 3) {
                    result = Long.parseLong(parts[1])/1024;
                }
            }
        } catch (IOException e) {}
        mActiveMemory = result;

        try {
            String firstLine = readLine("/proc/meminfo", 4);
            if (firstLine != null) {
                String parts[] = firstLine.split("\\s+");
                if (parts.length == 3) {
                    result = Long.parseLong(parts[1])/1024;
                }
            }
        } catch (IOException e) {}
        mCachedMemory = result;

    }

    private static String readLine(String filename, int line) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename), 256);
        try {
            for(int i = 1; i < line; i++) {
                reader.readLine();
            }
            return reader.readLine();
        } finally {
            reader.close();
        }
    }

    class FakeClearUserDataObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
        }
    }
}
