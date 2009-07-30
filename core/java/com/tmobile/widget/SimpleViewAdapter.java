
package com.tmobile.widget;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.ArrayList;

/**
 * A simple adapter to draw a list of views contained in a ArrayList<view>.
 *
 */
public class SimpleViewAdapter extends BaseAdapter {

    ArrayList<View> mArrayList;

    public SimpleViewAdapter(ArrayList<View> arrayList) {
        super();
        mArrayList = arrayList;
    }

    public int getCount() {
        return mArrayList.size();
    }

    public Object getItem(int position) {
        return mArrayList.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View view = (View)mArrayList.get(position);
        return view;
    }
}
