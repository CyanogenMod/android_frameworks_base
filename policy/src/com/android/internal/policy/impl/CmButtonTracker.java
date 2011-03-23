package com.android.internal.policy.impl;

import android.os.Handler;
import android.util.Slog;
import android.view.ViewConfiguration;

public class CmButtonTracker extends Object{
    private final static boolean DEBUG=false;
    private final static String TAG="CmButtonTracker";

    private int mKeyCode;
    private boolean mReverseMode=false;
    private Runnable mLongPressTracker;

    private boolean mIsDown=false;

    Handler mHandler;

    protected OnPressListener mOnPressListener=null;
    protected OnLongPressListener mOnLongPressListener=null;

    public interface OnPressListener {
        void onPress(int KeyCode);
    }

    public interface OnLongPressListener {
        void onLongPress(int KeyCode);
    }

    CmButtonTracker(int keyCode){
        super();
        mHandler=new Handler();
        mKeyCode=keyCode;

        mLongPressTracker = new Runnable() {
            public void run() {
                mIsDown=false;
                callLongPressListener();
            }
        };
    }

    public void setReverseMode(boolean reverseMode){
        mReverseMode=reverseMode;
        if(DEBUG) Slog.i(TAG, "KeyCode(" + Integer.toString(mKeyCode) + ") - " +
                "Reversemode: " + Boolean.toString(reverseMode));
    }

    public void setOnPressListener (OnPressListener l){
        mOnPressListener=l;
    }
    public void setOnLongPressListener (OnLongPressListener l){
        mOnLongPressListener=l;
    }

    public void track(int keyCode, boolean isDown){
        // if not our keycode, ignore
        if(keyCode!=mKeyCode)
            return;
        mHandler.removeCallbacks(mLongPressTracker);

        // key up event
        if(mIsDown){
            mIsDown=false;
            callPressListener();
            return;
        }

        // key down event - with long-press callback
        if(isDown){
            mIsDown=true;
            if(DEBUG) Slog.i(TAG, "KeyCode(" + Integer.toString(mKeyCode) + ") - " +
                    "IsDown");
            mHandler.postDelayed(mLongPressTracker, ViewConfiguration.getLongPressTimeout());
        }
    }

    private void callPressListener(){
        if(mReverseMode){
            if(mOnLongPressListener!=null)
                mOnLongPressListener.onLongPress(mKeyCode);
        }else{
            if(mOnPressListener!=null)
                mOnPressListener.onPress(mKeyCode);
        }

        if(DEBUG) Slog.i(TAG, "KeyCode(" + Integer.toString(mKeyCode) + ") - " +
        "callPressListener() - reverseMode: " + Boolean.toString(mReverseMode));
    }

    private void callLongPressListener(){
        if(mReverseMode){
            if(mOnPressListener!=null)
                mOnPressListener.onPress(mKeyCode);
        }else{
            if(mOnLongPressListener!=null)
                mOnLongPressListener.onLongPress(mKeyCode);
        }

        if(DEBUG) Slog.i(TAG, "KeyCode(" + Integer.toString(mKeyCode) + ") - " +
        "callLongPressListener() - reverseMode: " + Boolean.toString(mReverseMode));
    }
}
