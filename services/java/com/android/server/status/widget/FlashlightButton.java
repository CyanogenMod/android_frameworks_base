package com.android.server.status.widget;

import com.android.internal.R;
import com.android.server.status.widget.PowerButton;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;
import android.os.Build;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.lang.Thread;
import java.lang.Runnable;
import java.io.File;
import java.io.FileWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class FlashlightButton extends PowerButton {

    Context mContext;

    static FlashlightButton ownButton;
    private static final String TAG = "FlashlightButton";
    private static int currentMode;

    private static FileWriter mWriter;

    private static ExecutorService threadExecutor;
    private static boolean runTimer = false;

    private static final int MODE_DEFAULT = 0;
    private static final int MODE_HIGH = 1;
    private static boolean useDeathRay = !Build.DEVICE.equals("supersonic");;

    private static final String FLASHLIGHT_FILE;
    private static final String FLASHLIGHT_FILE_SPOTLIGHT = "/sys/class/leds/spotlight/brightness";
    static {
        File ff = new File(FLASHLIGHT_FILE_SPOTLIGHT);
        if (ff.exists()) {
            FLASHLIGHT_FILE = FLASHLIGHT_FILE_SPOTLIGHT;
        } else {
            FLASHLIGHT_FILE = "/sys/class/leds/flashlight/brightness";
        }
    }

    public void updateState(Context context) {
        mContext = context;
        boolean useCustomExp = Settings.System.getInt(mContext.getContentResolver(),
        Settings.System.NOTIF_EXPANDED_BAR_CUSTOM, 0) == 1;

        if(getFlashlightEnabled()) {
            if (useCustomExp) {
                currentIcon = com.android.internal.R.drawable.stat_flashlight_on_cust;
            } else {
                currentIcon = com.android.internal.R.drawable.stat_flashlight_on;
            }
            currentState = STATE_ENABLED;
        } else {
            if (useCustomExp) {
                currentIcon = com.android.internal.R.drawable.stat_flashlight_off_cust;
            } else {
                currentIcon = com.android.internal.R.drawable.stat_flashlight_off;
            }
            currentState = STATE_DISABLED;
        }
    }

    public void toggleState(Context context) {
        currentMode = Settings.System.getInt(context.getContentResolver(), Settings.System.EXPANDED_FLASH_MODE,
                    MODE_DEFAULT);
        boolean enabled = getFlashlightEnabled();
        runTimer = !enabled;
        setFlashlightEnabled(!enabled);
    }

    public static FlashlightButton getInstance() {
        if (ownButton==null) ownButton = new FlashlightButton();

        return ownButton;
    }


    public boolean getFlashlightEnabled() {
        try {
            FileInputStream fis = new FileInputStream(FLASHLIGHT_FILE);
            int result = fis.read();
            fis.close();
            return (result != '0');
        } catch (Exception e) {
            return false;
        }
    }

    public void setFlashlightEnabled(boolean on) {
        try {
            if (mWriter == null) {
                mWriter = new FileWriter(FLASHLIGHT_FILE);
            }
            int value = 0;
            if (on) {
                switch (currentMode) {
                    case MODE_HIGH:
                        value = useDeathRay ? 3 : 128;
                        break;
                    default:
                        value = 1;
                        break;
                }
            }
            mWriter.write(String.valueOf(value));
            mWriter.flush();
            if (!on) {
                mWriter.close();
                mWriter = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "setFlashlightEnabled failed", e);
        }
        startTimer(on);
    }

    private void startTimer(boolean on) {
        if (!on) return;
        if (threadExecutor == null) threadExecutor = Executors.newSingleThreadExecutor();

        flashTimer timerRun = new flashTimer();
        threadExecutor.execute(timerRun);
    }

    public class flashTimer implements Runnable {
        public flashTimer() {
        }
        public void run() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            setFlashlightEnabled(runTimer);
        }
    }


    @Override
    void initButton(int position) {
    }

}
