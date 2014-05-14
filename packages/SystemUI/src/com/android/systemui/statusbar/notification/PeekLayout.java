/*
 * Copyright (C) 2014 ParanoidAndroid Project.
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

package com.android.systemui.statusbar.notification;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;
import com.android.systemui.SwipeHelper;

public class PeekLayout extends LinearLayout implements SwipeHelper.Callback {

    private SwipeHelper mSwipeHelper;

    private Peek mPeek;

    public PeekLayout(Context context) {
        this(context, null);
    }

    public PeekLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        float densityScale = context.getResources().getDisplayMetrics().density;
        float pagingTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, this, densityScale, pagingTouchSlop);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float densityScale = getContext().getResources().getDisplayMetrics().density;
        mSwipeHelper.setDensityScale(densityScale);
        float pagingTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
        mSwipeHelper.setPagingTouchSlop(pagingTouchSlop);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mSwipeHelper.onInterceptTouchEvent(event) || super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mSwipeHelper.onTouchEvent(event) || super.onTouchEvent(event);
    }

    public void setPeek(Peek peek) {
        mPeek = peek;
    }

    @Override
    public View getChildAtPosition(MotionEvent ev) {
        return getChildContentView(null);
    }

    @Override
    public View getChildContentView(View v) {
        return mPeek.getNotificationView();
    }

    @Override
    public boolean canChildBeDismissed(View v) {
        StatusBarNotification n = (StatusBarNotification)
                mPeek.getNotificationView().getTag();
        return n.isClearable();
    }

    @Override
    public void onChildDismissed(View v) {
        StatusBarNotification n = (StatusBarNotification)
                mPeek.getNotificationView().getTag();
        final String pkg = n.getPackageName();
        final String tag = n.getTag();
        final int id = n.getId();
        try {
            mPeek.getStatusBarService().onNotificationClear(pkg, tag, id);
            mPeek.setAnimating(false);
        } catch (RemoteException ex) {
            // system process is dead if we're here.
        }
    }

    @Override
    public void onBeginDrag(View v) {
        mPeek.setAnimating(true);
    }

    @Override
    public void onDragCancelled(View v) {
        mPeek.setAnimating(false);
    }
}
