/*
 * Copyright (C) 2013 The ChameleonOS Project
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

package com.android.systemui.statusbar.sidebar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import com.android.systemui.R;
import com.android.systemui.statusbar.sidebar.FolderInfo.FolderListener;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends LinearLayout implements FolderListener {
    private static boolean sStaticValuesDirty = true;

    // The number of icons to display in the
    private static final int NUM_ITEMS_IN_PREVIEW = 3;

    private static final int STYLE_STACKED = 0;
    private static final int STYLE_GRID = 1;
    private static final int STYLE_CAROUSEL = 2;

    // The amount of vertical spread between items in the stack [0...1]
    private static final float PERSPECTIVE_SHIFT_FACTOR = 0.24f;

    // The degree to which the item in the back of the stack is scaled [0...1]
    // (0 means it's not scaled at all, 1 means it's scaled to nothing)
    private static final float PERSPECTIVE_SCALE_FACTOR = 0.35f;

    public static Drawable sSharedFolderLeaveBehind = null;

    private ImageView mPreviewBackground;
    private TextView mFolderName;
    private FolderInfo mInfo;
    private Folder mFolder;

    private int mNumItemsInPreview = NUM_ITEMS_IN_PREVIEW;
    private int mFolderIconStyle = STYLE_GRID;

    // These variables are all associated with the drawing of the preview; they are stored
    // as member variables for shared usage and to avoid computation on each frame
    private int mIntrinsicIconSize;
    private float mBaselineIconScale;
    private int mBaselineIconSize;
    private int mAvailableSpaceInPreview;
    private int mTotalWidth = -1;
    private int mPreviewOffsetX;
    private int mPreviewOffsetY;
    private float mMaxPerspectiveShift;
    boolean mAnimating = false;
    private static LayoutInflater sInflater;
    private static int sPreviewSize;
    private static int sPreviewPadding;

    private PreviewItemDrawingParams mParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private PreviewItemDrawingParams mAnimParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private ArrayList<AppItemInfo> mHiddenItems = new ArrayList<AppItemInfo>();

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FolderIcon(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        //mFolderIconStyle = PreferencesProvider.Interface.Homescreen.FolderIconStyle.getFolderIconStyle(getContext());
        mNumItemsInPreview = (mFolderIconStyle == STYLE_GRID) ? 4 : 3;
        sPreviewSize = context.getResources().getDimensionPixelSize(R.dimen.icon_size);
    }

    public static FolderIcon fromXml(int resId, ViewGroup group, OnClickListener listener,
            FolderInfo folderInfo, Context context, boolean isSidebar) {
        if (sInflater == null)
            sInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        FolderIcon icon = (FolderIcon) sInflater.inflate(resId, group, false);

        int textSize = context.getResources().getDimensionPixelSize(R.dimen.item_title_text_size);
        icon.mFolderName = (TextView) icon.findViewById(R.id.folder_icon_name);
        icon.mFolderName.setText(folderInfo.title);
        icon.mFolderName.setGravity(Gravity.CENTER);
        icon.mFolderName.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        icon.mPreviewBackground = (ImageView) icon.findViewById(R.id.preview_background);

        icon.setTag(folderInfo);
        icon.setOnClickListener(listener);
        icon.setDrawingCacheEnabled(true);
        icon.mInfo = folderInfo;
        Folder folder = Folder.fromXml(context, isSidebar);
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.mFolder = folder;

        folderInfo.addListener(icon);

        return icon;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        sStaticValuesDirty = true;
        return super.onSaveInstanceState();
    }

    public Folder getFolder() {
        return mFolder;
    }

    FolderInfo getFolderInfo() {
        return mInfo;
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        return (itemType == ItemInfo.TYPE_APPLICATION ||
                item != mInfo && !mInfo.opened);
    }

    public void addItem(AppItemInfo item) {
        mInfo.add(item);
    }

    public void setPreviewSize(int previewSize) {
        sPreviewSize = previewSize;
    }

    private void computePreviewDrawingParams(int drawableSize, int totalSize) {
        if (mIntrinsicIconSize != drawableSize || mTotalWidth != totalSize) {
            mIntrinsicIconSize = drawableSize;
            mTotalWidth = totalSize;

            final int previewSize = sPreviewSize;
            final int previewPadding = sPreviewPadding;

            mAvailableSpaceInPreview = (previewSize - 2 * previewPadding);
            // cos(45) = 0.707  + ~= 0.1) = 0.8f
            int adjustedAvailableSpace = (int) ((mAvailableSpaceInPreview / 2) * (1 + 0.8f));

            int unscaledHeight = (int) (mIntrinsicIconSize * (1 + PERSPECTIVE_SHIFT_FACTOR));
            mBaselineIconScale = (1.0f * adjustedAvailableSpace / unscaledHeight);

            mBaselineIconSize = (int) (mIntrinsicIconSize * mBaselineIconScale);
            mMaxPerspectiveShift = mBaselineIconSize * PERSPECTIVE_SHIFT_FACTOR;

            mPreviewOffsetX = (mTotalWidth - mAvailableSpaceInPreview) / 2;
            mPreviewOffsetY = previewPadding;
        }
    }

    private void computePreviewDrawingParams(Drawable d) {
        Rect bounds = d.getBounds();
        computePreviewDrawingParams(bounds.right - bounds.left, getMeasuredWidth());
    }

    class PreviewItemDrawingParams {
        PreviewItemDrawingParams(float transX, float transY, float scale, int overlayAlpha) {
            this.transX = transX;
            this.transY = transY;
            this.scale = scale;
            this.overlayAlpha = overlayAlpha;
        }
        float transX;
        float transY;
        float scale;
        int overlayAlpha;
        Drawable drawable;
    }

    private float getLocalCenterForIndex(int index, int[] center) {
        mParams = computePreviewItemDrawingParams(Math.min(mNumItemsInPreview, index), mParams);

        mParams.transX += mPreviewOffsetX;
        mParams.transY += mPreviewOffsetY;
        float offsetX = mParams.transX + (mParams.scale * mIntrinsicIconSize) / 2;
        float offsetY = mParams.transY + (mParams.scale * mIntrinsicIconSize) / 2;

        center[0] = Math.round(offsetX);
        center[1] = Math.round(offsetY);
        return mParams.scale;
    }

    private PreviewItemDrawingParams computePreviewItemDrawingParams(int index,
            PreviewItemDrawingParams params) {
        switch (mFolderIconStyle) {
            case STYLE_STACKED:
                return computePreviewItemDrawingParamsStacked(index, params);
            case STYLE_GRID:
                return computePreviewItemDrawingParamsGrid(index, params);
            case STYLE_CAROUSEL:
                return computePreviewItemDrawingParamsCarousel(index, params);
        }
        return params;
    }

    private PreviewItemDrawingParams computePreviewItemDrawingParamsStacked(int index,
            PreviewItemDrawingParams params) {
        index = mNumItemsInPreview - index - 1;
        float r = (index * 1.0f) / (mNumItemsInPreview - 1);
        float scale = (1 - PERSPECTIVE_SCALE_FACTOR * (1 - r));

        float offset = (1 - r) * mMaxPerspectiveShift;
        float scaledSize = scale * mBaselineIconSize;
        float scaleOffsetCorrection = (1 - scale) * mBaselineIconSize;

        // We want to imagine our coordinates from the bottom left, growing up and to the
        // right. This is natural for the x-axis, but for the y-axis, we have to invert things.
        float transY = mAvailableSpaceInPreview - (offset + scaledSize + scaleOffsetCorrection);
        float transX = offset + scaleOffsetCorrection;
        float totalScale = mBaselineIconScale * scale;
        final int overlayAlpha = (int) (80 * (1 - r));

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha);
        } else {
            params.transX = transX;
            params.transY = transY;
            params.scale = totalScale;
            params.overlayAlpha = overlayAlpha;
        }
        return params;
    }

    private PreviewItemDrawingParams computePreviewItemDrawingParamsGrid(int index,
            PreviewItemDrawingParams params) {
        //index = mNumItemsInPreview - index - 1;
        float iconScale = 0.45f;

        // We want to imagine our coordinates from the bottom left, growing up and to the
        // right. This is natural for the x-axis, but for the y-axis, we have to invert things.
        float totalCellSize = mAvailableSpaceInPreview / 2;
        float iconSize = mIntrinsicIconSize * iconScale;
        float cellOffset = (totalCellSize - iconSize) / 2f;
        int cellX = index % (mNumItemsInPreview / 2);
        int cellY = index / (mNumItemsInPreview / 2);
        float xOffset = (totalCellSize * cellX) + cellOffset;
        float yOffset = (totalCellSize * cellY) + cellOffset + mPreviewOffsetY;
        float transX = xOffset;
        float transY = yOffset;
        final int overlayAlpha = 0;

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, iconScale, overlayAlpha);
        } else {
            params.transX = transX;
            params.transY = transY;
            params.scale = iconScale;
            params.overlayAlpha = overlayAlpha;
        }
        return params;
    }

    private PreviewItemDrawingParams computePreviewItemDrawingParamsCarousel(int index,
                                                                            PreviewItemDrawingParams params) {
        float r = (index == 0) ? ((mNumItemsInPreview - 2) * 1.0f) / (mNumItemsInPreview - 1) :
                0;
        float scale = (1 - PERSPECTIVE_SCALE_FACTOR * (1 - r));

        float yOffset;
        float xOffset;
        int alpha;
        float scaledSize = scale * mBaselineIconSize;
        if (index > 0 ) {
            yOffset = scaledSize/3;
            xOffset = index == 1 ? 0f : mAvailableSpaceInPreview - scaledSize;
            alpha = 80;
        } else {
            yOffset = mMaxPerspectiveShift + scaledSize/3;
            xOffset = (mAvailableSpaceInPreview - scaledSize) / 2;
            alpha = 0;
        }
        //float scaleOffsetCorrection = (1 - scale) * mBaselineIconSize;

        // We want to imagine our coordinates from the bottom left, growing up and to the
        // right. This is natural for the x-axis, but for the y-axis, we have to invert things.
        float transY = yOffset;// + scaledSize + scaleOffsetCorrection);
        float transX = xOffset;// + scaleOffsetCorrection;
        float totalScale = mBaselineIconScale * scale;
        final int overlayAlpha = alpha;//(int) (80 * (1 - r));

        if (params == null) {
            params = new PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha);
        } else {
            params.transX = transX;
            params.transY = transY;
            params.scale = totalScale;
            params.overlayAlpha = overlayAlpha;
        }
        return params;
    }

    private void drawPreviewItem(Canvas canvas, PreviewItemDrawingParams params) {
        canvas.save();
        canvas.translate(params.transX + mPreviewOffsetX, params.transY + mPreviewOffsetY);
        canvas.scale(params.scale, params.scale);
        Drawable d = params.drawable;

        if (d != null) {
            d.setBounds(0, 0, mIntrinsicIconSize, mIntrinsicIconSize);
            d.setFilterBitmap(true);
            d.setColorFilter(Color.argb(params.overlayAlpha, 0, 0, 0), PorterDuff.Mode.SRC_ATOP);
            d.draw(canvas);
            d.clearColorFilter();
            d.setFilterBitmap(false);
        }
        canvas.restore();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mFolder == null) return;
        if (mFolder.getItemCount() == 0) return;

        ArrayList<View> items = mFolder.getItemsInReadingOrder();
        Drawable d;
        TextView v;

        // Update our drawing parameters if necessary
        if (mAnimating) {
            computePreviewDrawingParams(mAnimParams.drawable);
        } else {
            v = (TextView) items.get(0);
            d = v.getCompoundDrawables()[1];
            computePreviewDrawingParams(d);
        }

        int nItemsInPreview = Math.min(items.size(), mNumItemsInPreview);
        if (!mAnimating) {
            for (int i = nItemsInPreview - 1; i >= 0; i--) {
                v = (TextView) items.get(i);
                if (!mHiddenItems.contains(v.getTag())) {
                    d = v.getCompoundDrawables()[1];
                    mParams = computePreviewItemDrawingParams(i, mParams);
                    mParams.drawable = d;
                    drawPreviewItem(canvas, mParams);
                }
            }
        } else {
            drawPreviewItem(canvas, mAnimParams);
        }
    }

    public void setTextVisible(boolean visible) {
        if (visible) {
            mFolderName.setVisibility(VISIBLE);
        } else {
            mFolderName.setVisibility(GONE);
        }
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    public void onItemsChanged() {
        invalidate();
        requestLayout();
    }

    public void onAdd(AppItemInfo item) {
        invalidate();
        requestLayout();
    }

    public void onRemove(AppItemInfo item) {
        invalidate();
        requestLayout();
    }

    public void onTitleChanged(CharSequence title) {
        mFolderName.setText(title.toString());
        setContentDescription(title);
    }
}
