package com.android.systemui.statusbar.appcirclesidebar;

import android.util.FloatMath;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ListView;

import com.android.systemui.R;

public class CircularViewModifier extends ViewModifier {

    private static final int CIRCLE_OFFSET = 500;
    private static final float DEGTORAD = 1.0f / 180.0f * (float) Math.PI;
    private static final float SCALING_RATIO = 0.001f;
    private static final float TRANSLATION_RATIO = 0.09f;

    @Override
    void applyToView(final View v, final AbsListView parent) {
        final float halfHeight = v.getHeight() * 0.5f;
        final float parentHalfHeight = parent.getHeight() * 0.5f;
        final float y = v.getY();
        final float rot = parentHalfHeight - halfHeight - y;

        v.setPivotX(0.0f);
        v.setPivotY(halfHeight);
        v.setRotation(rot * 0.05f);
        v.setTranslationX((-FloatMath.cos(rot * TRANSLATION_RATIO * DEGTORAD) + 1) * CIRCLE_OFFSET);

        final float scale = 1.0f - Math.abs(parentHalfHeight - halfHeight - y) * SCALING_RATIO;
        v.setScaleX(scale);
        v.setScaleY(scale);
    }
}
