package com.android.systemui.statusbar.phone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.DisplayInfo;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.internal.util.ArrayUtils;
import com.android.systemui.R;
import com.android.systemui.statusbar.policy.KeyButtonView;

/**
 * Handles the editing of the navigation bar
 * @author Danesh M
 * @hide
 */
public class NavbarEditor implements OnTouchListener {

    /**
     * Holds reference to all assignable button ids
     */
    ArrayList<Integer> mIds = new ArrayList<Integer>(Arrays.asList(R.id.one, R.id.two, R.id.three,
            R.id.four, R.id.five,R.id.six));

    /**
     * Subset of mIds, to differentiate small/side buttons
     * since they can be assigned additional functionality
     */
    public static final int[] smallButtonIds = {R.id.one, R.id.six};

    /**
     * Map which holds references to supported/available buttons.
     */
    public static final LinkedHashMap<String, ButtonInfo> buttonMap =
            new LinkedHashMap<String,ButtonInfo>();

    protected static int visibleCount = 4;
    private static Boolean mIsDevicePhone = null;

    /**
     * Holds reference to the parent/root of the inflated view
     */
    private ViewGroup mParent;

    /**
     * Button chooser dialog
     */
    AlertDialog mDialog;

    /**
     * mVertical = Whether we're in landscape mode
     * mLongPressed = Whether the longpress runnable was activated.
     */
    boolean mVertical,mLongPressed;

    private Context mContext;

    //Available buttons
    public static final String NAVBAR_EMPTY = "empty";
    public static final String NAVBAR_HOME = "home";
    public static final String NAVBAR_BACK = "back";
    public static final String NAVBAR_SEARCH = "search";
    public static final String NAVBAR_RECENT = "recent";
    public static final String NAVBAR_CONDITIONAL_MENU = "menu0";
    public static final String NAVBAR_ALWAYS_MENU = "menu1";
    public static final String NAVBAR_MENU_BIG = "menu2";

    static {
        buttonMap.put(NAVBAR_HOME,
                new ButtonInfo(R.string.navbar_home_button, R.string.accessibility_home, KeyEvent.KEYCODE_HOME, R.drawable.ic_sysbar_home,
                        R.drawable.ic_sysbar_home_land, R.drawable.ic_sysbar_home));
        buttonMap.put(NAVBAR_CONDITIONAL_MENU,
                new ButtonInfo(R.string.navbar_menu_conditional_button, R.string.accessibility_menu, KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu,
                        R.drawable.ic_sysbar_menu_land, R.drawable.ic_sysbar_menu));
        buttonMap.put(NAVBAR_ALWAYS_MENU,
                new ButtonInfo(R.string.navbar_menu_always_button, R.string.accessibility_menu, KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu,
                        R.drawable.ic_sysbar_menu_land, R.drawable.ic_sysbar_menu));
        buttonMap.put(NAVBAR_MENU_BIG,
                new ButtonInfo(R.string.navbar_menu_big_button, R.string.accessibility_menu, KeyEvent.KEYCODE_MENU, R.drawable.ic_sysbar_menu_big,
                        R.drawable.ic_sysbar_menu_big_land, 0));
        buttonMap.put(NAVBAR_BACK,
                new ButtonInfo(R.string.navbar_back_button, R.string.accessibility_back,KeyEvent.KEYCODE_BACK, R.drawable.ic_sysbar_back,
                        R.drawable.ic_sysbar_back_land, R.drawable.ic_sysbar_back_side));
        buttonMap.put(NAVBAR_SEARCH,
                new ButtonInfo(R.string.navbar_search_button, R.string.accessibility_back, KeyEvent.KEYCODE_SEARCH, R.drawable.ic_sysbar_search,
                        R.drawable.ic_sysbar_search_land, R.drawable.ic_sysbar_search_side));
        buttonMap.put(NAVBAR_RECENT,
                new ButtonInfo(R.string.navbar_recent_button, R.string.accessibility_recent,0, R.drawable.ic_sysbar_recent,
                        R.drawable.ic_sysbar_recent_land, R.drawable.ic_sysbar_recent_side));
        buttonMap.put(NAVBAR_EMPTY,
                new ButtonInfo(R.string.navbar_empty_button, R.string.accessibility_clear_all,0, R.drawable.ic_sysbar_add,
                        R.drawable.ic_sysbar_add_land, R.drawable.ic_sysbar_add_side));
    }

