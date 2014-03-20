package com.android.systemui.statusbar.appcirclesidebar;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

public class InputItemHandler {
    private final Context mContext;

    public InputItemHandler(Context context) {
        mContext = context;
    }

    public void registerInputHandler(View view, final InputHandleListener handler) {
        registerClickListener(view, handler);
    }

    private void registerClickListener(final View view, final InputHandleListener handler) {
        view.setOnClickListener(new OnClickListener() {

            public void onClick(View v) {
                handler.handleOnClickEvent(v);
            }
        });
        view.setOnLongClickListener(new OnLongClickListener() {

            public boolean onLongClick(View v) {
                handler.handleOnLongClickEvent(v);
                return true;
            }
        });
    }

    public interface InputHandleListener {
        void handleOnClickEvent(View v);
        void handleOnLongClickEvent(View v);
    }
}
