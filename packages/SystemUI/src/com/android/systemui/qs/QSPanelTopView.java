package com.android.systemui.qs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.Nullable;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.android.systemui.R;

public class QSPanelTopView extends FrameLayout {

    public static final int TOAST_DURATION = 2000;

    protected View mEditTileInstructionView;
    protected View mDropTarget;
    protected View mBrightnessView;
    protected TextView mToastView;

    private boolean mEditing;
    private boolean mDisplayingInstructions;
    private boolean mDisplayingTrash;
    private boolean mDisplayingToast;

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public QSPanelTopView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
                          int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setClipToPadding(false);
    }

    public View getDropTarget() {
        return mDropTarget;
    }

    public View getBrightnessView() {
        return mBrightnessView;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDropTarget = findViewById(R.id.delete_container);
        mEditTileInstructionView = findViewById(R.id.edit_container);
        mBrightnessView = findViewById(R.id.brightness_container);
        mToastView = (TextView) findViewById(R.id.qs_toast);

        mDropTarget.setVisibility(View.GONE);
        mEditTileInstructionView.setVisibility(View.GONE);
    }

    public void setEditing(boolean editing) {
        mEditing = editing;
        if (editing) {
            mDisplayingInstructions = true;
            mDisplayingTrash = false;
        } else {
            mDisplayingInstructions = false;
            mDisplayingTrash = false;
        }
        animateToState();
    }

    public void onStopDrag() {
        mDisplayingTrash = false;
        animateToState();
    }

    public void onStartDrag() {
        mDisplayingTrash = true;
        animateToState();
    }

    public void toast(int textStrResId) {
        mDisplayingToast = true;
        mToastView.setText(textStrResId);
        animateToState();
    }

    private void animateToState() {
        showBrightnessSlider(!mEditing && !mDisplayingToast);
        showInstructions(mEditing
                && mDisplayingInstructions
                && !mDisplayingTrash
                && !mDisplayingToast);
        showTrash(mEditing && mDisplayingTrash && !mDisplayingToast);
        showToast(mDisplayingToast);
    }

    private void showBrightnessSlider(boolean show) {
        if (show) {
            // slide brightness in
            mBrightnessView
                    .animate()
                    .withLayer()
                    .y(getTop())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mBrightnessView.setVisibility(View.VISIBLE);
                        }
                    });
        } else {
            // slide out brightness
            mBrightnessView
                    .animate()
                    .withLayer()
                    .y(-getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mBrightnessView.setVisibility(View.INVISIBLE);
                        }
                    });
        }
    }

    private void showInstructions(boolean show) {
        if (show) {
            // slide in instructions
            mEditTileInstructionView.animate()
                    .withLayer()
                    .y(getTop())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mEditTileInstructionView.setVisibility(View.VISIBLE);
                        }
                    });
        } else {
            // animate instructions out
            mEditTileInstructionView.animate()
                    .withLayer()
                    .y(-getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mEditTileInstructionView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mEditTileInstructionView.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void showTrash(boolean show) {
        if (show) {
            // animate drop target in
            mDropTarget.animate()
                    .withLayer()
                    .y(getTop())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mDropTarget.setVisibility(View.VISIBLE);
                        }
                    });
        } else {
            // drop target animates up
            mDropTarget.animate()
                    .withLayer()
                    .y(-getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mDropTarget.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mDropTarget.setVisibility(View.GONE);
                        }
                    });
        }
    }

    private void showToast(boolean show) {
        if (show) {
            mToastView.animate()
                    .withLayer()
                    .y(getTop())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mToastView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mDisplayingToast = false;
                            mToastView.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    animateToState();
                                }
                            }, TOAST_DURATION);
                        }
                    });
        } else {
            mToastView.animate()
                    .withLayer()
                    .y(-getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mToastView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mToastView.setVisibility(View.GONE);
                        }
                    });
        }
    }
}
