
package com.android.systemui.statusbar.policy;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.widget.TextView;

public class SignalText extends TextView {

    private Context mContext;
    private TelephonyManager mTelephonyManager;

    private int dBm = 0;

    public static final int STYLE_HIDE = 0;
    public static final int STYLE_SHOW = 1;
    public static final int STYLE_SHOW_DBM = 2;

    private int mTextStyle;
    private int mSignalColor;

    private ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
            apply();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
            apply();
        }
    };


    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            dBm = signalStrength.getDbm();

            apply();
        }
    };

    public SignalText(Context context) {
        this(context, null);
    }

    public SignalText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.STATUSBAR_SIGNAL_TEXT), false,
                mSettingsObserver);
        resolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.STATUSBAR_SIGNAL_TEXT_COLOR), false,
                mSettingsObserver);

        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        updateSettings();
        apply();
    }

    @Override
    protected void onDetachedFromWindow() {
        mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);

        super.onDetachedFromWindow();
    }

    private void updateSettings() {
        ContentResolver resolver = getContext().getContentResolver();
        mSignalColor = Settings.System.getInt(resolver,
                Settings.System.STATUSBAR_SIGNAL_TEXT_COLOR,
                Color.WHITE);
        if (mSignalColor == Integer.MIN_VALUE) {
            // flag to reset the color
            mSignalColor = Color.WHITE;
        }
        setTextColor(mSignalColor);

        mTextStyle = Settings.System.getInt(getContext().getContentResolver(),
                Settings.System.STATUSBAR_SIGNAL_TEXT, STYLE_HIDE);
    }


    private void apply() {
        if (mTextStyle == STYLE_SHOW) {
            String result = Integer.toString(dBm);

            setText(result + " ");
        } else if (mTextStyle == STYLE_SHOW_DBM) {
            String result = Integer.toString(dBm) + " dBm ";

            SpannableStringBuilder formatted = new SpannableStringBuilder(result);
            int start = result.indexOf("d");

            CharacterStyle style = new RelativeSizeSpan(0.7f);
            formatted.setSpan(style, start, start + 3, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

            setText(formatted);
        } else {
            setText(null);
        }
    }
}
