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
 * limitations under the License
 */

package com.android.systemui.qs.tiles;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.MediaColumns;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.android.systemui.R;
import com.android.systemui.qs.QSDetailItems;
import com.android.systemui.qs.QSTile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Fun little camera tile. Follows {@link com.android.systemui.qs.QSTile.DetailAdapter}
 * implementation.
 * Created by Adnan on 1/17/15.
 */
public class CameraTile extends QSTile<QSTile.BooleanState>{
    private static final String MEDIA_INTENT_STRING = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA;
    private final Intent MEDIA_INTENT = new Intent(MEDIA_INTENT_STRING);
    private final CameraDetailAdapter mDetailAdapter;
    private Handler mHandler;

    public CameraTile(Host host) {
        super(host);
        mDetailAdapter = new CameraDetailAdapter();
        mHandler  = new Handler(host.getLooper().getMainLooper());
    }

    @Override
    public DetailAdapter getDetailAdapter() {
        return mDetailAdapter;
    }

    @Override
    protected void handleLongClick() {
        mHost.getContext().startActivity(MEDIA_INTENT.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    protected void handleClick() {
        showDetail(true);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.label = mContext.getString(R.string.quick_settings_camera_title);
        state.value = false;
        state.autoMirrorDrawable = false;
        state.iconId = R.drawable.ic_qs_camera;
    }

    @Override
    public void setListening(boolean listening) {
        // noopo
    }

    private final class CameraDetailAdapter implements DetailAdapter, QSDetailItems.Callback,
            TextureView.SurfaceTextureListener, Camera.PreviewCallback {
        private static final String DEFAULT_IMAGE_FILE_NAME_FORMAT = "'IMG'_yyyyMMdd_HHmmss";
        private static final int CAMERA_ID = 0;

        private FrameLayout mSurfaceLayout;
        private TextureView mTextureView;
        private View mFlashView;

        private Camera mCamera;
        private CameraOrientationListener mCameraOrientationListener = null;
        private int mOrientation;
        private int mJpegRotation;
        private int mDisplayRotation;
        private Camera.Size mCameraSize;
        private boolean mCameraStarted;
        private boolean mCameraBusy;
        private View mRootView;

        private Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
        private Camera.Parameters mParams;

        private Storage mStorage = new Storage();
        private SimpleDateFormat mImageNameFormatter;

        private Runnable mStartRunnable = new Runnable() {
            @Override
            public void run() {
                if (mCamera != null) {
                    return;
                }

                Camera.getCameraInfo(CAMERA_ID, mCameraInfo);

                try {
                    mCamera = Camera.open(CAMERA_ID);
                } catch (Exception e) {
                    //SHOW ERROR
                    return;
                }

                // Orientation listener to rotate the camera preview
                if (mCameraOrientationListener == null) {
                    mCameraOrientationListener = new CameraOrientationListener(mContext);
                }
                mCameraOrientationListener.enable();

                mParams = mCamera.getParameters();

//                // Use smallest preview size that is bigger than the tile view
//                Camera.Size previewSize = mParams.getPreviewSize();
//                for (Camera.Size size : mParams.getSupportedPreviewSizes()) {
//                    if (size.width > mRootView.getWidth() && size.height > mRootView.getHeight() &&
//                            size.width < previewSize.width && size.height < previewSize.height) {
//                        previewSize = size;
//                    }
//                }
//                mParams.setPreviewSize(previewSize.width, previewSize.height);

                // Use largest picture size
                Camera.Size pictureSize = mParams.getPictureSize();
                for (Camera.Size size : mParams.getSupportedPictureSizes()) {
                    if (size.width > pictureSize.width && size.height > pictureSize.height) {
                        pictureSize = size;
                    }
                }
                mCameraSize = pictureSize;
                mParams.setPictureSize(mCameraSize.width, mCameraSize.height);

                // Try focus with continuous modes first, then basic autofocus
                List<String> focusModes = mParams.getSupportedFocusModes();
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }

                mCamera.setParameters(mParams);
                updateOrientation();

                mTextureView = new TextureView(mContext);
                mTextureView.setVisibility(View.VISIBLE);
                mSurfaceLayout.addView(mTextureView, 0);
                mTextureView.setSurfaceTextureListener(CameraDetailAdapter.this);
            }
        };

        private Runnable mTakePictureRunnable = new Runnable() {
            @Override
            public void run() {
                if (mCamera == null) {
                    return;
                }

                // Repeat until the preview has started and we can
                // take picture
                if (!mCameraStarted) {
                    mHandler.postDelayed(this, 200);
                    return;
                }

                // To avoid crashes don't post new picture requests
                // if previous request has not returned
                if (mCameraBusy) {
                    return;
                }
                mCameraBusy = true;

                // Display flash animation above the preview
                mFlashView.setVisibility(View.VISIBLE);
                mFlashView.animate().alpha(0f).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        mFlashView.setVisibility(View.GONE);
                        mFlashView.setAlpha(1f);
                    }
                });

                // Update the JPEG rotation
                if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mJpegRotation = (mCameraInfo.orientation - mOrientation + 360) % 360;
                } else {
                    mJpegRotation = (mCameraInfo.orientation + mOrientation) % 360;
                }

