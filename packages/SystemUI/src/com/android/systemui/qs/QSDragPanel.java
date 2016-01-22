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
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.UserHandle;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.logging.MetricsLogger;
import com.android.systemui.FontSizeUtils;
import com.android.systemui.R;
import com.android.systemui.cm.UserContentObserver;
import com.android.systemui.qs.tiles.EditTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.settings.ToggleSlider;
import com.android.systemui.statusbar.phone.QSTileHost;
import com.android.systemui.statusbar.phone.SystemUIDialog;
import com.android.systemui.statusbar.policy.BrightnessMirrorController;
import com.android.systemui.tuner.QsTuner;
import com.viewpagerindicator.CirclePageIndicator;
import cyanogenmod.app.StatusBarPanelCustomTile;
import cyanogenmod.providers.CMSettings;
import org.cyanogenmod.internal.logging.CMMetricsLogger;
import org.cyanogenmod.internal.util.QSUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class QSDragPanel extends QSPanel implements View.OnDragListener, View.OnLongClickListener {

    private static final String TAG = "QSDragPanel";

    public static final boolean DEBUG_TILES = false;
    public static final boolean DEBUG_DRAG = false;

    private static final int MAX_ROW_COUNT = 3;
    private static final String BROADCAST_TILE_SPEC_PLACEHOLDER = "broadcast_placeholder";

    protected final ArrayList<QSPage> mPages = new ArrayList<>();

    protected QSViewPager mViewPager;
    protected PagerAdapter mPagerAdapter;
    QSPanelTopView mQsPanelTop;
    CirclePageIndicator mPageIndicator;
    private int mPageIndicatorHeight;

    private TextView mDetailRemoveButton;
    private DragTileRecord mDraggingRecord, mLastDragRecord;
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

    protected boolean mFirstRowLarge = true;
    private SettingsObserver mSettingsObserver;

    List<TileRecord> mCurrentlyAnimating
            = Collections.synchronizedList(new ArrayList<TileRecord>());

    private Point mDisplaySize;
    private int[] mTmpLoc;

    public QSDragPanel(Context context) {
        this(context, null);
    }

    public QSDragPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setupViews() {
        updateResources();

        mDetail = LayoutInflater.from(mContext).inflate(R.layout.qs_detail, this, false);
        mDetailContent = (ViewGroup) mDetail.findViewById(android.R.id.content);
        mDetailRemoveButton = (TextView) mDetail.findViewById(android.R.id.button3);
        mDetailSettingsButton = (TextView) mDetail.findViewById(android.R.id.button2);
        mDetailDoneButton = (TextView) mDetail.findViewById(android.R.id.button1);
        updateDetailText();
        mDetail.setVisibility(GONE);
        mDetail.setClickable(true);

        mQsPanelTop = (QSPanelTopView) LayoutInflater.from(mContext).inflate(R.layout.qs_tile_top,
                this, false);

        // tint trash can to default color
        final int color = mContext.getColor(R.color.qs_tile_trash);
        DrawableCompat.setTint(mQsPanelTop.getDropTargetIcon().getDrawable(), color);

        mBrightnessView = mQsPanelTop.getBrightnessView();
        mFooter = new QSFooter(this, mContext);

        // add target click listener
        mQsPanelTop.getAddTarget().setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TilesListAdapter adapter = new TilesListAdapter(mContext, QSDragPanel.this);
                        showDetailAdapter(true, adapter, v.getLocationOnScreen());
                        mDetail.bringToFront();
                    }
                });
        mViewPager = new QSViewPager(getContext());
        mViewPager.setDragPanel(this);

        mPageIndicator = new CirclePageIndicator(getContext());
        addView(mDetail);
        addView(mQsPanelTop);
        addView(mViewPager);
        addView(mPageIndicator);
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
                if (DEBUG_TILES) {
                    Log.d(TAG, "instantiateItem() called with "
                            + "container = [" + container + "], position = [" + position + "]");
                }

                if (mEditing && position == 0) {
                    QSSettings qss = (QSSettings)
                            View.inflate(container.getContext(), R.layout.qs_settings, null);
                    qss.setHost(mHost);
                    container.addView(qss, 0);
                    return qss;
                } else {
                    final int adjustedPosition = mEditing ? position - 1 : position;
                    QSPage page = mPages.get(adjustedPosition);
                    container.addView(page);
                    return page;
                }
            }

            @Override
            public void destroyItem(ViewGroup container, int position, Object object) {
                if (DEBUG_TILES) {
                    Log.d(TAG, "destroyItem() called with " + "container = ["
                            + container + "], position = [" + position + "], object = ["
                            + object + "]");
                }
                if (object instanceof View) {
                    container.removeView((View) object);
                }
            }

            @Override
            public int getItemPosition(Object object) {
                if (object instanceof QSPage) {
                    if (mEditing != ((QSPage) object).getAdapterEditingState()) {
                        // position of item changes when we set change the editing mode,
                        // sync it and send the new position
                        ((QSPage) object).setAdapterEditingState(mEditing);

                        // calculate new position
                        int indexOf = ((QSPage) object).getPageIndex();
                        if (mEditing) return indexOf + 1;
                        else return indexOf;

                    } else if (!mPages.contains(object) && !mDragging) {
                        // only return none if we aren't dragging (object may be removed from
                        // the records array temporarily and we might think we have less pages,
                        // we don't want to prematurely remove this page
                        return POSITION_NONE;
                    } else {

                        return POSITION_UNCHANGED;
                    }

                } else if (object instanceof QSSettings) {
                    if (((QSSettings) object).getAdapterEditingState() != mEditing) {
                        ((QSSettings) object).setAdapterEditingState(mEditing);
                        if (mEditing) return 0 /* locked at position 0 */;
                        else return POSITION_NONE;
                    } else {
                        return POSITION_UNCHANGED;
                    }
                }
                return super.getItemPosition(object);
            }

            @Override
            public int getCount() {
                final int qsPages = Math.max(getCurrentMaxPageCount(), 1);

                if (mPages != null && qsPages > mPages.size()) {
                    for(int i = mPages.size(); i < qsPages; i++) {
                        mPages.add(i, new QSPage(mViewPager.getContext(), QSDragPanel.this, i));
                    }
                }

                if (mEditing) return qsPages + 1;
                return qsPages;
            }

            @Override
            public boolean isViewFromObject(View view, Object object) {
                return view == object;
            }
        };
        mViewPager.setAdapter(mPagerAdapter);

        mPageIndicator.setViewPager(mViewPager);
        mPageIndicator.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset,
                                       int positionOffsetPixels) {
                if (DEBUG_DRAG) {
                    Log.i(TAG, "onPageScrolled() called with " + "position = ["
                            + position + "], positionOffset = [" + positionOffset
                            + "], positionOffsetPixels = [" + positionOffsetPixels + "]");
                }

                if (mEditing) {
                    float targetTranslationX = 0;

                    // targetTranslationX = where it's supposed to be - diff
                    int homeLocation = mViewPager.getMeasuredWidth();

                    // how far away from homeLocation is the scroll?
                    if (positionOffsetPixels < homeLocation
                            && position == 0) {
                        targetTranslationX = homeLocation - positionOffsetPixels;
                    }
                    mQsPanelTop.setTranslationX(targetTranslationX);
                }
            }

            @Override
            public void onPageSelected(int position) {
                if (mDragging && position != mDraggingRecord.page
                        && !mViewPager.isFakeDragging() && !mRestoring) {
                    if (DEBUG_DRAG) {
                        Log.w(TAG, "moving drag record to page: " + position);
                    }

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
        mViewPager.setOverScrollMode(OVER_SCROLL_NEVER);

        setClipChildren(false);

        mSettingsObserver = new SettingsObserver(new Handler());

        mViewPager.setOnDragListener(QSDragPanel.this);
        mQsPanelTop.setOnDragListener(QSDragPanel.this);
        mPageIndicator.setOnDragListener(QSDragPanel.this);
        setOnDragListener(QSDragPanel.this);

        mViewPager.setOverScrollMode(View.OVER_SCROLL_NEVER);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return mClipper.isAnimating() || mEditing;
    }

    @Override
    public void setBrightnessMirror(BrightnessMirrorController c) {
        super.onFinishInflate();
        ToggleSlider brightnessSlider =
                (ToggleSlider) mQsPanelTop.findViewById(R.id.brightness_slider);
        ToggleSlider mirror = (ToggleSlider) c.getMirror().findViewById(R.id.brightness_slider);
        brightnessSlider.setMirror(mirror);
        brightnessSlider.setMirrorController(c);
    }

    protected void drawTile(TileRecord r, QSTile.State state) {
        if (mEditing) {
            state.visible = true;
        }
        final int visibility = state.visible ? VISIBLE : GONE;
        setTileVisibility(r.tileView, visibility);
        setTileEnabled(r.tileView, state.enabled);
        r.tileView.onStateChanged(state);
    }

    @Override
    public void setListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
        for (TileRecord r : mRecords) {
            r.tile.setListening(mListening);
        }
        mFooter.setListening(mListening);
        mQsPanelTop.setListening(mListening);
        if (mListening) {
            refreshAllTiles();
        }
        if (mListening) {
            mSettingsObserver.observe();
        } else {
            mSettingsObserver.unobserve();
        }

        if (isLaidOut() && listening && showBrightnessSlider()) {
            mBrightnessController.registerCallbacks();
        } else {
            mBrightnessController.unregisterCallbacks();
        }
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

            mQsPanelTop.animate().translationX(0).start();
        }

        // clear the record state
        for (TileRecord record : mRecords) {
            setupRecord(record);
            drawTile(record, record.tile.getState());
        }
        mQsPanelTop.setEditing(editing);
        mPageIndicator.setEditing(editing);
        mPagerAdapter.notifyDataSetChanged();

        mViewPager.setOffscreenPageLimit(mEditing ? getCurrentMaxPageCount() + 1 : 1);
        mPagerAdapter.notifyDataSetChanged();

        requestLayout();
    }

    protected void onStartDrag() {
        mQsPanelTop.onStartDrag();
    }

    protected void onStopDrag() {
        mDraggingRecord.tileView.setAlpha(1f);

        mLastDragRecord = mDraggingRecord;
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

    protected int getPagesForCount(int tileCount) {
        if (tileCount == 0) {
            return 1;
        }
        tileCount = Math.max(0, tileCount - getTilesPerPage(true));
        // first page + rest of tiles
        return 1 + (int) Math.ceil(tileCount / (double) getTilesPerPage(false));
    }

    protected int getCurrentMaxPageCount() {
        int initialSize = mRecords.size();
        return getPagesForCount(initialSize);
    }

    @Override
    protected void updateDetailText() {
        super.updateDetailText();
        mDetailRemoveButton.setText(R.string.quick_settings_remove);
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

    public void setTiles(final Collection<QSTile<?>> tilesCollection) {
        final List<QSTile<?>> tiles = new ArrayList<>(tilesCollection);
        if (DEBUG_TILES) {
            Log.i(TAG, "setTiles() called with " + "tiles = ["
                    + tiles + "]");
        }

        if (mLastDragRecord != null && mRecords.indexOf(mLastDragRecord) == -1) {
            // the last removed record might be stored in mLastDragRecord if we just shifted
            // re-add it to the list so we'll clean it up below
            mRecords.add(mLastDragRecord);
            mLastDragRecord = null;
        }

        Map<QSTile<?>, DragTileRecord> recordMap = new ArrayMap<>();
        ListIterator<TileRecord> iterator = mRecords.listIterator(mRecords.size());

        int recordsRemoved = 0;
        // cleanup current records
        while (iterator.hasPrevious()) {
            DragTileRecord dr = (DragTileRecord) iterator.previous();

            if (tiles.contains(dr.tile)) {
                if (DEBUG_TILES) {
                    Log.i(TAG, "caching tile: " + dr.tile);
                }
                recordMap.put(dr.tile, dr);
            } else {
                if (DEBUG_TILES) {
                    Log.i(TAG, "removing tile: " + dr.tile);
                }
                // clean up view
                mPages.get(dr.page).removeView(dr.tileView);

                // remove record
                iterator.remove();
                recordsRemoved++;

                if (dr.page >= getCurrentMaxPageCount() - 1) {
                    final int childCount = mPages.get(dr.page).getChildCount();

                    if (childCount == 0) {
                        // if current page is the current max page COUNT (off by 1) then move back
                        final int currentIndex = mViewPager.getCurrentItem();
                        if (currentIndex == (getCurrentMaxPageCount()) + (mEditing ? 1 : 0)) {
                            mViewPager.setCurrentItem(currentIndex - 1, false);
                            mPagerAdapter.startUpdate(mViewPager);
                            final int pageIndex = mEditing ? currentIndex - 1 : currentIndex;
                            mPages.remove(pageIndex);
                            mPagerAdapter.finishUpdate(mViewPager);
                            mPagerAdapter.notifyDataSetChanged();
                        }
                    }
                }
            }
        }

        // at this point recordMap should have all retained tiles, no new or old tiles
        int delta = tiles.size() - recordMap.size() - recordsRemoved;
        if (DEBUG_TILES) {
            Log.i(TAG, "record map delta: " + delta);
        }
        mRecords.ensureCapacity(tiles.size());
        mPagerAdapter.notifyDataSetChanged();

        // add new tiles
        for (int i = 0; i < tiles.size(); i++) {
            QSTile<?> tile = tiles.get(i);
            final int tileDestPage = getPagesForCount(i + 1) - 1;

            if (DEBUG_TILES) {
                Log.d(TAG, "tile at : " + i + ": " + tile + " to dest page: " + tileDestPage);
            }
            DragTileRecord record;
            if (!recordMap.containsKey(tile)) {
                if (DEBUG_TILES) {
                    Log.d(TAG, "tile at: " + i + " not cached, adding it to records");
                }
                record = makeRecord(tile);
                record.destinationPage = tileDestPage;
                recordMap.put(tile, record);
                mRecords.add(i, record);
                mPagerAdapter.notifyDataSetChanged();
            } else {
                record = recordMap.get(tile);
                if (DEBUG_TILES) {
                    Log.d(TAG, "tile at : " + i + ": cached, restoring: " + record);
                }
                int indexOf = mRecords.indexOf(record);
                if (indexOf != i) {
                    if (DEBUG_TILES) {
                        Log.w(TAG, "moving index of " + record + " from "
                                + indexOf + " to " + i);
                    }
                    Collections.swap(mRecords, indexOf, i);

                    record.destinationPage = tileDestPage;
                    ensureDestinationPage(record);
                }

            }
            if (record.page == -1) {
                // add the view
                mPages.get(record.destinationPage).addView(record.tileView);
                record.page = record.destinationPage;
                if (DEBUG_TILES) {
                    Log.d(TAG, "added view " + record);
                }
            }
        }

        if (isShowingDetail()) {
            mDetail.bringToFront();
        }
        mPagerAdapter.notifyDataSetChanged();

        refreshAllTiles();
        requestLayout();
    }

    private void ensureDestinationPage(DragTileRecord record) {
        if (record.destinationPage != record.page) {
            if (record.page >= 0) {
                getPage(record.page).removeView(record.tileView);
            }
            getPage(record.destinationPage).addView(record.tileView);
            record.page = record.destinationPage;
        }
    }

    private DragTileRecord makeRecord(final QSTile<?> tile) {
        if (DEBUG_TILES) {
            Log.d(TAG, "+++ makeRecord() called with " + "tile = [" + tile + "]");
        }
        final DragTileRecord r = new DragTileRecord();


        r.tile = tile;
        r.page = -1;
        r.destinationPage = -1;
        r.tileView = tile.createTileView(mContext);
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
        r.tileView.setVisibility(mEditing ? View.VISIBLE : View.GONE);
        callback.onStateChanged(r.tile.getState());

        if (DEBUG_TILES) {
            Log.d(TAG, "--- makeRecord() called with " + "tile = [" + tile + "]");
        }
        return r;
    }

    private void removeDraggingRecord() {
        // what spec is this tile?
        String spec = mHost.getSpec(mDraggingRecord.tile);
        if (DEBUG_DRAG) {
            Log.w(TAG, "removing tile: " + mDraggingRecord + " with spec: " + spec);
        }
        onStopDrag();
        mHost.remove(spec);
    }

    public int getTilesPerPage(boolean firstPage) {
        if ((!mFirstRowLarge && firstPage) || !firstPage) {
            return QSTileHost.TILES_PER_PAGE + 1;
        }
        return QSTileHost.TILES_PER_PAGE;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mTmpLoc = null;
        mDisplaySize = null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);

        mQsPanelTop.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        mViewPager.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        mPageIndicator.measure(exactly(width), atMost(mPageIndicatorHeight));
        mFooter.getView().measure(exactly(width), MeasureSpec.UNSPECIFIED);

        int h = getRowTop(getCurrentMaxRow() + 1) + mPanelPaddingBottom;

        if (mFooter.hasFooter()) {
            h += mFooter.getView().getMeasuredHeight();
        }
        mDetail.measure(exactly(width), MeasureSpec.UNSPECIFIED);
        if (mDetail.getMeasuredHeight() < h) {
            mDetail.measure(exactly(width), exactly(h));
        }

        // Check if the detail view would be overflowing below the physical height of the device
        // and cutting the content off. If it is, reduce the detail height to fit.
        if (isShowingDetail()) {
            if (mDisplaySize == null) {
                mDisplaySize = new Point();
                getDisplay().getSize(mDisplaySize);
            }
            if (mTmpLoc == null) {
                mTmpLoc = new int[2];
                mDetail.getLocationOnScreen(mTmpLoc);
            }

            final int containerTop = mTmpLoc[1];
            final int detailBottom = containerTop + mDetail.getMeasuredHeight();
            if (detailBottom >= mDisplaySize.y) {
                // panel is hanging below the screen
                final int detailMinHeight = mDisplaySize.y - containerTop;
                mDetail.measure(exactly(width), exactly(detailMinHeight));
            }
            setMeasuredDimension(width, mDetail.getMeasuredHeight());
            mGridHeight = mDetail.getMeasuredHeight();
        } else {
            setMeasuredDimension(width, h);
            mGridHeight = h;
        }

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

    public static int atMost(int size) {
        return MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST);
    }

    @Override
    protected void handleShowDetailTile(TileRecord r, boolean show) {
        if (r instanceof DragTileRecord) {
            if ((mDetailRecord != null) == show && mDetailRecord == r) return;

            if (show) {
                r.detailAdapter = r.tile.getDetailAdapter();
                if (r.detailAdapter == null) return;
            }
            r.tile.setDetailListening(show);
            int x = (int) ((DragTileRecord) r).destination.x + r.tileView.getWidth() / 2;
            int y = mViewPager.getTop() + (int) ((DragTileRecord) r).destination.y + r.tileView.getHeight() / 2;
            handleShowDetailImpl(r, show, x, y);
        } else {
            super.handleShowDetailTile(r, show);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int w = getWidth();

        mQsPanelTop.layout(0, 0, w, mQsPanelTop.getMeasuredHeight());

        int viewPagerBottom = mQsPanelTop.getMeasuredHeight() + mViewPager.getMeasuredHeight();
        // view pager laid out from top of brightness view to bottom to page through settings
        mViewPager.layout(0, 0, w, viewPagerBottom);

        // layout page indicator inside viewpager inset
        mPageIndicator.layout(0, b - mPageIndicatorHeight, w, b);

        mDetail.layout(0, 0, w, mDetail.getMeasuredHeight());

        if (mFooter.hasFooter()) {
            View footer = mFooter.getView();
            footer.layout(0, getMeasuredHeight() - footer.getMeasuredHeight(),
                    footer.getMeasuredWidth(), getMeasuredHeight());
        }

        if (!isShowingDetail() && !isClosingDetail()) {
            mQsPanelTop.bringToFront();
        }
    }

    protected int getRowTop(int row) {
        int baseHeight = mQsPanelTop.getMeasuredHeight();
        if (row <= 0) return baseHeight;
        return baseHeight + mLargeCellHeight - mDualTileUnderlap + (row - 1) * mCellHeight;
    }

    public int getColumnCount() {
        return mColumns;
    }

    public int getColumnCount(int page, int row, boolean smart) {
        int cols = 0;
        for (Record record : mRecords) {
            if (record instanceof DragTileRecord) {
                DragTileRecord dr = (DragTileRecord) record;
                if (dr.tileView.getVisibility() == GONE) continue;
                if (dr.destinationPage != page) continue;
                if (dr.row == row) cols++;
            }
        }

        if (smart && isEditing() && (isDragging() || mRestoring) && !isDragRecordAttached()) {
            // if shifting tiles back, and one moved from previous page

            // if it's the very last row on the last page, we should add an extra column to account
            // for where teh dragging lastRecord would go
            DragTileRecord lastRecord = (DragTileRecord) mRecords.get(mRecords.size() - 1);
            if (lastRecord.destinationPage == page && lastRecord.row == row
                    && cols < getColumnCount()) {
                cols++;
                if (DEBUG_DRAG) {
                    boolean draggingRecordBefore = isBefore(mDraggingRecord, lastRecord);
                    Log.w(TAG, "adding another col, cols: " + cols + ", last: " + lastRecord
                            + ", drag: " + mDraggingRecord
                            + ", and dragging record before last: " + draggingRecordBefore);
                }
            }
        }
        return cols;
    }

    public int getColumnCount(int page, int row) {
        return getColumnCount(page, row, true);
    }

    public int getCurrentMaxRow() {
        int max = 0;
        for (TileRecord record : mRecords) {
            if (record.row > max) {
                max = record.row;
            }
        }
        return max;
    }

    public int getLeft(int page, int row, int col) {
        final boolean firstRowLarge = mFirstRowLarge && page == 0 && row == 0;
        int cols = firstRowLarge ? 2 : mColumns;
        return getLeft(row, col, cols, firstRowLarge);
    }

    public int getLeft(int page, int row, int col, int cols) {
        final boolean firstRowLarge = mFirstRowLarge && page == 0 && row == 0;
        return getLeft(row, col, cols, firstRowLarge);
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
                if (DEBUG_DRAG) {
                    Log.v(TAG, "ACTION_DRAG_STARTED on view: " + v);
                }

                if (originatingTileEvent) {
                    if (DEBUG_DRAG) {
                        Log.v(TAG, "ACTION_DRAG_STARTED on target view.");
                    }
                    mRestored = false;
                }

                break;

            case DragEvent.ACTION_DRAG_ENTERED:
                if (DEBUG_DRAG) {
                    if (targetTile != null) {
                        Log.v(TAG, "ACTION_DRAG_ENTERED on view with tile: " + targetTile);
                    } else {
                        Log.v(TAG, "ACTION_DRAG_ENTERED on view: " + v);
                    }
                }
                mLocationHits = 0;
                mMovedByLocation = false;

                if (v == mQsPanelTop) {
                    int color;
                    if (mDraggingRecord.tile instanceof EditTile) {
                        // use a different warning, user can't erase this one
                        color = mContext.getColor(R.color.qs_tile_trash_delete_tint_warning);
                    } else {
                        color = mContext.getColor(R.color.qs_tile_trash_delete_tint);
                    }

                    DrawableCompat.setTint(mQsPanelTop.getDropTargetIcon().getDrawable(), color);
                    mQsPanelTop.getDropTargetIcon().invalidate();
                }

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

                break;
            case DragEvent.ACTION_DRAG_ENDED:
                if (DEBUG_DRAG) {
                    Log.v(TAG, "ACTION_DRAG_ENDED on view: " + v + "(tile: "
                            + targetTile + "), result: " + event.getResult());
                }
                if (originatingTileEvent && !event.getResult()) {
                    // view pager probably ate the event
                    restoreDraggingTilePosition(v);
                }

                break;

            case DragEvent.ACTION_DROP:
                if (DEBUG_DRAG) {
                    Log.v(TAG, "ACTION_DROP, event loc: " + event.getX() + ", " + event.getY()
                            + " + with tile: " + targetTile + " and view: " + v);
                }
                mLastTouchLocationX = event.getX();
                mLastTouchLocationY = event.getY();

                if (isDropTargetEvent(event, v)) {
                    if (DEBUG_DRAG) {
                        Log.d(TAG, "dropping on delete target!!");
                    }
                    if (mDraggingRecord.tile instanceof EditTile) {
                        mQsPanelTop.toast(R.string.quick_settings_cannot_delete_edit_tile);
                        restoreDraggingTilePosition(v);
                        break;
                    } else {
                        mRestored = true;
                        removeDraggingRecord();
                    }
                } else {
                    restoreDraggingTilePosition(v);
                }
                break;

            case DragEvent.ACTION_DRAG_EXITED:
                if (DEBUG_DRAG) {
                    if (targetTile != null) {
                        Log.v(TAG, "ACTION_DRAG_EXITED on view with tile: " + targetTile);
                    } else {
                        Log.v(TAG, "ACTION_DRAG_EXITED on view: " + v);
                    }
                }

                if (v == mQsPanelTop) {
                    final int color = mContext.getColor(R.color.qs_tile_trash);
                    DrawableCompat.setTint(mQsPanelTop.getDropTargetIcon().getDrawable(), color);
                    mQsPanelTop.getDropTargetIcon().invalidate();
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
                    break;
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
                break;

            default:
                Log.w(TAG, "unhandled event");
                return false;
        }
        return true;
    }

    private boolean isDropTargetEvent(DragEvent event, View v) {
        if (DEBUG_DRAG) {
            Log.d(TAG, "isDropTargetEvent() called with " + "event = [" + event + "], v = [" + v + "]");
        }
        if (v == getDropTarget() || v == mQsPanelTop) {
            if (DEBUG_DRAG) {
                Log.d(TAG, "isDropTargetEvent() returns true by view");
            }
            return true;
        }

        if (v == mViewPager && mLastTouchLocationY <= getRowTop(0)) {
            if (DEBUG_DRAG) {
                Log.d(TAG, "isDropTargetEvent() returns true by loc");
            }
            return true;
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

        // see if we should animate this to the left or right off the page
        // the +1's are to account for the edit page
        if (mDraggingRecord.destinationPage > mViewPager.getCurrentItem() - 1) {
            if (DEBUG_DRAG) {
                Log.d(TAG, "adding width to animate out >>>>>");
            }
            destinationX += getWidth();
        } else if (mDraggingRecord.destinationPage < mViewPager.getCurrentItem() - 1) {
            if (DEBUG_DRAG) {
                Log.d(TAG, "removing width to animate out <<<<<");
            }
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
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mViewPager.requestDisallowInterceptTouchEvent(false);

                        removeTransientView(mDraggingRecord.tileView);

                        final QSPage targetP = getPage(mDraggingRecord.destinationPage);

                        if (DEBUG_DRAG) {
                            if (dragRecordDetached) {
                                Log.i(TAG, "drag record was detached");
                            } else {
                                Log.i(TAG, "drag record was attached");
                            }
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

        // clamp this value to the max count we want.
        int maxRows = Math.min(MAX_ROW_COUNT - 1 /* we are 0 based */, getCurrentMaxRow());

        if (tile.row > maxRows) {
            tile.destinationPage = tile.destinationPage + 1;
            tile.row = 0;
            tile.col = 0;

            if (DEBUG_DRAG) {
                Log.w(TAG, "tile's destination page moved to: " + tile.destinationPage);
            }
        }
        int columnCount = Math.max(1, getColumnCount(tile.destinationPage, tile.row, false));
        if (columnCount < maxCols) {
            // if columncount gives us 1 and we're at col 2
            columnCount = Math.max((tile.col + 1), columnCount);
        }
        if (DEBUG_DRAG) {
            Log.w(TAG, "columCount at: " + columnCount);
        }

        boolean firstRowLarge = mFirstRowLarge && tile.row == 0 && tile.destinationPage == 0;

        tile.destination.x = getLeft(tile.row, tile.col, columnCount, firstRowLarge);
        tile.destination.y = getRowTop(tile.row);

        if (DEBUG_DRAG) {
            Log.i(TAG, "---setToNextDestination() called with " + "tile = [" + tile + "], now at: "
                    + tile.destination);
        }
    }

    private boolean isBefore(DragTileRecord r1, DragTileRecord r2) {
        if (DEBUG_DRAG) {
            Log.d(TAG, "isBefore() called with " + "r1 = [" + r1 + "], r2 = [" + r2 + "]");
        }
        boolean isBefore = r1.destinationPage <= r2.destinationPage;
        if (r1.destinationPage == r2.destinationPage) {
            isBefore = r1.row <= r2.row;
            if (r1.row == r2.row) {
                isBefore = r1.col <= r2.col;
            }
        }

        if (DEBUG_DRAG) {
            Log.d(TAG, "r1 isBefore r2: " + isBefore);
        }
        return isBefore;
    }

    private void setToLastDestination(DragTileRecord record) {
        DragTileRecord last = (DragTileRecord) mRecords.get(mRecords.size() - 1);
        if (DEBUG_DRAG) {
            Log.d(TAG, "setToLastDestination() called with record = ["
                    + record + "], and last record is: " + last);
        }

        if (isBefore(record, last)) {
            // if the record is before the last record in the records list, set it to the
            // last location, then spoof it one space forward
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
            Log.e(TAG, "got a null record on touch down.");
            return false;
        }

        mDraggingRecord = record;

        mDraggingRecord.tileView.setAlpha(0);
        mDraggingRecord.tileView.setDual(false, false);
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
                if (DEBUG_DRAG) {
                    Log.e(TAG, "updating desired colum count to: " + desiredColCount);
                }
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
                                if (ti.tileView.setDual(dual, ti.tile.hasDualTargetsDetails())) {
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

    @Override
    protected void handleShowDetailImpl(Record r, boolean show, int x, int y) {
        super.handleShowDetailImpl(r, show, x, y);
        if (show) {
            final StatusBarPanelCustomTile customTile = r.detailAdapter.getCustomTile();
            mDetailRemoveButton.setVisibility(customTile != null &&
                    !(customTile.getPackage().equals(mContext.getPackageName())
                            || customTile.getUid() == android.os.Process.SYSTEM_UID)
                    ? VISIBLE : GONE);
            mDetailRemoveButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mHost.removeCustomTile(customTile);
                    closeDetail();
                }
            });
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        FontSizeUtils.updateFontSize(mDetailRemoveButton, R.dimen.qs_detail_button_text_size);
    }

    @Override
    public void setExpanded(boolean expanded) {
        super.setExpanded(expanded);
        if (!expanded) {
            if (mEditing) {
                mHost.setEditing(false);
            }
        }
    }

    public void updateResources() {
        final Resources res = mContext.getResources();
        final int columns = Math.max(1, res.getInteger(R.integer.quick_settings_num_columns));
        mCellHeight = res.getDimensionPixelSize(R.dimen.qs_tile_height);
        mCellWidth = (int) (mCellHeight * TILE_ASPECT);
        mLargeCellHeight = res.getDimensionPixelSize(R.dimen.qs_dual_tile_height);
        mLargeCellWidth = (int) (mLargeCellHeight * TILE_ASPECT);
        mPanelPaddingBottom = res.getDimensionPixelSize(R.dimen.qs_panel_padding_bottom);
        mDualTileUnderlap = res.getDimensionPixelSize(R.dimen.qs_dual_tile_padding_vertical);
        mBrightnessPaddingTop = res.getDimensionPixelSize(R.dimen.qs_brightness_padding_top);
        mPageIndicatorHeight = res.getDimensionPixelSize(R.dimen.qs_panel_page_indicator_height);
        if (mColumns != columns) {
            mColumns = columns;
            if (isLaidOut()) postInvalidate();
        }
        if (isLaidOut()) {
            for (TileRecord r : mRecords) {
                r.tile.clearState();
            }
            if (mListening) {
                refreshAllTiles();
            }
            updateDetailText();
        }
    }

    public boolean isAnimating(TileRecord t) {
        return mCurrentlyAnimating.contains(t);
    }

    public void cleanup() {
        if (mSettingsObserver != null) {
            mSettingsObserver.unobserve();
        }
    }

    public static class TilesListAdapter extends BaseExpandableListAdapter
            implements QSTile.DetailAdapter {

        public static final String PACKAGE_ANDROID = "android";

        Context mContext;
        QSTileHost mHost;
        QSDragPanel mPanel;

        ArrayMap<String, List<String>> mPackageTileMap = new ArrayMap<>();

        public TilesListAdapter(Context context, QSDragPanel panel) {
            mContext = context;
            mHost = panel.getHost();
            mPanel = panel;

            List<String> currentTileSpec = mHost.getTileSpecs();
            final Collection<String> tiles = QSUtils.getAvailableTiles(mContext);
            tiles.removeAll(currentTileSpec);

            // we'll always have a system tiles category
            mPackageTileMap.put(PACKAGE_ANDROID, new ArrayList<String>());

            final Iterator<String> i = tiles.iterator();
            while (i.hasNext()) {
                final String spec = i.next();
                if (QSUtils.isStaticQsTile(spec) || QSUtils.isDynamicQsTile(spec)) {
                    List<String> packageList = mPackageTileMap.get(PACKAGE_ANDROID);
                    packageList.add(spec);
                } else {
                    String tilePackage = getCustomTilePackage(spec);
                    List<String> packageList = mPackageTileMap.get(tilePackage);
                    if (packageList == null) {
                        mPackageTileMap.put(tilePackage, packageList = new ArrayList<>());
                    }
                    packageList.add(spec);
                }
            }

            // add broadcast tile
            mPackageTileMap.get(PACKAGE_ANDROID).add(BROADCAST_TILE_SPEC_PLACEHOLDER);

            final List<String> systemTiles = mPackageTileMap.get(PACKAGE_ANDROID);
            Collections.sort(systemTiles);
        }

        private String getCustomTilePackage(String spec) {
            StatusBarPanelCustomTile sbc = mHost.getCustomTileData().get(spec).sbc;
            return sbc.getPackage();
        }

        @Override
        public int getGroupCount() {
            return mPackageTileMap.keySet().size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return mPackageTileMap.valueAt(groupPosition).size();
        }

        @Override
        public String getGroup(int groupPosition) {
            return mPackageTileMap.keyAt(groupPosition);
        }

        @Override
        public String getChild(int groupPosition, int childPosition) {
            return mPackageTileMap.valueAt(groupPosition).get(childPosition);
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return mPackageTileMap.valueAt(groupPosition).get(childPosition).hashCode();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                                 ViewGroup parent) {
            LinearLayout row = (LinearLayout) convertView;
            if (row == null) {
                row = (LinearLayout) LayoutInflater.from(mContext)
                        .inflate(R.layout.qs_tile_category_row, parent, false);
            }
            TextView title = (TextView) row.findViewById(android.R.id.title);

            ImageView systemOrAppIcon = (ImageView) row.findViewById(android.R.id.icon);
            ImageView expansionIndicator = (ImageView) row.findViewById(android.R.id.icon2);

            expansionIndicator.setImageResource(isExpanded ? R.drawable.ic_qs_tile_contract
                    : R.drawable.ic_qs_tile_expand);
            // hide indicator when there's only 1 group
            final boolean singleGroupMode = getGroupCount() == 1;
            expansionIndicator.setVisibility(singleGroupMode ? View.GONE : View.VISIBLE);

            String group = getGroup(groupPosition);
            if (group.equals(PACKAGE_ANDROID)) {
                group = mContext.getText(R.string.quick_settings_tiles_category_system).toString();
                // special icon
                systemOrAppIcon.setImageResource(R.drawable.ic_qs_tile_category_system);
            } else {
                systemOrAppIcon.setImageResource(R.drawable.ic_qs_tile_category_other);
            }
            title.setText(group);

            if (isExpanded) {
                expansionIndicator.setColorFilter(
                        mContext.getColor(
                    R.color.qs_detailed_expansion_indicator_color), PorterDuff.Mode.SRC_ATOP);
                systemOrAppIcon.setColorFilter(
                        mContext.getColor(R.color.qs_detailed_icon_tint_color), PorterDuff.Mode.SRC_ATOP);
                title.setTextColor(mContext.getColor(R.color.qs_detailed_title_text_color));
            } else {
                title.setTextColor(mContext.getColor(R.color.qs_detailed_default_text_color));
                systemOrAppIcon.setColorFilter(null);
                expansionIndicator.setColorFilter(null);
            }
            return row;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                                 View convertView, ViewGroup parent) {
            LinearLayout child = (LinearLayout) convertView;
            if (child == null) {
                child = (LinearLayout) LayoutInflater.from(mContext)
                        .inflate(R.layout.qs_tile_child_row, parent, false);
            }
            String spec = getChild(groupPosition, childPosition);

            TextView title = (TextView) child.findViewById(android.R.id.title);
            title.setText(getQSTileLabel(spec));

            ImageView icon = (ImageView) child.findViewById(android.R.id.icon);
            icon.setImageDrawable(getQSTileIcon(spec));

            return child;
        }

        private String getQSTileLabel(String spec) {
            if (spec.equals(BROADCAST_TILE_SPEC_PLACEHOLDER)) {
                return mContext.getText(R.string.broadcast_tile).toString();
            } else if (QSUtils.isStaticQsTile(spec)) {
                int resource = QSTileHost.getLabelResource(spec);
                return mContext.getText(resource).toString();
            } else if (QSUtils.isDynamicQsTile(spec)) {
                return QSUtils.getDynamicQSTileLabel(mContext,
                        UserHandle.myUserId(), spec);
            } else {
                return getPackageLabel(getCustomTilePackage(spec));
            }
        }

        private Drawable getQSTileIcon(String spec) {
            if (QSUtils.isDynamicQsTile(spec)) {
                return QSTile.ResourceIcon.get(
                        QSUtils.getDynamicQSTileResIconId(mContext, UserHandle.myUserId(), spec))
                        .getDrawable(mContext);
            } else if (QSUtils.isStaticQsTile(spec)) {
                return QSTile.ResourceIcon.get(QSTileHost.getIconResource(spec))
                        .getDrawable(mContext);
            } else {
                QSTile<?> tile = mHost.getTile(spec);
                if (tile != null) {
                    QSTile.State state = tile.getState();
                    if (state != null && state.icon != null) {
                        return state.icon.getDrawable(mContext);
                    }
                }
                if (spec.equals(BROADCAST_TILE_SPEC_PLACEHOLDER)) {
                    return getPackageDrawable(PACKAGE_ANDROID);
                }
                return getPackageDrawable(getCustomTilePackage(spec));
            }
        }

        private String getPackageLabel(String packageName) {
            try {
                return mContext.getPackageManager().getApplicationLabel(
                        mContext.getPackageManager().getApplicationInfo(packageName, 0)).toString();
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        private Drawable getPackageDrawable(String packageName) {
            try {
                return mContext.getPackageManager().getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }

        @Override
        public int getTitle() {
            return R.string.quick_settings_tiles_add_tiles;
        }

        @Override
        public Boolean getToggleState() {
            return null;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            ExpandableListView lv = (ExpandableListView) convertView;
            if (lv == null) {
                lv = new ExpandableListView(parent.getContext());
                lv.setOnTouchListener(new OnTouchListener() {
                    // Setting on Touch Listener for handling the touch inside ScrollView
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        // Disallow the touch request for parent scroll on touch of child view
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        return false;
                    }
                });
            }
            lv.setAdapter(this);
            lv.expandGroup(mPackageTileMap.indexOfKey(PACKAGE_ANDROID));
            lv.setGroupIndicator(null);
            lv.setChildIndicator(null);
            lv.setChildDivider(new ColorDrawable(Color.TRANSPARENT));
            lv.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v,
                                            int groupPosition, int childPosition, long id) {
                    String spec = getChild(groupPosition, childPosition);
                    if (spec.equals(BROADCAST_TILE_SPEC_PLACEHOLDER)) {
                        showBroadcastTileDialog();
                    } else {
                        mPanel.add(spec);
                        mPanel.closeDetail();
                    }
                    return true;
                }
            });
            lv.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
                @Override
                public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition,
                                            long id) {
                    if (getGroupCount() == 1) {
                        // disable contracting/expanding group when there's only 1
                        return true;
                    }
                    return false;
                }
            });
            return lv;
        }

        @Override
        public Intent getSettingsIntent() {
            return null;
        }

        @Override
        public StatusBarPanelCustomTile getCustomTile() {
            return null;
        }

        @Override
        public void setToggleState(boolean state) {

        }

        @Override
        public int getMetricsCategory() {
            return CMMetricsLogger.DONT_LOG;
        }

        public void showBroadcastTileDialog() {
            final EditText editText = new EditText(mContext);
            final AlertDialog d = new AlertDialog.Builder(mContext)
                    .setTitle(R.string.broadcast_tile)
                    .setView(editText)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String action = editText.getText().toString();
                            if (isValid(action)) {
                                mPanel.add(IntentTile.PREFIX + action + ')');
                                mPanel.closeDetail();
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
    }

    public void add(String tile) {
        MetricsLogger.action(getContext(), MetricsLogger.TUNER_QS_ADD, tile);
        List<String> tiles = new ArrayList<>(mHost.getTileSpecs());
        tiles.add(tile);
        mHost.setTiles(tiles);
    }

    public boolean isDragging() {
        return mDragging;
    }

    public boolean isDragRecordAttached() {
        return mRecords.indexOf(mDraggingRecord) >= 0;
    }

    public boolean isOnSettingsPage() {
        return mEditing && mViewPager.getCurrentItem() == 0;
    }

    public void goToSettingsPage() {
        if (mEditing) {
            mViewPager.setCurrentItem(0, true);
        }
    }

    class SettingsObserver extends UserContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        @Override
        protected void observe() {
            super.observe();

            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(CMSettings.Secure.getUriFor(
                    CMSettings.Secure.QS_USE_MAIN_TILES), false, this, UserHandle.USER_ALL);
            update();
        }

        @Override
        protected void unobserve() {
            super.unobserve();

            ContentResolver resolver = mContext.getContentResolver();
            resolver.unregisterContentObserver(this);
        }

        @Override
        public void update() {
            ContentResolver resolver = mContext.getContentResolver();
            int currentUserId = ActivityManager.getCurrentUser();
            boolean firstRowLarge = CMSettings.Secure.getIntForUser(resolver,
                    CMSettings.Secure.QS_USE_MAIN_TILES, 1, currentUserId) == 1;
            if (firstRowLarge != mFirstRowLarge) {
                mFirstRowLarge = firstRowLarge;
                setTiles(new ArrayList<QSTile<?>>()); // clear out states
                setTiles(mHost.getTiles());
            }
        }
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
