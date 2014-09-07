package com.android.keyguard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.PaletteItem;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;

import com.android.internal.util.cm.SmartPackageStatsContracts;
import com.android.internal.util.cm.SmartPackageStatsUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.keyguard.SlidingUpPanelLayout.SlideState;

public class AppDock extends LinearLayout implements View.OnTouchListener, SlidingUpPanelLayout.PanelSlideListener {

    private static final int ARROW_MAX_HEIGHT = 300;

    ArrayList<ImageView> mViews = new ArrayList<ImageView>();
    ViewInfo mLastTouch;
    SlidingUpPanelLayout mPanel;
    float middleHeight = 0;
    Path mPath;
    Paint mPaint;
    ValueAnimator mFadeIn;
    private FetchApps mFetchApps;
    private AccelerateInterpolator mInterpolator = new AccelerateInterpolator();

    private class FetchApps extends AsyncTask<Void, Void, ArrayList<ComponentName>> {

        @Override
        protected ArrayList<ComponentName> doInBackground(Void... params) {
            return SmartPackageStatsUtils.getRelevantComponentForRightNow(getContext(), 8);
        }

        @Override
        protected void onPostExecute(ArrayList<ComponentName> infos) {
            if (infos != null) {
                for (int i = 0; i < mViews.size(); i++) {
                    ImageView v = mViews.get(i);
                    ComponentName info = null;
                    if (i < infos.size()) {
                        info = infos.get(i);
                    }
                    populateView(v, info);
                }
            }
        }

    }

    public AppDock(Context context) {
        super(context);
        init(context);
    }

    public AppDock(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public AppDock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        mPath.reset();
        mPath.moveTo(0, 0);
        mPath.lineTo(getWidth() / 2, middleHeight);
        mPath.lineTo(getWidth(), 0);
        mPath.lineTo(getWidth(), getHeight());
        mPath.lineTo(0, getHeight());
        mPath.close();
        canvas.drawPath(mPath, mPaint);
    }

