package com.android.systemui.qs;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.android.systemui.R;

public class QSPage extends ViewGroup {

    private static final String TAG = "QSPage";

    static final float TILE_ASPECT = 1.2f;

    private int mCellWidth;
    private int mCellHeight;
    private int mLargeCellWidth;
    private int mLargeCellHeight;
    private int mGridHeight;

    private QSDragPanel mPanel;

    private int mPage;

    private boolean mAdapterEditingState;

    public QSPage(Context context, QSDragPanel panel, int page) {
        super(context);
        mPanel = panel;
        mPage = page;
        updateResources();
        setClipChildren(false);
        setClipToPadding(false);
        setClipToOutline(false);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
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
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        if (mPanel.mCurrentlyAnimating.isEmpty() && !mPanel.isDragging()) {
            int r = -1;
            int c = -1;
            int rows = 0;
            for (QSPanel.TileRecord ts : mPanel.mRecords) {
                QSDragPanel.DragTileRecord record = (QSDragPanel.DragTileRecord) ts;
                if (record.page != mPage) continue;
                if (record.tileView.getVisibility() == GONE) continue;

                if (mPage == 0 && r == 0 && c == 1 && mPanel.mFirstRowLarge) {
                    r = 1;
                    c = 0;
                } else if (r == -1 || c == (mPanel.getColumnCount() - 1)) {
                    r++;
                    c = 0;
                } else {
                    c++;
                }
                record.row = r;
                record.col = c;
                rows = r + 1;
            }
            mGridHeight = mPanel.getRowTop(rows);
        }

        View previousView = mPanel.getBrightnessView();
        for (QSPanel.TileRecord ts : mPanel.mRecords) {
            QSDragPanel.DragTileRecord record = (QSDragPanel.DragTileRecord) ts;
            if (record.page != mPage) continue;
            if (record.page != record.destinationPage) continue;

            final boolean dual = dualRecord(record);
            if (record.tileView.setDual(dual, record.tile.hasDualTargetsDetails())) {
                record.tileView.handleStateChanged(record.tile.getState());
            }
            if (record.tileView.getVisibility() == GONE) continue;
            final int cw = dual ? mLargeCellWidth : mCellWidth;
            final int ch = dual ? mLargeCellHeight : mCellHeight;
            record.tileView.measure(exactly(cw), exactly(ch));
            previousView = record.tileView.updateAccessibilityOrder(previousView);
        }
        setMeasuredDimension(width, exactly(mGridHeight));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int w = getWidth();
        boolean isRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        for (QSPanel.TileRecord ts : mPanel.mRecords) {
            QSDragPanel.DragTileRecord record = (QSDragPanel.DragTileRecord) ts;
            if (record.page != mPage) continue;
            if (record.page != record.destinationPage) continue;
            if (record.tileView.getVisibility() == GONE) continue;

            final int cols = mPanel.getColumnCount(mPage, record.row);

            int left = mPanel.getLeft(record.row, record.col, cols, dualRecord(record));
            final int top = mPanel.getRowTop(record.row);
            int right;
            int tileWith = record.tileView.getMeasuredWidth();
            if (isRtl) {
                right = w - left;
                left = right - tileWith;
            } else {
                right = left + tileWith;
            }
            if (mPanel.isAnimating(record)) continue;
            if (false) {
                Log.v(TAG + "-" + mPage, "laying out " + record + ", top: " + top + ", left: " + left);
                Log.d(TAG, record + " wiping translations: "
                        + record.tileView.getTranslationX()
                        + ", " + record.tileView.getTranslationY());
            }
            record.tileView.setTranslationX(0);
            record.tileView.setTranslationY(0);

            record.destination.x = record.tileView.getX();
            record.destination.y = record.tileView.getY();

            record.tileView.layout(left, top, right, top + record.tileView.getMeasuredHeight());
        }
    }

    public boolean getAdapterEditingState() {
        return mAdapterEditingState;
    }

    public void setAdapterEditingState(boolean editing) {
        this.mAdapterEditingState = editing;
    }

    public boolean dualRecord(QSPanel.TileRecord record) {
        return mPanel.mFirstRowLarge && record.row == 0 && mPage == 0;
    }

    private static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }
}
