package com.android.systemui.statusbar.phone;

import android.view.View;

/*
    Allows mirroring of view states such as alpha, translation...etc
 */
public class ViewLinker<T extends View & ViewLinker.ViewLinkerParent> {

    public static final int LINK_ALPHA = 0x1;
    public static final int LINK_TRANSLATION = 0x2;

    private final LinkInfo[] mLinkedViews;
    private final T mParent;

    public interface ViewLinkerCallback {
        void onAlphaChanged(float alpha);
        void onTranslationXChanged(float translationX);
    }

    public interface ViewLinkerParent {
        void registerLinker(ViewLinkerCallback callback);
    }

    public static class LinkInfo {
        private View mView;
        private int mFlags;
        public LinkInfo(View v, int linkFlags) {
            mView = v;
            mFlags = linkFlags;
        }
        private boolean supportsFlag(int flag) {
            return (mFlags & flag) != 0;
        }
    }

    private ViewLinkerCallback mCallback = new ViewLinkerCallback() {
        @Override
        public void onAlphaChanged(float alpha) {
            for (LinkInfo v : mLinkedViews) {
                if (v.supportsFlag(LINK_ALPHA)) {
                    v.mView.setAlpha(alpha);
                }
            }
        }

        @Override
        public void onTranslationXChanged(float translationX) {
            for (LinkInfo v : mLinkedViews) {
                if (v.supportsFlag(LINK_TRANSLATION)) {
                    v.mView.setTranslationX(translationX);
                }
            }
        }
    };

    public ViewLinker(T parent, LinkInfo... viewsToLink) {
        mLinkedViews = viewsToLink;
        mParent = parent;
        ensureParentNotInLink();
        parent.registerLinker(mCallback);
    }

    private void ensureParentNotInLink() {
        for (LinkInfo v : mLinkedViews) {
            if (v.mView == mParent) {
                throw new IllegalStateException("Parent cannot be" +
                        "one of the linked views");
            }
        }
    }

    public View getParent() {
        return mParent;
    }
}
