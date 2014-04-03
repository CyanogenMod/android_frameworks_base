/*
 * Copyright (C) 2014 SlimRoms Project
 * Author: Lars Greiss - email: kufikugel@googlemail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.systemui.slimrecent;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;

import com.android.cards.internal.CardExpand;
import com.android.systemui.R;

import java.lang.ref.WeakReference;

/**
 * This class handles our expanded card which shows the actual task screenshot.
 * The loader (#link:BitmapDownloaderTask) class is handled here as well and put
 * the loaded task screenshot for the time the task exists into the LRU cache.
 */
public class RecentExpandedCard extends CardExpand {

    private Context mContext;

    private Drawable mDefaultThumbnailBackground;

    private int mPersistentTaskId = -1;
    private String mLabel;
    private int mThumbnailWidth;
    private int mThumbnailHeight;
    private int mBottomPadding;
    private float mScaleFactor;
    private boolean mScaleFactorChanged;

    private BitmapDownloaderTask mTask;

    private boolean mReload;
    private boolean mDoNotNullBitmap;

    public RecentExpandedCard(Context context, int persistentTaskId,
            String label, float scaleFactor) {
        this(context, R.layout.recent_inner_card_expand,
                persistentTaskId, label, scaleFactor);
    }

    // Main constructor. Set the important values we need.
    public RecentExpandedCard(Context context, int innerLayout,
            int persistentTaskId, String label, float scaleFactor) {
        super(context, innerLayout);
        mContext = context;
        mPersistentTaskId = persistentTaskId;
        mLabel = label;
        mScaleFactor = scaleFactor;

        initDimensions();
    }

    // Update expanded card content.
    public void updateExpandedContent(int persistentTaskId, String label, float scaleFactor) {
        if (label != null && label.equals(mLabel)) {
            mDoNotNullBitmap = true;
        }
        mLabel = label;
        mPersistentTaskId = persistentTaskId;
        mReload = true;

        if (scaleFactor != mScaleFactor) {
            mScaleFactorChanged = true;
            mScaleFactor = scaleFactor;
            initDimensions();
        }
    }

    // Setup main dimensions we need.
    private void initDimensions() {
        final Resources res = mContext.getResources();
        // Render the default thumbnail background
        mThumbnailWidth = (int) (res.getDimensionPixelSize(
                R.dimen.recent_thumbnail_width) * mScaleFactor);
        mThumbnailHeight = (int) (res.getDimensionPixelSize(
                R.dimen.recent_thumbnail_height) * mScaleFactor);
        mBottomPadding = (int) (res.getDimensionPixelSize(
                R.dimen.recent_thumbnail_bottom_padding) * mScaleFactor);

        mDefaultThumbnailBackground = new ColorDrawableWithDimensions(
                res.getColor(R.color.card_backgroundExpand), mThumbnailWidth, mThumbnailHeight);
    }