                mParams.setRotation(mJpegRotation);
                mCamera.setParameters(mParams);

                // Request a picture
                try {
                    mCamera.takePicture(null, null, new Camera.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            mCameraBusy = false;

                            long time = System.currentTimeMillis();
                            int orientation = (mOrientation + mDisplayRotation) % 360;

                            mStorage.addImage(mContext.getContentResolver(),
                                    mImageNameFormatter.format(new Date(time)),
                                    time, orientation, data, mCameraSize.width,
                                    mCameraSize.height);

                            mCamera.startPreview();
                        }
                    });
                } catch (RuntimeException e) {
                    // This can happen if user is pressing the
                    // tile too fast, nothing we can do
                }
            }
        };

        private Runnable mReleaseCameraRunnable = new Runnable() {
            @Override
            public void run() {
                if (mCamera == null) {
                    return;
                }

                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
                mCameraStarted = false;
                mCameraOrientationListener.disable();

                mTextureView.setVisibility(View.GONE);
                mSurfaceLayout.removeView(mTextureView);
                mTextureView.setSurfaceTextureListener(null);
                mTextureView = null;
            }
        };

        private Runnable mAutoFocusRunnable = new Runnable() {
            @Override
            public void run() {
                if (mCameraStarted) {
                    try {
                        mCamera.autoFocus(null);
                    } catch (RuntimeException e) {
                        // In the case that autofocus throws a {@link RuntimeException}
                        // here, we should handle it gracefully instead of taking down systemui
                        Log.wtf(CameraTile.class.getSimpleName(), "Unable to autofocus");
                    }
                }
            }
        };


        @Override
        public void onDetailItemClick(QSDetailItems.Item item) {

        }

        @Override
        public void onDetailItemDisconnect(QSDetailItems.Item item) {

        }

        @Override
        public int getTitle() {
            return R.string.quick_settings_camera_title;
        }

        @Override
        public Boolean getToggleState() {
            return null;
        }

        @Override
        public View createDetailView(Context context, View convertView, ViewGroup parent) {
            // We can't reuse qsdetailitems root view, inflate our own
            mRootView = LayoutInflater.from(context).inflate(R.layout.qs_camera_detail,
                    parent, false);

            if (DEBUG) Log.d(TAG, "addOnAttachStateChangeListener");
            mRootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    if (DEBUG) Log.d(TAG, "onViewAttachedToWindow");
                    mHandler.post(mStartRunnable);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (DEBUG) Log.d(TAG, "onViewDetachedFromWindow");
                    mHandler.post(mReleaseCameraRunnable);
                    mRootView.removeCallbacks(null);
                }
            });

            String imageFileNameFormat = DEFAULT_IMAGE_FILE_NAME_FORMAT;
            try {
                final PackageManager pm = context.getPackageManager();
                final Resources camRes = pm.getResourcesForApplication("com.android.gallery3d");
                int imageFileNameFormatResId = camRes.getIdentifier(
                        "image_file_name_format", "string", "com.android.gallery3d");
                imageFileNameFormat = camRes.getString(imageFileNameFormatResId);
            } catch (PackageManager.NameNotFoundException ex) {
                // Use default
            } catch (Resources.NotFoundException ex) {
                // Use default
            }

            mImageNameFormatter = new SimpleDateFormat(imageFileNameFormat);

            mSurfaceLayout = (FrameLayout) mRootView.findViewById(R.id.camera_surface_holder);
            mFlashView = mRootView.findViewById(R.id.camera_surface_flash_overlay);

            return mRootView;
        }

        @Override
        public Intent getSettingsIntent() {
            return MEDIA_INTENT;
        }

        @Override
        public void setToggleState(boolean state) {
            // noop
        }

        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            mHandler.post(mAutoFocusRunnable);
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            // The Surface has been created, now tell the camera where
            // to draw the preview.
            try {
                mCamera.setOneShotPreviewCallback(this);
                mCamera.setPreviewTexture(surfaceTexture);
                mCamera.startPreview();
                mCameraStarted = true;
                mCameraBusy = false;
            } catch (IOException e) {
                // Try release camera
                mCamera.release();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            mHandler.post(mReleaseCameraRunnable);
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }

        private void updateOrientation() {
            final WindowManager wm = (WindowManager) mContext
                    .getSystemService(Context.WINDOW_SERVICE);
            int rotation = wm.getDefaultDisplay().getRotation();

            switch (rotation) {
                case Surface.ROTATION_0:
                default:
                    mDisplayRotation = 0;
                    break;
                case Surface.ROTATION_90:
                    mDisplayRotation = 90;
                    break;
                case Surface.ROTATION_180:
                    mDisplayRotation = 180;
                    break;
                case Surface.ROTATION_270:
                    mDisplayRotation = 270;
                    break;
            }

            int cameraOrientation;

            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraOrientation = (mCameraInfo.orientation + mDisplayRotation) % 360;
                cameraOrientation = (360 - cameraOrientation) % 360;  // compensate the mirror
            } else {
                cameraOrientation = (mCameraInfo.orientation - mDisplayRotation + 360) % 360;
            }

            mCamera.setDisplayOrientation(cameraOrientation);
        }

        private class CameraOrientationListener extends OrientationEventListener {
            public CameraOrientationListener(Context context) {
                super(context);
            }

            @Override
            public void onOrientationChanged(int orientation) {
                if (mCamera == null || orientation == ORIENTATION_UNKNOWN) {
                    return;
                }

                mOrientation = (orientation + 45) / 90 * 90;
                updateOrientation();
            }
        }

        private class Storage {
            private static final String TAG = "CameraStorage";
            private String mRoot = Environment.getExternalStorageDirectory().toString();
            private Storage() {}

            public String writeFile(String title, byte[] data) {
                String path = generateFilepath(title);
                FileOutputStream out = null;

                try {
                    out = new FileOutputStream(path);
                    out.write(data);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to write data", e);
                } finally {
                    try {
                        out.close();
                    } catch (Exception e) {
                        // Do nothing here
                    }
                }
                return path;
            }

            // Save the image and add it to media store.
            public Uri addImage(ContentResolver resolver, String title, long date,
                                int orientation, byte[] jpeg, int width, int height) {
                // Save the image.
                String path = writeFile(title, jpeg);
                return addImage(resolver, title, date, orientation, jpeg.length,
                        path, width, height);
            }

            // Add the image to media store.
            public Uri addImage(ContentResolver resolver, String title, long date,
                                int orientation, int jpegLength, String path, int width, int height) {

                try {
                    ExifInterface exif = new ExifInterface(path);
                    switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)) {
                        case ExifInterface.ORIENTATION_ROTATE_90:
                            orientation = 90;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_180:
                            orientation = 180;
                            break;
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            orientation = 270;
                            break;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to read exif", e);
                }

                if ((mJpegRotation + orientation) % 180 != 0) {
                    int temp = width;
                    width = height;
                    height = width;
                }

                // Insert into MediaStore.
                ContentValues values = new ContentValues(9);
                values.put(ImageColumns.TITLE, title);
                values.put(ImageColumns.DISPLAY_NAME, title + ".jpg");
                values.put(ImageColumns.DATE_TAKEN, date);
                values.put(ImageColumns.MIME_TYPE, "image/jpeg");

                // Clockwise rotation in degrees. 0, 90, 180, or 270.
                values.put(ImageColumns.ORIENTATION, orientation);
                values.put(ImageColumns.DATA, path);
                values.put(ImageColumns.SIZE, jpegLength);
                values.put(MediaColumns.WIDTH, width);
                values.put(MediaColumns.HEIGHT, height);

                Uri uri = null;

                try {
                    uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                } catch (Throwable th)  {
                    // This can happen when the external volume is already mounted, but
                    // MediaScanner has not notify MediaProvider to add that volume.
                    // The picture is still safe and MediaScanner will find it and
                    // insert it into MediaProvider. The only problem is that the user
                    // cannot click the thumbnail to review the picture.
                    Log.e(TAG, "Failed to write MediaStore" + th);
                }
                return uri;
            }

            private String generateDCIM() {
                return new File(mRoot, Environment.DIRECTORY_DCIM).toString();
            }

            public String generateDirectory() {
                return generateDCIM() + "/Camera";
            }

            private String generateFilepath(String title) {
                return generateDirectory() + '/' + title + ".jpg";
            }

            public String generateBucketId() {
                return String.valueOf(generateDirectory().toLowerCase().hashCode());
            }

            public int generateBucketIdInt() {
                return generateDirectory().toLowerCase().hashCode();
            }
        }
    }
}
