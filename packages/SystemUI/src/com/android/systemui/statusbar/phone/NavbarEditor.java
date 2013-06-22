/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.systemui.statusbar.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.DisplayInfo;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.util.ArrayUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.NavigationButtons;
import com.android.systemui.statusbar.NavigationButtons.ButtonInfo;
import com.android.systemui.statusbar.policy.KeyButtonView;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Handles the editing of the navigation bar
 * @author Danesh M
 * @hide
 */
public class NavbarEditor implements View.OnTouchListener {
    /**
     * Holds reference to all assignable button ids.
     * Hold this in sync with {@link NavigationButtons#BUTTON_COUNT}
     */
    public static final int[] BUTTON_IDS =
            { R.id.one, R.id.two, R.id.three, R.id.four, R.id.five, R.id.six };

    /**
     * Subset of BUTON_IDS, to differentiate small/side buttons
     * since they can be assigned additional functionality.
     * Hold this in sync with {@link NavigationButtons#BUTTON_IS_SMALL}
     */
    public static final int[] SMALL_BUTTON_IDS = { R.id.one, R.id.six };

    private final ArrayList<Integer> mIds;

    private static Boolean sIsDevicePhone = null;
    protected int mVisibleCount = 4;
    private boolean mInEditMode = false;

    /**
     * Holds reference to the parent/root of the inflated view
     */
    private View mParent;

    /**
     * Button chooser dialog
     */
    AlertDialog mDialog;

    /**
     * mVertical = Whether we're in landscape mode
     * mLongPressed = Whether the longpress runnable was activated.
     */
    boolean mVertical;
    boolean mLongPressed;
    float mDragOrigin;

    private Context mContext;

    // just to avoid reallocations
    private static final int[] sLocation = new int[2];

    public NavbarEditor (View parent, boolean orientation) {
        mParent = parent;
        mVertical = orientation;
        mContext = parent.getContext();

        mIds = new ArrayList<Integer>();
        for (int id : BUTTON_IDS) {
            mIds.add(id);
        }
    }

