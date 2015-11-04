/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.support.v4.view.PagerAdapter;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.systemui.R;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.settings.ToggleSlider;
import com.android.systemui.tuner.TunerService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class QSDragPanel extends QSPanel implements View.OnDragListener, View.OnLongClickListener {

    private static final String TAG = "QSDragPanel";

    public static final boolean DEBUG_DRAG = true;

    protected final ArrayList<QSPage> mPages = new ArrayList<>();

    protected QSViewPager mViewPager;
    protected PagerAdapter mPagerAdapter;
    protected View mEditTileInstructionView;
    protected View mDropTarget;

    private boolean mEditing;
    private boolean mDragging;
    private float mLastTouchLocationX, mLastTouchLocationY;
    private int mLocationHits;
    private int mLastDragIndex;
    private boolean mRestored;

    private DragTileRecord mDraggingRecord;

    List<TileRecord> mCurrentlyAnimating
            = Collections.synchronizedList(new ArrayList<TileRecord>());
    private Collection<QSTile<?>> mTempTiles = null;

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
        mBrightnessView = LayoutInflater.from(mContext).inflate(
                R.layout.quick_settings_brightness_dialog, this, false);
        mFooter = new QSFooter(this, mContext);
        mDropTarget = LayoutInflater.from(mContext).inflate(
                R.layout.qs_tile_drop_target, this, false);

        mEditTileInstructionView = LayoutInflater.from(mContext).inflate(
                R.layout.qs_tile_edit_header, this, false);
        mEditTileInstructionView.findViewById(R.id.add_target).setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAddDialog();
                    }
                });
        mViewPager = new QSViewPager(getContext());

        addView(mDetail);
        addView(mBrightnessView);
        addView(mEditTileInstructionView);
        addView(mDropTarget);
        addView(mViewPager);
        addView(mFooter.getView());

        mClipper = new QSDetailClipper(mDetail);

        mBrightnessController = new BrightnessController(getContext(),
                (ImageView) findViewById(R.id.brightness_icon),
                (ToggleSlider) findViewById(R.id.brightness_slider));

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
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setCurrentItem(0);

        mDropTarget.setVisibility(View.INVISIBLE);
        mEditTileInstructionView.setVisibility(View.INVISIBLE);

        setClipChildren(false);
        updateResources();

        mViewPager.setOnDragListener(this);
        mBrightnessView.setOnDragListener(this);
        mDropTarget.setOnDragListener(this);
    }

    public void setEditing(boolean editing) {
        if (mEditing == editing) return;

        // clear the record state
        for (TileRecord record : mRecords) {
            if (editing) {
                final QSTile.State state = record.tile.getState();
                // force it to be seen
                state.visible = true;
                drawTile(record, state);
            } else {
                record.tile.refreshState();
                // restore state
                drawTile(record, record.tile.getState());
            }
            if (record instanceof DragTileRecord) {
                setupRecord((DragTileRecord) record, true);
            }
        }
        requestLayout();

        if (editing) {
            // slide out brightness
            mBrightnessView
                    .animate()
                    .y(-mBrightnessView.getMeasuredHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mBrightnessView.setVisibility(View.INVISIBLE);
                        }
                    });

            // slide in instructions
            mEditTileInstructionView.animate()
                    .y(getTop() + mBrightnessPaddingTop)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mEditTileInstructionView.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                        }
                    });
        } else {
            // slide instructions out
            mEditTileInstructionView.animate()
                    .y(-mEditTileInstructionView.getHeight())
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            mEditTileInstructionView.setVisibility(View.INVISIBLE);
                        }
                    });
            // slide brightness in
            mBrightnessView
                    .animate()
                    .y(getTop() + mBrightnessPaddingTop)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            mBrightnessView.setVisibility(View.VISIBLE);
                        }
                    });
        }
        mEditing = editing;
    }

    protected void onStartDrag() {
        // animate instructions out
        mEditTileInstructionView.animate()
                .y(-mEditTileInstructionView.getHeight())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mEditTileInstructionView.setVisibility(View.INVISIBLE);
                    }
                });
        // animate drop target in
        mDropTarget.animate()
                .y(getTop() + mBrightnessPaddingTop)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        mDropTarget.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                    }
                });
    }

    protected void onStopDrag() {
        // drop target animates up
        mDropTarget.animate()
                .y(-mDropTarget.getMeasuredHeight())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mDropTarget.setVisibility(View.INVISIBLE);
                    }
                });

        // instructions go back down
        mEditTileInstructionView.animate()
                .y(getTop() + mBrightnessPaddingTop)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        mEditTileInstructionView.setVisibility(View.VISIBLE);
                    }
                });
    }

    protected View getDropTarget() {
        return mDropTarget;
    }

    public boolean isEditing() {
        return mEditing;
    }

    protected int getPagesForCount(int size) {
        return (int) Math.ceil(size / (double) getTilesPerPage());
    }

    protected int getCurrentMaxPageCount() {
        if (mTempTiles != null) {
            return getPagesForCount(mRecords.size() + mTempTiles.size());
        }
        return getPagesForCount(mRecords.size());
    }

    public void setTiles(Collection<QSTile<?>> tiles) {
        for (Record record : mRecords) {
            if (record instanceof DragTileRecord) {
                DragTileRecord dr = (DragTileRecord) record;
                mPages.get(dr.page).removeView(dr.tileView);
            }
        }
        mRecords.clear();
        if (isLaidOut() && !mDragging) {
            for (QSTile<?> tile : tiles) {
                addTile(tile);
            }
            if (isShowingDetail()) {
                mDetail.bringToFront();
            }
            refreshAllTiles();
        } else {
            if (DEBUG_DRAG) Log.w(TAG, "setting temporary tiles to layout");
            mTempTiles = Collections.synchronizedCollection(new ArrayList<QSTile<?>>(tiles));
        }
        mPagerAdapter.notifyDataSetChanged();
    }

    protected void addTile(final QSTile<?> tile) {
        if (DEBUG_DRAG) Log.d(TAG, "+++ addTile() called with " + "tile = [" + tile + "]");
        final DragTileRecord r = new DragTileRecord();

        mRecords.add(r);
        mPagerAdapter.notifyDataSetChanged();

        int potentialPageIdx = getPagesForCount(mRecords.size()) - 1;

        if (DEBUG_DRAG)
            Log.i(TAG, "potential page: " + potentialPageIdx + ", page sizes: " + mPages.size());

        if ((mPages.size() - 1) != potentialPageIdx) {
            if (DEBUG_DRAG)
                Log.w(TAG, "pages aren't big enough to handle this tile!, trying to reset stuff");
        }

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
                r.tile.click();
            }
        };
        final OnClickListener clickSecondary = new OnClickListener() {
            @Override
            public void onClick(View v) {
                r.tile.secondaryClick();
            }
        };
        final OnLongClickListener longClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                r.tile.longClick();
                return true;
            }
        };
        r.tileView.init(click, clickSecondary, longClick);
        r.tile.setListening(mListening);
        callback.onStateChanged(r.tile.getState());
        r.tile.refreshState();

        mPages.get(r.page).addView(r.tileView);

        mPagerAdapter.notifyDataSetChanged();

        if (DEBUG_DRAG) Log.d(TAG, "--- addTile() called with " + "tile = [" + tile + "]");
    }

    public int getTilesPerPage() {
        return 8;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (DEBUG_DRAG) Log.d(TAG, "onMeasure()");

        final int width = MeasureSpec.getSize(widthMeasureSpec);

        mDropTarget.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        mBrightnessView.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        mEditTileInstructionView.measure(exactly(width), MeasureSpec.UNSPECIFIED);

        final int brightnessHeight = mBrightnessView.getMeasuredHeight() + mBrightnessPaddingTop;

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
    }

    private static int exactly(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (mDragging) {
            Log.e(TAG, "skipping layout because we're dragging");
            return;
        }
        if (DEBUG_DRAG) Log.d(TAG, "onLayout()");
        final int w = getWidth();

        mDropTarget.layout(0, mBrightnessPaddingTop,
                mDropTarget.getMeasuredWidth(),
                mBrightnessPaddingTop + mDropTarget.getMeasuredHeight());

        mEditTileInstructionView.layout(0, mBrightnessPaddingTop,
                mEditTileInstructionView.getMeasuredWidth(),
                mBrightnessPaddingTop + mEditTileInstructionView.getMeasuredHeight());

        mBrightnessView.layout(0, mBrightnessPaddingTop,
                mBrightnessView.getMeasuredWidth(),
                mBrightnessPaddingTop + mBrightnessView.getMeasuredHeight());

        final int dh = Math.max(mDetail.getMeasuredHeight(), mViewPager.getMeasuredHeight());
        mViewPager.layout(0,
                mBrightnessView.getMeasuredHeight() + mBrightnessPaddingTop,
                w, dh);

        mDetail.layout(0, 0, mDetail.getMeasuredWidth(), dh);
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
            mPagerAdapter.notifyDataSetChanged();
            mTempTiles = null;
        }

        if (mEditing) {
            for (TileRecord record : mRecords) {
                if (record instanceof DragTileRecord) {
                    setupRecord((DragTileRecord) record, true);
                }
            }
        }
    }

    public int getRowTop(int row) {
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
                if (dr.page != page) continue;
                if (dr.row == row) cols++;
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

    public int getLeft(DragTileRecord record) {
        if (mRecords.contains(record)) {
            return getLeft(record.page, record.row, getColumnCount(record.page, record.row),
                    getPage(record.page).useLargeCellWidth(record));
        } else {
            // what it would be if this record was attached
            int cols = getColumnCount(record.destinationPage, record.row) + 1;
            final boolean firstRow = record.destinationPage == 0 && record.row == 0;
            return getLeft(record.row, record.col, cols, firstRow);
        }
    }

    public int getLeft(int page, int row, int col) {
        final boolean firstRow = page == 0 && row == 0;
        int cols = firstRow ? 2 : mColumns;
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

    private void setupRecord(DragTileRecord record, boolean wipeTranslation) {
        if (DEBUG_DRAG) {
            Log.d(TAG, "setupRecord() called with "
                    + "record = [" + record + "], wipeTranslation = [" + wipeTranslation + "]");
        }
        record.tileView.setOnDragListener(mEditing ? this : null);
        record.tileView.setTag(record.tile);
        record.tileView.setOnLongClickListener(mEditing ? this : null);

        for (int i = 0; i < record.tileView.getChildCount(); i++) {
            record.tileView.getChildAt(i).setClickable(!mEditing);
        }

        if (wipeTranslation) {
            if (DEBUG_DRAG) {
                Log.d(TAG, record + " wiping translations: "
                        + record.tileView.getTranslationX()
                        + ", " + record.tileView.getTranslationY());
            }
            record.tileView.setTranslationX(0);
            record.tileView.setTranslationY(0);
            record.destination.x = record.tileView.getX();
            record.destination.y = record.tileView.getY();
        }
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

        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                if (originatingTileEvent) {
                    if (DEBUG_DRAG) {
                        Log.v(TAG, "ACTION_DRAG_STARTED on target view.");
                    }
                    mDraggingRecord.tileView.setVisibility(View.INVISIBLE);
                    mLastDragIndex = mRecords.indexOf(mDraggingRecord);
                    mRestored = false;
                }
                return true;

            case DragEvent.ACTION_DRAG_ENTERED:
                mLocationHits = 0;

                if (!originatingTileEvent && v != getDropTarget() && targetTile != null) {
                    if (DEBUG_DRAG) {
                        Log.e(TAG, "entered tile " + targetTile);
                    }
                    if (mCurrentlyAnimating.isEmpty() && !mViewPager.isFakeDragging()) {
                        shiftTiles(targetTile, true);
                    } else {
                        if (DEBUG_DRAG) {
                            Log.w(TAG, "ignoring action enter for animating tiles and fake drags");
                        }
                    }
                }

                if (DEBUG_DRAG) {
                    Log.v(TAG, "ACTION_DRAG_ENTERED on view with tile: " + targetTile);
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
                    onStopDrag();
                }

                return true;

            case DragEvent.ACTION_DRAG_EXITED:
                if (targetTile != null) {
                    if (DEBUG_DRAG) {
                        Log.v(TAG, "ACTION_DRAG_EXITED on view with tile: " + targetTile);
                    }
                } else {
                    if (DEBUG_DRAG) {
                        Log.v(TAG, "ACTION_DRAG_EXITED on view: " + v);
                    }
                }
                if (originatingTileEvent) {
                    mLastDragIndex = mRecords.indexOf(mDraggingRecord);
                    if (DEBUG_DRAG) {
                        Log.w(TAG, "updating last drag index to : " + mLastDragIndex);
                    }

                    if (targetTile != null) {
                        // move tiles back
                        shiftTiles(targetTile, false);
                    }
                }
                return true;

            case DragEvent.ACTION_DROP:
                if (DEBUG_DRAG) {
                    Log.v(TAG, "ACTION_DROP, event loc: " + event.getX() + ", " + event.getY()
                            + " + with tile: " + targetTile);
                }
                mLastTouchLocationX = event.getX();
                mLastTouchLocationY = event.getY();

                if (v == getDropTarget()) {
                    if (DEBUG_DRAG) {
                        Log.w(TAG, "removing tile: " + mDraggingRecord);
                    }
                    getPage(mDraggingRecord.page).removeView(mDraggingRecord.tileView);
                    // what spec is this tile?

                    // TODO remove tile

                } else {
                    restoreDraggingTilePosition(v);
                }
                onStopDrag();

                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                mLastTouchLocationX = event.getX();
                mLastTouchLocationY = event.getY();

                if (mCurrentlyAnimating.isEmpty()) {
                    // do nothing if we're animating tiles
                    if (v == mViewPager) {
                        // do we need to change pages?
                        int x = (int) event.getX();
                        int width = mViewPager.getWidth();
                        int scrollPadding = (int) (width * QSViewPager.SCROLL_PERCENT);
                        if (x < scrollPadding) {
                            // scroll left
                            if (mViewPager.canScrollHorizontally(-1)) {
                                mViewPager.animatePagerTransition(false);
                            }
                        } else if (x > width - scrollPadding) {
                            if (mViewPager.canScrollHorizontally(1)) {
                                mViewPager.animatePagerTransition(true);
                            }
                        }
                    } else {
                        if (!originatingTileEvent && v != getDropTarget() && targetTile != null) {
                            // dragging around on another tile
                            if (mLocationHits++ == 30) {
                                // add dragging tile to current page
                                shiftTiles(targetTile, true);
                            } else {
                                mLocationHits++;
                            }

                        }
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
        if (DEBUG_DRAG) {
            Log.i(TAG, "restoreDraggingTilePosition() called with "
                    + "v = [" + (v.getTag() != null ? v.getTag() : v) + "]");
        }
        mDraggingRecord.tileView.setVisibility(View.VISIBLE);

        final boolean dragRecordDetached = mRecords.indexOf(mDraggingRecord) == -1;

        if (DEBUG_DRAG) {
            Log.v(TAG, "mLastDragIndex: " + mLastDragIndex
                    + ", detached: " + dragRecordDetached + ", drag record: " + mDraggingRecord);
        }

        final QSPage originalPage = getPage(mDraggingRecord.page);

        if (dragRecordDetached) {
            originalPage.removeView(mDraggingRecord.tileView);
            addTransientView(mDraggingRecord.tileView, 0);

            if (mLastDragIndex == -1) {
                // where to put
                if (DEBUG_DRAG) {
                    Log.e(TAG, "last drag index is -1!!! what do?");
                }
            }

            int hops = mRecords.size() - mLastDragIndex;
            if (hops > 0) {
                for (int i = 0; i < hops; i++) {
                    setToNextDestination(mDraggingRecord);
                }
            } else {
                // dropping it in the same index?
                mDraggingRecord.destination.x = getLeft(mDraggingRecord);
                mDraggingRecord.destination.y = getRowTop(mDraggingRecord.row);
            }

            if (mDraggingRecord.destinationPage != mDraggingRecord.page) {
                if (DEBUG_DRAG) {
                    Log.w(TAG, "dragging records needs to adjust its page");
                }
            }
        }

        // need to move center of the dragging view to the coords of the event.
        float touchEventBoxLeft = v.getX()
                + (mLastTouchLocationX - (mDraggingRecord.tileView.getWidth() / 2));
        float touchEventBoxTop = v.getY()
                + (mLastTouchLocationY - (mDraggingRecord.tileView.getHeight() / 2));

        if (DEBUG_DRAG) {
            Log.d(TAG, "setting drag record view to coords: x:" + touchEventBoxLeft
                    + ", y:" + touchEventBoxTop);
            Log.d(TAG, "animating drag record to: " + mDraggingRecord + ", loc: "
                    + mDraggingRecord.destination);
        }
        mDraggingRecord.tileView.setX(touchEventBoxLeft);
        mDraggingRecord.tileView.setY(touchEventBoxTop);

        float destinationY = mDraggingRecord.destination.y;
        if (dragRecordDetached) {
            destinationY += mViewPager.getTop();
        }
        mDraggingRecord.tileView.animate()
                .x(mDraggingRecord.destination.x)
                .y(destinationY) // part of the viewpager now
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mViewPager.requestDisallowInterceptTouchEvent(false);
                        if (dragRecordDetached) {
                            removeTransientView(mDraggingRecord.tileView);
                            mRecords.add(mDraggingRecord);

                            // add it to the next originalPage if it exists
                            final QSPage lastPage = getPage(mDraggingRecord.destinationPage);
                            if (false) Log.v(TAG, "lastPage: " + lastPage);
                            if (lastPage != null) {
                                mDraggingRecord.page = mDraggingRecord.destinationPage;

                                lastPage.addView(mDraggingRecord.tileView);
                                setupRecord(mDraggingRecord, false);

                                // reset this to be in the coords of the page, not viewpager anymore
                                mDraggingRecord.tileView.setY(mDraggingRecord.destination.y);
                            }

                        }
                        mDragging = false;
                        mPagerAdapter.notifyDataSetChanged();

                        if (false) Log.v(TAG, "requesting layout");
                        requestLayout();
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

        if (tile.row > maxRows) {
            tile.destinationPage = tile.page + 1;
            tile.row = 0;
            tile.col = 0;

            if (DEBUG_DRAG) {
                Log.w(TAG, "tile's destination page moved to: " + tile.destinationPage);
            }
        }

        int columnCount = getColumnCount(tile.destinationPage, tile.row);
        if (!mRecords.contains(tile)) {
            // increase column count for the destination location to account for this tile being added
            columnCount++;
            if (DEBUG_DRAG) {
                Log.w(TAG, "column count adjusted to: " + columnCount);
            }
        }

        tile.destination.x = getLeft(tile);
        tile.destination.y = getRowTop(tile.row);

        if (DEBUG_DRAG) {
            Log.i(TAG, "---setToNextDestination() called with " + "tile = [" + tile + "], now at: "
                    + tile.destination);
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

        TileShadow mTileShadow = new TileShadow(v);
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

            // the index of the original position of the statingTile before it moved
            int startingIndex = mRecords.indexOf(startingTile);

            mRecords.add(startingIndex, mDraggingRecord);

            shiftAllTilesRight(startingTile.page, startingIndex);

        } else {
            // it is also probably the dragging tile
            final int startingIndex = mRecords.indexOf(startingTile);

            final int draggingIndex = mRecords.indexOf(mDraggingRecord);

            if (startingIndex != draggingIndex) {
                if (DEBUG_DRAG) {
                    Log.e(TAG, "startinIndex: " + startingIndex + ", draggingIndex: "
                            + draggingIndex + ", and they differ!!!!");
                }
            }

            // startingTile should be the empty tile that things should start shifting into
            shiftAllTilesLeft(startingTile.page, startingIndex);

            // remove the dragging record
            if (mRecords.remove(mDraggingRecord)) {
                if (DEBUG_DRAG) {
                    Log.v(TAG, "removed dragging record after moving tiles back");
                }
            }
        }

        mViewPager.getAdapter().notifyDataSetChanged();
    }

    private void shiftAllTilesRight(int startingPage, int startingIndex) {
        for (int j = 0; j < mRecords.size() - 1; j++) {
            final DragTileRecord ti = (DragTileRecord) mRecords.get(j);
            final DragTileRecord tnext = (DragTileRecord) mRecords.get(j + 1);

            if (ti.page < startingPage) {
                // skip uninteresting pages
                continue;
            } else if (ti.page == startingPage && j < startingIndex) {
                // skip unwanted indices
                continue;
            }


            ti.row = tnext.row;
            ti.col = tnext.col;

            ti.destination.x = getLeft(ti.page, ti.row, ti.col);
            ti.destination.y = getRowTop(ti.row);

            if (ti.page != tnext.page) {
                if (DEBUG_DRAG) {
                    Log.v(TAG, "moving " + ti + " to page " + tnext.page + ", at coords: "
                            + tnext.row + ", col: " + tnext.col);
                }

                final QSPage tilePageSource = getPage(ti.page);
                final QSPage tilePageTarget = getPage(tnext.page);
                tilePageSource.removeView(ti.tileView);
                tilePageSource.addTransientView(ti.tileView, 0);

                ti.page = tilePageTarget.getPageIndex();

                ti.tileView.animate()
                        .x(ti.destination.x + getWidth())
                        .y(ti.destination.y)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                tilePageSource.removeTransientView(ti.tileView);
                                tilePageTarget.addView(ti.tileView);
                                mPagerAdapter.notifyDataSetChanged();
                                ti.tileView.setX(ti.destination.x);
                                setupRecord(ti, false);

                                mCurrentlyAnimating.remove(ti);
                            }
                        });
                mCurrentlyAnimating.add(ti);

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
                mCurrentlyAnimating.add(ti);
            }
        }

        // need to do last tile manually
        final DragTileRecord last = (DragTileRecord) mRecords.get(mRecords.size() - 1);

        if (DEBUG_DRAG) {
            Log.i(TAG, "last tile shifting to the right: " + last);
        }
        setToNextDestination(last);
        if (last.destinationPage != last.page) {
            // TODO fixme!!!!
            if (DEBUG_DRAG) {
                Log.e(TAG, "last tile: " + last + ", has a new dest page which isn't handled!");
            }
        }
        if (DEBUG_DRAG) {
            if (DEBUG_DRAG) Log.i(TAG, "shift finished: " + last);
        }

        last.tileView.animate()
                .x(last.destination.x)
                .y(last.destination.y)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mCurrentlyAnimating.remove(last);
                    }
                });
        mCurrentlyAnimating.add(last);
    }

    private void shiftAllTilesLeft(int startingPage, int startingIndex) {
        DragTileRecord startingTile = (DragTileRecord) mRecords.get(startingIndex);

        final PointF lastLocation = new PointF(startingTile.destination.x,
                startingTile.destination.y);
        PointF reallyTempLoc = new PointF();
        int lastRow = startingTile.row, lastCol = startingTile.col, tempRow,
                tempCol, lastPage = startingTile.page, tempPage;

        for (int j = 0; j < mRecords.size(); j++) {
            final DragTileRecord ti = (DragTileRecord) mRecords.get(j);

            if (j < startingIndex) {
                if (DEBUG_DRAG) {
                    Log.d(TAG, "shiftTilesLeft() skipping " + ti
                            + " because its before startingIndex: " + startingIndex);
                }
                // skip unwanted indices
                continue;
            }

            // save current tile's loc
            reallyTempLoc.x = ti.destination.x;
            reallyTempLoc.y = ti.destination.y;

            tempRow = ti.row;
            tempCol = ti.col;
            tempPage = ti.page;

            // set current tiles loc to the previous location
            ti.destination.x = lastLocation.x;
            ti.destination.y = lastLocation.y;

            ti.row = lastRow;
            ti.col = lastCol;

            if (ti.page != lastPage) {
                if (DEBUG_DRAG) {
                    Log.v(TAG, "page moving " + ti + " to " + lastPage + ", at coords: "
                            + lastRow + ", col: " + lastCol);
                }

                final QSPage originalPage = getPage(ti.page);
                originalPage.removeView(ti.tileView);
                final QSPage page = getPage(lastPage);
                page.addTransientView(ti.tileView, 0);

                ti.tileView.setX(reallyTempLoc.x + getWidth());
                ti.tileView.setY(reallyTempLoc.y);

                ti.page = lastPage;

                ti.tileView.animate()
                        .x(ti.destination.x)
                        .y(ti.destination.y)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {

                                page.removeTransientView(ti.tileView);
                                page.addView(ti.tileView);
                                mPagerAdapter.notifyDataSetChanged();

                                setupRecord(ti, false);

                                mCurrentlyAnimating.remove(ti);

                            }
                        });
                mCurrentlyAnimating.add(ti);

            } else {
                if (DEBUG_DRAG) {
                    Log.v(TAG, "moving " + ti + " to coords: " + lastRow + ", col: " + lastCol);
                }

                ti.tileView.animate()
                        .x(ti.destination.x)
                        .y(ti.destination.y)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                mCurrentlyAnimating.remove(ti);
                            }
                        });
                mCurrentlyAnimating.add(ti);
            }

            // update previous location
            lastLocation.x = reallyTempLoc.x;
            lastLocation.y = reallyTempLoc.y;

            lastRow = tempRow;
            lastCol = tempCol;
            lastPage = tempPage;
        }
    }

    protected void showAddDialog() {
        TunerService.setTunerEnabled(mContext, true);

        mHost.collapsePanels();

        Intent intent = new Intent("com.android.settings.action.EXTRA_SETTINGS");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivityAsUser(intent, UserHandle.CURRENT);
    }

    public static final class DragTileRecord extends TileRecord {
        public int page;
        public int destinationPage;
        public PointF destination = new PointF();

        @Override
        public String toString() {
            String label = tile.getClass().getSimpleName();

            return "[" + label + ", coords: (" + row + ", " + col + ") at page: " + page + "]";
        }
    }

    private static class TileShadow extends View.DragShadowBuilder {

        private Drawable mShadow;
        private Rect mBounds;

        public TileShadow(View view) {
            super(view);
            mShadow = view.getContext().getDrawable(R.drawable.qs_tile_background_drag);
            mBounds = new Rect();
        }

        @Override
        public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
            super.onProvideShadowMetrics(shadowSize, shadowTouchPoint);

            mBounds.set(0, 0, shadowSize.x, shadowSize.y);
            mShadow.setBounds(mBounds);
        }

        @Override
        public void onDrawShadow(Canvas canvas) {
            mShadow.draw(canvas);
            super.onDrawShadow(canvas);
        }
    }
}
