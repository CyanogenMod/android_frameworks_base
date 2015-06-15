/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.qs;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import android.widget.BaseAdapter;

import cyanogenmod.app.CustomTile;

import com.android.systemui.qs.tiles.UserDetailItemView;
import com.android.systemui.R;

/**
 * Quick settings common detail grid view with circular items.
 */
public class QSDetailItemsGrid extends PseudoGridView {
    private static final String TAG = "QSDetailItemsGrid";
    private QSDetailItemsGridAdapter mDetailItemsGridAdapter;

    public QSDetailItemsGrid(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public static QSDetailItemsGrid inflate(Context context, ViewGroup parent, boolean attach) {
        return (QSDetailItemsGrid) LayoutInflater.from(context).inflate(
                R.layout.qs_detail_items_grid, parent, attach);
    }

    public QSDetailItemsGridAdapter createAndSetAdapter(String externalPackage,
            CustomTile.ExpandedItem[] items) {
        mDetailItemsGridAdapter = new QSDetailItemsGridAdapter(externalPackage, mContext, items);
        ViewGroupAdapterBridge.link(this, mDetailItemsGridAdapter);
        return mDetailItemsGridAdapter;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnTouchListener(new OnTouchListener() {
            // Setting on Touch Listener for handling the touch inside ScrollView
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Disallow the touch request for parent scroll on touch of child view
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            }
        });
    }

    public static class QSDetailItemsGridAdapter extends BaseAdapter implements OnClickListener {
        private String mPkg;
        private Context mContext;
        private CustomTile.ExpandedItem[] mItems;
        private OnPseudoGriditemClickListener mOnPseudoGridItemClickListener;

        public interface OnPseudoGriditemClickListener {
            void onPsuedoGridItemClick(View view, CustomTile.ExpandedItem item);
        }

        public QSDetailItemsGridAdapter(String packageName,
                  Context context, CustomTile.ExpandedItem[] items) {
            mPkg = packageName;
            mContext = context;
            mItems = items;
        }

        public void setOnPseudoGridItemClickListener(OnPseudoGriditemClickListener
                onPseudoGridItemClickListener) {
            mOnPseudoGridItemClickListener = onPseudoGridItemClickListener;
        }

        @Override
        public int getCount() {
            return mItems.length;
        }

        @Override
        public CustomTile.ExpandedItem getItem(int position) {
            return mItems[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            UserDetailItemView v = UserDetailItemView.convertOrInflate(
                    mContext, convertView, parent);
            CustomTile.ExpandedItem item = getItem(i);
            Drawable d = null;
            try {
                d = getPackageContext(mPkg, mContext).getResources()
                        .getDrawable(item.itemDrawableResourceId);
            } catch (Throwable t) {
                Log.w(TAG, "Error creating package context" + mPkg +
                        " id=" + item.itemDrawableResourceId, t);
            }
            if (v != convertView) {
                v.setOnClickListener(this);
            }
            String name = item.itemTitle;
            v.setActivated(true);
            v.bind(name, d);
            v.setTag(item);
            return v;
        }

        @Override
        public void onClick(View view) {
            CustomTile.ExpandedItem item = (CustomTile.ExpandedItem) view.getTag();
            mOnPseudoGridItemClickListener.onPsuedoGridItemClick(view, item);
        }
    }

    private static Context getPackageContext(String pkg, Context context) {
        Context packageContext;
        try {
            packageContext = context.createPackageContext(pkg, 0);
        } catch (Throwable t) {
            Log.w(TAG, "Error creating package context" + pkg, t);
            return null;
        }
        return packageContext;
    }
}