    @Override
    public void setupInnerViewElements(ViewGroup parent, View view) {
        if (view == null || mPersistentTaskId == -1) {
            return;
        }

        // We use here a view holder to reduce expensive findViewById calls
        // when getView is called on the arrayadapter which calls setupInnerViewElements.
        // Simply just check if the given view was already tagged. If yes we know it has
        // the thumbnailView we want to have. If not we search it, give it to the viewholder
        // and tag the view for the next call to reuse the holded information later.
        ViewHolder holder;
        holder = (ViewHolder) view.getTag();

        if (holder == null) {
            holder = new ViewHolder();
            holder.thumbnailView = (RecentImageView) view.findViewById(R.id.thumbnail);
            // Take scale factor into account if it is different then default or it has changed.
            if (mScaleFactor != RecentController.DEFAULT_SCALE_FACTOR || mScaleFactorChanged) {
                mScaleFactorChanged = false;
                final ViewGroup.MarginLayoutParams layoutParams =
                        (ViewGroup.MarginLayoutParams) holder.thumbnailView.getLayoutParams();
                layoutParams.width = mThumbnailWidth;
                layoutParams.height = mThumbnailHeight;
                layoutParams.setMargins(0, 0, 0, mBottomPadding);
                holder.thumbnailView.setLayoutParams(layoutParams);
            }
            view.setTag(holder);
        }

        // Assign task bitmap to our view via async task loader. If it is just
        // a refresh of the view do not load it again
        // and use the allready present one from the LRU Cache.
        if (mTask == null || mReload) {
            if (!mDoNotNullBitmap) {
                holder.thumbnailView.setImageDrawable(mDefaultThumbnailBackground);
            }

            mReload = false;
            mDoNotNullBitmap = false;

            mTask = new BitmapDownloaderTask(holder.thumbnailView, mContext, mScaleFactor);
            mTask.executeOnExecutor(
                    AsyncTask.THREAD_POOL_EXECUTOR, mPersistentTaskId);
        } else {
            if (mTask.isLoaded()) {
                // We may have lost our thumbnail in our cache.
                // Check for it. If it is not present reload it again.
                Bitmap bitmap = CacheController.getInstance(mContext)
                        .getBitmapFromMemCache(String.valueOf(mPersistentTaskId));

                if (bitmap == null) {
                    mTask = new BitmapDownloaderTask(holder.thumbnailView, mContext, mScaleFactor);
                    mTask.executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, mPersistentTaskId);
                } else {
                    holder.thumbnailView.setImageBitmap(CacheController.getInstance(mContext)
                            .getBitmapFromMemCache(String.valueOf(mPersistentTaskId)));
                }
            }
        }
    }

    static class ViewHolder {
        RecentImageView thumbnailView;
    }

    // Loads the actual task bitmap.
    private static Bitmap loadThumbnail(int persistentTaskId, Context context, float scaleFactor) {
        if (context == null) {
            return null;
        }
        final ActivityManager am = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        return getResizedBitmap(am.getTaskTopThumbnail(persistentTaskId), context, scaleFactor);
    }

    // Resize and crop the task bitmap to the overlay values.
    private static Bitmap getResizedBitmap(Bitmap source, Context context, float scaleFactor) {
        if (source == null) {
            return null;
        }

        final Resources res = context.getResources();
        final int thumbnailWidth =
                (int) (res.getDimensionPixelSize(
                        R.dimen.recent_thumbnail_width) * scaleFactor);
        final int thumbnailHeight =
                (int) (res.getDimensionPixelSize(
                        R.dimen.recent_thumbnail_height) * scaleFactor);

        final int sourceWidth = source.getWidth();
        final int sourceHeight = source.getHeight();

        // Compute the scaling factors to fit the new height and width, respectively.
        // To cover the final image, the final scaling will be the bigger
        // of these two.
        final float xScale = (float) thumbnailWidth / sourceWidth;
        final float yScale = (float) thumbnailHeight / sourceHeight;
        final float scale = Math.max(xScale, yScale);

        // Now get the size of the source bitmap when scaled
        final float scaledWidth = scale * sourceWidth;
        final float scaledHeight = scale * sourceHeight;

        // Let's find out the left coordinates if the scaled bitmap
        // should be centered in the new size given by the parameters
        final float left = (thumbnailWidth - scaledWidth) / 2;

        // The target rectangle for the new, scaled version of the source bitmap
        final RectF targetRect = new RectF(left, 0.0f, left + scaledWidth, scaledHeight);

        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setAntiAlias(true);

        // Finally, we create a new bitmap of the specified size and draw our new,
        // scaled bitmap onto it.
        final Bitmap dest = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Config.ARGB_8888);
        final Canvas canvas = new Canvas(dest);
        canvas.drawBitmap(source, null, targetRect, paint);

        return dest;
    }

    // AsyncTask loader for the task bitmap.
    private static class BitmapDownloaderTask extends AsyncTask<Integer, Void, Bitmap> {

        private boolean mLoaded;

        private final WeakReference<RecentImageView> rImageViewReference;
        private final WeakReference<Context> rContext;

        private int mOrigPri;
        private float mScaleFactor;

        private String mLRUCacheKey;

        public BitmapDownloaderTask(RecentImageView imageView,
                Context context, float scaleFactor) {
            rImageViewReference = new WeakReference<RecentImageView>(imageView);
            rContext = new WeakReference<Context>(context);
            mScaleFactor = scaleFactor;
        }

        @Override
        protected Bitmap doInBackground(Integer... params) {
            mLoaded = false;
            mLRUCacheKey = null;
            // Save current thread priority and set it during the loading
            // to background priority.
            mOrigPri = Process.getThreadPriority(Process.myTid());
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            if (isCancelled() || rContext == null) {
                return null;
            }
            mLRUCacheKey = String.valueOf(params[0]);
            // Load and return bitmap
            return loadThumbnail(params[0], rContext.get(), mScaleFactor);
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (isCancelled()) {
                bitmap = null;
            }
            // Restore original thread priority.
            Process.setThreadPriority(mOrigPri);

            // Assign image to the view.
            if (rImageViewReference != null) {
                mLoaded = true;
                final RecentImageView imageView = rImageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    if (bitmap != null && rContext != null) {
                        final Context context = rContext.get();
                        if (context != null) {
                            // Put the loaded bitmap into the LRU cache for later use.
                            CacheController.getInstance(context)
                                    .addBitmapToMemoryCache(mLRUCacheKey, bitmap);
                        }
                    }
                }
            }
        }

        public boolean isLoaded() {
            return mLoaded;
        }
    }
}
