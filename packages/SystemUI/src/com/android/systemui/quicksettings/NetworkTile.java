package com.android.systemui.quicksettings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

public abstract class NetworkTile extends QuickSettingsTile implements NetworkSignalChangedCallback {
    private NetworkController mController;

    private final long mDefaultDuration = new ValueAnimator().getDuration();
    private final long mShortDuration = mDefaultDuration / 3;

    protected NetworkTile(Context context, QuickSettingsController qsc,
            NetworkController controller, int layoutResourceId) {
        super(context, qsc, layoutResourceId);

        mController = controller;
    }

    @Override
    void onPostCreate() {
        mController.addNetworkSignalChangedCallback(this);
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mController.removeNetworkSignalChangedCallback(this);
        super.onDestroy();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    protected abstract void updateTile();

    protected void setActivity(boolean in, boolean out) {
        setVisibility(mTile.findViewById(R.id.activity_in), in);
        setVisibility(mTile.findViewById(R.id.activity_out), out);
    }

    private void setVisibility(View view, boolean visible) {
        final float newAlpha = visible ? 1 : 0;
        if (view.getAlpha() != newAlpha) {
            view.animate()
                .setDuration(visible ? mShortDuration : mDefaultDuration)
                .alpha(newAlpha)
                .start();
        }
    }
}
