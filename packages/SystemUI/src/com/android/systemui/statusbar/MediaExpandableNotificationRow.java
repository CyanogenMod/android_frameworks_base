/*
 * Copyright (C) 2015 The CyanogenMod Project
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
package com.android.systemui.statusbar;

import android.content.Context;
import android.content.res.Resources;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import cyanogenmod.providers.CMSettings;

public class MediaExpandableNotificationRow extends ExpandableNotificationRow {

    private static final String TAG = MediaExpandableNotificationRow.class.getSimpleName();
    public static final boolean DEBUG = false || Build.IS_DEBUGGABLE;

    public static final int MAX_QUEUE_ENTRIES = 3;

    private QueueView mQueue;
    private ViewGroup mQueueGroup;

    private Integer mMaxQueueHeight;
    private int mDisplayedRows;

    private MediaController mController;

    private SettingsObserver mSettingsObserver;
    private boolean mQueueEnabled;

    public MediaExpandableNotificationRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSettingsObserver = new SettingsObserver(new Handler());
        mQueueEnabled = isQueueEnabled(context);
        mMaxQueueHeight = getMaxQueueHeight();
    }

    public boolean inflateGuts() {
        if (!mQueueEnabled) {
            return true;
        }
        return !mQueue.isUserSelectingRow();
    }

    @Override
    public int getMaxNotificationHeight() {
        if (!mQueueEnabled) {
            return super.getMaxNotificationHeight();
        }
        return getMaxQueueHeight() + super.getMaxNotificationHeight();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mQueue = (QueueView) findViewById(R.id.queue_view);
        mQueue.setQueueEnabled(mQueueEnabled);

        mQueueGroup = (ViewGroup) findViewById(R.id.queue_group);
        mQueueGroup.setVisibility(mQueueEnabled ? View.VISIBLE : View.GONE);
    }

    private int getMaxQueueHeight() {
        int rows = mQueue != null ? mQueue.getCurrentQueueRowCount() : 0;

        if (mDisplayedRows != rows) {
            mDisplayedRows = rows;
            mMaxQueueHeight = null;
        }
        if (mMaxQueueHeight == null) {
            Resources res = mContext.getResources();
            // (num of max queue rows * their height) + shadow
            mMaxQueueHeight = (mDisplayedRows
                    * res.getDimensionPixelSize(R.dimen.queue_row_height)); // 3 * queue_row_height
            if (mDisplayedRows > 0) {
                mMaxQueueHeight += res.getDimensionPixelSize(R.dimen.queue_top_shadow); // 3dp or so
            }
        }
        return mMaxQueueHeight;
    }

    @Override
    protected boolean filterMotionEvent(MotionEvent event) {
        if (!mQueueEnabled) {
            return super.filterMotionEvent(event);
        }
        if (isExpanded() && mQueue.isUserSelectingRow()
                && event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_UP
                && event.getActionMasked() != MotionEvent.ACTION_CANCEL) {
            return false;
        }

        return super.filterMotionEvent(event);
    }

    @Override
    public int getMaxHeight() {
    if (!mQueueEnabled) {
        return super.getMaxHeight();
    }
        final int maxHeight = super.getMaxHeight();
        return maxHeight + getMaxQueueHeight();
    }

    public void setMediaController(MediaController mediaController) {
        if (DEBUG) Log.d(TAG, "setMediaController() called with "
                + "mediaController = [" + mediaController + "]");
        mController = mediaController;
        if (mQueue.setController(mController) && mQueueEnabled) {
            notifyHeightChanged();
        }
    }

    /**
     * Apply an expansion state to the layout.
     */
    @Override
    public void applyExpansionToLayout() {
        if (!mQueueEnabled) {
            super.applyExpansionToLayout();
            return;
        }

        if (DEBUG) Log.i(TAG, "applyExpansionToLayout()");

        boolean expand = isExpanded();
        if (expand && isExpandable()) {
            setActualHeight(mMaxExpandHeight);
        } else {
            setActualHeight(getMinHeight());
        }
    }

    @Override
    protected void updateMaxExpandHeight() {
        if (!mQueueEnabled) {
            super.updateMaxExpandHeight();
            return;
        }

        int intrinsicBefore = getIntrinsicHeight();
        mMaxExpandHeight = mPrivateLayout.getMaxHeight() + getMaxQueueHeight();
        if (intrinsicBefore != getIntrinsicHeight()) {
            notifyHeightChanged();
        }
    }

    @Override
    public int getIntrinsicHeight() {
        if (mGuts != null && mGuts.getVisibility() == View.VISIBLE) {
            return mGuts.getActualHeight();
        }
        if (!mQueueEnabled) {
            return super.getIntrinsicHeight();
        }
        if (mShowingPublicForIntrinsicHeight) {
            return getMinHeight();
        }
        if (isUserLocked()) {
            return getActualHeight();
        }
        boolean inExpansionState = isExpanded();
        if (!inExpansionState) {
            // not expanded, so we return the collapsed size
            return getMinHeight();
        }
        return getMaxExpandHeight();
    }

    @Override
    public int getMaxExpandHeight() {
        if (mGuts != null && mGuts.getVisibility() == View.VISIBLE) {
            return mGuts.getActualHeight();
        }
        if (!mQueueEnabled) {
            return super.getMaxExpandHeight();
        }

        return mShowingPublicForIntrinsicHeight
                ? getMinHeight()
                : mMaxExpandHeight;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    private class SettingsObserver extends UserContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();
            mContext.getContentResolver().registerContentObserver(CMSettings.System.CONTENT_URI,
                    true, this);
        }

        @Override
        protected void unobserve() {
            super.unobserve();
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        protected void update() {
            mQueueEnabled = isQueueEnabled(mContext);
            mQueue.setQueueEnabled(mQueueEnabled);
            mQueueGroup.setVisibility(mQueueEnabled ? View.VISIBLE : View.GONE);
            mMaxQueueHeight = null;
        }
    }

    public static boolean isQueueEnabled(Context context) {
        return CMSettings.System.getInt(context.getContentResolver(),
                CMSettings.System.NOTIFICATION_PLAY_QUEUE, 1) == 1;
    }
}
