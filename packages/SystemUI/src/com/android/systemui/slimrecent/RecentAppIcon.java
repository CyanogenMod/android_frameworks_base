/*
 * Copyright (C) 2014 SlimRoms Project
 * Author: Lars Greiss - email: kufikugel@googlemail.com
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

package com.android.systemui.slimrecent;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;

import com.android.cards.internal.CardThumbnail;
import com.android.systemui.R;

/**
 * This class handles the view of our app icon.
 */
public class RecentAppIcon extends CardThumbnail {

    private Context mContext;

    private int mIconSize;
    private float mScaleFactor;
    private boolean mScaleFactorChanged;
    private String mPackageName;

    public RecentAppIcon(Context context, String packageName, float scaleFactor) {
        super(context);
        mContext = context;
        mPackageName = packageName;
        mScaleFactor = scaleFactor;

        mIconSize = (int) context.getResources()
                .getDimensionPixelSize(R.dimen.recent_app_icon_size);
    }

    // Update icon.
    public void updateIcon(String packageName, float scaleFactor) {
        mPackageName = packageName;
        if (scaleFactor != mScaleFactor) {
            mScaleFactorChanged = true;
            mScaleFactor = scaleFactor;
        }
    }

    /**
     * Assign the icon to the view. If it is cached fetch it from the cache.
     * If not call the app icon loader.
     */
    @Override
    public void setupInnerViewElements(ViewGroup parent, View view) {
        if (view == null || mPackageName == null) {
            return;
        }

        // We use here a view holder to reduce expensive findViewById calls
        // when getView is called on the arrayadapter which calls setupInnerViewElements.
        // Simply just check if the given view was already tagged. If yes we know it has
        // the appIconView we want to have. If not we search it, give it to the viewholder
        // and tag the view for the next call to reuse the holded information later.
        ViewHolder holder;
        holder = (ViewHolder) view.getTag();

        if (holder == null) {
            holder = new ViewHolder();
            holder.appIconView = (RecentImageView) view.findViewById(R.id.card_thumbnail_image);
            // Take scale factor into account if it is different then default or it has changed.
            if (mScaleFactor != RecentController.DEFAULT_SCALE_FACTOR || mScaleFactorChanged) {
                mScaleFactorChanged = false;
                final ViewGroup.LayoutParams layoutParams = holder.appIconView.getLayoutParams();
                layoutParams.width = layoutParams.height = (int) (mIconSize * mScaleFactor);
                holder.appIconView.setLayoutParams(layoutParams);
            }
            view.setTag(holder);
        }

        final Bitmap appIcon =
                CacheController.getInstance(mContext).getBitmapFromMemCache(mPackageName);
        if (appIcon == null || mScaleFactorChanged) {
            mScaleFactorChanged = false;
            AppIconLoader.getInstance(mContext).loadAppIcon(
                    mPackageName, holder.appIconView, mScaleFactor);
        } else {
            holder.appIconView.setImageBitmap(appIcon);
        }

    }

    static class ViewHolder {
        RecentImageView appIconView;
    }

}
