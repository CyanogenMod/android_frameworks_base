package com.android.systemui.qs;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by roman on 11/4/15.
 */
public class QSPage extends ViewGroup {

    private static final String TAG = "QSPage";

    static final float TILE_ASPECT = 1.2f;

    private int mColumns;
    private int mCellWidth;
    private int mCellHeight;
    private int mLargeCellWidth;
    private int mLargeCellHeight;
    private int mDualTileUnderlap;
    private int mGridHeight;
    private int mPanelPaddingBottom;
    private boolean mListening;

    private QSDragPanel mPanel;

    private int mPage;


    public QSPage(Context context, QSDragPanel panel, int page) {
        super(context);
        mPanel = panel;
        mPage = page;
        updateResources();
        setClipChildren(false);
        setClipToPadding(false);
        setClipToOutline(false);
    }

    public int getPageIndex() {
        return mPage;
    }

    public void updateResources() {
        final Resources res = mContext.getResources();
        final int columns = Math.max(1, res.getInteger(R.integer.quick_settings_num_columns));
        mCellHeight = res.getDimensionPixelSize(R.dimen.qs_tile_height);
        mCellWidth = (int)(mCellHeight * TILE_ASPECT);
        mLargeCellHeight = res.getDimensionPixelSize(R.dimen.qs_dual_tile_height);
        mLargeCellWidth = (int)(mLargeCellHeight * TILE_ASPECT);
        mDualTileUnderlap = res.getDimensionPixelSize(R.dimen.qs_dual_tile_padding_vertical);
        mPanelPaddingBottom = res.getDimensionPixelSize(R.dimen.qs_panel_padding_bottom);
        if (mColumns != columns) {
            mColumns = columns;
            postInvalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        int r = -1;
        int c = -1;
        int rows = 0;
        boolean rowIsDual = false;
        for (QSPanel.TileRecord ts : mPanel.mRecords) {
            QSDragPanel.DragTileRecord record = (QSDragPanel.DragTileRecord) ts;
            if (record.page != mPage) continue;
            if (record.tileView.getVisibility() == GONE) continue;
            // wrap to next column if we've reached the max # of columns
            // also don't allow dual + single tiles on the same row
            if (r == -1 || c == (mColumns - 1) || rowIsDual != record.tile.supportsDualTargets()) {
                r++;
                c = 0;
                rowIsDual = record.tile.supportsDualTargets();
            } else {
                c++;
            }
            record.row = r;
            record.col = c;
            rows = r + 1;
        }

        View previousView = null;
        for (QSPanel.TileRecord ts : mPanel.mRecords) {
            QSDragPanel.DragTileRecord record = (QSDragPanel.DragTileRecord) ts;
            if (record.page != mPage) continue;
            if (record.tileView.setDual(record.tile.supportsDualTargets())) {
                record.tileView.handleStateChanged(record.tile.getState());
            }
            if (record.tileView.getVisibility() == GONE) continue;
            final int cw = useLargeCellWidth(record) ? mLargeCellWidth : mCellWidth;
            final int ch = useLargeCellWidth(record) ? mLargeCellHeight : mCellHeight;
            record.tileView.measure(exactly(cw), exactly(ch));
            // TODO fixme
//            previousView = record.tileView.updateAccessibilityOrder(previousView);
        }
        int h = mPanel.getRowTop(rows);
        mGridHeight = h;
        setMeasuredDimension(width, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int w = getWidth();
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (QSPanel.TileRecord ts : mPanel.mRecords) {
            QSDragPanel.DragTileRecord record = (QSDragPanel.DragTileRecord) ts;
            if (record.page != mPage) continue;
            if (record.tileView.getVisibility() == GONE) continue;

            final int cols = mPanel.getColumnCount(mPage, record.row);
            int left = mPanel.getLeft(record.row, record.col, cols, useLargeCellWidth(record));
            final int top = mPanel.getRowTop(record.row);
            int right;
            int tileWith = record.tileView.getMeasuredWidth();
            if (isRtl) {
                right = w - left;
                left = right - tileWith;
            } else {
                right = left + tileWith;
            }
            if (true) Log.v(TAG + "-" + mPage, "laying out " + record + ", top: " + top + ", left: " + left);
            record.tileView.layout(left, top, right, top + record.tileView.getMeasuredHeight());
        }
    }

    public boolean useLargeCellWidth(QSPanel.TileRecord record) {
        return record.row == 0 && mPage == 0;
    }

    public int getColumnCount() {
        return mColumns;
    }

    private static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }
}
