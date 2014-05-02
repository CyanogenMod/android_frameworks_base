
package com.android.systemui.quicksettings;

import android.content.Context;
import android.util.AttributeSet;

import com.pheelicks.visualizer.VisualizerView;

public class QuickTileVisualizer extends VisualizerView {

    public QuickTileVisualizer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
    }

    public QuickTileVisualizer(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public QuickTileVisualizer(Context context) {
        super(context, null, 0);
    }

}
