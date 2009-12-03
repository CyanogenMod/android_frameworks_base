package com.android.server.status;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class LatestItemView extends FrameLayout {

    public LatestItemView(Context context, AttributeSet attrs) {
        super(context, attrs, Utils.resolveDefaultStyleAttr(context,
            "com_android_server_status_latestItemView",
            com.android.internal.R.attr.com_android_server_status_latestItemView));
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        return onTouchEvent(ev);
    }
}
