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
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.cards.internal.CardHeader;
import com.android.systemui.R;

/**
 * This class handles the header view.
 */
public class RecentHeader extends CardHeader {

    private Context mContext;

    private String mLabel;
    private int mHeaderHeight;
    private int mHeaderWidth;
    private float mHeaderTextSize;
    private int mExpandedButtonWidth;
    private float mScaleFactor;
    private boolean mScaleFactorChanged;

    public RecentHeader(Context context, String label, float scaleFactor) {
        super(context);
        mContext = context;
        mLabel = label;
        mScaleFactor = scaleFactor;
        initDimensions();

        Drawable d = mContext.getResources().getDrawable(R.drawable.card_menu_button_expand);
        mExpandedButtonWidth = d.getIntrinsicWidth();
    }

    // Update header content.
    public void updateHeader(String label, float scaleFactor) {
        mLabel = label;
        if (scaleFactor != mScaleFactor) {
            mScaleFactorChanged = true;
            mScaleFactor = scaleFactor;
            initDimensions();
        }

    }

    // Set initial dimensions.
    private void initDimensions() {
        Resources res = mContext.getResources();
        mHeaderHeight = (int) (res.getDimensionPixelSize(
                R.dimen.recent_app_icon_size) * mScaleFactor);
        mHeaderWidth = (int) (res.getDimensionPixelSize(
                R.dimen.recent_header_width) * mScaleFactor);
        mHeaderTextSize = res.getDimensionPixelSize(
                R.dimen.recent_text_size) * mScaleFactor;
    }

    /**
     * Assign the label to the view.
     */
    @Override
    public void setupInnerViewElements(ViewGroup parent, View view) {
        if (view == null || mLabel == null) {
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
            holder.textView = (TextView) view.findViewById(R.id.card_header_inner_simple_title);
            // Take scale factor into account if it is different then default or it has changed.
            if (mScaleFactor != RecentController.DEFAULT_SCALE_FACTOR || mScaleFactorChanged) {
                mScaleFactorChanged = false;
                int diff = 0;
                // We have on static element in the header (expanded icon button). Take this into
                // account to calculate the needed width.
                if (mScaleFactor < RecentController.DEFAULT_SCALE_FACTOR) {
                    diff = (int) ((mExpandedButtonWidth
                            - mExpandedButtonWidth * mScaleFactor) * 2);
                }
                final ViewGroup.LayoutParams layoutParams = holder.textView.getLayoutParams();
                layoutParams.width = mHeaderWidth - diff;
                layoutParams.height = mHeaderHeight;
                holder.textView.setLayoutParams(layoutParams);
                holder.textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, mHeaderTextSize);
            }
            view.setTag(holder);
        }
        int defaultCardText = mContext.getResources().getColor(
                R.color.card_text_color_header);
        int textColor = Settings.System.getIntForUser( mContext.getContentResolver(),
                Settings.System.RECENT_CARD_TEXT_COLOR,
                defaultCardText, UserHandle.USER_CURRENT);
        holder.textView.setText(mLabel);
        if (textColor != 0x00ffffff) {
            holder.textView.setTextColor(textColor);
        } else {
            holder.textView.setTextColor(defaultCardText);
        }
    }

    static class ViewHolder {
        TextView textView;
    }

}