    public void startDipAnimation() {
        if (mFadeIn != null && (mFadeIn.isStarted() || mFadeIn.isRunning())) {
            return;
        }
        mFadeIn = new ValueAnimator();
        mFadeIn.setFloatValues(0.25f, 10f);
        mFadeIn.setDuration(2000);
        mFadeIn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Float input = (Float) animation.getAnimatedValue();
                double finalValue = Math.exp(-input) * Math.cos(2f * Math.PI * input);
                middleHeight = (-1) * (int) (finalValue * 150);
                invalidate();
            }
        });
        mFadeIn.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                middleHeight = 0;
            }
        });
        mFadeIn.start();
    }

    private Runnable mPopulatePackagesRunnable = new Runnable() {
        @Override
        public void run() {
            if (mFetchApps == null || mFetchApps.getStatus() != AsyncTask.Status.RUNNING) {
                mFetchApps = new FetchApps();
                mFetchApps.execute();
            }
        }
    };

    private ContentObserver mObserver = new ContentObserver(null) {

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            populatePackages();
        }
    };

    private void init(Context context) {
        setWillNotDraw(false);
        context.getContentResolver().registerContentObserver(SmartPackageStatsContracts.AUTHORITY_URI, true, mObserver);
        populatePackages();
        setPadding(0, 40, 0, 0);
        setOrientation(LinearLayout.VERTICAL);

        mPath = new Path();
        mPaint = new Paint();
        mPaint.setColor(Color.parseColor("#88000000"));
        mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(0);
        mPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        for (int j = 0; j < 2; j++) {
            LinearLayout layout = new LinearLayout(getContext());
            LinearLayout.LayoutParams params = new LayoutParams(220, 220);
            params.weight = 1;
            for (int i = 0; i < 4; i++) {
                ImageView v = new ImageView(getContext());
                v.setPadding(25, 25, 25, 25);
                v.setOnTouchListener(this);
                mViews.add(v);
                layout.addView(v, params);
            }
            addView(layout);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mFadeIn != null) {
            mFadeIn.cancel();
        }
        if (v != mPanel) {
            mLastTouch = (ViewInfo) v.getTag();
        }
        return false;
    }

    private void populatePackages() {
        removeCallbacks(mPopulatePackagesRunnable);
        postDelayed(mPopulatePackagesRunnable, 1000);
    }

    public void launchApp() {
        if (mLastTouch == null) {
            return;
        }
        Intent i = new Intent();
        i.setComponent(mLastTouch.info);
        try {
            KeyguardActivityLauncher blah = new KeyguardActivityLauncher() {

                @Override
                Context getContext() {
                    return AppDock.this.getContext();
                }

                @Override
                KeyguardSecurityCallback getCallback() {
                    // TODO Auto-generated method stub
                    return null;
                }

                @Override
                LockPatternUtils getLockPatternUtils() {
                    return new LockPatternUtils(getContext());
                }
                
            };
            blah.launchActivity(i, false, false, null, null);
            //final Bundle animation = ActivityOptions.makeCustomAnimation(blah.getContext(), R.anim.slide, R.anim.exit).toBundle();
            //blah.launchActivityWithAnimation(i, false, animation, null, null);
            //blah.launchActivity(i, false, false, null, onStarted);
            //context.startActivity(i);
            //activity.overridePendingTransition(R.anim.slide, R.anim.exit);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
        mLastTouch = null;
    }

    public void setPanel(SlidingUpPanelLayout panel) {
        mPanel = panel;
        mPanel.setPanelSlideListener(this);
        mPanel.setOnTouchListener(this);
    }

    private class ViewInfo {
        ComponentName info;
        int color;
        private ViewInfo(ComponentName info, int color) {
            this.info = info;
            this.color = color;
        }
    }

    private void populateView(ImageView v, ComponentName activityInfo) {
        if (activityInfo == null) {
            v.setImageDrawable(null);
            v.setTag(null);
            return;
        }

        PackageManager manager = getContext().getPackageManager();
        int bestColor = Color.RED;
        try {
            Drawable icon = manager.getActivityIcon(activityInfo);
            v.setImageDrawable(icon);
            Palette pallete = Palette.generate(((BitmapDrawable) icon).getBitmap());

            int highestPopulation = 0;
            for (PaletteItem color : pallete.getPallete()) {
                int index = color.toString().lastIndexOf("Population:");
                String temp = color.toString().substring(index + "Population:".length());
                if (highestPopulation < Integer.parseInt(temp.substring(0, temp.length() - 1).trim())) {
                    highestPopulation = Integer.parseInt(temp.substring(0, temp.length() - 1).trim());
                    bestColor = color.getRgb();
                };
            }
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        v.setTag(new ViewInfo(activityInfo, bestColor));
    }

    ArgbEvaluator evaluator = new ArgbEvaluator();
    public void slide(float slideOffset, boolean inverse) {
        if (slideOffset == 0f) {
            mPaint.setColor(Color.parseColor("#88000000"));
            for (ImageView v : mViews) {
                v.setImageAlpha(255);
            }
        } else if (mLastTouch != null) {
            float offset = Math.min(1, slideOffset * 2.25f);
            Integer color = (Integer) evaluator.evaluate(offset, Color.parseColor("#88000000"), mLastTouch.color);
            if (Color.alpha(color) <= 255) {
                mPaint.setColor(color);
            }
            for (ImageView v : mViews) {
                v.setImageAlpha(255 - mPaint.getAlpha());
            }
        }
        middleHeight = (inverse ? 1 : -1) * (int) (mInterpolator.getInterpolation(slideOffset)  * ARROW_MAX_HEIGHT * 8f);
        int max = inverse ? ARROW_MAX_HEIGHT / 5 : ARROW_MAX_HEIGHT;
        if ((Math.abs(middleHeight) > max)) {
            middleHeight = (inverse ? 1 : -1) * max;
        }
        invalidate();
    }

    @Override
    public void onPanelSlide(View panel, float slideOffset) {
        if (mHiding) return;
        slide(slideOffset, false);
        if (slideOffset > 0.8 && !mPanel.isBeingDragged()) {
            launchApp();
        } else if (slideOffset <= 0.3f && !mPanel.isBeingDragged()) {
            startDipAnimation();
        }
    }

    @Override
    public void onPanelCollapsed(View panel) {

    }

    @Override
    public void onPanelExpanded(View panel) {
        launchApp();
    }

    @Override
    public void onPanelAnchored(View panel) {

    }

    @Override
    public void onPanelHidden(View panel) {

    }
boolean mHiding;
    public void setHiding(boolean b) {
        mHiding = b;
    }
}