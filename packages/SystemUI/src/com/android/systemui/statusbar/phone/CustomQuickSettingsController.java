package com.android.systemui.statusbar.phone;

import java.util.ArrayList;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.android.systemui.quicksettings.AirplaneModeQuickSettings;
import com.android.systemui.quicksettings.AlarmQuickSettings;
import com.android.systemui.quicksettings.AutoRotateQuickSettings;
import com.android.systemui.quicksettings.BluetoothQuickSettings;
import com.android.systemui.quicksettings.BrightnessQuickSettings;
import com.android.systemui.quicksettings.BugReportQuickSettings;
import com.android.systemui.quicksettings.CustomQuickSettings;
import com.android.systemui.quicksettings.GPSQuickSettings;
import com.android.systemui.quicksettings.MobileNetworkQuickSettings;
import com.android.systemui.quicksettings.MobileNetworkTypeQuickSettings;
import com.android.systemui.quicksettings.RingerQuickSettings;
import com.android.systemui.quicksettings.RingerVibrationQuickSettings;
import com.android.systemui.quicksettings.SleepQuickSettings;
import com.android.systemui.quicksettings.ToggleLockscreenQuickSettings;
import com.android.systemui.quicksettings.VibrationQuickSettings;
import com.android.systemui.quicksettings.WiFiDisplayQuickSettings;
import com.android.systemui.quicksettings.WiFiQuickSettings;

public class CustomQuickSettingsController {

    private final Context mContext;
    public PanelBar mBar;
    private final ViewGroup mContainerView;
    private final Handler mHandler;
    private final ArrayList<Integer> quicksettings;
    public PhoneStatusBar mStatusBarService;
    private final DisplayManager mDisplayManager;

    // Constants

    public static final int WIFI_TILE = 0;
    public static final int MOBILE_NETWORK_TILE = 1;
    public static final int AIRPLANE_MODE_TILE = 2;
    public static final int BLUETOOTH_TILE = 3;
    public static final int SOUND_TILE = 4;
    public static final int VIBRATION_TILE = 5;
    public static final int SOUND_VIBRATION_TILE = 6;
    public static final int SLEEP_TILE = 7;
    public static final int TOGGLE_LOCKSCREEN_TILE = 8;
    public static final int GPS_TILE = 9;
    public static final int AUTO_ROTATION_TILE = 10;
    public static final int BRIGHTNESS_TILE = 11;
    public static final int MOBILE_NETWORK_TYPE_TILE = 12;

    public static final int ALARM_TILE = 13;
    public static final int BUG_REPORT_TILE = 14;
    public static final int WIFI_DISPLAY_TILE = 15;

    public CustomQuickSettingsController(Context context, QuickSettingsContainerView container, PhoneStatusBar statusBarService) {
        mContext = context;
        mContainerView = container;
        mHandler = new Handler();
        quicksettings = new ArrayList<Integer>();
        quicksettings.add(WIFI_TILE);
        quicksettings.add(MOBILE_NETWORK_TILE);
        quicksettings.add(AIRPLANE_MODE_TILE);
        quicksettings.add(BLUETOOTH_TILE);
        //quicksettings.add(SOUND_TILE);
        //quicksettings.add(VIBRATION_TILE);
        quicksettings.add(SOUND_VIBRATION_TILE);
        quicksettings.add(SLEEP_TILE);
        quicksettings.add(TOGGLE_LOCKSCREEN_TILE);
        quicksettings.add(GPS_TILE);
        quicksettings.add(AUTO_ROTATION_TILE);
        quicksettings.add(BRIGHTNESS_TILE);
        //quicksettings.add(MOBILE_NETWORK_TYPE_TILE);
        quicksettings.add(ALARM_TILE);
        quicksettings.add(BUG_REPORT_TILE);
        quicksettings.add(WIFI_DISPLAY_TILE);
        mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        mStatusBarService = statusBarService;
        setupQuickSettings();
    }

    void setupQuickSettings(){
        LayoutInflater inflater = LayoutInflater.from(mContext);
        addQuickSettings(inflater);
    }

    void setBar(PanelBar bar) {
        mBar = bar;
    }

    void addQuickSettings(LayoutInflater inflater){
        for(Integer entry: quicksettings){
            CustomQuickSettings qs = null;
            switch(entry){
            case WIFI_TILE:
                qs = new WiFiQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case MOBILE_NETWORK_TILE:
                qs = new MobileNetworkQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case AIRPLANE_MODE_TILE:
                qs = new AirplaneModeQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case BLUETOOTH_TILE:
                qs = new BluetoothQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case SOUND_TILE:
                qs = new RingerQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case VIBRATION_TILE:
                qs = new VibrationQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case SOUND_VIBRATION_TILE:
                qs = new RingerVibrationQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case SLEEP_TILE:
                qs = new SleepQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case TOGGLE_LOCKSCREEN_TILE:
                qs = new ToggleLockscreenQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case GPS_TILE:
                qs = new GPSQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case AUTO_ROTATION_TILE:
                qs = new AutoRotateQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case BRIGHTNESS_TILE:
                qs = new BrightnessQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case MOBILE_NETWORK_TYPE_TILE:
                qs = new MobileNetworkTypeQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            case ALARM_TILE:
                qs = new AlarmQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case BUG_REPORT_TILE:
                qs = new BugReportQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this, mHandler);
                break;
            case WIFI_DISPLAY_TILE:
                qs = new WiFiDisplayQuickSettings(mContext, inflater, (QuickSettingsContainerView) mContainerView, this);
                break;
            }
            if(qs != null){
                qs.setupQuickSettingsTile();
            }
        }
    }

    public void setService(PhoneStatusBar phoneStatusBar) {
        mStatusBarService = phoneStatusBar;
    }

    public void setImeWindowStatus(boolean visible){

    }

    public void updateResources() {
        // TODO Auto-generated method stub

    }

}
