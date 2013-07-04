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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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
    private static final int[] BUTTON_IDS =
            { R.id.one, R.id.two, R.id.three, R.id.four, R.id.five, R.id.six };

    /**
     * Subset of BUTON_IDS, to differentiate small/side buttons
     * since they can be assigned additional functionality.
     * Hold this in sync with {@link NavigationButtons#BUTTON_IS_SMALL}
     */
    private static final int[] SMALL_BUTTON_IDS = { R.id.one, R.id.six };

    // holds the button views in the order they currently appear on screen
    private final ArrayList<KeyButtonView> mButtonViews;

    private Context mContext;
    private static Boolean sIsDevicePhone = null;
    private boolean mInEditMode = false;

    // Holds reference to the parent/root of the inflated view
    private View mParent;

    // Button chooser dialog
    private AlertDialog mDialog;

    // true == we're in landscape mode
    private boolean mVertical;
    // true == we're currently checking for long press
    private boolean mLongPressed;
    // start point of the current drag operation
    private float mDragOrigin;

    // just to avoid reallocations
    private static final int[] sLocation = new int[2];

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

    public NavbarEditor (View parent, boolean orientation) {
        mContext = parent.getContext();
        mParent = parent;
        mVertical = orientation;

        mButtonViews = new ArrayList<KeyButtonView>();
        for (int id : BUTTON_IDS) {
            mButtonViews.add((KeyButtonView) mParent.findViewById(id));
        }
    }

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

    public static boolean isDevicePhone(Context context) {
        if (sIsDevicePhone == null) {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            DisplayInfo outDisplayInfo = new DisplayInfo();

            wm.getDefaultDisplay().getDisplayInfo(outDisplayInfo);

            int shortSize = Math.min(outDisplayInfo.logicalHeight, outDisplayInfo.logicalWidth);
            int shortSizeDp = shortSize * DisplayMetrics.DENSITY_DEFAULT / outDisplayInfo.logicalDensityDpi;

            // 0-599dp: "phone" UI with a separate status & navigation bar
            sIsDevicePhone = shortSizeDp < 600;
        }

        return sIsDevicePhone;
    }

    /**
     * Find intersecting view in mButtonViews
     * @param pos - pointer location
     * @param v - view being dragged
     * @return intersecting view or null
     */
    private View findInterceptingView(float pos, View v) {
        for (KeyButtonView otherView : mButtonViews) {
            if (otherView == v) {
                continue;
            }

            if (ArrayUtils.contains(SMALL_BUTTON_IDS, otherView.getId())) {
                continue;
            }

            otherView.getLocationOnScreen(sLocation);
            float otherPos = sLocation[mVertical ? 1 : 0];
            float otherDimension = mVertical ? v.getHeight() : v.getWidth();

            if (pos > (otherPos + otherDimension / 4) && pos < (otherPos + otherDimension)) {
                return otherView;
            }
        }
        return null;
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
            View affectedView = findInterceptingView(pos, view);
            if (affectedView == null) {
                return false;
            }
            switchId(affectedView, view);
        } else if (event.getAction() == MotionEvent.ACTION_UP
                || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.setPressed(false);
            view.removeCallbacks(mCheckLongPress);

            if (!mLongPressed && !view.getTag().equals(NavigationButtons.HOME)) {
                final boolean isSmallButton = ArrayUtils.contains(SMALL_BUTTON_IDS, view.getId());
                final ButtonAdapter list = new ButtonAdapter(mContext, mButtonViews, isSmallButton);

                AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                        .setTitle(mContext.getString(R.string.navbar_dialog_title))
                        .setAdapter(list, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                KeyButtonView button = (KeyButtonView) view;
                                ButtonInfo info = (ButtonInfo) list.getItem(which);

                                button.setInfo(info, mVertical, isSmallButton, false);
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
     * updates their mButtonViews entry
     * @param targetView - view to be replaced
     * @param view - view being dragged
     */
    private void switchId(View targetView, View view) {
        ViewGroup parent = (ViewGroup) view.getParent();

        targetView.getLocationOnScreen(sLocation);
        if (!mVertical) {
            targetView.setX(mDragOrigin - parent.getLeft());
            mDragOrigin = sLocation[0];
        } else {
            targetView.setY(mDragOrigin - parent.getTop());
            mDragOrigin = sLocation[1];
        }

        int targetIndex = mButtonViews.indexOf(targetView);
        int draggedIndex = mButtonViews.indexOf(view);
        Collections.swap(mButtonViews, draggedIndex, targetIndex);
    }

    /**
     * Saves the current key arrangement
     * to the settings provider
     */
    protected void saveKeys() {
        ButtonInfo[] buttons = new ButtonInfo[NavigationButtons.SLOT_COUNT];
        for (int i = 0; i < NavigationButtons.SLOT_COUNT; i++) {
            int idIndex = mVertical ? NavigationButtons.SLOT_COUNT - i - 1 : i;
            buttons[i] = (ButtonInfo) mButtonViews.get(idIndex).getTag();
        }
        NavigationButtons.storeButtonMap(mContext, buttons);
    }

    /**
     * Updates the buttons according to the
     * key arrangement stored in settings provider
     */
    protected void updateKeys() {
        ButtonInfo[] buttons = NavigationButtons.loadButtonMap(mContext);
        int visibleCount = 0;
        boolean smallButtonsEmpty = !mInEditMode;

        if (smallButtonsEmpty) {
            int mainButtonsCount = 0;
            for (int i = 0; i < buttons.length; i++) {
                ButtonInfo info = buttons[mVertical ? buttons.length - i - 1 : i];
                boolean isSmallButton = NavigationButtons.IS_SLOT_SMALL[i];
                if (!info.equals(NavigationButtons.EMPTY)) {
                    if (isSmallButton) {
                        smallButtonsEmpty = false;
                        break;
                    } else {
                        mainButtonsCount++;
                    }
                }
            }
            // only consider hiding the small buttons completely if we have 4 button mode
            if (smallButtonsEmpty && mainButtonsCount < 4) {
                smallButtonsEmpty = false;
            }
        }

        for (int i = 0; i < buttons.length; i++) {
            int id = BUTTON_IDS[i];
            ButtonInfo info = buttons[mVertical ? buttons.length - i - 1 : i];
            KeyButtonView button = (KeyButtonView) mParent.findViewById(id);
            boolean isSmallButton = NavigationButtons.IS_SLOT_SMALL[i];

            button.setInfo(info, mVertical, isSmallButton, smallButtonsEmpty);
            if (!info.equals(NavigationButtons.EMPTY) && !isSmallButton) {
                visibleCount++;
            }

            button.setTranslationX(0);
            mButtonViews.set(i, button);
        }

        if (isDevicePhone(mContext)) {
            adjustPadding(visibleCount);
        }
        updateLowLights(visibleCount);
    }

    /**
     * Accommodates the padding between keys based on
     * number of keys in use.
     */
    private void adjustPadding(int visibleCount) {
        ViewGroup viewParent = (ViewGroup) mParent.findViewById(R.id.mid_nav_buttons);
        int totalViews = viewParent.getChildCount();

        for (int v = 0; v < totalViews; v++) {
            View currentKey = viewParent.getChildAt(v);
            if (!(currentKey instanceof KeyButtonView)) {
                continue;
            }
            View nextPadding = viewParent.getChildAt(v + 1);
            if (nextPadding == null) {
                continue;
            }

            View nextKey = viewParent.getChildAt(v + 2);
            ButtonInfo nextInfo = nextKey == null ? null : (ButtonInfo) nextKey.getTag();
            ButtonInfo currentInfo = (ButtonInfo) currentKey.getTag();

            if (nextInfo != null && currentInfo != null && currentInfo != NavigationButtons.EMPTY) {
                if (nextInfo != NavigationButtons.EMPTY || visibleCount > 1) {
                    nextPadding.setVisibility(View.VISIBLE);
                } else {
                    nextPadding.setVisibility(View.GONE);
                }
                visibleCount--;
            } else {
                nextPadding.setVisibility(View.GONE);
            }
        }
    }

    protected void updateLowLights(int visibleCount) {
        ViewGroup lowLights = (ViewGroup) mParent.findViewById(R.id.lights_out);
        int totalViews = lowLights.getChildCount();

        for (int v = 0;v < totalViews; v++) {
            View currentView = lowLights.getChildAt(v);
            if (!(currentView instanceof ImageView)) {
                continue;
            }

            if (visibleCount <= 0) {
                currentView.setVisibility(View.GONE);
            } else {
                currentView.setVisibility(View.VISIBLE);
                visibleCount--;
            }

            View blank = lowLights.getChildAt(v + 1);
            if (blank != null) {
                blank.setVisibility(visibleCount > 0 ? View.VISIBLE : View.GONE);
            }
        }
    }

    private static class ButtonAdapter extends ArrayAdapter<ButtonInfo> {
        private ArrayList<ButtonInfo> mTakenItems;

        public ButtonAdapter(Context context,
                ArrayList<KeyButtonView> buttons, boolean smallButtons) {
            super(context, R.layout.navigation_bar_edit_menu_item, R.id.key_text,
                    buildItems(smallButtons));

            mTakenItems = new ArrayList<ButtonInfo>();
            for (KeyButtonView button : buttons) {
                ButtonInfo info = (ButtonInfo) button.getTag();
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
            ButtonInfo info = getItem(position);
            boolean enabled = isEnabled(position);

            TextView text = (TextView) view.findViewById(R.id.key_text);
            text.setText(getContext().getResources().getString(info.displayId));
            text.setEnabled(enabled);

            ImageView icon = (ImageView) view.findViewById(R.id.key_icon);
            icon.setImageResource(info.portResource);
            icon.setColorFilter(new PorterDuffColorFilter(
                    text.getCurrentTextColor(), PorterDuff.Mode.SRC_IN));

            return view;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int position) {
            return !mTakenItems.contains(getItem(position));
        }
    }
}
