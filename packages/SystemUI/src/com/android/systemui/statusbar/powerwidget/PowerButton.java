package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.widget.ImageView;
import android.provider.Settings;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public abstract class PowerButton {
    public static final String TAG = "PowerButton";

    public static final int STATE_ENABLED = 1;
    public static final int STATE_DISABLED = 2;
    public static final int STATE_TURNING_ON = 3;
    public static final int STATE_TURNING_OFF = 4;
    public static final int STATE_INTERMEDIATE = 5;
    public static final int STATE_UNKNOWN = 6;

    public static final String BUTTON_WIFI = "toggleWifi";
    public static final String BUTTON_GPS = "toggleGPS";
    public static final String BUTTON_BLUETOOTH = "toggleBluetooth";
    public static final String BUTTON_BRIGHTNESS = "toggleBrightness";
    public static final String BUTTON_SOUND = "toggleSound";
    public static final String BUTTON_SYNC = "toggleSync";
    public static final String BUTTON_WIFIAP = "toggleWifiAp";
    public static final String BUTTON_SCREENTIMEOUT = "toggleScreenTimeout";
    public static final String BUTTON_MOBILEDATA = "toggleMobileData";
    public static final String BUTTON_LOCKSCREEN = "toggleLockScreen";
    public static final String BUTTON_NETWORKMODE = "toggleNetworkMode";
    public static final String BUTTON_AUTOROTATE = "toggleAutoRotate";
    public static final String BUTTON_AIRPLANE = "toggleAirplane";
    public static final String BUTTON_FLASHLIGHT = "toggleFlashlight";
    public static final String BUTTON_SLEEP = "toggleSleepMode";
    public static final String BUTTON_MEDIA_PLAY_PAUSE = "toggleMediaPlayPause";
    public static final String BUTTON_MEDIA_PREVIOUS = "toggleMediaPrevious";
    public static final String BUTTON_MEDIA_NEXT = "toggleMediaNext";
    public static final String BUTTON_LTE = "toggleLte";
    public static final String BUTTON_WIMAX = "toggleWimax";
    public static final String BUTTON_UNKNOWN = "unknown";

    private static final Mode MASK_MODE = Mode.SCREEN;

    protected int mIcon;
    protected int mState;
    protected View mView;
    protected String mType = BUTTON_UNKNOWN;

    private ImageView mIconView;
    private ImageView mIndicatorView;

    private View.OnClickListener mExternalClickListener;
    private View.OnLongClickListener mExternalLongClickListener;

    protected boolean mHapticFeedback;
    protected Vibrator mVibrator;
    private long[] mClickPattern;
    private long[] mLongClickPattern;

    // we use this to ensure we update our views on the UI thread
    private Handler mViewUpdateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (mIconView != null) {
                mIconView.setImageResource(mIcon);
            }
            if (mIndicatorView != null) {
                Context context = mIndicatorView.getContext();
                ContentResolver cr = context.getContentResolver();
                Resources res = context.getResources();

                boolean visible = Settings.System.getInt(cr,
                        Settings.System.EXPANDED_HIDE_INDICATOR, 0) == 1;
                int colorMask = res.getColor(android.R.color.holo_blue_light);

                mIndicatorView.setVisibility(visible ? View.VISIBLE : View.GONE);

                switch (mState) {
                    case STATE_ENABLED:
                        colorMask = (colorMask & 0x00FFFFFF) | 0xA0000000;
                        break;
                    case STATE_DISABLED:
                        colorMask = (colorMask & 0x00FFFFFF) | 0x33000000;
                        break;
                    default:
                        colorMask = (colorMask & 0x00FFFFFF) | 0x60000000;
                        break;
                }

                mIndicatorView.setImageDrawable(context.getResources().getDrawable(
                            R.drawable.stat_bgon_custom, colorMask, MASK_MODE));
            }
        }
    };

    protected abstract void updateState(Context context);
    protected abstract void toggleState(Context context);
    protected abstract boolean handleLongClick(Context context);

    protected void update(Context context) {
        updateState(context);
        updateView();
    }

    protected void onReceive(Context context, Intent intent) {
        // do nothing as a standard, override this if the button needs to respond
        // to broadcast events from the StatusBarService broadcast receiver
    }

    protected void onChangeUri(ContentResolver resolver, Uri uri) {
        // do nothing as a standard, override this if the button needs to respond
        // to a changed setting
    }

    /* package */ void setHapticFeedback(boolean enabled,
            long[] clickPattern, long[] longClickPattern) {
        mHapticFeedback = enabled;
        mClickPattern = clickPattern;
        mLongClickPattern = longClickPattern;
    }

    protected IntentFilter getBroadcastIntentFilter() {
        return new IntentFilter();
    }

    protected List<Uri> getObservedUris() {
        return new ArrayList<Uri>();
    }

    protected void setupButton(View view) {
        mView = view;
        if (mView != null) {
            mView.setTag(mType);
            mView.setOnClickListener(mClickListener);
            mView.setOnLongClickListener(mLongClickListener);

            mIconView = (ImageView) mView.findViewById(R.id.power_widget_button_image);
            mIndicatorView = (ImageView) mView.findViewById(R.id.power_widget_button_indic);
            mVibrator = (Vibrator) mView.getContext().getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            mIconView = null;
            mIndicatorView = null;
        }
    }

    protected void updateView() {
        mViewUpdateHandler.sendEmptyMessage(0);
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mHapticFeedback && mClickPattern != null) {
                mVibrator.vibrate(mClickPattern, -1);
            }
            toggleState(v.getContext());
            update(v.getContext());

            if (mExternalClickListener != null) {
                mExternalClickListener.onClick(v);
            }
        }
    };

    private View.OnLongClickListener mLongClickListener = new View.OnLongClickListener() {
        public boolean onLongClick(View v) {
            boolean result = handleLongClick(v.getContext());

            if (result && mHapticFeedback && mLongClickPattern != null) {
                mVibrator.vibrate(mLongClickPattern, -1);
            }

            try {
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            } catch (RemoteException e) {
            }

            if (result && mExternalLongClickListener != null) {
                mExternalLongClickListener.onLongClick(v);
            }
            return result;
        }
    };

    void setExternalClickListener(View.OnClickListener listener) {
        mExternalClickListener = listener;
    }

    void setExternalLongClickListener(View.OnLongClickListener listener) {
        mExternalLongClickListener = listener;
    }

    protected SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences("PowerButton-" + mType, Context.MODE_PRIVATE);
    }
}
