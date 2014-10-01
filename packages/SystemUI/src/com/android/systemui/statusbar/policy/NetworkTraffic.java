package com.android.systemui.statusbar.policy;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PorterDuff.Mode;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.R;

import java.text.DecimalFormat;

/*
 * Seeing how an Integer object in java requires at least 16 Bytes, it seemed awfully wasteful
 * to only use it for a single boolean. 32-bits is plenty of room for what we need it to do.
 */
public class NetworkTraffic extends TextView {
    public static final int MASK_UP     = 0x00000001;      // Least valuable bit
    public static final int MASK_DOWN   = 0x00000002;      // Second least valuable bit
    public static final int MASK_UNIT   = 0x00000004;      // Third least valuable bit
    public static final int MASK_PERIOD = 0xFFFF0000;    // Most valuable 16 bits

    private static final int KILOBIT  = 1000;
    private static final int KILOBYTE = 1024;

    private static final DecimalFormat decimalFormat = new DecimalFormat("##0.#");
    static {
        decimalFormat.setMaximumIntegerDigits(3);
        decimalFormat.setMaximumFractionDigits(1);
    }

    private final ConnectivityManager connectivityManager;

    private BroadcastReceiver mIntentReceiver;

    private int mState = 0;
    private boolean mAttached;
    private boolean mEnabled;
    private boolean mWasConnectionAvailable;

    private long totalRxBytes;
    private long totalTxBytes;
    private long lastUpdateTime;
    private int  txtSizeSingle;
    private int  txtSizeMulti;

    private int KB = KILOBIT;
    private int MB = KB * KB;
    private int GB = MB * KB;
    private boolean mAutoHide;
    private int mAutoHideThreshold;

    private final Handler mTrafficHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            long timeDelta = SystemClock.elapsedRealtime() - lastUpdateTime;

            if (timeDelta < getInterval(mState) * .95) {
                if (msg.what != 1) {
                    // we just updated the view, nothing further to do
                    return;
                }
                if (timeDelta < 1) {
                    // Can't div by 0 so make sure the value displayed is minimal
                    timeDelta = Long.MAX_VALUE;
                }
            }
            lastUpdateTime = SystemClock.elapsedRealtime();

            // Calculate the data rate from the change in total bytes and time
            final long newTotalRxBytes = TrafficStats.getTotalRxBytes();
            final long newTotalTxBytes = TrafficStats.getTotalTxBytes();
            long rxData = newTotalRxBytes - totalRxBytes;
            long txData = newTotalTxBytes - totalTxBytes;

