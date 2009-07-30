package com.tmobile.widget;

import com.android.internal.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.SpinnerAdapter;

public class Filmstrip extends Gallery {
    private Drawable.ConstantState mSelector;

    public Filmstrip(Context context) {
        this(context, null);
    }

    public Filmstrip(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.filmstripStyle);
    }

    public Filmstrip(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.AbsListView, defStyle, 0);

        Drawable selector = a.getDrawableWithContext(context, R.styleable.AbsListView_listSelector);
        if (selector != null) {
            setSelector(selector);
        }

        a.recycle();
    }

    public void setSelector(Drawable selector) {
        if (mSelector != selector.getConstantState()) {
            if (mSelector == null) {
                if (getAdapter() != null) {
                    throw new UnsupportedOperationException("Cannot currently set selector after an adapter has been assigned.  Please fix.");
                }
            }
            mSelector = selector.getConstantState();
            requestLayout();
        }
    }

    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        if (mSelector != null && adapter != null) {
            super.setAdapter(new FilmstripAdapter(adapter));
        } else {
            super.setAdapter(adapter);
        }
    }

    private class FilmstripAdapter extends BaseAdapter {
        private final SpinnerAdapter mBase;

        public FilmstripAdapter(SpinnerAdapter adapter) {
            mBase = adapter;
        }
        
        SpinnerAdapter getBaseAdapter() {
            return mBase;
        }

        public int getCount() {
            return mBase.getCount();
        }

        public Object getItem(int position) {
            return mBase.getItem(position);
        }

        public long getItemId(int position) {
            return mBase.getItemId(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View view = mBase.getView(position, convertView, parent);

            if (convertView == null && view.getBackground() == null && mSelector != null) {
                view.setBackgroundDrawable(mSelector.newDrawable());
            }

            return view; 
        }

        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return mBase.getDropDownView(position, convertView, parent);
        }

        public int getItemViewType(int position) {
            return mBase.getItemViewType(position);
        }

        public int getViewTypeCount() {
            return mBase.getViewTypeCount();
        }

        public boolean hasStableIds() {
            return mBase.hasStableIds();
        }

        public boolean isEmpty() {
            return mBase.isEmpty();
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            mBase.registerDataSetObserver(observer);
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            mBase.unregisterDataSetObserver(observer);
        }
    }
}
