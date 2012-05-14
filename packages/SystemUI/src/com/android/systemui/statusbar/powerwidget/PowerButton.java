package com.android.systemui.statusbar.powerwidget;

import com.android.systemui.R;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.provider.Settings;
import android.view.View;
import android.provider.Settings;
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
    public static final String BUTTON_WIMAX = "toggleWimax";
    public static final String BUTTON_UNKNOWN = "unknown";

    private static final Mode MASK_MODE = Mode.SCREEN;

    protected int mIcon;
    protected int mState;
    protected View mView;
    protected String mType = BUTTON_UNKNOWN;

    private View.OnClickListener mExternalClickListener;
    private View.OnLongClickListener mExternalLongClickListener;

    // we use this to ensure we update our views on the UI thread
    private Handler mViewUpdateHandler = new Handler() {
            public void handleMessage(Message msg) {
                // this is only used to update the view, so do it
                if(mView != null) {
                    Context context = mView.getContext();
                    Resources res = context.getResources();
                    int buttonLayer = R.id.power_widget_button;
                    int buttonIcon = R.id.power_widget_button_image;
                    int buttonState = R.id.power_widget_button_indic;
                    ImageView indic = (ImageView)mView.findViewById(R.id.power_widget_button_indic);
                    if ((Settings.System.getInt(context.getContentResolver(),Settings.System.EXPANDED_HIDE_INDICATOR, 0)) == 1){
                        indic.setVisibility(8);
                    }else{
                        indic.setVisibility(0);
                    }
                    updateImageView(buttonIcon, mIcon);

                    int sColorMaskBase = Settings.System.getInt(context.getContentResolver(),
                            Settings.System.EXPANDED_VIEW_WIDGET_COLOR, 0xFF8DE20D);
                    int sColorMaskOn    = (sColorMaskBase & 0x00FFFFFF) | 0xA0000000;
                    int sColorMaskOff   = (sColorMaskBase & 0x00FFFFFF) | 0x33000000;
                    int sColorMaskInter = (sColorMaskBase & 0x00FFFFFF) | 0x60000000;

                    /* Button State */
                    switch(mState) {
                        case STATE_ENABLED:
                            updateImageView(buttonState,
                                    res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskOn, MASK_MODE));
                            break;
                        case STATE_DISABLED:
                            updateImageView(buttonState,
                                    res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskOff, MASK_MODE));
                            break;
                        default:
                            updateImageView(buttonState,
                                    res.getDrawable(R.drawable.stat_bgon_custom, sColorMaskInter, MASK_MODE));
                            break;
                    }
                }
            }
        };

    protected abstract void updateState();
    protected abstract void toggleState();
    protected abstract boolean handleLongClick();

    protected void update() {
        updateState();
        updateView();
    }

    protected void onReceive(Context context, Intent intent) {
        // do nothing as a standard, override this if the button needs to respond
        // to broadcast events from the StatusBarService broadcast receiver
    }

    protected void onChangeUri(Uri uri) {
        // do nothing as a standard, override this if the button needs to respond
        // to a changed setting
    }

    protected IntentFilter getBroadcastIntentFilter() {
        return new IntentFilter();
    }

    protected List<Uri> getObservedUris() {
        return new ArrayList<Uri>();
    }

    protected void setupButton(View view) {
        mView = view;
        if(mView != null) {
            mView.setTag(mType);
            mView.setOnClickListener(mClickListener);
            mView.setOnLongClickListener(mLongClickListener);
        }
    }

    protected void updateView() {
        mViewUpdateHandler.sendEmptyMessage(0);
    }

    private void updateImageView(int id, int resId) {
        ImageView imageIcon = (ImageView)mView.findViewById(id);
        imageIcon.setImageResource(resId);
    }

    private void updateImageView(int id, Drawable resDraw) {
        ImageView imageIcon = (ImageView)mView.findViewById(id);
        imageIcon.setImageResource(R.drawable.stat_bgon_custom);
        imageIcon.setImageDrawable(resDraw);
    }

    private View.OnClickListener mClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            toggleState();

            if (mExternalClickListener != null) {
                mExternalClickListener.onClick(v);
            }
        }
    };

    private View.OnLongClickListener mLongClickListener = new View.OnLongClickListener() {
        public boolean onLongClick(View v) {
            boolean result = handleLongClick();

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
}