    public NavbarEditor (ViewGroup parent, Boolean orientation) {
        mParent = parent;
        mVertical = orientation;
        mContext = parent.getContext();
    }

    /**
     * Set the button listeners to this
     * class when in edit mode
     */
    protected void setupListeners() {
        for (int id : mIds) {
            mParent.findViewById(id).setOnTouchListener(this);
        }
    }

    /**
     * Find intersecting views in mIds
     * @param pos - pointer location
     * @param v - view being dragged
     * @return array index in mIds of view intersecting
     */
    private int findInterceptingViewIndex(float pos, View v) {
        int location[] = new int[2];
        for (int cc = 0; cc < mIds.size(); cc++) {
            if (!ArrayUtils.contains(smallButtonIds,mIds.get(cc))) {
                View tmpV = mParent.findViewById(mIds.get(cc));
                tmpV.getLocationOnScreen(location);
                if (tmpV == v) {
                    continue;
                } else if (!mVertical && (pos > (location[0]+v.getWidth()/4) && pos < location[0]+v.getWidth())) {
                    return cc;
                } else if (mVertical && (pos > (location[1]+v.getHeight()/4) && pos < location[1]+v.getHeight())) {
                    return cc;
                }
            }
        }
        return -1;
    }

    /**
     * Longpress runnable to assign buttons in edit mode
     */
    Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (NavigationBarView.getEditMode()) {
                mLongPressed = true;
                mParent.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return;
            }
        }
    };

    protected static boolean isDevicePhone(Context con) {
        if (mIsDevicePhone == null) {
            WindowManager wm = (WindowManager)con.getSystemService(Context.WINDOW_SERVICE);
            DisplayInfo outDisplayInfo = new DisplayInfo();
            wm.getDefaultDisplay().getDisplayInfo(outDisplayInfo);
            int shortSize = Math.min(outDisplayInfo.logicalHeight, outDisplayInfo.logicalWidth);
            int shortSizeDp = shortSize * DisplayMetrics.DENSITY_DEFAULT / outDisplayInfo.logicalDensityDpi;
            if (shortSizeDp < 600) {
                // 0-599dp: "phone" UI with a separate status & navigation bar
                mIsDevicePhone = true;
            } else {
                mIsDevicePhone = false;
            }
        }
        return mIsDevicePhone;
    }

    @Override
    public boolean onTouch(final View view, MotionEvent event) {
        if (!NavigationBarView.getEditMode() || (mDialog != null && mDialog.isShowing())) {
            return false;
        }
        float curPos = 0;
        if (!mVertical) {
            curPos = event.getRawX();
        } else {
            curPos = event.getRawY();
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int screenLoc[] = new int[2];
            view.setPressed(true);
            view.getLocationOnScreen(screenLoc);
            // Store the starting view position in the parent's tag
            if (!mVertical) {
                mParent.setTag(Float.valueOf(screenLoc[0]));
            } else {
                mParent.setTag(Float.valueOf(screenLoc[1]));
            }
            view.postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            view.setPressed(false);
            if (!mLongPressed || ArrayUtils.contains(smallButtonIds, view.getId())) {
                return false;
            }
            view.bringToFront();
            ViewGroup viewParent = (ViewGroup) view.getParent();
            float buttonSize = 0;
            if (!mVertical) {
                buttonSize = view.getWidth();
            } else {
                buttonSize = view.getHeight();
            }
            // Prevents user from dragging view outside of bounds
            if ((!mVertical && ((curPos)  > (viewParent.getWidth() + viewParent.getLeft()) || (curPos - buttonSize/2 <= viewParent.getLeft()))) ||
                (mVertical && ((curPos  > (viewParent.getHeight() + viewParent.getTop())) || (curPos < viewParent.getTop())))) {
                return false;
            }
            if (!mVertical) {
                view.setX(curPos - viewParent.getLeft() - buttonSize/2);
            } else {
                view.setY(curPos - viewParent.getTop() - buttonSize/2);
            }
            int affectedViewPosition = findInterceptingViewIndex(curPos, view);
            if (affectedViewPosition == -1) {
                return false;
            }
            switchId(mIds.indexOf(view.getId()), affectedViewPosition, view);
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.setPressed(false);
            view.removeCallbacks(mCheckLongPress);
            if (!mLongPressed && !view.getTag().equals("home")) {
                final ButtonAdapter list = new ButtonAdapter(ArrayUtils.contains(smallButtonIds, view.getId()) ? true : false);
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setTitle(mContext.getString(R.string.navbar_dialog_title));
                builder.setAdapter(list, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((KeyButtonView) view).setInfo(list.getItem(which).toString(), mVertical);
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
                mDialog = builder.create();
                mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                mDialog.setCanceledOnTouchOutside(false);
                mDialog.show();
                mLongPressed=false;
                return true;
            }
            mLongPressed=false;
            // Reset the dragged view to its original location
            ViewGroup vParent = (ViewGroup) view.getParent();
            if (!mVertical) {
                view.setX((Float) mParent.getTag() - vParent.getLeft());
            } else {
                view.setY((Float) mParent.getTag() - vParent.getTop());
            }
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
        View tView = mParent.findViewById(mIds.get(from));
        int screenLoc[] = new int[2];
        tView.getLocationOnScreen(screenLoc);
        ViewGroup a = (ViewGroup) view.getParent();
        if (!mVertical) {
            tView.setX((Float) mParent.getTag() - a.getLeft());
            mParent.setTag(Float.valueOf(screenLoc[0]));
        } else {
            tView.setY((Float) mParent.getTag() - a.getTop());
            mParent.setTag(Float.valueOf(screenLoc[1]));
        }
        Collections.swap(mIds,to,from);
    }

    /**
     * Saves the current key arrangement
     * to the settings provider
     */
    @SuppressWarnings("unchecked")
    protected void saveKeys() {
        ((ViewGroup) mParent.findViewById(R.id.mid_nav_buttons)).setLayoutTransition(null);
        StringBuilder saveValue = new StringBuilder();
        String delim = "";
        ArrayList<Integer> idMap = (ArrayList<Integer>) mIds.clone();
        if (mVertical) Collections.reverse(idMap);
        for (int id : idMap) {
            saveValue.append(delim);
            delim="|";
            saveValue.append(mParent.findViewById(id).getTag());
        }
        Settings.System.putString(mContext.getContentResolver(), Settings.System.NAV_BUTTONS, saveValue.toString());
    }

    /**
     * Reinflates navigation bar on demand
     * base on current orientation
     */
    protected void reInflate() {
        ((ViewGroup)mParent).removeAllViews();
        if (mVertical) {
            View.inflate(mContext, R.layout.mid_navigation_bar_land, (ViewGroup) mParent);
        } else {
            View.inflate(mContext, R.layout.mid_navigation_bar_port, (ViewGroup) mParent);
        }
    }

    /**
     * Updates the buttons according to the
     * key arrangement stored in settings provider
     */
    @SuppressWarnings("unchecked")
    protected void updateKeys() {
        String saved = Settings.System.getString(mContext.getContentResolver(), Settings.System.NAV_BUTTONS);
        if (saved == null) {
            saved = "empty|back|home|recent|empty|menu0";
        }
        int cc = 0;
        ArrayList<Integer> idMap = (ArrayList<Integer>) mIds.clone();
        if (mVertical) Collections.reverse(idMap);
        visibleCount = 0;
        for (String buttons : saved.split("\\|")) {
            KeyButtonView curView = (KeyButtonView) mParent.findViewById(idMap.get(cc));
            boolean isSmallButton = ArrayUtils.contains(NavbarEditor.smallButtonIds, curView.getId());
            curView.setInfo(buttons, mVertical);
            if (!curView.getTag().equals(NAVBAR_EMPTY) && !isSmallButton) {
                visibleCount++;
            }
            cc++;
        }
        if (isDevicePhone(mContext)) {
            adjustPadding();
        }
    }

    /**
     * Accommodates the padding between keys based on
     * number of keys in use.
     */
    private void adjustPadding() {
        ViewGroup viewParent = (ViewGroup) mParent.findViewById(R.id.mid_nav_buttons);
        int sCount = visibleCount;
        for (int v = 0; v < viewParent.getChildCount();v++) {
            View cView = viewParent.getChildAt(v);
            if (cView instanceof KeyButtonView) {
                View nextPadding = viewParent.getChildAt(v+1);
                if (nextPadding != null) {
                    View nextKey = viewParent.getChildAt(v+2);
                    String nextTag = NAVBAR_EMPTY;
                    if (nextKey != null) {
                        nextTag = (String) nextKey.getTag();
                    }
                    String curTag = (String) cView.getTag();
                    if (nextKey != null && nextTag != null && curTag != null && !curTag.equals(NAVBAR_EMPTY)) {
                        if (!nextTag.equals(NAVBAR_EMPTY)){
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

    protected void updateLowLights(View current) {
        ViewGroup lowLights = (ViewGroup) current.findViewById(R.id.lights_out);
        int totalViews = lowLights.getChildCount();
        int visibleCount = NavbarEditor.visibleCount;
        for (int v = 0;v<totalViews;v++) {
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

    /**
     * Class to store info about supported buttons
     */
    public static final class ButtonInfo {
        public int displayId;
        public int contentDescription;
        public int keyCode;
        public int portResource;
        public int landResource;
        public int sideResource;
        /**
         * Constructor for new button type
         * @param rId - resource id of text shown to user in choose dialog
         * @param cD  - accessibility information regarding button
         * @param mC  - keyCode to execute on button press
         * @param pR  - portrait resource used to display button
         * @param lR  - landscape resource used to display button
         * @param sR  - smaller scaled resource for side buttons
         */
        ButtonInfo (int rId, int cD, int mC, int pR, int lR, int sR) {
            displayId = rId;
            contentDescription = cD;
            keyCode = mC;
            portResource = pR;
            landResource = lR;
            sideResource = sR;
        }
    }

    private class ButtonAdapter implements ListAdapter {

        /**
         * Already assigned items
         */
        ArrayList<String> takenItems;
        ArrayList<String> items;
        LayoutInflater inflater;

        ButtonAdapter (boolean smallButtons) {
            inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            takenItems = new ArrayList<String>();
            for (int id : mIds) {
                String vTag = (String) mParent.findViewById(id).getTag();
                if (vTag == null || vTag.equals(NAVBAR_EMPTY)) {
                    continue;
                }
                takenItems.add(vTag);
            }
            items = new ArrayList<String>(buttonMap.keySet());
            // home button is not assignable
            items.remove(NAVBAR_HOME);
            // menu buttons can only be assigned to side buttons
            if (!smallButtons) {
                items.remove(NAVBAR_CONDITIONAL_MENU);
                items.remove(NAVBAR_ALWAYS_MENU);
            } else {
                items.remove(NAVBAR_MENU_BIG);
            }
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int arg0) {
            return items.get(arg0);
        }

        @Override
        public long getItemId(int arg0) {
            return 0;
        }

        @Override
        public int getItemViewType(int arg0) {
            return 0;
        }

        @Override
        public View getView(int arg0, View convertView, ViewGroup parent) {
            if(convertView == null) {
                convertView = inflater.inflate(android.R.layout.select_dialog_item, parent,false);
            }
            TextView text = (TextView) convertView.findViewById(android.R.id.text1);
            if (takenItems.contains(items.get(arg0))) {
                text.setBackgroundColor(Color.parseColor("#181818"));
            } else {
                text.setBackground(null);
            }
            text.setText(mParent.getResources().getString(buttonMap.get(items.get(arg0)).displayId));
            return convertView;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public void registerDataSetObserver(DataSetObserver arg0) {
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver arg0) {
        }

        @Override
        public boolean areAllItemsEnabled() {
            return true;
        }

        @Override
        public boolean isEnabled(int arg0) {
            if (takenItems.contains(items.get(arg0))) {
                return false;
            }
            return true;
        }

    }

    final void dismissDialog() {
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

}
