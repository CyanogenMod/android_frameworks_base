package com.android.systemui.statusbar.powerwidget;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.widget.ImageView;

import com.android.systemui.R;

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
    public static final String BUTTON_REBOOT = "toggleReboot";
    public static final String BUTTON_FCHARGE = "toggleFCharge";
    public static final String BUTTON_UNKNOWN = "unknown";
    private static final String SEPARATOR = "OV=I=XseparatorX=I=VO";
//    private static final Mode MASK_MODE = Mode.SCREEN;

    protected int mIcon;
    protected int mState;
    protected View mView;
    protected String mType = BUTTON_UNKNOWN;

    private ImageView mIconView;

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
        }
    };

    protected abstract void updateState(Context context);
    protected abstract void toggleState(Context context);
    protected abstract boolean handleLongClick(Context context);

    protected void update(Context context) {

	boolean mEnableToggleColors = Settings.System.getInt(context.getContentResolver(),
                  Settings.System.ENABLE_TOGGLE_COLORS, 0) != 0;
	boolean mEnableToggleBar = Settings.System.getInt(context.getContentResolver(),
                  Settings.System.ENABLE_TOGGLE_BAR, 0) != 0;

	if(mEnableToggleBar || mEnableToggleColors) {
//    	int colorBackgroundOn = Color.TRANSPARENT;
//    	int colorBackgroundOff = Color.TRANSPARENT;
    	int colorIconOn;
    	int colorIconOff;
    	int defaultColor;
    	int defaultOffColor;
	int defaultIntermediatColor;
	int colorIconIntermediate;

        float[] hsv = new float[3];
        defaultColor = context.getResources().getColor(
                com.android.internal.R.color.holo_blue_light);
        Color.colorToHSV(defaultColor, hsv);
        hsv[2] *= 0.5f; // value component
        defaultOffColor = Color.HSVToColor(hsv);

	colorIconOn = Settings.System.getInt(context.getContentResolver(),
                	Settings.System.TOGGLE_ICON_ON_COLOR, defaultColor);
	colorIconOff = Settings.System.getInt(context.getContentResolver(),
                	Settings.System.TOGGLE_ICON_OFF_COLOR, defaultOffColor);

		if(mState == STATE_ENABLED) {
		Drawable bg = context.getResources().getDrawable(R.drawable.btn_on);
		   if(mEnableToggleBar) {
            	   bg.setColorFilter(defaultColor, PorterDuff.Mode.SRC_ATOP);
		   mIconView.setBackgroundDrawable(bg);
		   }
		   if(mEnableToggleColors) {
		   mIconView.setColorFilter(colorIconOn, PorterDuff.Mode.SRC_ATOP);
//		   mIconView.setBackgroundColor(colorBackgroundOn);
		   }
		} else if(mState == STATE_DISABLED || mState == STATE_UNKNOWN) {
		Drawable bg = context.getResources().getDrawable(R.drawable.btn_off);
		   if(mEnableToggleBar) {
            	   bg.setColorFilter(defaultOffColor, PorterDuff.Mode.SRC_ATOP);
		   mIconView.setBackgroundDrawable(bg);
		   }
		   if(mEnableToggleColors) {		   
		   mIconView.setColorFilter(colorIconOff, PorterDuff.Mode.SRC_ATOP);
//		   mIconView.setBackgroundColor(colorBackgroundOff);
		   }
		} else {
		Drawable bg = context.getResources().getDrawable(R.drawable.btn_on);
		   if(mEnableToggleBar) {
       		   Color.colorToHSV(defaultColor, hsv);
        	   hsv[2] *= 0.7f; // value component
        	   defaultIntermediatColor = Color.HSVToColor(hsv);
            	   bg.setColorFilter(defaultIntermediatColor, PorterDuff.Mode.SRC_ATOP);
		   mIconView.setBackgroundDrawable(bg);
		   }
		   if(mEnableToggleColors) {
       		   Color.colorToHSV(colorIconOn, hsv);
        	   hsv[2] *= 0.7f; // value component
        	   colorIconIntermediate = Color.HSVToColor(hsv);	   
		   mIconView.setColorFilter(colorIconIntermediate, PorterDuff.Mode.SRC_ATOP);
//		   mIconView.setBackgroundColor(colorBackgroundOff);
		   }
		}
	}
        updateState(context);
        updateView();
    }

    public String[] parseStoredValue(CharSequence val) {
        if (TextUtils.isEmpty(val)) {
          return null;
        } else {
          return val.toString().split(SEPARATOR);
        }
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
            mVibrator = (Vibrator) mView.getContext().getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            mIconView = null;
        }
    }

    protected void updateView() {
        mViewUpdateHandler.sendEmptyMessage(0);
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mHapticFeedback && mClickPattern != null) {
                if (mClickPattern.length == 1) {
                    // One-shot vibration
                    mVibrator.vibrate(mClickPattern[0]);
                } else {
                    // Pattern vibration
                    mVibrator.vibrate(mClickPattern, -1);
                }
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

            if (result) {
                try {
                    WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
                } catch (RemoteException e) {
                }
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

