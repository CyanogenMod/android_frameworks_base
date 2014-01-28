/*
 * Copyright (C) 2011 Sergey Margaritov
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

package android.preference;

import android.app.ColorPickerDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.drawable.AlphaPatternDrawable;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

/**
 * A {@link Preference} that provides a Color picker
 * functionality.
 * <p>
 * This preference will store a Color into the SharedPreferences.
 *
 * @author Sergey Margaritov
 * @attr ref android.R.styleable#ColorPickerPreference_defaultColor
 * @attr ref android.R.styleable#ColorPickerPreference_alphaSlider
 */
public class ColorPickerPreference extends Preference implements
            Preference.OnPreferenceClickListener, ColorPickerDialog.OnColorChangedListener {

    private View mView;
    private ColorPickerDialog mDialog;

    private int mValue;
    private float mDensity = 0;

    private boolean mAlphaSliderEnabled = false;
    private boolean mHexValueEnabled = false;
    private boolean mDefaultColorPanelsEnabled = false;

    public ColorPickerPreference(Context context) {
        super(context);
        init(context, null, -1);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, -1);
    }

    public ColorPickerPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getColor(index, Color.BLACK);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        onColorChanged(restoreValue ? getPersistedInt(mValue) : (Integer) defaultValue);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        mDensity = getContext().getResources().getDisplayMetrics().density;
        setOnPreferenceClickListener(this);
        TypedArray a = context.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.ColorPickerPreference, defStyle, 0);
        mAlphaSliderEnabled = a.getBoolean(
                com.android.internal.R.styleable.ColorPickerPreference_alphaSlider, false);
        mHexValueEnabled = a.getBoolean(
                com.android.internal.R.styleable.ColorPickerPreference_inputEnabled, false);
        mDefaultColorPanelsEnabled = a.getBoolean(
                com.android.internal.R.styleable.ColorPickerPreference_enableDefaultColorPanels,
                false);

        a.recycle();
    }


    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        mView = view;
        setPreviewColor();
    }
    private void setPreviewColor() {
        if (mView == null) return;

        ImageView iView = new ImageView(getContext());
        LinearLayout widgetFrameView = ((LinearLayout) mView
                .findViewById(com.android.internal.R.id.widget_frame));

        if (widgetFrameView == null) return;

        widgetFrameView.setVisibility(View.VISIBLE);
        widgetFrameView.setPadding(widgetFrameView.getPaddingLeft(),
                widgetFrameView.getPaddingTop(), (int) (mDensity * 8),
                widgetFrameView.getPaddingBottom());
        // remove already create preview image
        int count = widgetFrameView.getChildCount();
        if (count > 0) {
            widgetFrameView.removeViews(0, count);
        }
        widgetFrameView.addView(iView);
        widgetFrameView.setMinimumWidth(0);
        iView.setBackgroundDrawable(new AlphaPatternDrawable((int) (5 * mDensity)));
        iView.setImageBitmap(getPreviewBitmap());
    }

    private Bitmap getPreviewBitmap() {
        int d = (int) (mDensity * 31); // 30dip
        int color = mValue;
        Bitmap bm = Bitmap.createBitmap(d, d, Config.ARGB_8888);
        int w = bm.getWidth();
        int h = bm.getHeight();
        int c = color;
        for (int i = 0; i < w; i++) {
            for (int j = i; j < h; j++) {
                c = (i <= 1 || j <= 1 || i >= w - 2 || j >= h - 2) ? Color.GRAY : color;
                bm.setPixel(i, j, c);
                if (i != j) {
                    bm.setPixel(j, i, c);
                }
            }
        }
        return bm;
    }

    @Override
    public void onColorChanged(int color) {
        if (isPersistent()) {
            persistInt(color);
        }
        mValue = color;
        setPreviewColor();
        try {
            getOnPreferenceChangeListener().onPreferenceChange(this, color);
        } catch (NullPointerException e) {
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        showDialog(null);
        return false;
    }

    protected void showDialog(Bundle state) {
        mDialog = new ColorPickerDialog(getContext(), mValue);
        mDialog.setOnColorChangedListener(this);
        if (mAlphaSliderEnabled) {
            mDialog.setAlphaSliderVisible(true);
        }
        if (mHexValueEnabled) {
            mDialog.setHexValueVisible(true);
        }
        if (mDefaultColorPanelsEnabled) {
            mDialog.setDefaultColorPanelsVisible(true);
        }
        if (state != null) {
            mDialog.onRestoreInstanceState(state);
        }
        mDialog.show();
    }

    /**
     * Toggle Alpha Slider visibility (by default it's disabled)
     * @param enable
     */
    public void setAlphaSliderEnabled(boolean enable) {
        mAlphaSliderEnabled = enable;
    }

    /**
     * Toggle Hex Value visibility (by default it's disabled)
     * @param enable
     */
    public void setHexValueEnabled(boolean enable) {
        mHexValueEnabled = enable;
    }

    /**
     * Toggle Default color panels visibility (by default it's disabled)
     * @param enable
     */
    public void setDefaultColorPanelsEnabled(boolean enable) {
        mDefaultColorPanelsEnabled = enable;
    }

    /**
     * For custom purposes. Not used by ColorPickerPreferrence
     * @param color
     * @author Unknown
     */
    public static String convertToARGB(int color) {
        String alpha = Integer.toHexString(Color.alpha(color));
        String red = Integer.toHexString(Color.red(color));
        String green = Integer.toHexString(Color.green(color));
        String blue = Integer.toHexString(Color.blue(color));

        if (alpha.length() == 1) {
            alpha = "0" + alpha;
        }

        if (red.length() == 1) {
            red = "0" + red;
        }

        if (green.length() == 1) {
            green = "0" + green;
        }

        if (blue.length() == 1) {
            blue = "0" + blue;
        }
        return "#" + alpha + red + green + blue;
    }

    /**
     * For custom purposes. Not used by ColorPickerPreference
     * @param color
     * @author Charles Rosaaen
     * @return A string representing the hex value of color,
     * without the alpha value
     */
    public static String convertToRGB(int color) {
        String red = Integer.toHexString(Color.red(color));
        String green = Integer.toHexString(Color.green(color));
        String blue = Integer.toHexString(Color.blue(color));

        if (red.length() == 1) {
            red = "0" + red;
        }

        if (green.length() == 1) {
            green = "0" + green;
        }

        if (blue.length() == 1) {
            blue = "0" + blue;
        }

        return "#" + red + green + blue;
    }

    /**
     * For custom purposes. Not used by ColorPickerPreferrence
     * @param argb
     * @throws NumberFormatException
     * @author Unknown
     */
    public static int convertToColorInt(String argb) throws IllegalArgumentException {

        if (!argb.startsWith("#")) {
            argb = "#" + argb;
        }

        return Color.parseColor(argb);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }
        if (mDialog == null || !mDialog.isShowing()) {
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.dialogBundle = mDialog.onSaveInstanceState();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class) ||
                    !(state instanceof SavedState)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        showDialog(myState.dialogBundle);
    }

    private static class SavedState extends BaseSavedState {
        Bundle dialogBundle;

        public SavedState(Parcel source) {
            super(source);
            dialogBundle = source.readBundle();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeBundle(dialogBundle);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
}