            if (shouldHide(rxData, txData, timeDelta)) {
                setText("");
                setVisibility(View.GONE);
            } else if (!getConnectAvailable()) {
                clearHandlerCallbacks();
                setVisibility(View.GONE);
            } else {
                // If bit/s convert from Bytes to bits
                final String symbol;
                if (KB == KILOBYTE) {
                    symbol = "B/s";
                } else {
                    symbol = "b/s";
                    rxData = rxData * 8;
                    txData = txData * 8;
                }

                // Get information for uplink ready so the line return can be added
                String output = "";
                if (isSet(mState, MASK_UP)) {
                    output = formatOutput(timeDelta, txData, symbol);
                }

                // Ensure text size is where it needs to be
                int textSize;
                if (isSet(mState, MASK_UP + MASK_DOWN)) {
                    output += "\n";
                    textSize = txtSizeMulti;
                } else {
                    textSize = txtSizeSingle;
                }

                // Add information for downlink if it's called for
                if (isSet(mState, MASK_DOWN)) {
                    output += formatOutput(timeDelta, rxData, symbol);
                }

                // Update view if there's anything new to show
                if (!output.contentEquals(getText())) {
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) textSize);
                    setText(output);
                }
                setVisibility(View.VISIBLE);
            }

            // Post delayed message to refresh in ~1000ms
            totalRxBytes = newTotalRxBytes;
            totalTxBytes = newTotalTxBytes;
            clearHandlerCallbacks();
            mTrafficHandler.postDelayed(mRunnable, getInterval(mState));
        }

        private String formatOutput(long timeDelta, long data, String symbol) {
            final long speed = (long) (data / (timeDelta / 1000F));
            if (speed < KB) {
                return decimalFormat.format(speed) + symbol;
            } else if (speed < MB) {
                return decimalFormat.format(speed / (float) KB) + 'k' + symbol;
            } else if (speed < GB) {
                return decimalFormat.format(speed / (float) MB) + 'M' + symbol;
            }
            return decimalFormat.format(speed / (float) GB) + 'G' + symbol;
        }

        private boolean shouldHide(long rxData, long txData, long timeDelta) {
            long speedTxKB = (long)(txData / (timeDelta / 1000f)) / KILOBYTE;
            long speedRxKB = (long)(rxData / (timeDelta / 1000f)) / KILOBYTE;
            int mState = 2;
                return mAutoHide &&
                   (mState == MASK_DOWN && speedRxKB <= mAutoHideThreshold ||
                    mState == MASK_UP && speedTxKB <= mAutoHideThreshold ||
                    mState == MASK_UP + MASK_DOWN &&
                       speedRxKB <= mAutoHideThreshold &&
                       speedTxKB <= mAutoHideThreshold);
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    private class SettingsObserver extends ContentObserver {
        public SettingsObserver(Handler handler) {
            super(handler);
        }

        public void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            final Uri uri = Settings.System.getUriFor(Settings.System.NETWORK_TRAFFIC_STATE);
            resolver.registerContentObserver(uri, false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_AUTOHIDE), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_COLOR), false,
                    this, UserHandle.USER_ALL);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NETWORK_TRAFFIC_ICON_COLOR), false,
                    this, UserHandle.USER_ALL);

            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public NetworkTraffic(Context context) {
        this(context, null);
    }

    public NetworkTraffic(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkTraffic(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        final Resources resources = getResources();
        txtSizeSingle = resources.getDimensionPixelSize(R.dimen.net_traffic_single_text_size);
        txtSizeMulti = resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size);

        final Handler mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            registerReceivers();
        }
        updateSettings();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            unregisterReceivers();
            mAttached = false;
        }
    }

    private void registerReceivers() {
        if (mIntentReceiver == null) {
            mIntentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    final String action = intent.getAction();
                    if (TextUtils.equals(action, ConnectivityManager.CONNECTIVITY_ACTION)) {
                        // update the indicator
                        update();
                    }
                }
            };
            final IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            mContext.registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    private void unregisterReceivers() {
        if (mIntentReceiver != null) {
            try {
                mContext.unregisterReceiver(mIntentReceiver);
            } catch (Exception ignored) { }
            mIntentReceiver = null;
        }
    }

    private boolean getConnectAvailable() {
        final NetworkInfo network = (connectivityManager != null)
                ? connectivityManager.getActiveNetworkInfo() : null;
        return network != null && network.isConnected();
    }

    private void update() {
        // if the indicator is not enabled, skip updating it
        if (!mEnabled) return;

        // check if we have connectivity
        if (getConnectAvailable()) {
            // if we are attached, let the handler know
            if (mAttached) {
                totalRxBytes = TrafficStats.getTotalRxBytes();
                lastUpdateTime = SystemClock.elapsedRealtime();
                mTrafficHandler.sendEmptyMessage(1);
            }
            // if we did not have connectivity before, the indicator was hidden, therefore show it
            if (!mWasConnectionAvailable) {
                setVisibility(View.VISIBLE);
                updateTrafficDrawable();
            }
            mWasConnectionAvailable = true;
        } else {
            // else check if there was a connectivity before and if, hide the view
            if (mWasConnectionAvailable) {
                setVisibility(View.GONE);
            }
            mWasConnectionAvailable = false;
        }
    }

    private void updateSettings() {
        final ContentResolver resolver = mContext.getContentResolver();

        mAutoHide = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE, 0,
                UserHandle.USER_CURRENT) == 1;

        mAutoHideThreshold = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_AUTOHIDE_THRESHOLD, 10,
                UserHandle.USER_CURRENT);

        mState = Settings.System.getIntForUser(resolver,
                Settings.System.NETWORK_TRAFFIC_STATE, 0,
                UserHandle.USER_CURRENT);

	    int defaultColor = Settings.System.getInt(resolver,
                Settings.System.NETWORK_TRAFFIC_COLOR, 0xFFFFFFFF);

	    int mNetworkTrafficColor = Settings.System.getInt(resolver,
                Settings.System.NETWORK_TRAFFIC_COLOR, -2);

	    int mNetworkTrafficIconColor = Settings.System.getInt(resolver,
                Settings.System.NETWORK_TRAFFIC_ICON_COLOR, -2);

	    if (mNetworkTrafficColor == Integer.MIN_VALUE
                || mNetworkTrafficColor == -2) {
            mNetworkTrafficColor = defaultColor;
        }

	    setTextColor(mNetworkTrafficColor);

        // reset the was connection available state
        mWasConnectionAvailable = false;

        if (isSet(mState, MASK_UNIT)) {
            KB = KILOBYTE;
        } else {
            KB = KILOBIT;
        }

        MB = KB * KB;
        GB = MB * KB;

        if (isSet(mState, MASK_UP) || isSet(mState, MASK_DOWN)) {
            mEnabled = true;
        } else {
            mEnabled = false;
            clearHandlerCallbacks();
        }

        // if the indicator is enabled, update the view, else hide it
        if (mEnabled) {
            registerReceivers();
            update();
        } else {
            unregisterReceivers();
            setVisibility(View.GONE);
        }
    }

    private static boolean isSet(int intState, int intMask) {
        return (intState & intMask) == intMask;
    }

    private static int getInterval(int intState) {
        int intInterval = intState >>> 16;
        return (intInterval >= 250 && intInterval <= 32750) ? intInterval : 1000;
    }

    private void clearHandlerCallbacks() {
        mTrafficHandler.removeCallbacks(mRunnable);
        mTrafficHandler.removeMessages(0);
        mTrafficHandler.removeMessages(1);
    }

    private void updateTrafficDrawable() {
        final int intTrafficDrawable;
        final int iconcolor = Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.NETWORK_TRAFFIC_ICON_COLOR, -2);
        if (isSet(mState, MASK_UP + MASK_DOWN)) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_updown;
            mContext.getResources().getDrawable(R.drawable.stat_sys_network_traffic_updown).setColorFilter(iconcolor , Mode.MULTIPLY);
        } else if (isSet(mState, MASK_UP)) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_up;
            mContext.getResources().getDrawable(R.drawable.stat_sys_network_traffic_up).setColorFilter(iconcolor , Mode.MULTIPLY);
        } else if (isSet(mState, MASK_DOWN)) {
            intTrafficDrawable = R.drawable.stat_sys_network_traffic_down;
            mContext.getResources().getDrawable(R.drawable.stat_sys_network_traffic_down).setColorFilter(iconcolor , Mode.MULTIPLY);
        } else {
            intTrafficDrawable = 0;
        }
        setCompoundDrawablesWithIntrinsicBounds(0, 0, intTrafficDrawable, 0);
    }
}
