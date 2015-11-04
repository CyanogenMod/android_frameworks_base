/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.systemui.qs;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.qs.tiles.EditTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.settings.ToggleSlider;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.tuner.QsTuner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class QSDragPanel extends QSPanel implements View.OnDragListener, View.OnLongClickListener {

    private static final String TAG = "QSDragPanel";

    public static final boolean DEBUG_DRAG = true;

    public static final String ACTION_EDIT_TILES = "com.android.systemui.ACTION_EDIT_TILES";
    public static final String EXTRA_EDIT = "edit";
    public static final String EXTRA_RESET = "reset";

    protected final ArrayList<QSPage> mPages = new ArrayList<>();

    protected QSViewPager mViewPager;
    protected PagerAdapter mPagerAdapter;
    QSPanelTopView mQsPanelTop;

    private DragTileRecord mDraggingRecord;
    private boolean mEditing;
    private boolean mDragging;
    private float mLastTouchLocationX, mLastTouchLocationY;
    private int mLocationHits;
    private int mLastLeftShift = -1;
    private int mLastRightShift = -1;
    private boolean mRestored;
    private boolean mRestoring;
    // whether the current view we are dragging in has shifted tiles
    private boolean mMovedByLocation = false;

    List<TileRecord> mCurrentlyAnimating
            = Collections.synchronizedList(new ArrayList<TileRecord>());
    private Collection<QSTile<?>> mTempTiles = null;

    private BroadcastReceiver mEditReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra(EXTRA_RESET) && intent.getBooleanExtra(EXTRA_RESET, false)) {
                setEditing(false);
                setTiles(mHost.getTiles());
            }
        }
    };

    public QSDragPanel(Context context) {
        this(context, null);
    }

    public QSDragPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setupViews() {
        mDetail = LayoutInflater.from(mContext).inflate(R.layout.qs_detail, this, false);
        mDetailContent = (ViewGroup) mDetail.findViewById(android.R.id.content);
        mDetailSettingsButton = (TextView) mDetail.findViewById(android.R.id.button2);
        mDetailDoneButton = (TextView) mDetail.findViewById(android.R.id.button1);
        updateDetailText();
        mDetail.setVisibility(GONE);
        mDetail.setClickable(true);

        LayoutInflater.from(mContext).inflate(R.layout.qs_tile_top, this, true);
        mQsPanelTop = (QSPanelTopView) findViewById(R.id.qs_panel_top);
        mBrightnessView = mQsPanelTop.getBrightnessView();
        mFooter = new QSFooter(this, mContext);

        // add target click listener
        mQsPanelTop.findViewById(R.id.add_target).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAddDialog();
                    }
                });
        mViewPager = new QSViewPager(getContext());

        addView(mDetail);
        addView(mViewPager);
        addView(mFooter.getView());

        mClipper = new QSDetailClipper(mDetail);

        mBrightnessController = new BrightnessController(getContext(),
                (ImageView) mQsPanelTop.getBrightnessView().findViewById(R.id.brightness_icon),
                (ToggleSlider) mQsPanelTop.getBrightnessView().findViewById(R.id.brightness_slider));

        mDetailDoneButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                announceForAccessibility(
                        mContext.getString(R.string.accessibility_desc_quick_settings));
                closeDetail();
            }
        });

        mPagerAdapter = new PagerAdapter() {
            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                if (DEBUG_DRAG) {
                    Log.d(TAG, "instantiateItem() called with "
                            + "container = [" + container + "], position = [" + position + "]");
                }
                QSPage page = new QSPage(container.getContext(), QSDragPanel.this, position);
                LayoutParams params =
                        new LayoutParams(LayoutParams.MATCH_PARENT,
                                LayoutParams.FILL_PARENT);
                container.addView(page, params);
                mPages.add(page);
                return page;
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                if (DEBUG_DRAG) {
                    Log.d(TAG, "destroyItem() called with " + "container = ["
                            + container + "], position = [" + position + "], object = ["
                            + object + "]");
                }
                if (object instanceof View) {
                    mPages.remove(object);
                    container.removeView((View) object);
                }
            }

            @Override
            public int getCount() {
                return Math.max(getCurrentMaxPageCount(), 1);
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }
        };
        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset,
                                       int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                if (mDragging && position != mDraggingRecord.page
                        && !mViewPager.isFakeDragging() && !mRestoring) {
                    Log.w(TAG, "moving drag record to page: " + position);

                    // remove it from the previous page and add it here
                    final QSPage sourceP = getPage(mDraggingRecord.page);
                    final QSPage targetP = getPage(position);

                    sourceP.removeView(mDraggingRecord.tileView);
                    mDraggingRecord.page = position;
                    targetP.addView(mDraggingRecord.tileView);

                    // set coords off screen until we're ready to place it
                    mDraggingRecord.tileView.setX(-mDraggingRecord.tileView.getMeasuredWidth());
                    mDraggingRecord.tileView.setY(-mDraggingRecord.tileView.getMeasuredHeight());
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setCurrentItem(0);

        setClipChildren(false);
        updateResources();

        mViewPager.setOnDragListener(this);
        mQsPanelTop.getBrightnessView().setOnDragListener(this);
        mQsPanelTop.getDropTarget().setOnDragListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mContext.registerReceiver(mEditReceiver, new IntentFilter(ACTION_EDIT_TILES));
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContext.unregisterReceiver(mEditReceiver);
    }

    protected void drawTile(TileRecord r, QSTile.State state) {
        final int visibility = state.visible || mEditing ? VISIBLE : GONE;
        setTileVisibility(r.tileView, visibility);
        r.tileView.onStateChanged(state);
    }

    public void setEditing(boolean editing) {
        if (mEditing == editing) return;
        mEditing = editing;

        if (!editing) {
            // persist the new config.
            List<String> newTiles = new ArrayList<>();
            for (TileRecord record : mRecords) {
                newTiles.add(mHost.getSpec(record.tile));
            }
            mHost.setTiles(newTiles);

            refreshAllTiles();
        }

        // clear the record state
        for (TileRecord record : mRecords) {
            setupRecord(record);
            drawTile(record, record.tile.getState());
        }
        mQsPanelTop.setEditing(editing);
        ensurePagerState();
        requestLayout();
    }

    protected void onStartDrag() {
        mQsPanelTop.onStartDrag();
    }

    protected void onStopDrag() {
        //mDraggingRecord.tileView.setVisibility(View.VISIBLE);
        mDraggingRecord.tileView.setAlpha(1f);

        mDraggingRecord = null;
        mDragging = false;
        mRestored = false;

        mLastLeftShift = -1;
        mLastRightShift = -1;

        mQsPanelTop.onStopDrag();

        requestLayout();
    }

    protected View getDropTarget() {
        return mQsPanelTop.getDropTarget();
    }

    public View getBrightnessView() {
        return mQsPanelTop.getBrightnessView();
    }

    public boolean isEditing() {
        return mEditing;
    }

    protected int getPagesForCount(int size) {
        return (int) Math.ceil(size / (double) getTilesPerPage());
    }

    protected int getCurrentMaxPageCount() {
        int initialSize = mRecords.size();
        if (mTempTiles != null) {
            return getPagesForCount(initialSize + mTempTiles.size());
        }
        return getPagesForCount(initialSize);
    }

    /**
     * @return returns the number of pages that has at least 1 visible tile
     */
    protected int getVisibleTilePageCount() {
        // if all tiles are invisible on the page, do not count it
        int pages = 0;

        int lastPage = -1;
        boolean allTilesInvisible = true;

        for (TileRecord record : mRecords) {
            DragTileRecord dr = (DragTileRecord) record;
            if (dr.destinationPage != lastPage) {
                if (!allTilesInvisible) {
                    pages++;
                }
                lastPage = dr.destinationPage;
                allTilesInvisible = true;
            }
            if (allTilesInvisible && dr.tile.getState().visible) {
                allTilesInvisible = false;
            }
        }
        // last tile may have set this
        if (!allTilesInvisible) {
            pages++;
        }
        return pages;
    }

    public void setTiles(Collection<QSTile<?>> tiles) {
        if (DEBUG_DRAG) {
            Log.i(TAG, "setTiles() called with " + "tiles = ["
                    + tiles + "], mTempTiles: " + mTempTiles);
            if (mTempTiles != null) {
                Log.e(TAG, "temp tiles being overridden... : " +
                        Arrays.toString(mTempTiles.toArray()));
            }
        }
        for (Record record : mRecords) {
            if (record instanceof DragTileRecord) {
                DragTileRecord dr = (DragTileRecord) record;
                mPages.get(dr.page).removeView(dr.tileView);
            }
        }
        mRecords.clear();
        if (isLaidOut()) {
            for (QSTile<?> tile : tiles) {
                addTile(tile);
            }
            if (isShowingDetail()) {
                mDetail.bringToFront();
            }
        } else if (!isLaidOut()) {
            if (DEBUG_DRAG) {
                Log.w(TAG, "setting temporary tiles to layout");
            }
            mTempTiles = Collections.synchronizedCollection(new ArrayList<QSTile<?>>(tiles));
        }
        mPagerAdapter.notifyDataSetChanged();
        requestLayout();
        ensurePagerState();
    }

    protected void addTile(final QSTile<?> tile) {
        if (DEBUG_DRAG) {
            Log.d(TAG, "+++ addTile() called with " + "tile = [" + tile + "]");
        }
        final DragTileRecord r = new DragTileRecord();

        mRecords.add(r);
        mPagerAdapter.notifyDataSetChanged();

        int potentialPageIdx = getPagesForCount(mRecords.size()) - 1;

        r.tile = tile;
        r.page = potentialPageIdx;
        r.destinationPage = r.page;
        r.tileView = tile.createTileView(mContext);
        r.tileView.setVisibility(View.GONE);
        final QSTile.Callback callback = new QSTile.Callback() {
            @Override
            public void onStateChanged(QSTile.State state) {
                if (!r.openingDetail) {
                    drawTile(r, state);
                }
            }

            @Override
            public void onShowDetail(boolean show) {
                showDetail(show, r);
            }

            @Override
            public void onToggleStateChanged(boolean state) {
                if (mDetailRecord == r) {
                    fireToggleStateChanged(state);
                }
            }

            @Override
            public void onScanStateChanged(boolean state) {
                r.scanState = state;
                if (mDetailRecord == r) {
                    fireScanStateChanged(r.scanState);
                }
            }

            @Override
            public void onAnnouncementRequested(CharSequence announcement) {
                announceForAccessibility(announcement);
            }
        };
        r.tile.setCallback(callback);
        final OnClickListener click = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mEditing || r.tile instanceof EditTile) {
                    r.tile.click();
                }
            }
        };
        final OnClickListener clickSecondary = new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mEditing) {
                    r.tile.secondaryClick();
                }
            }
        };
        final OnLongClickListener longClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (!mEditing) {
                    r.tile.longClick();
                } else {
                    QSDragPanel.this.onLongClick(r.tileView);
                }
                return true;
            }
        };
        r.tileView.init(click, clickSecondary, longClick);
        r.tile.setListening(mListening);
        r.tile.refreshState();
        if (mEditing) {
            // force it to be visible, we'll refresh its state once editing is done
            r.tile.getState().visible = true;
        }
        callback.onStateChanged(r.tile.getState());

        mPages.get(r.page).addView(r.tileView);

        ensurePagerState();

        if (DEBUG_DRAG) {
            Log.d(TAG, "--- addTile() called with " + "tile = [" + tile + "]");
        }
    }

    public void ensurePagerState() {
        final boolean pagingEnabled = getVisibleTilePageCount() > 1 || mDragging;
        mViewPager.setPagingEnabled(pagingEnabled);
    }

    public int getTilesPerPage() {
        return 8;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (DEBUG_DRAG) Log.d(TAG, "onMeasure()");

        final int width = MeasureSpec.getSize(widthMeasureSpec);

        mQsPanelTop.measure(exactly(width), MeasureSpec.UNSPECIFIED);

        final int brightnessHeight = mQsPanelTop.getMeasuredHeight();

        mFooter.getView().measure(exactly(width), MeasureSpec.UNSPECIFIED);

        mViewPager.measure(exactly(width), MeasureSpec.UNSPECIFIED);

        int h = brightnessHeight + mViewPager.getMeasuredHeight();
        if (mFooter.hasFooter()) {
            h += mFooter.getView().getMeasuredHeight();
        }
        mDetail.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        if (mDetail.getMeasuredHeight() < h) {
            mDetail.measure(exactly(width), exactly(h));
        }
        mGridHeight = h;
        setMeasuredDimension(width, Math.max(h, mDetail.getMeasuredHeight()));

        for (TileRecord record : mRecords) {
            setupRecord(record);
        }
    }

    private void setupRecord(TileRecord record) {
        record.tileView.setEditing(mEditing);
        record.tileView.setOnDragListener(mEditing ? this : null);
    }

    public static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (DEBUG_DRAG) Log.d(TAG, "onLayout()");
        final int w = getWidth();

        mQsPanelTop.layout(0, t, mQsPanelTop.getMeasuredWidth(),
                t + mQsPanelTop.getMeasuredHeight());

        final int dh = Math.max(mDetail.getMeasuredHeight(), mViewPager.getMeasuredHeight());
        mViewPager.layout(0, t + mQsPanelTop.getMeasuredHeight(), w, t + dh);
        mDetail.layout(0, t, mDetail.getMeasuredWidth(), t + dh);
        if (mFooter.hasFooter()) {
            View footer = mFooter.getView();
            footer.layout(0, getMeasuredHeight() - footer.getMeasuredHeight(),
                    footer.getMeasuredWidth(), getMeasuredHeight());
        }

        if (mTempTiles != null) {
            final Iterator<QSTile<?>> iterator = mTempTiles.iterator();
            while (iterator.hasNext()) {
                addTile(iterator.next());
                iterator.remove();
            }
            mTempTiles = null;
            mPagerAdapter.notifyDataSetChanged();
        }
        ensurePagerState();
    }

    protected int getRowTop(int row) {
        if (row <= 0) return 0;
        return mLargeCellHeight - mDualTileUnderlap + (row - 1) * mCellHeight;
    }

    public int getColumnCount() {
        return mColumns;
    }

    public int getColumnCount(int page, int row) {
        int cols = 0;
        for (Record record : mRecords) {
            if (record instanceof DragTileRecord) {
                DragTileRecord dr = (DragTileRecord) record;
                if (dr.tileView.getVisibility() == GONE) continue;
                if (dr.destinationPage != page) continue;
                if (dr.row == row) cols++;
            }
        }

        if (isEditing() && (isDragging() || mRestoring) && !isDragRecordAttached()) {
            // if shifting tiles back, and one moved from previous page

            // if it's the very last row on the last page, we should add an extra column to account
            // for where teh dragging record would go
            DragTileRecord record = (DragTileRecord) mRecords.get(mRecords.size() - 1);

            if (record.destinationPage == page && record.row == row && cols < getColumnCount()) {
                cols++;
                if (DEBUG_DRAG) {
                    Log.w(TAG, "adding another col, cols: " + cols + ", last: " + record
                            + ", drag: " + mDraggingRecord + ", ");
                }
            }
        }
        return cols;
    }

    public int getMaxRows() {
        int max = 0;
        for (TileRecord record : mRecords) {
            if (record.row > max) {
                max = record.row;
            }
        }
        return max;
    }

    public int getLeft(int page, int row, int col) {
        final boolean firstRow = page == 0 && row == 0;
        int cols = firstRow ? 2 : mColumns;
        return getLeft(row, col, cols, firstRow);
    }

    public int getLeft(int page, int row, int col, int cols) {
        final boolean firstRow = page == 0 && row == 0;
        return getLeft(row, col, cols, firstRow);
    }

    public int getLeft(int row, int col, int cols, boolean firstRowLarge) {
        final int cw = row == 0 && firstRowLarge ? mLargeCellWidth : mCellWidth;
        final int extra = (getWidth() - cw * cols) / (cols + 1);
        int left = col * cw + (col + 1) * extra;
        return left;
    }

    public QSPage getCurrentPage() {
        return mPages.get(mViewPager.getCurrentItem());
    }

    public QSPage getPage(int pos) {
        if (pos >= mPages.size()) {
            return null;
        }
        return mPages.get(pos);
    }

    private TileRecord getRecord(View v) {
        for (TileRecord record : mRecords) {
            if (record.tileView == v) {
                return record;
            }
        }
        return null;
    }

    @Override
    public boolean onDrag(View v, DragEvent event) {
        final DragTileRecord targetTile = (DragTileRecord) getRecord(v);
        boolean originatingTileEvent = mDraggingRecord != null && v == mDraggingRecord.tileView;

        final int dragRecordIndex = mRecords.indexOf(mDraggingRecord);
        boolean dragRecordAttached = dragRecordIndex != -1;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                if (originatingTileEvent) {
                    if (DEBUG_DRAG) {
                        Log.v(TAG, "ACTION_DRAG_STARTED on target view.");
                    }
                    mRestored = false;
                }
                return true;

            case DragEvent.ACTION_DRAG_ENTERED:
                if (DEBUG_DRAG) {
                    Log.v(TAG, "ACTION_DRAG_ENTERED on view with tile: " + targetTile);
                }
                mLocationHits = 0;
                mMovedByLocation = false;

                if (!originatingTileEvent && v != getDropTarget() && targetTile != null) {
                    if (DEBUG_DRAG) {
                        Log.e(TAG, "entered tile " + targetTile);
                    }
                    if (mCurrentlyAnimating.isEmpty()
                            && !mViewPager.isFakeDragging()
                            && !dragRecordAttached) {
                        mMovedByLocation = true;
                        shiftTiles(targetTile, true);
                    } else {
                        if (DEBUG_DRAG) {
                            Log.w(TAG, "ignoring action enter for animating tiles and fake drags");
                        }
                    }
                }

                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                if (DEBUG_DRAG) {
                    Log.v(TAG, "ACTION_DRAG_ENDED on view: " + v + "(tile: "
                            + targetTile + "), result: " + event.getResult());
                }
                if (originatingTileEvent && !event.getResult()) {
                    // view pager probably ate the event
                    restoreDraggingTilePosition(v);
                }

                return true;

            case DragEvent.ACTION_DROP:
                if (DEBUG_DRAG) {
                    Log.v(TAG, "ACTION_DROP, event loc: " + event.getX() + ", " + event.getY()
                            + " + with tile: " + targetTile + " and view: " + v);
                }
                mLastTouchLocationX = event.getX();
                mLastTouchLocationY = event.getY();

                if (v == getDropTarget()) {
                    if (DEBUG_DRAG) {
                        Log.d(TAG, "dropping on delete target!!");
                    }
                    if (mDraggingRecord.tile instanceof EditTile) {
                        restoreDraggingTilePosition(v);
                        mQsPanelTop.toast(R.string.quick_settings_cannot_delete_edit_tile);
                        return true;
                    } else {
                        mRestored = true;
                        getPage(mDraggingRecord.page).removeView(mDraggingRecord.tileView);

                        // what spec is this tile?
                        String spec = mHost.getSpec(mDraggingRecord.tile);
                        if (DEBUG_DRAG) {
                            Log.w(TAG, "removing tile: " + mDraggingRecord + " with spec: " + spec);
                        }
                        mHost.remove(spec);
                        onStopDrag();
                    }
                } else {
                    restoreDraggingTilePosition(v);
                }
                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                if (DEBUG_DRAG) {
                    if (targetTile != null) {
                        Log.v(TAG, "ACTION_DRAG_EXITED on view with tile: " + targetTile);
                    } else {
                        Log.v(TAG, "ACTION_DRAG_EXITED on view: " + v);
                    }
                }
                if (originatingTileEvent
                        && mCurrentlyAnimating.isEmpty()
                        && !mViewPager.isFakeDragging()
                        && dragRecordAttached
                        && mLastLeftShift == -1) {

                    if (DEBUG_DRAG) {
                        Log.v(TAG, "target: " + targetTile + ", hit mLastRightShift: "
                                + mLastRightShift + ", mLastLeftShift: "
                                + mLastLeftShift + ", dragRecordIndex: "
                                + dragRecordIndex);
                    }

                    // move tiles back
                    shiftTiles(mDraggingRecord, false);
                    return true;
                }
                // fall through so exit events can trigger a left shift
            case DragEvent.ACTION_DRAG_LOCATION:
                mLastTouchLocationX = event.getX();
                mLastTouchLocationY = event.getY();

                // do nothing if we're animating tiles
                if (mCurrentlyAnimating.isEmpty() && !mViewPager.isFakeDragging()) {
                    if (v == mViewPager) {
                        // do we need to change pages?
                        int x = (int) event.getX();
                        int width = mViewPager.getWidth();
                        int scrollPadding = (int) (width * QSViewPager.SCROLL_PERCENT);
                        if (x < scrollPadding) {
                            if (mViewPager.canScrollHorizontally(-1)) {
                                mViewPager.animatePagerTransition(false);
                                return true;
                            }
                        } else if (x > width - scrollPadding) {
                            if (mViewPager.canScrollHorizontally(1)) {
                                mViewPager.animatePagerTransition(true);
                                return true;
                            }
                        }
                    }
                    if (DEBUG_DRAG) {
                        Log.v(TAG, "location hit:// target: " + targetTile
                                + ", hit mLastRightShift: " + mLastRightShift
                                + ", mLastLeftShift: " + mLastLeftShift
                                + ", dragRecordIndex: " + dragRecordIndex
                                + ", originatingTileEvent: " + originatingTileEvent
                                + ", mLocationHits: " + mLocationHits
                                + ", mMovedByLocation: " + mMovedByLocation);
                    }

                    if (v != getDropTarget() && targetTile != null && !dragRecordAttached) {
                        // dragging around on another tile
                        if (mLocationHits++ == 30) {
                            if (DEBUG_DRAG) {
                                Log.w(TAG, "shifting right due to location hits.");
                            }
                            // add dragging tile to current page
                            shiftTiles(targetTile, true);
                            mMovedByLocation = true;
                        } else {
                            mLocationHits++;
                        }
                    } else if (mLastRightShift != -1 // right has shifted recently
                            && mLastLeftShift == -1 // -1 means its attached
                            && dragRecordIndex == mLastRightShift
                            && !originatingTileEvent
                            && !mMovedByLocation /* helps avoid continuous shifting */) {
                        // check if the location is on another tile/view
                        // that is not the last drag index, shift back left to revert back and
                        // potentially get ready for shifting right
                        if (DEBUG_DRAG) {
                            Log.w(TAG, "conditions met to reverse!!!! shifting left. <<<<<<<");
                        }
                        shiftTiles((DragTileRecord) mRecords.get(mLastRightShift), false);
                        mMovedByLocation = true;
                    }
                } else {
                    if (DEBUG_DRAG) {
                        Log.i(TAG, "ignoring location event because things are animating, size: "
                                + mCurrentlyAnimating.size());
                    }
                }
                return true;
            default:
                Log.w(TAG, "unhandled event");
        }
        return false;
    }

    private void restoreDraggingTilePosition(View v) {
        if (mRestored) {
            return;
        }
        mRestored = true;
        mRestoring = true;
        mCurrentlyAnimating.add(mDraggingRecord);

        if (DEBUG_DRAG) {
            Log.i(TAG, "restoreDraggingTilePosition() called with "
                    + "v = [" + (v.getTag() != null ? v.getTag() : v) + "]");
        }
        final boolean dragRecordDetached = mRecords.indexOf(mDraggingRecord) == -1;

        if (DEBUG_DRAG) {
            Log.v(TAG, "mLastLeftShift: " + mLastLeftShift
                    + ", detached: " + dragRecordDetached + ", drag record: " + mDraggingRecord);
        }

        final QSPage originalPage = getPage(mDraggingRecord.page);
        originalPage.removeView(mDraggingRecord.tileView);
        addTransientView(mDraggingRecord.tileView, 0);
        mDraggingRecord.tileView.setTransitionVisibility(View.VISIBLE);

        mLastTouchLocationY += mViewPager.getTop(); // we are in the threshold of the viewpager meow

        // need to move center of the dragging view to the coords of the event.
        final float touchEventBoxLeft = v.getX()
                + (mLastTouchLocationX - (mDraggingRecord.tileView.getWidth() / 2));
        final float touchEventBoxTop = v.getY()
                + (mLastTouchLocationY - (mDraggingRecord.tileView.getHeight() / 2));

        mDraggingRecord.tileView.setX(touchEventBoxLeft);
        mDraggingRecord.tileView.setY(touchEventBoxTop);

        if (dragRecordDetached) {
            setToLastDestination(mDraggingRecord);
            if (DEBUG_DRAG) {
                Log.d(TAG, "setting drag record view to coords: x:" + touchEventBoxLeft
                        + ", y:" + touchEventBoxTop);
                Log.d(TAG, "animating drag record to: " + mDraggingRecord + ", loc: "
                        + mDraggingRecord.destination);
            }
        } else {
            mDraggingRecord.destination.x = getLeft(mDraggingRecord.destinationPage,
                    mDraggingRecord.row, mDraggingRecord.col,
                    getColumnCount(mDraggingRecord.destinationPage, mDraggingRecord.row));

            mDraggingRecord.destination.y = getRowTop(mDraggingRecord.row);
        }

        // setup x destination to animate to
        float destinationX = mDraggingRecord.destination.x;
        if (mDraggingRecord.destinationPage > mViewPager.getCurrentItem()) {
            destinationX += getWidth();
        } else if (mDraggingRecord.destinationPage < mViewPager.getCurrentItem()) {
            destinationX -= getWidth();
        }

        // setup y
        float destinationY = mDraggingRecord.destination.y + mViewPager.getTop();

        mDraggingRecord.tileView.animate()
                .x(destinationX)
                .y(destinationY) // part of the viewpager now
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        mDraggingRecord.tileView.setAlpha(1);
                        mDraggingRecord.tileView.setBackground(
                                mDraggingRecord.tileView.newTileBackground());
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mViewPager.requestDisallowInterceptTouchEvent(false);

                        removeTransientView(mDraggingRecord.tileView);

                        final QSPage targetP = getPage(mDraggingRecord.destinationPage);

                        if (dragRecordDetached) {
                            Log.i(TAG, "drag record was detached");

                        } else {
                            Log.i(TAG, "drag record was attached");
                        }
                        mDraggingRecord.page = mDraggingRecord.destinationPage;
                        targetP.addView(mDraggingRecord.tileView);

                        mDraggingRecord.tileView.setX(mDraggingRecord.destination.x);
                        // reset this to be in the coords of the page, not viewpager anymore
                        mDraggingRecord.tileView.setY(mDraggingRecord.destination.y);

                        mCurrentlyAnimating.remove(mDraggingRecord);

                        mRestoring = false;

                        if (dragRecordDetached) {
                            mRecords.add(mDraggingRecord);
                            mPagerAdapter.notifyDataSetChanged();
                        }
                        onStopDrag();
                    }
                });
    }

    private void setToNextDestination(DragTileRecord tile) {
        if (DEBUG_DRAG) {
            Log.i(TAG, "+++setToNextDestination() called with " + "tile = [" + tile + "], at: "
                    + tile.destination);
        }
        tile.col++;
        int maxCols = getColumnCount();

        if (tile.col >= maxCols) {
            tile.col = 0;
            tile.row++;
            if (DEBUG_DRAG) {
                Log.w(TAG, "reached max column count, shifting to next row: " + tile.row);
            }
        }

        int maxRows = getMaxRows();

        if (tile.row >= maxRows) {
            tile.destinationPage = tile.destinationPage + 1;
            tile.row = 0;
            tile.col = 0;

            if (DEBUG_DRAG) {
                Log.w(TAG, "tile's destination page moved to: " + tile.destinationPage);
            }
        }
        int columnCount = Math.max(1, getColumnCount(tile.destinationPage, tile.row));
        if (DEBUG_DRAG) {
            Log.w(TAG, "columCount initially at: " + columnCount);
        }

        if (!mRecords.contains(tile) && tile != mDraggingRecord) {
            // increase column count for the destination location to account for this tile being added
            columnCount++;
            if (DEBUG_DRAG) {
                Log.w(TAG, "column count adjusted to: " + columnCount);
            }
        }
        boolean firstRowLarge = tile.row == 0 && tile.destinationPage == 0;

        tile.destination.x = getLeft(tile.row, tile.col, columnCount, firstRowLarge);
        tile.destination.y = getRowTop(tile.row);

        if (DEBUG_DRAG) {
            Log.i(TAG, "---setToNextDestination() called with " + "tile = [" + tile + "], now at: "
                    + tile.destination);
        }
    }

    private void setToLastDestination(DragTileRecord record) {
        DragTileRecord last = (DragTileRecord) mRecords.get(mRecords.size() - 1);
        Log.d(TAG, "setToLastDestination() called with record = ["
                + record + "], and last record is: " + last);
        if (record != last && record.destinationPage <= last.destinationPage) {
            record.destinationPage = last.destinationPage;
            record.row = last.row;
            record.col = last.col;
            record.destination.x = last.destination.x;
            record.destination.y = last.destination.y;
            setToNextDestination(record);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        final DragTileRecord record = (DragTileRecord) getRecord(v);
        if (record == null) {
            // TODO couldn't find a matching tag?
            Log.e(TAG, "got a null record on touh down.");
            return false;
        }

        mDraggingRecord = record;

        mDraggingRecord.tileView.setAlpha(0);
        mDraggingRecord.tileView.setTileBackground(null);
        mDraggingRecord.tileView.setDual(false);
        TileShadow mTileShadow = new TileShadow(mDraggingRecord.tileView);

        v.startDrag(null, mTileShadow, null, 0);

        mViewPager.requestDisallowInterceptTouchEvent(true);

        onStartDrag();
        mDragging = true;
        return true;
    }

    private void shiftTiles(DragTileRecord startingTile, boolean forward) {
        if (DEBUG_DRAG) {
            Log.i(TAG, "shiftTiles() called with " + "startingTile = [" + startingTile
                    + "], forward = [" + forward + "]");
        }

        if (forward) {
            // startingTile and all after will need to be shifted one to the right
            // dragging tile needs room

            final int destP = startingTile.destinationPage;
            final int rowF = startingTile.row;
            final int colF = startingTile.col;
            PointF loc = new PointF(startingTile.destination.x, startingTile.destination.y);

            // the index of the original position of the statingTile before it moved
            int startingIndex = mRecords.indexOf(startingTile);
            mLastRightShift = startingIndex;
            mLastLeftShift = -1;

            shiftAllTilesRight(startingIndex);
            mRecords.add(startingIndex, mDraggingRecord);

            mPagerAdapter.notifyDataSetChanged();

            mDraggingRecord.col = colF;
            mDraggingRecord.row = rowF;
            mDraggingRecord.destination = loc;
            mDraggingRecord.destinationPage = destP;

            mDraggingRecord.tileView.setX(mDraggingRecord.destination.x);
            mDraggingRecord.tileView.setY(mDraggingRecord.destination.y);

        } else {
            // it is also probably the dragging tile
            final int startingIndex = mRecords.indexOf(startingTile);
            mLastLeftShift = startingIndex;
            mLastRightShift = -1;

            final int draggingIndex = mRecords.indexOf(mDraggingRecord);

            if (startingIndex != draggingIndex) {
                if (DEBUG_DRAG) {
                    Log.e(TAG, "startinIndex: " + startingIndex + ", draggingIndex: "
                            + draggingIndex + ", and they differ!!!!");
                }
            }

            // startingTile should be the "empty" tile that things should start shifting into
            shiftAllTilesLeft(startingIndex);

            // remove the dragging record
            if (mRecords.remove(mDraggingRecord)) {
                mPagerAdapter.notifyDataSetChanged();
                if (DEBUG_DRAG) {
                    Log.v(TAG, "removed dragging record after moving tiles back");
                }
            }

            // set coords off screen until we're ready to place it
            mDraggingRecord.tileView.setX(-mDraggingRecord.tileView.getMeasuredWidth());
            mDraggingRecord.tileView.setY(-mDraggingRecord.tileView.getMeasuredHeight());
        }

        mViewPager.getAdapter().notifyDataSetChanged();
    }

    private void shiftAllTilesRight(int startingIndex) {
        int desiredColumnCount = -1;
        for (int j = startingIndex; j < mRecords.size() - 1; j++) {
            final DragTileRecord ti = (DragTileRecord) mRecords.get(j);
            final DragTileRecord tnext = (DragTileRecord) mRecords.get(j + 1);

            mCurrentlyAnimating.add(ti);
            if (tnext.row != ti.row || desiredColumnCount == -1) {
                desiredColumnCount = getColumnCount(tnext.destinationPage, tnext.row);
                //Log.w(TAG, "updated desiredColumnCount: " + desiredColumnCount);
            }

            if (DEBUG_DRAG) {
                Log.v(TAG, "moving " + ti + " to page " + tnext.destinationPage + ", at coords: "
                        + tnext.row + ", col: " + tnext.col + ", dest: " + tnext.destination);
            }

            ti.row = tnext.row;
            ti.col = tnext.col;
            ti.destination.x = getLeft(tnext.destinationPage, ti.row, ti.col, desiredColumnCount);
//            ti.destination.x = getLeft(ti.row, ti.col, desiredColumnCount,
//                    tnext.destinationPage == 0 && ti.row == 0);
            ti.destination.y = getRowTop(ti.row);

            if (ti.destinationPage != tnext.destinationPage) {
                ti.destinationPage = tnext.destinationPage;

                final QSPage tilePageSource = getPage(ti.page);
                final QSPage tilePageTarget = getPage(ti.destinationPage);
                tilePageSource.removeView(ti.tileView);

                tilePageSource.addTransientView(ti.tileView, 0);
                ti.tileView.animate()
                        .x(ti.destination.x + getWidth())
                        .y(ti.destination.y)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                tilePageSource.removeTransientView(ti.tileView);
                                ti.page = tilePageTarget.getPageIndex();
                                tilePageTarget.addView(ti.tileView);
                                ti.tileView.setX(ti.destination.x);
                                ti.tileView.setY(ti.destination.y);

                                mCurrentlyAnimating.remove(ti);
                                requestLayout();
                            }
                        });

            } else {
                ti.tileView.animate()
                        .x(ti.destination.x)
                        .y(ti.destination.y)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mCurrentlyAnimating.remove(ti);
                            }
                        });
            }
        }

        // need to do last tile manually
        final DragTileRecord last = (DragTileRecord) mRecords.get(mRecords.size() - 1);
        mCurrentlyAnimating.add(last);

        if (DEBUG_DRAG) {
            Log.i(TAG, "last tile shifting to the right: " + last);
        }
        setToNextDestination(last);
        if (last.page != last.destinationPage) {
            final QSPage tilePageSource = getPage(last.page);
            final QSPage tilePageTarget = getPage(last.destinationPage);
            tilePageSource.removeView(last.tileView);
            tilePageSource.addTransientView(last.tileView, 0);

            last.tileView.animate()
                    .x(last.destination.x + getWidth())
                    .y(last.destination.y)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            tilePageSource.removeTransientView(last.tileView);
                            last.page = tilePageTarget.getPageIndex();
                            tilePageTarget.addView(last.tileView);
                            last.tileView.setX(last.destination.x);
                            last.tileView.setY(last.destination.y);

                            if (DEBUG_DRAG) {
                                Log.i(TAG, "page shift finished: " + last);
                            }

                            mCurrentlyAnimating.remove(last);
                            requestLayout();
                        }
                    });
        } else {
            last.tileView.animate()
                    .x(last.destination.x)
                    .y(last.destination.y)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (DEBUG_DRAG) {
                                Log.i(TAG, "shift finished: " + last);
                            }

                            mCurrentlyAnimating.remove(last);
                        }
                    });
        }
    }

    private void shiftAllTilesLeft(int startingIndex) {
        DragTileRecord startingTile = (DragTileRecord) mRecords.get(startingIndex);

        final PointF lastLocation = new PointF(startingTile.destination.x,
                startingTile.destination.y);
        PointF reallyTempLoc = new PointF();
        int lastRow = startingTile.row, lastCol = startingTile.col, tempRow,
                tempCol, lastPage = startingTile.destinationPage, tempPage;

        int desiredColCount = getColumnCount(startingTile.destinationPage, startingTile.row);
        for (int j = startingIndex + 1; j < mRecords.size(); j++) {
            final DragTileRecord ti = (DragTileRecord) mRecords.get(j);

            mCurrentlyAnimating.add(ti);

            if (DEBUG_DRAG) {
                Log.v(TAG, "moving " + ti + " to " + lastPage + ", at coords: "
                        + lastRow + ", col: " + lastCol);
                Log.i(TAG, "and will have desiredColCount: " + desiredColCount);
            }

            final int columnCountF = desiredColCount;

            if (ti.row != lastRow) {
                desiredColCount = getColumnCount(ti.destinationPage, ti.row);
                Log.e(TAG, "updating desired colum count to: " + desiredColCount);
            }

            // save current tile's loc
            reallyTempLoc.x = ti.destination.x;
            reallyTempLoc.y = ti.destination.y;

            tempRow = ti.row;
            tempCol = ti.col;
            tempPage = ti.destinationPage;

            ti.row = lastRow;
            ti.col = lastCol;

            ti.destination.x = getLeft(lastRow, lastCol, columnCountF,
                    lastPage == 0 && lastRow == 0);
            ti.destination.y = getRowTop(lastRow);

            final boolean dual = getPage(ti.destinationPage).dualRecord(ti);

            if (ti.destinationPage != lastPage) {
                ti.destinationPage = lastPage;

                ti.tileView.setX(reallyTempLoc.x + getWidth());
                ti.tileView.setY(reallyTempLoc.y);

                final QSPage originalPage = getPage(ti.page);
                final QSPage page = getPage(lastPage);

                originalPage.removeView(ti.tileView);

                ti.tileView.animate()
                        .x(ti.destination.x)
                        .y(ti.destination.y)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                page.addTransientView(ti.tileView, 0);
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                page.removeTransientView(ti.tileView);
                                ti.page = page.getPageIndex();
                                page.addView(ti.tileView);

                                mCurrentlyAnimating.remove(ti);
                                requestLayout();
                            }
                        });
            } else {
                ti.tileView.animate()
                        .x(ti.destination.x)
                        .y(ti.destination.y)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (ti.tileView.setDual(dual)) {
                                    if (DEBUG_DRAG) {
                                        Log.w(TAG, ti + " changed dual state to : "
                                                + ti.tileView.isDual());
                                    }
                                    ti.tileView.handleStateChanged(ti.tile.getState());
                                    ti.tileView.invalidate();
                                }

                                mCurrentlyAnimating.remove(ti);
                                requestLayout();
                            }
                        });
            }

            // update previous location
            lastLocation.x = reallyTempLoc.x;
            lastLocation.y = reallyTempLoc.y;

            lastRow = tempRow;
            lastCol = tempCol;
            lastPage = tempPage;
        }
    }

    public int getDesiredColumnCount(int page, int row) {
        if (page == 0 && row == 0) {
            return 2; // TODO change if large tiles are disabled
        } else {
            return mColumns;
        }
    }

    @Override
    public void setExpanded(boolean expanded) {
        super.setExpanded(expanded);
        if (!expanded) {
            if (mEditing) {
                setEditing(false);
            }
        }
    }

    public boolean isAnimating(TileRecord t) {
        return mCurrentlyAnimating.contains(t);
    }

    // todo implement proper add tile ui
    protected void showAddDialog() {
        List<String> tiles = mHost.getTileSpecs();
        int numBroadcast = 0;
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).startsWith(IntentTile.PREFIX)) {
                numBroadcast++;
            }
        }
        String[] defaults =
                getContext().getString(R.string.quick_settings_tiles_default).split(",");
        final String[] available = new String[defaults.length + 1
                - (tiles.size() - numBroadcast)];
        final String[] availableTiles = new String[available.length];
        int index = 0;
        for (int i = 0; i < defaults.length; i++) {
            if (tiles.contains(defaults[i])) {
                continue;
            }
            int resource = mHost.getLabelResource(defaults[i]);
            if (resource != 0) {
                availableTiles[index] = defaults[i];
                available[index++] = getContext().getString(resource);
            } else {
                availableTiles[index] = defaults[i];
                available[index++] = defaults[i];
            }
        }
        available[index++] = getContext().getString(R.string.broadcast_tile);

        final AlertDialog d = new AlertDialog.Builder(getContext(), R.style.Theme_SystemUI_Dialog)
                .setTitle(R.string.add_tile)
                .setItems(available, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < available.length - 1) {
                            add(availableTiles[which]);
                        } else {
                            showBroadcastTileDialog();
                        }
                    }
                }).create();
        SystemUIDialog.makeSystemUIDialog(d);
        d.show();
    }

    public void showBroadcastTileDialog() {
        final EditText editText = new EditText(getContext());
        final AlertDialog d = new AlertDialog.Builder(getContext())
                .setTitle(R.string.broadcast_tile)
                .setView(editText)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        String action = editText.getText().toString();
                        if (isValid(action)) {
                            add(IntentTile.PREFIX + action + ')');
                        }
                    }
                }).create();
        SystemUIDialog.makeSystemUIDialog(d);
        d.show();
    }

    private boolean isValid(String action) {
        for (int i = 0; i < action.length(); i++) {
            char c = action.charAt(i);
            if (!Character.isAlphabetic(c) && !Character.isDigit(c) && c != '.') {
                return false;
            }
        }
        return true;
    }

    public void add(String tile) {
        MetricsLogger.action(getContext(), MetricsLogger.TUNER_QS_ADD, tile);
        List<String> tiles = new ArrayList<>(mHost.getTileSpecs());
        tiles.add(tile);
        mHost.setTiles(tiles);
    }

    public void reset() {
        Settings.Secure.putStringForUser(getContext().getContentResolver(),
                QSTileHost.TILES_SETTING, "default", ActivityManager.getCurrentUser());
        setEditing(false);
        setTiles(mHost.getTiles());
        requestLayout();
    }

    public boolean isDragging() {
        return mDragging;
    }

    public boolean isDragRecordAttached() {
        return mDragging && mDraggingRecord != null && mRecords.indexOf(mDraggingRecord) >= 0;
    }

    public static final class DragTileRecord extends TileRecord {
        public int page;
        public int destinationPage;
        public PointF destination = new PointF();

        @Override
        public String toString() {
            String label = tile instanceof QsTuner.DraggableTile ? tile.toString() :
                    tile.getClass().getSimpleName();

            String p = "at page: " + page;
            if (destinationPage != page) {
                p += "{-> " + destinationPage + "} ";
            }

            return "[" + label + ", coords: (" + row + ", " + col + ") " + p + "]";
        }
    }

    private static class TileShadow extends View.DragShadowBuilder {

        public TileShadow(View view) {
            super(view);
            Drawable shadow = view.getContext().getDrawable(R.drawable.qs_tile_background_drag);
            view.setBackground(shadow);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            super.onDrawShadow(canvas);
        }
    }
}
