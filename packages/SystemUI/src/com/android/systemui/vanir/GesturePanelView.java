/*
 * Copyright (C) 2014 VanirAOSP && the Android Open Source Project
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

package com.android.systemui.vanir;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

import android.app.ActivityManagerNative;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.view.SoundEffectConstants;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.internal.statusbar.IStatusBarService;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_HOME;
import static android.view.KeyEvent.KEYCODE_APP_SWITCH;

public class GesturePanelView extends FrameLayout implements GestureOverlayView.OnGestureListener {
    public static final String TAG = "GesturePanelView";
    private final File mStoreFile = new File("/sdcard", "gpv_gestures");

    private GestureOverlayView mGestureView;
    private BaseStatusBar mBar;

    State mState;
    View mContent;
    GestureLibrary mStore;
    TranslateAnimation mSlideIn;
    TranslateAnimation mSlideOut;

    public static enum State {Expanded, Opening, Closing}

    public GesturePanelView(Context context) {
        this(context, null);
    }

    public GesturePanelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GesturePanelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mStore = GestureLibraries.fromFile(mStoreFile);
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
    }

    // on-screen buttons
    OnClickListener mCancelButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mState != State.Opening) {
                switchToState(State.Closing);
            }
        }
    };

    OnClickListener mAddGestureButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setComponent(ComponentName.unflattenFromString(
                    "com.android.settings/com.android.settings.vanir.gesturepanel.GestureBuilderActivity"));
            intent.addCategory("android.intent.category.LAUNCHER");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            switchToState(State.Closing);
        }
    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mContent = findViewById(R.id.content);
        mGestureView = (GestureOverlayView) findViewById(R.id.gesture_overlay);
        mGestureView.setGestureVisible(true);
        mGestureView.addOnGestureListener(this);
        findViewById(R.id.cancel_gesturing).setOnClickListener(mCancelButtonListener);
        TextView addButton = (TextView) findViewById(R.id.add_gesture);
        addButton.setTextSize(getResources().getDimension(R.dimen.gesture_panel_text_size));
        addButton.setOnClickListener(mAddGestureButtonListener);

        try {
            IStatusBarService.Stub.asInterface(
                ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).collapsePanels();
        } catch (RemoteException e) {
            // EL CHUPACABRA!!
        }

        createAnimations();
        invalidate();

        if (mStore != null) {
            mStore.load();
        }
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getAction() == ACTION_DOWN) {
            if ((event.getKeyCode() == KEYCODE_BACK) ||
                (event.getKeyCode() == KEYCODE_HOME) ||
                (event.getKeyCode() == KEYCODE_APP_SWITCH)) {
                switchToState(State.Closing);
            }
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }

    protected void switchToState(State state) {
        switch (state) {
            case Expanded:
                mGestureView.setVisibility(View.VISIBLE);
                break;
            case Opening:
                mContent.setVisibility(View.VISIBLE);
                mContent.startAnimation(mSlideIn);
                break;
            case Closing:
                mContent.startAnimation(mSlideOut);
                break;
        }
        mState = state;
    }

    // Public methods for PSB & BSB
    public void switchToClosingState() {
        switchToState(State.Closing);
    }

    public void switchToOpenState() {
        switchToState(State.Opening);
    }

    public boolean isGesturePanelAttached() {
        return mState != State.Closing;
    }

    // Animations
    private Animation.AnimationListener mAnimListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            animation.cancel();
            mContent.clearAnimation();
            mGestureView.clearAnimation();
            switch (mState) {
                case Closing:
                    mBar.removeGesturePanelView();
                    break;
                case Opening:
                    switchToState(State.Expanded);
                    break;
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    private void createAnimations() {
        mSlideIn = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

        mSlideOut = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f);
        mSlideIn.setDuration(100);
        mSlideIn.setInterpolator(new DecelerateInterpolator());
        mSlideIn.setFillAfter(true);
        mSlideIn.setAnimationListener(mAnimListener);
        mSlideOut.setDuration(175);
        mSlideOut.setInterpolator(new AccelerateInterpolator());
        mSlideOut.setFillAfter(true);
        mSlideOut.setAnimationListener(mAnimListener);
    }

    // Gesture handling
    @Override
    public void onGesture(GestureOverlayView overlay, MotionEvent event) {
    }

    @Override
    public void onGestureCancelled(GestureOverlayView overlay, MotionEvent event) {
    }

    @Override
    public void onGestureEnded(GestureOverlayView overlay, MotionEvent event) {
        Gesture gesture = overlay.getGesture();
        List<Prediction> predictions = mStore.recognize(gesture);
        for (Prediction prediction : predictions) {
            if (prediction.score >= 2.0) {
                String uri = prediction.name.substring(prediction.name.indexOf('|') + 1);
                try {
                    Intent intent = Intent.parseUri(uri, 0);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                } catch (URISyntaxException e) {
                    Log.e(TAG, "URISyntaxException: [" + uri + "]");
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "ActivityNotFound: [" + uri + "]");
                }
                playSoundEffect(SoundEffectConstants.CLICK);
                switchToState(State.Closing);
                return;
            }
        }
    }

    @Override
    public void onGestureStarted(GestureOverlayView overlay, MotionEvent event) {
        if (mState != State.Expanded) {
            switchToState(State.Expanded);
        }
    }
}
