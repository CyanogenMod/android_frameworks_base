/*
 * Copyright (C) 2014 SlimRoms Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.view.View;

public class NavigationBarOverlay {

    private static final String TAG = NavigationBarOverlay.class.getSimpleName();

    private NavigationBarView mNavigationBar;

    private Context mContext;
    private boolean mAnimate = true;

    public NavigationBarOverlay() {
    }

    public void setNavigationBar(NavigationBarView view) {
        mNavigationBar = view;
        mNavigationBar.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                mAnimate = (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0;
            }
        });
    }

    public void setNavigationBarOverlay(float alpha, int color) {
        if (mNavigationBar == null || !mAnimate) {
            return;
        }
        final Drawable drawable = new ColorDrawable(manipulateAlpha(color, alpha));
        mNavigationBar.setForgroundColor(drawable);
    }

    public void setIsExpanded(boolean expanded) {
        mAnimate = !expanded;
    }

    private static int manipulateAlpha(int color, float alpha) {
        return Color.argb((int) (alpha * 255), Color.red(color),
                Color.green(color), Color.blue(color));
    }
}
