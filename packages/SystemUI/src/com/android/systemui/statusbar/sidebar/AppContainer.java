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

package com.android.systemui.statusbar.sidebar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import com.android.systemui.R;

public class AppContainer extends LinearLayout {
    private float mInsertDelta;
    private ItemInfo mAddToItem = null;

    public AppContainer(Context context) {
        this(context, null);
    }

    public AppContainer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mInsertDelta = context.getResources().getDimensionPixelSize(R.dimen.item_above_below_delta);
    }
    
    public void repositionView(View view, float x, float y, boolean isFolder) {
        int index = indexOfChild(view);
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            float mid = v.getY() + v.getHeight()/2;
            if (i == index){
                if((index < childCount-1 && y > mid+mInsertDelta && y < v.getY()+v.getHeight()) &&
                        view.getVisibility() != View.GONE) {
                    removeView(view);
                    view.setVisibility(View.GONE);
                    addView(view, i+1);
                }
                continue;
            }
            if (v != view)
                v.setBackgroundResource(0);
            if ((i < index && (y > v.getY() && y < mid-mInsertDelta)) ||
                    (i > index && (y > mid+mInsertDelta && y < v.getY()+v.getHeight()))) {
                removeView(view);
                addView(view, i);
                view.setVisibility(View.VISIBLE);
                mAddToItem = null;
                return;
            } else if (!isFolder && v != view && y >= mid-mInsertDelta && y <= mid+mInsertDelta) {
                view.setVisibility(View.GONE);
                v.setBackgroundResource(R.drawable.item_placeholder);
                mAddToItem = (ItemInfo) v.getTag();
                return;
            } else
                view.setVisibility(View.VISIBLE);
            mAddToItem = null;
        }
    }
    
    public ItemInfo getAddToItem() {
        return mAddToItem;
    }
}
