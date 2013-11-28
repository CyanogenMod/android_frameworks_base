/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.keyguard;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.SpannableStringBuilder;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.internal.widget.LockPatternUtils;

import static java.util.Arrays.asList;
import java.util.Collections;
import java.util.List;

public class NumPadKey extends Button {
    // list of "ABC", etc per digit, starting with '0'
    static String sKlondike[];
    static List<Integer> sDigits = asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    static int sCount = 0;
    static boolean sShuffled;
    int mDigit = -1;
    int mTextViewResId;
    TextView mTextView = null;
    boolean mEnableHaptics;

    Context mContext;
    TypedArray mStyleable;

    private View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View thisView) {
            if (mTextView == null) {
                if (mTextViewResId > 0) {
                    final View v = NumPadKey.this.getRootView().findViewById(mTextViewResId);
                    if (v != null && v instanceof TextView) {
                        mTextView = (TextView) v;
                    }
                }
            }
            // check for time-based lockouts
            if (mTextView != null && mTextView.isEnabled()) {
                mTextView.append(String.valueOf(mDigit));
            }
            doHapticKeyClick();
        }
    };

    public NumPadKey(Context context) {
        this(context, null);
    }

    public NumPadKey(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NumPadKey(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mContext = context;

        mStyleable = mContext.obtainStyledAttributes(attrs, R.styleable.NumPadKey);

        setTextViewResId(mStyleable.getResourceId(R.styleable.NumPadKey_textView, 0));

        setOnClickListener(mListener);
        setOnHoverListener(new LiftToActivateListener(mContext));
        setAccessibilityDelegate(new ObscureSpeechDelegate(mContext));

        mEnableHaptics = new LockPatternUtils(mContext).isTactileFeedbackEnabled();
        createNumKeyPad(false);
    }

    public void createNumKeyPad(boolean enableRandom) {
        if (enableRandom) {
            if (!sShuffled) {
                Collections.shuffle(sDigits);
                sShuffled = true;
            }
            mDigit = sDigits.get(sCount);
        } else {
            mDigit = mStyleable.getInt(R.styleable.NumPadKey_digit, mDigit);
        }

        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(String.valueOf(mDigit));
        if (mDigit >= 0) {
            if (sKlondike == null) {
                sKlondike = mContext.getResources().getStringArray(
                        R.array.lockscreen_num_pad_klondike);
            }
            if (sKlondike != null && sKlondike.length > mDigit) {
                final String extra = sKlondike[mDigit];
                final int extraLen = extra.length();
                if (extraLen > 0) {
                    builder.append(" ");
                    builder.append(extra);
                    builder.setSpan(
                        new TextAppearanceSpan(mContext, R.style.TextAppearance_NumPadKey_Klondike),
                        builder.length()-extraLen, builder.length(), 0);
                }
            }
        }
        sCount++;
        setText(builder);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        // Reset the "announced headset" flag when detached.
        ObscureSpeechDelegate.sAnnouncedHeadset = false;
    }

    public void setTextView(TextView tv) {
        mTextView = tv;
    }

    public void setTextViewResId(int resId) {
        mTextView = null;
        mTextViewResId = resId;
    }

    // Cause a VIRTUAL_KEY vibration
    public void doHapticKeyClick() {
        if (mEnableHaptics) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                    | HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    public void initNumKeyPad() {
        sCount = 0;
        sShuffled = false;
    }
}
