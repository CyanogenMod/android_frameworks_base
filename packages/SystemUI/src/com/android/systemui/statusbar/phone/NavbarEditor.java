package com.android.systemui.statusbar.phone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.provider.Settings;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
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

    ArrayList<Integer> mIds = new ArrayList<Integer>(Arrays.asList(R.id.one, R.id.two, R.id.three, R.id.four, R.id.five,R.id.six));
    //Subset of mIds, to differentiate small/side buttons
    //since they can be assigned additional functionality
    public static final int[] smallButtonIds = {R.id.one, R.id.six};
    public static final LinkedHashMap<String, ButtonInfo> buttonMap = new LinkedHashMap<String,ButtonInfo>();
    ViewGroup mParent;
    AlertDialog mDialog;
    boolean mVertical,mLongPressed;

    static {
        //TODO USE search stuff, volume ..etc
        buttonMap.put("home",
                new ButtonInfo("Home button", R.string.accessibility_home,KeyEvent.KEYCODE_HOME,R.drawable.ic_sysbar_home,
                        R.drawable.ic_sysbar_home_land, R.drawable.ic_sysbar_home));
        buttonMap.put("menu0",
                new ButtonInfo("Menu (autoHide) button" ,R.string.accessibility_menu,KeyEvent.KEYCODE_MENU,R.drawable.ic_sysbar_menu,
                        R.drawable.ic_sysbar_menu_land, R.drawable.ic_sysbar_menu));
        buttonMap.put("menu1",
                new ButtonInfo("Menu (alwaysShow) button" ,R.string.accessibility_menu,KeyEvent.KEYCODE_MENU,R.drawable.ic_sysbar_menu,
                        R.drawable.ic_sysbar_menu_land, R.drawable.ic_sysbar_menu));
        buttonMap.put("back",
                new ButtonInfo("Back button", R.string.accessibility_back,KeyEvent.KEYCODE_BACK,R.drawable.ic_sysbar_back,
                        R.drawable.ic_sysbar_back_land, R.drawable.ic_sysbar_back_side));
        buttonMap.put("search",
                new ButtonInfo("Search button",R.string.accessibility_back,KeyEvent.KEYCODE_SEARCH,R.drawable.ic_sysbar_search,
                        R.drawable.ic_sysbar_search_land, R.drawable.ic_sysbar_search_side));
        buttonMap.put("recent",
                new ButtonInfo("Recent button",R.string.accessibility_recent,0,R.drawable.ic_sysbar_recent,
                        R.drawable.ic_sysbar_recent_land, R.drawable.ic_sysbar_recent_side));
        buttonMap.put("empty",
                new ButtonInfo("Empty button",R.string.accessibility_clear_all,0,R.drawable.ic_sysbar_add,
                        R.drawable.ic_sysbar_add_land, R.drawable.ic_sysbar_add_side));
    }

    public NavbarEditor (ViewGroup parent, Boolean orientation) {
        mParent = parent;
        mVertical = orientation;
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

    Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (NavigationBarView.getEditMode()) {
                mLongPressed = true;
                mParent.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                return;
            }
        }
    };

    @Override
    public boolean onTouch(final View view, MotionEvent event) {
        if (!NavigationBarView.getEditMode() || (mDialog != null && mDialog.isShowing())) {
            return false;
        }
        float curPos = event.getRawX();
        if (mVertical) {
            curPos = event.getRawY();
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int secondLoc[] = new int[2];
            view.getLocationOnScreen(secondLoc);
            mParent.setTag(Float.valueOf(secondLoc[0]));
            if (mVertical) {
                mParent.setTag(Float.valueOf(secondLoc[1]));
            }
            view.postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (!mLongPressed || ArrayUtils.contains(smallButtonIds, view.getId())) {
                return false;
            }
            view.bringToFront();
            float buttonSize = view.getWidth();
            ViewGroup viewParent = (ViewGroup) view.getParent();
            if (mVertical) {
                buttonSize = view.getHeight();
            }
            if (!mVertical && ((curPos)  > (viewParent.getWidth() + viewParent.getLeft()) || (curPos - buttonSize/2 <= viewParent.getLeft()))) {
                return false;
            }
            if (mVertical && ((curPos  > (viewParent.getHeight() + viewParent.getTop())) || (curPos < viewParent.getTop()))) {
                return false;
            }
            int viewPosition = mIds.indexOf(view.getId());
            if (!mVertical) {
                view.setX(curPos - viewParent.getLeft() - buttonSize/2);
            } else {
                view.setY(curPos - viewParent.getTop() - buttonSize/2);
            }
            int affectedViewPosition = findInterceptingViewIndex(curPos, view);
            if (affectedViewPosition == -1) {
                return false;
            }
            switchId(viewPosition, affectedViewPosition, view);
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            view.removeCallbacks(mCheckLongPress);
            if (!mLongPressed && !view.getTag().equals("home")) {
                final ButtonAdapter list = new ButtonAdapter(ArrayUtils.contains(smallButtonIds, view.getId()) ? true : false);
                //Needs to be localized
                AlertDialog.Builder builder = new AlertDialog.Builder(mParent.getContext());
                builder.setTitle("Choose action to assign");
                builder.setAdapter(list, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ((KeyButtonView) view).setInfo(list.getItem(which).toString(), mVertical);
                        view.setPressed(false);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        view.setPressed(false);
                    }
                });
                mDialog = builder.create();
                mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                mDialog.show();
                mLongPressed=false;
                return true;
            }
            mLongPressed=false;
            ViewGroup a = (ViewGroup) view.getParent();
            if (!mVertical) {
                view.setX((Float) mParent.getTag() - a.getLeft());
            } else {
                view.setY((Float) mParent.getTag() - a.getTop());
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
        View secondView = mParent.findViewById(mIds.get(from));
        int secondLoc[] = new int[2];
        secondView.getLocationOnScreen(secondLoc);
        ViewGroup a = (ViewGroup) view.getParent();
        if (!mVertical) {
            secondView.setX((Float) mParent.getTag() - a.getLeft());
            mParent.setTag(Float.valueOf(secondLoc[0]));
        } else {
            secondView.setY((Float) mParent.getTag() - a.getTop());
            mParent.setTag(Float.valueOf(secondLoc[1]));
        }
        Collections.swap(mIds,to,from);
    }

    /**
     * Saves the current key arrangement
     * to the settings provider
     */
    protected void saveKeys() {
        StringBuilder saveValue = new StringBuilder();
        String delim = "";
        ArrayList<Integer> idMap = (ArrayList<Integer>) mIds.clone();
        if (mVertical) Collections.reverse(idMap);
        for (int id : idMap) {
            saveValue.append(delim);
            delim="|";
            saveValue.append(mParent.findViewById(id).getTag());
        }
        Settings.System.putString(mParent.getContext().getContentResolver(), Settings.System.NAV_BUTTONS, saveValue.toString());
    }

    /**
     * Reinflates navigation bar on demand
     * base on current orientation
     */
    protected void reInflate() {
        ((ViewGroup)mParent).removeAllViews();
        if (mVertical) {
            View.inflate(mParent.getContext(), R.layout.mid_navigation_bar_land, (ViewGroup) mParent);
        } else {
            View.inflate(mParent.getContext(), R.layout.mid_navigation_bar_port, (ViewGroup) mParent);
        }
    }

    /**
     * Updates the buttons according to the
     * key arrangement stored in settings provider
     */
    protected void updateKeys() {
        String saved = Settings.System.getString(mParent.getContext().getContentResolver(), Settings.System.NAV_BUTTONS);
        if (saved == null) {
            saved = "empty|back|home|recent|empty|menu0";
        }
        int cc = 0;
        ArrayList<Integer> idMap = (ArrayList<Integer>) mIds.clone();
        if (mVertical) Collections.reverse(idMap);
        for (String buttons : saved.split("\\|")) {
            KeyButtonView curView = (KeyButtonView) mParent.findViewById(idMap.get(cc));
            curView.setInfo(buttons, mVertical);
            if (curView.getVisibility() == View.GONE) {
                adjustPadding(curView);
            }
            cc++;
        }
    }

    /**
     * Accomodates the padding between keys based on
     * number of keys in use.
     */
    private void adjustPadding(KeyButtonView curView) {
        ViewGroup viewParent = (ViewGroup) curView.getParent();
        if (viewParent == mParent.findViewById(R.id.nav_buttons)){
            return;
        } else {
            int viewIndex = viewParent.indexOfChild(curView);
            View nextPadding = viewParent.getChildAt(viewIndex+1); 
            if (nextPadding == null) {
                nextPadding = viewParent.getChildAt(viewIndex - 1);
            }
            nextPadding.setVisibility(View.GONE);
        }
    }

    public static final class ButtonInfo {
        public String displayString;
        public int contentDescription;
        public int mCode;
        public int portResource;
        public int landResource;
        public int sideResource;
        ButtonInfo (String dS, int cD, int mC, int pR, int lR, int sR) {
            displayString = dS;
            contentDescription = cD;
            mCode = mC;
            portResource = pR;
            landResource = lR;
            sideResource = sR;
        }
    }

    private class ButtonAdapter implements ListAdapter {
        ArrayList<String> items;
        ArrayList<String> takenItems;
        LayoutInflater inflater;

        ButtonAdapter (boolean smallButtons) {
            inflater = (LayoutInflater) mParent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            takenItems = new ArrayList<String>();
            for (int id : mIds) {
                if (mParent.findViewById(id).getTag()==null || mParent.findViewById(id).getTag().equals("empty")) {
                    continue;
                }
                takenItems.add(mParent.findViewById(id).getTag().toString());
            }
            items = new ArrayList<String>(buttonMap.keySet());
            items.remove("home");
            if (!smallButtons) {
                items.remove("menu0");
                items.remove("menu1");
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
            if(convertView==null){
                convertView = inflater.inflate(android.R.layout.select_dialog_item, parent,false);
            }
            TextView text = (TextView) convertView.findViewById(android.R.id.text1);
            if (takenItems.contains(items.get(arg0))) {
                text.setBackgroundColor(Color.parseColor("#181818"));
            } else {
                text.setBackgroundDrawable(null);
            }
            text.setText(buttonMap.get(items.get(arg0)).displayString);
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

}
