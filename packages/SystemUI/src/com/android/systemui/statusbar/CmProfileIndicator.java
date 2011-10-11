/*
 * Created by Sven Dawitz; Copyright (C) 2011 CyanogenMod Project
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

package com.android.systemui.statusbar;

import android.app.ProfileManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.provider.CmSystem;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.android.internal.R;

/**
 * @author Sven Dawitz for CyanogenMod
 * @category Dynamic statusbar icons
 */
public class CmProfileIndicator extends ImageView {
    // intent broadcasted by ProfileManagerServer after profile changed
    public static final String INTENT_ACTION_PROFILE_SELECTED = "android.intent.action.PROFILE_SELECTED";

    ProfileManager mProfileManager;

    // contains name of active profile
    private String mActiveProfileName = "";
    // contains initial letter of active profile
    private String mProfileInitial = "";
    // true, if active profile has statusbar indicator enabled
    private boolean mShowIndicator = false;
    // true only, as long as we are attached to statusbar
    private boolean mAttached = false;
    // background image on which we paint the letter
    private Bitmap mBackground;
    // holds our brush
    private Paint mPaint = null;
    // holds the color to use with this theme
    private int mColor;
    // some rects for precalculation
    final private Rect mDrawSrcRect = new Rect();
    final private Rect mDrawDstRect = new Rect();
    final private Rect mTextBounds = new Rect();

    /*
     * constructor - w00t w00t!
     * @param context
     */
    public CmProfileIndicator(Context context) {
        this(context, null);
    }

    public CmProfileIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CmProfileIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        // get color from global statusbar style
        TextAppearanceSpan ta = new TextAppearanceSpan(context,
                R.style.TextAppearance_StatusBar);
        mColor = ta.getTextColor().getDefaultColor();

        // initilize paint for later text output
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Style.FILL);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setFakeBoldText(false);
        mPaint.setColor(mColor);

        // get background drawable
        Bitmap bgImmutable = CmSystem.getBitmapFor(context.getResources(),
                R.drawable.stat_cm_profile_bg);
        mBackground=bgImmutable.copy(Bitmap.Config.ARGB_8888, true);

        // replace #ff00ff color in bitmap with color from theme
        for (int x = 0; x < mBackground.getWidth(); x++)
            for (int y = 0; y < mBackground.getHeight(); y++)
                if (mBackground.getPixel(x, y) == 0xffff00ff)
                    mBackground.setPixel(x, y, mColor);

        // get profilemanager
        mProfileManager = (ProfileManager) context.
                getSystemService(Context.PROFILE_SERVICE);

        mDrawSrcRect.set(0, 0, mBackground.getWidth(), mBackground.getHeight());
        setVisibility(View.GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;

            IntentFilter filter = new IntentFilter();
            filter.addAction(INTENT_ACTION_PROFILE_SELECTED);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());

        }

        // request active profile, since the initial broadcast was too early
        mProfileManager.triggerProfileBroadcast();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);

            mAttached = false;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(mBackground.getWidth(), height);
        mDrawDstRect.set(0, 0, mBackground.getWidth(), height);
        mPaint.setTextSize(height/2);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);

        if (!mAttached || !mShowIndicator)
            return;

        // clean canvas
        c.drawARGB(0x00, 0x00, 0x00, 0x00);

        //set up rectangles and draw background
        c.drawBitmap(mBackground, mDrawSrcRect, mDrawDstRect, null);


        //draw first letter of profile name
        mPaint.getTextBounds(mProfileInitial, 0, 1, mTextBounds);
        c.drawText(mProfileInitial, getWidth()/2, (getHeight() + mTextBounds.height()) * 0.5f, mPaint);
    }

    /*
     * Catches any changes to current profile
     */
    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(INTENT_ACTION_PROFILE_SELECTED)) {
                // set profile vars
                mActiveProfileName = intent.getStringExtra("name");
                mShowIndicator = intent.getBooleanExtra("showIndicator", false);
                mProfileInitial = mActiveProfileName.substring(0, 1);

                setVisibility(mShowIndicator ? View.VISIBLE : View.GONE);
                invalidate();
            }
        }
    };
}
