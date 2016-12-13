package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.Clock;
import com.android.systemui.tuner.TunerService;

import static com.android.systemui.statusbar.policy.Clock.AM_PM_STYLE_GONE;
import static com.android.systemui.statusbar.policy.Clock.CLOCK_SECONDS;

/**
 * To control your...clock
 */
public class ClockController implements TunerService.Tunable {

    private static final String TAG = "ClockController";

    public static final int CLOCK_POSITION_RIGHT = 0;
    public static final int CLOCK_POSITION_CENTER = 1;
    public static final int CLOCK_POSITION_LEFT = 2;

    public static final String CLOCK_POSITION = "cmsystem:status_bar_clock";
    public static final String CLOCK_STYLE = "cmsystem:status_bar_am_pm";

    private final NotificationIconAreaController mNotificationIconAreaController;
    private final Context mContext;
    private Clock mRightClock, mCenterClock, mLeftClock, mActiveClock;

    private int mAmPmStyle = AM_PM_STYLE_GONE;
    private int mClockPosition = CLOCK_POSITION_RIGHT;
    private boolean mClockVisible = true;
    private boolean mShowSeconds = false;

    private int mIconTint = Color.WHITE;

    public ClockController(View statusBar,
            NotificationIconAreaController notificationIconAreaController, Handler handler) {
        mRightClock = (Clock) statusBar.findViewById(R.id.clock);
        mCenterClock = (Clock) statusBar.findViewById(R.id.center_clock);
        mLeftClock = (Clock) statusBar.findViewById(R.id.left_clock);
        mNotificationIconAreaController = notificationIconAreaController;
        mContext = statusBar.getContext();

        mActiveClock = mRightClock;

        TunerService.get(mContext).addTunable(this, CLOCK_POSITION, CLOCK_STYLE, CLOCK_SECONDS);
    }

    private Clock getClockForCurrentLocation() {
        Clock clockForAlignment;
        switch (mClockPosition) {
            case CLOCK_POSITION_CENTER:
                clockForAlignment = mCenterClock;
                break;
            case CLOCK_POSITION_LEFT:
                clockForAlignment = mLeftClock;
                break;
            case CLOCK_POSITION_RIGHT:
            default:
                clockForAlignment = mRightClock;
                break;
        }
        return clockForAlignment;
    }

    private void updateActiveClock() {
        mActiveClock.setVisibility(View.GONE);
        if (!mClockVisible) {
            return;
        }

        mActiveClock = getClockForCurrentLocation();
        mActiveClock.setVisibility(View.VISIBLE);
        mActiveClock.setAmPmStyle(mAmPmStyle);
        mActiveClock.setShowSeconds(mShowSeconds);

        setClockAndDateStatus();
        setTextColor(mIconTint);
        updateFontSize();
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        Log.d(TAG, "onTuningChanged key=" + key + " value=" + newValue);

        if (CLOCK_POSITION.equals(key)) {
            mClockPosition = newValue == null ? CLOCK_POSITION_RIGHT : Integer.valueOf(newValue);
        } else if (CLOCK_STYLE.equals(key)) {
            mAmPmStyle = newValue == null ? AM_PM_STYLE_GONE : Integer.valueOf(newValue);
        } else if (CLOCK_SECONDS.equals(key)) {
            mShowSeconds = newValue != null && Integer.parseInt(newValue) != 0;
        }
        updateActiveClock();
    }

    private void setClockAndDateStatus() {
        if (mNotificationIconAreaController != null) {
            mNotificationIconAreaController.setClockAndDateStatus(mClockPosition);
        }
    }

    public void setVisibility(boolean visible) {
        if (mActiveClock != null) {
            mActiveClock.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        mClockVisible = visible;
    }

    public void setTextColor(int iconTint) {
        mIconTint = iconTint;
        if (mActiveClock != null) {
            mActiveClock.setTextColor(iconTint);
        }
    }

    public void setTextColor(Rect tintArea, int iconTint) {
        if (mActiveClock != null) {
            setTextColor(StatusBarIconController.getTint(tintArea, mActiveClock, iconTint));
        }
    }

    public void updateFontSize() {
        if (mActiveClock != null) {
            FontSizeUtils.updateFontSize(mActiveClock, R.dimen.status_bar_clock_size);
        }
    }

    public void setPaddingRelative(int start, int top, int end, int bottom) {
        if (mActiveClock != null) {
            mActiveClock.setPaddingRelative(start, top, end, bottom);
        }
    }
}