    /**
     * Find intersecting views in mIds
     * @param pos - pointer location
     * @param v - view being dragged
     * @return array index in mIds of view intersecting
     */
    private int findInterceptingViewIndex(float pos, View v) {
        for (int i = 0; i < mIds.size(); i++) {
            if (ArrayUtils.contains(SMALL_BUTTON_IDS, mIds.get(i))) {
                continue;
            }

            View otherView = mParent.findViewById(mIds.get(i));
            if (otherView == v) {
                continue;
            }

            otherView.getLocationOnScreen(sLocation);
            float otherPos = sLocation[mVertical ? 1 : 0];
            float otherDimension = mVertical ? v.getHeight() : v.getWidth();

            if (pos > (otherPos + otherDimension / 4) && pos < (otherPos + otherDimension)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Longpress runnable to assign buttons in edit mode
     */
    private Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (mInEditMode) {
                mLongPressed = true;
                mParent.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            }
        }
    };

    public void setEditMode(boolean editMode) {
        mInEditMode = editMode;
        for (Integer id : BUTTON_IDS) {
            KeyButtonView button = (KeyButtonView) mParent.findViewById(id);
            if (button != null) {
                button.setEditMode(editMode);
                button.setOnTouchListener(editMode ? this : null);
            }
        }
        if (!editMode && mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

    protected static boolean isDevicePhone(Context con) {
        if (sIsDevicePhone == null) {
            WindowManager wm = (WindowManager)con.getSystemService(Context.WINDOW_SERVICE);
            DisplayInfo outDisplayInfo = new DisplayInfo();
            wm.getDefaultDisplay().getDisplayInfo(outDisplayInfo);
            int shortSize = Math.min(outDisplayInfo.logicalHeight, outDisplayInfo.logicalWidth);
            int shortSizeDp = shortSize * DisplayMetrics.DENSITY_DEFAULT / outDisplayInfo.logicalDensityDpi;
            if (shortSizeDp < 600) {
                // 0-599dp: "phone" UI with a separate status & navigation bar
                sIsDevicePhone = true;
            } else {
                sIsDevicePhone = false;
            }
        }
        return sIsDevicePhone;
    }

    @Override
    public boolean onTouch(final View view, MotionEvent event) {
        if (!mInEditMode || (mDialog != null && mDialog.isShowing())) {
            return false;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            view.setPressed(true);
            view.getLocationOnScreen(sLocation);
            mDragOrigin = sLocation[mVertical ? 1 : 0];
            view.postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            view.setPressed(false);

            if (!mLongPressed || ArrayUtils.contains(SMALL_BUTTON_IDS, view.getId())) {
                return false;
            }

            ViewGroup viewParent = (ViewGroup) view.getParent();
            float pos = mVertical ? event.getRawY() : event.getRawX();
            float buttonSize = mVertical ? view.getHeight() : view.getWidth();
            float min = mVertical ? viewParent.getTop() : (viewParent.getLeft() - buttonSize / 2);
            float max = mVertical ? (viewParent.getTop() + viewParent.getHeight())
                    : (viewParent.getLeft() + viewParent.getWidth());

            // Prevents user from dragging view outside of bounds
            if (pos < min || pos > max) {
                return false;
            }
            if (!mVertical) {
                view.setX(pos - viewParent.getLeft() - buttonSize / 2);
            } else {
                view.setY(pos - viewParent.getTop() - buttonSize / 2);
            }
            int affectedViewPosition = findInterceptingViewIndex(pos, view);
            if (affectedViewPosition == -1) {
                return false;
            }
            switchId(mIds.indexOf(view.getId()), affectedViewPosition, view);
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.setPressed(false);
            view.removeCallbacks(mCheckLongPress);

            if (!mLongPressed && !view.getTag().equals(NavigationButtons.HOME)) {
                final ButtonAdapter list = new ButtonAdapter(mContext, mParent, mIds,
                        ArrayUtils.contains(SMALL_BUTTON_IDS, view.getId()));
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                        .setTitle(mContext.getString(R.string.navbar_dialog_title))
                        .setAdapter(list, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                KeyButtonView button = (KeyButtonView) view;
                                ButtonInfo info = (ButtonInfo) list.getItem(which);

                                button.setInfo(info, mVertical);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

                mDialog = builder.create();
                mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
                mDialog.setCanceledOnTouchOutside(false);
                mDialog.show();
            } else {
                // Reset the dragged view to its original location
                ViewGroup parent = (ViewGroup) view.getParent();

                if (!mVertical) {
                    view.setX(mDragOrigin - parent.getLeft());
                } else {
                    view.setY(mDragOrigin - parent.getTop());
                }
            }
            mLongPressed = false;
        }
        return true;
    }

    /**
     * Switches positions of two views and
     * updates their mIds entry
     * @param to - index for new position in mIds
     * @param from - index for old position in mIds
     * @param view - view being dragged
     */
    private void switchId(int to, int from, View view) {
        View targetView = mParent.findViewById(mIds.get(from));
        ViewGroup parent = (ViewGroup) view.getParent();

        targetView.getLocationOnScreen(sLocation);
        if (!mVertical) {
            targetView.setX(mDragOrigin - parent.getLeft());
            mDragOrigin = sLocation[0];
        } else {
            targetView.setY(mDragOrigin - parent.getTop());
            mDragOrigin = sLocation[1];
        }

        Collections.swap(mIds, to, from);
    }

    /**
     * Saves the current key arrangement
     * to the settings provider
     */
    protected void saveKeys() {
        ButtonInfo[] buttons = new ButtonInfo[NavigationButtons.SLOT_COUNT];
        for (int i = 0; i < NavigationButtons.SLOT_COUNT; i++) {
            int idIndex = mVertical ? NavigationButtons.SLOT_COUNT - i : i;
            buttons[i] = (ButtonInfo) mParent.findViewById(mIds.get(idIndex)).getTag();
        }
        NavigationButtons.storeButtonMap(mContext, buttons);
    }

    /**
     * Updates the buttons according to the
     * key arrangement stored in settings provider
     */
    protected void updateKeys() {
        ButtonInfo[] buttons = NavigationButtons.loadButtonMap(mContext);

        mVisibleCount = 0;

        for (int i = 0; i < buttons.length; i++) {
            int id = BUTTON_IDS[i];
            ButtonInfo info = buttons[i];
            KeyButtonView button = (KeyButtonView) mParent.findViewById(id);
            boolean isSmallButton = NavigationButtons.IS_SLOT_SMALL[i];

            button.setInfo(info, mVertical);
            if (!info.equals(NavigationButtons.EMPTY) && !isSmallButton) {
                mVisibleCount++;
            }

            button.setTranslationX(0);
            mIds.set(i, id);
        }

        if (isDevicePhone(mContext)) {
            adjustPadding();
        }
        updateLowLights();
    }

    /**
     * Accommodates the padding between keys based on
     * number of keys in use.
     */
    private void adjustPadding() {
        ViewGroup viewParent = (ViewGroup) mParent.findViewById(R.id.mid_nav_buttons);
        int sCount = mVisibleCount;
        for (int v = 0; v < viewParent.getChildCount();v++) {
            View cView = viewParent.getChildAt(v);
            if (cView instanceof KeyButtonView) {
                View nextPadding = viewParent.getChildAt(v+1);
                if (nextPadding != null) {
                    View nextKey = viewParent.getChildAt(v+2);
                    ButtonInfo nextBi = NavigationButtons.EMPTY;
                    if (nextKey != null) {
                        nextBi = (ButtonInfo) nextKey.getTag();
                    }
                    ButtonInfo curBi = (ButtonInfo) cView.getTag();
                    if (nextKey != null && nextBi != null
                            && curBi != null && curBi != NavigationButtons.EMPTY) {
                        if (nextBi != NavigationButtons.EMPTY){
                            nextPadding.setVisibility(View.VISIBLE);
                        } else {
                            if (sCount > 1) {
                                nextPadding.setVisibility(View.VISIBLE);
                            } else {
                                nextPadding.setVisibility(View.GONE);
                            }
                        }
                        sCount--;
                    } else {
                        nextPadding.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    protected void updateLowLights() {
        ViewGroup lowLights = (ViewGroup) mParent.findViewById(R.id.lights_out);
        int totalViews = lowLights.getChildCount();
        int visibleCount = mVisibleCount;
        for (int v = 0;v < totalViews; v++) {
            if (lowLights.getChildAt(v) instanceof ImageView) {
                View blank = lowLights.getChildAt(v+1);
                if (visibleCount <= 0) {
                    lowLights.getChildAt(v).setVisibility(View.GONE);
                    if (blank != null) {
                        blank.setVisibility(View.GONE);
                    }
                } else {
                    lowLights.getChildAt(v).setVisibility(View.VISIBLE);
                    visibleCount--;
                    if (visibleCount > 0 && blank != null) {
                        blank.setVisibility(View.VISIBLE);
                    } else if (blank != null) {
                        blank.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    private static class ButtonAdapter extends ArrayAdapter<ButtonInfo> {
        private ArrayList<ButtonInfo> mTakenItems;

        public ButtonAdapter(Context context, View buttonContainer,
                ArrayList<Integer> buttonIds, boolean smallButtons) {
            super(context, android.R.layout.select_dialog_item, android.R.id.text1,
                    buildItems(smallButtons));

            mTakenItems = new ArrayList<ButtonInfo>();
            for (int id : buttonIds) {
                ButtonInfo info = (ButtonInfo) buttonContainer.findViewById(id).getTag();
                if (info != null && info != NavigationButtons.EMPTY) {
                    mTakenItems.add(info);
                }
            }
        }

        private static ArrayList<ButtonInfo> buildItems(boolean smallButtons) {
            ArrayList<ButtonInfo> items =
                    new ArrayList<ButtonInfo>(NavigationButtons.BUTTON_MAP.values());

            // home button is not assignable
            items.remove(NavigationButtons.HOME);
            // menu buttons can only be assigned to side buttons
            if (!smallButtons) {
                items.remove(NavigationButtons.CONDITIONAL_MENU);
                items.remove(NavigationButtons.ALWAYS_MENU);
            } else {
                items.remove(NavigationButtons.MENU_BIG);
            }

            return items;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            TextView text = (TextView) view.findViewById(android.R.id.text1);
            if (isEnabled(position)) {
                text.setBackground(null);
            } else {
                // FIXME: make this a color resource
                text.setBackgroundColor(Color.parseColor("#181818"));
            }
            text.setText(getContext().getResources().getString(getItem(position).displayId));

            return view;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !mTakenItems.contains(getItem(position));
        }
    }
}
