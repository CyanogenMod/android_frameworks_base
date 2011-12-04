/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.internal.policy.impl;

import com.android.internal.R;
import com.android.internal.telephony.IccCard;
import com.android.internal.widget.DigitalClock;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.RotarySelector;
import com.android.internal.widget.SlidingTab;
import com.android.internal.widget.SlidingTab.OnTriggerListener;
import com.android.internal.widget.RingSelector;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.GestureOverlayView.OnGesturePerformedListener;
import android.gesture.Prediction;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.media.AudioManager;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Vibrator;
import android.preference.MultiSelectListPreference;
import android.provider.Settings;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import java.util.ArrayList;
import java.util.Date;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

/**
 * The screen within {@link LockPatternKeyguardView} that shows general
 * information about the device depending on its state, and how to get
 * past it, as applicable.
 */
class LockScreen extends LinearLayout implements KeyguardScreen, KeyguardUpdateMonitor.InfoCallback,
        KeyguardUpdateMonitor.SimStateCallback, SlidingTab.OnTriggerListener, RotarySelector.OnDialTriggerListener,
        RingSelector.OnRingTriggerListener, OnGesturePerformedListener{

    private static final boolean DBG = false;
    private static final String TAG = "LockScreen";
    private static final String ENABLE_MENU_KEY_FILE = "/data/local/enable_menu_key";
    private static final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");

    private Status mStatus = Status.Normal;

    private LockPatternUtils mLockPatternUtils;
    private KeyguardUpdateMonitor mUpdateMonitor;
    private KeyguardScreenCallback mCallback;

    private GestureOverlayView mGestureOverlay;
    private TextView mCarrier;
    private SlidingTab mTabSelector;
    private SlidingTab mSelector2;
    private RotarySelector mRotarySelector;
    private RingSelector mRingSelector;
    private DigitalClock mClock;
    private TextView mDate;
    private TextView mTime;
    private TextView mAmPm;
    private TextView mStatus1;
    private TextView mStatus2;
    private TextView mScreenLocked;
    private TextView mEmergencyCallText;
    private Button mEmergencyCallButton;
    private ImageButton mPlayIcon;
    private ImageButton mPauseIcon;
    private ImageButton mRewindIcon;
    private ImageButton mForwardIcon;
    private ImageButton mAlbumArt;
    private AudioManager am = (AudioManager)getContext().getSystemService(Context.AUDIO_SERVICE);
    private boolean mWasMusicActive = am.isMusicActive();
    private boolean mIsMusicActive = false;
    private GestureLibrary mLibrary;

    private TextView mCustomMsg;
    private TextView mNowPlaying;

    // current configuration state of keyboard and display
    private int mKeyboardHidden;
    private int mCreationOrientation;

    // are we showing battery information?
    private boolean mShowingBatteryInfo = false;

    // last known plugged in state
    private boolean mPluggedIn = false;

    // last known battery level
    private int mBatteryLevel = 100;

    private String mNextAlarm = null;
    private Drawable mAlarmIcon = null;
    private String mCharging = null;
    private Drawable mChargingIcon = null;

    private boolean mSilentMode;
    private AudioManager mAudioManager;
    private String mDateFormatString;
    private boolean mEnableMenuKeyInLockScreen;

    private boolean mTrackballUnlockScreen = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.TRACKBALL_UNLOCK_SCREEN, 0) == 1);

    private boolean mSliderUnlockScreen = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.SLIDER_UNLOCK_SCREEN, 0) == 1);

    private boolean mMenuUnlockScreen = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.MENU_UNLOCK_SCREEN, 0) == 1);

    private boolean mLockAlwaysBattery = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_ALWAYS_BATTERY, 0) == 1);

    private boolean mLockCalendarAlarm = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CALENDAR_ALARM, 0) == 1);

    private String[] mCalendars = MultiSelectListPreference.parseStoredValue(Settings.System.getString(
            mContext.getContentResolver(), Settings.System.LOCKSCREEN_CALENDARS));

    private boolean mLockCalendarRemindersOnly = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CALENDAR_REMINDERS_ONLY, 0) == 1);

    private long mLockCalendarLookahead = Settings.System.getLong(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CALENDAR_LOOKAHEAD, 10800000);

    private boolean mLockMusicControls = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_MUSIC_CONTROLS, 1) == 1);

    private boolean mNowPlayingToggle = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_NOW_PLAYING, 1) == 1);

    private boolean mAlbumArtToggle = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_ALBUM_ART, 1) == 1);

    private int mLockMusicHeadset = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_MUSIC_CONTROLS_HEADSET, 0));

    private boolean useLockMusicHeadsetWired = ((mLockMusicHeadset == 1) || (mLockMusicHeadset == 3));
    private boolean useLockMusicHeadsetBT = ((mLockMusicHeadset == 2) || (mLockMusicHeadset == 3));

    private boolean mLockAlwaysMusic = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_ALWAYS_MUSIC_CONTROLS, 0) == 1);

    private boolean mCustomAppToggle = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CUSTOM_APP_TOGGLE, 0) == 1);

    private String mCustomAppActivity = (Settings.System.getString(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CUSTOM_APP_ACTIVITY));

    private String[] mCustomRingAppActivities = new String[] {
            Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_CUSTOM_RING_APP_ACTIVITIES[0]),
            Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_CUSTOM_RING_APP_ACTIVITIES[1]),
            Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_CUSTOM_RING_APP_ACTIVITIES[2]),
            Settings.System.getString(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_CUSTOM_RING_APP_ACTIVITIES[3])
    };

    private int mLockscreenStyle = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_STYLE_PREF, 5));

    private int mCustomIconStyle = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_CUSTOM_ICON_STYLE, 1);

    private int mWidgetLayout = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_WIDGETS_LAYOUT, 0);

    private boolean mRotaryUnlockDown = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_ROTARY_UNLOCK_DOWN, 0) == 1);

    private boolean mRotaryHideArrows = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_ROTARY_HIDE_ARROWS, 0) == 1);

    private boolean mUseRotaryLockscreen = (mLockscreenStyle == 2);

    private boolean mUseRotaryRevLockscreen = (mLockscreenStyle == 3);

    private boolean mUseLenseSquareLockscreen = (mLockscreenStyle == 4);
    private boolean mLensePortrait = false;

    private boolean mUseRingLockscreen = (mLockscreenStyle == 5);

    private boolean mRingUnlockMiddle = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_RING_UNLOCK_MIDDLE, 0) == 1);

    private boolean mRingMinimal = (Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.LOCKSCREEN_RING_MINIMAL, 0) == 1);

    private double mGestureSensitivity;
    private boolean mGestureTrail;
    private boolean mGestureActive;
    private boolean mHideUnlockTab;
    private int mGestureColor;

    private Bitmap mCustomAppIcon;
    private String mCustomAppName;

    private Bitmap[] mCustomRingAppIcons = new Bitmap[4];

    /**
     * The status of this lock screen.
     */
    enum Status {
        /**
         * Normal case (sim card present, it's not locked)
         */
        Normal(true),

        /**
         * The sim card is 'network locked'.
         */
        NetworkLocked(true),

        /**
         * The sim card is missing.
         */
        SimMissing(false),

        /**
         * The sim card is missing, and this is the device isn't provisioned, so we don't let
         * them get past the screen.
         */
        SimMissingLocked(false),

        /**
         * The sim card is PUK locked, meaning they've entered the wrong sim unlock code too many
         * times.
         */
        SimPukLocked(false),

        /**
         * The sim card is locked.
         */
        SimLocked(true);

        private final boolean mShowStatusLines;

        Status(boolean mShowStatusLines) {
            this.mShowStatusLines = mShowStatusLines;
        }

        /**
         * @return Whether the status lines (battery level and / or next alarm) are shown while
         *         in this state.  Mostly dictated by whether this is room for them.
         */
        public boolean showStatusLines() {
            return mShowStatusLines;
        }
    }

    /**
     * In general, we enable unlocking the insecure key guard with the menu key. However, there are
     * some cases where we wish to disable it, notably when the menu button placement or technology
     * is prone to false positives.
     *
     * @return true if the menu key should be enabled
     */
    private boolean shouldEnableMenuKey() {
        final Resources res = getResources();
        final boolean configDisabled = res.getBoolean(R.bool.config_disableMenuKeyInLockScreen);
        final boolean isMonkey = SystemProperties.getBoolean("ro.monkey", false);
        final boolean fileOverride = (new File(ENABLE_MENU_KEY_FILE)).exists();
        return !configDisabled || isMonkey || fileOverride;
    }

    /**
     * @param context Used to setup the view.
     * @param configuration The current configuration. Used to use when selecting layout, etc.
     * @param lockPatternUtils Used to know the state of the lock pattern settings.
     * @param updateMonitor Used to register for updates on various keyguard related
     *    state, and query the initial state at setup.
     * @param callback Used to communicate back to the host keyguard view.
     */
    LockScreen(Context context, Configuration configuration, LockPatternUtils lockPatternUtils,
            KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
        super(context);
        mLockPatternUtils = lockPatternUtils;
        mUpdateMonitor = updateMonitor;
        mCallback = callback;

        mEnableMenuKeyInLockScreen = shouldEnableMenuKey();

        mCreationOrientation = configuration.orientation;

        mKeyboardHidden = configuration.hardKeyboardHidden;

        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** CREATING LOCK SCREEN", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + " res orient=" + context.getResources().getConfiguration().orientation);
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        if (DBG) Log.v(TAG, "Creation orientation = " + mCreationOrientation);
        if (mCreationOrientation != Configuration.ORIENTATION_LANDSCAPE) {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock, this, true);
        } else {
            inflater.inflate(R.layout.keyguard_screen_tab_unlock_land, this, true);
        }
        ViewGroup lockWallpaper = (ViewGroup) findViewById(R.id.root);
        setBackground(mContext,lockWallpaper);
        mCarrier = (TextView) findViewById(R.id.carrier);
        // Required for Marquee to work
        mCarrier.setSelected(true);
        mCarrier.setTextColor(0xffffffff);

        mClock = (DigitalClock) findViewById(R.id.time);
        mTime = (TextView) findViewById(R.id.timeDisplay);
        mAmPm = (TextView) findViewById(R.id.am_pm);
        mDate = (TextView) findViewById(R.id.date);
        mStatus1 = (TextView) findViewById(R.id.status1);
        mStatus2 = (TextView) findViewById(R.id.status2);

        mCustomMsg = (TextView) findViewById(R.id.customMsg);

        if (mCustomMsg != null) {
            if (mLockPatternUtils.isShowCustomMsg()) {
                mCustomMsg.setVisibility(View.VISIBLE);
                mCustomMsg.setText(mLockPatternUtils.getCustomMsg());
            } else {
                mCustomMsg.setVisibility(View.GONE);
            }
        }

        mPlayIcon = (ImageButton) findViewById(R.id.musicControlPlay);
        mPauseIcon = (ImageButton) findViewById(R.id.musicControlPause);
        mRewindIcon = (ImageButton) findViewById(R.id.musicControlPrevious);
        mForwardIcon = (ImageButton) findViewById(R.id.musicControlNext);
        mAlbumArt = (ImageButton) findViewById(R.id.albumArt);
        mNowPlaying = (TextView) findViewById(R.id.musicNowPlaying);
        mNowPlaying.setSelected(true); // set focus to TextView to allow scrolling
        mNowPlaying.setTextColor(0xffffffff);

        mScreenLocked = (TextView) findViewById(R.id.screenLocked);

        mRotarySelector = (RotarySelector) findViewById(R.id.rotary_selector);

        mTabSelector = (SlidingTab) findViewById(R.id.tab_selector);
        mTabSelector.setHoldAfterTrigger(true, false);
        mTabSelector.setLeftHintText(R.string.lockscreen_unlock_label);

        mRingSelector = (RingSelector) findViewById(R.id.ring_selector);

        if (mCustomAppActivity != null) {
            try {
                Intent i = Intent.parseUri(mCustomAppActivity, 0);
                mCustomAppName =  i.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
                PackageManager pm = context.getPackageManager();
                ActivityInfo ai = i.resolveActivityInfo(pm,PackageManager.GET_ACTIVITIES);
                if (ai != null) {
                    if (mCustomAppName == null) mCustomAppName = ai.loadLabel(pm).toString();

                    if (mCustomIconStyle != 1) {
                        Bitmap iconBmp = ((BitmapDrawable)ai.loadIcon(pm)).getBitmap();
                        Bitmap jogBmp = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_jog_dial_blank);

                        int jogWidth = jogBmp.getWidth();
                        float density = getResources().getDisplayMetrics().density;
                        float padding = 7f * density;
                        int iconWidth = jogWidth - Math.round(padding*2);
                        int sqSide = (int) (iconWidth / Math.sqrt(2));

                        mCustomAppIcon=Bitmap.createBitmap(jogBmp.getWidth(), jogBmp.getHeight(), Bitmap.Config.ARGB_8888);
                        Bitmap scaledIconBmp = Bitmap.createScaledBitmap(iconBmp, sqSide, sqSide, true);
                        int margin = (jogWidth - sqSide) / 2;

                        Canvas canvas=new Canvas(mCustomAppIcon);
                        canvas.drawBitmap(jogBmp, 0, 0, new Paint());
                        canvas.drawBitmap(scaledIconBmp, margin, margin, new Paint());
                    }
                }
            } catch (URISyntaxException e) {
            }
        }

        float density = getResources().getDisplayMetrics().density;
        int ringAppIconSize = context.getResources().getInteger(R.integer.config_ringSecIconSizeDIP);
        for (int q = 0; q < 4; q++) {
            if (mCustomRingAppActivities[q] != null) {
                mRingSelector.showSecRing(q);
                try {
                    Intent i = Intent.parseUri(mCustomRingAppActivities[q], 0);
                    PackageManager pm = context.getPackageManager();
                    ActivityInfo ai = i.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
                    if (ai != null) {
                        Bitmap iconBmp = ((BitmapDrawable) ai.loadIcon(pm)).getBitmap();
                        mCustomRingAppIcons[q] = Bitmap.createScaledBitmap(iconBmp,
                                (int) (density * ringAppIconSize), (int) (density * ringAppIconSize), true);
                        mRingSelector.setSecRingResources(q, mCustomRingAppIcons[q], R.drawable.jog_ring_secback_normal);
                    }
                } catch (URISyntaxException e) {
                }
            } else {
                mRingSelector.hideSecRing(q);
            }
        }

        if (mCustomAppIcon == null)
            mCustomAppIcon=BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_jog_dial_custom);

        mSelector2 = (SlidingTab) findViewById(R.id.tab_selector2);
        if (mSelector2 != null) {
            mSelector2.setHoldAfterTrigger(true, false);
            mSelector2.setLeftHintText(R.string.lockscreen_phone_label);
            mSelector2.setRightHintText(mCustomAppName);
        }

        mEmergencyCallText = (TextView) findViewById(R.id.emergencyCallText);
        mEmergencyCallButton = (Button) findViewById(R.id.emergencyCallButton);
        mEmergencyCallButton.setText(R.string.lockscreen_emergency_call);

        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
        mEmergencyCallButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.takeEmergencyCallAction();
            }
        });

        mPlayIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                refreshMusicStatus();
                if (!am.isMusicActive()) {
                    mPauseIcon.setVisibility(View.VISIBLE);
                    mPlayIcon.setVisibility(View.GONE);
                    mRewindIcon.setVisibility(View.VISIBLE);
                    mForwardIcon.setVisibility(View.VISIBLE);
                    sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                }
            }
        });

        mPauseIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                refreshMusicStatus();
                if (am.isMusicActive()) {
                    mPlayIcon.setVisibility(View.VISIBLE);
                    mPauseIcon.setVisibility(View.GONE);
                    mRewindIcon.setVisibility(View.GONE);
                    mForwardIcon.setVisibility(View.GONE);
                    sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
                }
            }
        });

        mRewindIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            }
        });

        mForwardIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mCallback.pokeWakelock();
                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_NEXT);
            }
        });

        mAlbumArt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent musicIntent = new Intent(Intent.ACTION_VIEW);
                musicIntent.setClassName("com.android.music","com.android.music.MediaPlaybackActivity");
                musicIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(musicIntent);
                mCallback.goToUnlockScreen();
            }
        });

        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        mUpdateMonitor.registerInfoCallback(this);
        mUpdateMonitor.registerSimStateCallback(this);

        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mSilentMode = isSilentMode();

        //Rotary setup
        if(!mRotaryUnlockDown){
            mRotarySelector.setLeftHandleResource(R.drawable.ic_jog_dial_unlock);
            mRotarySelector.setMidHandleResource(mCustomAppIcon);
        }else{
            mRotarySelector.setLeftHandleResource(mCustomAppIcon);
            mRotarySelector.setMidHandleResource(R.drawable.ic_jog_dial_unlock);
        }
        mRotarySelector.enableCustomAppDimple(mCustomAppToggle);
        mRotarySelector.setRevamped(mUseRotaryRevLockscreen);
        mRotarySelector.setLenseSquare(mUseRotaryRevLockscreen);
        if(mRotaryHideArrows)
            mRotarySelector.hideArrows(true);

        //hide most items when we are in potrait lense mode
        mLensePortrait=(mUseLenseSquareLockscreen && mCreationOrientation != Configuration.ORIENTATION_LANDSCAPE);
        if (mLensePortrait || mWidgetLayout == 1 )
            setLenseWidgetsVisibility(View.INVISIBLE);

        //Ring setup
        if (mRingMinimal) {
            mRingSelector.enableRingMinimal(mRingMinimal);
            //unlock with middle - left and right are hidden
            mRingSelector.setMiddleRingResources(
                R.drawable.ic_jog_dial_unlock,
                R.drawable.jog_tab_target_green,
                R.drawable.jog_ring_ring_green);
        }else if(mCustomAppToggle) {
            mRingSelector.enableMiddleRing(mCustomAppToggle);
            if(mRingUnlockMiddle) {
                mRingSelector.enableMiddlePrimary(mRingUnlockMiddle);
                mRingSelector.setLeftRingResources(
                    R.drawable.ic_jog_dial_custom,
                    R.drawable.jog_tab_target_green,
                    R.drawable.jog_ring_ring_green);
                //unlock with middle
                mRingSelector.setMiddleRingResources(
                    R.drawable.ic_jog_dial_unlock,
                    R.drawable.jog_tab_target_green,
                    R.drawable.jog_ring_ring_green);
                }else{
                //unlock on left
                mRingSelector.setLeftRingResources(
                    R.drawable.ic_jog_dial_unlock,
                    R.drawable.jog_tab_target_green,
                    R.drawable.jog_ring_ring_green);
                mRingSelector.setMiddleRingResources(
                    R.drawable.ic_jog_dial_custom,
                    R.drawable.jog_tab_target_green,
                    R.drawable.jog_ring_ring_green);
                }
        }else{
            //no middle ring
            mRingSelector.setLeftRingResources(
                R.drawable.ic_jog_dial_unlock,
                R.drawable.jog_tab_target_green,
                R.drawable.jog_ring_ring_green);
            mRingSelector.enableRingMinimal(false);
        }

        mTabSelector.setLeftTabResources(
                R.drawable.ic_jog_dial_unlock,
                R.drawable.jog_tab_target_green,
                R.drawable.jog_tab_bar_left_unlock,
                R.drawable.jog_tab_left_unlock);

        updateRightTabResources();

        mRotarySelector.setOnDialTriggerListener(this);
        mTabSelector.setOnTriggerListener(this);
        mRingSelector.setOnRingTriggerListener(this);

        if (mSelector2 != null) {
            mSelector2.setLeftTabResources(R.drawable.ic_jog_dial_answer,
                    R.drawable.jog_tab_target_green, R.drawable.jog_tab_bar_left_generic,
                    R.drawable.jog_tab_left_generic);

            mSelector2.setRightTabResources(mCustomAppIcon,
                    R.drawable.jog_tab_target_green,
                    R.drawable.jog_tab_bar_right_generic,
                    R.drawable.jog_tab_right_generic);

            mSelector2.setOnTriggerListener(new OnTriggerListener() {
                public void onTrigger(View v, int whichHandle) {
                    if (whichHandle == SlidingTab.OnTriggerListener.LEFT_HANDLE) {
                        Intent callIntent = new Intent(Intent.ACTION_DIAL);
                        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(callIntent);
                        mCallback.goToUnlockScreen();
                    } else if (whichHandle == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
                        if (mCustomAppActivity != null) {
                            runActivity(mCustomAppActivity);
                        }
                    }
                }

                @Override
                public void onGrabbedStateChange(View v, int grabbedState) {
                    mCallback.pokeWakelock();
                }
            });
        }
        mGestureActive = (Settings.System.getInt(context.getContentResolver(),
                Settings.System.LOCKSCREEN_GESTURES_ENABLED, 0) == 1);
        mGestureTrail = (Settings.System.getInt(context.getContentResolver(),
                Settings.System.LOCKSCREEN_GESTURES_TRAIL, 0) == 1);
        mGestureColor = Settings.System.getInt(context.getContentResolver(),
                Settings.System.LOCKSCREEN_GESTURES_COLOR, 0xFFFFFF00);
        boolean prefHideUnlockTab = (Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.LOCKSCREEN_GESTURES_DISABLE_UNLOCK, 0) == 1);
        if (!mGestureActive) {
            mGestureTrail = false;
        }

        mGestureOverlay = (GestureOverlayView) findViewById(R.id.gestures);
        mGestureOverlay.setGestureVisible(mGestureTrail);
        mGestureOverlay.setGestureColor(mGestureColor);
        boolean GestureCanUnlock = false;
        if (mGestureOverlay != null) {
            if (mGestureActive) {
                File mStoreFile = new File(Environment.getDataDirectory(),
                        "/misc/lockscreen_gestures");
                mGestureSensitivity = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.LOCKSCREEN_GESTURES_SENSITIVITY, 3);
                mLibrary = GestureLibraries.fromFile(mStoreFile);
                if (mLibrary.load()) {
                    mGestureOverlay.addOnGesturePerformedListener(this);
                    for (String name : mLibrary.getGestureEntries()) {
                        String[] payload = name.split("___", 2);
                        if ("UNLOCK".equals(payload[1])) {
                            GestureCanUnlock = true;
                            break;
                        }
                    }
                }
            }
        }
        // Safety check in case our preferences in CMParts for hiding the unlock
        // tab don't properly update system settings... ALWAYS provide a way to unlock the phone
        if (prefHideUnlockTab && (GestureCanUnlock || mTrackballUnlockScreen || mMenuUnlockScreen)) {
            mHideUnlockTab = true;
        } else {
            mHideUnlockTab = false;
        }

        resetStatusInfo(updateMonitor);
        centerWidgets();
    }

    private void centerWidgets() {
        if (mWidgetLayout == 2) {
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)
            mCarrier.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            mCarrier.setLayoutParams(layoutParams);
            mCarrier.setGravity(Gravity.CENTER_HORIZONTAL);
            layoutParams = (RelativeLayout.LayoutParams)mDate.getLayoutParams();
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, 1);
            mDate.setLayoutParams(layoutParams);
            layoutParams = (RelativeLayout.LayoutParams)mStatus1.getLayoutParams();
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, 1);
            layoutParams.leftMargin = 0;
            mStatus1.setLayoutParams(layoutParams);
            layoutParams = (RelativeLayout.LayoutParams)mStatus2.getLayoutParams();
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, 1);
            layoutParams.leftMargin = 0;
            mStatus2.setLayoutParams(layoutParams);
            layoutParams = (RelativeLayout.LayoutParams)mClock.getLayoutParams();
            layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, 1);
            mClock.setLayoutParams(layoutParams);
        }
    }

    static void setBackground(Context bcontext, ViewGroup layout){
        String mLockBack = Settings.System.getString(bcontext.getContentResolver(), Settings.System.LOCKSCREEN_BACKGROUND);
        if (mLockBack!=null){
            if (!mLockBack.isEmpty()){
                try {
                    layout.setBackgroundColor(Integer.parseInt(mLockBack));
                }catch(NumberFormatException e){
                }
            }else{
                String lockWallpaper = "";
                try {
                    lockWallpaper = bcontext.createPackageContext("com.cyanogenmod.cmparts", 0).getFilesDir()+"/lockwallpaper";
                } catch (NameNotFoundException e1) {
                }
                if (!lockWallpaper.isEmpty()){
                    Bitmap lockb = BitmapFactory.decodeFile(lockWallpaper);
                    layout.setBackgroundDrawable(new BitmapDrawable(lockb));
                }
            }
        }
    }

    private boolean isSilentMode() {
        return mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
    }

    private void updateRightTabResources() {
        boolean vibe = mSilentMode
            && (mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE);

        int iconId = mSilentMode ? (vibe ? R.drawable.ic_jog_dial_vibrate_on
                : R.drawable.ic_jog_dial_sound_off) : R.drawable.ic_jog_dial_sound_on;
        int targetId = mSilentMode ? R.drawable.jog_tab_target_yellow
                : R.drawable.jog_tab_target_gray;

        mRotarySelector.setRightHandleResource(iconId);

        mTabSelector.setRightTabResources(iconId, targetId,
                mSilentMode ? R.drawable.jog_tab_bar_right_sound_on
                            : R.drawable.jog_tab_bar_right_sound_off,
                mSilentMode ? R.drawable.jog_tab_right_sound_on
                            : R.drawable.jog_tab_right_sound_off);

        mRingSelector.setRightRingResources(iconId, targetId,
                mSilentMode ? R.drawable.jog_ring_ring_yellow
                        : R.drawable.jog_ring_ring_gray);
    }

    private void resetStatusInfo(KeyguardUpdateMonitor updateMonitor) {
        mShowingBatteryInfo = updateMonitor.shouldShowBatteryInfo();
        mPluggedIn = updateMonitor.isDevicePluggedIn();
        mBatteryLevel = updateMonitor.getBatteryLevel();
        mIsMusicActive = am.isMusicActive();

        mStatus = getCurrentStatus(updateMonitor.getSimState());
        updateLayout(mStatus);

        refreshBatteryStringAndIcon();
        refreshAlarmDisplay();
        refreshMusicStatus();
        refreshPlayingTitle();

        mDateFormatString = getContext().getString(R.string.full_wday_month_day_no_year);
        refreshTimeAndDateDisplay();
        updateStatusLines();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER && mTrackballUnlockScreen)
                || (keyCode == KeyEvent.KEYCODE_MENU && mMenuUnlockScreen)
                || (keyCode == KeyEvent.KEYCODE_MENU && mEnableMenuKeyInLockScreen)) {

            mCallback.goToUnlockScreen();
        }
        return false;
    }

    /** {@inheritDoc} */
    public void onTrigger(View v, int whichHandle) {
        if (whichHandle == SlidingTab.OnTriggerListener.LEFT_HANDLE) {
            mCallback.goToUnlockScreen();
        } else if (whichHandle == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
            toggleSilentMode();
            updateRightTabResources();
            mCallback.pokeWakelock();
        }
    }

    /** {@inheritDoc} */
    public void onDialTrigger(View v, int whichHandle) {
        boolean mUnlockTrigger=false;
        boolean mCustomAppTrigger=false;

        if(whichHandle == RotarySelector.OnDialTriggerListener.LEFT_HANDLE){
            if(mRotaryUnlockDown)
                mCustomAppTrigger=true;
            else
                mUnlockTrigger=true;
        }
        if(whichHandle == RotarySelector.OnDialTriggerListener.MID_HANDLE){
            if(mRotaryUnlockDown)
                mUnlockTrigger=true;
            else
                mCustomAppTrigger=true;
        }

        if (mUnlockTrigger) {
            mCallback.goToUnlockScreen();
        } else if (mCustomAppTrigger) {
            if (mCustomAppActivity != null) {
                runActivity(mCustomAppActivity);
            }
        } else if (whichHandle == RotarySelector.OnDialTriggerListener.RIGHT_HANDLE) {
            // toggle silent mode
            toggleSilentMode();
            updateRightTabResources();
            mCallback.pokeWakelock();
        }
    }

    /** {@inheritDoc} */
    public void onRingTrigger(View v, int whichRing, int whichApp) {
        boolean mUnlockTrigger=false;
        boolean mCustomAppTrigger=false;

        if (whichRing == RingSelector.OnRingTriggerListener.LEFT_RING) {
            if(mRingUnlockMiddle)
                mCustomAppTrigger=true;
            else
                mUnlockTrigger=true;
        }
        if (whichRing == RingSelector.OnRingTriggerListener.MIDDLE_RING) {
            if(mRingUnlockMiddle || mRingMinimal)
                mUnlockTrigger=true;
            else
                mCustomAppTrigger=true;
        }

        if (mUnlockTrigger) {
            mCallback.goToUnlockScreen();
        }else if (mCustomAppTrigger) {
            if (mCustomRingAppActivities[whichApp] != null) {
                runActivity(mCustomRingAppActivities[whichApp]);
            }
        }else if (whichRing == RingSelector.OnRingTriggerListener.RIGHT_RING) {
            toggleSilentMode();
            updateRightTabResources();
            mCallback.pokeWakelock();
        }
    }

    /** {@inheritDoc} */
    public void onGrabbedStateChange(View v, int grabbedState) {
        if (grabbedState == SlidingTab.OnTriggerListener.RIGHT_HANDLE) {
            mSilentMode = isSilentMode();
            mTabSelector.setRightHintText(mSilentMode ? R.string.lockscreen_sound_on_label
                    : R.string.lockscreen_sound_off_label);
        }

        // Don't poke the wake lock when returning to a state where the handle is
        // not grabbed since that can happen when the system (instead of the user)
        // cancels the grab.
        if (grabbedState != SlidingTab.OnTriggerListener.NO_HANDLE) {
            mGestureOverlay.cancelGesture();
            mCallback.pokeWakelock();
        } else if (mUseRingLockscreen && mCallback != null) {
            mCallback.pokeWakelock();
        }
    }

    /**
     * Displays a message in a text view and then restores the previous text.
     * @param textView The text view.
     * @param text The text.
     * @param color The color to apply to the text, or 0 if the existing color should be used.
     * @param iconResourceId The left hand icon.
     */
    private void toastMessage(final TextView textView, final String text, final int color, final int iconResourceId) {
        if (mPendingR1 != null) {
            textView.removeCallbacks(mPendingR1);
            mPendingR1 = null;
        }
        if (mPendingR2 != null) {
            mPendingR2.run(); // fire immediately, restoring non-toasted appearance
            textView.removeCallbacks(mPendingR2);
            mPendingR2 = null;
        }

        final String oldText = textView.getText().toString();
        final ColorStateList oldColors = textView.getTextColors();

        mPendingR1 = new Runnable() {
            public void run() {
                textView.setText(text);
                if (color != 0) {
                    textView.setTextColor(color);
                }
                textView.setCompoundDrawablesWithIntrinsicBounds(iconResourceId, 0, 0, 0);
            }
        };

        textView.postDelayed(mPendingR1, 0);
        mPendingR2 = new Runnable() {
            public void run() {
                textView.setText(oldText);
                textView.setTextColor(oldColors);
                textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
        };
        textView.postDelayed(mPendingR2, 3500);
    }
    private Runnable mPendingR1;
    private Runnable mPendingR2;

    private void refreshAlarmDisplay() {
        mNextAlarm = mLockPatternUtils.getNextAlarm();

        if (mNextAlarm != null) {
            mAlarmIcon = getContext().getResources().getDrawable(R.drawable.ic_lock_idle_alarm);
        } else if (mLockCalendarAlarm) {
            mNextAlarm = mLockPatternUtils.getNextCalendarAlarm(mLockCalendarLookahead,
                    mCalendars, mLockCalendarRemindersOnly);
            if (mNextAlarm != null) {
                mAlarmIcon = getContext().getResources().getDrawable(R.drawable.ic_lock_idle_calendar);
            }
        }

        updateStatusLines();
    }

    /** {@inheritDoc} */
    public void onRefreshBatteryInfo(boolean showBatteryInfo, boolean pluggedIn,
            int batteryLevel) {
        if (DBG) Log.d(TAG, "onRefreshBatteryInfo(" + showBatteryInfo + ", " + pluggedIn + ")");
        mShowingBatteryInfo = showBatteryInfo;
        mPluggedIn = pluggedIn;
        mBatteryLevel = batteryLevel;

        refreshBatteryStringAndIcon();
        updateStatusLines();
    }

    private void refreshBatteryStringAndIcon() {
        if (!mShowingBatteryInfo && !mLockAlwaysBattery || mLensePortrait) {
            mCharging = null;
            return;
        }

        if (mPluggedIn) {
            mChargingIcon =
                getContext().getResources().getDrawable(R.drawable.ic_lock_idle_charging);
            if (mUpdateMonitor.isDeviceCharged()) {
                mCharging = getContext().getString(R.string.lockscreen_charged);
            } else {
                mCharging = getContext().getString(R.string.lockscreen_plugged_in, mBatteryLevel);
            }
        } else {
            if (mBatteryLevel <= 20) {
                mChargingIcon =
                    getContext().getResources().getDrawable(R.drawable.ic_lock_idle_low_battery);
                mCharging = getContext().getString(R.string.lockscreen_low_battery, mBatteryLevel);
            } else {
                mChargingIcon =
                    getContext().getResources().getDrawable(R.drawable.ic_lock_idle_discharging);
                mCharging = getContext().getString(R.string.lockscreen_discharging, mBatteryLevel);
            }
        }
    }

    private void refreshMusicStatus() {
        if ((mWasMusicActive || mIsMusicActive || mLockAlwaysMusic
            || (mAudioManager.isWiredHeadsetOn() && useLockMusicHeadsetWired)
            || (mAudioManager.isBluetoothA2dpOn() && useLockMusicHeadsetBT)) && (mLockMusicControls)) {
            if (am.isMusicActive()) {
                mPauseIcon.setVisibility(View.VISIBLE);
                mPlayIcon.setVisibility(View.GONE);
                mRewindIcon.setVisibility(View.VISIBLE);
                mForwardIcon.setVisibility(View.VISIBLE);
            } else {
                mPlayIcon.setVisibility(View.VISIBLE);
                mPauseIcon.setVisibility(View.GONE);
                mRewindIcon.setVisibility(View.GONE);
                mForwardIcon.setVisibility(View.GONE);
            }
        } else {
            mPlayIcon.setVisibility(View.GONE);
            mPauseIcon.setVisibility(View.GONE);
            mRewindIcon.setVisibility(View.GONE);
            mForwardIcon.setVisibility(View.GONE);
        }
    }
    private void refreshPlayingTitle() {
        String nowPlaying = KeyguardViewMediator.NowPlaying();
        mNowPlaying.setText(nowPlaying);
        mNowPlaying.setVisibility(View.GONE);
        mAlbumArt.setVisibility(View.GONE);

        if (am.isMusicActive() && !nowPlaying.equals("") && mLockMusicControls) {
            if (mNowPlayingToggle) {
                mNowPlaying.setVisibility(View.VISIBLE);
                mNowPlaying.setSelected(true); // set focus to TextView to allow scrolling
            }
            // Set album art
            Uri uri = getArtworkUri(getContext(), KeyguardViewMediator.SongId(),
                    KeyguardViewMediator.AlbumId());
            if (uri != null && mAlbumArtToggle && !(mUseRingLockscreen && mCustomAppToggle && !mHideUnlockTab)) {
                mAlbumArt.setImageURI(uri);
                mAlbumArt.setVisibility(View.VISIBLE);
            }
        }
    }

    private void sendMediaButtonEvent(int code) {
        long eventtime = SystemClock.uptimeMillis();

        Intent downIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent downEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_DOWN, code, 0);
        downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent);
        getContext().sendOrderedBroadcast(downIntent, null);

        Intent upIntent = new Intent(Intent.ACTION_MEDIA_BUTTON, null);
        KeyEvent upEvent = new KeyEvent(eventtime, eventtime, KeyEvent.ACTION_UP, code, 0);
        upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent);
        getContext().sendOrderedBroadcast(upIntent, null);
    }

    /** {@inheritDoc} */
    public void onTimeChanged() {
        refreshTimeAndDateDisplay();
    }

    /** {@inheritDoc} */
    public void onMusicChanged() {
        refreshPlayingTitle();
    }

    private void refreshTimeAndDateDisplay() {
        mRotarySelector.invalidate();
        mDate.setText(DateFormat.format(mDateFormatString, new Date()));
    }

    private void updateStatusLines() {
        if (!mStatus.showStatusLines()
                || (mCharging == null && mNextAlarm == null) || mLensePortrait || mWidgetLayout == 1) {
            mStatus1.setVisibility(View.INVISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);
        } else if (mCharging != null && mNextAlarm == null) {
            // charging only
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);

            mStatus1.setText(mCharging);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mChargingIcon, null, null, null);
        } else if (mNextAlarm != null && mCharging == null) {
            // next alarm only
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.INVISIBLE);

            mStatus1.setText(mNextAlarm);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mAlarmIcon, null, null, null);
        } else if (mCharging != null && mNextAlarm != null) {
            // both charging and next alarm
            mStatus1.setVisibility(View.VISIBLE);
            mStatus2.setVisibility(View.VISIBLE);

            mStatus1.setText(mCharging);
            mStatus1.setCompoundDrawablesWithIntrinsicBounds(mChargingIcon, null, null, null);
            mStatus2.setText(mNextAlarm);
            mStatus2.setCompoundDrawablesWithIntrinsicBounds(mAlarmIcon, null, null, null);
        }
    }

    /** {@inheritDoc} */
    public void onRefreshCarrierInfo(CharSequence plmn, CharSequence spn) {
        if (DBG) Log.d(TAG, "onRefreshCarrierInfo(" + plmn + ", " + spn + ")");
        updateLayout(mStatus);
    }

    /**
     * Determine the current status of the lock screen given the sim state and other stuff.
     */
    private Status getCurrentStatus(IccCard.State simState) {
        boolean missingAndNotProvisioned = (!mUpdateMonitor.isDeviceProvisioned()
                && simState == IccCard.State.ABSENT);
        if (missingAndNotProvisioned) {
            return Status.SimMissingLocked;
        }

        switch (simState) {
            case ABSENT:
                return Status.SimMissing;
            case NETWORK_LOCKED:
                return Status.SimMissingLocked;
            case NOT_READY:
                return Status.SimMissing;
            case PIN_REQUIRED:
                return Status.SimLocked;
            case PUK_REQUIRED:
                return Status.SimPukLocked;
            case READY:
                return Status.Normal;
            case UNKNOWN:
                return Status.SimMissing;
        }
        return Status.SimMissing;
    }

    /**
     * Update the layout to match the current status.
     */
    private void updateLayout(Status status) {
        // The emergency call button no longer appears on this screen.
        if (DBG) Log.d(TAG, "updateLayout: status=" + status);

        mCustomAppToggle = (Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.LOCKSCREEN_CUSTOM_APP_TOGGLE, 0) == 1);
        mRingMinimal = (Settings.System.getInt(mContext.getContentResolver(),
                                Settings.System.LOCKSCREEN_RING_MINIMAL, 0) == 1);
        if (mCustomAppToggle) {
            mRingSelector.enableMiddleRing(mCustomAppToggle);
            mRotarySelector.enableCustomAppDimple(mCustomAppToggle);
        }
        if (mRingMinimal) {
            mRingSelector.enableRingMinimal(mRingMinimal);
        }

        mEmergencyCallButton.setVisibility(View.GONE); // in almost all cases

        switch (status) {
            case Normal:
                // text
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                mUpdateMonitor.getTelephonySpn()));

                // Empty now, but used for sliding tab feedback
                mScreenLocked.setText("");

                // layout
                setUnlockWidgetsState(true);
                mScreenLocked.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.GONE);
                break;
            case NetworkLocked:
                // The carrier string shows both sim card status (i.e. No Sim Card) and
                // carrier's name and/or "Emergency Calls Only" status
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                getContext().getText(R.string.lockscreen_network_locked_message)));
                mScreenLocked.setText(R.string.lockscreen_instructions_when_pattern_disabled);

                // layout
                setUnlockWidgetsState(true);
                mScreenLocked.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.GONE);
                break;
            case SimMissing:
                // text
                mCarrier.setText(R.string.lockscreen_missing_sim_message_short);
                mScreenLocked.setText(R.string.lockscreen_missing_sim_instructions);

                // layout
                setUnlockWidgetsState(true);
                mScreenLocked.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.VISIBLE);
                // do not need to show the e-call button; user may unlock
                break;
            case SimMissingLocked:
                // text
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                getContext().getText(R.string.lockscreen_missing_sim_message_short)));
                mScreenLocked.setText(R.string.lockscreen_missing_sim_instructions);

                // layout
                setUnlockWidgetsState(false);
                mScreenLocked.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                break;
            case SimLocked:
                // text
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                getContext().getText(R.string.lockscreen_sim_locked_message)));

                // layout
                setUnlockWidgetsState(true);
                mScreenLocked.setVisibility(View.INVISIBLE);
                mEmergencyCallText.setVisibility(View.GONE);
                break;
            case SimPukLocked:
                // text
                mCarrier.setText(
                        getCarrierString(
                                mUpdateMonitor.getTelephonyPlmn(),
                                getContext().getText(R.string.lockscreen_sim_puk_locked_message)));
                mScreenLocked.setText(R.string.lockscreen_sim_puk_locked_instructions);

                // layout
                setUnlockWidgetsState(false);
                mScreenLocked.setVisibility(View.VISIBLE);
                mEmergencyCallText.setVisibility(View.VISIBLE);
                mEmergencyCallButton.setVisibility(View.VISIBLE);
                break;
        }
        if (mHideUnlockTab) {
            setUnlockWidgetsState(false);
        }
    }

    private void setUnlockWidgetsState(boolean show) {
        if (show) {
            if (mUseRotaryLockscreen || mUseRotaryRevLockscreen || mUseLenseSquareLockscreen) {
                mRotarySelector.setVisibility(View.VISIBLE);
                mRotarySelector.setRevamped(mUseRotaryRevLockscreen);
                mRotarySelector.setLenseSquare(mUseLenseSquareLockscreen);
                mTabSelector.setVisibility(View.GONE);
                mRingSelector.setVisibility(View.GONE);
                if (mSelector2 != null) {
                    mSelector2.setVisibility(View.GONE);
                }
            } else if (mUseRingLockscreen) {
                mRingSelector.setVisibility(View.VISIBLE);
                mRotarySelector.setVisibility(View.GONE);
                mTabSelector.setVisibility(View.GONE);
                if (mSelector2 != null) {
                    mSelector2.setVisibility(View.GONE);
                }
            } else {
                mRotarySelector.setVisibility(View.GONE);
                mRingSelector.setVisibility(View.GONE);
                mTabSelector.setVisibility(View.VISIBLE);
                if (mSelector2 != null) {
                    if (mCustomAppToggle) {
                        mSelector2.setVisibility(View.VISIBLE);
                    } else {
                        mSelector2.setVisibility(View.GONE);
                    }
                }
            }
        } else {
            mRotarySelector.setVisibility(View.GONE);
            mRingSelector.setVisibility(View.GONE);
            mTabSelector.setVisibility(View.GONE);
            if (mSelector2 != null) {
                mSelector2.setVisibility(View.GONE);
            }
        }
    }

    static CharSequence getCarrierString(CharSequence telephonyPlmn, CharSequence telephonySpn) {
        if (telephonyPlmn != null && (telephonySpn == null || "".contentEquals(telephonySpn))) {
            return telephonyPlmn;
        } else if (telephonySpn != null && (telephonyPlmn == null || "".contentEquals(telephonyPlmn))) {
            return telephonySpn;
        } else if (telephonyPlmn != null && telephonySpn != null) {
            return telephonyPlmn + "|" + telephonySpn;
        } else {
            return "";
        }
    }

    public void onSimStateChanged(IccCard.State simState) {
        if (DBG) Log.d(TAG, "onSimStateChanged(" + simState + ")");
        mStatus = getCurrentStatus(simState);
        updateLayout(mStatus);
        updateStatusLines();
    }

    void updateConfiguration() {
        Configuration newConfig = getResources().getConfiguration();
        if (newConfig.hardKeyboardHidden != mKeyboardHidden) {
            mKeyboardHidden = newConfig.hardKeyboardHidden;
            final boolean isKeyboardOpen = mKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO;
            if (mSliderUnlockScreen && isKeyboardOpen) {
                mCallback.goToUnlockScreen();
                return;
            }
        }
        if (newConfig.orientation != mCreationOrientation) {
            mCallback.recreateMe(newConfig);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.v(TAG, "***** LOCK ATTACHED TO WINDOW");
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + getResources().getConfiguration());
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (LockPatternKeyguardView.DEBUG_CONFIGURATION) {
            Log.w(TAG, "***** LOCK CONFIG CHANGING", new RuntimeException());
            Log.v(TAG, "Cur orient=" + mCreationOrientation
                    + ", new config=" + newConfig);
        }
        updateConfiguration();
    }

    /** {@inheritDoc} */
    public boolean needsInput() {
        return false;
    }

    /** {@inheritDoc} */
    public void onPause() {
        if (mUseRingLockscreen) {
            mRingSelector.setVisibility(View.VISIBLE);
        }
    }

    /** {@inheritDoc} */
    public void onResume() {
        resetStatusInfo(mUpdateMonitor);
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
    }

    /** {@inheritDoc} */
    public void cleanUp() {
        mUpdateMonitor.removeCallback(this); // this must be first
        mLockPatternUtils = null;
        mUpdateMonitor = null;
        mCallback = null;
    }

    /** {@inheritDoc} */
    public void onRingerModeChanged(int state) {
        boolean silent = AudioManager.RINGER_MODE_NORMAL != state;
        if (silent != mSilentMode) {
            mSilentMode = silent;
            updateRightTabResources();
        }
    }

    public void onPhoneStateChanged(String newState) {
        mLockPatternUtils.updateEmergencyCallButtonState(mEmergencyCallButton);
    }

    private void toggleSilentMode() {
        // tri state silent<->vibrate<->ring if silent mode is enabled, otherwise toggle silent mode
        final boolean mVolumeControlSilent = Settings.System.getInt(mContext.getContentResolver(),
            Settings.System.VOLUME_CONTROL_SILENT, 0) != 0;
        mSilentMode = mVolumeControlSilent
            ? ((mAudioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) || !mSilentMode)
            : !mSilentMode;
        if (mSilentMode) {
            final boolean vibe = mVolumeControlSilent
            ? (mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE)
            : (Settings.System.getInt(
                getContext().getContentResolver(),
                Settings.System.VIBRATE_IN_SILENT, 1) == 1);

            mAudioManager.setRingerMode(vibe
                ? AudioManager.RINGER_MODE_VIBRATE
                : AudioManager.RINGER_MODE_SILENT);
        } else {
            mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }

        String message = mSilentMode ?
                getContext().getString(R.string.global_action_silent_mode_on_status) :
                getContext().getString(R.string.global_action_silent_mode_off_status);

        final int toastIcon = mSilentMode
            ? R.drawable.ic_lock_ringer_off
            : R.drawable.ic_lock_ringer_on;

        final int toastColor = mSilentMode
            ? getContext().getResources().getColor(R.color.keyguard_text_color_soundoff)
            : getContext().getResources().getColor(R.color.keyguard_text_color_soundon);
        toastMessage(mScreenLocked, message, toastColor, toastIcon);
    }

    @Override
    public void onGesturePerformed(GestureOverlayView overlay, Gesture gesture) {
        ArrayList<Prediction> predictions = mLibrary.recognize(gesture);
        if (predictions.size() > 0 && predictions.get(0).score > mGestureSensitivity) {
            String[] payload = predictions.get(0).name.split("___", 3);
            String uri = payload[1];
            if (uri != null) {
                if ("UNLOCK".equals(uri)) {
                    mCallback.goToUnlockScreen();
                } else if ("SOUND".equals(uri)) {
                    toggleSilentMode();
                    mCallback.pokeWakelock();
                } else if ("FLASHLIGHT".equals(uri)) {
                    mContext.sendBroadcast(new Intent("net.cactii.flash2.TOGGLE_FLASHLIGHT"));
                    mCallback.pokeWakelock();
                } else if ("NEXT".equals(uri)) {
                    mForwardIcon.performClick();
                } else if ("PREVIOUS".equals(uri)) {
                    mRewindIcon.performClick();
                } else if ("PLAYPAUSE".equals(uri)) {
                    if (mPauseIcon.getVisibility() == View.VISIBLE) {
                        mPauseIcon.performClick();
                    } else {
                        mPlayIcon.performClick();
                    }
                } else
                    try {
                        Intent i = Intent.parseUri(uri, 0);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                        mContext.startActivity(i);
                        // Run in background if requested
                        if (payload.length > 2) {
                            // Define vibrator
                            Vibrator v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                            // Vibrate pattern when gesture is correct
                            long[] pattern = {
                                0, 200
                            };
                            v.vibrate(pattern, -1);

                            mCallback.pokeWakelock();
                        } else {
                            mCallback.goToUnlockScreen();
                        }
                    } catch (URISyntaxException e) {
                    } catch (ActivityNotFoundException e) {
                    }
            }
        } else {
            mCallback.pokeWakelock(); // reset timeout - give them another chance to gesture
        }
    }

    // shameless kang of music widgets
    public static Uri getArtworkUri(Context context, long song_id, long album_id) {

        if (album_id < 0) {
            // This is something that is not in the database, so get the album art directly
            // from the file.
            if (song_id >= 0) {
                return getArtworkUriFromFile(context, song_id, -1);
            }
            return null;
        }

        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
        if (uri != null) {
            InputStream in = null;
            try {
                in = res.openInputStream(uri);
                return uri;
            } catch (FileNotFoundException ex) {
                // The album art thumbnail does not actually exist. Maybe the user deleted it, or
                // maybe it never existed to begin with.
                return getArtworkUriFromFile(context, song_id, album_id);
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                }
            }
        }
        return null;
    }

    private static Uri getArtworkUriFromFile(Context context, long songid, long albumid) {

        if (albumid < 0 && songid < 0) {
            return null;
        }

        try {
            if (albumid < 0) {
                Uri uri = Uri.parse("content://media/external/audio/media/" + songid + "/albumart");
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    return uri;
                }
            } else {
                Uri uri = ContentUris.withAppendedId(sArtworkUri, albumid);
                ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r");
                if (pfd != null) {
                    return uri;
                }
            }
        } catch (FileNotFoundException ex) {
            //
        }
        return null;
    }

    /*
     * enables or disables visibility of most lockscreen widgets
     * depending on lense status
     */
    private void setLenseWidgetsVisibility(int visibility){
        mClock.setVisibility(visibility);
        mDate.setVisibility(visibility);
        mTime.setVisibility(visibility);
        mAmPm.setVisibility(visibility);
        mCarrier.setVisibility(visibility);
        mNowPlaying.setVisibility(visibility);
        mAlbumArt.setVisibility(visibility);

        if (DateFormat.is24HourFormat(mContext))
            mAmPm.setVisibility(View.INVISIBLE);

        mNowPlayingToggle = false;
        mAlbumArtToggle = false;
        if (visibility == View.VISIBLE
                && (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.LOCKSCREEN_NOW_PLAYING, 1) == 1))
            mNowPlayingToggle = true;
        if (visibility == View.VISIBLE
                && (Settings.System.getInt(mContext.getContentResolver(),
                        Settings.System.LOCKSCREEN_ALBUM_ART, 1) == 1))
            mAlbumArtToggle = true;
    }

    private void runActivity(String uri) {
        try {
            Intent i = Intent.parseUri(uri, 0);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            mContext.startActivity(i);
            mCallback.goToUnlockScreen();
        } catch (URISyntaxException e) {
        } catch (ActivityNotFoundException e) {
        }
    }
}
