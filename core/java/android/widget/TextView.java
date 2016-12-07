/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.widget;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR1;

import android.R;
import android.annotation.ColorInt;
import android.annotation.DrawableRes;
import android.annotation.FloatRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.Size;
import android.annotation.StringRes;
import android.annotation.StyleRes;
import android.annotation.XmlRes;
import android.app.Activity;
import android.app.assist.AssistStructure;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.UndoManager;
import android.content.res.ColorStateList;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.ParcelableParcel;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.BoringLayout;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.GetChars;
import android.text.GraphicsOperations;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Layout;
import android.text.ParcelableSpan;
import android.text.Selection;
import android.text.SpanWatcher;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.TextWatcher;
import android.text.method.AllCapsTransformationMethod;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.DateKeyListener;
import android.text.method.DateTimeKeyListener;
import android.text.method.DialerKeyListener;
import android.text.method.DigitsKeyListener;
import android.text.method.KeyListener;
import android.text.method.LinkMovementMethod;
import android.text.method.MetaKeyKeyListener;
import android.text.method.MovementMethod;
import android.text.method.PasswordTransformationMethod;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TextKeyListener;
import android.text.method.TimeKeyListener;
import android.text.method.TransformationMethod;
import android.text.method.TransformationMethod2;
import android.text.method.WordIterator;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ParagraphStyle;
import android.text.style.SpellCheckSpan;
import android.text.style.SuggestionSpan;
import android.text.style.URLSpan;
import android.text.style.UpdateAppearance;
import android.text.util.Linkify;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.AccessibilityIterators.TextSegmentIterator;
import android.view.ActionMode;
import android.view.Choreographer;
import android.view.ContextMenu;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewHierarchyEncoder;
import android.view.ViewParent;
import android.view.ViewRootImpl;
import android.view.ViewStructure;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.textservice.SpellCheckerSubtype;
import android.view.textservice.TextServicesManager;
import android.widget.RemoteViews.RemoteView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FastMath;
import com.android.internal.widget.EditableInputConnection;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Displays text to the user and optionally allows them to edit it.  A TextView
 * is a complete text editor, however the basic class is configured to not
 * allow editing; see {@link EditText} for a subclass that configures the text
 * view for editing.
 *
 * <p>
 * To allow users to copy some or all of the TextView's value and paste it somewhere else, set the
 * XML attribute {@link android.R.styleable#TextView_textIsSelectable
 * android:textIsSelectable} to "true" or call
 * {@link #setTextIsSelectable setTextIsSelectable(true)}. The {@code textIsSelectable} flag
 * allows users to make selection gestures in the TextView, which in turn triggers the system's
 * built-in copy/paste controls.
 * <p>
 * <b>XML attributes</b>
 * <p>
 * See {@link android.R.styleable#TextView TextView Attributes},
 * {@link android.R.styleable#View View Attributes}
 *
 * @attr ref android.R.styleable#TextView_text
 * @attr ref android.R.styleable#TextView_bufferType
 * @attr ref android.R.styleable#TextView_hint
 * @attr ref android.R.styleable#TextView_textColor
 * @attr ref android.R.styleable#TextView_textColorHighlight
 * @attr ref android.R.styleable#TextView_textColorHint
 * @attr ref android.R.styleable#TextView_textAppearance
 * @attr ref android.R.styleable#TextView_textColorLink
 * @attr ref android.R.styleable#TextView_textSize
 * @attr ref android.R.styleable#TextView_textScaleX
 * @attr ref android.R.styleable#TextView_fontFamily
 * @attr ref android.R.styleable#TextView_typeface
 * @attr ref android.R.styleable#TextView_textStyle
 * @attr ref android.R.styleable#TextView_cursorVisible
 * @attr ref android.R.styleable#TextView_maxLines
 * @attr ref android.R.styleable#TextView_maxHeight
 * @attr ref android.R.styleable#TextView_lines
 * @attr ref android.R.styleable#TextView_height
 * @attr ref android.R.styleable#TextView_minLines
 * @attr ref android.R.styleable#TextView_minHeight
 * @attr ref android.R.styleable#TextView_maxEms
 * @attr ref android.R.styleable#TextView_maxWidth
 * @attr ref android.R.styleable#TextView_ems
 * @attr ref android.R.styleable#TextView_width
 * @attr ref android.R.styleable#TextView_minEms
 * @attr ref android.R.styleable#TextView_minWidth
 * @attr ref android.R.styleable#TextView_gravity
 * @attr ref android.R.styleable#TextView_scrollHorizontally
 * @attr ref android.R.styleable#TextView_password
 * @attr ref android.R.styleable#TextView_singleLine
 * @attr ref android.R.styleable#TextView_selectAllOnFocus
 * @attr ref android.R.styleable#TextView_includeFontPadding
 * @attr ref android.R.styleable#TextView_maxLength
 * @attr ref android.R.styleable#TextView_shadowColor
 * @attr ref android.R.styleable#TextView_shadowDx
 * @attr ref android.R.styleable#TextView_shadowDy
 * @attr ref android.R.styleable#TextView_shadowRadius
 * @attr ref android.R.styleable#TextView_autoLink
 * @attr ref android.R.styleable#TextView_linksClickable
 * @attr ref android.R.styleable#TextView_numeric
 * @attr ref android.R.styleable#TextView_digits
 * @attr ref android.R.styleable#TextView_phoneNumber
 * @attr ref android.R.styleable#TextView_inputMethod
 * @attr ref android.R.styleable#TextView_capitalize
 * @attr ref android.R.styleable#TextView_autoText
 * @attr ref android.R.styleable#TextView_editable
 * @attr ref android.R.styleable#TextView_freezesText
 * @attr ref android.R.styleable#TextView_ellipsize
 * @attr ref android.R.styleable#TextView_drawableTop
 * @attr ref android.R.styleable#TextView_drawableBottom
 * @attr ref android.R.styleable#TextView_drawableRight
 * @attr ref android.R.styleable#TextView_drawableLeft
 * @attr ref android.R.styleable#TextView_drawableStart
 * @attr ref android.R.styleable#TextView_drawableEnd
 * @attr ref android.R.styleable#TextView_drawablePadding
 * @attr ref android.R.styleable#TextView_drawableTint
 * @attr ref android.R.styleable#TextView_drawableTintMode
 * @attr ref android.R.styleable#TextView_lineSpacingExtra
 * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
 * @attr ref android.R.styleable#TextView_marqueeRepeatLimit
 * @attr ref android.R.styleable#TextView_inputType
 * @attr ref android.R.styleable#TextView_imeOptions
 * @attr ref android.R.styleable#TextView_privateImeOptions
 * @attr ref android.R.styleable#TextView_imeActionLabel
 * @attr ref android.R.styleable#TextView_imeActionId
 * @attr ref android.R.styleable#TextView_editorExtras
 * @attr ref android.R.styleable#TextView_elegantTextHeight
 * @attr ref android.R.styleable#TextView_letterSpacing
 * @attr ref android.R.styleable#TextView_fontFeatureSettings
 * @attr ref android.R.styleable#TextView_breakStrategy
 * @attr ref android.R.styleable#TextView_hyphenationFrequency
 */
@RemoteView
public class TextView extends View implements ViewTreeObserver.OnPreDrawListener {
    static final String LOG_TAG = "TextView";
    static final boolean DEBUG_EXTRACT = false;

    // Enum for the "typeface" XML parameter.
    // TODO: How can we get this from the XML instead of hardcoding it here?
    private static final int SANS = 1;
    private static final int SERIF = 2;
    private static final int MONOSPACE = 3;

    // Bitfield for the "numeric" XML parameter.
    // TODO: How can we get this from the XML instead of hardcoding it here?
    private static final int SIGNED = 2;
    private static final int DECIMAL = 4;

    /**
     * Draw marquee text with fading edges as usual
     */
    private static final int MARQUEE_FADE_NORMAL = 0;

    /**
     * Draw marquee text as ellipsize end while inactive instead of with the fade.
     * (Useful for devices where the fade can be expensive if overdone)
     */
    private static final int MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS = 1;

    /**
     * Draw marquee text with fading edges because it is currently active/animating.
     */
    private static final int MARQUEE_FADE_SWITCH_SHOW_FADE = 2;

    private static final int LINES = 1;
    private static final int EMS = LINES;
    private static final int PIXELS = 2;

    private static final RectF TEMP_RECTF = new RectF();

    /** @hide */
    static final int VERY_WIDE = 1024 * 1024; // XXX should be much larger
    private static final int ANIMATED_SCROLL_GAP = 250;

    private static final InputFilter[] NO_FILTERS = new InputFilter[0];
    private static final Spanned EMPTY_SPANNED = new SpannedString("");

    private static final int CHANGE_WATCHER_PRIORITY = 100;

    // New state used to change background based on whether this TextView is multiline.
    private static final int[] MULTILINE_STATE_SET = { R.attr.state_multiline };

    // Accessibility action to share selected text.
    private static final int ACCESSIBILITY_ACTION_SHARE = 0x10000000;

    /**
     * @hide
     */
    // Accessibility action start id for "process text" actions.
    static final int ACCESSIBILITY_ACTION_PROCESS_TEXT_START_ID = 0x10000100;

    /**
     * @hide
     */
    static final int PROCESS_TEXT_REQUEST_CODE = 100;

    /**
     *  Return code of {@link #doKeyDown}.
     */
    private static final int KEY_EVENT_NOT_HANDLED = 0;
    private static final int KEY_EVENT_HANDLED = -1;
    private static final int KEY_DOWN_HANDLED_BY_KEY_LISTENER = 1;
    private static final int KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD = 2;

    // System wide time for last cut, copy or text changed action.
    static long sLastCutCopyOrTextChangedTime;

    private ColorStateList mTextColor;
    private ColorStateList mHintTextColor;
    private ColorStateList mLinkTextColor;
    @ViewDebug.ExportedProperty(category = "text")
    private int mCurTextColor;
    private int mCurHintTextColor;
    private boolean mFreezesText;

    private Editable.Factory mEditableFactory = Editable.Factory.getInstance();
    private Spannable.Factory mSpannableFactory = Spannable.Factory.getInstance();

    private float mShadowRadius, mShadowDx, mShadowDy;
    private int mShadowColor;

    private boolean mPreDrawRegistered;
    private boolean mPreDrawListenerDetached;

    // A flag to prevent repeated movements from escaping the enclosing text view. The idea here is
    // that if a user is holding down a movement key to traverse text, we shouldn't also traverse
    // the view hierarchy. On the other hand, if the user is using the movement key to traverse views
    // (i.e. the first movement was to traverse out of this view, or this view was traversed into by
    // the user holding the movement key down) then we shouldn't prevent the focus from changing.
    private boolean mPreventDefaultMovement;

    private TextUtils.TruncateAt mEllipsize;

    static class Drawables {
        static final int LEFT = 0;
        static final int TOP = 1;
        static final int RIGHT = 2;
        static final int BOTTOM = 3;

        static final int DRAWABLE_NONE = -1;
        static final int DRAWABLE_RIGHT = 0;
        static final int DRAWABLE_LEFT = 1;

        final Rect mCompoundRect = new Rect();

        final Drawable[] mShowing = new Drawable[4];

        ColorStateList mTintList;
        PorterDuff.Mode mTintMode;
        boolean mHasTint;
        boolean mHasTintMode;

        Drawable mDrawableStart, mDrawableEnd, mDrawableError, mDrawableTemp;
        Drawable mDrawableLeftInitial, mDrawableRightInitial;

        boolean mIsRtlCompatibilityMode;
        boolean mOverride;

        int mDrawableSizeTop, mDrawableSizeBottom, mDrawableSizeLeft, mDrawableSizeRight,
                mDrawableSizeStart, mDrawableSizeEnd, mDrawableSizeError, mDrawableSizeTemp;

        int mDrawableWidthTop, mDrawableWidthBottom, mDrawableHeightLeft, mDrawableHeightRight,
                mDrawableHeightStart, mDrawableHeightEnd, mDrawableHeightError, mDrawableHeightTemp;

        int mDrawablePadding;

        int mDrawableSaved = DRAWABLE_NONE;

        public Drawables(Context context) {
            final int targetSdkVersion = context.getApplicationInfo().targetSdkVersion;
            mIsRtlCompatibilityMode = (targetSdkVersion < JELLY_BEAN_MR1 ||
                !context.getApplicationInfo().hasRtlSupport());
            mOverride = false;
        }

        /**
         * @return {@code true} if this object contains metadata that needs to
         *         be retained, {@code false} otherwise
         */
        public boolean hasMetadata() {
            return mDrawablePadding != 0 || mHasTintMode || mHasTint;
        }

        /**
         * Updates the list of displayed drawables to account for the current
         * layout direction.
         *
         * @param layoutDirection the current layout direction
         * @return {@code true} if the displayed drawables changed
         */
        public boolean resolveWithLayoutDirection(int layoutDirection) {
            final Drawable previousLeft = mShowing[Drawables.LEFT];
            final Drawable previousRight = mShowing[Drawables.RIGHT];

            // First reset "left" and "right" drawables to their initial values
            mShowing[Drawables.LEFT] = mDrawableLeftInitial;
            mShowing[Drawables.RIGHT] = mDrawableRightInitial;

            if (mIsRtlCompatibilityMode) {
                // Use "start" drawable as "left" drawable if the "left" drawable was not defined
                if (mDrawableStart != null && mShowing[Drawables.LEFT] == null) {
                    mShowing[Drawables.LEFT] = mDrawableStart;
                    mDrawableSizeLeft = mDrawableSizeStart;
                    mDrawableHeightLeft = mDrawableHeightStart;
                }
                // Use "end" drawable as "right" drawable if the "right" drawable was not defined
                if (mDrawableEnd != null && mShowing[Drawables.RIGHT] == null) {
                    mShowing[Drawables.RIGHT] = mDrawableEnd;
                    mDrawableSizeRight = mDrawableSizeEnd;
                    mDrawableHeightRight = mDrawableHeightEnd;
                }
            } else {
                // JB-MR1+ normal case: "start" / "end" drawables are overriding "left" / "right"
                // drawable if and only if they have been defined
                switch(layoutDirection) {
                    case LAYOUT_DIRECTION_RTL:
                        if (mOverride) {
                            mShowing[Drawables.RIGHT] = mDrawableStart;
                            mDrawableSizeRight = mDrawableSizeStart;
                            mDrawableHeightRight = mDrawableHeightStart;

                            mShowing[Drawables.LEFT] = mDrawableEnd;
                            mDrawableSizeLeft = mDrawableSizeEnd;
                            mDrawableHeightLeft = mDrawableHeightEnd;
                        }
                        break;

                    case LAYOUT_DIRECTION_LTR:
                    default:
                        if (mOverride) {
                            mShowing[Drawables.LEFT] = mDrawableStart;
                            mDrawableSizeLeft = mDrawableSizeStart;
                            mDrawableHeightLeft = mDrawableHeightStart;

                            mShowing[Drawables.RIGHT] = mDrawableEnd;
                            mDrawableSizeRight = mDrawableSizeEnd;
                            mDrawableHeightRight = mDrawableHeightEnd;
                        }
                        break;
                }
            }

            applyErrorDrawableIfNeeded(layoutDirection);

            return mShowing[Drawables.LEFT] != previousLeft
                    || mShowing[Drawables.RIGHT] != previousRight;
        }

        public void setErrorDrawable(Drawable dr, TextView tv) {
            if (mDrawableError != dr && mDrawableError != null) {
                mDrawableError.setCallback(null);
            }
            mDrawableError = dr;

            if (mDrawableError != null) {
                final Rect compoundRect = mCompoundRect;
                final int[] state = tv.getDrawableState();

                mDrawableError.setState(state);
                mDrawableError.copyBounds(compoundRect);
                mDrawableError.setCallback(tv);
                mDrawableSizeError = compoundRect.width();
                mDrawableHeightError = compoundRect.height();
            } else {
                mDrawableSizeError = mDrawableHeightError = 0;
            }
        }

        private void applyErrorDrawableIfNeeded(int layoutDirection) {
            // first restore the initial state if needed
            switch (mDrawableSaved) {
                case DRAWABLE_LEFT:
                    mShowing[Drawables.LEFT] = mDrawableTemp;
                    mDrawableSizeLeft = mDrawableSizeTemp;
                    mDrawableHeightLeft = mDrawableHeightTemp;
                    break;
                case DRAWABLE_RIGHT:
                    mShowing[Drawables.RIGHT] = mDrawableTemp;
                    mDrawableSizeRight = mDrawableSizeTemp;
                    mDrawableHeightRight = mDrawableHeightTemp;
                    break;
                case DRAWABLE_NONE:
                default:
            }
            // then, if needed, assign the Error drawable to the correct location
            if (mDrawableError != null) {
                switch(layoutDirection) {
                    case LAYOUT_DIRECTION_RTL:
                        mDrawableSaved = DRAWABLE_LEFT;

                        mDrawableTemp = mShowing[Drawables.LEFT];
                        mDrawableSizeTemp = mDrawableSizeLeft;
                        mDrawableHeightTemp = mDrawableHeightLeft;

                        mShowing[Drawables.LEFT] = mDrawableError;
                        mDrawableSizeLeft = mDrawableSizeError;
                        mDrawableHeightLeft = mDrawableHeightError;
                        break;
                    case LAYOUT_DIRECTION_LTR:
                    default:
                        mDrawableSaved = DRAWABLE_RIGHT;

                        mDrawableTemp = mShowing[Drawables.RIGHT];
                        mDrawableSizeTemp = mDrawableSizeRight;
                        mDrawableHeightTemp = mDrawableHeightRight;

                        mShowing[Drawables.RIGHT] = mDrawableError;
                        mDrawableSizeRight = mDrawableSizeError;
                        mDrawableHeightRight = mDrawableHeightError;
                        break;
                }
            }
        }
    }

    Drawables mDrawables;

    private CharWrapper mCharWrapper;

    private Marquee mMarquee;
    private boolean mRestartMarquee;

    private int mMarqueeRepeatLimit = 3;

    private int mLastLayoutDirection = -1;

    /**
     * On some devices the fading edges add a performance penalty if used
     * extensively in the same layout. This mode indicates how the marquee
     * is currently being shown, if applicable. (mEllipsize will == MARQUEE)
     */
    private int mMarqueeFadeMode = MARQUEE_FADE_NORMAL;

    /**
     * When mMarqueeFadeMode is not MARQUEE_FADE_NORMAL, this stores
     * the layout that should be used when the mode switches.
     */
    private Layout mSavedMarqueeModeLayout;

    @ViewDebug.ExportedProperty(category = "text")
    private CharSequence mText;
    private CharSequence mTransformed;
    private BufferType mBufferType = BufferType.NORMAL;

    private CharSequence mHint;
    private Layout mHintLayout;

    private MovementMethod mMovement;

    private TransformationMethod mTransformation;
    private boolean mAllowTransformationLengthChange;
    private ChangeWatcher mChangeWatcher;

    private ArrayList<TextWatcher> mListeners;

    // display attributes
    private final TextPaint mTextPaint;
    private boolean mUserSetTextScaleX;
    private Layout mLayout;
    private boolean mLocalesChanged = false;

    @ViewDebug.ExportedProperty(category = "text")
    private int mGravity = Gravity.TOP | Gravity.START;
    private boolean mHorizontallyScrolling;

    private int mAutoLinkMask;
    private boolean mLinksClickable = true;

    private float mSpacingMult = 1.0f;
    private float mSpacingAdd = 0.0f;

    private int mBreakStrategy;
    private int mHyphenationFrequency;

    private int mMaximum = Integer.MAX_VALUE;
    private int mMaxMode = LINES;
    private int mMinimum = 0;
    private int mMinMode = LINES;

    private int mOldMaximum = mMaximum;
    private int mOldMaxMode = mMaxMode;

    private int mMaxWidth = Integer.MAX_VALUE;
    private int mMaxWidthMode = PIXELS;
    private int mMinWidth = 0;
    private int mMinWidthMode = PIXELS;

    private boolean mSingleLine;
    private int mDesiredHeightAtMeasure = -1;
    private boolean mIncludePad = true;
    private int mDeferScroll = -1;

    // tmp primitives, so we don't alloc them on each draw
    private Rect mTempRect;
    private long mLastScroll;
    private Scroller mScroller;

    private BoringLayout.Metrics mBoring, mHintBoring;
    private BoringLayout mSavedLayout, mSavedHintLayout;

    private TextDirectionHeuristic mTextDir;

    private InputFilter[] mFilters = NO_FILTERS;

    private volatile Locale mCurrentSpellCheckerLocaleCache;

    // It is possible to have a selection even when mEditor is null (programmatically set, like when
    // a link is pressed). These highlight-related fields do not go in mEditor.
    int mHighlightColor = 0x6633B5E5;
    private Path mHighlightPath;
    private final Paint mHighlightPaint;
    private boolean mHighlightPathBogus = true;

    // Although these fields are specific to editable text, they are not added to Editor because
    // they are defined by the TextView's style and are theme-dependent.
    int mCursorDrawableRes;
    // These six fields, could be moved to Editor, since we know their default values and we
    // could condition the creation of the Editor to a non standard value. This is however
    // brittle since the hardcoded values here (such as
    // com.android.internal.R.drawable.text_select_handle_left) would have to be updated if the
    // default style is modified.
    int mTextSelectHandleLeftRes;
    int mTextSelectHandleRightRes;
    int mTextSelectHandleRes;
    int mTextEditSuggestionItemLayout;
    int mTextEditSuggestionContainerLayout;
    int mTextEditSuggestionHighlightStyle;

    /**
     * EditText specific data, created on demand when one of the Editor fields is used.
     * See {@link #createEditorIfNeeded()}.
     */
    private Editor mEditor;

    private static final int DEVICE_PROVISIONED_UNKNOWN = 0;
    private static final int DEVICE_PROVISIONED_NO = 1;
    private static final int DEVICE_PROVISIONED_YES = 2;

    /**
     * Some special options such as sharing selected text should only be shown if the device
     * is provisioned. Only check the provisioned state once for a given view instance.
     */
    private int mDeviceProvisionedState = DEVICE_PROVISIONED_UNKNOWN;

    /**
     * Kick-start the font cache for the zygote process (to pay the cost of
     * initializing freetype for our default font only once).
     * @hide
     */
    public static void preloadFontCache() {
        Paint p = new Paint();
        p.setAntiAlias(true);
        // We don't care about the result, just the side-effect of measuring.
        p.measureText("H");
    }

    /**
     * Interface definition for a callback to be invoked when an action is
     * performed on the editor.
     */
    public interface OnEditorActionListener {
        /**
         * Called when an action is being performed.
         *
         * @param v The view that was clicked.
         * @param actionId Identifier of the action.  This will be either the
         * identifier you supplied, or {@link EditorInfo#IME_NULL
         * EditorInfo.IME_NULL} if being called due to the enter key
         * being pressed.
         * @param event If triggered by an enter key, this is the event;
         * otherwise, this is null.
         * @return Return true if you have consumed the action, else false.
         */
        boolean onEditorAction(TextView v, int actionId, KeyEvent event);
    }

    public TextView(Context context) {
        this(context, null);
    }

    public TextView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.textViewStyle);
    }

    public TextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressWarnings("deprecation")
    public TextView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        mText = "";

        final Resources res = getResources();
        final CompatibilityInfo compat = res.getCompatibilityInfo();

        mTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.density = res.getDisplayMetrics().density;
        mTextPaint.setCompatibilityScaling(compat.applicationScale);

        mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHighlightPaint.setCompatibilityScaling(compat.applicationScale);

        mMovement = getDefaultMovementMethod();

        mTransformation = null;

        int textColorHighlight = 0;
        ColorStateList textColor = null;
        ColorStateList textColorHint = null;
        ColorStateList textColorLink = null;
        int textSize = 15;
        String fontFamily = null;
        boolean fontFamilyExplicit = false;
        int typefaceIndex = -1;
        int styleIndex = -1;
        boolean allCaps = false;
        int shadowcolor = 0;
        float dx = 0, dy = 0, r = 0;
        boolean elegant = false;
        float letterSpacing = 0;
        String fontFeatureSettings = null;
        mBreakStrategy = Layout.BREAK_STRATEGY_SIMPLE;
        mHyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE;

        final Resources.Theme theme = context.getTheme();

        /*
         * Look the appearance up without checking first if it exists because
         * almost every TextView has one and it greatly simplifies the logic
         * to be able to parse the appearance first and then let specific tags
         * for this View override it.
         */
        TypedArray a = theme.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.TextViewAppearance, defStyleAttr, defStyleRes);
        TypedArray appearance = null;
        int ap = a.getResourceId(
                com.android.internal.R.styleable.TextViewAppearance_textAppearance, -1);
        a.recycle();
        if (ap != -1) {
            appearance = theme.obtainStyledAttributes(
                    ap, com.android.internal.R.styleable.TextAppearance);
        }
        if (appearance != null) {
            int n = appearance.getIndexCount();
            for (int i = 0; i < n; i++) {
                int attr = appearance.getIndex(i);

                switch (attr) {
                case com.android.internal.R.styleable.TextAppearance_textColorHighlight:
                    textColorHighlight = appearance.getColor(attr, textColorHighlight);
                    break;

                case com.android.internal.R.styleable.TextAppearance_textColor:
                    textColor = appearance.getColorStateList(attr);
                    break;

                case com.android.internal.R.styleable.TextAppearance_textColorHint:
                    textColorHint = appearance.getColorStateList(attr);
                    break;

                case com.android.internal.R.styleable.TextAppearance_textColorLink:
                    textColorLink = appearance.getColorStateList(attr);
                    break;

                case com.android.internal.R.styleable.TextAppearance_textSize:
                    textSize = appearance.getDimensionPixelSize(attr, textSize);
                    break;

                case com.android.internal.R.styleable.TextAppearance_typeface:
                    typefaceIndex = appearance.getInt(attr, -1);
                    break;

                case com.android.internal.R.styleable.TextAppearance_fontFamily:
                    fontFamily = appearance.getString(attr);
                    break;

                case com.android.internal.R.styleable.TextAppearance_textStyle:
                    styleIndex = appearance.getInt(attr, -1);
                    break;

                case com.android.internal.R.styleable.TextAppearance_textAllCaps:
                    allCaps = appearance.getBoolean(attr, false);
                    break;

                case com.android.internal.R.styleable.TextAppearance_shadowColor:
                    shadowcolor = appearance.getInt(attr, 0);
                    break;

                case com.android.internal.R.styleable.TextAppearance_shadowDx:
                    dx = appearance.getFloat(attr, 0);
                    break;

                case com.android.internal.R.styleable.TextAppearance_shadowDy:
                    dy = appearance.getFloat(attr, 0);
                    break;

                case com.android.internal.R.styleable.TextAppearance_shadowRadius:
                    r = appearance.getFloat(attr, 0);
                    break;

                case com.android.internal.R.styleable.TextAppearance_elegantTextHeight:
                    elegant = appearance.getBoolean(attr, false);
                    break;

                case com.android.internal.R.styleable.TextAppearance_letterSpacing:
                    letterSpacing = appearance.getFloat(attr, 0);
                    break;

                case com.android.internal.R.styleable.TextAppearance_fontFeatureSettings:
                    fontFeatureSettings = appearance.getString(attr);
                    break;
                }
            }

            appearance.recycle();
        }

        boolean editable = getDefaultEditable();
        CharSequence inputMethod = null;
        int numeric = 0;
        CharSequence digits = null;
        boolean phone = false;
        boolean autotext = false;
        int autocap = -1;
        int buffertype = 0;
        boolean selectallonfocus = false;
        Drawable drawableLeft = null, drawableTop = null, drawableRight = null,
            drawableBottom = null, drawableStart = null, drawableEnd = null;
        ColorStateList drawableTint = null;
        PorterDuff.Mode drawableTintMode = null;
        int drawablePadding = 0;
        int ellipsize = -1;
        boolean singleLine = false;
        int maxlength = -1;
        CharSequence text = "";
        CharSequence hint = null;
        boolean password = false;
        int inputType = EditorInfo.TYPE_NULL;

        a = theme.obtainStyledAttributes(
                    attrs, com.android.internal.R.styleable.TextView, defStyleAttr, defStyleRes);

        int n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
            case com.android.internal.R.styleable.TextView_editable:
                editable = a.getBoolean(attr, editable);
                break;

            case com.android.internal.R.styleable.TextView_inputMethod:
                inputMethod = a.getText(attr);
                break;

            case com.android.internal.R.styleable.TextView_numeric:
                numeric = a.getInt(attr, numeric);
                break;

            case com.android.internal.R.styleable.TextView_digits:
                digits = a.getText(attr);
                break;

            case com.android.internal.R.styleable.TextView_phoneNumber:
                phone = a.getBoolean(attr, phone);
                break;

            case com.android.internal.R.styleable.TextView_autoText:
                autotext = a.getBoolean(attr, autotext);
                break;

            case com.android.internal.R.styleable.TextView_capitalize:
                autocap = a.getInt(attr, autocap);
                break;

            case com.android.internal.R.styleable.TextView_bufferType:
                buffertype = a.getInt(attr, buffertype);
                break;

            case com.android.internal.R.styleable.TextView_selectAllOnFocus:
                selectallonfocus = a.getBoolean(attr, selectallonfocus);
                break;

            case com.android.internal.R.styleable.TextView_autoLink:
                mAutoLinkMask = a.getInt(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_linksClickable:
                mLinksClickable = a.getBoolean(attr, true);
                break;

            case com.android.internal.R.styleable.TextView_drawableLeft:
                drawableLeft = a.getDrawable(attr);
                break;

            case com.android.internal.R.styleable.TextView_drawableTop:
                drawableTop = a.getDrawable(attr);
                break;

            case com.android.internal.R.styleable.TextView_drawableRight:
                drawableRight = a.getDrawable(attr);
                break;

            case com.android.internal.R.styleable.TextView_drawableBottom:
                drawableBottom = a.getDrawable(attr);
                break;

            case com.android.internal.R.styleable.TextView_drawableStart:
                drawableStart = a.getDrawable(attr);
                break;

            case com.android.internal.R.styleable.TextView_drawableEnd:
                drawableEnd = a.getDrawable(attr);
                break;

            case com.android.internal.R.styleable.TextView_drawableTint:
                drawableTint = a.getColorStateList(attr);
                break;

            case com.android.internal.R.styleable.TextView_drawableTintMode:
                drawableTintMode = Drawable.parseTintMode(a.getInt(attr, -1), drawableTintMode);
                break;

            case com.android.internal.R.styleable.TextView_drawablePadding:
                drawablePadding = a.getDimensionPixelSize(attr, drawablePadding);
                break;

            case com.android.internal.R.styleable.TextView_maxLines:
                setMaxLines(a.getInt(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_maxHeight:
                setMaxHeight(a.getDimensionPixelSize(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_lines:
                setLines(a.getInt(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_height:
                setHeight(a.getDimensionPixelSize(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_minLines:
                setMinLines(a.getInt(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_minHeight:
                setMinHeight(a.getDimensionPixelSize(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_maxEms:
                setMaxEms(a.getInt(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_maxWidth:
                setMaxWidth(a.getDimensionPixelSize(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_ems:
                setEms(a.getInt(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_width:
                setWidth(a.getDimensionPixelSize(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_minEms:
                setMinEms(a.getInt(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_minWidth:
                setMinWidth(a.getDimensionPixelSize(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_gravity:
                setGravity(a.getInt(attr, -1));
                break;

            case com.android.internal.R.styleable.TextView_hint:
                hint = a.getText(attr);
                break;

            case com.android.internal.R.styleable.TextView_text:
                text = a.getText(attr);
                break;

            case com.android.internal.R.styleable.TextView_scrollHorizontally:
                if (a.getBoolean(attr, false)) {
                    setHorizontallyScrolling(true);
                }
                break;

            case com.android.internal.R.styleable.TextView_singleLine:
                singleLine = a.getBoolean(attr, singleLine);
                break;

            case com.android.internal.R.styleable.TextView_ellipsize:
                ellipsize = a.getInt(attr, ellipsize);
                break;

            case com.android.internal.R.styleable.TextView_marqueeRepeatLimit:
                setMarqueeRepeatLimit(a.getInt(attr, mMarqueeRepeatLimit));
                break;

            case com.android.internal.R.styleable.TextView_includeFontPadding:
                if (!a.getBoolean(attr, true)) {
                    setIncludeFontPadding(false);
                }
                break;

            case com.android.internal.R.styleable.TextView_cursorVisible:
                if (!a.getBoolean(attr, true)) {
                    setCursorVisible(false);
                }
                break;

            case com.android.internal.R.styleable.TextView_maxLength:
                maxlength = a.getInt(attr, -1);
                break;

            case com.android.internal.R.styleable.TextView_textScaleX:
                setTextScaleX(a.getFloat(attr, 1.0f));
                break;

            case com.android.internal.R.styleable.TextView_freezesText:
                mFreezesText = a.getBoolean(attr, false);
                break;

            case com.android.internal.R.styleable.TextView_shadowColor:
                shadowcolor = a.getInt(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_shadowDx:
                dx = a.getFloat(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_shadowDy:
                dy = a.getFloat(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_shadowRadius:
                r = a.getFloat(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_enabled:
                setEnabled(a.getBoolean(attr, isEnabled()));
                break;

            case com.android.internal.R.styleable.TextView_textColorHighlight:
                textColorHighlight = a.getColor(attr, textColorHighlight);
                break;

            case com.android.internal.R.styleable.TextView_textColor:
                textColor = a.getColorStateList(attr);
                break;

            case com.android.internal.R.styleable.TextView_textColorHint:
                textColorHint = a.getColorStateList(attr);
                break;

            case com.android.internal.R.styleable.TextView_textColorLink:
                textColorLink = a.getColorStateList(attr);
                break;

            case com.android.internal.R.styleable.TextView_textSize:
                textSize = a.getDimensionPixelSize(attr, textSize);
                break;

            case com.android.internal.R.styleable.TextView_typeface:
                typefaceIndex = a.getInt(attr, typefaceIndex);
                break;

            case com.android.internal.R.styleable.TextView_textStyle:
                styleIndex = a.getInt(attr, styleIndex);
                break;

            case com.android.internal.R.styleable.TextView_fontFamily:
                fontFamily = a.getString(attr);
                fontFamilyExplicit = true;
                break;

            case com.android.internal.R.styleable.TextView_password:
                password = a.getBoolean(attr, password);
                break;

            case com.android.internal.R.styleable.TextView_lineSpacingExtra:
                mSpacingAdd = a.getDimensionPixelSize(attr, (int) mSpacingAdd);
                break;

            case com.android.internal.R.styleable.TextView_lineSpacingMultiplier:
                mSpacingMult = a.getFloat(attr, mSpacingMult);
                break;

            case com.android.internal.R.styleable.TextView_inputType:
                inputType = a.getInt(attr, EditorInfo.TYPE_NULL);
                break;

            case com.android.internal.R.styleable.TextView_allowUndo:
                createEditorIfNeeded();
                mEditor.mAllowUndo = a.getBoolean(attr, true);
                break;

            case com.android.internal.R.styleable.TextView_imeOptions:
                createEditorIfNeeded();
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeOptions = a.getInt(attr,
                        mEditor.mInputContentType.imeOptions);
                break;

            case com.android.internal.R.styleable.TextView_imeActionLabel:
                createEditorIfNeeded();
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeActionLabel = a.getText(attr);
                break;

            case com.android.internal.R.styleable.TextView_imeActionId:
                createEditorIfNeeded();
                mEditor.createInputContentTypeIfNeeded();
                mEditor.mInputContentType.imeActionId = a.getInt(attr,
                        mEditor.mInputContentType.imeActionId);
                break;

            case com.android.internal.R.styleable.TextView_privateImeOptions:
                setPrivateImeOptions(a.getString(attr));
                break;

            case com.android.internal.R.styleable.TextView_editorExtras:
                try {
                    setInputExtras(a.getResourceId(attr, 0));
                } catch (XmlPullParserException e) {
                    Log.w(LOG_TAG, "Failure reading input extras", e);
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Failure reading input extras", e);
                }
                break;

            case com.android.internal.R.styleable.TextView_textCursorDrawable:
                mCursorDrawableRes = a.getResourceId(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_textSelectHandleLeft:
                mTextSelectHandleLeftRes = a.getResourceId(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_textSelectHandleRight:
                mTextSelectHandleRightRes = a.getResourceId(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_textSelectHandle:
                mTextSelectHandleRes = a.getResourceId(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_textEditSuggestionItemLayout:
                mTextEditSuggestionItemLayout = a.getResourceId(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_textEditSuggestionContainerLayout:
                mTextEditSuggestionContainerLayout = a.getResourceId(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_textEditSuggestionHighlightStyle:
                mTextEditSuggestionHighlightStyle = a.getResourceId(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_textIsSelectable:
                setTextIsSelectable(a.getBoolean(attr, false));
                break;

            case com.android.internal.R.styleable.TextView_textAllCaps:
                allCaps = a.getBoolean(attr, false);
                break;

            case com.android.internal.R.styleable.TextView_elegantTextHeight:
                elegant = a.getBoolean(attr, false);
                break;

            case com.android.internal.R.styleable.TextView_letterSpacing:
                letterSpacing = a.getFloat(attr, 0);
                break;

            case com.android.internal.R.styleable.TextView_fontFeatureSettings:
                fontFeatureSettings = a.getString(attr);
                break;

            case com.android.internal.R.styleable.TextView_breakStrategy:
                mBreakStrategy = a.getInt(attr, Layout.BREAK_STRATEGY_SIMPLE);
                break;

            case com.android.internal.R.styleable.TextView_hyphenationFrequency:
                mHyphenationFrequency = a.getInt(attr, Layout.HYPHENATION_FREQUENCY_NONE);
                break;
            }
        }
        a.recycle();

        BufferType bufferType = BufferType.EDITABLE;

        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        final boolean passwordInputType = variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
        final boolean webPasswordInputType = variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD);
        final boolean numberPasswordInputType = variation
                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);

        if (inputMethod != null) {
            Class<?> c;

            try {
                c = Class.forName(inputMethod.toString());
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }

            try {
                createEditorIfNeeded();
                mEditor.mKeyListener = (KeyListener) c.newInstance();
            } catch (InstantiationException ex) {
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
            try {
                mEditor.mInputType = inputType != EditorInfo.TYPE_NULL
                        ? inputType
                        : mEditor.mKeyListener.getInputType();
            } catch (IncompatibleClassChangeError e) {
                mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
            }
        } else if (digits != null) {
            createEditorIfNeeded();
            mEditor.mKeyListener = DigitsKeyListener.getInstance(digits.toString());
            // If no input type was specified, we will default to generic
            // text, since we can't tell the IME about the set of digits
            // that was selected.
            mEditor.mInputType = inputType != EditorInfo.TYPE_NULL
                    ? inputType : EditorInfo.TYPE_CLASS_TEXT;
        } else if (inputType != EditorInfo.TYPE_NULL) {
            setInputType(inputType, true);
            // If set, the input type overrides what was set using the deprecated singleLine flag.
            singleLine = !isMultilineInputType(inputType);
        } else if (phone) {
            createEditorIfNeeded();
            mEditor.mKeyListener = DialerKeyListener.getInstance();
            mEditor.mInputType = inputType = EditorInfo.TYPE_CLASS_PHONE;
        } else if (numeric != 0) {
            createEditorIfNeeded();
            mEditor.mKeyListener = DigitsKeyListener.getInstance((numeric & SIGNED) != 0,
                                                   (numeric & DECIMAL) != 0);
            inputType = EditorInfo.TYPE_CLASS_NUMBER;
            if ((numeric & SIGNED) != 0) {
                inputType |= EditorInfo.TYPE_NUMBER_FLAG_SIGNED;
            }
            if ((numeric & DECIMAL) != 0) {
                inputType |= EditorInfo.TYPE_NUMBER_FLAG_DECIMAL;
            }
            mEditor.mInputType = inputType;
        } else if (autotext || autocap != -1) {
            TextKeyListener.Capitalize cap;

            inputType = EditorInfo.TYPE_CLASS_TEXT;

            switch (autocap) {
            case 1:
                cap = TextKeyListener.Capitalize.SENTENCES;
                inputType |= EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES;
                break;

            case 2:
                cap = TextKeyListener.Capitalize.WORDS;
                inputType |= EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;
                break;

            case 3:
                cap = TextKeyListener.Capitalize.CHARACTERS;
                inputType |= EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS;
                break;

            default:
                cap = TextKeyListener.Capitalize.NONE;
                break;
            }

            createEditorIfNeeded();
            mEditor.mKeyListener = TextKeyListener.getInstance(autotext, cap);
            mEditor.mInputType = inputType;
        } else if (isTextSelectable()) {
            // Prevent text changes from keyboard.
            if (mEditor != null) {
                mEditor.mKeyListener = null;
                mEditor.mInputType = EditorInfo.TYPE_NULL;
            }
            bufferType = BufferType.SPANNABLE;
            // So that selection can be changed using arrow keys and touch is handled.
            setMovementMethod(ArrowKeyMovementMethod.getInstance());
        } else if (editable) {
            createEditorIfNeeded();
            mEditor.mKeyListener = TextKeyListener.getInstance();
            mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
        } else {
            if (mEditor != null) mEditor.mKeyListener = null;

            switch (buffertype) {
                case 0:
                    bufferType = BufferType.NORMAL;
                    break;
                case 1:
                    bufferType = BufferType.SPANNABLE;
                    break;
                case 2:
                    bufferType = BufferType.EDITABLE;
                    break;
            }
        }

        if (mEditor != null) mEditor.adjustInputType(password, passwordInputType,
                webPasswordInputType, numberPasswordInputType);

        if (selectallonfocus) {
            createEditorIfNeeded();
            mEditor.mSelectAllOnFocus = true;

            if (bufferType == BufferType.NORMAL)
                bufferType = BufferType.SPANNABLE;
        }

        // Set up the tint (if needed) before setting the drawables so that it
        // gets applied correctly.
        if (drawableTint != null || drawableTintMode != null) {
            if (mDrawables == null) {
                mDrawables = new Drawables(context);
            }
            if (drawableTint != null) {
                mDrawables.mTintList = drawableTint;
                mDrawables.mHasTint = true;
            }
            if (drawableTintMode != null) {
                mDrawables.mTintMode = drawableTintMode;
                mDrawables.mHasTintMode = true;
            }
        }

        // This call will save the initial left/right drawables
        setCompoundDrawablesWithIntrinsicBounds(
            drawableLeft, drawableTop, drawableRight, drawableBottom);
        setRelativeDrawablesIfNeeded(drawableStart, drawableEnd);
        setCompoundDrawablePadding(drawablePadding);

        // Same as setSingleLine(), but make sure the transformation method and the maximum number
        // of lines of height are unchanged for multi-line TextViews.
        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, singleLine, singleLine);

        if (singleLine && getKeyListener() == null && ellipsize < 0) {
                ellipsize = 3; // END
        }

        switch (ellipsize) {
            case 1:
                setEllipsize(TextUtils.TruncateAt.START);
                break;
            case 2:
                setEllipsize(TextUtils.TruncateAt.MIDDLE);
                break;
            case 3:
                setEllipsize(TextUtils.TruncateAt.END);
                break;
            case 4:
                if (ViewConfiguration.get(context).isFadingMarqueeEnabled()) {
                    setHorizontalFadingEdgeEnabled(true);
                    mMarqueeFadeMode = MARQUEE_FADE_NORMAL;
                } else {
                    setHorizontalFadingEdgeEnabled(false);
                    mMarqueeFadeMode = MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS;
                }
                setEllipsize(TextUtils.TruncateAt.MARQUEE);
                break;
        }

        setTextColor(textColor != null ? textColor : ColorStateList.valueOf(0xFF000000));
        setHintTextColor(textColorHint);
        setLinkTextColor(textColorLink);
        if (textColorHighlight != 0) {
            setHighlightColor(textColorHighlight);
        }
        setRawTextSize(textSize);
        setElegantTextHeight(elegant);
        setLetterSpacing(letterSpacing);
        setFontFeatureSettings(fontFeatureSettings);

        if (allCaps) {
            setTransformationMethod(new AllCapsTransformationMethod(getContext()));
        }

        if (password || passwordInputType || webPasswordInputType || numberPasswordInputType) {
            setTransformationMethod(PasswordTransformationMethod.getInstance());
            typefaceIndex = MONOSPACE;
        } else if (mEditor != null &&
                (mEditor.mInputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION))
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)) {
            typefaceIndex = MONOSPACE;
        }

        if (typefaceIndex != -1 && !fontFamilyExplicit) {
            fontFamily = null;
        }
        setTypefaceFromAttrs(fontFamily, typefaceIndex, styleIndex);

        if (shadowcolor != 0) {
            setShadowLayer(r, dx, dy, shadowcolor);
        }

        if (maxlength >= 0) {
            setFilters(new InputFilter[] { new InputFilter.LengthFilter(maxlength) });
        } else {
            setFilters(NO_FILTERS);
        }

        setText(text, bufferType);
        if (hint != null) setHint(hint);

        /*
         * Views are not normally focusable unless specified to be.
         * However, TextViews that have input or movement methods *are*
         * focusable by default.
         */
        a = context.obtainStyledAttributes(
                attrs, com.android.internal.R.styleable.View, defStyleAttr, defStyleRes);

        boolean focusable = mMovement != null || getKeyListener() != null;
        boolean clickable = focusable || isClickable();
        boolean longClickable = focusable || isLongClickable();

        n = a.getIndexCount();
        for (int i = 0; i < n; i++) {
            int attr = a.getIndex(i);

            switch (attr) {
            case com.android.internal.R.styleable.View_focusable:
                focusable = a.getBoolean(attr, focusable);
                break;

            case com.android.internal.R.styleable.View_clickable:
                clickable = a.getBoolean(attr, clickable);
                break;

            case com.android.internal.R.styleable.View_longClickable:
                longClickable = a.getBoolean(attr, longClickable);
                break;
            }
        }
        a.recycle();

        setFocusable(focusable);
        setClickable(clickable);
        setLongClickable(longClickable);

        if (mEditor != null) mEditor.prepareCursorControllers();

        // If not explicitly specified this view is important for accessibility.
        if (getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
    }

    private int[] parseDimensionArray(TypedArray dimens) {
        if (dimens == null) {
            return null;
        }
        int[] result = new int[dimens.length()];
        for (int i = 0; i < result.length; i++) {
            result[i] = dimens.getDimensionPixelSize(i, 0);
        }
        return result;
    }

    /**
     * @hide
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PROCESS_TEXT_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                CharSequence result = data.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                if (result != null) {
                    if (isTextEditable()) {
                        replaceSelectionWithText(result);
                        if (mEditor != null) {
                            mEditor.refreshTextActionMode();
                        }
                    } else {
                        if (result.length() > 0) {
                            Toast.makeText(getContext(), String.valueOf(result), Toast.LENGTH_LONG)
                                .show();
                        }
                    }
                }
            } else if (mText instanceof Spannable) {
                // Reset the selection.
                Selection.setSelection((Spannable) mText, getSelectionEnd());
            }
        }
    }

    private void setTypefaceFromAttrs(String familyName, int typefaceIndex, int styleIndex) {
        Typeface tf = null;
        if (familyName != null) {
            tf = Typeface.create(familyName, styleIndex);
            if (tf != null) {
                setTypeface(tf);
                return;
            }
        }
        switch (typefaceIndex) {
            case SANS:
                tf = Typeface.SANS_SERIF;
                break;

            case SERIF:
                tf = Typeface.SERIF;
                break;

            case MONOSPACE:
                tf = Typeface.MONOSPACE;
                break;
        }

        setTypeface(tf, styleIndex);
    }

    private void setRelativeDrawablesIfNeeded(Drawable start, Drawable end) {
        boolean hasRelativeDrawables = (start != null) || (end != null);
        if (hasRelativeDrawables) {
            Drawables dr = mDrawables;
            if (dr == null) {
                mDrawables = dr = new Drawables(getContext());
            }
            mDrawables.mOverride = true;
            final Rect compoundRect = dr.mCompoundRect;
            int[] state = getDrawableState();
            if (start != null) {
                start.setBounds(0, 0, start.getIntrinsicWidth(), start.getIntrinsicHeight());
                start.setState(state);
                start.copyBounds(compoundRect);
                start.setCallback(this);

                dr.mDrawableStart = start;
                dr.mDrawableSizeStart = compoundRect.width();
                dr.mDrawableHeightStart = compoundRect.height();
            } else {
                dr.mDrawableSizeStart = dr.mDrawableHeightStart = 0;
            }
            if (end != null) {
                end.setBounds(0, 0, end.getIntrinsicWidth(), end.getIntrinsicHeight());
                end.setState(state);
                end.copyBounds(compoundRect);
                end.setCallback(this);

                dr.mDrawableEnd = end;
                dr.mDrawableSizeEnd = compoundRect.width();
                dr.mDrawableHeightEnd = compoundRect.height();
            } else {
                dr.mDrawableSizeEnd = dr.mDrawableHeightEnd = 0;
            }
            resetResolvedDrawables();
            resolveDrawables();
            applyCompoundDrawableTint();
        }
    }

    @android.view.RemotableViewMethod
    @Override
    public void setEnabled(boolean enabled) {
        if (enabled == isEnabled()) {
            return;
        }

        if (!enabled) {
            // Hide the soft input if the currently active TextView is disabled
            InputMethodManager imm = InputMethodManager.peekInstance();
            if (imm != null && imm.isActive(this)) {
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        }

        super.setEnabled(enabled);

        if (enabled) {
            // Make sure IME is updated with current editor info.
            InputMethodManager imm = InputMethodManager.peekInstance();
            if (imm != null) imm.restartInput(this);
        }

        // Will change text color
        if (mEditor != null) {
            mEditor.invalidateTextDisplayList();
            mEditor.prepareCursorControllers();

            // start or stop the cursor blinking as appropriate
            mEditor.makeBlink();
        }
    }

    /**
     * Sets the typeface and style in which the text should be displayed,
     * and turns on the fake bold and italic bits in the Paint if the
     * Typeface that you provided does not have all the bits in the
     * style that you specified.
     *
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     */
    public void setTypeface(Typeface tf, int style) {
        if (style > 0) {
            if (tf == null) {
                tf = Typeface.defaultFromStyle(style);
            } else {
                tf = Typeface.create(tf, style);
            }

            setTypeface(tf);
            // now compute what (if any) algorithmic styling is needed
            int typefaceStyle = tf != null ? tf.getStyle() : 0;
            int need = style & ~typefaceStyle;
            mTextPaint.setFakeBoldText((need & Typeface.BOLD) != 0);
            mTextPaint.setTextSkewX((need & Typeface.ITALIC) != 0 ? -0.25f : 0);
        } else {
            mTextPaint.setFakeBoldText(false);
            mTextPaint.setTextSkewX(0);
            setTypeface(tf);
        }
    }

    /**
     * Subclasses override this to specify that they have a KeyListener
     * by default even if not specifically called for in the XML options.
     */
    protected boolean getDefaultEditable() {
        return false;
    }

    /**
     * Subclasses override this to specify a default movement method.
     */
    protected MovementMethod getDefaultMovementMethod() {
        return null;
    }

    /**
     * Return the text the TextView is displaying. If setText() was called with
     * an argument of BufferType.SPANNABLE or BufferType.EDITABLE, you can cast
     * the return value from this method to Spannable or Editable, respectively.
     *
     * Note: The content of the return value should not be modified. If you want
     * a modifiable one, you should make your own copy first.
     *
     * @attr ref android.R.styleable#TextView_text
     */
    @ViewDebug.CapturedViewProperty
    public CharSequence getText() {
        return mText;
    }

    /**
     * Returns the length, in characters, of the text managed by this TextView
     */
    public int length() {
        return mText.length();
    }

    /**
     * Return the text the TextView is displaying as an Editable object.  If
     * the text is not editable, null is returned.
     *
     * @see #getText
     */
    public Editable getEditableText() {
        return (mText instanceof Editable) ? (Editable)mText : null;
    }

    /**
     * @return the height of one standard line in pixels.  Note that markup
     * within the text can cause individual lines to be taller or shorter
     * than this height, and the layout may contain additional first-
     * or last-line padding.
     */
    public int getLineHeight() {
        return FastMath.round(mTextPaint.getFontMetricsInt(null) * mSpacingMult + mSpacingAdd);
    }

    /**
     * @return the Layout that is currently being used to display the text.
     * This can be null if the text or width has recently changes.
     */
    public final Layout getLayout() {
        return mLayout;
    }

    /**
     * @return the Layout that is currently being used to display the hint text.
     * This can be null.
     */
    final Layout getHintLayout() {
        return mHintLayout;
    }

    /**
     * Retrieve the {@link android.content.UndoManager} that is currently associated
     * with this TextView.  By default there is no associated UndoManager, so null
     * is returned.  One can be associated with the TextView through
     * {@link #setUndoManager(android.content.UndoManager, String)}
     *
     * @hide
     */
    public final UndoManager getUndoManager() {
        // TODO: Consider supporting a global undo manager.
        throw new UnsupportedOperationException("not implemented");
    }


    /**
     * @hide
     */
    @VisibleForTesting
    public final Editor getEditorForTesting() {
        return mEditor;
    }

    /**
     * Associate an {@link android.content.UndoManager} with this TextView.  Once
     * done, all edit operations on the TextView will result in appropriate
     * {@link android.content.UndoOperation} objects pushed on the given UndoManager's
     * stack.
     *
     * @param undoManager The {@link android.content.UndoManager} to associate with
     * this TextView, or null to clear any existing association.
     * @param tag String tag identifying this particular TextView owner in the
     * UndoManager.  This is used to keep the correct association with the
     * {@link android.content.UndoOwner} of any operations inside of the UndoManager.
     *
     * @hide
     */
    public final void setUndoManager(UndoManager undoManager, String tag) {
        // TODO: Consider supporting a global undo manager. An implementation will need to:
        // * createEditorIfNeeded()
        // * Promote to BufferType.EDITABLE if needed.
        // * Update the UndoManager and UndoOwner.
        // Likewise it will need to be able to restore the default UndoManager.
        throw new UnsupportedOperationException("not implemented");
    }

    /**
     * @return the current key listener for this TextView.
     * This will frequently be null for non-EditText TextViews.
     *
     * @attr ref android.R.styleable#TextView_numeric
     * @attr ref android.R.styleable#TextView_digits
     * @attr ref android.R.styleable#TextView_phoneNumber
     * @attr ref android.R.styleable#TextView_inputMethod
     * @attr ref android.R.styleable#TextView_capitalize
     * @attr ref android.R.styleable#TextView_autoText
     */
    public final KeyListener getKeyListener() {
        return mEditor == null ? null : mEditor.mKeyListener;
    }

    /**
     * Sets the key listener to be used with this TextView.  This can be null
     * to disallow user input.  Note that this method has significant and
     * subtle interactions with soft keyboards and other input method:
     * see {@link KeyListener#getInputType() KeyListener.getContentType()}
     * for important details.  Calling this method will replace the current
     * content type of the text view with the content type returned by the
     * key listener.
     * <p>
     * Be warned that if you want a TextView with a key listener or movement
     * method not to be focusable, or if you want a TextView without a
     * key listener or movement method to be focusable, you must call
     * {@link #setFocusable} again after calling this to get the focusability
     * back the way you want it.
     *
     * @attr ref android.R.styleable#TextView_numeric
     * @attr ref android.R.styleable#TextView_digits
     * @attr ref android.R.styleable#TextView_phoneNumber
     * @attr ref android.R.styleable#TextView_inputMethod
     * @attr ref android.R.styleable#TextView_capitalize
     * @attr ref android.R.styleable#TextView_autoText
     */
    public void setKeyListener(KeyListener input) {
        setKeyListenerOnly(input);
        fixFocusableAndClickableSettings();

        if (input != null) {
            createEditorIfNeeded();
            try {
                mEditor.mInputType = mEditor.mKeyListener.getInputType();
            } catch (IncompatibleClassChangeError e) {
                mEditor.mInputType = EditorInfo.TYPE_CLASS_TEXT;
            }
            // Change inputType, without affecting transformation.
            // No need to applySingleLine since mSingleLine is unchanged.
            setInputTypeSingleLine(mSingleLine);
        } else {
            if (mEditor != null) mEditor.mInputType = EditorInfo.TYPE_NULL;
        }

        InputMethodManager imm = InputMethodManager.peekInstance();
        if (imm != null) imm.restartInput(this);
    }

    private void setKeyListenerOnly(KeyListener input) {
        if (mEditor == null && input == null) return; // null is the default value

        createEditorIfNeeded();
        if (mEditor.mKeyListener != input) {
            mEditor.mKeyListener = input;
            if (input != null && !(mText instanceof Editable)) {
                setText(mText);
            }

            setFilters((Editable) mText, mFilters);
        }
    }

    /**
     * @return the movement method being used for this TextView.
     * This will frequently be null for non-EditText TextViews.
     */
    public final MovementMethod getMovementMethod() {
        return mMovement;
    }

    /**
     * Sets the movement method (arrow key handler) to be used for
     * this TextView.  This can be null to disallow using the arrow keys
     * to move the cursor or scroll the view.
     * <p>
     * Be warned that if you want a TextView with a key listener or movement
     * method not to be focusable, or if you want a TextView without a
     * key listener or movement method to be focusable, you must call
     * {@link #setFocusable} again after calling this to get the focusability
     * back the way you want it.
     */
    public final void setMovementMethod(MovementMethod movement) {
        if (mMovement != movement) {
            mMovement = movement;

            if (movement != null && !(mText instanceof Spannable)) {
                setText(mText);
            }

            fixFocusableAndClickableSettings();

            // SelectionModifierCursorController depends on textCanBeSelected, which depends on
            // mMovement
            if (mEditor != null) mEditor.prepareCursorControllers();
        }
    }

    private void fixFocusableAndClickableSettings() {
        if (mMovement != null || (mEditor != null && mEditor.mKeyListener != null)) {
            setFocusable(true);
            setClickable(true);
            setLongClickable(true);
        } else {
            setFocusable(false);
            setClickable(false);
            setLongClickable(false);
        }
    }

    /**
     * @return the current transformation method for this TextView.
     * This will frequently be null except for single-line and password
     * fields.
     *
     * @attr ref android.R.styleable#TextView_password
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public final TransformationMethod getTransformationMethod() {
        return mTransformation;
    }

    /**
     * Sets the transformation that is applied to the text that this
     * TextView is displaying.
     *
     * @attr ref android.R.styleable#TextView_password
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public final void setTransformationMethod(TransformationMethod method) {
        if (method == mTransformation) {
            // Avoid the setText() below if the transformation is
            // the same.
            return;
        }
        if (mTransformation != null) {
            if (mText instanceof Spannable) {
                ((Spannable) mText).removeSpan(mTransformation);
            }
        }

        mTransformation = method;

        if (method instanceof TransformationMethod2) {
            TransformationMethod2 method2 = (TransformationMethod2) method;
            mAllowTransformationLengthChange = !isTextSelectable() && !(mText instanceof Editable);
            method2.setLengthChangesAllowed(mAllowTransformationLengthChange);
        } else {
            mAllowTransformationLengthChange = false;
        }

        setText(mText);

        if (hasPasswordTransformationMethod()) {
            notifyViewAccessibilityStateChangedIfNeeded(
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
        }

        // PasswordTransformationMethod always have LTR text direction heuristics returned by
        // getTextDirectionHeuristic, needs reset
        mTextDir = getTextDirectionHeuristic();
    }

    /**
     * Returns the top padding of the view, plus space for the top
     * Drawable if any.
     */
    public int getCompoundPaddingTop() {
        final Drawables dr = mDrawables;
        if (dr == null || dr.mShowing[Drawables.TOP] == null) {
            return mPaddingTop;
        } else {
            return mPaddingTop + dr.mDrawablePadding + dr.mDrawableSizeTop;
        }
    }

    /**
     * Returns the bottom padding of the view, plus space for the bottom
     * Drawable if any.
     */
    public int getCompoundPaddingBottom() {
        final Drawables dr = mDrawables;
        if (dr == null || dr.mShowing[Drawables.BOTTOM] == null) {
            return mPaddingBottom;
        } else {
            return mPaddingBottom + dr.mDrawablePadding + dr.mDrawableSizeBottom;
        }
    }

    /**
     * Returns the left padding of the view, plus space for the left
     * Drawable if any.
     */
    public int getCompoundPaddingLeft() {
        final Drawables dr = mDrawables;
        if (dr == null || dr.mShowing[Drawables.LEFT] == null) {
            return mPaddingLeft;
        } else {
            return mPaddingLeft + dr.mDrawablePadding + dr.mDrawableSizeLeft;
        }
    }

    /**
     * Returns the right padding of the view, plus space for the right
     * Drawable if any.
     */
    public int getCompoundPaddingRight() {
        final Drawables dr = mDrawables;
        if (dr == null || dr.mShowing[Drawables.RIGHT] == null) {
            return mPaddingRight;
        } else {
            return mPaddingRight + dr.mDrawablePadding + dr.mDrawableSizeRight;
        }
    }

    /**
     * Returns the start padding of the view, plus space for the start
     * Drawable if any.
     */
    public int getCompoundPaddingStart() {
        resolveDrawables();
        switch(getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingLeft();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingRight();
        }
    }

    /**
     * Returns the end padding of the view, plus space for the end
     * Drawable if any.
     */
    public int getCompoundPaddingEnd() {
        resolveDrawables();
        switch(getLayoutDirection()) {
            default:
            case LAYOUT_DIRECTION_LTR:
                return getCompoundPaddingRight();
            case LAYOUT_DIRECTION_RTL:
                return getCompoundPaddingLeft();
        }
    }

    /**
     * Returns the extended top padding of the view, including both the
     * top Drawable if any and any extra space to keep more than maxLines
     * of text from showing.  It is only valid to call this after measuring.
     */
    public int getExtendedPaddingTop() {
        if (mMaxMode != LINES) {
            return getCompoundPaddingTop();
        }

        if (mLayout == null) {
            assumeLayout();
        }

        if (mLayout.getLineCount() <= mMaximum) {
            return getCompoundPaddingTop();
        }

        int top = getCompoundPaddingTop();
        int bottom = getCompoundPaddingBottom();
        int viewht = getHeight() - top - bottom;
        int layoutht = mLayout.getLineTop(mMaximum);

        if (layoutht >= viewht) {
            return top;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return top;
        } else if (gravity == Gravity.BOTTOM) {
            return top + viewht - layoutht;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return top + (viewht - layoutht) / 2;
        }
    }

    /**
     * Returns the extended bottom padding of the view, including both the
     * bottom Drawable if any and any extra space to keep more than maxLines
     * of text from showing.  It is only valid to call this after measuring.
     */
    public int getExtendedPaddingBottom() {
        if (mMaxMode != LINES) {
            return getCompoundPaddingBottom();
        }

        if (mLayout == null) {
            assumeLayout();
        }

        if (mLayout.getLineCount() <= mMaximum) {
            return getCompoundPaddingBottom();
        }

        int top = getCompoundPaddingTop();
        int bottom = getCompoundPaddingBottom();
        int viewht = getHeight() - top - bottom;
        int layoutht = mLayout.getLineTop(mMaximum);

        if (layoutht >= viewht) {
            return bottom;
        }

        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;
        if (gravity == Gravity.TOP) {
            return bottom + viewht - layoutht;
        } else if (gravity == Gravity.BOTTOM) {
            return bottom;
        } else { // (gravity == Gravity.CENTER_VERTICAL)
            return bottom + (viewht - layoutht) / 2;
        }
    }

    /**
     * Returns the total left padding of the view, including the left
     * Drawable if any.
     */
    public int getTotalPaddingLeft() {
        return getCompoundPaddingLeft();
    }

    /**
     * Returns the total right padding of the view, including the right
     * Drawable if any.
     */
    public int getTotalPaddingRight() {
        return getCompoundPaddingRight();
    }

    /**
     * Returns the total start padding of the view, including the start
     * Drawable if any.
     */
    public int getTotalPaddingStart() {
        return getCompoundPaddingStart();
    }

    /**
     * Returns the total end padding of the view, including the end
     * Drawable if any.
     */
    public int getTotalPaddingEnd() {
        return getCompoundPaddingEnd();
    }

    /**
     * Returns the total top padding of the view, including the top
     * Drawable if any, the extra space to keep more than maxLines
     * from showing, and the vertical offset for gravity, if any.
     */
    public int getTotalPaddingTop() {
        return getExtendedPaddingTop() + getVerticalOffset(true);
    }

    /**
     * Returns the total bottom padding of the view, including the bottom
     * Drawable if any, the extra space to keep more than maxLines
     * from showing, and the vertical offset for gravity, if any.
     */
    public int getTotalPaddingBottom() {
        return getExtendedPaddingBottom() + getBottomVerticalOffset(true);
    }

    /**
     * Sets the Drawables (if any) to appear to the left of, above, to the
     * right of, and below the text. Use {@code null} if you do not want a
     * Drawable there. The Drawables must already have had
     * {@link Drawable#setBounds} called.
     * <p>
     * Calling this method will overwrite any Drawables previously set using
     * {@link #setCompoundDrawablesRelative} or related methods.
     *
     * @attr ref android.R.styleable#TextView_drawableLeft
     * @attr ref android.R.styleable#TextView_drawableTop
     * @attr ref android.R.styleable#TextView_drawableRight
     * @attr ref android.R.styleable#TextView_drawableBottom
     */
    public void setCompoundDrawables(@Nullable Drawable left, @Nullable Drawable top,
            @Nullable Drawable right, @Nullable Drawable bottom) {
        Drawables dr = mDrawables;

        // We're switching to absolute, discard relative.
        if (dr != null) {
            if (dr.mDrawableStart != null) dr.mDrawableStart.setCallback(null);
            dr.mDrawableStart = null;
            if (dr.mDrawableEnd != null) dr.mDrawableEnd.setCallback(null);
            dr.mDrawableEnd = null;
            dr.mDrawableSizeStart = dr.mDrawableHeightStart = 0;
            dr.mDrawableSizeEnd = dr.mDrawableHeightEnd = 0;
        }

        final boolean drawables = left != null || top != null || right != null || bottom != null;
        if (!drawables) {
            // Clearing drawables...  can we free the data structure?
            if (dr != null) {
                if (!dr.hasMetadata()) {
                    mDrawables = null;
                } else {
                    // We need to retain the last set padding, so just clear
                    // out all of the fields in the existing structure.
                    for (int i = dr.mShowing.length - 1; i >= 0; i--) {
                        if (dr.mShowing[i] != null) {
                            dr.mShowing[i].setCallback(null);
                        }
                        dr.mShowing[i] = null;
                    }
                    dr.mDrawableSizeLeft = dr.mDrawableHeightLeft = 0;
                    dr.mDrawableSizeRight = dr.mDrawableHeightRight = 0;
                    dr.mDrawableSizeTop = dr.mDrawableWidthTop = 0;
                    dr.mDrawableSizeBottom = dr.mDrawableWidthBottom = 0;
                }
            }
        } else {
            if (dr == null) {
                mDrawables = dr = new Drawables(getContext());
            }

            mDrawables.mOverride = false;

            if (dr.mShowing[Drawables.LEFT] != left && dr.mShowing[Drawables.LEFT] != null) {
                dr.mShowing[Drawables.LEFT].setCallback(null);
            }
            dr.mShowing[Drawables.LEFT] = left;

            if (dr.mShowing[Drawables.TOP] != top && dr.mShowing[Drawables.TOP] != null) {
                dr.mShowing[Drawables.TOP].setCallback(null);
            }
            dr.mShowing[Drawables.TOP] = top;

            if (dr.mShowing[Drawables.RIGHT] != right && dr.mShowing[Drawables.RIGHT] != null) {
                dr.mShowing[Drawables.RIGHT].setCallback(null);
            }
            dr.mShowing[Drawables.RIGHT] = right;

            if (dr.mShowing[Drawables.BOTTOM] != bottom && dr.mShowing[Drawables.BOTTOM] != null) {
                dr.mShowing[Drawables.BOTTOM].setCallback(null);
            }
            dr.mShowing[Drawables.BOTTOM] = bottom;

            final Rect compoundRect = dr.mCompoundRect;
            int[] state;

            state = getDrawableState();

            if (left != null) {
                left.setState(state);
                left.copyBounds(compoundRect);
                left.setCallback(this);
                dr.mDrawableSizeLeft = compoundRect.width();
                dr.mDrawableHeightLeft = compoundRect.height();
            } else {
                dr.mDrawableSizeLeft = dr.mDrawableHeightLeft = 0;
            }

            if (right != null) {
                right.setState(state);
                right.copyBounds(compoundRect);
                right.setCallback(this);
                dr.mDrawableSizeRight = compoundRect.width();
                dr.mDrawableHeightRight = compoundRect.height();
            } else {
                dr.mDrawableSizeRight = dr.mDrawableHeightRight = 0;
            }

            if (top != null) {
                top.setState(state);
                top.copyBounds(compoundRect);
                top.setCallback(this);
                dr.mDrawableSizeTop = compoundRect.height();
                dr.mDrawableWidthTop = compoundRect.width();
            } else {
                dr.mDrawableSizeTop = dr.mDrawableWidthTop = 0;
            }

            if (bottom != null) {
                bottom.setState(state);
                bottom.copyBounds(compoundRect);
                bottom.setCallback(this);
                dr.mDrawableSizeBottom = compoundRect.height();
                dr.mDrawableWidthBottom = compoundRect.width();
            } else {
                dr.mDrawableSizeBottom = dr.mDrawableWidthBottom = 0;
            }
        }

        // Save initial left/right drawables
        if (dr != null) {
            dr.mDrawableLeftInitial = left;
            dr.mDrawableRightInitial = right;
        }

        resetResolvedDrawables();
        resolveDrawables();
        applyCompoundDrawableTint();
        invalidate();
        requestLayout();
    }

    /**
     * Sets the Drawables (if any) to appear to the left of, above, to the
     * right of, and below the text. Use 0 if you do not want a Drawable there.
     * The Drawables' bounds will be set to their intrinsic bounds.
     * <p>
     * Calling this method will overwrite any Drawables previously set using
     * {@link #setCompoundDrawablesRelative} or related methods.
     *
     * @param left Resource identifier of the left Drawable.
     * @param top Resource identifier of the top Drawable.
     * @param right Resource identifier of the right Drawable.
     * @param bottom Resource identifier of the bottom Drawable.
     *
     * @attr ref android.R.styleable#TextView_drawableLeft
     * @attr ref android.R.styleable#TextView_drawableTop
     * @attr ref android.R.styleable#TextView_drawableRight
     * @attr ref android.R.styleable#TextView_drawableBottom
     */
    @android.view.RemotableViewMethod
    public void setCompoundDrawablesWithIntrinsicBounds(@DrawableRes int left,
            @DrawableRes int top, @DrawableRes int right, @DrawableRes int bottom) {
        final Context context = getContext();
        setCompoundDrawablesWithIntrinsicBounds(left != 0 ? context.getDrawable(left) : null,
                top != 0 ? context.getDrawable(top) : null,
                right != 0 ? context.getDrawable(right) : null,
                bottom != 0 ? context.getDrawable(bottom) : null);
    }

    /**
     * Sets the Drawables (if any) to appear to the left of, above, to the
     * right of, and below the text. Use {@code null} if you do not want a
     * Drawable there. The Drawables' bounds will be set to their intrinsic
     * bounds.
     * <p>
     * Calling this method will overwrite any Drawables previously set using
     * {@link #setCompoundDrawablesRelative} or related methods.
     *
     * @attr ref android.R.styleable#TextView_drawableLeft
     * @attr ref android.R.styleable#TextView_drawableTop
     * @attr ref android.R.styleable#TextView_drawableRight
     * @attr ref android.R.styleable#TextView_drawableBottom
     */
    @android.view.RemotableViewMethod
    public void setCompoundDrawablesWithIntrinsicBounds(@Nullable Drawable left,
            @Nullable Drawable top, @Nullable Drawable right, @Nullable Drawable bottom) {

        if (left != null) {
            left.setBounds(0, 0, left.getIntrinsicWidth(), left.getIntrinsicHeight());
        }
        if (right != null) {
            right.setBounds(0, 0, right.getIntrinsicWidth(), right.getIntrinsicHeight());
        }
        if (top != null) {
            top.setBounds(0, 0, top.getIntrinsicWidth(), top.getIntrinsicHeight());
        }
        if (bottom != null) {
            bottom.setBounds(0, 0, bottom.getIntrinsicWidth(), bottom.getIntrinsicHeight());
        }
        setCompoundDrawables(left, top, right, bottom);
    }

    /**
     * Sets the Drawables (if any) to appear to the start of, above, to the end
     * of, and below the text. Use {@code null} if you do not want a Drawable
     * there. The Drawables must already have had {@link Drawable#setBounds}
     * called.
     * <p>
     * Calling this method will overwrite any Drawables previously set using
     * {@link #setCompoundDrawables} or related methods.
     *
     * @attr ref android.R.styleable#TextView_drawableStart
     * @attr ref android.R.styleable#TextView_drawableTop
     * @attr ref android.R.styleable#TextView_drawableEnd
     * @attr ref android.R.styleable#TextView_drawableBottom
     */
    @android.view.RemotableViewMethod
    public void setCompoundDrawablesRelative(@Nullable Drawable start, @Nullable Drawable top,
            @Nullable Drawable end, @Nullable Drawable bottom) {
        Drawables dr = mDrawables;

        // We're switching to relative, discard absolute.
        if (dr != null) {
            if (dr.mShowing[Drawables.LEFT] != null) {
                dr.mShowing[Drawables.LEFT].setCallback(null);
            }
            dr.mShowing[Drawables.LEFT] = dr.mDrawableLeftInitial = null;
            if (dr.mShowing[Drawables.RIGHT] != null) {
                dr.mShowing[Drawables.RIGHT].setCallback(null);
            }
            dr.mShowing[Drawables.RIGHT] = dr.mDrawableRightInitial = null;
            dr.mDrawableSizeLeft = dr.mDrawableHeightLeft = 0;
            dr.mDrawableSizeRight = dr.mDrawableHeightRight = 0;
        }

        final boolean drawables = start != null || top != null
                || end != null || bottom != null;

        if (!drawables) {
            // Clearing drawables...  can we free the data structure?
            if (dr != null) {
                if (!dr.hasMetadata()) {
                    mDrawables = null;
                } else {
                    // We need to retain the last set padding, so just clear
                    // out all of the fields in the existing structure.
                    if (dr.mDrawableStart != null) dr.mDrawableStart.setCallback(null);
                    dr.mDrawableStart = null;
                    if (dr.mShowing[Drawables.TOP] != null) {
                        dr.mShowing[Drawables.TOP].setCallback(null);
                    }
                    dr.mShowing[Drawables.TOP] = null;
                    if (dr.mDrawableEnd != null) {
                        dr.mDrawableEnd.setCallback(null);
                    }
                    dr.mDrawableEnd = null;
                    if (dr.mShowing[Drawables.BOTTOM] != null) {
                        dr.mShowing[Drawables.BOTTOM].setCallback(null);
                    }
                    dr.mShowing[Drawables.BOTTOM] = null;
                    dr.mDrawableSizeStart = dr.mDrawableHeightStart = 0;
                    dr.mDrawableSizeEnd = dr.mDrawableHeightEnd = 0;
                    dr.mDrawableSizeTop = dr.mDrawableWidthTop = 0;
                    dr.mDrawableSizeBottom = dr.mDrawableWidthBottom = 0;
                }
            }
        } else {
            if (dr == null) {
                mDrawables = dr = new Drawables(getContext());
            }

            mDrawables.mOverride = true;

            if (dr.mDrawableStart != start && dr.mDrawableStart != null) {
                dr.mDrawableStart.setCallback(null);
            }
            dr.mDrawableStart = start;

            if (dr.mShowing[Drawables.TOP] != top && dr.mShowing[Drawables.TOP] != null) {
                dr.mShowing[Drawables.TOP].setCallback(null);
            }
            dr.mShowing[Drawables.TOP] = top;

            if (dr.mDrawableEnd != end && dr.mDrawableEnd != null) {
                dr.mDrawableEnd.setCallback(null);
            }
            dr.mDrawableEnd = end;

            if (dr.mShowing[Drawables.BOTTOM] != bottom && dr.mShowing[Drawables.BOTTOM] != null) {
                dr.mShowing[Drawables.BOTTOM].setCallback(null);
            }
            dr.mShowing[Drawables.BOTTOM] = bottom;

            final Rect compoundRect = dr.mCompoundRect;
            int[] state;

            state = getDrawableState();

            if (start != null) {
                start.setState(state);
                start.copyBounds(compoundRect);
                start.setCallback(this);
                dr.mDrawableSizeStart = compoundRect.width();
                dr.mDrawableHeightStart = compoundRect.height();
            } else {
                dr.mDrawableSizeStart = dr.mDrawableHeightStart = 0;
            }

            if (end != null) {
                end.setState(state);
                end.copyBounds(compoundRect);
                end.setCallback(this);
                dr.mDrawableSizeEnd = compoundRect.width();
                dr.mDrawableHeightEnd = compoundRect.height();
            } else {
                dr.mDrawableSizeEnd = dr.mDrawableHeightEnd = 0;
            }

            if (top != null) {
                top.setState(state);
                top.copyBounds(compoundRect);
                top.setCallback(this);
                dr.mDrawableSizeTop = compoundRect.height();
                dr.mDrawableWidthTop = compoundRect.width();
            } else {
                dr.mDrawableSizeTop = dr.mDrawableWidthTop = 0;
            }

            if (bottom != null) {
                bottom.setState(state);
                bottom.copyBounds(compoundRect);
                bottom.setCallback(this);
                dr.mDrawableSizeBottom = compoundRect.height();
                dr.mDrawableWidthBottom = compoundRect.width();
            } else {
                dr.mDrawableSizeBottom = dr.mDrawableWidthBottom = 0;
            }
        }

        resetResolvedDrawables();
        resolveDrawables();
        invalidate();
        requestLayout();
    }

    /**
     * Sets the Drawables (if any) to appear to the start of, above, to the end
     * of, and below the text. Use 0 if you do not want a Drawable there. The
     * Drawables' bounds will be set to their intrinsic bounds.
     * <p>
     * Calling this method will overwrite any Drawables previously set using
     * {@link #setCompoundDrawables} or related methods.
     *
     * @param start Resource identifier of the start Drawable.
     * @param top Resource identifier of the top Drawable.
     * @param end Resource identifier of the end Drawable.
     * @param bottom Resource identifier of the bottom Drawable.
     *
     * @attr ref android.R.styleable#TextView_drawableStart
     * @attr ref android.R.styleable#TextView_drawableTop
     * @attr ref android.R.styleable#TextView_drawableEnd
     * @attr ref android.R.styleable#TextView_drawableBottom
     */
    @android.view.RemotableViewMethod
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(@DrawableRes int start,
            @DrawableRes int top, @DrawableRes int end, @DrawableRes int bottom) {
        final Context context = getContext();
        setCompoundDrawablesRelativeWithIntrinsicBounds(
                start != 0 ? context.getDrawable(start) : null,
                top != 0 ? context.getDrawable(top) : null,
                end != 0 ? context.getDrawable(end) : null,
                bottom != 0 ? context.getDrawable(bottom) : null);
    }

    /**
     * Sets the Drawables (if any) to appear to the start of, above, to the end
     * of, and below the text. Use {@code null} if you do not want a Drawable
     * there. The Drawables' bounds will be set to their intrinsic bounds.
     * <p>
     * Calling this method will overwrite any Drawables previously set using
     * {@link #setCompoundDrawables} or related methods.
     *
     * @attr ref android.R.styleable#TextView_drawableStart
     * @attr ref android.R.styleable#TextView_drawableTop
     * @attr ref android.R.styleable#TextView_drawableEnd
     * @attr ref android.R.styleable#TextView_drawableBottom
     */
    @android.view.RemotableViewMethod
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(@Nullable Drawable start,
            @Nullable Drawable top, @Nullable Drawable end, @Nullable Drawable bottom) {

        if (start != null) {
            start.setBounds(0, 0, start.getIntrinsicWidth(), start.getIntrinsicHeight());
        }
        if (end != null) {
            end.setBounds(0, 0, end.getIntrinsicWidth(), end.getIntrinsicHeight());
        }
        if (top != null) {
            top.setBounds(0, 0, top.getIntrinsicWidth(), top.getIntrinsicHeight());
        }
        if (bottom != null) {
            bottom.setBounds(0, 0, bottom.getIntrinsicWidth(), bottom.getIntrinsicHeight());
        }
        setCompoundDrawablesRelative(start, top, end, bottom);
    }

    /**
     * Returns drawables for the left, top, right, and bottom borders.
     *
     * @attr ref android.R.styleable#TextView_drawableLeft
     * @attr ref android.R.styleable#TextView_drawableTop
     * @attr ref android.R.styleable#TextView_drawableRight
     * @attr ref android.R.styleable#TextView_drawableBottom
     */
    @NonNull
    public Drawable[] getCompoundDrawables() {
        final Drawables dr = mDrawables;
        if (dr != null) {
            return dr.mShowing.clone();
        } else {
            return new Drawable[] { null, null, null, null };
        }
    }

    /**
     * Returns drawables for the start, top, end, and bottom borders.
     *
     * @attr ref android.R.styleable#TextView_drawableStart
     * @attr ref android.R.styleable#TextView_drawableTop
     * @attr ref android.R.styleable#TextView_drawableEnd
     * @attr ref android.R.styleable#TextView_drawableBottom
     */
    @NonNull
    public Drawable[] getCompoundDrawablesRelative() {
        final Drawables dr = mDrawables;
        if (dr != null) {
            return new Drawable[] {
                dr.mDrawableStart, dr.mShowing[Drawables.TOP],
                dr.mDrawableEnd, dr.mShowing[Drawables.BOTTOM]
            };
        } else {
            return new Drawable[] { null, null, null, null };
        }
    }

    /**
     * Sets the size of the padding between the compound drawables and
     * the text.
     *
     * @attr ref android.R.styleable#TextView_drawablePadding
     */
    @android.view.RemotableViewMethod
    public void setCompoundDrawablePadding(int pad) {
        Drawables dr = mDrawables;
        if (pad == 0) {
            if (dr != null) {
                dr.mDrawablePadding = pad;
            }
        } else {
            if (dr == null) {
                mDrawables = dr = new Drawables(getContext());
            }
            dr.mDrawablePadding = pad;
        }

        invalidate();
        requestLayout();
    }

    /**
     * Returns the padding between the compound drawables and the text.
     *
     * @attr ref android.R.styleable#TextView_drawablePadding
     */
    public int getCompoundDrawablePadding() {
        final Drawables dr = mDrawables;
        return dr != null ? dr.mDrawablePadding : 0;
    }

    /**
     * Applies a tint to the compound drawables. Does not modify the
     * current tint mode, which is {@link PorterDuff.Mode#SRC_IN} by default.
     * <p>
     * Subsequent calls to
     * {@link #setCompoundDrawables(Drawable, Drawable, Drawable, Drawable)}
     * and related methods will automatically mutate the drawables and apply
     * the specified tint and tint mode using
     * {@link Drawable#setTintList(ColorStateList)}.
     *
     * @param tint the tint to apply, may be {@code null} to clear tint
     *
     * @attr ref android.R.styleable#TextView_drawableTint
     * @see #getCompoundDrawableTintList()
     * @see Drawable#setTintList(ColorStateList)
     */
    public void setCompoundDrawableTintList(@Nullable ColorStateList tint) {
        if (mDrawables == null) {
            mDrawables = new Drawables(getContext());
        }
        mDrawables.mTintList = tint;
        mDrawables.mHasTint = true;

        applyCompoundDrawableTint();
    }

    /**
     * @return the tint applied to the compound drawables
     * @attr ref android.R.styleable#TextView_drawableTint
     * @see #setCompoundDrawableTintList(ColorStateList)
     */
    public ColorStateList getCompoundDrawableTintList() {
        return mDrawables != null ? mDrawables.mTintList : null;
    }

    /**
     * Specifies the blending mode used to apply the tint specified by
     * {@link #setCompoundDrawableTintList(ColorStateList)} to the compound
     * drawables. The default mode is {@link PorterDuff.Mode#SRC_IN}.
     *
     * @param tintMode the blending mode used to apply the tint, may be
     *                 {@code null} to clear tint
     * @attr ref android.R.styleable#TextView_drawableTintMode
     * @see #setCompoundDrawableTintList(ColorStateList)
     * @see Drawable#setTintMode(PorterDuff.Mode)
     */
    public void setCompoundDrawableTintMode(@Nullable PorterDuff.Mode tintMode) {
        if (mDrawables == null) {
            mDrawables = new Drawables(getContext());
        }
        mDrawables.mTintMode = tintMode;
        mDrawables.mHasTintMode = true;

        applyCompoundDrawableTint();
    }

    /**
     * Returns the blending mode used to apply the tint to the compound
     * drawables, if specified.
     *
     * @return the blending mode used to apply the tint to the compound
     *         drawables
     * @attr ref android.R.styleable#TextView_drawableTintMode
     * @see #setCompoundDrawableTintMode(PorterDuff.Mode)
     */
    public PorterDuff.Mode getCompoundDrawableTintMode() {
        return mDrawables != null ? mDrawables.mTintMode : null;
    }

    private void applyCompoundDrawableTint() {
        if (mDrawables == null) {
            return;
        }

        if (mDrawables.mHasTint || mDrawables.mHasTintMode) {
            final ColorStateList tintList = mDrawables.mTintList;
            final PorterDuff.Mode tintMode = mDrawables.mTintMode;
            final boolean hasTint = mDrawables.mHasTint;
            final boolean hasTintMode = mDrawables.mHasTintMode;
            final int[] state = getDrawableState();

            for (Drawable dr : mDrawables.mShowing) {
                if (dr == null) {
                    continue;
                }

                if (dr == mDrawables.mDrawableError) {
                    // From a developer's perspective, the error drawable isn't
                    // a compound drawable. Don't apply the generic compound
                    // drawable tint to it.
                    continue;
                }

                dr.mutate();

                if (hasTint) {
                    dr.setTintList(tintList);
                }

                if (hasTintMode) {
                    dr.setTintMode(tintMode);
                }

                // The drawable (or one of its children) may not have been
                // stateful before applying the tint, so let's try again.
                if (dr.isStateful()) {
                    dr.setState(state);
                }
            }
        }
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        if (left != mPaddingLeft ||
            right != mPaddingRight ||
            top != mPaddingTop ||
            bottom != mPaddingBottom) {
            nullLayouts();
        }

        // the super call will requestLayout()
        super.setPadding(left, top, right, bottom);
        invalidate();
    }

    @Override
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        if (start != getPaddingStart() ||
            end != getPaddingEnd() ||
            top != mPaddingTop ||
            bottom != mPaddingBottom) {
            nullLayouts();
        }

        // the super call will requestLayout()
        super.setPaddingRelative(start, top, end, bottom);
        invalidate();
    }

    /**
     * Gets the autolink mask of the text.  See {@link
     * android.text.util.Linkify#ALL Linkify.ALL} and peers for
     * possible values.
     *
     * @attr ref android.R.styleable#TextView_autoLink
     */
    public final int getAutoLinkMask() {
        return mAutoLinkMask;
    }

    /**
     * Sets the text appearance from the specified style resource.
     * <p>
     * Use a framework-defined {@code TextAppearance} style like
     * {@link android.R.style#TextAppearance_Material_Body1 @android:style/TextAppearance.Material.Body1}
     * or see {@link android.R.styleable#TextAppearance TextAppearance} for the
     * set of attributes that can be used in a custom style.
     *
     * @param resId the resource identifier of the style to apply
     * @attr ref android.R.styleable#TextView_textAppearance
     */
    @SuppressWarnings("deprecation")
    public void setTextAppearance(@StyleRes int resId) {
        setTextAppearance(mContext, resId);
    }

    /**
     * Sets the text color, size, style, hint color, and highlight color
     * from the specified TextAppearance resource.
     *
     * @deprecated Use {@link #setTextAppearance(int)} instead.
     */
    @Deprecated
    public void setTextAppearance(Context context, @StyleRes int resId) {
        final TypedArray ta = context.obtainStyledAttributes(resId, R.styleable.TextAppearance);

        final int textColorHighlight = ta.getColor(
                R.styleable.TextAppearance_textColorHighlight, 0);
        if (textColorHighlight != 0) {
            setHighlightColor(textColorHighlight);
        }

        final ColorStateList textColor = ta.getColorStateList(R.styleable.TextAppearance_textColor);
        if (textColor != null) {
            setTextColor(textColor);
        }

        final int textSize = ta.getDimensionPixelSize(R.styleable.TextAppearance_textSize, 0);
        if (textSize != 0) {
            setRawTextSize(textSize);
        }

        final ColorStateList textColorHint = ta.getColorStateList(
                R.styleable.TextAppearance_textColorHint);
        if (textColorHint != null) {
            setHintTextColor(textColorHint);
        }

        final ColorStateList textColorLink = ta.getColorStateList(
                R.styleable.TextAppearance_textColorLink);
        if (textColorLink != null) {
            setLinkTextColor(textColorLink);
        }

        final String fontFamily = ta.getString(R.styleable.TextAppearance_fontFamily);
        final int typefaceIndex = ta.getInt(R.styleable.TextAppearance_typeface, -1);
        final int styleIndex = ta.getInt(R.styleable.TextAppearance_textStyle, -1);
        setTypefaceFromAttrs(fontFamily, typefaceIndex, styleIndex);

        final int shadowColor = ta.getInt(R.styleable.TextAppearance_shadowColor, 0);
        if (shadowColor != 0) {
            final float dx = ta.getFloat(R.styleable.TextAppearance_shadowDx, 0);
            final float dy = ta.getFloat(R.styleable.TextAppearance_shadowDy, 0);
            final float r = ta.getFloat(R.styleable.TextAppearance_shadowRadius, 0);
            setShadowLayer(r, dx, dy, shadowColor);
        }

        if (ta.getBoolean(R.styleable.TextAppearance_textAllCaps, false)) {
            setTransformationMethod(new AllCapsTransformationMethod(getContext()));
        }

        if (ta.hasValue(R.styleable.TextAppearance_elegantTextHeight)) {
            setElegantTextHeight(ta.getBoolean(
                R.styleable.TextAppearance_elegantTextHeight, false));
        }

        if (ta.hasValue(R.styleable.TextAppearance_letterSpacing)) {
            setLetterSpacing(ta.getFloat(
                R.styleable.TextAppearance_letterSpacing, 0));
        }

        if (ta.hasValue(R.styleable.TextAppearance_fontFeatureSettings)) {
            setFontFeatureSettings(ta.getString(
                R.styleable.TextAppearance_fontFeatureSettings));
        }

        ta.recycle();
    }

    /**
     * Get the default primary {@link Locale} of the text in this TextView. This will always be
     * the first member of {@link #getTextLocales()}.
     * @return the default primary {@link Locale} of the text in this TextView.
     */
    @NonNull
    public Locale getTextLocale() {
        return mTextPaint.getTextLocale();
    }

    /**
     * Get the default {@link LocaleList} of the text in this TextView.
     * @return the default {@link LocaleList} of the text in this TextView.
     */
    @NonNull @Size(min=1)
    public LocaleList getTextLocales() {
        return mTextPaint.getTextLocales();
    }

    /**
     * Set the default {@link LocaleList} of the text in this TextView to a one-member list
     * containing just the given value.
     *
     * @param locale the {@link Locale} for drawing text, must not be null.
     *
     * @see #setTextLocales
     */
    public void setTextLocale(@NonNull Locale locale) {
        mLocalesChanged = true;
        mTextPaint.setTextLocale(locale);
        if (mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    /**
     * Set the default {@link LocaleList} of the text in this TextView to the given value.
     *
     * This value is used to choose appropriate typefaces for ambiguous characters (typically used
     * for CJK locales to disambiguate Hanzi/Kanji/Hanja characters). It also affects
     * other aspects of text display, including line breaking.
     *
     * @param locales the {@link LocaleList} for drawing text, must not be null or empty.
     *
     * @see Paint#setTextLocales
     */
    public void setTextLocales(@NonNull @Size(min=1) LocaleList locales) {
        mLocalesChanged = true;
        mTextPaint.setTextLocales(locales);
        if (mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!mLocalesChanged) {
            mTextPaint.setTextLocales(LocaleList.getDefault());
            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * @return the size (in pixels) of the default text size in this TextView.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public float getTextSize() {
        return mTextPaint.getTextSize();
    }

    /**
     * @return the size (in scaled pixels) of thee default text size in this TextView.
     * @hide
     */
    @ViewDebug.ExportedProperty(category = "text")
    public float getScaledTextSize() {
        return mTextPaint.getTextSize() / mTextPaint.density;
    }

    /** @hide */
    @ViewDebug.ExportedProperty(category = "text", mapping = {
            @ViewDebug.IntToString(from = Typeface.NORMAL, to = "NORMAL"),
            @ViewDebug.IntToString(from = Typeface.BOLD, to = "BOLD"),
            @ViewDebug.IntToString(from = Typeface.ITALIC, to = "ITALIC"),
            @ViewDebug.IntToString(from = Typeface.BOLD_ITALIC, to = "BOLD_ITALIC")
    })
    public int getTypefaceStyle() {
        Typeface typeface = mTextPaint.getTypeface();
        return typeface != null ? typeface.getStyle() : Typeface.NORMAL;
    }

    /**
     * Set the default text size to the given value, interpreted as "scaled
     * pixel" units.  This size is adjusted based on the current density and
     * user font size preference.
     *
     * @param size The scaled pixel size.
     *
     * @attr ref android.R.styleable#TextView_textSize
     */
    @android.view.RemotableViewMethod
    public void setTextSize(float size) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
    }

    /**
     * Set the default text size to a given unit and value.  See {@link
     * TypedValue} for the possible dimension units.
     *
     * @param unit The desired dimension unit.
     * @param size The desired size in the given units.
     *
     * @attr ref android.R.styleable#TextView_textSize
     */
    public void setTextSize(int unit, float size) {
        Context c = getContext();
        Resources r;

        if (c == null)
            r = Resources.getSystem();
        else
            r = c.getResources();

        setRawTextSize(TypedValue.applyDimension(
                unit, size, r.getDisplayMetrics()));
    }

    private void setRawTextSize(float size) {
        if (size != mTextPaint.getTextSize()) {
            mTextPaint.setTextSize(size);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * @return the extent by which text is currently being stretched
     * horizontally.  This will usually be 1.
     */
    public float getTextScaleX() {
        return mTextPaint.getTextScaleX();
    }

    /**
     * Sets the extent by which text should be stretched horizontally.
     *
     * @attr ref android.R.styleable#TextView_textScaleX
     */
    @android.view.RemotableViewMethod
    public void setTextScaleX(float size) {
        if (size != mTextPaint.getTextScaleX()) {
            mUserSetTextScaleX = true;
            mTextPaint.setTextScaleX(size);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets the typeface and style in which the text should be displayed.
     * Note that not all Typeface families actually have bold and italic
     * variants, so you may need to use
     * {@link #setTypeface(Typeface, int)} to get the appearance
     * that you actually want.
     *
     * @see #getTypeface()
     *
     * @attr ref android.R.styleable#TextView_fontFamily
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     */
    public void setTypeface(Typeface tf) {
        if (mTextPaint.getTypeface() != tf) {
            mTextPaint.setTypeface(tf);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * @return the current typeface and style in which the text is being
     * displayed.
     *
     * @see #setTypeface(Typeface)
     *
     * @attr ref android.R.styleable#TextView_fontFamily
     * @attr ref android.R.styleable#TextView_typeface
     * @attr ref android.R.styleable#TextView_textStyle
     */
    public Typeface getTypeface() {
        return mTextPaint.getTypeface();
    }

    /**
     * Set the TextView's elegant height metrics flag. This setting selects font
     * variants that have not been compacted to fit Latin-based vertical
     * metrics, and also increases top and bottom bounds to provide more space.
     *
     * @param elegant set the paint's elegant metrics flag.
     *
     * @attr ref android.R.styleable#TextView_elegantTextHeight
     */
    public void setElegantTextHeight(boolean elegant) {
        if (elegant != mTextPaint.isElegantTextHeight()) {
            mTextPaint.setElegantTextHeight(elegant);
            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * @return the extent by which text is currently being letter-spaced.
     * This will normally be 0.
     *
     * @see #setLetterSpacing(float)
     * @see Paint#setLetterSpacing
     */
    public float getLetterSpacing() {
        return mTextPaint.getLetterSpacing();
    }

    /**
     * Sets text letter-spacing.  The value is in 'EM' units.  Typical values
     * for slight expansion will be around 0.05.  Negative values tighten text.
     *
     * @see #getLetterSpacing()
     * @see Paint#getLetterSpacing
     *
     * @attr ref android.R.styleable#TextView_letterSpacing
     */
    @android.view.RemotableViewMethod
    public void setLetterSpacing(float letterSpacing) {
        if (letterSpacing != mTextPaint.getLetterSpacing()) {
            mTextPaint.setLetterSpacing(letterSpacing);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Returns the font feature settings. The format is the same as the CSS
     * font-feature-settings attribute:
     * <a href="http://dev.w3.org/csswg/css-fonts/#propdef-font-feature-settings">
     *     http://dev.w3.org/csswg/css-fonts/#propdef-font-feature-settings</a>
     *
     * @return the currently set font feature settings.  Default is null.
     *
     * @see #setFontFeatureSettings(String)
     * @see Paint#setFontFeatureSettings(String) Paint.setFontFeatureSettings(String)
     */
    @Nullable
    public String getFontFeatureSettings() {
        return mTextPaint.getFontFeatureSettings();
    }

    /**
     * Sets the break strategy for breaking paragraphs into lines. The default value for
     * TextView is {@link Layout#BREAK_STRATEGY_HIGH_QUALITY}, and the default value for
     * EditText is {@link Layout#BREAK_STRATEGY_SIMPLE}, the latter to avoid the
     * text "dancing" when being edited.
     *
     * @attr ref android.R.styleable#TextView_breakStrategy
     * @see #getBreakStrategy()
     */
    public void setBreakStrategy(@Layout.BreakStrategy int breakStrategy) {
        mBreakStrategy = breakStrategy;
        if (mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    /**
     * @return the currently set break strategy.
     *
     * @attr ref android.R.styleable#TextView_breakStrategy
     * @see #setBreakStrategy(int)
     */
    @Layout.BreakStrategy
    public int getBreakStrategy() {
        return mBreakStrategy;
    }

    /**
     * Sets the hyphenation frequency. The default value for both TextView and EditText, which is set
     * from the theme, is {@link Layout#HYPHENATION_FREQUENCY_NORMAL}.
     *
     * @attr ref android.R.styleable#TextView_hyphenationFrequency
     * @see #getHyphenationFrequency()
     */
    public void setHyphenationFrequency(@Layout.HyphenationFrequency int hyphenationFrequency) {
        mHyphenationFrequency = hyphenationFrequency;
        if (mLayout != null) {
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    /**
     * @return the currently set hyphenation frequency.
     *
     * @attr ref android.R.styleable#TextView_hyphenationFrequency
     * @see #setHyphenationFrequency(int)
     */
    @Layout.HyphenationFrequency
    public int getHyphenationFrequency() {
        return mHyphenationFrequency;
    }

    /**
     * Sets font feature settings. The format is the same as the CSS
     * font-feature-settings attribute:
     * <a href="http://dev.w3.org/csswg/css-fonts/#propdef-font-feature-settings">
     *     http://dev.w3.org/csswg/css-fonts/#propdef-font-feature-settings</a>
     *
     * @param fontFeatureSettings font feature settings represented as CSS compatible string
     *
     * @see #getFontFeatureSettings()
     * @see Paint#getFontFeatureSettings() Paint.getFontFeatureSettings()
     *
     * @attr ref android.R.styleable#TextView_fontFeatureSettings
     */
    @android.view.RemotableViewMethod
    public void setFontFeatureSettings(@Nullable String fontFeatureSettings) {
        if (fontFeatureSettings != mTextPaint.getFontFeatureSettings()) {
            mTextPaint.setFontFeatureSettings(fontFeatureSettings);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }


    /**
     * Sets the text color for all the states (normal, selected,
     * focused) to be this color.
     *
     * @param color A color value in the form 0xAARRGGBB.
     * Do not pass a resource ID. To get a color value from a resource ID, call
     * {@link android.support.v4.content.ContextCompat#getColor(Context, int) getColor}.
     *
     * @see #setTextColor(ColorStateList)
     * @see #getTextColors()
     *
     * @attr ref android.R.styleable#TextView_textColor
     */
    @android.view.RemotableViewMethod
    public void setTextColor(@ColorInt int color) {
        mTextColor = ColorStateList.valueOf(color);
        updateTextColors();
    }

    /**
     * Sets the text color.
     *
     * @see #setTextColor(int)
     * @see #getTextColors()
     * @see #setHintTextColor(ColorStateList)
     * @see #setLinkTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColor
     */
    @android.view.RemotableViewMethod
    public void setTextColor(ColorStateList colors) {
        if (colors == null) {
            throw new NullPointerException();
        }

        mTextColor = colors;
        updateTextColors();
    }

    /**
     * Gets the text colors for the different states (normal, selected, focused) of the TextView.
     *
     * @see #setTextColor(ColorStateList)
     * @see #setTextColor(int)
     *
     * @attr ref android.R.styleable#TextView_textColor
     */
    public final ColorStateList getTextColors() {
        return mTextColor;
    }

    /**
     * <p>Return the current color selected for normal text.</p>
     *
     * @return Returns the current text color.
     */
    @ColorInt
    public final int getCurrentTextColor() {
        return mCurTextColor;
    }

    /**
     * Sets the color used to display the selection highlight.
     *
     * @attr ref android.R.styleable#TextView_textColorHighlight
     */
    @android.view.RemotableViewMethod
    public void setHighlightColor(@ColorInt int color) {
        if (mHighlightColor != color) {
            mHighlightColor = color;
            invalidate();
        }
    }

    /**
     * @return the color used to display the selection highlight
     *
     * @see #setHighlightColor(int)
     *
     * @attr ref android.R.styleable#TextView_textColorHighlight
     */
    @ColorInt
    public int getHighlightColor() {
        return mHighlightColor;
    }

    /**
     * Sets whether the soft input method will be made visible when this
     * TextView gets focused. The default is true.
     */
    @android.view.RemotableViewMethod
    public final void setShowSoftInputOnFocus(boolean show) {
        createEditorIfNeeded();
        mEditor.mShowSoftInputOnFocus = show;
    }

    /**
     * Returns whether the soft input method will be made visible when this
     * TextView gets focused. The default is true.
     */
    public final boolean getShowSoftInputOnFocus() {
        // When there is no Editor, return default true value
        return mEditor == null || mEditor.mShowSoftInputOnFocus;
    }

    /**
     * Gives the text a shadow of the specified blur radius and color, the specified
     * distance from its drawn position.
     * <p>
     * The text shadow produced does not interact with the properties on view
     * that are responsible for real time shadows,
     * {@link View#getElevation() elevation} and
     * {@link View#getTranslationZ() translationZ}.
     *
     * @see Paint#setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowColor
     * @attr ref android.R.styleable#TextView_shadowDx
     * @attr ref android.R.styleable#TextView_shadowDy
     * @attr ref android.R.styleable#TextView_shadowRadius
     */
    public void setShadowLayer(float radius, float dx, float dy, int color) {
        mTextPaint.setShadowLayer(radius, dx, dy, color);

        mShadowRadius = radius;
        mShadowDx = dx;
        mShadowDy = dy;
        mShadowColor = color;

        // Will change text clip region
        if (mEditor != null) {
            mEditor.invalidateTextDisplayList();
            mEditor.invalidateHandlesAndActionMode();
        }
        invalidate();
    }

    /**
     * Gets the radius of the shadow layer.
     *
     * @return the radius of the shadow layer. If 0, the shadow layer is not visible
     *
     * @see #setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowRadius
     */
    public float getShadowRadius() {
        return mShadowRadius;
    }

    /**
     * @return the horizontal offset of the shadow layer
     *
     * @see #setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowDx
     */
    public float getShadowDx() {
        return mShadowDx;
    }

    /**
     * @return the vertical offset of the shadow layer
     *
     * @see #setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowDy
     */
    public float getShadowDy() {
        return mShadowDy;
    }

    /**
     * @return the color of the shadow layer
     *
     * @see #setShadowLayer(float, float, float, int)
     *
     * @attr ref android.R.styleable#TextView_shadowColor
     */
    @ColorInt
    public int getShadowColor() {
        return mShadowColor;
    }

    /**
     * @return the base paint used for the text.  Please use this only to
     * consult the Paint's properties and not to change them.
     */
    public TextPaint getPaint() {
        return mTextPaint;
    }

    /**
     * Sets the autolink mask of the text.  See {@link
     * android.text.util.Linkify#ALL Linkify.ALL} and peers for
     * possible values.
     *
     * @attr ref android.R.styleable#TextView_autoLink
     */
    @android.view.RemotableViewMethod
    public final void setAutoLinkMask(int mask) {
        mAutoLinkMask = mask;
    }

    /**
     * Sets whether the movement method will automatically be set to
     * {@link LinkMovementMethod} if {@link #setAutoLinkMask} has been
     * set to nonzero and links are detected in {@link #setText}.
     * The default is true.
     *
     * @attr ref android.R.styleable#TextView_linksClickable
     */
    @android.view.RemotableViewMethod
    public final void setLinksClickable(boolean whether) {
        mLinksClickable = whether;
    }

    /**
     * Returns whether the movement method will automatically be set to
     * {@link LinkMovementMethod} if {@link #setAutoLinkMask} has been
     * set to nonzero and links are detected in {@link #setText}.
     * The default is true.
     *
     * @attr ref android.R.styleable#TextView_linksClickable
     */
    public final boolean getLinksClickable() {
        return mLinksClickable;
    }

    /**
     * Returns the list of URLSpans attached to the text
     * (by {@link Linkify} or otherwise) if any.  You can call
     * {@link URLSpan#getURL} on them to find where they link to
     * or use {@link Spanned#getSpanStart} and {@link Spanned#getSpanEnd}
     * to find the region of the text they are attached to.
     */
    public URLSpan[] getUrls() {
        if (mText instanceof Spanned) {
            return ((Spanned) mText).getSpans(0, mText.length(), URLSpan.class);
        } else {
            return new URLSpan[0];
        }
    }

    /**
     * Sets the color of the hint text for all the states (disabled, focussed, selected...) of this
     * TextView.
     *
     * @see #setHintTextColor(ColorStateList)
     * @see #getHintTextColors()
     * @see #setTextColor(int)
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     */
    @android.view.RemotableViewMethod
    public final void setHintTextColor(@ColorInt int color) {
        mHintTextColor = ColorStateList.valueOf(color);
        updateTextColors();
    }

    /**
     * Sets the color of the hint text.
     *
     * @see #getHintTextColors()
     * @see #setHintTextColor(int)
     * @see #setTextColor(ColorStateList)
     * @see #setLinkTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     */
    public final void setHintTextColor(ColorStateList colors) {
        mHintTextColor = colors;
        updateTextColors();
    }

    /**
     * @return the color of the hint text, for the different states of this TextView.
     *
     * @see #setHintTextColor(ColorStateList)
     * @see #setHintTextColor(int)
     * @see #setTextColor(ColorStateList)
     * @see #setLinkTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColorHint
     */
    public final ColorStateList getHintTextColors() {
        return mHintTextColor;
    }

    /**
     * <p>Return the current color selected to paint the hint text.</p>
     *
     * @return Returns the current hint text color.
     */
    @ColorInt
    public final int getCurrentHintTextColor() {
        return mHintTextColor != null ? mCurHintTextColor : mCurTextColor;
    }

    /**
     * Sets the color of links in the text.
     *
     * @see #setLinkTextColor(ColorStateList)
     * @see #getLinkTextColors()
     *
     * @attr ref android.R.styleable#TextView_textColorLink
     */
    @android.view.RemotableViewMethod
    public final void setLinkTextColor(@ColorInt int color) {
        mLinkTextColor = ColorStateList.valueOf(color);
        updateTextColors();
    }

    /**
     * Sets the color of links in the text.
     *
     * @see #setLinkTextColor(int)
     * @see #getLinkTextColors()
     * @see #setTextColor(ColorStateList)
     * @see #setHintTextColor(ColorStateList)
     *
     * @attr ref android.R.styleable#TextView_textColorLink
     */
    public final void setLinkTextColor(ColorStateList colors) {
        mLinkTextColor = colors;
        updateTextColors();
    }

    /**
     * @return the list of colors used to paint the links in the text, for the different states of
     * this TextView
     *
     * @see #setLinkTextColor(ColorStateList)
     * @see #setLinkTextColor(int)
     *
     * @attr ref android.R.styleable#TextView_textColorLink
     */
    public final ColorStateList getLinkTextColors() {
        return mLinkTextColor;
    }

    /**
     * Sets the horizontal alignment of the text and the
     * vertical gravity that will be used when there is extra space
     * in the TextView beyond what is required for the text itself.
     *
     * @see android.view.Gravity
     * @attr ref android.R.styleable#TextView_gravity
     */
    public void setGravity(int gravity) {
        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.START;
        }
        if ((gravity & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
            gravity |= Gravity.TOP;
        }

        boolean newLayout = false;

        if ((gravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) !=
            (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)) {
            newLayout = true;
        }

        if (gravity != mGravity) {
            invalidate();
        }

        mGravity = gravity;

        if (mLayout != null && newLayout) {
            // XXX this is heavy-handed because no actual content changes.
            int want = mLayout.getWidth();
            int hintWant = mHintLayout == null ? 0 : mHintLayout.getWidth();

            makeNewLayout(want, hintWant, UNKNOWN_BORING, UNKNOWN_BORING,
                          mRight - mLeft - getCompoundPaddingLeft() -
                          getCompoundPaddingRight(), true);
        }
    }

    /**
     * Returns the horizontal and vertical alignment of this TextView.
     *
     * @see android.view.Gravity
     * @attr ref android.R.styleable#TextView_gravity
     */
    public int getGravity() {
        return mGravity;
    }

    /**
     * @return the flags on the Paint being used to display the text.
     * @see Paint#getFlags
     */
    public int getPaintFlags() {
        return mTextPaint.getFlags();
    }

    /**
     * Sets flags on the Paint being used to display the text and
     * reflows the text if they are different from the old flags.
     * @see Paint#setFlags
     */
    @android.view.RemotableViewMethod
    public void setPaintFlags(int flags) {
        if (mTextPaint.getFlags() != flags) {
            mTextPaint.setFlags(flags);

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets whether the text should be allowed to be wider than the
     * View is.  If false, it will be wrapped to the width of the View.
     *
     * @attr ref android.R.styleable#TextView_scrollHorizontally
     */
    public void setHorizontallyScrolling(boolean whether) {
        if (mHorizontallyScrolling != whether) {
            mHorizontallyScrolling = whether;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Returns whether the text is allowed to be wider than the View is.
     * If false, the text will be wrapped to the width of the View.
     *
     * @attr ref android.R.styleable#TextView_scrollHorizontally
     * @hide
     */
    public boolean getHorizontallyScrolling() {
        return mHorizontallyScrolling;
    }

    /**
     * Makes the TextView at least this many lines tall.
     *
     * Setting this value overrides any other (minimum) height setting. A single line TextView will
     * set this value to 1.
     *
     * @see #getMinLines()
     *
     * @attr ref android.R.styleable#TextView_minLines
     */
    @android.view.RemotableViewMethod
    public void setMinLines(int minlines) {
        mMinimum = minlines;
        mMinMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * @return the minimum number of lines displayed in this TextView, or -1 if the minimum
     * height was set in pixels instead using {@link #setMinHeight(int) or #setHeight(int)}.
     *
     * @see #setMinLines(int)
     *
     * @attr ref android.R.styleable#TextView_minLines
     */
    public int getMinLines() {
        return mMinMode == LINES ? mMinimum : -1;
    }

    /**
     * Makes the TextView at least this many pixels tall.
     *
     * Setting this value overrides any other (minimum) number of lines setting.
     *
     * @attr ref android.R.styleable#TextView_minHeight
     */
    @android.view.RemotableViewMethod
    public void setMinHeight(int minHeight) {
        mMinimum = minHeight;
        mMinMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the minimum height of this TextView expressed in pixels, or -1 if the minimum
     * height was set in number of lines instead using {@link #setMinLines(int) or #setLines(int)}.
     *
     * @see #setMinHeight(int)
     *
     * @attr ref android.R.styleable#TextView_minHeight
     */
    public int getMinHeight() {
        return mMinMode == PIXELS ? mMinimum : -1;
    }

    /**
     * Makes the TextView at most this many lines tall.
     *
     * Setting this value overrides any other (maximum) height setting.
     *
     * @attr ref android.R.styleable#TextView_maxLines
     */
    @android.view.RemotableViewMethod
    public void setMaxLines(int maxlines) {
        mMaximum = maxlines;
        mMaxMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * @return the maximum number of lines displayed in this TextView, or -1 if the maximum
     * height was set in pixels instead using {@link #setMaxHeight(int) or #setHeight(int)}.
     *
     * @see #setMaxLines(int)
     *
     * @attr ref android.R.styleable#TextView_maxLines
     */
    public int getMaxLines() {
        return mMaxMode == LINES ? mMaximum : -1;
    }

    /**
     * Makes the TextView at most this many pixels tall.  This option is mutually exclusive with the
     * {@link #setMaxLines(int)} method.
     *
     * Setting this value overrides any other (maximum) number of lines setting.
     *
     * @attr ref android.R.styleable#TextView_maxHeight
     */
    @android.view.RemotableViewMethod
    public void setMaxHeight(int maxHeight) {
        mMaximum = maxHeight;
        mMaxMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the maximum height of this TextView expressed in pixels, or -1 if the maximum
     * height was set in number of lines instead using {@link #setMaxLines(int) or #setLines(int)}.
     *
     * @see #setMaxHeight(int)
     *
     * @attr ref android.R.styleable#TextView_maxHeight
     */
    public int getMaxHeight() {
        return mMaxMode == PIXELS ? mMaximum : -1;
    }

    /**
     * Makes the TextView exactly this many lines tall.
     *
     * Note that setting this value overrides any other (minimum / maximum) number of lines or
     * height setting. A single line TextView will set this value to 1.
     *
     * @attr ref android.R.styleable#TextView_lines
     */
    @android.view.RemotableViewMethod
    public void setLines(int lines) {
        mMaximum = mMinimum = lines;
        mMaxMode = mMinMode = LINES;

        requestLayout();
        invalidate();
    }

    /**
     * Makes the TextView exactly this many pixels tall.
     * You could do the same thing by specifying this number in the
     * LayoutParams.
     *
     * Note that setting this value overrides any other (minimum / maximum) number of lines or
     * height setting.
     *
     * @attr ref android.R.styleable#TextView_height
     */
    @android.view.RemotableViewMethod
    public void setHeight(int pixels) {
        mMaximum = mMinimum = pixels;
        mMaxMode = mMinMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Makes the TextView at least this many ems wide
     *
     * @attr ref android.R.styleable#TextView_minEms
     */
    @android.view.RemotableViewMethod
    public void setMinEms(int minems) {
        mMinWidth = minems;
        mMinWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the minimum width of the TextView, expressed in ems or -1 if the minimum width
     * was set in pixels instead (using {@link #setMinWidth(int)} or {@link #setWidth(int)}).
     *
     * @see #setMinEms(int)
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_minEms
     */
    public int getMinEms() {
        return mMinWidthMode == EMS ? mMinWidth : -1;
    }

    /**
     * Makes the TextView at least this many pixels wide
     *
     * @attr ref android.R.styleable#TextView_minWidth
     */
    @android.view.RemotableViewMethod
    public void setMinWidth(int minpixels) {
        mMinWidth = minpixels;
        mMinWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the minimum width of the TextView, in pixels or -1 if the minimum width
     * was set in ems instead (using {@link #setMinEms(int)} or {@link #setEms(int)}).
     *
     * @see #setMinWidth(int)
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_minWidth
     */
    public int getMinWidth() {
        return mMinWidthMode == PIXELS ? mMinWidth : -1;
    }

    /**
     * Makes the TextView at most this many ems wide
     *
     * @attr ref android.R.styleable#TextView_maxEms
     */
    @android.view.RemotableViewMethod
    public void setMaxEms(int maxems) {
        mMaxWidth = maxems;
        mMaxWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the maximum width of the TextView, expressed in ems or -1 if the maximum width
     * was set in pixels instead (using {@link #setMaxWidth(int)} or {@link #setWidth(int)}).
     *
     * @see #setMaxEms(int)
     * @see #setEms(int)
     *
     * @attr ref android.R.styleable#TextView_maxEms
     */
    public int getMaxEms() {
        return mMaxWidthMode == EMS ? mMaxWidth : -1;
    }

    /**
     * Makes the TextView at most this many pixels wide
     *
     * @attr ref android.R.styleable#TextView_maxWidth
     */
    @android.view.RemotableViewMethod
    public void setMaxWidth(int maxpixels) {
        mMaxWidth = maxpixels;
        mMaxWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * @return the maximum width of the TextView, in pixels or -1 if the maximum width
     * was set in ems instead (using {@link #setMaxEms(int)} or {@link #setEms(int)}).
     *
     * @see #setMaxWidth(int)
     * @see #setWidth(int)
     *
     * @attr ref android.R.styleable#TextView_maxWidth
     */
    public int getMaxWidth() {
        return mMaxWidthMode == PIXELS ? mMaxWidth : -1;
    }

    /**
     * Makes the TextView exactly this many ems wide
     *
     * @see #setMaxEms(int)
     * @see #setMinEms(int)
     * @see #getMinEms()
     * @see #getMaxEms()
     *
     * @attr ref android.R.styleable#TextView_ems
     */
    @android.view.RemotableViewMethod
    public void setEms(int ems) {
        mMaxWidth = mMinWidth = ems;
        mMaxWidthMode = mMinWidthMode = EMS;

        requestLayout();
        invalidate();
    }

    /**
     * Makes the TextView exactly this many pixels wide.
     * You could do the same thing by specifying this number in the
     * LayoutParams.
     *
     * @see #setMaxWidth(int)
     * @see #setMinWidth(int)
     * @see #getMinWidth()
     * @see #getMaxWidth()
     *
     * @attr ref android.R.styleable#TextView_width
     */
    @android.view.RemotableViewMethod
    public void setWidth(int pixels) {
        mMaxWidth = mMinWidth = pixels;
        mMaxWidthMode = mMinWidthMode = PIXELS;

        requestLayout();
        invalidate();
    }

    /**
     * Sets line spacing for this TextView.  Each line will have its height
     * multiplied by <code>mult</code> and have <code>add</code> added to it.
     *
     * @attr ref android.R.styleable#TextView_lineSpacingExtra
     * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
     */
    public void setLineSpacing(float add, float mult) {
        if (mSpacingAdd != add || mSpacingMult != mult) {
            mSpacingAdd = add;
            mSpacingMult = mult;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Gets the line spacing multiplier
     *
     * @return the value by which each line's height is multiplied to get its actual height.
     *
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingExtra()
     *
     * @attr ref android.R.styleable#TextView_lineSpacingMultiplier
     */
    public float getLineSpacingMultiplier() {
        return mSpacingMult;
    }

    /**
     * Gets the line spacing extra space
     *
     * @return the extra space that is added to the height of each lines of this TextView.
     *
     * @see #setLineSpacing(float, float)
     * @see #getLineSpacingMultiplier()
     *
     * @attr ref android.R.styleable#TextView_lineSpacingExtra
     */
    public float getLineSpacingExtra() {
        return mSpacingAdd;
    }

    /**
     * Convenience method: Append the specified text to the TextView's
     * display buffer, upgrading it to BufferType.EDITABLE if it was
     * not already editable.
     */
    public final void append(CharSequence text) {
        append(text, 0, text.length());
    }

    /**
     * Convenience method: Append the specified text slice to the TextView's
     * display buffer, upgrading it to BufferType.EDITABLE if it was
     * not already editable.
     */
    public void append(CharSequence text, int start, int end) {
        if (!(mText instanceof Editable)) {
            setText(mText, BufferType.EDITABLE);
        }

        ((Editable) mText).append(text, start, end);

        if (mAutoLinkMask != 0) {
            boolean linksWereAdded = Linkify.addLinks((Spannable) mText, mAutoLinkMask);
            // Do not change the movement method for text that support text selection as it
            // would prevent an arbitrary cursor displacement.
            if (linksWereAdded && mLinksClickable && !textCanBeSelected()) {
                setMovementMethod(LinkMovementMethod.getInstance());
            }
        }
    }

    private void updateTextColors() {
        boolean inval = false;
        int color = mTextColor.getColorForState(getDrawableState(), 0);
        if (color != mCurTextColor) {
            mCurTextColor = color;
            inval = true;
        }
        if (mLinkTextColor != null) {
            color = mLinkTextColor.getColorForState(getDrawableState(), 0);
            if (color != mTextPaint.linkColor) {
                mTextPaint.linkColor = color;
                inval = true;
            }
        }
        if (mHintTextColor != null) {
            color = mHintTextColor.getColorForState(getDrawableState(), 0);
            if (color != mCurHintTextColor) {
                mCurHintTextColor = color;
                if (mText.length() == 0) {
                    inval = true;
                }
            }
        }
        if (inval) {
            // Text needs to be redrawn with the new color
            if (mEditor != null) mEditor.invalidateTextDisplayList();
            invalidate();
        }
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        if (mTextColor != null && mTextColor.isStateful()
                || (mHintTextColor != null && mHintTextColor.isStateful())
                || (mLinkTextColor != null && mLinkTextColor.isStateful())) {
            updateTextColors();
        }

        if (mDrawables != null) {
            final int[] state = getDrawableState();
            for (Drawable dr : mDrawables.mShowing) {
                if (dr != null && dr.isStateful() && dr.setState(state)) {
                    invalidateDrawable(dr);
                }
            }
        }
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        if (mDrawables != null) {
            for (Drawable dr : mDrawables.mShowing) {
                if (dr != null) {
                    dr.setHotspot(x, y);
                }
            }
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        // Save state if we are forced to
        final boolean freezesText = getFreezesText();
        boolean hasSelection = false;
        int start = -1;
        int end = -1;

        if (mText != null) {
            start = getSelectionStart();
            end = getSelectionEnd();
            if (start >= 0 || end >= 0) {
                // Or save state if there is a selection
                hasSelection = true;
            }
        }

        if (freezesText || hasSelection) {
            SavedState ss = new SavedState(superState);

            if (freezesText) {
                if (mText instanceof Spanned) {
                    final Spannable sp = new SpannableStringBuilder(mText);

                    if (mEditor != null) {
                        removeMisspelledSpans(sp);
                        sp.removeSpan(mEditor.mSuggestionRangeSpan);
                    }

                    ss.text = sp;
                } else {
                    ss.text = mText.toString();
                }
            }

            if (hasSelection) {
                // XXX Should also save the current scroll position!
                ss.selStart = start;
                ss.selEnd = end;
            }

            if (isFocused() && start >= 0 && end >= 0) {
                ss.frozenWithFocus = true;
            }

            ss.error = getError();

            if (mEditor != null) {
                ss.editorState = mEditor.saveInstanceState();
            }
            return ss;
        }

        return superState;
    }

    void removeMisspelledSpans(Spannable spannable) {
        SuggestionSpan[] suggestionSpans = spannable.getSpans(0, spannable.length(),
                SuggestionSpan.class);
        for (int i = 0; i < suggestionSpans.length; i++) {
            int flags = suggestionSpans[i].getFlags();
            if ((flags & SuggestionSpan.FLAG_EASY_CORRECT) != 0
                    && (flags & SuggestionSpan.FLAG_MISSPELLED) != 0) {
                spannable.removeSpan(suggestionSpans[i]);
            }
        }
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState ss = (SavedState)state;
        super.onRestoreInstanceState(ss.getSuperState());

        // XXX restore buffer type too, as well as lots of other stuff
        if (ss.text != null) {
            setText(ss.text);
        }

        if (ss.selStart >= 0 && ss.selEnd >= 0) {
            if (mText instanceof Spannable) {
                int len = mText.length();

                if (ss.selStart > len || ss.selEnd > len) {
                    String restored = "";

                    if (ss.text != null) {
                        restored = "(restored) ";
                    }

                    Log.e(LOG_TAG, "Saved cursor position " + ss.selStart +
                          "/" + ss.selEnd + " out of range for " + restored +
                          "text " + mText);
                } else {
                    Selection.setSelection((Spannable) mText, ss.selStart, ss.selEnd);

                    if (ss.frozenWithFocus) {
                        createEditorIfNeeded();
                        mEditor.mFrozenWithFocus = true;
                    }
                }
            }
        }

        if (ss.error != null) {
            final CharSequence error = ss.error;
            // Display the error later, after the first layout pass
            post(new Runnable() {
                public void run() {
                    if (mEditor == null || !mEditor.mErrorWasChanged) {
                        setError(error);
                    }
                }
            });
        }

        if (ss.editorState != null) {
            createEditorIfNeeded();
            mEditor.restoreInstanceState(ss.editorState);
        }
    }

    /**
     * Control whether this text view saves its entire text contents when
     * freezing to an icicle, in addition to dynamic state such as cursor
     * position.  By default this is false, not saving the text.  Set to true
     * if the text in the text view is not being saved somewhere else in
     * persistent storage (such as in a content provider) so that if the
     * view is later thawed the user will not lose their data. For
     * {@link android.widget.EditText} it is always enabled, regardless of
     * the value of the attribute.
     *
     * @param freezesText Controls whether a frozen icicle should include the
     * entire text data: true to include it, false to not.
     *
     * @attr ref android.R.styleable#TextView_freezesText
     */
    @android.view.RemotableViewMethod
    public void setFreezesText(boolean freezesText) {
        mFreezesText = freezesText;
    }

    /**
     * Return whether this text view is including its entire text contents
     * in frozen icicles. For {@link android.widget.EditText} it always returns true.
     *
     * @return Returns true if text is included, false if it isn't.
     *
     * @see #setFreezesText
     */
    public boolean getFreezesText() {
        return mFreezesText;
    }

    ///////////////////////////////////////////////////////////////////////////

    /**
     * Sets the Factory used to create new Editables.
     */
    public final void setEditableFactory(Editable.Factory factory) {
        mEditableFactory = factory;
        setText(mText);
    }

    /**
     * Sets the Factory used to create new Spannables.
     */
    public final void setSpannableFactory(Spannable.Factory factory) {
        mSpannableFactory = factory;
        setText(mText);
    }

    /**
     * Sets the string value of the TextView. TextView <em>does not</em> accept
     * HTML-like formatting, which you can do with text strings in XML resource files.
     * To style your strings, attach android.text.style.* objects to a
     * {@link android.text.SpannableString SpannableString}, or see the
     * <a href="{@docRoot}guide/topics/resources/available-resources.html#stringresources">
     * Available Resource Types</a> documentation for an example of setting
     * formatted text in the XML resource file.
     *
     * @attr ref android.R.styleable#TextView_text
     */
    @android.view.RemotableViewMethod
    public final void setText(CharSequence text) {
        setText(text, mBufferType);
    }

    /**
     * Like {@link #setText(CharSequence)},
     * except that the cursor position (if any) is retained in the new text.
     *
     * @param text The new text to place in the text view.
     *
     * @see #setText(CharSequence)
     */
    @android.view.RemotableViewMethod
    public final void setTextKeepState(CharSequence text) {
        setTextKeepState(text, mBufferType);
    }

    /**
     * Sets the text that this TextView is to display (see
     * {@link #setText(CharSequence)}) and also sets whether it is stored
     * in a styleable/spannable buffer and whether it is editable.
     *
     * @attr ref android.R.styleable#TextView_text
     * @attr ref android.R.styleable#TextView_bufferType
     */
    public void setText(CharSequence text, BufferType type) {
        setText(text, type, true, 0);

        if (mCharWrapper != null) {
            mCharWrapper.mChars = null;
        }
    }

    private void setText(CharSequence text, BufferType type,
                         boolean notifyBefore, int oldlen) {
        if (text == null) {
            text = "";
        }

        // If suggestions are not enabled, remove the suggestion spans from the text
        if (!isSuggestionsEnabled()) {
            text = removeSuggestionSpans(text);
        }

        if (!mUserSetTextScaleX) mTextPaint.setTextScaleX(1.0f);

        if (text instanceof Spanned &&
            ((Spanned) text).getSpanStart(TextUtils.TruncateAt.MARQUEE) >= 0) {
            if (ViewConfiguration.get(mContext).isFadingMarqueeEnabled()) {
                setHorizontalFadingEdgeEnabled(true);
                mMarqueeFadeMode = MARQUEE_FADE_NORMAL;
            } else {
                setHorizontalFadingEdgeEnabled(false);
                mMarqueeFadeMode = MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS;
            }
            setEllipsize(TextUtils.TruncateAt.MARQUEE);
        }

        int n = mFilters.length;
        for (int i = 0; i < n; i++) {
            CharSequence out = mFilters[i].filter(text, 0, text.length(), EMPTY_SPANNED, 0, 0);
            if (out != null) {
                text = out;
            }
        }

        if (notifyBefore) {
            if (mText != null) {
                oldlen = mText.length();
                sendBeforeTextChanged(mText, 0, oldlen, text.length());
            } else {
                sendBeforeTextChanged("", 0, 0, text.length());
            }
        }

        boolean needEditableForNotification = false;

        if (mListeners != null && mListeners.size() != 0) {
            needEditableForNotification = true;
        }

        if (type == BufferType.EDITABLE || getKeyListener() != null ||
                needEditableForNotification) {
            createEditorIfNeeded();
            mEditor.forgetUndoRedo();
            Editable t = mEditableFactory.newEditable(text);
            text = t;
            setFilters(t, mFilters);
            InputMethodManager imm = InputMethodManager.peekInstance();
            if (imm != null) imm.restartInput(this);
        } else if (type == BufferType.SPANNABLE || mMovement != null) {
            text = mSpannableFactory.newSpannable(text);
        } else if (!(text instanceof CharWrapper)) {
            text = TextUtils.stringOrSpannedString(text);
        }

        if (mAutoLinkMask != 0) {
            Spannable s2;

            if (type == BufferType.EDITABLE || text instanceof Spannable) {
                s2 = (Spannable) text;
            } else {
                s2 = mSpannableFactory.newSpannable(text);
            }

            if (Linkify.addLinks(s2, mAutoLinkMask)) {
                text = s2;
                type = (type == BufferType.EDITABLE) ? BufferType.EDITABLE : BufferType.SPANNABLE;

                /*
                 * We must go ahead and set the text before changing the
                 * movement method, because setMovementMethod() may call
                 * setText() again to try to upgrade the buffer type.
                 */
                mText = text;

                // Do not change the movement method for text that support text selection as it
                // would prevent an arbitrary cursor displacement.
                if (mLinksClickable && !textCanBeSelected()) {
                    setMovementMethod(LinkMovementMethod.getInstance());
                }
            }
        }

        mBufferType = type;
        mText = text;

        if (mTransformation == null) {
            mTransformed = text;
        } else {
            mTransformed = mTransformation.getTransformation(text, this);
        }

        final int textLength = text.length();

        if (text instanceof Spannable && !mAllowTransformationLengthChange) {
            Spannable sp = (Spannable) text;

            // Remove any ChangeWatchers that might have come from other TextViews.
            final ChangeWatcher[] watchers = sp.getSpans(0, sp.length(), ChangeWatcher.class);
            final int count = watchers.length;
            for (int i = 0; i < count; i++) {
                sp.removeSpan(watchers[i]);
            }

            if (mChangeWatcher == null) mChangeWatcher = new ChangeWatcher();

            sp.setSpan(mChangeWatcher, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE |
                       (CHANGE_WATCHER_PRIORITY << Spanned.SPAN_PRIORITY_SHIFT));

            if (mEditor != null) mEditor.addSpanWatchers(sp);

            if (mTransformation != null) {
                sp.setSpan(mTransformation, 0, textLength, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            }

            if (mMovement != null) {
                mMovement.initialize(this, (Spannable) text);

                /*
                 * Initializing the movement method will have set the
                 * selection, so reset mSelectionMoved to keep that from
                 * interfering with the normal on-focus selection-setting.
                 */
                if (mEditor != null) mEditor.mSelectionMoved = false;
            }
        }

        if (mLayout != null) {
            checkForRelayout();
        }

        sendOnTextChanged(text, 0, oldlen, textLength);
        onTextChanged(text, 0, oldlen, textLength);

        notifyViewAccessibilityStateChangedIfNeeded(AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT);

        if (needEditableForNotification) {
            sendAfterTextChanged((Editable) text);
        }

        // SelectionModifierCursorController depends on textCanBeSelected, which depends on text
        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    /**
     * Sets the TextView to display the specified slice of the specified
     * char array.  You must promise that you will not change the contents
     * of the array except for right before another call to setText(),
     * since the TextView has no way to know that the text
     * has changed and that it needs to invalidate and re-layout.
     */
    public final void setText(char[] text, int start, int len) {
        int oldlen = 0;

        if (start < 0 || len < 0 || start + len > text.length) {
            throw new IndexOutOfBoundsException(start + ", " + len);
        }

        /*
         * We must do the before-notification here ourselves because if
         * the old text is a CharWrapper we destroy it before calling
         * into the normal path.
         */
        if (mText != null) {
            oldlen = mText.length();
            sendBeforeTextChanged(mText, 0, oldlen, len);
        } else {
            sendBeforeTextChanged("", 0, 0, len);
        }

        if (mCharWrapper == null) {
            mCharWrapper = new CharWrapper(text, start, len);
        } else {
            mCharWrapper.set(text, start, len);
        }

        setText(mCharWrapper, mBufferType, false, oldlen);
    }

    /**
     * Like {@link #setText(CharSequence, android.widget.TextView.BufferType)},
     * except that the cursor position (if any) is retained in the new text.
     *
     * @see #setText(CharSequence, android.widget.TextView.BufferType)
     */
    public final void setTextKeepState(CharSequence text, BufferType type) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        int len = text.length();

        setText(text, type);

        if (start >= 0 || end >= 0) {
            if (mText instanceof Spannable) {
                Selection.setSelection((Spannable) mText,
                                       Math.max(0, Math.min(start, len)),
                                       Math.max(0, Math.min(end, len)));
            }
        }
    }

    @android.view.RemotableViewMethod
    public final void setText(@StringRes int resid) {
        setText(getContext().getResources().getText(resid));
    }

    public final void setText(@StringRes int resid, BufferType type) {
        setText(getContext().getResources().getText(resid), type);
    }

    /**
     * Sets the text to be displayed when the text of the TextView is empty.
     * Null means to use the normal empty text. The hint does not currently
     * participate in determining the size of the view.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    @android.view.RemotableViewMethod
    public final void setHint(CharSequence hint) {
        mHint = TextUtils.stringOrSpannedString(hint);

        if (mLayout != null) {
            checkForRelayout();
        }

        if (mText.length() == 0) {
            invalidate();
        }

        // Invalidate display list if hint is currently used
        if (mEditor != null && mText.length() == 0 && mHint != null) {
            mEditor.invalidateTextDisplayList();
        }
    }

    /**
     * Sets the text to be displayed when the text of the TextView is empty,
     * from a resource.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    @android.view.RemotableViewMethod
    public final void setHint(@StringRes int resid) {
        setHint(getContext().getResources().getText(resid));
    }

    /**
     * Returns the hint that is displayed when the text of the TextView
     * is empty.
     *
     * @attr ref android.R.styleable#TextView_hint
     */
    @ViewDebug.CapturedViewProperty
    public CharSequence getHint() {
        return mHint;
    }

    boolean isSingleLine() {
        return mSingleLine;
    }

    private static boolean isMultilineInputType(int type) {
        return (type & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE)) ==
            (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE);
    }

    /**
     * Removes the suggestion spans.
     */
    CharSequence removeSuggestionSpans(CharSequence text) {
       if (text instanceof Spanned) {
           Spannable spannable;
           if (text instanceof Spannable) {
               spannable = (Spannable) text;
           } else {
               spannable = new SpannableString(text);
               text = spannable;
           }

           SuggestionSpan[] spans = spannable.getSpans(0, text.length(), SuggestionSpan.class);
           for (int i = 0; i < spans.length; i++) {
               spannable.removeSpan(spans[i]);
           }
       }
       return text;
    }

    /**
     * Set the type of the content with a constant as defined for {@link EditorInfo#inputType}. This
     * will take care of changing the key listener, by calling {@link #setKeyListener(KeyListener)},
     * to match the given content type.  If the given content type is {@link EditorInfo#TYPE_NULL}
     * then a soft keyboard will not be displayed for this text view.
     *
     * Note that the maximum number of displayed lines (see {@link #setMaxLines(int)}) will be
     * modified if you change the {@link EditorInfo#TYPE_TEXT_FLAG_MULTI_LINE} flag of the input
     * type.
     *
     * @see #getInputType()
     * @see #setRawInputType(int)
     * @see android.text.InputType
     * @attr ref android.R.styleable#TextView_inputType
     */
    public void setInputType(int type) {
        final boolean wasPassword = isPasswordInputType(getInputType());
        final boolean wasVisiblePassword = isVisiblePasswordInputType(getInputType());
        setInputType(type, false);
        final boolean isPassword = isPasswordInputType(type);
        final boolean isVisiblePassword = isVisiblePasswordInputType(type);
        boolean forceUpdate = false;
        if (isPassword) {
            setTransformationMethod(PasswordTransformationMethod.getInstance());
            setTypefaceFromAttrs(null /* fontFamily */, MONOSPACE, 0);
        } else if (isVisiblePassword) {
            if (mTransformation == PasswordTransformationMethod.getInstance()) {
                forceUpdate = true;
            }
            setTypefaceFromAttrs(null /* fontFamily */, MONOSPACE, 0);
        } else if (wasPassword || wasVisiblePassword) {
            // not in password mode, clean up typeface and transformation
            setTypefaceFromAttrs(null /* fontFamily */, -1, -1);
            if (mTransformation == PasswordTransformationMethod.getInstance()) {
                forceUpdate = true;
            }
        }

        boolean singleLine = !isMultilineInputType(type);

        // We need to update the single line mode if it has changed or we
        // were previously in password mode.
        if (mSingleLine != singleLine || forceUpdate) {
            // Change single line mode, but only change the transformation if
            // we are not in password mode.
            applySingleLine(singleLine, !isPassword, true);
        }

        if (!isSuggestionsEnabled()) {
            mText = removeSuggestionSpans(mText);
        }

        InputMethodManager imm = InputMethodManager.peekInstance();
        if (imm != null) imm.restartInput(this);
    }

    /**
     * It would be better to rely on the input type for everything. A password inputType should have
     * a password transformation. We should hence use isPasswordInputType instead of this method.
     *
     * We should:
     * - Call setInputType in setKeyListener instead of changing the input type directly (which
     * would install the correct transformation).
     * - Refuse the installation of a non-password transformation in setTransformation if the input
     * type is password.
     *
     * However, this is like this for legacy reasons and we cannot break existing apps. This method
     * is useful since it matches what the user can see (obfuscated text or not).
     *
     * @return true if the current transformation method is of the password type.
     */
    boolean hasPasswordTransformationMethod() {
        return mTransformation instanceof PasswordTransformationMethod;
    }

    private static boolean isPasswordInputType(int inputType) {
        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        return variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_PASSWORD)
                || variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                || variation
                == (EditorInfo.TYPE_CLASS_NUMBER | EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private static boolean isVisiblePasswordInputType(int inputType) {
        final int variation =
                inputType & (EditorInfo.TYPE_MASK_CLASS | EditorInfo.TYPE_MASK_VARIATION);
        return variation
                == (EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
    }

    /**
     * Directly change the content type integer of the text view, without
     * modifying any other state.
     * @see #setInputType(int)
     * @see android.text.InputType
     * @attr ref android.R.styleable#TextView_inputType
     */
    public void setRawInputType(int type) {
        if (type == InputType.TYPE_NULL && mEditor == null) return; //TYPE_NULL is the default value
        createEditorIfNeeded();
        mEditor.mInputType = type;
    }

    private void setInputType(int type, boolean direct) {
        final int cls = type & EditorInfo.TYPE_MASK_CLASS;
        KeyListener input;
        if (cls == EditorInfo.TYPE_CLASS_TEXT) {
            boolean autotext = (type & EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT) != 0;
            TextKeyListener.Capitalize cap;
            if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
                cap = TextKeyListener.Capitalize.CHARACTERS;
            } else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
                cap = TextKeyListener.Capitalize.WORDS;
            } else if ((type & EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
                cap = TextKeyListener.Capitalize.SENTENCES;
            } else {
                cap = TextKeyListener.Capitalize.NONE;
            }
            input = TextKeyListener.getInstance(autotext, cap);
        } else if (cls == EditorInfo.TYPE_CLASS_NUMBER) {
            input = DigitsKeyListener.getInstance(
                    (type & EditorInfo.TYPE_NUMBER_FLAG_SIGNED) != 0,
                    (type & EditorInfo.TYPE_NUMBER_FLAG_DECIMAL) != 0);
        } else if (cls == EditorInfo.TYPE_CLASS_DATETIME) {
            switch (type & EditorInfo.TYPE_MASK_VARIATION) {
                case EditorInfo.TYPE_DATETIME_VARIATION_DATE:
                    input = DateKeyListener.getInstance();
                    break;
                case EditorInfo.TYPE_DATETIME_VARIATION_TIME:
                    input = TimeKeyListener.getInstance();
                    break;
                default:
                    input = DateTimeKeyListener.getInstance();
                    break;
            }
        } else if (cls == EditorInfo.TYPE_CLASS_PHONE) {
            input = DialerKeyListener.getInstance();
        } else {
            input = TextKeyListener.getInstance();
        }
        setRawInputType(type);
        if (direct) {
            createEditorIfNeeded();
            mEditor.mKeyListener = input;
        } else {
            setKeyListenerOnly(input);
        }
    }

    /**
     * Get the type of the editable content.
     *
     * @see #setInputType(int)
     * @see android.text.InputType
     */
    public int getInputType() {
        return mEditor == null ? EditorInfo.TYPE_NULL : mEditor.mInputType;
    }

    /**
     * Change the editor type integer associated with the text view, which
     * will be reported to an IME with {@link EditorInfo#imeOptions} when it
     * has focus.
     * @see #getImeOptions
     * @see android.view.inputmethod.EditorInfo
     * @attr ref android.R.styleable#TextView_imeOptions
     */
    public void setImeOptions(int imeOptions) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeOptions = imeOptions;
    }

    /**
     * Get the type of the IME editor.
     *
     * @see #setImeOptions(int)
     * @see android.view.inputmethod.EditorInfo
     */
    public int getImeOptions() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.imeOptions : EditorInfo.IME_NULL;
    }

    /**
     * Change the custom IME action associated with the text view, which
     * will be reported to an IME with {@link EditorInfo#actionLabel}
     * and {@link EditorInfo#actionId} when it has focus.
     * @see #getImeActionLabel
     * @see #getImeActionId
     * @see android.view.inputmethod.EditorInfo
     * @attr ref android.R.styleable#TextView_imeActionLabel
     * @attr ref android.R.styleable#TextView_imeActionId
     */
    public void setImeActionLabel(CharSequence label, int actionId) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeActionLabel = label;
        mEditor.mInputContentType.imeActionId = actionId;
    }

    /**
     * Get the IME action label previous set with {@link #setImeActionLabel}.
     *
     * @see #setImeActionLabel
     * @see android.view.inputmethod.EditorInfo
     */
    public CharSequence getImeActionLabel() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.imeActionLabel : null;
    }

    /**
     * Get the IME action ID previous set with {@link #setImeActionLabel}.
     *
     * @see #setImeActionLabel
     * @see android.view.inputmethod.EditorInfo
     */
    public int getImeActionId() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.imeActionId : 0;
    }

    /**
     * Set a special listener to be called when an action is performed
     * on the text view.  This will be called when the enter key is pressed,
     * or when an action supplied to the IME is selected by the user.  Setting
     * this means that the normal hard key event will not insert a newline
     * into the text view, even if it is multi-line; holding down the ALT
     * modifier will, however, allow the user to insert a newline character.
     */
    public void setOnEditorActionListener(OnEditorActionListener l) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.onEditorActionListener = l;
    }

    /**
     * Called when an attached input method calls
     * {@link InputConnection#performEditorAction(int)
     * InputConnection.performEditorAction()}
     * for this text view.  The default implementation will call your action
     * listener supplied to {@link #setOnEditorActionListener}, or perform
     * a standard operation for {@link EditorInfo#IME_ACTION_NEXT
     * EditorInfo.IME_ACTION_NEXT}, {@link EditorInfo#IME_ACTION_PREVIOUS
     * EditorInfo.IME_ACTION_PREVIOUS}, or {@link EditorInfo#IME_ACTION_DONE
     * EditorInfo.IME_ACTION_DONE}.
     *
     * <p>For backwards compatibility, if no IME options have been set and the
     * text view would not normally advance focus on enter, then
     * the NEXT and DONE actions received here will be turned into an enter
     * key down/up pair to go through the normal key handling.
     *
     * @param actionCode The code of the action being performed.
     *
     * @see #setOnEditorActionListener
     */
    public void onEditorAction(int actionCode) {
        final Editor.InputContentType ict = mEditor == null ? null : mEditor.mInputContentType;
        if (ict != null) {
            if (ict.onEditorActionListener != null) {
                if (ict.onEditorActionListener.onEditorAction(this,
                        actionCode, null)) {
                    return;
                }
            }

            // This is the handling for some default action.
            // Note that for backwards compatibility we don't do this
            // default handling if explicit ime options have not been given,
            // instead turning this into the normal enter key codes that an
            // app may be expecting.
            if (actionCode == EditorInfo.IME_ACTION_NEXT) {
                View v = focusSearch(FOCUS_FORWARD);
                if (v != null) {
                    if (!v.requestFocus(FOCUS_FORWARD)) {
                        throw new IllegalStateException("focus search returned a view " +
                                "that wasn't able to take focus!");
                    }
                }
                return;

            } else if (actionCode == EditorInfo.IME_ACTION_PREVIOUS) {
                View v = focusSearch(FOCUS_BACKWARD);
                if (v != null) {
                    if (!v.requestFocus(FOCUS_BACKWARD)) {
                        throw new IllegalStateException("focus search returned a view " +
                                "that wasn't able to take focus!");
                    }
                }
                return;

            } else if (actionCode == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = InputMethodManager.peekInstance();
                if (imm != null && imm.isActive(this)) {
                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
                }
                return;
            }
        }

        ViewRootImpl viewRootImpl = getViewRootImpl();
        if (viewRootImpl != null) {
            long eventTime = SystemClock.uptimeMillis();
            viewRootImpl.dispatchKeyFromIme(
                    new KeyEvent(eventTime, eventTime,
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE
                    | KeyEvent.FLAG_EDITOR_ACTION));
            viewRootImpl.dispatchKeyFromIme(
                    new KeyEvent(SystemClock.uptimeMillis(), eventTime,
                    KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE
                    | KeyEvent.FLAG_EDITOR_ACTION));
        }
    }

    /**
     * Set the private content type of the text, which is the
     * {@link EditorInfo#privateImeOptions EditorInfo.privateImeOptions}
     * field that will be filled in when creating an input connection.
     *
     * @see #getPrivateImeOptions()
     * @see EditorInfo#privateImeOptions
     * @attr ref android.R.styleable#TextView_privateImeOptions
     */
    public void setPrivateImeOptions(String type) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.privateImeOptions = type;
    }

    /**
     * Get the private type of the content.
     *
     * @see #setPrivateImeOptions(String)
     * @see EditorInfo#privateImeOptions
     */
    public String getPrivateImeOptions() {
        return mEditor != null && mEditor.mInputContentType != null
                ? mEditor.mInputContentType.privateImeOptions : null;
    }

    /**
     * Set the extra input data of the text, which is the
     * {@link EditorInfo#extras TextBoxAttribute.extras}
     * Bundle that will be filled in when creating an input connection.  The
     * given integer is the resource ID of an XML resource holding an
     * {@link android.R.styleable#InputExtras &lt;input-extras&gt;} XML tree.
     *
     * @see #getInputExtras(boolean)
     * @see EditorInfo#extras
     * @attr ref android.R.styleable#TextView_editorExtras
     */
    public void setInputExtras(@XmlRes int xmlResId) throws XmlPullParserException, IOException {
        createEditorIfNeeded();
        XmlResourceParser parser = getResources().getXml(xmlResId);
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.extras = new Bundle();
        getResources().parseBundleExtras(parser, mEditor.mInputContentType.extras);
    }

    /**
     * Retrieve the input extras currently associated with the text view, which
     * can be viewed as well as modified.
     *
     * @param create If true, the extras will be created if they don't already
     * exist.  Otherwise, null will be returned if none have been created.
     * @see #setInputExtras(int)
     * @see EditorInfo#extras
     * @attr ref android.R.styleable#TextView_editorExtras
     */
    public Bundle getInputExtras(boolean create) {
        if (mEditor == null && !create) return null;
        createEditorIfNeeded();
        if (mEditor.mInputContentType == null) {
            if (!create) return null;
            mEditor.createInputContentTypeIfNeeded();
        }
        if (mEditor.mInputContentType.extras == null) {
            if (!create) return null;
            mEditor.mInputContentType.extras = new Bundle();
        }
        return mEditor.mInputContentType.extras;
    }

    /**
     * Change "hint" locales associated with the text view, which will be reported to an IME with
     * {@link EditorInfo#hintLocales} when it has focus.
     *
     * <p><strong>Note:</strong> If you want new "hint" to take effect immediately you need to
     * call {@link InputMethodManager#restartInput(View)}.</p>
     * @param hintLocales List of the languages that the user is supposed to switch to no matter
     * what input method subtype is currently used. Set {@code null} to clear the current "hint".
     * @see #getImeHIntLocales()
     * @see android.view.inputmethod.EditorInfo#hintLocales
     */
    public void setImeHintLocales(@Nullable LocaleList hintLocales) {
        createEditorIfNeeded();
        mEditor.createInputContentTypeIfNeeded();
        mEditor.mInputContentType.imeHintLocales = hintLocales;
    }

    /**
     * @return The current languages list "hint". {@code null} when no "hint" is available.
     * @see #setImeHintLocales(LocaleList)
     * @see android.view.inputmethod.EditorInfo#hintLocales
     */
    @Nullable
    public LocaleList getImeHintLocales() {
        if (mEditor == null) { return null; }
        if (mEditor.mInputContentType == null) { return null; }
        return mEditor.mInputContentType.imeHintLocales;
    }

    /**
     * Returns the error message that was set to be displayed with
     * {@link #setError}, or <code>null</code> if no error was set
     * or if it the error was cleared by the widget after user input.
     */
    public CharSequence getError() {
        return mEditor == null ? null : mEditor.mError;
    }

    /**
     * Sets the right-hand compound drawable of the TextView to the "error"
     * icon and sets an error message that will be displayed in a popup when
     * the TextView has focus.  The icon and error message will be reset to
     * null when any key events cause changes to the TextView's text.  If the
     * <code>error</code> is <code>null</code>, the error message and icon
     * will be cleared.
     */
    @android.view.RemotableViewMethod
    public void setError(CharSequence error) {
        if (error == null) {
            setError(null, null);
        } else {
            Drawable dr = getContext().getDrawable(
                    com.android.internal.R.drawable.indicator_input_error);

            dr.setBounds(0, 0, dr.getIntrinsicWidth(), dr.getIntrinsicHeight());
            setError(error, dr);
        }
    }

    /**
     * Sets the right-hand compound drawable of the TextView to the specified
     * icon and sets an error message that will be displayed in a popup when
     * the TextView has focus.  The icon and error message will be reset to
     * null when any key events cause changes to the TextView's text.  The
     * drawable must already have had {@link Drawable#setBounds} set on it.
     * If the <code>error</code> is <code>null</code>, the error message will
     * be cleared (and you should provide a <code>null</code> icon as well).
     */
    public void setError(CharSequence error, Drawable icon) {
        createEditorIfNeeded();
        mEditor.setError(error, icon);
        notifyViewAccessibilityStateChangedIfNeeded(
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        boolean result = super.setFrame(l, t, r, b);

        if (mEditor != null) mEditor.setFrame();

        restartMarqueeIfNeeded();

        return result;
    }

    private void restartMarqueeIfNeeded() {
        if (mRestartMarquee && mEllipsize == TextUtils.TruncateAt.MARQUEE) {
            mRestartMarquee = false;
            startMarquee();
        }
    }

    /**
     * Sets the list of input filters that will be used if the buffer is
     * Editable. Has no effect otherwise.
     *
     * @attr ref android.R.styleable#TextView_maxLength
     */
    public void setFilters(InputFilter[] filters) {
        if (filters == null) {
            throw new IllegalArgumentException();
        }

        mFilters = filters;

        if (mText instanceof Editable) {
            setFilters((Editable) mText, filters);
        }
    }

    /**
     * Sets the list of input filters on the specified Editable,
     * and includes mInput in the list if it is an InputFilter.
     */
    private void setFilters(Editable e, InputFilter[] filters) {
        if (mEditor != null) {
            final boolean undoFilter = mEditor.mUndoInputFilter != null;
            final boolean keyFilter = mEditor.mKeyListener instanceof InputFilter;
            int num = 0;
            if (undoFilter) num++;
            if (keyFilter) num++;
            if (num > 0) {
                InputFilter[] nf = new InputFilter[filters.length + num];

                System.arraycopy(filters, 0, nf, 0, filters.length);
                num = 0;
                if (undoFilter) {
                    nf[filters.length] = mEditor.mUndoInputFilter;
                    num++;
                }
                if (keyFilter) {
                    nf[filters.length + num] = (InputFilter) mEditor.mKeyListener;
                }

                e.setFilters(nf);
                return;
            }
        }
        e.setFilters(filters);
    }

    /**
     * Returns the current list of input filters.
     *
     * @attr ref android.R.styleable#TextView_maxLength
     */
    public InputFilter[] getFilters() {
        return mFilters;
    }

    /////////////////////////////////////////////////////////////////////////

    private int getBoxHeight(Layout l) {
        Insets opticalInsets = isLayoutModeOptical(mParent) ? getOpticalInsets() : Insets.NONE;
        int padding = (l == mHintLayout) ?
                getCompoundPaddingTop() + getCompoundPaddingBottom() :
                getExtendedPaddingTop() + getExtendedPaddingBottom();
        return getMeasuredHeight() - padding + opticalInsets.top + opticalInsets.bottom;
    }

    int getVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.TOP) {
            int boxht = getBoxHeight(l);
            int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.BOTTOM)
                    voffset = boxht - textht;
                else // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
            }
        }
        return voffset;
    }

    private int getBottomVerticalOffset(boolean forceNormal) {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        Layout l = mLayout;
        if (!forceNormal && mText.length() == 0 && mHintLayout != null) {
            l = mHintLayout;
        }

        if (gravity != Gravity.BOTTOM) {
            int boxht = getBoxHeight(l);
            int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.TOP)
                    voffset = boxht - textht;
                else // (gravity == Gravity.CENTER_VERTICAL)
                    voffset = (boxht - textht) >> 1;
            }
        }
        return voffset;
    }

    void invalidateCursorPath() {
        if (mHighlightPathBogus) {
            invalidateCursor();
        } else {
            final int horizontalPadding = getCompoundPaddingLeft();
            final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

            if (mEditor.mCursorCount == 0) {
                synchronized (TEMP_RECTF) {
                    /*
                     * The reason for this concern about the thickness of the
                     * cursor and doing the floor/ceil on the coordinates is that
                     * some EditTexts (notably textfields in the Browser) have
                     * anti-aliased text where not all the characters are
                     * necessarily at integer-multiple locations.  This should
                     * make sure the entire cursor gets invalidated instead of
                     * sometimes missing half a pixel.
                     */
                    float thick = (float) Math.ceil(mTextPaint.getStrokeWidth());
                    if (thick < 1.0f) {
                        thick = 1.0f;
                    }

                    thick /= 2.0f;

                    // mHighlightPath is guaranteed to be non null at that point.
                    mHighlightPath.computeBounds(TEMP_RECTF, false);

                    invalidate((int) Math.floor(horizontalPadding + TEMP_RECTF.left - thick),
                            (int) Math.floor(verticalPadding + TEMP_RECTF.top - thick),
                            (int) Math.ceil(horizontalPadding + TEMP_RECTF.right + thick),
                            (int) Math.ceil(verticalPadding + TEMP_RECTF.bottom + thick));
                }
            } else {
                for (int i = 0; i < mEditor.mCursorCount; i++) {
                    Rect bounds = mEditor.mCursorDrawable[i].getBounds();
                    invalidate(bounds.left + horizontalPadding, bounds.top + verticalPadding,
                            bounds.right + horizontalPadding, bounds.bottom + verticalPadding);
                }
            }
        }
    }

    void invalidateCursor() {
        int where = getSelectionEnd();

        invalidateCursor(where, where, where);
    }

    private void invalidateCursor(int a, int b, int c) {
        if (a >= 0 || b >= 0 || c >= 0) {
            int start = Math.min(Math.min(a, b), c);
            int end = Math.max(Math.max(a, b), c);
            invalidateRegion(start, end, true /* Also invalidates blinking cursor */);
        }
    }

    /**
     * Invalidates the region of text enclosed between the start and end text offsets.
     */
    void invalidateRegion(int start, int end, boolean invalidateCursor) {
        if (mLayout == null) {
            invalidate();
        } else {
                int lineStart = mLayout.getLineForOffset(start);
                int top = mLayout.getLineTop(lineStart);

                // This is ridiculous, but the descent from the line above
                // can hang down into the line we really want to redraw,
                // so we have to invalidate part of the line above to make
                // sure everything that needs to be redrawn really is.
                // (But not the whole line above, because that would cause
                // the same problem with the descenders on the line above it!)
                if (lineStart > 0) {
                    top -= mLayout.getLineDescent(lineStart - 1);
                }

                int lineEnd;

                if (start == end)
                    lineEnd = lineStart;
                else
                    lineEnd = mLayout.getLineForOffset(end);

                int bottom = mLayout.getLineBottom(lineEnd);

                // mEditor can be null in case selection is set programmatically.
                if (invalidateCursor && mEditor != null) {
                    for (int i = 0; i < mEditor.mCursorCount; i++) {
                        Rect bounds = mEditor.mCursorDrawable[i].getBounds();
                        top = Math.min(top, bounds.top);
                        bottom = Math.max(bottom, bounds.bottom);
                    }
                }

                final int compoundPaddingLeft = getCompoundPaddingLeft();
                final int verticalPadding = getExtendedPaddingTop() + getVerticalOffset(true);

                int left, right;
                if (lineStart == lineEnd && !invalidateCursor) {
                    left = (int) mLayout.getPrimaryHorizontal(start);
                    right = (int) (mLayout.getPrimaryHorizontal(end) + 1.0);
                    left += compoundPaddingLeft;
                    right += compoundPaddingLeft;
                } else {
                    // Rectangle bounding box when the region spans several lines
                    left = compoundPaddingLeft;
                    right = getWidth() - getCompoundPaddingRight();
                }

                invalidate(mScrollX + left, verticalPadding + top,
                        mScrollX + right, verticalPadding + bottom);
        }
    }

    private void registerForPreDraw() {
        if (!mPreDrawRegistered) {
            getViewTreeObserver().addOnPreDrawListener(this);
            mPreDrawRegistered = true;
        }
    }

    private void unregisterForPreDraw() {
        getViewTreeObserver().removeOnPreDrawListener(this);
        mPreDrawRegistered = false;
        mPreDrawListenerDetached = false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean onPreDraw() {
        if (mLayout == null) {
            assumeLayout();
        }

        if (mMovement != null) {
            /* This code also provides auto-scrolling when a cursor is moved using a
             * CursorController (insertion point or selection limits).
             * For selection, ensure start or end is visible depending on controller's state.
             */
            int curs = getSelectionEnd();
            // Do not create the controller if it is not already created.
            if (mEditor != null && mEditor.mSelectionModifierCursorController != null &&
                    mEditor.mSelectionModifierCursorController.isSelectionStartDragged()) {
                curs = getSelectionStart();
            }

            /*
             * TODO: This should really only keep the end in view if
             * it already was before the text changed.  I'm not sure
             * of a good way to tell from here if it was.
             */
            if (curs < 0 && (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                curs = mText.length();
            }

            if (curs >= 0) {
                bringPointIntoView(curs);
            }
        } else {
            bringTextIntoView();
        }

        // This has to be checked here since:
        // - onFocusChanged cannot start it when focus is given to a view with selected text (after
        //   a screen rotation) since layout is not yet initialized at that point.
        if (mEditor != null && mEditor.mCreatedWithASelection) {
            mEditor.refreshTextActionMode();
            mEditor.mCreatedWithASelection = false;
        }

        unregisterForPreDraw();

        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (mEditor != null) mEditor.onAttachedToWindow();

        if (mPreDrawListenerDetached) {
            getViewTreeObserver().addOnPreDrawListener(this);
            mPreDrawListenerDetached = false;
        }
    }

    /** @hide */
    @Override
    protected void onDetachedFromWindowInternal() {
        if (mPreDrawRegistered) {
            getViewTreeObserver().removeOnPreDrawListener(this);
            mPreDrawListenerDetached = true;
        }

        resetResolvedDrawables();

        if (mEditor != null) mEditor.onDetachedFromWindow();

        super.onDetachedFromWindowInternal();
    }

    @Override
    public void onScreenStateChanged(int screenState) {
        super.onScreenStateChanged(screenState);
        if (mEditor != null) mEditor.onScreenStateChanged(screenState);
    }

    @Override
    protected boolean isPaddingOffsetRequired() {
        return mShadowRadius != 0 || mDrawables != null;
    }

    @Override
    protected int getLeftPaddingOffset() {
        return getCompoundPaddingLeft() - mPaddingLeft +
                (int) Math.min(0, mShadowDx - mShadowRadius);
    }

    @Override
    protected int getTopPaddingOffset() {
        return (int) Math.min(0, mShadowDy - mShadowRadius);
    }

    @Override
    protected int getBottomPaddingOffset() {
        return (int) Math.max(0, mShadowDy + mShadowRadius);
    }

    @Override
    protected int getRightPaddingOffset() {
        return -(getCompoundPaddingRight() - mPaddingRight) +
                (int) Math.max(0, mShadowDx + mShadowRadius);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        final boolean verified = super.verifyDrawable(who);
        if (!verified && mDrawables != null) {
            for (Drawable dr : mDrawables.mShowing) {
                if (who == dr) {
                    return true;
                }
            }
        }
        return verified;
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mDrawables != null) {
            for (Drawable dr : mDrawables.mShowing) {
                if (dr != null) {
                    dr.jumpToCurrentState();
                }
            }
        }
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        boolean handled = false;

        if (verifyDrawable(drawable)) {
            final Rect dirty = drawable.getBounds();
            int scrollX = mScrollX;
            int scrollY = mScrollY;

            // IMPORTANT: The coordinates below are based on the coordinates computed
            // for each compound drawable in onDraw(). Make sure to update each section
            // accordingly.
            final TextView.Drawables drawables = mDrawables;
            if (drawables != null) {
                if (drawable == drawables.mShowing[Drawables.LEFT]) {
                    final int compoundPaddingTop = getCompoundPaddingTop();
                    final int compoundPaddingBottom = getCompoundPaddingBottom();
                    final int vspace = mBottom - mTop - compoundPaddingBottom - compoundPaddingTop;

                    scrollX += mPaddingLeft;
                    scrollY += compoundPaddingTop + (vspace - drawables.mDrawableHeightLeft) / 2;
                    handled = true;
                } else if (drawable == drawables.mShowing[Drawables.RIGHT]) {
                    final int compoundPaddingTop = getCompoundPaddingTop();
                    final int compoundPaddingBottom = getCompoundPaddingBottom();
                    final int vspace = mBottom - mTop - compoundPaddingBottom - compoundPaddingTop;

                    scrollX += (mRight - mLeft - mPaddingRight - drawables.mDrawableSizeRight);
                    scrollY += compoundPaddingTop + (vspace - drawables.mDrawableHeightRight) / 2;
                    handled = true;
                } else if (drawable == drawables.mShowing[Drawables.TOP]) {
                    final int compoundPaddingLeft = getCompoundPaddingLeft();
                    final int compoundPaddingRight = getCompoundPaddingRight();
                    final int hspace = mRight - mLeft - compoundPaddingRight - compoundPaddingLeft;

                    scrollX += compoundPaddingLeft + (hspace - drawables.mDrawableWidthTop) / 2;
                    scrollY += mPaddingTop;
                    handled = true;
                } else if (drawable == drawables.mShowing[Drawables.BOTTOM]) {
                    final int compoundPaddingLeft = getCompoundPaddingLeft();
                    final int compoundPaddingRight = getCompoundPaddingRight();
                    final int hspace = mRight - mLeft - compoundPaddingRight - compoundPaddingLeft;

                    scrollX += compoundPaddingLeft + (hspace - drawables.mDrawableWidthBottom) / 2;
                    scrollY += (mBottom - mTop - mPaddingBottom - drawables.mDrawableSizeBottom);
                    handled = true;
                }
            }

            if (handled) {
                invalidate(dirty.left + scrollX, dirty.top + scrollY,
                        dirty.right + scrollX, dirty.bottom + scrollY);
            }
        }

        if (!handled) {
            super.invalidateDrawable(drawable);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        // horizontal fading edge causes SaveLayerAlpha, which doesn't support alpha modulation
        return ((getBackground() != null && getBackground().getCurrent() != null)
                || mText instanceof Spannable || hasSelection()
                || isHorizontalFadingEdgeEnabled());
    }

    /**
     *
     * Returns the state of the {@code textIsSelectable} flag (See
     * {@link #setTextIsSelectable setTextIsSelectable()}). Although you have to set this flag
     * to allow users to select and copy text in a non-editable TextView, the content of an
     * {@link EditText} can always be selected, independently of the value of this flag.
     * <p>
     *
     * @return True if the text displayed in this TextView can be selected by the user.
     *
     * @attr ref android.R.styleable#TextView_textIsSelectable
     */
    public boolean isTextSelectable() {
        return mEditor == null ? false : mEditor.mTextIsSelectable;
    }

    /**
     * Sets whether the content of this view is selectable by the user. The default is
     * {@code false}, meaning that the content is not selectable.
     * <p>
     * When you use a TextView to display a useful piece of information to the user (such as a
     * contact's address), make it selectable, so that the user can select and copy its
     * content. You can also use set the XML attribute
     * {@link android.R.styleable#TextView_textIsSelectable} to "true".
     * <p>
     * When you call this method to set the value of {@code textIsSelectable}, it sets
     * the flags {@code focusable}, {@code focusableInTouchMode}, {@code clickable},
     * and {@code longClickable} to the same value. These flags correspond to the attributes
     * {@link android.R.styleable#View_focusable android:focusable},
     * {@link android.R.styleable#View_focusableInTouchMode android:focusableInTouchMode},
     * {@link android.R.styleable#View_clickable android:clickable}, and
     * {@link android.R.styleable#View_longClickable android:longClickable}. To restore any of these
     * flags to a state you had set previously, call one or more of the following methods:
     * {@link #setFocusable(boolean) setFocusable()},
     * {@link #setFocusableInTouchMode(boolean) setFocusableInTouchMode()},
     * {@link #setClickable(boolean) setClickable()} or
     * {@link #setLongClickable(boolean) setLongClickable()}.
     *
     * @param selectable Whether the content of this TextView should be selectable.
     */
    public void setTextIsSelectable(boolean selectable) {
        if (!selectable && mEditor == null) return; // false is default value with no edit data

        createEditorIfNeeded();
        if (mEditor.mTextIsSelectable == selectable) return;

        mEditor.mTextIsSelectable = selectable;
        setFocusableInTouchMode(selectable);
        setFocusable(selectable);
        setClickable(selectable);
        setLongClickable(selectable);

        // mInputType should already be EditorInfo.TYPE_NULL and mInput should be null

        setMovementMethod(selectable ? ArrowKeyMovementMethod.getInstance() : null);
        setText(mText, selectable ? BufferType.SPANNABLE : BufferType.NORMAL);

        // Called by setText above, but safer in case of future code changes
        mEditor.prepareCursorControllers();
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState;

        if (mSingleLine) {
            drawableState = super.onCreateDrawableState(extraSpace);
        } else {
            drawableState = super.onCreateDrawableState(extraSpace + 1);
            mergeDrawableStates(drawableState, MULTILINE_STATE_SET);
        }

        if (isTextSelectable()) {
            // Disable pressed state, which was introduced when TextView was made clickable.
            // Prevents text color change.
            // setClickable(false) would have a similar effect, but it also disables focus changes
            // and long press actions, which are both needed by text selection.
            final int length = drawableState.length;
            for (int i = 0; i < length; i++) {
                if (drawableState[i] == R.attr.state_pressed) {
                    final int[] nonPressedState = new int[length - 1];
                    System.arraycopy(drawableState, 0, nonPressedState, 0, i);
                    System.arraycopy(drawableState, i + 1, nonPressedState, i, length - i - 1);
                    return nonPressedState;
                }
            }
        }

        return drawableState;
    }

    private Path getUpdatedHighlightPath() {
        Path highlight = null;
        Paint highlightPaint = mHighlightPaint;

        final int selStart = getSelectionStart();
        final int selEnd = getSelectionEnd();
        if (mMovement != null && (isFocused() || isPressed()) && selStart >= 0) {
            if (selStart == selEnd) {
                if (mEditor != null && mEditor.isCursorVisible() &&
                        (SystemClock.uptimeMillis() - mEditor.mShowCursor) %
                        (2 * Editor.BLINK) < Editor.BLINK) {
                    if (mHighlightPathBogus) {
                        if (mHighlightPath == null) mHighlightPath = new Path();
                        mHighlightPath.reset();
                        mLayout.getCursorPath(selStart, mHighlightPath, mText);
                        mEditor.updateCursorsPositions();
                        mHighlightPathBogus = false;
                    }

                    // XXX should pass to skin instead of drawing directly
                    highlightPaint.setColor(mCurTextColor);
                    highlightPaint.setStyle(Paint.Style.STROKE);
                    highlight = mHighlightPath;
                }
            } else {
                if (mHighlightPathBogus) {
                    if (mHighlightPath == null) mHighlightPath = new Path();
                    mHighlightPath.reset();
                    mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
                    mHighlightPathBogus = false;
                }

                // XXX should pass to skin instead of drawing directly
                highlightPaint.setColor(mHighlightColor);
                highlightPaint.setStyle(Paint.Style.FILL);

                highlight = mHighlightPath;
            }
        }
        return highlight;
    }

    /**
     * @hide
     */
    public int getHorizontalOffsetForDrawables() {
        return 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        restartMarqueeIfNeeded();

        // Draw the background for this view
        super.onDraw(canvas);

        final int compoundPaddingLeft = getCompoundPaddingLeft();
        final int compoundPaddingTop = getCompoundPaddingTop();
        final int compoundPaddingRight = getCompoundPaddingRight();
        final int compoundPaddingBottom = getCompoundPaddingBottom();
        final int scrollX = mScrollX;
        final int scrollY = mScrollY;
        final int right = mRight;
        final int left = mLeft;
        final int bottom = mBottom;
        final int top = mTop;
        final boolean isLayoutRtl = isLayoutRtl();
        final int offset = getHorizontalOffsetForDrawables();
        final int leftOffset = isLayoutRtl ? 0 : offset;
        final int rightOffset = isLayoutRtl ? offset : 0 ;

        final Drawables dr = mDrawables;
        if (dr != null) {
            /*
             * Compound, not extended, because the icon is not clipped
             * if the text height is smaller.
             */

            int vspace = bottom - top - compoundPaddingBottom - compoundPaddingTop;
            int hspace = right - left - compoundPaddingRight - compoundPaddingLeft;

            // IMPORTANT: The coordinates computed are also used in invalidateDrawable()
            // Make sure to update invalidateDrawable() when changing this code.
            if (dr.mShowing[Drawables.LEFT] != null) {
                canvas.save();
                canvas.translate(scrollX + mPaddingLeft + leftOffset,
                                 scrollY + compoundPaddingTop +
                                 (vspace - dr.mDrawableHeightLeft) / 2);
                dr.mShowing[Drawables.LEFT].draw(canvas);
                canvas.restore();
            }

            // IMPORTANT: The coordinates computed are also used in invalidateDrawable()
            // Make sure to update invalidateDrawable() when changing this code.
            if (dr.mShowing[Drawables.RIGHT] != null) {
                canvas.save();
                canvas.translate(scrollX + right - left - mPaddingRight
                        - dr.mDrawableSizeRight - rightOffset,
                         scrollY + compoundPaddingTop + (vspace - dr.mDrawableHeightRight) / 2);
                dr.mShowing[Drawables.RIGHT].draw(canvas);
                canvas.restore();
            }

            // IMPORTANT: The coordinates computed are also used in invalidateDrawable()
            // Make sure to update invalidateDrawable() when changing this code.
            if (dr.mShowing[Drawables.TOP] != null) {
                canvas.save();
                canvas.translate(scrollX + compoundPaddingLeft +
                        (hspace - dr.mDrawableWidthTop) / 2, scrollY + mPaddingTop);
                dr.mShowing[Drawables.TOP].draw(canvas);
                canvas.restore();
            }

            // IMPORTANT: The coordinates computed are also used in invalidateDrawable()
            // Make sure to update invalidateDrawable() when changing this code.
            if (dr.mShowing[Drawables.BOTTOM] != null) {
                canvas.save();
                canvas.translate(scrollX + compoundPaddingLeft +
                        (hspace - dr.mDrawableWidthBottom) / 2,
                         scrollY + bottom - top - mPaddingBottom - dr.mDrawableSizeBottom);
                dr.mShowing[Drawables.BOTTOM].draw(canvas);
                canvas.restore();
            }
        }

        int color = mCurTextColor;

        if (mLayout == null) {
            assumeLayout();
        }

        Layout layout = mLayout;

        if (mHint != null && mText.length() == 0) {
            if (mHintTextColor != null) {
                color = mCurHintTextColor;
            }

            layout = mHintLayout;
        }

        mTextPaint.setColor(color);
        mTextPaint.drawableState = getDrawableState();

        canvas.save();
        /*  Would be faster if we didn't have to do this. Can we chop the
            (displayable) text so that we don't need to do this ever?
        */

        int extendedPaddingTop = getExtendedPaddingTop();
        int extendedPaddingBottom = getExtendedPaddingBottom();

        final int vspace = mBottom - mTop - compoundPaddingBottom - compoundPaddingTop;
        final int maxScrollY = mLayout.getHeight() - vspace;

        float clipLeft = compoundPaddingLeft + scrollX;
        float clipTop = (scrollY == 0) ? 0 : extendedPaddingTop + scrollY;
        float clipRight = right - left - getCompoundPaddingRight() + scrollX;
        float clipBottom = bottom - top + scrollY -
                ((scrollY == maxScrollY) ? 0 : extendedPaddingBottom);

        if (mShadowRadius != 0) {
            clipLeft += Math.min(0, mShadowDx - mShadowRadius);
            clipRight += Math.max(0, mShadowDx + mShadowRadius);

            clipTop += Math.min(0, mShadowDy - mShadowRadius);
            clipBottom += Math.max(0, mShadowDy + mShadowRadius);
        }

        canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);

        int voffsetText = 0;
        int voffsetCursor = 0;

        // translate in by our padding
        /* shortcircuit calling getVerticaOffset() */
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffsetText = getVerticalOffset(false);
            voffsetCursor = getVerticalOffset(true);
        }
        canvas.translate(compoundPaddingLeft, extendedPaddingTop + voffsetText);

        final int layoutDirection = getLayoutDirection();
        final int absoluteGravity = Gravity.getAbsoluteGravity(mGravity, layoutDirection);
        if (isMarqueeFadeEnabled()) {
            if (!mSingleLine && getLineCount() == 1 && canMarquee() &&
                    (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) != Gravity.LEFT) {
                final int width = mRight - mLeft;
                final int padding = getCompoundPaddingLeft() + getCompoundPaddingRight();
                final float dx = mLayout.getLineRight(0) - (width - padding);
                canvas.translate(layout.getParagraphDirection(0) * dx, 0.0f);
            }

            if (mMarquee != null && mMarquee.isRunning()) {
                final float dx = -mMarquee.getScroll();
                canvas.translate(layout.getParagraphDirection(0) * dx, 0.0f);
            }
        }

        final int cursorOffsetVertical = voffsetCursor - voffsetText;

        Path highlight = getUpdatedHighlightPath();
        if (mEditor != null) {
            mEditor.onDraw(canvas, layout, highlight, mHighlightPaint, cursorOffsetVertical);
        } else {
            layout.draw(canvas, highlight, mHighlightPaint, cursorOffsetVertical);
        }

        if (mMarquee != null && mMarquee.shouldDrawGhost()) {
            final float dx = mMarquee.getGhostOffset();
            canvas.translate(layout.getParagraphDirection(0) * dx, 0.0f);
            layout.draw(canvas, highlight, mHighlightPaint, cursorOffsetVertical);
        }

        canvas.restore();
    }

    @Override
    public void getFocusedRect(Rect r) {
        if (mLayout == null) {
            super.getFocusedRect(r);
            return;
        }

        int selEnd = getSelectionEnd();
        if (selEnd < 0) {
            super.getFocusedRect(r);
            return;
        }

        int selStart = getSelectionStart();
        if (selStart < 0 || selStart >= selEnd) {
            int line = mLayout.getLineForOffset(selEnd);
            r.top = mLayout.getLineTop(line);
            r.bottom = mLayout.getLineBottom(line);
            r.left = (int) mLayout.getPrimaryHorizontal(selEnd) - 2;
            r.right = r.left + 4;
        } else {
            int lineStart = mLayout.getLineForOffset(selStart);
            int lineEnd = mLayout.getLineForOffset(selEnd);
            r.top = mLayout.getLineTop(lineStart);
            r.bottom = mLayout.getLineBottom(lineEnd);
            if (lineStart == lineEnd) {
                r.left = (int) mLayout.getPrimaryHorizontal(selStart);
                r.right = (int) mLayout.getPrimaryHorizontal(selEnd);
            } else {
                // Selection extends across multiple lines -- make the focused
                // rect cover the entire width.
                if (mHighlightPathBogus) {
                    if (mHighlightPath == null) mHighlightPath = new Path();
                    mHighlightPath.reset();
                    mLayout.getSelectionPath(selStart, selEnd, mHighlightPath);
                    mHighlightPathBogus = false;
                }
                synchronized (TEMP_RECTF) {
                    mHighlightPath.computeBounds(TEMP_RECTF, true);
                    r.left = (int)TEMP_RECTF.left-1;
                    r.right = (int)TEMP_RECTF.right+1;
                }
            }
        }

        // Adjust for padding and gravity.
        int paddingLeft = getCompoundPaddingLeft();
        int paddingTop = getExtendedPaddingTop();
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            paddingTop += getVerticalOffset(false);
        }
        r.offset(paddingLeft, paddingTop);
        int paddingBottom = getExtendedPaddingBottom();
        r.bottom += paddingBottom;
    }

    /**
     * Return the number of lines of text, or 0 if the internal Layout has not
     * been built.
     */
    public int getLineCount() {
        return mLayout != null ? mLayout.getLineCount() : 0;
    }

    /**
     * Return the baseline for the specified line (0...getLineCount() - 1)
     * If bounds is not null, return the top, left, right, bottom extents
     * of the specified line in it. If the internal Layout has not been built,
     * return 0 and set bounds to (0, 0, 0, 0)
     * @param line which line to examine (0..getLineCount() - 1)
     * @param bounds Optional. If not null, it returns the extent of the line
     * @return the Y-coordinate of the baseline
     */
    public int getLineBounds(int line, Rect bounds) {
        if (mLayout == null) {
            if (bounds != null) {
                bounds.set(0, 0, 0, 0);
            }
            return 0;
        }
        else {
            int baseline = mLayout.getLineBounds(line, bounds);

            int voffset = getExtendedPaddingTop();
            if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
                voffset += getVerticalOffset(true);
            }
            if (bounds != null) {
                bounds.offset(getCompoundPaddingLeft(), voffset);
            }
            return baseline + voffset;
        }
    }

    @Override
    public int getBaseline() {
        if (mLayout == null) {
            return super.getBaseline();
        }

        return getBaselineOffset() + mLayout.getLineBaseline(0);
    }

    int getBaselineOffset() {
        int voffset = 0;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffset = getVerticalOffset(true);
        }

        if (isLayoutModeOptical(mParent)) {
            voffset -= getOpticalInsets().top;
        }

        return getExtendedPaddingTop() + voffset;
    }

    /**
     * @hide
     */
    @Override
    protected int getFadeTop(boolean offsetRequired) {
        if (mLayout == null) return 0;

        int voffset = 0;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffset = getVerticalOffset(true);
        }

        if (offsetRequired) voffset += getTopPaddingOffset();

        return getExtendedPaddingTop() + voffset;
    }

    /**
     * @hide
     */
    @Override
    protected int getFadeHeight(boolean offsetRequired) {
        return mLayout != null ? mLayout.getHeight() : 0;
    }

    @Override
    public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
        if (mText instanceof Spannable && mLinksClickable) {
            final float x = event.getX(pointerIndex);
            final float y = event.getY(pointerIndex);
            final int offset = getOffsetForPosition(x, y);
            final ClickableSpan[] clickables = ((Spannable) mText).getSpans(offset, offset,
                    ClickableSpan.class);
            if (clickables.length > 0) {
                return PointerIcon.getSystemIcon(mContext, PointerIcon.TYPE_HAND);
            }
        }
        if (isTextSelectable() || isTextEditable()) {
            return PointerIcon.getSystemIcon(mContext, PointerIcon.TYPE_TEXT);
        }
        return super.onResolvePointerIcon(event, pointerIndex);
    }

    @Override
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        // Note: If the IME is in fullscreen mode and IMS#mExtractEditText is in text action mode,
        // InputMethodService#onKeyDown and InputMethodService#onKeyUp are responsible to call
        // InputMethodService#mExtractEditText.maybeHandleBackInTextActionMode(event).
        if (keyCode == KeyEvent.KEYCODE_BACK && handleBackInTextActionModeIfNeeded(event)) {
            return true;
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /**
     * @hide
     */
    public boolean handleBackInTextActionModeIfNeeded(KeyEvent event) {
        // Do nothing unless mEditor is in text action mode.
        if (mEditor == null || mEditor.mTextActionMode == null) {
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            KeyEvent.DispatcherState state = getKeyDispatcherState();
            if (state != null) {
                state.startTracking(event, this);
            }
            return true;
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            KeyEvent.DispatcherState state = getKeyDispatcherState();
            if (state != null) {
                state.handleUpEvent(event);
            }
            if (event.isTracking() && !event.isCanceled()) {
                stopTextActionMode();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int which = doKeyDown(keyCode, event, null);
        if (which == KEY_EVENT_NOT_HANDLED) {
            return super.onKeyDown(keyCode, event);
        }

        return true;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        KeyEvent down = KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN);
        final int which = doKeyDown(keyCode, down, event);
        if (which == KEY_EVENT_NOT_HANDLED) {
            // Go through default dispatching.
            return super.onKeyMultiple(keyCode, repeatCount, event);
        }
        if (which == KEY_EVENT_HANDLED) {
            // Consumed the whole thing.
            return true;
        }

        repeatCount--;

        // We are going to dispatch the remaining events to either the input
        // or movement method.  To do this, we will just send a repeated stream
        // of down and up events until we have done the complete repeatCount.
        // It would be nice if those interfaces had an onKeyMultiple() method,
        // but adding that is a more complicated change.
        KeyEvent up = KeyEvent.changeAction(event, KeyEvent.ACTION_UP);
        if (which == KEY_DOWN_HANDLED_BY_KEY_LISTENER) {
            // mEditor and mEditor.mInput are not null from doKeyDown
            mEditor.mKeyListener.onKeyUp(this, (Editable)mText, keyCode, up);
            while (--repeatCount > 0) {
                mEditor.mKeyListener.onKeyDown(this, (Editable)mText, keyCode, down);
                mEditor.mKeyListener.onKeyUp(this, (Editable)mText, keyCode, up);
            }
            hideErrorIfUnchanged();

        } else if (which == KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD) {
            // mMovement is not null from doKeyDown
            mMovement.onKeyUp(this, (Spannable)mText, keyCode, up);
            while (--repeatCount > 0) {
                mMovement.onKeyDown(this, (Spannable)mText, keyCode, down);
                mMovement.onKeyUp(this, (Spannable)mText, keyCode, up);
            }
        }

        return true;
    }

    /**
     * Returns true if pressing ENTER in this field advances focus instead
     * of inserting the character.  This is true mostly in single-line fields,
     * but also in mail addresses and subjects which will display on multiple
     * lines but where it doesn't make sense to insert newlines.
     */
    private boolean shouldAdvanceFocusOnEnter() {
        if (getKeyListener() == null) {
            return false;
        }

        if (mSingleLine) {
            return true;
        }

        if (mEditor != null &&
                (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
            if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if pressing TAB in this field advances focus instead
     * of inserting the character.  Insert tabs only in multi-line editors.
     */
    private boolean shouldAdvanceFocusOnTab() {
        if (getKeyListener() != null && !mSingleLine && mEditor != null &&
                (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
            if (variation == EditorInfo.TYPE_TEXT_FLAG_IME_MULTI_LINE
                    || variation == EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) {
                return false;
            }
        }
        return true;
    }

    private int doKeyDown(int keyCode, KeyEvent event, KeyEvent otherEvent) {
        if (!isEnabled()) {
            return KEY_EVENT_NOT_HANDLED;
        }

        // If this is the initial keydown, we don't want to prevent a movement away from this view.
        // While this shouldn't be necessary because any time we're preventing default movement we
        // should be restricting the focus to remain within this view, thus we'll also receive
        // the key up event, occasionally key up events will get dropped and we don't want to
        // prevent the user from traversing out of this on the next key down.
        if (event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(keyCode)) {
            mPreventDefaultMovement = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                if (event.hasNoModifiers()) {
                    // When mInputContentType is set, we know that we are
                    // running in a "modern" cupcake environment, so don't need
                    // to worry about the application trying to capture
                    // enter key events.
                    if (mEditor != null && mEditor.mInputContentType != null) {
                        // If there is an action listener, given them a
                        // chance to consume the event.
                        if (mEditor.mInputContentType.onEditorActionListener != null &&
                                mEditor.mInputContentType.onEditorActionListener.onEditorAction(
                                this, EditorInfo.IME_NULL, event)) {
                            mEditor.mInputContentType.enterDown = true;
                            // We are consuming the enter key for them.
                            return KEY_EVENT_HANDLED;
                        }
                    }

                    // If our editor should move focus when enter is pressed, or
                    // this is a generated event from an IME action button, then
                    // don't let it be inserted into the text.
                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
                            || shouldAdvanceFocusOnEnter()) {
                        if (hasOnClickListeners()) {
                            return KEY_EVENT_NOT_HANDLED;
                        }
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    if (shouldAdvanceFocusOnEnter()) {
                        return KEY_EVENT_NOT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_TAB:
                if (event.hasNoModifiers() || event.hasModifiers(KeyEvent.META_SHIFT_ON)) {
                    if (shouldAdvanceFocusOnTab()) {
                        return KEY_EVENT_NOT_HANDLED;
                    }
                }
                break;

                // Has to be done on key down (and not on key up) to correctly be intercepted.
            case KeyEvent.KEYCODE_BACK:
                if (mEditor != null && mEditor.mTextActionMode != null) {
                    stopTextActionMode();
                    return KEY_EVENT_HANDLED;
                }
                break;

            case KeyEvent.KEYCODE_CUT:
                if (event.hasNoModifiers() && canCut()) {
                    if (onTextContextMenuItem(ID_CUT)) {
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_COPY:
                if (event.hasNoModifiers() && canCopy()) {
                    if (onTextContextMenuItem(ID_COPY)) {
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;

            case KeyEvent.KEYCODE_PASTE:
                if (event.hasNoModifiers() && canPaste()) {
                    if (onTextContextMenuItem(ID_PASTE)) {
                        return KEY_EVENT_HANDLED;
                    }
                }
                break;
        }

        if (mEditor != null && mEditor.mKeyListener != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    beginBatchEdit();
                    final boolean handled = mEditor.mKeyListener.onKeyOther(this, (Editable) mText,
                            otherEvent);
                    hideErrorIfUnchanged();
                    doDown = false;
                    if (handled) {
                        return KEY_EVENT_HANDLED;
                    }
                } catch (AbstractMethodError e) {
                    // onKeyOther was added after 1.0, so if it isn't
                    // implemented we need to try to dispatch as a regular down.
                } finally {
                    endBatchEdit();
                }
            }

            if (doDown) {
                beginBatchEdit();
                final boolean handled = mEditor.mKeyListener.onKeyDown(this, (Editable) mText,
                        keyCode, event);
                endBatchEdit();
                hideErrorIfUnchanged();
                if (handled) return KEY_DOWN_HANDLED_BY_KEY_LISTENER;
            }
        }

        // bug 650865: sometimes we get a key event before a layout.
        // don't try to move around if we don't know the layout.

        if (mMovement != null && mLayout != null) {
            boolean doDown = true;
            if (otherEvent != null) {
                try {
                    boolean handled = mMovement.onKeyOther(this, (Spannable) mText,
                            otherEvent);
                    doDown = false;
                    if (handled) {
                        return KEY_EVENT_HANDLED;
                    }
                } catch (AbstractMethodError e) {
                    // onKeyOther was added after 1.0, so if it isn't
                    // implemented we need to try to dispatch as a regular down.
                }
            }
            if (doDown) {
                if (mMovement.onKeyDown(this, (Spannable)mText, keyCode, event)) {
                    if (event.getRepeatCount() == 0 && !KeyEvent.isModifierKey(keyCode)) {
                        mPreventDefaultMovement = true;
                    }
                    return KEY_DOWN_HANDLED_BY_MOVEMENT_METHOD;
                }
            }
        }

        return mPreventDefaultMovement && !KeyEvent.isModifierKey(keyCode) ?
                KEY_EVENT_HANDLED : KEY_EVENT_NOT_HANDLED;
    }

    /**
     * Resets the mErrorWasChanged flag, so that future calls to {@link #setError(CharSequence)}
     * can be recorded.
     * @hide
     */
    public void resetErrorChangedFlag() {
        /*
         * Keep track of what the error was before doing the input
         * so that if an input filter changed the error, we leave
         * that error showing.  Otherwise, we take down whatever
         * error was showing when the user types something.
         */
        if (mEditor != null) mEditor.mErrorWasChanged = false;
    }

    /**
     * @hide
     */
    public void hideErrorIfUnchanged() {
        if (mEditor != null && mEditor.mError != null && !mEditor.mErrorWasChanged) {
            setError(null, null);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!isEnabled()) {
            return super.onKeyUp(keyCode, event);
        }

        if (!KeyEvent.isModifierKey(keyCode)) {
            mPreventDefaultMovement = false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (event.hasNoModifiers()) {
                    /*
                     * If there is a click listener, just call through to
                     * super, which will invoke it.
                     *
                     * If there isn't a click listener, try to show the soft
                     * input method.  (It will also
                     * call performClick(), but that won't do anything in
                     * this case.)
                     */
                    if (!hasOnClickListeners()) {
                        if (mMovement != null && mText instanceof Editable
                                && mLayout != null && onCheckIsTextEditor()) {
                            InputMethodManager imm = InputMethodManager.peekInstance();
                            viewClicked(imm);
                            if (imm != null && getShowSoftInputOnFocus()) {
                                imm.showSoftInput(this, 0);
                            }
                        }
                    }
                }
                return super.onKeyUp(keyCode, event);

            case KeyEvent.KEYCODE_ENTER:
                if (event.hasNoModifiers()) {
                    if (mEditor != null && mEditor.mInputContentType != null
                            && mEditor.mInputContentType.onEditorActionListener != null
                            && mEditor.mInputContentType.enterDown) {
                        mEditor.mInputContentType.enterDown = false;
                        if (mEditor.mInputContentType.onEditorActionListener.onEditorAction(
                                this, EditorInfo.IME_NULL, event)) {
                            return true;
                        }
                    }

                    if ((event.getFlags() & KeyEvent.FLAG_EDITOR_ACTION) != 0
                            || shouldAdvanceFocusOnEnter()) {
                        /*
                         * If there is a click listener, just call through to
                         * super, which will invoke it.
                         *
                         * If there isn't a click listener, try to advance focus,
                         * but still call through to super, which will reset the
                         * pressed state and longpress state.  (It will also
                         * call performClick(), but that won't do anything in
                         * this case.)
                         */
                        if (!hasOnClickListeners()) {
                            View v = focusSearch(FOCUS_DOWN);

                            if (v != null) {
                                if (!v.requestFocus(FOCUS_DOWN)) {
                                    throw new IllegalStateException(
                                            "focus search returned a view " +
                                            "that wasn't able to take focus!");
                                }

                                /*
                                 * Return true because we handled the key; super
                                 * will return false because there was no click
                                 * listener.
                                 */
                                super.onKeyUp(keyCode, event);
                                return true;
                            } else if ((event.getFlags()
                                    & KeyEvent.FLAG_EDITOR_ACTION) != 0) {
                                // No target for next focus, but make sure the IME
                                // if this came from it.
                                InputMethodManager imm = InputMethodManager.peekInstance();
                                if (imm != null && imm.isActive(this)) {
                                    imm.hideSoftInputFromWindow(getWindowToken(), 0);
                                }
                            }
                        }
                    }
                    return super.onKeyUp(keyCode, event);
                }
                break;
        }

        if (mEditor != null && mEditor.mKeyListener != null)
            if (mEditor.mKeyListener.onKeyUp(this, (Editable) mText, keyCode, event))
                return true;

        if (mMovement != null && mLayout != null)
            if (mMovement.onKeyUp(this, (Spannable) mText, keyCode, event))
                return true;

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return mEditor != null && mEditor.mInputType != EditorInfo.TYPE_NULL;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (onCheckIsTextEditor() && isEnabled()) {
            mEditor.createInputMethodStateIfNeeded();
            outAttrs.inputType = getInputType();
            if (mEditor.mInputContentType != null) {
                outAttrs.imeOptions = mEditor.mInputContentType.imeOptions;
                outAttrs.privateImeOptions = mEditor.mInputContentType.privateImeOptions;
                outAttrs.actionLabel = mEditor.mInputContentType.imeActionLabel;
                outAttrs.actionId = mEditor.mInputContentType.imeActionId;
                outAttrs.extras = mEditor.mInputContentType.extras;
                outAttrs.hintLocales = mEditor.mInputContentType.imeHintLocales;
            } else {
                outAttrs.imeOptions = EditorInfo.IME_NULL;
                outAttrs.hintLocales = null;
            }
            if (focusSearch(FOCUS_DOWN) != null) {
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_NEXT;
            }
            if (focusSearch(FOCUS_UP) != null) {
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS;
            }
            if ((outAttrs.imeOptions&EditorInfo.IME_MASK_ACTION)
                    == EditorInfo.IME_ACTION_UNSPECIFIED) {
                if ((outAttrs.imeOptions&EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0) {
                    // An action has not been set, but the enter key will move to
                    // the next focus, so set the action to that.
                    outAttrs.imeOptions |= EditorInfo.IME_ACTION_NEXT;
                } else {
                    // An action has not been set, and there is no focus to move
                    // to, so let's just supply a "done" action.
                    outAttrs.imeOptions |= EditorInfo.IME_ACTION_DONE;
                }
                if (!shouldAdvanceFocusOnEnter()) {
                    outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                }
            }
            if (isMultilineInputType(outAttrs.inputType)) {
                // Multi-line text editors should always show an enter key.
                outAttrs.imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
            }
            outAttrs.hintText = mHint;
            if (mText instanceof Editable) {
                InputConnection ic = new EditableInputConnection(this);
                outAttrs.initialSelStart = getSelectionStart();
                outAttrs.initialSelEnd = getSelectionEnd();
                outAttrs.initialCapsMode = ic.getCursorCapsMode(getInputType());
                return ic;
            }
        }
        return null;
    }

    /**
     * If this TextView contains editable content, extract a portion of it
     * based on the information in <var>request</var> in to <var>outText</var>.
     * @return Returns true if the text was successfully extracted, else false.
     */
    public boolean extractText(ExtractedTextRequest request, ExtractedText outText) {
        createEditorIfNeeded();
        return mEditor.extractText(request, outText);
    }

    /**
     * This is used to remove all style-impacting spans from text before new
     * extracted text is being replaced into it, so that we don't have any
     * lingering spans applied during the replace.
     */
    static void removeParcelableSpans(Spannable spannable, int start, int end) {
        Object[] spans = spannable.getSpans(start, end, ParcelableSpan.class);
        int i = spans.length;
        while (i > 0) {
            i--;
            spannable.removeSpan(spans[i]);
        }
    }

    /**
     * Apply to this text view the given extracted text, as previously
     * returned by {@link #extractText(ExtractedTextRequest, ExtractedText)}.
     */
    public void setExtractedText(ExtractedText text) {
        Editable content = getEditableText();
        if (text.text != null) {
            if (content == null) {
                setText(text.text, TextView.BufferType.EDITABLE);
            } else {
                int start = 0;
                int end = content.length();

                if (text.partialStartOffset >= 0) {
                    final int N = content.length();
                    start = text.partialStartOffset;
                    if (start > N) start = N;
                    end = text.partialEndOffset;
                    if (end > N) end = N;
                }

                removeParcelableSpans(content, start, end);
                if (TextUtils.equals(content.subSequence(start, end), text.text)) {
                    if (text.text instanceof Spanned) {
                        // OK to copy spans only.
                        TextUtils.copySpansFrom((Spanned) text.text, 0, end - start,
                                Object.class, content, start);
                    }
                } else {
                    content.replace(start, end, text.text);
                }
            }
        }

        // Now set the selection position...  make sure it is in range, to
        // avoid crashes.  If this is a partial update, it is possible that
        // the underlying text may have changed, causing us problems here.
        // Also we just don't want to trust clients to do the right thing.
        Spannable sp = (Spannable)getText();
        final int N = sp.length();
        int start = text.selectionStart;
        if (start < 0) start = 0;
        else if (start > N) start = N;
        int end = text.selectionEnd;
        if (end < 0) end = 0;
        else if (end > N) end = N;
        Selection.setSelection(sp, start, end);

        // Finally, update the selection mode.
        if ((text.flags&ExtractedText.FLAG_SELECTING) != 0) {
            MetaKeyKeyListener.startSelecting(this, sp);
        } else {
            MetaKeyKeyListener.stopSelecting(this, sp);
        }
    }

    /**
     * @hide
     */
    public void setExtracting(ExtractedTextRequest req) {
        if (mEditor.mInputMethodState != null) {
            mEditor.mInputMethodState.mExtractedTextRequest = req;
        }
        // This would stop a possible selection mode, but no such mode is started in case
        // extracted mode will start. Some text is selected though, and will trigger an action mode
        // in the extracted view.
        mEditor.hideCursorAndSpanControllers();
        stopTextActionMode();
        if (mEditor.mSelectionModifierCursorController != null) {
            mEditor.mSelectionModifierCursorController.resetTouchOffsets();
        }
    }

    /**
     * Called by the framework in response to a text completion from
     * the current input method, provided by it calling
     * {@link InputConnection#commitCompletion
     * InputConnection.commitCompletion()}.  The default implementation does
     * nothing; text views that are supporting auto-completion should override
     * this to do their desired behavior.
     *
     * @param text The auto complete text the user has selected.
     */
    public void onCommitCompletion(CompletionInfo text) {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a text auto-correction (such as fixing a typo using a
     * a dictionnary) from the current input method, provided by it calling
     * {@link InputConnection#commitCorrection} InputConnection.commitCorrection()}. The default
     * implementation flashes the background of the corrected word to provide feedback to the user.
     *
     * @param info The auto correct info about the text that was corrected.
     */
    public void onCommitCorrection(CorrectionInfo info) {
        if (mEditor != null) mEditor.onCommitCorrection(info);
    }

    public void beginBatchEdit() {
        if (mEditor != null) mEditor.beginBatchEdit();
    }

    public void endBatchEdit() {
        if (mEditor != null) mEditor.endBatchEdit();
    }

    /**
     * Called by the framework in response to a request to begin a batch
     * of edit operations through a call to link {@link #beginBatchEdit()}.
     */
    public void onBeginBatchEdit() {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a request to end a batch
     * of edit operations through a call to link {@link #endBatchEdit}.
     */
    public void onEndBatchEdit() {
        // intentionally empty
    }

    /**
     * Called by the framework in response to a private command from the
     * current method, provided by it calling
     * {@link InputConnection#performPrivateCommand
     * InputConnection.performPrivateCommand()}.
     *
     * @param action The action name of the command.
     * @param data Any additional data for the command.  This may be null.
     * @return Return true if you handled the command, else false.
     */
    public boolean onPrivateIMECommand(String action, Bundle data) {
        return false;
    }

    private void nullLayouts() {
        if (mLayout instanceof BoringLayout && mSavedLayout == null) {
            mSavedLayout = (BoringLayout) mLayout;
        }
        if (mHintLayout instanceof BoringLayout && mSavedHintLayout == null) {
            mSavedHintLayout = (BoringLayout) mHintLayout;
        }

        mSavedMarqueeModeLayout = mLayout = mHintLayout = null;

        mBoring = mHintBoring = null;

        // Since it depends on the value of mLayout
        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    /**
     * Make a new Layout based on the already-measured size of the view,
     * on the assumption that it was measured correctly at some point.
     */
    private void assumeLayout() {
        int width = mRight - mLeft - getCompoundPaddingLeft() - getCompoundPaddingRight();

        if (width < 1) {
            width = 0;
        }

        int physicalWidth = width;

        if (mHorizontallyScrolling) {
            width = VERY_WIDE;
        }

        makeNewLayout(width, physicalWidth, UNKNOWN_BORING, UNKNOWN_BORING,
                      physicalWidth, false);
    }

    private Layout.Alignment getLayoutAlignment() {
        Layout.Alignment alignment;
        switch (getTextAlignment()) {
            case TEXT_ALIGNMENT_GRAVITY:
                switch (mGravity & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.START:
                        alignment = Layout.Alignment.ALIGN_NORMAL;
                        break;
                    case Gravity.END:
                        alignment = Layout.Alignment.ALIGN_OPPOSITE;
                        break;
                    case Gravity.LEFT:
                        alignment = Layout.Alignment.ALIGN_LEFT;
                        break;
                    case Gravity.RIGHT:
                        alignment = Layout.Alignment.ALIGN_RIGHT;
                        break;
                    case Gravity.CENTER_HORIZONTAL:
                        alignment = Layout.Alignment.ALIGN_CENTER;
                        break;
                    default:
                        alignment = Layout.Alignment.ALIGN_NORMAL;
                        break;
                }
                break;
            case TEXT_ALIGNMENT_TEXT_START:
                alignment = Layout.Alignment.ALIGN_NORMAL;
                break;
            case TEXT_ALIGNMENT_TEXT_END:
                alignment = Layout.Alignment.ALIGN_OPPOSITE;
                break;
            case TEXT_ALIGNMENT_CENTER:
                alignment = Layout.Alignment.ALIGN_CENTER;
                break;
            case TEXT_ALIGNMENT_VIEW_START:
                alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ?
                        Layout.Alignment.ALIGN_RIGHT : Layout.Alignment.ALIGN_LEFT;
                break;
            case TEXT_ALIGNMENT_VIEW_END:
                alignment = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ?
                        Layout.Alignment.ALIGN_LEFT : Layout.Alignment.ALIGN_RIGHT;
                break;
            case TEXT_ALIGNMENT_INHERIT:
                // This should never happen as we have already resolved the text alignment
                // but better safe than sorry so we just fall through
            default:
                alignment = Layout.Alignment.ALIGN_NORMAL;
                break;
        }
        return alignment;
    }

    /**
     * The width passed in is now the desired layout width,
     * not the full view width with padding.
     * {@hide}
     */
    protected void makeNewLayout(int wantWidth, int hintWidth,
                                 BoringLayout.Metrics boring,
                                 BoringLayout.Metrics hintBoring,
                                 int ellipsisWidth, boolean bringIntoView) {
        stopMarquee();

        // Update "old" cached values
        mOldMaximum = mMaximum;
        mOldMaxMode = mMaxMode;

        mHighlightPathBogus = true;

        if (wantWidth < 0) {
            wantWidth = 0;
        }
        if (hintWidth < 0) {
            hintWidth = 0;
        }

        Layout.Alignment alignment = getLayoutAlignment();
        final boolean testDirChange = mSingleLine && mLayout != null &&
            (alignment == Layout.Alignment.ALIGN_NORMAL ||
             alignment == Layout.Alignment.ALIGN_OPPOSITE);
        int oldDir = 0;
        if (testDirChange) oldDir = mLayout.getParagraphDirection(0);
        boolean shouldEllipsize = mEllipsize != null && getKeyListener() == null;
        final boolean switchEllipsize = mEllipsize == TruncateAt.MARQUEE &&
                mMarqueeFadeMode != MARQUEE_FADE_NORMAL;
        TruncateAt effectiveEllipsize = mEllipsize;
        if (mEllipsize == TruncateAt.MARQUEE &&
                mMarqueeFadeMode == MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS) {
            effectiveEllipsize = TruncateAt.END_SMALL;
        }

        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

        mLayout = makeSingleLayout(wantWidth, boring, ellipsisWidth, alignment, shouldEllipsize,
                effectiveEllipsize, effectiveEllipsize == mEllipsize);
        if (switchEllipsize) {
            TruncateAt oppositeEllipsize = effectiveEllipsize == TruncateAt.MARQUEE ?
                    TruncateAt.END : TruncateAt.MARQUEE;
            mSavedMarqueeModeLayout = makeSingleLayout(wantWidth, boring, ellipsisWidth, alignment,
                    shouldEllipsize, oppositeEllipsize, effectiveEllipsize != mEllipsize);
        }

        shouldEllipsize = mEllipsize != null;
        mHintLayout = null;

        if (mHint != null) {
            if (shouldEllipsize) hintWidth = wantWidth;

            if (hintBoring == UNKNOWN_BORING) {
                hintBoring = BoringLayout.isBoring(mHint, mTextPaint, mTextDir,
                                                   mHintBoring);
                if (hintBoring != null) {
                    mHintBoring = hintBoring;
                }
            }

            if (hintBoring != null) {
                if (hintBoring.width <= hintWidth &&
                    (!shouldEllipsize || hintBoring.width <= ellipsisWidth)) {
                    if (mSavedHintLayout != null) {
                        mHintLayout = mSavedHintLayout.
                                replaceOrMake(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
                                hintBoring, mIncludePad);
                    } else {
                        mHintLayout = BoringLayout.make(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
                                hintBoring, mIncludePad);
                    }

                    mSavedHintLayout = (BoringLayout) mHintLayout;
                } else if (shouldEllipsize && hintBoring.width <= hintWidth) {
                    if (mSavedHintLayout != null) {
                        mHintLayout = mSavedHintLayout.
                                replaceOrMake(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
                                hintBoring, mIncludePad, mEllipsize,
                                ellipsisWidth);
                    } else {
                        mHintLayout = BoringLayout.make(mHint, mTextPaint,
                                hintWidth, alignment, mSpacingMult, mSpacingAdd,
                                hintBoring, mIncludePad, mEllipsize,
                                ellipsisWidth);
                    }
                }
            }
            // TODO: code duplication with makeSingleLayout()
            if (mHintLayout == null) {
                StaticLayout.Builder builder = StaticLayout.Builder.obtain(mHint, 0,
                        mHint.length(), mTextPaint, hintWidth)
                        .setAlignment(alignment)
                        .setTextDirection(mTextDir)
                        .setLineSpacing(mSpacingAdd, mSpacingMult)
                        .setIncludePad(mIncludePad)
                        .setBreakStrategy(mBreakStrategy)
                        .setHyphenationFrequency(mHyphenationFrequency);
                if (shouldEllipsize) {
                    builder.setEllipsize(mEllipsize)
                            .setEllipsizedWidth(ellipsisWidth)
                            .setMaxLines(mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
                }
                mHintLayout = builder.build();
            }
        }

        if (bringIntoView || (testDirChange && oldDir != mLayout.getParagraphDirection(0))) {
            registerForPreDraw();
        }

        if (mEllipsize == TextUtils.TruncateAt.MARQUEE) {
            if (!compressText(ellipsisWidth)) {
                final int height = mLayoutParams.height;
                // If the size of the view does not depend on the size of the text, try to
                // start the marquee immediately
                if (height != LayoutParams.WRAP_CONTENT && height != LayoutParams.MATCH_PARENT) {
                    startMarquee();
                } else {
                    // Defer the start of the marquee until we know our width (see setFrame())
                    mRestartMarquee = true;
                }
            }
        }

        // CursorControllers need a non-null mLayout
        if (mEditor != null) mEditor.prepareCursorControllers();
    }

    /**
     * @hide
     */
    protected Layout makeSingleLayout(int wantWidth, BoringLayout.Metrics boring, int ellipsisWidth,
            Layout.Alignment alignment, boolean shouldEllipsize, TruncateAt effectiveEllipsize,
            boolean useSaved) {
        Layout result = null;
        if (mText instanceof Spannable) {
            result = new DynamicLayout(mText, mTransformed, mTextPaint, wantWidth,
                    alignment, mTextDir, mSpacingMult, mSpacingAdd, mIncludePad,
                    mBreakStrategy, mHyphenationFrequency,
                    getKeyListener() == null ? effectiveEllipsize : null, ellipsisWidth);
        } else {
            if (boring == UNKNOWN_BORING) {
                boring = BoringLayout.isBoring(mTransformed, mTextPaint, mTextDir, mBoring);
                if (boring != null) {
                    mBoring = boring;
                }
            }

            if (boring != null) {
                if (boring.width <= wantWidth &&
                        (effectiveEllipsize == null || boring.width <= ellipsisWidth)) {
                    if (useSaved && mSavedLayout != null) {
                        result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint,
                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
                                boring, mIncludePad);
                    } else {
                        result = BoringLayout.make(mTransformed, mTextPaint,
                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
                                boring, mIncludePad);
                    }

                    if (useSaved) {
                        mSavedLayout = (BoringLayout) result;
                    }
                } else if (shouldEllipsize && boring.width <= wantWidth) {
                    if (useSaved && mSavedLayout != null) {
                        result = mSavedLayout.replaceOrMake(mTransformed, mTextPaint,
                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
                                boring, mIncludePad, effectiveEllipsize,
                                ellipsisWidth);
                    } else {
                        result = BoringLayout.make(mTransformed, mTextPaint,
                                wantWidth, alignment, mSpacingMult, mSpacingAdd,
                                boring, mIncludePad, effectiveEllipsize,
                                ellipsisWidth);
                    }
                }
            }
        }
        if (result == null) {
            StaticLayout.Builder builder = StaticLayout.Builder.obtain(mTransformed,
                    0, mTransformed.length(), mTextPaint, wantWidth)
                    .setAlignment(alignment)
                    .setTextDirection(mTextDir)
                    .setLineSpacing(mSpacingAdd, mSpacingMult)
                    .setIncludePad(mIncludePad)
                    .setBreakStrategy(mBreakStrategy)
                    .setHyphenationFrequency(mHyphenationFrequency);
            if (shouldEllipsize) {
                builder.setEllipsize(effectiveEllipsize)
                        .setEllipsizedWidth(ellipsisWidth)
                        .setMaxLines(mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE);
            }
            // TODO: explore always setting maxLines
            result = builder.build();
        }
        return result;
    }

    private boolean compressText(float width) {
        if (isHardwareAccelerated()) return false;

        // Only compress the text if it hasn't been compressed by the previous pass
        if (width > 0.0f && mLayout != null && getLineCount() == 1 && !mUserSetTextScaleX &&
                mTextPaint.getTextScaleX() == 1.0f) {
            final float textWidth = mLayout.getLineWidth(0);
            final float overflow = (textWidth + 1.0f - width) / width;
            if (overflow > 0.0f && overflow <= Marquee.MARQUEE_DELTA_MAX) {
                mTextPaint.setTextScaleX(1.0f - overflow - 0.005f);
                post(new Runnable() {
                    public void run() {
                        requestLayout();
                    }
                });
                return true;
            }
        }

        return false;
    }

    private static int desired(Layout layout) {
        int n = layout.getLineCount();
        CharSequence text = layout.getText();
        float max = 0;

        // if any line was wrapped, we can't use it.
        // but it's ok for the last line not to have a newline

        for (int i = 0; i < n - 1; i++) {
            if (text.charAt(layout.getLineEnd(i) - 1) != '\n')
                return -1;
        }

        for (int i = 0; i < n; i++) {
            max = Math.max(max, layout.getLineWidth(i));
        }

        return (int) Math.ceil(max);
    }

    /**
     * Set whether the TextView includes extra top and bottom padding to make
     * room for accents that go above the normal ascent and descent.
     * The default is true.
     *
     * @see #getIncludeFontPadding()
     *
     * @attr ref android.R.styleable#TextView_includeFontPadding
     */
    public void setIncludeFontPadding(boolean includepad) {
        if (mIncludePad != includepad) {
            mIncludePad = includepad;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Gets whether the TextView includes extra top and bottom padding to make
     * room for accents that go above the normal ascent and descent.
     *
     * @see #setIncludeFontPadding(boolean)
     *
     * @attr ref android.R.styleable#TextView_includeFontPadding
     */
    public boolean getIncludeFontPadding() {
        return mIncludePad;
    }

    private static final BoringLayout.Metrics UNKNOWN_BORING = new BoringLayout.Metrics();

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        BoringLayout.Metrics boring = UNKNOWN_BORING;
        BoringLayout.Metrics hintBoring = UNKNOWN_BORING;

        if (mTextDir == null) {
            mTextDir = getTextDirectionHeuristic();
        }

        int des = -1;
        boolean fromexisting = false;

        if (widthMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            width = widthSize;
        } else {
            if (mLayout != null && mEllipsize == null) {
                des = desired(mLayout);
            }

            if (des < 0) {
                boring = BoringLayout.isBoring(mTransformed, mTextPaint, mTextDir, mBoring);
                if (boring != null) {
                    mBoring = boring;
                }
            } else {
                fromexisting = true;
            }

            if (boring == null || boring == UNKNOWN_BORING) {
                if (des < 0) {
                    des = (int) Math.ceil(Layout.getDesiredWidth(mTransformed, mTextPaint));
                }
                width = des;
            } else {
                width = boring.width;
            }

            final Drawables dr = mDrawables;
            if (dr != null) {
                width = Math.max(width, dr.mDrawableWidthTop);
                width = Math.max(width, dr.mDrawableWidthBottom);
            }

            if (mHint != null) {
                int hintDes = -1;
                int hintWidth;

                if (mHintLayout != null && mEllipsize == null) {
                    hintDes = desired(mHintLayout);
                }

                if (hintDes < 0) {
                    hintBoring = BoringLayout.isBoring(mHint, mTextPaint, mTextDir, mHintBoring);
                    if (hintBoring != null) {
                        mHintBoring = hintBoring;
                    }
                }

                if (hintBoring == null || hintBoring == UNKNOWN_BORING) {
                    if (hintDes < 0) {
                        hintDes = (int) Math.ceil(Layout.getDesiredWidth(mHint, mTextPaint));
                    }
                    hintWidth = hintDes;
                } else {
                    hintWidth = hintBoring.width;
                }

                if (hintWidth > width) {
                    width = hintWidth;
                }
            }

            width += getCompoundPaddingLeft() + getCompoundPaddingRight();

            if (mMaxWidthMode == EMS) {
                width = Math.min(width, mMaxWidth * getLineHeight());
            } else {
                width = Math.min(width, mMaxWidth);
            }

            if (mMinWidthMode == EMS) {
                width = Math.max(width, mMinWidth * getLineHeight());
            } else {
                width = Math.max(width, mMinWidth);
            }

            // Check against our minimum width
            width = Math.max(width, getSuggestedMinimumWidth());

            if (widthMode == MeasureSpec.AT_MOST) {
                width = Math.min(widthSize, width);
            }
        }

        int want = width - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int unpaddedWidth = want;

        if (mHorizontallyScrolling) want = VERY_WIDE;

        int hintWant = want;
        int hintWidth = (mHintLayout == null) ? hintWant : mHintLayout.getWidth();

        if (mLayout == null) {
            makeNewLayout(want, hintWant, boring, hintBoring,
                          width - getCompoundPaddingLeft() - getCompoundPaddingRight(), false);
        } else {
            final boolean layoutChanged = (mLayout.getWidth() != want) ||
                    (hintWidth != hintWant) ||
                    (mLayout.getEllipsizedWidth() !=
                            width - getCompoundPaddingLeft() - getCompoundPaddingRight());

            final boolean widthChanged = (mHint == null) &&
                    (mEllipsize == null) &&
                    (want > mLayout.getWidth()) &&
                    (mLayout instanceof BoringLayout || (fromexisting && des >= 0 && des <= want));

            final boolean maximumChanged = (mMaxMode != mOldMaxMode) || (mMaximum != mOldMaximum);

            if (layoutChanged || maximumChanged) {
                if (!maximumChanged && widthChanged) {
                    mLayout.increaseWidthTo(want);
                } else {
                    makeNewLayout(want, hintWant, boring, hintBoring,
                            width - getCompoundPaddingLeft() - getCompoundPaddingRight(), false);
                }
            } else {
                // Nothing has changed
            }
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            // Parent has told us how big to be. So be it.
            height = heightSize;
            mDesiredHeightAtMeasure = -1;
        } else {
            int desired = getDesiredHeight();

            height = desired;
            mDesiredHeightAtMeasure = desired;

            if (heightMode == MeasureSpec.AT_MOST) {
                height = Math.min(desired, heightSize);
            }
        }

        int unpaddedHeight = height - getCompoundPaddingTop() - getCompoundPaddingBottom();
        if (mMaxMode == LINES && mLayout.getLineCount() > mMaximum) {
            unpaddedHeight = Math.min(unpaddedHeight, mLayout.getLineTop(mMaximum));
        }

        /*
         * We didn't let makeNewLayout() register to bring the cursor into view,
         * so do it here if there is any possibility that it is needed.
         */
        if (mMovement != null ||
            mLayout.getWidth() > unpaddedWidth ||
            mLayout.getHeight() > unpaddedHeight) {
            registerForPreDraw();
        } else {
            scrollTo(0, 0);
        }

        setMeasuredDimension(width, height);
    }

    private int getDesiredHeight() {
        return Math.max(
                getDesiredHeight(mLayout, true),
                getDesiredHeight(mHintLayout, mEllipsize != null));
    }

    private int getDesiredHeight(Layout layout, boolean cap) {
        if (layout == null) {
            return 0;
        }

        int linecount = layout.getLineCount();
        int pad = getCompoundPaddingTop() + getCompoundPaddingBottom();
        int desired = layout.getLineTop(linecount);

        final Drawables dr = mDrawables;
        if (dr != null) {
            desired = Math.max(desired, dr.mDrawableHeightLeft);
            desired = Math.max(desired, dr.mDrawableHeightRight);
        }

        desired += pad;

        if (mMaxMode == LINES) {
            /*
             * Don't cap the hint to a certain number of lines.
             * (Do cap it, though, if we have a maximum pixel height.)
             */
            if (cap) {
                if (linecount > mMaximum) {
                    desired = layout.getLineTop(mMaximum);

                    if (dr != null) {
                        desired = Math.max(desired, dr.mDrawableHeightLeft);
                        desired = Math.max(desired, dr.mDrawableHeightRight);
                    }

                    desired += pad;
                    linecount = mMaximum;
                }
            }
        } else {
            desired = Math.min(desired, mMaximum);
        }

        if (mMinMode == LINES) {
            if (linecount < mMinimum) {
                desired += getLineHeight() * (mMinimum - linecount);
            }
        } else {
            desired = Math.max(desired, mMinimum);
        }

        // Check against our minimum height
        desired = Math.max(desired, getSuggestedMinimumHeight());

        return desired;
    }

    /**
     * Check whether a change to the existing text layout requires a
     * new view layout.
     */
    private void checkForResize() {
        boolean sizeChanged = false;

        if (mLayout != null) {
            // Check if our width changed
            if (mLayoutParams.width == LayoutParams.WRAP_CONTENT) {
                sizeChanged = true;
                invalidate();
            }

            // Check if our height changed
            if (mLayoutParams.height == LayoutParams.WRAP_CONTENT) {
                int desiredHeight = getDesiredHeight();

                if (desiredHeight != this.getHeight()) {
                    sizeChanged = true;
                }
            } else if (mLayoutParams.height == LayoutParams.MATCH_PARENT) {
                if (mDesiredHeightAtMeasure >= 0) {
                    int desiredHeight = getDesiredHeight();

                    if (desiredHeight != mDesiredHeightAtMeasure) {
                        sizeChanged = true;
                    }
                }
            }
        }

        if (sizeChanged) {
            requestLayout();
            // caller will have already invalidated
        }
    }

    /**
     * Check whether entirely new text requires a new view layout
     * or merely a new text layout.
     */
    private void checkForRelayout() {
        // If we have a fixed width, we can just swap in a new text layout
        // if the text height stays the same or if the view height is fixed.

        if ((mLayoutParams.width != LayoutParams.WRAP_CONTENT ||
                (mMaxWidthMode == mMinWidthMode && mMaxWidth == mMinWidth)) &&
                (mHint == null || mHintLayout != null) &&
                (mRight - mLeft - getCompoundPaddingLeft() - getCompoundPaddingRight() > 0)) {
            // Static width, so try making a new text layout.

            int oldht = mLayout.getHeight();
            int want = mLayout.getWidth();
            int hintWant = mHintLayout == null ? 0 : mHintLayout.getWidth();

            /*
             * No need to bring the text into view, since the size is not
             * changing (unless we do the requestLayout(), in which case it
             * will happen at measure).
             */
            makeNewLayout(want, hintWant, UNKNOWN_BORING, UNKNOWN_BORING,
                          mRight - mLeft - getCompoundPaddingLeft() - getCompoundPaddingRight(),
                          false);

            if (mEllipsize != TextUtils.TruncateAt.MARQUEE) {
                // In a fixed-height view, so use our new text layout.
                if (mLayoutParams.height != LayoutParams.WRAP_CONTENT &&
                    mLayoutParams.height != LayoutParams.MATCH_PARENT) {
                    invalidate();
                    return;
                }

                // Dynamic height, but height has stayed the same,
                // so use our new text layout.
                if (mLayout.getHeight() == oldht &&
                    (mHintLayout == null || mHintLayout.getHeight() == oldht)) {
                    invalidate();
                    return;
                }
            }

            // We lose: the height has changed and we have a dynamic height.
            // Request a new view layout using our new text layout.
            requestLayout();
            invalidate();
        } else {
            // Dynamic width, so we have no choice but to request a new
            // view layout with a new text layout.
            nullLayouts();
            requestLayout();
            invalidate();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mDeferScroll >= 0) {
            int curs = mDeferScroll;
            mDeferScroll = -1;
            bringPointIntoView(Math.min(curs, mText.length()));
        }
    }

    private boolean isShowingHint() {
        return TextUtils.isEmpty(mText) && !TextUtils.isEmpty(mHint);
    }

    /**
     * Returns true if anything changed.
     */
    private boolean bringTextIntoView() {
        Layout layout = isShowingHint() ? mHintLayout : mLayout;
        int line = 0;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            line = layout.getLineCount() - 1;
        }

        Layout.Alignment a = layout.getParagraphAlignment(line);
        int dir = layout.getParagraphDirection(line);
        int hspace = mRight - mLeft - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vspace = mBottom - mTop - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int ht = layout.getHeight();

        int scrollx, scrolly;

        // Convert to left, center, or right alignment.
        if (a == Layout.Alignment.ALIGN_NORMAL) {
            a = dir == Layout.DIR_LEFT_TO_RIGHT ? Layout.Alignment.ALIGN_LEFT :
                Layout.Alignment.ALIGN_RIGHT;
        } else if (a == Layout.Alignment.ALIGN_OPPOSITE){
            a = dir == Layout.DIR_LEFT_TO_RIGHT ? Layout.Alignment.ALIGN_RIGHT :
                Layout.Alignment.ALIGN_LEFT;
        }

        if (a == Layout.Alignment.ALIGN_CENTER) {
            /*
             * Keep centered if possible, or, if it is too wide to fit,
             * keep leading edge in view.
             */

            int left = (int) Math.floor(layout.getLineLeft(line));
            int right = (int) Math.ceil(layout.getLineRight(line));

            if (right - left < hspace) {
                scrollx = (right + left) / 2 - hspace / 2;
            } else {
                if (dir < 0) {
                    scrollx = right - hspace;
                } else {
                    scrollx = left;
                }
            }
        } else if (a == Layout.Alignment.ALIGN_RIGHT) {
            int right = (int) Math.ceil(layout.getLineRight(line));
            scrollx = right - hspace;
        } else { // a == Layout.Alignment.ALIGN_LEFT (will also be the default)
            scrollx = (int) Math.floor(layout.getLineLeft(line));
        }

        if (ht < vspace) {
            scrolly = 0;
        } else {
            if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                scrolly = ht - vspace;
            } else {
                scrolly = 0;
            }
        }

        if (scrollx != mScrollX || scrolly != mScrollY) {
            scrollTo(scrollx, scrolly);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Move the point, specified by the offset, into the view if it is needed.
     * This has to be called after layout. Returns true if anything changed.
     */
    public boolean bringPointIntoView(int offset) {
        if (isLayoutRequested()) {
            mDeferScroll = offset;
            return false;
        }
        boolean changed = false;

        Layout layout = isShowingHint() ? mHintLayout: mLayout;

        if (layout == null) return changed;

        int line = layout.getLineForOffset(offset);

        int grav;

        switch (layout.getParagraphAlignment(line)) {
            case ALIGN_LEFT:
                grav = 1;
                break;
            case ALIGN_RIGHT:
                grav = -1;
                break;
            case ALIGN_NORMAL:
                grav = layout.getParagraphDirection(line);
                break;
            case ALIGN_OPPOSITE:
                grav = -layout.getParagraphDirection(line);
                break;
            case ALIGN_CENTER:
            default:
                grav = 0;
                break;
        }

        // We only want to clamp the cursor to fit within the layout width
        // in left-to-right modes, because in a right to left alignment,
        // we want to scroll to keep the line-right on the screen, as other
        // lines are likely to have text flush with the right margin, which
        // we want to keep visible.
        // A better long-term solution would probably be to measure both
        // the full line and a blank-trimmed version, and, for example, use
        // the latter measurement for centering and right alignment, but for
        // the time being we only implement the cursor clamping in left to
        // right where it is most likely to be annoying.
        final boolean clamped = grav > 0;
        // FIXME: Is it okay to truncate this, or should we round?
        final int x = (int)layout.getPrimaryHorizontal(offset, clamped);
        final int top = layout.getLineTop(line);
        final int bottom = layout.getLineTop(line + 1);

        int left = (int) Math.floor(layout.getLineLeft(line));
        int right = (int) Math.ceil(layout.getLineRight(line));
        int ht = layout.getHeight();

        int hspace = mRight - mLeft - getCompoundPaddingLeft() - getCompoundPaddingRight();
        int vspace = mBottom - mTop - getExtendedPaddingTop() - getExtendedPaddingBottom();
        if (!mHorizontallyScrolling && right - left > hspace && right > x) {
            // If cursor has been clamped, make sure we don't scroll.
            right = Math.max(x, left + hspace);
        }

        int hslack = (bottom - top) / 2;
        int vslack = hslack;

        if (vslack > vspace / 4)
            vslack = vspace / 4;
        if (hslack > hspace / 4)
            hslack = hspace / 4;

        int hs = mScrollX;
        int vs = mScrollY;

        if (top - vs < vslack)
            vs = top - vslack;
        if (bottom - vs > vspace - vslack)
            vs = bottom - (vspace - vslack);
        if (ht - vs < vspace)
            vs = ht - vspace;
        if (0 - vs > 0)
            vs = 0;

        if (grav != 0) {
            if (x - hs < hslack) {
                hs = x - hslack;
            }
            if (x - hs > hspace - hslack) {
                hs = x - (hspace - hslack);
            }
        }

        if (grav < 0) {
            if (left - hs > 0)
                hs = left;
            if (right - hs < hspace)
                hs = right - hspace;
        } else if (grav > 0) {
            if (right - hs < hspace)
                hs = right - hspace;
            if (left - hs > 0)
                hs = left;
        } else /* grav == 0 */ {
            if (right - left <= hspace) {
                /*
                 * If the entire text fits, center it exactly.
                 */
                hs = left - (hspace - (right - left)) / 2;
            } else if (x > right - hslack) {
                /*
                 * If we are near the right edge, keep the right edge
                 * at the edge of the view.
                 */
                hs = right - hspace;
            } else if (x < left + hslack) {
                /*
                 * If we are near the left edge, keep the left edge
                 * at the edge of the view.
                 */
                hs = left;
            } else if (left > hs) {
                /*
                 * Is there whitespace visible at the left?  Fix it if so.
                 */
                hs = left;
            } else if (right < hs + hspace) {
                /*
                 * Is there whitespace visible at the right?  Fix it if so.
                 */
                hs = right - hspace;
            } else {
                /*
                 * Otherwise, float as needed.
                 */
                if (x - hs < hslack) {
                    hs = x - hslack;
                }
                if (x - hs > hspace - hslack) {
                    hs = x - (hspace - hslack);
                }
            }
        }

        if (hs != mScrollX || vs != mScrollY) {
            if (mScroller == null) {
                scrollTo(hs, vs);
            } else {
                long duration = AnimationUtils.currentAnimationTimeMillis() - mLastScroll;
                int dx = hs - mScrollX;
                int dy = vs - mScrollY;

                if (duration > ANIMATED_SCROLL_GAP) {
                    mScroller.startScroll(mScrollX, mScrollY, dx, dy);
                    awakenScrollBars(mScroller.getDuration());
                    invalidate();
                } else {
                    if (!mScroller.isFinished()) {
                        mScroller.abortAnimation();
                    }

                    scrollBy(dx, dy);
                }

                mLastScroll = AnimationUtils.currentAnimationTimeMillis();
            }

            changed = true;
        }

        if (isFocused()) {
            // This offsets because getInterestingRect() is in terms of viewport coordinates, but
            // requestRectangleOnScreen() is in terms of content coordinates.

            // The offsets here are to ensure the rectangle we are using is
            // within our view bounds, in case the cursor is on the far left
            // or right.  If it isn't withing the bounds, then this request
            // will be ignored.
            if (mTempRect == null) mTempRect = new Rect();
            mTempRect.set(x - 2, top, x + 2, bottom);
            getInterestingRect(mTempRect, line);
            mTempRect.offset(mScrollX, mScrollY);

            if (requestRectangleOnScreen(mTempRect)) {
                changed = true;
            }
        }

        return changed;
    }

    /**
     * Move the cursor, if needed, so that it is at an offset that is visible
     * to the user.  This will not move the cursor if it represents more than
     * one character (a selection range).  This will only work if the
     * TextView contains spannable text; otherwise it will do nothing.
     *
     * @return True if the cursor was actually moved, false otherwise.
     */
    public boolean moveCursorToVisibleOffset() {
        if (!(mText instanceof Spannable)) {
            return false;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start != end) {
            return false;
        }

        // First: make sure the line is visible on screen:

        int line = mLayout.getLineForOffset(start);

        final int top = mLayout.getLineTop(line);
        final int bottom = mLayout.getLineTop(line + 1);
        final int vspace = mBottom - mTop - getExtendedPaddingTop() - getExtendedPaddingBottom();
        int vslack = (bottom - top) / 2;
        if (vslack > vspace / 4)
            vslack = vspace / 4;
        final int vs = mScrollY;

        if (top < (vs+vslack)) {
            line = mLayout.getLineForVertical(vs+vslack+(bottom-top));
        } else if (bottom > (vspace+vs-vslack)) {
            line = mLayout.getLineForVertical(vspace+vs-vslack-(bottom-top));
        }

        // Next: make sure the character is visible on screen:

        final int hspace = mRight - mLeft - getCompoundPaddingLeft() - getCompoundPaddingRight();
        final int hs = mScrollX;
        final int leftChar = mLayout.getOffsetForHorizontal(line, hs);
        final int rightChar = mLayout.getOffsetForHorizontal(line, hspace+hs);

        // line might contain bidirectional text
        final int lowChar = leftChar < rightChar ? leftChar : rightChar;
        final int highChar = leftChar > rightChar ? leftChar : rightChar;

        int newStart = start;
        if (newStart < lowChar) {
            newStart = lowChar;
        } else if (newStart > highChar) {
            newStart = highChar;
        }

        if (newStart != start) {
            Selection.setSelection((Spannable)mText, newStart);
            return true;
        }

        return false;
    }

    @Override
    public void computeScroll() {
        if (mScroller != null) {
            if (mScroller.computeScrollOffset()) {
                mScrollX = mScroller.getCurrX();
                mScrollY = mScroller.getCurrY();
                invalidateParentCaches();
                postInvalidate();  // So we draw again
            }
        }
    }

    private void getInterestingRect(Rect r, int line) {
        convertFromViewportToContentCoordinates(r);

        // Rectangle can can be expanded on first and last line to take
        // padding into account.
        // TODO Take left/right padding into account too?
        if (line == 0) r.top -= getExtendedPaddingTop();
        if (line == mLayout.getLineCount() - 1) r.bottom += getExtendedPaddingBottom();
    }

    private void convertFromViewportToContentCoordinates(Rect r) {
        final int horizontalOffset = viewportToContentHorizontalOffset();
        r.left += horizontalOffset;
        r.right += horizontalOffset;

        final int verticalOffset = viewportToContentVerticalOffset();
        r.top += verticalOffset;
        r.bottom += verticalOffset;
    }

    int viewportToContentHorizontalOffset() {
        return getCompoundPaddingLeft() - mScrollX;
    }

    int viewportToContentVerticalOffset() {
        int offset = getExtendedPaddingTop() - mScrollY;
        if ((mGravity & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            offset += getVerticalOffset(false);
        }
        return offset;
    }

    @Override
    public void debug(int depth) {
        super.debug(depth);

        String output = debugIndent(depth);
        output += "frame={" + mLeft + ", " + mTop + ", " + mRight
                + ", " + mBottom + "} scroll={" + mScrollX + ", " + mScrollY
                + "} ";

        if (mText != null) {

            output += "mText=\"" + mText + "\" ";
            if (mLayout != null) {
                output += "mLayout width=" + mLayout.getWidth()
                        + " height=" + mLayout.getHeight();
            }
        } else {
            output += "mText=NULL";
        }
        Log.d(VIEW_LOG_TAG, output);
    }

    /**
     * Convenience for {@link Selection#getSelectionStart}.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public int getSelectionStart() {
        return Selection.getSelectionStart(getText());
    }

    /**
     * Convenience for {@link Selection#getSelectionEnd}.
     */
    @ViewDebug.ExportedProperty(category = "text")
    public int getSelectionEnd() {
        return Selection.getSelectionEnd(getText());
    }

    /**
     * Return true iff there is a selection inside this text view.
     */
    public boolean hasSelection() {
        final int selectionStart = getSelectionStart();
        final int selectionEnd = getSelectionEnd();

        return selectionStart >= 0 && selectionStart != selectionEnd;
    }

    String getSelectedText() {
        if (!hasSelection()) {
            return null;
        }

        final int start = getSelectionStart();
        final int end = getSelectionEnd();
        return String.valueOf(
                start > end ? mText.subSequence(end, start) : mText.subSequence(start, end));
    }

    /**
     * Sets the properties of this field (lines, horizontally scrolling,
     * transformation method) to be for a single-line input.
     *
     * @attr ref android.R.styleable#TextView_singleLine
     */
    public void setSingleLine() {
        setSingleLine(true);
    }

    /**
     * Sets the properties of this field to transform input to ALL CAPS
     * display. This may use a "small caps" formatting if available.
     * This setting will be ignored if this field is editable or selectable.
     *
     * This call replaces the current transformation method. Disabling this
     * will not necessarily restore the previous behavior from before this
     * was enabled.
     *
     * @see #setTransformationMethod(TransformationMethod)
     * @attr ref android.R.styleable#TextView_textAllCaps
     */
    public void setAllCaps(boolean allCaps) {
        if (allCaps) {
            setTransformationMethod(new AllCapsTransformationMethod(getContext()));
        } else {
            setTransformationMethod(null);
        }
    }

    /**
     * If true, sets the properties of this field (number of lines, horizontally scrolling,
     * transformation method) to be for a single-line input; if false, restores these to the default
     * conditions.
     *
     * Note that the default conditions are not necessarily those that were in effect prior this
     * method, and you may want to reset these properties to your custom values.
     *
     * @attr ref android.R.styleable#TextView_singleLine
     */
    @android.view.RemotableViewMethod
    public void setSingleLine(boolean singleLine) {
        // Could be used, but may break backward compatibility.
        // if (mSingleLine == singleLine) return;
        setInputTypeSingleLine(singleLine);
        applySingleLine(singleLine, true, true);
    }

    /**
     * Adds or remove the EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE on the mInputType.
     * @param singleLine
     */
    private void setInputTypeSingleLine(boolean singleLine) {
        if (mEditor != null &&
                (mEditor.mInputType & EditorInfo.TYPE_MASK_CLASS) == EditorInfo.TYPE_CLASS_TEXT) {
            if (singleLine) {
                mEditor.mInputType &= ~EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
            } else {
                mEditor.mInputType |= EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
            }
        }
    }

    private void applySingleLine(boolean singleLine, boolean applyTransformation,
            boolean changeMaxLines) {
        mSingleLine = singleLine;
        if (singleLine) {
            setLines(1);
            setHorizontallyScrolling(true);
            if (applyTransformation) {
                setTransformationMethod(SingleLineTransformationMethod.getInstance());
            }
        } else {
            if (changeMaxLines) {
                setMaxLines(Integer.MAX_VALUE);
            }
            setHorizontallyScrolling(false);
            if (applyTransformation) {
                setTransformationMethod(null);
            }
        }
    }

    /**
     * Causes words in the text that are longer than the view's width
     * to be ellipsized instead of broken in the middle.  You may also
     * want to {@link #setSingleLine} or {@link #setHorizontallyScrolling}
     * to constrain the text to a single line.  Use <code>null</code>
     * to turn off ellipsizing.
     *
     * If {@link #setMaxLines} has been used to set two or more lines,
     * only {@link android.text.TextUtils.TruncateAt#END} and
     * {@link android.text.TextUtils.TruncateAt#MARQUEE} are supported
     * (other ellipsizing types will not do anything).
     *
     * @attr ref android.R.styleable#TextView_ellipsize
     */
    public void setEllipsize(TextUtils.TruncateAt where) {
        // TruncateAt is an enum. != comparison is ok between these singleton objects.
        if (mEllipsize != where) {
            mEllipsize = where;

            if (mLayout != null) {
                nullLayouts();
                requestLayout();
                invalidate();
            }
        }
    }

    /**
     * Sets how many times to repeat the marquee animation. Only applied if the
     * TextView has marquee enabled. Set to -1 to repeat indefinitely.
     *
     * @see #getMarqueeRepeatLimit()
     *
     * @attr ref android.R.styleable#TextView_marqueeRepeatLimit
     */
    public void setMarqueeRepeatLimit(int marqueeLimit) {
        mMarqueeRepeatLimit = marqueeLimit;
    }

    /**
     * Gets the number of times the marquee animation is repeated. Only meaningful if the
     * TextView has marquee enabled.
     *
     * @return the number of times the marquee animation is repeated. -1 if the animation
     * repeats indefinitely
     *
     * @see #setMarqueeRepeatLimit(int)
     *
     * @attr ref android.R.styleable#TextView_marqueeRepeatLimit
     */
    public int getMarqueeRepeatLimit() {
        return mMarqueeRepeatLimit;
    }

    /**
     * Returns where, if anywhere, words that are longer than the view
     * is wide should be ellipsized.
     */
    @ViewDebug.ExportedProperty
    public TextUtils.TruncateAt getEllipsize() {
        return mEllipsize;
    }

    /**
     * Set the TextView so that when it takes focus, all the text is
     * selected.
     *
     * @attr ref android.R.styleable#TextView_selectAllOnFocus
     */
    @android.view.RemotableViewMethod
    public void setSelectAllOnFocus(boolean selectAllOnFocus) {
        createEditorIfNeeded();
        mEditor.mSelectAllOnFocus = selectAllOnFocus;

        if (selectAllOnFocus && !(mText instanceof Spannable)) {
            setText(mText, BufferType.SPANNABLE);
        }
    }

    /**
     * Set whether the cursor is visible. The default is true. Note that this property only
     * makes sense for editable TextView.
     *
     * @see #isCursorVisible()
     *
     * @attr ref android.R.styleable#TextView_cursorVisible
     */
    @android.view.RemotableViewMethod
    public void setCursorVisible(boolean visible) {
        if (visible && mEditor == null) return; // visible is the default value with no edit data
        createEditorIfNeeded();
        if (mEditor.mCursorVisible != visible) {
            mEditor.mCursorVisible = visible;
            invalidate();

            mEditor.makeBlink();

            // InsertionPointCursorController depends on mCursorVisible
            mEditor.prepareCursorControllers();
        }
    }

    /**
     * @return whether or not the cursor is visible (assuming this TextView is editable)
     *
     * @see #setCursorVisible(boolean)
     *
     * @attr ref android.R.styleable#TextView_cursorVisible
     */
    public boolean isCursorVisible() {
        // true is the default value
        return mEditor == null ? true : mEditor.mCursorVisible;
    }

    private boolean canMarquee() {
        int width = (mRight - mLeft - getCompoundPaddingLeft() - getCompoundPaddingRight());
        return width > 0 && (mLayout.getLineWidth(0) > width ||
                (mMarqueeFadeMode != MARQUEE_FADE_NORMAL && mSavedMarqueeModeLayout != null &&
                        mSavedMarqueeModeLayout.getLineWidth(0) > width));
    }

    private void startMarquee() {
        // Do not ellipsize EditText
        if (getKeyListener() != null) return;

        if (compressText(getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight())) {
            return;
        }

        if ((mMarquee == null || mMarquee.isStopped()) && (isFocused() || isSelected()) &&
                getLineCount() == 1 && canMarquee()) {

            if (mMarqueeFadeMode == MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS) {
                mMarqueeFadeMode = MARQUEE_FADE_SWITCH_SHOW_FADE;
                final Layout tmp = mLayout;
                mLayout = mSavedMarqueeModeLayout;
                mSavedMarqueeModeLayout = tmp;
                setHorizontalFadingEdgeEnabled(true);
                requestLayout();
                invalidate();
            }

            if (mMarquee == null) mMarquee = new Marquee(this);
            mMarquee.start(mMarqueeRepeatLimit);
        }
    }

    private void stopMarquee() {
        if (mMarquee != null && !mMarquee.isStopped()) {
            mMarquee.stop();
        }

        if (mMarqueeFadeMode == MARQUEE_FADE_SWITCH_SHOW_FADE) {
            mMarqueeFadeMode = MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS;
            final Layout tmp = mSavedMarqueeModeLayout;
            mSavedMarqueeModeLayout = mLayout;
            mLayout = tmp;
            setHorizontalFadingEdgeEnabled(false);
            requestLayout();
            invalidate();
        }
    }

    private void startStopMarquee(boolean start) {
        if (mEllipsize == TextUtils.TruncateAt.MARQUEE) {
            if (start) {
                startMarquee();
            } else {
                stopMarquee();
            }
        }
    }

    /**
     * This method is called when the text is changed, in case any subclasses
     * would like to know.
     *
     * Within <code>text</code>, the <code>lengthAfter</code> characters
     * beginning at <code>start</code> have just replaced old text that had
     * length <code>lengthBefore</code>. It is an error to attempt to make
     * changes to <code>text</code> from this callback.
     *
     * @param text The text the TextView is displaying
     * @param start The offset of the start of the range of the text that was
     * modified
     * @param lengthBefore The length of the former text that has been replaced
     * @param lengthAfter The length of the replacement modified text
     */
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        // intentionally empty, template pattern method can be overridden by subclasses
    }

    /**
     * This method is called when the selection has changed, in case any
     * subclasses would like to know.
     *
     * @param selStart The new selection start location.
     * @param selEnd The new selection end location.
     */
    protected void onSelectionChanged(int selStart, int selEnd) {
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED);
    }

    /**
     * Adds a TextWatcher to the list of those whose methods are called
     * whenever this TextView's text changes.
     * <p>
     * In 1.0, the {@link TextWatcher#afterTextChanged} method was erroneously
     * not called after {@link #setText} calls.  Now, doing {@link #setText}
     * if there are any text changed listeners forces the buffer type to
     * Editable if it would not otherwise be and does call this method.
     */
    public void addTextChangedListener(TextWatcher watcher) {
        if (mListeners == null) {
            mListeners = new ArrayList<TextWatcher>();
        }

        mListeners.add(watcher);
    }

    /**
     * Removes the specified TextWatcher from the list of those whose
     * methods are called
     * whenever this TextView's text changes.
     */
    public void removeTextChangedListener(TextWatcher watcher) {
        if (mListeners != null) {
            int i = mListeners.indexOf(watcher);

            if (i >= 0) {
                mListeners.remove(i);
            }
        }
    }

    private void sendBeforeTextChanged(CharSequence text, int start, int before, int after) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).beforeTextChanged(text, start, before, after);
            }
        }

        // The spans that are inside or intersect the modified region no longer make sense
        removeIntersectingNonAdjacentSpans(start, start + before, SpellCheckSpan.class);
        removeIntersectingNonAdjacentSpans(start, start + before, SuggestionSpan.class);
    }

    // Removes all spans that are inside or actually overlap the start..end range
    private <T> void removeIntersectingNonAdjacentSpans(int start, int end, Class<T> type) {
        if (!(mText instanceof Editable)) return;
        Editable text = (Editable) mText;

        T[] spans = text.getSpans(start, end, type);
        final int length = spans.length;
        for (int i = 0; i < length; i++) {
            final int spanStart = text.getSpanStart(spans[i]);
            final int spanEnd = text.getSpanEnd(spans[i]);
            if (spanEnd == start || spanStart == end) break;
            text.removeSpan(spans[i]);
        }
    }

    void removeAdjacentSuggestionSpans(final int pos) {
        if (!(mText instanceof Editable)) return;
        final Editable text = (Editable) mText;

        final SuggestionSpan[] spans = text.getSpans(pos, pos, SuggestionSpan.class);
        final int length = spans.length;
        for (int i = 0; i < length; i++) {
            final int spanStart = text.getSpanStart(spans[i]);
            final int spanEnd = text.getSpanEnd(spans[i]);
            if (spanEnd == pos || spanStart == pos) {
                if (SpellChecker.haveWordBoundariesChanged(text, pos, pos, spanStart, spanEnd)) {
                    text.removeSpan(spans[i]);
                }
            }
        }
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void sendOnTextChanged(CharSequence text, int start, int before, int after) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).onTextChanged(text, start, before, after);
            }
        }

        if (mEditor != null) mEditor.sendOnTextChanged(start, after);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void sendAfterTextChanged(Editable text) {
        if (mListeners != null) {
            final ArrayList<TextWatcher> list = mListeners;
            final int count = list.size();
            for (int i = 0; i < count; i++) {
                list.get(i).afterTextChanged(text);
            }
        }
        hideErrorIfUnchanged();
    }

    void updateAfterEdit() {
        invalidate();
        int curs = getSelectionStart();

        if (curs >= 0 || (mGravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
            registerForPreDraw();
        }

        checkForResize();

        if (curs >= 0) {
            mHighlightPathBogus = true;
            if (mEditor != null) mEditor.makeBlink();
            bringPointIntoView(curs);
        }
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void handleTextChanged(CharSequence buffer, int start, int before, int after) {
        sLastCutCopyOrTextChangedTime = 0;

        final Editor.InputMethodState ims = mEditor == null ? null : mEditor.mInputMethodState;
        if (ims == null || ims.mBatchEditNesting == 0) {
            updateAfterEdit();
        }
        if (ims != null) {
            ims.mContentChanged = true;
            if (ims.mChangedStart < 0) {
                ims.mChangedStart = start;
                ims.mChangedEnd = start+before;
            } else {
                ims.mChangedStart = Math.min(ims.mChangedStart, start);
                ims.mChangedEnd = Math.max(ims.mChangedEnd, start + before - ims.mChangedDelta);
            }
            ims.mChangedDelta += after-before;
        }
        resetErrorChangedFlag();
        sendOnTextChanged(buffer, start, before, after);
        onTextChanged(buffer, start, before, after);
    }

    /**
     * Not private so it can be called from an inner class without going
     * through a thunk.
     */
    void spanChange(Spanned buf, Object what, int oldStart, int newStart, int oldEnd, int newEnd) {
        // XXX Make the start and end move together if this ends up
        // spending too much time invalidating.

        boolean selChanged = false;
        int newSelStart=-1, newSelEnd=-1;

        final Editor.InputMethodState ims = mEditor == null ? null : mEditor.mInputMethodState;

        if (what == Selection.SELECTION_END) {
            selChanged = true;
            newSelEnd = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                invalidateCursor(Selection.getSelectionStart(buf), oldStart, newStart);
                checkForResize();
                registerForPreDraw();
                if (mEditor != null) mEditor.makeBlink();
            }
        }

        if (what == Selection.SELECTION_START) {
            selChanged = true;
            newSelStart = newStart;

            if (oldStart >= 0 || newStart >= 0) {
                int end = Selection.getSelectionEnd(buf);
                invalidateCursor(end, oldStart, newStart);
            }
        }

        if (selChanged) {
            mHighlightPathBogus = true;
            if (mEditor != null && !isFocused()) mEditor.mSelectionMoved = true;

            if ((buf.getSpanFlags(what)&Spanned.SPAN_INTERMEDIATE) == 0) {
                if (newSelStart < 0) {
                    newSelStart = Selection.getSelectionStart(buf);
                }
                if (newSelEnd < 0) {
                    newSelEnd = Selection.getSelectionEnd(buf);
                }

                if (mEditor != null) {
                    mEditor.refreshTextActionMode();
                    if (!hasSelection() && mEditor.mTextActionMode == null && hasTransientState()) {
                        // User generated selection has been removed.
                        setHasTransientState(false);
                    }
                }
                onSelectionChanged(newSelStart, newSelEnd);
            }
        }

        if (what instanceof UpdateAppearance || what instanceof ParagraphStyle ||
                what instanceof CharacterStyle) {
            if (ims == null || ims.mBatchEditNesting == 0) {
                invalidate();
                mHighlightPathBogus = true;
                checkForResize();
            } else {
                ims.mContentChanged = true;
            }
            if (mEditor != null) {
                if (oldStart >= 0) mEditor.invalidateTextDisplayList(mLayout, oldStart, oldEnd);
                if (newStart >= 0) mEditor.invalidateTextDisplayList(mLayout, newStart, newEnd);
                mEditor.invalidateHandlesAndActionMode();
            }
        }

        if (MetaKeyKeyListener.isMetaTracker(buf, what)) {
            mHighlightPathBogus = true;
            if (ims != null && MetaKeyKeyListener.isSelectingMetaTracker(buf, what)) {
                ims.mSelectionModeChanged = true;
            }

            if (Selection.getSelectionStart(buf) >= 0) {
                if (ims == null || ims.mBatchEditNesting == 0) {
                    invalidateCursor();
                } else {
                    ims.mCursorChanged = true;
                }
            }
        }

        if (what instanceof ParcelableSpan) {
            // If this is a span that can be sent to a remote process,
            // the current extract editor would be interested in it.
            if (ims != null && ims.mExtractedTextRequest != null) {
                if (ims.mBatchEditNesting != 0) {
                    if (oldStart >= 0) {
                        if (ims.mChangedStart > oldStart) {
                            ims.mChangedStart = oldStart;
                        }
                        if (ims.mChangedStart > oldEnd) {
                            ims.mChangedStart = oldEnd;
                        }
                    }
                    if (newStart >= 0) {
                        if (ims.mChangedStart > newStart) {
                            ims.mChangedStart = newStart;
                        }
                        if (ims.mChangedStart > newEnd) {
                            ims.mChangedStart = newEnd;
                        }
                    }
                } else {
                    if (DEBUG_EXTRACT) Log.v(LOG_TAG, "Span change outside of batch: "
                            + oldStart + "-" + oldEnd + ","
                            + newStart + "-" + newEnd + " " + what);
                    ims.mContentChanged = true;
                }
            }
        }

        if (mEditor != null && mEditor.mSpellChecker != null && newStart < 0 &&
                what instanceof SpellCheckSpan) {
            mEditor.mSpellChecker.onSpellCheckSpanRemoved((SpellCheckSpan) what);
        }
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (isTemporarilyDetached()) {
            // If we are temporarily in the detach state, then do nothing.
            super.onFocusChanged(focused, direction, previouslyFocusedRect);
            return;
        }

        if (mEditor != null) mEditor.onFocusChanged(focused, direction);

        if (focused) {
            if (mText instanceof Spannable) {
                Spannable sp = (Spannable) mText;
                MetaKeyKeyListener.resetMetaState(sp);
            }
        }

        startStopMarquee(focused);

        if (mTransformation != null) {
            mTransformation.onFocusChanged(this, mText, focused, direction, previouslyFocusedRect);
        }

        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);

        if (mEditor != null) mEditor.onWindowFocusChanged(hasWindowFocus);

        startStopMarquee(hasWindowFocus);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (mEditor != null && visibility != VISIBLE) {
            mEditor.hideCursorAndSpanControllers();
            stopTextActionMode();
        }
    }

    /**
     * Use {@link BaseInputConnection#removeComposingSpans
     * BaseInputConnection.removeComposingSpans()} to remove any IME composing
     * state from this text view.
     */
    public void clearComposingText() {
        if (mText instanceof Spannable) {
            BaseInputConnection.removeComposingSpans((Spannable)mText);
        }
    }

    @Override
    public void setSelected(boolean selected) {
        boolean wasSelected = isSelected();

        super.setSelected(selected);

        if (selected != wasSelected && mEllipsize == TextUtils.TruncateAt.MARQUEE) {
            if (selected) {
                startMarquee();
            } else {
                stopMarquee();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        if (mEditor != null) {
            mEditor.onTouchEvent(event);

            if (mEditor.mSelectionModifierCursorController != null &&
                    mEditor.mSelectionModifierCursorController.isDragAcceleratorActive()) {
                return true;
            }
        }

        final boolean superResult = super.onTouchEvent(event);

        /*
         * Don't handle the release after a long press, because it will move the selection away from
         * whatever the menu action was trying to affect. If the long press should have triggered an
         * insertion action mode, we can now actually show it.
         */
        if (mEditor != null && mEditor.mDiscardNextActionUp && action == MotionEvent.ACTION_UP) {
            mEditor.mDiscardNextActionUp = false;

            if (mEditor.mIsInsertionActionModeStartPending) {
                mEditor.startInsertionActionMode();
                mEditor.mIsInsertionActionModeStartPending = false;
            }
            return superResult;
        }

        final boolean touchIsFinished = (action == MotionEvent.ACTION_UP) &&
                (mEditor == null || !mEditor.mIgnoreActionUpEvent) && isFocused();

         if ((mMovement != null || onCheckIsTextEditor()) && isEnabled()
                && mText instanceof Spannable && mLayout != null) {
            boolean handled = false;

            if (mMovement != null) {
                handled |= mMovement.onTouchEvent(this, (Spannable) mText, event);
            }

            final boolean textIsSelectable = isTextSelectable();
            if (touchIsFinished && mLinksClickable && mAutoLinkMask != 0 && textIsSelectable) {
                // The LinkMovementMethod which should handle taps on links has not been installed
                // on non editable text that support text selection.
                // We reproduce its behavior here to open links for these.
                ClickableSpan[] links = ((Spannable) mText).getSpans(getSelectionStart(),
                        getSelectionEnd(), ClickableSpan.class);

                if (links.length > 0) {
                    links[0].onClick(this);
                    handled = true;
                }
            }

            if (touchIsFinished && (isTextEditable() || textIsSelectable)) {
                // Show the IME, except when selecting in read-only text.
                final InputMethodManager imm = InputMethodManager.peekInstance();
                viewClicked(imm);
                if (!textIsSelectable && mEditor.mShowSoftInputOnFocus) {
                    handled |= imm != null && imm.showSoftInput(this, 0);
                }

                // The above condition ensures that the mEditor is not null
                mEditor.onTouchUpEvent(event);

                handled = true;
            }

            if (handled) {
                return true;
            }
        }

        return superResult;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mMovement != null && mText instanceof Spannable && mLayout != null) {
            try {
                if (mMovement.onGenericMotionEvent(this, (Spannable) mText, event)) {
                    return true;
                }
            } catch (AbstractMethodError ex) {
                // onGenericMotionEvent was added to the MovementMethod interface in API 12.
                // Ignore its absence in case third party applications implemented the
                // interface directly.
            }
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (mEditor != null) {
            mEditor.onCreateContextMenu(menu);
        }
    }

    @Override
    public boolean showContextMenu() {
        if (mEditor != null) {
            mEditor.setContextMenuAnchor(Float.NaN, Float.NaN);
        }
        return super.showContextMenu();
    }

    @Override
    public boolean showContextMenu(float x, float y) {
        if (mEditor != null) {
            mEditor.setContextMenuAnchor(x, y);
        }
        return super.showContextMenu(x, y);
    }

    /**
     * @return True iff this TextView contains a text that can be edited, or if this is
     * a selectable TextView.
     */
    boolean isTextEditable() {
        return mText instanceof Editable && onCheckIsTextEditor() && isEnabled();
    }

    /**
     * Returns true, only while processing a touch gesture, if the initial
     * touch down event caused focus to move to the text view and as a result
     * its selection changed.  Only valid while processing the touch gesture
     * of interest, in an editable text view.
     */
    public boolean didTouchFocusSelect() {
        return mEditor != null && mEditor.mTouchFocusSelected;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        if (mEditor != null) mEditor.mIgnoreActionUpEvent = true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        if (mMovement != null && mText instanceof Spannable && mLayout != null) {
            if (mMovement.onTrackballEvent(this, (Spannable) mText, event)) {
                return true;
            }
        }

        return super.onTrackballEvent(event);
    }

    public void setScroller(Scroller s) {
        mScroller = s;
    }

    @Override
    protected float getLeftFadingEdgeStrength() {
        if (isMarqueeFadeEnabled() && mMarquee != null && !mMarquee.isStopped()) {
            final Marquee marquee = mMarquee;
            if (marquee.shouldDrawLeftFade()) {
                return getHorizontalFadingEdgeStrength(marquee.getScroll(), 0.0f);
            } else {
                return 0.0f;
            }
        } else if (getLineCount() == 1) {
            final float lineLeft = getLayout().getLineLeft(0);
            if(lineLeft > mScrollX) return 0.0f;
            return getHorizontalFadingEdgeStrength(mScrollX, lineLeft);
        }
        return super.getLeftFadingEdgeStrength();
    }

    @Override
    protected float getRightFadingEdgeStrength() {
        if (isMarqueeFadeEnabled() && mMarquee != null && !mMarquee.isStopped()) {
            final Marquee marquee = mMarquee;
            return getHorizontalFadingEdgeStrength(marquee.getMaxFadeScroll(), marquee.getScroll());
        } else if (getLineCount() == 1) {
            final float rightEdge = mScrollX + (getWidth() - getCompoundPaddingLeft() -
                    getCompoundPaddingRight());
            final float lineRight = getLayout().getLineRight(0);
            if(lineRight < rightEdge) return 0.0f;
            return getHorizontalFadingEdgeStrength(rightEdge, lineRight);
        }
        return super.getRightFadingEdgeStrength();
    }

    /**
     * Calculates the fading edge strength as the ratio of the distance between two
     * horizontal positions to {@link View#getHorizontalFadingEdgeLength()}. Uses the absolute
     * value for the distance calculation.
     *
     * @param position1 A horizontal position.
     * @param position2 A horizontal position.
     * @return Fading edge strength between [0.0f, 1.0f].
     */
    @FloatRange(from=0.0, to=1.0)
    private final float getHorizontalFadingEdgeStrength(float position1, float position2) {
        final int horizontalFadingEdgeLength = getHorizontalFadingEdgeLength();
        if(horizontalFadingEdgeLength == 0) return 0.0f;
        final float diff = Math.abs(position1 - position2);
        if(diff > horizontalFadingEdgeLength) return 1.0f;
        return diff / horizontalFadingEdgeLength;
    }

    private final boolean isMarqueeFadeEnabled() {
        return mEllipsize == TextUtils.TruncateAt.MARQUEE &&
                mMarqueeFadeMode != MARQUEE_FADE_SWITCH_SHOW_ELLIPSIS;
    }

    @Override
    protected int computeHorizontalScrollRange() {
        if (mLayout != null) {
            return mSingleLine && (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) == Gravity.LEFT ?
                    (int) mLayout.getLineWidth(0) : mLayout.getWidth();
        }

        return super.computeHorizontalScrollRange();
    }

    @Override
    protected int computeVerticalScrollRange() {
        if (mLayout != null)
            return mLayout.getHeight();

        return super.computeVerticalScrollRange();
    }

    @Override
    protected int computeVerticalScrollExtent() {
        return getHeight() - getCompoundPaddingTop() - getCompoundPaddingBottom();
    }

    @Override
    public void findViewsWithText(ArrayList<View> outViews, CharSequence searched, int flags) {
        super.findViewsWithText(outViews, searched, flags);
        if (!outViews.contains(this) && (flags & FIND_VIEWS_WITH_TEXT) != 0
                && !TextUtils.isEmpty(searched) && !TextUtils.isEmpty(mText)) {
            String searchedLowerCase = searched.toString().toLowerCase();
            String textLowerCase = mText.toString().toLowerCase();
            if (textLowerCase.contains(searchedLowerCase)) {
                outViews.add(this);
            }
        }
    }

    public enum BufferType {
        NORMAL, SPANNABLE, EDITABLE,
    }

    /**
     * Returns the TextView_textColor attribute from the TypedArray, if set, or
     * the TextAppearance_textColor from the TextView_textAppearance attribute,
     * if TextView_textColor was not set directly.
     *
     * @removed
     */
    public static ColorStateList getTextColors(Context context, TypedArray attrs) {
        if (attrs == null) {
            // Preserve behavior prior to removal of this API.
            throw new NullPointerException();
        }

        // It's not safe to use this method from apps. The parameter 'attrs'
        // must have been obtained using the TextView filter array which is not
        // available to the SDK. As such, we grab a default TypedArray with the
        // right filter instead here.
        final TypedArray a = context.obtainStyledAttributes(R.styleable.TextView);
        ColorStateList colors = a.getColorStateList(R.styleable.TextView_textColor);
        if (colors == null) {
            final int ap = a.getResourceId(R.styleable.TextView_textAppearance, 0);
            if (ap != 0) {
                final TypedArray appearance = context.obtainStyledAttributes(
                        ap, R.styleable.TextAppearance);
                colors = appearance.getColorStateList(R.styleable.TextAppearance_textColor);
                appearance.recycle();
            }
        }
        a.recycle();

        return colors;
    }

    /**
     * Returns the default color from the TextView_textColor attribute from the
     * AttributeSet, if set, or the default color from the
     * TextAppearance_textColor from the TextView_textAppearance attribute, if
     * TextView_textColor was not set directly.
     *
     * @removed
     */
    public static int getTextColor(Context context, TypedArray attrs, int def) {
        final ColorStateList colors = getTextColors(context, attrs);
        if (colors == null) {
            return def;
        } else {
            return colors.getDefaultColor();
        }
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
            // Handle Ctrl-only shortcuts.
            switch (keyCode) {
            case KeyEvent.KEYCODE_A:
                if (canSelectText()) {
                    return onTextContextMenuItem(ID_SELECT_ALL);
                }
                break;
            case KeyEvent.KEYCODE_Z:
                if (canUndo()) {
                    return onTextContextMenuItem(ID_UNDO);
                }
                break;
            case KeyEvent.KEYCODE_X:
                if (canCut()) {
                    return onTextContextMenuItem(ID_CUT);
                }
                break;
            case KeyEvent.KEYCODE_C:
                if (canCopy()) {
                    return onTextContextMenuItem(ID_COPY);
                }
                break;
            case KeyEvent.KEYCODE_V:
                if (canPaste()) {
                    return onTextContextMenuItem(ID_PASTE);
                }
                break;
            }
        } else if (event.hasModifiers(KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON)) {
            // Handle Ctrl-Shift shortcuts.
            switch (keyCode) {
                case KeyEvent.KEYCODE_Z:
                    if (canRedo()) {
                        return onTextContextMenuItem(ID_REDO);
                    }
                    break;
                case KeyEvent.KEYCODE_V:
                    if (canPaste()) {
                        return onTextContextMenuItem(ID_PASTE_AS_PLAIN_TEXT);
                    }
            }
        }
        return super.onKeyShortcut(keyCode, event);
    }

    /**
     * Unlike {@link #textCanBeSelected()}, this method is based on the <i>current</i> state of the
     * TextView. {@link #textCanBeSelected()} has to be true (this is one of the conditions to have
     * a selection controller (see {@link Editor#prepareCursorControllers()}), but this is not
     * sufficient.
     */
    boolean canSelectText() {
        return mText.length() != 0 && mEditor != null && mEditor.hasSelectionController();
    }

    /**
     * Test based on the <i>intrinsic</i> charateristics of the TextView.
     * The text must be spannable and the movement method must allow for arbitary selection.
     *
     * See also {@link #canSelectText()}.
     */
    boolean textCanBeSelected() {
        // prepareCursorController() relies on this method.
        // If you change this condition, make sure prepareCursorController is called anywhere
        // the value of this condition might be changed.
        if (mMovement == null || !mMovement.canSelectArbitrarily()) return false;
        return isTextEditable() ||
                (isTextSelectable() && mText instanceof Spannable && isEnabled());
    }

    private Locale getTextServicesLocale(boolean allowNullLocale) {
        // Start fetching the text services locale asynchronously.
        updateTextServicesLocaleAsync();
        // If !allowNullLocale and there is no cached text services locale, just return the default
        // locale.
        return (mCurrentSpellCheckerLocaleCache == null && !allowNullLocale) ? Locale.getDefault()
                : mCurrentSpellCheckerLocaleCache;
    }

    /**
     * This is a temporary method. Future versions may support multi-locale text.
     * Caveat: This method may not return the latest text services locale, but this should be
     * acceptable and it's more important to make this method asynchronous.
     *
     * @return The locale that should be used for a word iterator
     * in this TextView, based on the current spell checker settings,
     * the current IME's locale, or the system default locale.
     * Please note that a word iterator in this TextView is different from another word iterator
     * used by SpellChecker.java of TextView. This method should be used for the former.
     * @hide
     */
    // TODO: Support multi-locale
    // TODO: Update the text services locale immediately after the keyboard locale is switched
    // by catching intent of keyboard switch event
    public Locale getTextServicesLocale() {
        return getTextServicesLocale(false /* allowNullLocale */);
    }

    /**
     * @return true if this TextView is specialized for showing and interacting with the extracted
     * text in a full-screen input method.
     * @hide
     */
    public boolean isInExtractedMode() {
        return false;
    }

    /**
     * This is a temporary method. Future versions may support multi-locale text.
     * Caveat: This method may not return the latest spell checker locale, but this should be
     * acceptable and it's more important to make this method asynchronous.
     *
     * @return The locale that should be used for a spell checker in this TextView,
     * based on the current spell checker settings, the current IME's locale, or the system default
     * locale.
     * @hide
     */
    public Locale getSpellCheckerLocale() {
        return getTextServicesLocale(true /* allowNullLocale */);
    }

    private void updateTextServicesLocaleAsync() {
        // AsyncTask.execute() uses a serial executor which means we don't have
        // to lock around updateTextServicesLocaleLocked() to prevent it from
        // being executed n times in parallel.
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                updateTextServicesLocaleLocked();
            }
        });
    }

    private void updateTextServicesLocaleLocked() {
        final TextServicesManager textServicesManager = (TextServicesManager)
                mContext.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
        final SpellCheckerSubtype subtype = textServicesManager.getCurrentSpellCheckerSubtype(true);
        final Locale locale;
        if (subtype != null) {
            locale = subtype.getLocaleObject();
        } else {
            locale = null;
        }
        mCurrentSpellCheckerLocaleCache = locale;
    }

    void onLocaleChanged() {
        mEditor.onLocaleChanged();
    }

    /**
     * This method is used by the ArrowKeyMovementMethod to jump from one word to the other.
     * Made available to achieve a consistent behavior.
     * @hide
     */
    public WordIterator getWordIterator() {
        if (mEditor != null) {
            return mEditor.getWordIterator();
        } else {
            return null;
        }
    }

    /** @hide */
    @Override
    public void onPopulateAccessibilityEventInternal(AccessibilityEvent event) {
        super.onPopulateAccessibilityEventInternal(event);

        final CharSequence text = getTextForAccessibility();
        if (!TextUtils.isEmpty(text)) {
            event.getText().add(text);
        }
    }

    /**
     * @return true if the user has explicitly allowed accessibility services
     * to speak passwords.
     */
    private boolean shouldSpeakPasswordsForAccessibility() {
        return (Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SPEAK_PASSWORD, 0,
                UserHandle.USER_CURRENT_OR_SELF) == 1);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return TextView.class.getName();
    }

    @Override
    public void onProvideStructure(ViewStructure structure) {
        super.onProvideStructure(structure);
        final boolean isPassword = hasPasswordTransformationMethod()
                || isPasswordInputType(getInputType());
        if (!isPassword) {
            if (mLayout == null) {
                assumeLayout();
            }
            Layout layout = mLayout;
            final int lineCount = layout.getLineCount();
            if (lineCount <= 1) {
                // Simple case: this is a single line.
                structure.setText(getText(), getSelectionStart(), getSelectionEnd());
            } else {
                // Complex case: multi-line, could be scrolled or within a scroll container
                // so some lines are not visible.
                final int[] tmpCords = new int[2];
                getLocationInWindow(tmpCords);
                final int topWindowLocation = tmpCords[1];
                View root = this;
                ViewParent viewParent = getParent();
                while (viewParent instanceof View) {
                    root = (View) viewParent;
                    viewParent = root.getParent();
                }
                final int windowHeight = root.getHeight();
                final int topLine;
                final int bottomLine;
                if (topWindowLocation >= 0) {
                    // The top of the view is fully within its window; start text at line 0.
                    topLine = getLineAtCoordinateUnclamped(0);
                    bottomLine = getLineAtCoordinateUnclamped(windowHeight-1);
                } else {
                    // The top of hte window has scrolled off the top of the window; figure out
                    // the starting line for this.
                    topLine = getLineAtCoordinateUnclamped(-topWindowLocation);
                    bottomLine = getLineAtCoordinateUnclamped(windowHeight-1-topWindowLocation);
                }
                // We want to return some contextual lines above/below the lines that are
                // actually visible.
                int expandedTopLine = topLine - (bottomLine-topLine)/2;
                if (expandedTopLine < 0) {
                    expandedTopLine = 0;
                }
                int expandedBottomLine = bottomLine + (bottomLine-topLine)/2;
                if (expandedBottomLine >= lineCount) {
                    expandedBottomLine = lineCount-1;
                }
                // Convert lines into character offsets.
                int expandedTopChar = layout.getLineStart(expandedTopLine);
                int expandedBottomChar = layout.getLineEnd(expandedBottomLine);
                // Take into account selection -- if there is a selection, we need to expand
                // the text we are returning to include that selection.
                final int selStart = getSelectionStart();
                final int selEnd = getSelectionEnd();
                if (selStart < selEnd) {
                    if (selStart < expandedTopChar) {
                        expandedTopChar = selStart;
                    }
                    if (selEnd > expandedBottomChar) {
                        expandedBottomChar = selEnd;
                    }
                }
                // Get the text and trim it to the range we are reporting.
                CharSequence text = getText();
                if (expandedTopChar > 0 || expandedBottomChar < text.length()) {
                    text = text.subSequence(expandedTopChar, expandedBottomChar);
                }
                structure.setText(text, selStart-expandedTopChar, selEnd-expandedTopChar);
                final int[] lineOffsets = new int[bottomLine-topLine+1];
                final int[] lineBaselines = new int[bottomLine-topLine+1];
                final int baselineOffset = getBaselineOffset();
                for (int i=topLine; i<=bottomLine; i++) {
                    lineOffsets[i-topLine] = layout.getLineStart(i);
                    lineBaselines[i-topLine] = layout.getLineBaseline(i) + baselineOffset;
                }
                structure.setTextLines(lineOffsets, lineBaselines);
            }

            // Extract style information that applies to the TextView as a whole.
            int style = 0;
            int typefaceStyle = getTypefaceStyle();
            if ((typefaceStyle & Typeface.BOLD) != 0) {
                style |= AssistStructure.ViewNode.TEXT_STYLE_BOLD;
            }
            if ((typefaceStyle & Typeface.ITALIC) != 0) {
                style |= AssistStructure.ViewNode.TEXT_STYLE_ITALIC;
            }

            // Global styles can also be set via TextView.setPaintFlags().
            int paintFlags = mTextPaint.getFlags();
            if ((paintFlags & Paint.FAKE_BOLD_TEXT_FLAG) != 0) {
                style |= AssistStructure.ViewNode.TEXT_STYLE_BOLD;
            }
            if ((paintFlags & Paint.UNDERLINE_TEXT_FLAG) != 0) {
                style |= AssistStructure.ViewNode.TEXT_STYLE_UNDERLINE;
            }
            if ((paintFlags & Paint.STRIKE_THRU_TEXT_FLAG) != 0) {
                style |= AssistStructure.ViewNode.TEXT_STYLE_STRIKE_THRU;
            }

            // TextView does not have its own text background color. A background is either part
            // of the View (and can be any drawable) or a BackgroundColorSpan inside the text.
            structure.setTextStyle(getTextSize(), getCurrentTextColor(),
                    AssistStructure.ViewNode.TEXT_COLOR_UNDEFINED /* bgColor */, style);
        }
        structure.setHint(getHint());
    }

    /** @hide */
    @Override
    public void onInitializeAccessibilityEventInternal(AccessibilityEvent event) {
        super.onInitializeAccessibilityEventInternal(event);

        final boolean isPassword = hasPasswordTransformationMethod();
        event.setPassword(isPassword);

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
            event.setFromIndex(Selection.getSelectionStart(mText));
            event.setToIndex(Selection.getSelectionEnd(mText));
            event.setItemCount(mText.length());
        }
    }

    /** @hide */
    @Override
    public void onInitializeAccessibilityNodeInfoInternal(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfoInternal(info);

        final boolean isPassword = hasPasswordTransformationMethod();
        info.setPassword(isPassword);
        info.setText(getTextForAccessibility());

        if (mBufferType == BufferType.EDITABLE) {
            info.setEditable(true);
            if (isEnabled()) {
                info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_TEXT);
            }
        }

        if (mEditor != null) {
            info.setInputType(mEditor.mInputType);

            if (mEditor.mError != null) {
                info.setContentInvalid(true);
                info.setError(mEditor.mError);
            }
        }

        if (!TextUtils.isEmpty(mText)) {
            info.addAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
            info.addAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
            info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH
                    | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);
            info.addAction(AccessibilityNodeInfo.ACTION_SET_SELECTION);
        }

        if (isFocused()) {
            if (canCopy()) {
                info.addAction(AccessibilityNodeInfo.ACTION_COPY);
            }
            if (canPaste()) {
                info.addAction(AccessibilityNodeInfo.ACTION_PASTE);
            }
            if (canCut()) {
                info.addAction(AccessibilityNodeInfo.ACTION_CUT);
            }
            if (canShare()) {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        ACCESSIBILITY_ACTION_SHARE,
                        getResources().getString(com.android.internal.R.string.share)));
            }
            if (canProcessText()) {  // also implies mEditor is not null.
                mEditor.mProcessTextIntentActionsHandler.onInitializeAccessibilityNodeInfo(info);
            }
        }

        // Check for known input filter types.
        final int numFilters = mFilters.length;
        for (int i = 0; i < numFilters; i++) {
            final InputFilter filter = mFilters[i];
            if (filter instanceof InputFilter.LengthFilter) {
                info.setMaxTextLength(((InputFilter.LengthFilter) filter).getMax());
            }
        }

        if (!isSingleLine()) {
            info.setMultiLine(true);
        }
    }

    /**
     * Performs an accessibility action after it has been offered to the
     * delegate.
     *
     * @hide
     */
    @Override
    public boolean performAccessibilityActionInternal(int action, Bundle arguments) {
        if (mEditor != null
                && mEditor.mProcessTextIntentActionsHandler.performAccessibilityAction(action)) {
            return true;
        }
        switch (action) {
            case AccessibilityNodeInfo.ACTION_CLICK: {
                return performAccessibilityActionClick(arguments);
            }
            case AccessibilityNodeInfo.ACTION_COPY: {
                if (isFocused() && canCopy()) {
                    if (onTextContextMenuItem(ID_COPY)) {
                        return true;
                    }
                }
            } return false;
            case AccessibilityNodeInfo.ACTION_PASTE: {
                if (isFocused() && canPaste()) {
                    if (onTextContextMenuItem(ID_PASTE)) {
                        return true;
                    }
                }
            } return false;
            case AccessibilityNodeInfo.ACTION_CUT: {
                if (isFocused() && canCut()) {
                    if (onTextContextMenuItem(ID_CUT)) {
                        return true;
                    }
                }
            } return false;
            case AccessibilityNodeInfo.ACTION_SET_SELECTION: {
                ensureIterableTextForAccessibilitySelectable();
                CharSequence text = getIterableTextForAccessibility();
                if (text == null) {
                    return false;
                }
                final int start = (arguments != null) ? arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, -1) : -1;
                final int end = (arguments != null) ? arguments.getInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, -1) : -1;
                if ((getSelectionStart() != start || getSelectionEnd() != end)) {
                    // No arguments clears the selection.
                    if (start == end && end == -1) {
                        Selection.removeSelection((Spannable) text);
                        return true;
                    }
                    if (start >= 0 && start <= end && end <= text.length()) {
                        Selection.setSelection((Spannable) text, start, end);
                        // Make sure selection mode is engaged.
                        if (mEditor != null) {
                            mEditor.startSelectionActionMode();
                        }
                        return true;
                    }
                }
            } return false;
            case AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
            case AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY: {
                ensureIterableTextForAccessibilitySelectable();
                return super.performAccessibilityActionInternal(action, arguments);
            }
            case ACCESSIBILITY_ACTION_SHARE: {
                if (isFocused() && canShare()) {
                    if (onTextContextMenuItem(ID_SHARE)) {
                        return true;
                    }
                }
            } return false;
            case AccessibilityNodeInfo.ACTION_SET_TEXT: {
                if (!isEnabled() || (mBufferType != BufferType.EDITABLE)) {
                    return false;
                }
                CharSequence text = (arguments != null) ? arguments.getCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE) : null;
                setText(text);
                if (mText != null) {
                    int updatedTextLength = mText.length();
                    if (updatedTextLength > 0) {
                        Selection.setSelection((Spannable) mText, updatedTextLength);
                    }
                }
            } return true;
            default: {
                return super.performAccessibilityActionInternal(action, arguments);
            }
        }
    }

    private boolean performAccessibilityActionClick(Bundle arguments) {
        boolean handled = false;

        if (!isEnabled()) {
            return false;
        }

        if (isClickable() || isLongClickable()) {
            // Simulate View.onTouchEvent for an ACTION_UP event
            if (isFocusable() && !isFocused()) {
                requestFocus();
            }

            performClick();
            handled = true;
        }

        // Show the IME, except when selecting in read-only text.
        if ((mMovement != null || onCheckIsTextEditor()) && hasSpannableText() && mLayout != null
                && (isTextEditable() || isTextSelectable()) && isFocused()) {
            final InputMethodManager imm = InputMethodManager.peekInstance();
            viewClicked(imm);
            if (!isTextSelectable() && mEditor.mShowSoftInputOnFocus && imm != null) {
                handled |= imm.showSoftInput(this, 0);
            }
        }

        return handled;
    }

    private boolean hasSpannableText() {
        return mText != null && mText instanceof Spannable;
    }

    /** @hide */
    @Override
    public void sendAccessibilityEventInternal(int eventType) {
        if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED && mEditor != null) {
            mEditor.mProcessTextIntentActionsHandler.initializeAccessibilityActions();
        }

        // Do not send scroll events since first they are not interesting for
        // accessibility and second such events a generated too frequently.
        // For details see the implementation of bringTextIntoView().
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return;
        }
        super.sendAccessibilityEventInternal(eventType);
    }

    /**
     * Returns the text that should be exposed to accessibility services.
     * <p>
     * This approximates what is displayed visually. If the user has specified
     * that accessibility services should speak passwords, this method will
     * bypass any password transformation method and return unobscured text.
     *
     * @return the text that should be exposed to accessibility services, may
     *         be {@code null} if no text is set
     */
    @Nullable
    private CharSequence getTextForAccessibility() {
        // If the text is empty, we must be showing the hint text.
        if (TextUtils.isEmpty(mText)) {
            return mHint;
        }

        // Check whether we need to bypass the transformation
        // method and expose unobscured text.
        if (hasPasswordTransformationMethod() && shouldSpeakPasswordsForAccessibility()) {
            return mText;
        }

        // Otherwise, speak whatever text is being displayed.
        return mTransformed;
    }

    void sendAccessibilityEventTypeViewTextChanged(CharSequence beforeText,
            int fromIndex, int removedCount, int addedCount) {
        AccessibilityEvent event =
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        event.setFromIndex(fromIndex);
        event.setRemovedCount(removedCount);
        event.setAddedCount(addedCount);
        event.setBeforeText(beforeText);
        sendAccessibilityEventUnchecked(event);
    }

    /**
     * Returns whether this text view is a current input method target.  The
     * default implementation just checks with {@link InputMethodManager}.
     */
    public boolean isInputMethodTarget() {
        InputMethodManager imm = InputMethodManager.peekInstance();
        return imm != null && imm.isActive(this);
    }

    static final int ID_SELECT_ALL = android.R.id.selectAll;
    static final int ID_UNDO = android.R.id.undo;
    static final int ID_REDO = android.R.id.redo;
    static final int ID_CUT = android.R.id.cut;
    static final int ID_COPY = android.R.id.copy;
    static final int ID_PASTE = android.R.id.paste;
    static final int ID_SHARE = android.R.id.shareText;
    static final int ID_PASTE_AS_PLAIN_TEXT = android.R.id.pasteAsPlainText;
    static final int ID_REPLACE = android.R.id.replaceText;

    /**
     * Called when a context menu option for the text view is selected.  Currently
     * this will be one of {@link android.R.id#selectAll}, {@link android.R.id#cut},
     * {@link android.R.id#copy}, {@link android.R.id#paste} or {@link android.R.id#shareText}.
     *
     * @return true if the context menu item action was performed.
     */
    public boolean onTextContextMenuItem(int id) {
        int min = 0;
        int max = mText.length();

        if (isFocused()) {
            final int selStart = getSelectionStart();
            final int selEnd = getSelectionEnd();

            min = Math.max(0, Math.min(selStart, selEnd));
            max = Math.max(0, Math.max(selStart, selEnd));
        }

        switch (id) {
            case ID_SELECT_ALL:
                selectAllText();
                return true;

            case ID_UNDO:
                if (mEditor != null) {
                    mEditor.undo();
                }
                return true;  // Returns true even if nothing was undone.

            case ID_REDO:
                if (mEditor != null) {
                    mEditor.redo();
                }
                return true;  // Returns true even if nothing was undone.

            case ID_PASTE:
                paste(min, max, true /* withFormatting */);
                return true;

            case ID_PASTE_AS_PLAIN_TEXT:
                paste(min, max, false /* withFormatting */);
                return true;

            case ID_CUT:
                setPrimaryClip(ClipData.newPlainText(null, getTransformedText(min, max)));
                deleteText_internal(min, max);
                return true;

            case ID_COPY:
                setPrimaryClip(ClipData.newPlainText(null, getTransformedText(min, max)));
                stopTextActionMode();
                return true;

            case ID_REPLACE:
                if (mEditor != null) {
                    mEditor.replace();
                }
                return true;

            case ID_SHARE:
                shareSelectedText();
                return true;
        }
        return false;
    }

    CharSequence getTransformedText(int start, int end) {
        return removeSuggestionSpans(mTransformed.subSequence(start, end));
    }

    @Override
    public boolean performLongClick() {
        boolean handled = false;

        if (mEditor != null) {
            mEditor.mIsBeingLongClicked = true;
        }

        if (super.performLongClick()) {
            handled = true;
        }

        if (mEditor != null) {
            handled |= mEditor.performLongClick(handled);
            mEditor.mIsBeingLongClicked = false;
        }

        if (handled) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (mEditor != null) mEditor.mDiscardNextActionUp = true;
        }

        return handled;
    }

    @Override
    protected void onScrollChanged(int horiz, int vert, int oldHoriz, int oldVert) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert);
        if (mEditor != null) {
            mEditor.onScrollChanged();
        }
    }

    /**
     * Return whether or not suggestions are enabled on this TextView. The suggestions are generated
     * by the IME or by the spell checker as the user types. This is done by adding
     * {@link SuggestionSpan}s to the text.
     *
     * When suggestions are enabled (default), this list of suggestions will be displayed when the
     * user asks for them on these parts of the text. This value depends on the inputType of this
     * TextView.
     *
     * The class of the input type must be {@link InputType#TYPE_CLASS_TEXT}.
     *
     * In addition, the type variation must be one of
     * {@link InputType#TYPE_TEXT_VARIATION_NORMAL},
     * {@link InputType#TYPE_TEXT_VARIATION_EMAIL_SUBJECT},
     * {@link InputType#TYPE_TEXT_VARIATION_LONG_MESSAGE},
     * {@link InputType#TYPE_TEXT_VARIATION_SHORT_MESSAGE} or
     * {@link InputType#TYPE_TEXT_VARIATION_WEB_EDIT_TEXT}.
     *
     * And finally, the {@link InputType#TYPE_TEXT_FLAG_NO_SUGGESTIONS} flag must <i>not</i> be set.
     *
     * @return true if the suggestions popup window is enabled, based on the inputType.
     */
    public boolean isSuggestionsEnabled() {
        if (mEditor == null) return false;
        if ((mEditor.mInputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            return false;
        }
        if ((mEditor.mInputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) > 0) return false;

        final int variation = mEditor.mInputType & EditorInfo.TYPE_MASK_VARIATION;
        return (variation == EditorInfo.TYPE_TEXT_VARIATION_NORMAL ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_SUBJECT ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_LONG_MESSAGE ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_SHORT_MESSAGE ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT);
    }

    /**
     * If provided, this ActionMode.Callback will be used to create the ActionMode when text
     * selection is initiated in this View.
     *
     * <p>The standard implementation populates the menu with a subset of Select All, Cut, Copy,
     * Paste, Replace and Share actions, depending on what this View supports.
     *
     * <p>A custom implementation can add new entries in the default menu in its
     * {@link android.view.ActionMode.Callback#onPrepareActionMode(ActionMode, android.view.Menu)}
     * method. The default actions can also be removed from the menu using
     * {@link android.view.Menu#removeItem(int)} and passing {@link android.R.id#selectAll},
     * {@link android.R.id#cut}, {@link android.R.id#copy}, {@link android.R.id#paste},
     * {@link android.R.id#replaceText} or {@link android.R.id#shareText} ids as parameters.
     *
     * <p>Returning false from
     * {@link android.view.ActionMode.Callback#onCreateActionMode(ActionMode, android.view.Menu)}
     * will prevent the action mode from being started.
     *
     * <p>Action click events should be handled by the custom implementation of
     * {@link android.view.ActionMode.Callback#onActionItemClicked(ActionMode,
     * android.view.MenuItem)}.
     *
     * <p>Note that text selection mode is not started when a TextView receives focus and the
     * {@link android.R.attr#selectAllOnFocus} flag has been set. The content is highlighted in
     * that case, to allow for quick replacement.
     */
    public void setCustomSelectionActionModeCallback(ActionMode.Callback actionModeCallback) {
        createEditorIfNeeded();
        mEditor.mCustomSelectionActionModeCallback = actionModeCallback;
    }

    /**
     * Retrieves the value set in {@link #setCustomSelectionActionModeCallback}. Default is null.
     *
     * @return The current custom selection callback.
     */
    public ActionMode.Callback getCustomSelectionActionModeCallback() {
        return mEditor == null ? null : mEditor.mCustomSelectionActionModeCallback;
    }

    /**
     * If provided, this ActionMode.Callback will be used to create the ActionMode when text
     * insertion is initiated in this View.
     * The standard implementation populates the menu with a subset of Select All,
     * Paste and Replace actions, depending on what this View supports.
     *
     * <p>A custom implementation can add new entries in the default menu in its
     * {@link android.view.ActionMode.Callback#onPrepareActionMode(android.view.ActionMode,
     * android.view.Menu)} method. The default actions can also be removed from the menu using
     * {@link android.view.Menu#removeItem(int)} and passing {@link android.R.id#selectAll},
     * {@link android.R.id#paste} or {@link android.R.id#replaceText} ids as parameters.</p>
     *
     * <p>Returning false from
     * {@link android.view.ActionMode.Callback#onCreateActionMode(android.view.ActionMode,
     * android.view.Menu)} will prevent the action mode from being started.</p>
     *
     * <p>Action click events should be handled by the custom implementation of
     * {@link android.view.ActionMode.Callback#onActionItemClicked(android.view.ActionMode,
     * android.view.MenuItem)}.</p>
     *
     * <p>Note that text insertion mode is not started when a TextView receives focus and the
     * {@link android.R.attr#selectAllOnFocus} flag has been set.</p>
     */
    public void setCustomInsertionActionModeCallback(ActionMode.Callback actionModeCallback) {
        createEditorIfNeeded();
        mEditor.mCustomInsertionActionModeCallback = actionModeCallback;
    }

    /**
     * Retrieves the value set in {@link #setCustomInsertionActionModeCallback}. Default is null.
     *
     * @return The current custom insertion callback.
     */
    public ActionMode.Callback getCustomInsertionActionModeCallback() {
        return mEditor == null ? null : mEditor.mCustomInsertionActionModeCallback;
    }

    /**
     * @hide
     */
    protected void stopTextActionMode() {
        if (mEditor != null) {
            mEditor.stopTextActionMode();
        }
    }

    boolean canUndo() {
        return mEditor != null && mEditor.canUndo();
    }

    boolean canRedo() {
        return mEditor != null && mEditor.canRedo();
    }

    boolean canCut() {
        if (hasPasswordTransformationMethod()) {
            return false;
        }

        if (mText.length() > 0 && hasSelection() && mText instanceof Editable && mEditor != null &&
                mEditor.mKeyListener != null) {
            return true;
        }

        return false;
    }

    boolean canCopy() {
        if (hasPasswordTransformationMethod()) {
            return false;
        }

        if (mText.length() > 0 && hasSelection() && mEditor != null) {
            return true;
        }

        return false;
    }

    boolean canShare() {
        if (!getContext().canStartActivityForResult() || !isDeviceProvisioned()) {
            return false;
        }
        return canCopy();
    }

    boolean isDeviceProvisioned() {
        if (mDeviceProvisionedState == DEVICE_PROVISIONED_UNKNOWN) {
            mDeviceProvisionedState = Settings.Global.getInt(
                    mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 0) != 0
                    ? DEVICE_PROVISIONED_YES
                    : DEVICE_PROVISIONED_NO;
        }
        return mDeviceProvisionedState == DEVICE_PROVISIONED_YES;
    }

    boolean canPaste() {
        return (mText instanceof Editable &&
                mEditor != null && mEditor.mKeyListener != null &&
                getSelectionStart() >= 0 &&
                getSelectionEnd() >= 0 &&
                ((ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE)).
                hasPrimaryClip());
    }

    boolean canProcessText() {
        if (getId() == View.NO_ID) {
            return false;
        }
        return canShare();
    }

    boolean canSelectAllText() {
        return canSelectText() && !hasPasswordTransformationMethod()
                && !(getSelectionStart() == 0 && getSelectionEnd() == mText.length());
    }

    boolean selectAllText() {
        final int length = mText.length();
        Selection.setSelection((Spannable) mText, 0, length);
        return length > 0;
    }

    void replaceSelectionWithText(CharSequence text) {
        ((Editable) mText).replace(getSelectionStart(), getSelectionEnd(), text);
    }

    /**
     * Paste clipboard content between min and max positions.
     */
    private void paste(int min, int max, boolean withFormatting) {
        ClipboardManager clipboard =
            (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {
            boolean didFirst = false;
            for (int i=0; i<clip.getItemCount(); i++) {
                final CharSequence paste;
                if (withFormatting) {
                    paste = clip.getItemAt(i).coerceToStyledText(getContext());
                } else {
                    // Get an item as text and remove all spans by toString().
                    final CharSequence text = clip.getItemAt(i).coerceToText(getContext());
                    paste = (text instanceof Spanned) ? text.toString() : text;
                }
                if (paste != null) {
                    if (!didFirst) {
                        Selection.setSelection((Spannable) mText, max);
                        ((Editable) mText).replace(min, max, paste);
                        didFirst = true;
                    } else {
                        ((Editable) mText).insert(getSelectionEnd(), "\n");
                        ((Editable) mText).insert(getSelectionEnd(), paste);
                    }
                }
            }
            sLastCutCopyOrTextChangedTime = 0;
        }
    }

    private void shareSelectedText() {
        String selectedText = getSelectedText();
        if (selectedText != null && !selectedText.isEmpty()) {
            Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.removeExtra(android.content.Intent.EXTRA_TEXT);
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, selectedText);
            getContext().startActivity(Intent.createChooser(sharingIntent, null));
            Selection.setSelection((Spannable) mText, getSelectionEnd());
        }
    }

    private void setPrimaryClip(ClipData clip) {
        ClipboardManager clipboard = (ClipboardManager) getContext().
                getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(clip);
        sLastCutCopyOrTextChangedTime = SystemClock.uptimeMillis();
    }

    /**
     * Get the character offset closest to the specified absolute position. A typical use case is to
     * pass the result of {@link MotionEvent#getX()} and {@link MotionEvent#getY()} to this method.
     *
     * @param x The horizontal absolute position of a point on screen
     * @param y The vertical absolute position of a point on screen
     * @return the character offset for the character whose position is closest to the specified
     *  position. Returns -1 if there is no layout.
     */
    public int getOffsetForPosition(float x, float y) {
        if (getLayout() == null) return -1;
        final int line = getLineAtCoordinate(y);
        final int offset = getOffsetAtCoordinate(line, x);
        return offset;
    }

    float convertToLocalHorizontalCoordinate(float x) {
        x -= getTotalPaddingLeft();
        // Clamp the position to inside of the view.
        x = Math.max(0.0f, x);
        x = Math.min(getWidth() - getTotalPaddingRight() - 1, x);
        x += getScrollX();
        return x;
    }

    int getLineAtCoordinate(float y) {
        y -= getTotalPaddingTop();
        // Clamp the position to inside of the view.
        y = Math.max(0.0f, y);
        y = Math.min(getHeight() - getTotalPaddingBottom() - 1, y);
        y += getScrollY();
        return getLayout().getLineForVertical((int) y);
    }

    int getLineAtCoordinateUnclamped(float y) {
        y -= getTotalPaddingTop();
        y += getScrollY();
        return getLayout().getLineForVertical((int) y);
    }

    int getOffsetAtCoordinate(int line, float x) {
        x = convertToLocalHorizontalCoordinate(x);
        return getLayout().getOffsetForHorizontal(line, x);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return mEditor != null && mEditor.hasInsertionController();

            case DragEvent.ACTION_DRAG_ENTERED:
                TextView.this.requestFocus();
                return true;

            case DragEvent.ACTION_DRAG_LOCATION:
                final int offset = getOffsetForPosition(event.getX(), event.getY());
                Selection.setSelection((Spannable)mText, offset);
                return true;

            case DragEvent.ACTION_DROP:
                if (mEditor != null) mEditor.onDrop(event);
                return true;

            case DragEvent.ACTION_DRAG_ENDED:
            case DragEvent.ACTION_DRAG_EXITED:
            default:
                return true;
        }
    }

    boolean isInBatchEditMode() {
        if (mEditor == null) return false;
        final Editor.InputMethodState ims = mEditor.mInputMethodState;
        if (ims != null) {
            return ims.mBatchEditNesting > 0;
        }
        return mEditor.mInBatchEditControllers;
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);

        final TextDirectionHeuristic newTextDir = getTextDirectionHeuristic();
        if (mTextDir != newTextDir) {
            mTextDir = newTextDir;
            if (mLayout != null) {
                checkForRelayout();
            }
        }
    }

    /**
     * @hide
     */
    protected TextDirectionHeuristic getTextDirectionHeuristic() {
        if (hasPasswordTransformationMethod()) {
            // passwords fields should be LTR
            return TextDirectionHeuristics.LTR;
        }

        // Always need to resolve layout direction first
        final boolean defaultIsRtl = (getLayoutDirection() == LAYOUT_DIRECTION_RTL);

        // Now, we can select the heuristic
        switch (getTextDirection()) {
            default:
            case TEXT_DIRECTION_FIRST_STRONG:
                return (defaultIsRtl ? TextDirectionHeuristics.FIRSTSTRONG_RTL :
                        TextDirectionHeuristics.FIRSTSTRONG_LTR);
            case TEXT_DIRECTION_ANY_RTL:
                return TextDirectionHeuristics.ANYRTL_LTR;
            case TEXT_DIRECTION_LTR:
                return TextDirectionHeuristics.LTR;
            case TEXT_DIRECTION_RTL:
                return TextDirectionHeuristics.RTL;
            case TEXT_DIRECTION_LOCALE:
                return TextDirectionHeuristics.LOCALE;
            case TEXT_DIRECTION_FIRST_STRONG_LTR:
                return TextDirectionHeuristics.FIRSTSTRONG_LTR;
            case TEXT_DIRECTION_FIRST_STRONG_RTL:
                return TextDirectionHeuristics.FIRSTSTRONG_RTL;
        }
    }

    /**
     * @hide
     */
    @Override
    public void onResolveDrawables(int layoutDirection) {
        // No need to resolve twice
        if (mLastLayoutDirection == layoutDirection) {
            return;
        }
        mLastLayoutDirection = layoutDirection;

        // Resolve drawables
        if (mDrawables != null) {
            if (mDrawables.resolveWithLayoutDirection(layoutDirection)) {
                prepareDrawableForDisplay(mDrawables.mShowing[Drawables.LEFT]);
                prepareDrawableForDisplay(mDrawables.mShowing[Drawables.RIGHT]);
                applyCompoundDrawableTint();
            }
        }
    }

    /**
     * Prepares a drawable for display by propagating layout direction and
     * drawable state.
     *
     * @param dr the drawable to prepare
     */
    private void prepareDrawableForDisplay(@Nullable Drawable dr) {
        if (dr == null) {
            return;
        }

        dr.setLayoutDirection(getLayoutDirection());

        if (dr.isStateful()) {
            dr.setState(getDrawableState());
            dr.jumpToCurrentState();
        }
    }

    /**
     * @hide
     */
    protected void resetResolvedDrawables() {
        super.resetResolvedDrawables();
        mLastLayoutDirection = -1;
    }

    /**
     * @hide
     */
    protected void viewClicked(InputMethodManager imm) {
        if (imm != null) {
            imm.viewClicked(this);
        }
    }

    /**
     * Deletes the range of text [start, end[.
     * @hide
     */
    protected void deleteText_internal(int start, int end) {
        ((Editable) mText).delete(start, end);
    }

    /**
     * Replaces the range of text [start, end[ by replacement text
     * @hide
     */
    protected void replaceText_internal(int start, int end, CharSequence text) {
        ((Editable) mText).replace(start, end, text);
    }

    /**
     * Sets a span on the specified range of text
     * @hide
     */
    protected void setSpan_internal(Object span, int start, int end, int flags) {
        ((Editable) mText).setSpan(span, start, end, flags);
    }

    /**
     * Moves the cursor to the specified offset position in text
     * @hide
     */
    protected void setCursorPosition_internal(int start, int end) {
        Selection.setSelection(((Editable) mText), start, end);
    }

    /**
     * An Editor should be created as soon as any of the editable-specific fields (grouped
     * inside the Editor object) is assigned to a non-default value.
     * This method will create the Editor if needed.
     *
     * A standard TextView (as well as buttons, checkboxes...) should not qualify and hence will
     * have a null Editor, unlike an EditText. Inconsistent in-between states will have an
     * Editor for backward compatibility, as soon as one of these fields is assigned.
     *
     * Also note that for performance reasons, the mEditor is created when needed, but not
     * reset when no more edit-specific fields are needed.
     */
    private void createEditorIfNeeded() {
        if (mEditor == null) {
            mEditor = new Editor(this);
        }
    }

    /**
     * @hide
     */
    @Override
    public CharSequence getIterableTextForAccessibility() {
        return mText;
    }

    private void ensureIterableTextForAccessibilitySelectable() {
        if (!(mText instanceof Spannable)) {
            setText(mText, BufferType.SPANNABLE);
        }
    }

    /**
     * @hide
     */
    @Override
    public TextSegmentIterator getIteratorForGranularity(int granularity) {
        switch (granularity) {
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE: {
                Spannable text = (Spannable) getIterableTextForAccessibility();
                if (!TextUtils.isEmpty(text) && getLayout() != null) {
                    AccessibilityIterators.LineTextSegmentIterator iterator =
                        AccessibilityIterators.LineTextSegmentIterator.getInstance();
                    iterator.initialize(text, getLayout());
                    return iterator;
                }
            } break;
            case AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE: {
                Spannable text = (Spannable) getIterableTextForAccessibility();
                if (!TextUtils.isEmpty(text) && getLayout() != null) {
                    AccessibilityIterators.PageTextSegmentIterator iterator =
                        AccessibilityIterators.PageTextSegmentIterator.getInstance();
                    iterator.initialize(this);
                    return iterator;
                }
            } break;
        }
        return super.getIteratorForGranularity(granularity);
    }

    /**
     * @hide
     */
    @Override
    public int getAccessibilitySelectionStart() {
        return getSelectionStart();
    }

    /**
     * @hide
     */
    public boolean isAccessibilitySelectionExtendable() {
        return true;
    }

    /**
     * @hide
     */
    @Override
    public int getAccessibilitySelectionEnd() {
        return getSelectionEnd();
    }

    /**
     * @hide
     */
    @Override
    public void setAccessibilitySelection(int start, int end) {
        if (getAccessibilitySelectionStart() == start
                && getAccessibilitySelectionEnd() == end) {
            return;
        }
        CharSequence text = getIterableTextForAccessibility();
        if (Math.min(start, end) >= 0 && Math.max(start, end) <= text.length()) {
            Selection.setSelection((Spannable) text, start, end);
        } else {
            Selection.removeSelection((Spannable) text);
        }
        // Hide all selection controllers used for adjusting selection
        // since we are doing so explicitlty by other means and these
        // controllers interact with how selection behaves.
        if (mEditor != null) {
            mEditor.hideCursorAndSpanControllers();
            mEditor.stopTextActionMode();
        }
    }

    /** @hide */
    @Override
    protected void encodeProperties(@NonNull ViewHierarchyEncoder stream) {
        super.encodeProperties(stream);

        TruncateAt ellipsize = getEllipsize();
        stream.addProperty("text:ellipsize", ellipsize == null ? null : ellipsize.name());
        stream.addProperty("text:textSize", getTextSize());
        stream.addProperty("text:scaledTextSize", getScaledTextSize());
        stream.addProperty("text:typefaceStyle", getTypefaceStyle());
        stream.addProperty("text:selectionStart", getSelectionStart());
        stream.addProperty("text:selectionEnd", getSelectionEnd());
        stream.addProperty("text:curTextColor", mCurTextColor);
        stream.addProperty("text:text", mText == null ? null : mText.toString());
        stream.addProperty("text:gravity", mGravity);
    }

    /**
     * User interface state that is stored by TextView for implementing
     * {@link View#onSaveInstanceState}.
     */
    public static class SavedState extends BaseSavedState {
        int selStart = -1;
        int selEnd = -1;
        CharSequence text;
        boolean frozenWithFocus;
        CharSequence error;
        ParcelableParcel editorState;  // Optional state from Editor.

        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(selStart);
            out.writeInt(selEnd);
            out.writeInt(frozenWithFocus ? 1 : 0);
            TextUtils.writeToParcel(text, out, flags);

            if (error == null) {
                out.writeInt(0);
            } else {
                out.writeInt(1);
                TextUtils.writeToParcel(error, out, flags);
            }

            if (editorState == null) {
                out.writeInt(0);
            } else {
                out.writeInt(1);
                editorState.writeToParcel(out, flags);
            }
        }

        @Override
        public String toString() {
            String str = "TextView.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " start=" + selStart + " end=" + selEnd;
            if (text != null) {
                str += " text=" + text;
            }
            return str + "}";
        }

        @SuppressWarnings("hiding")
        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        private SavedState(Parcel in) {
            super(in);
            selStart = in.readInt();
            selEnd = in.readInt();
            frozenWithFocus = (in.readInt() != 0);
            text = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);

            if (in.readInt() != 0) {
                error = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            }

            if (in.readInt() != 0) {
                editorState = ParcelableParcel.CREATOR.createFromParcel(in);
            }
        }
    }

    private static class CharWrapper implements CharSequence, GetChars, GraphicsOperations {
        private char[] mChars;
        private int mStart, mLength;

        public CharWrapper(char[] chars, int start, int len) {
            mChars = chars;
            mStart = start;
            mLength = len;
        }

        /* package */ void set(char[] chars, int start, int len) {
            mChars = chars;
            mStart = start;
            mLength = len;
        }

        public int length() {
            return mLength;
        }

        public char charAt(int off) {
            return mChars[off + mStart];
        }

        @Override
        public String toString() {
            return new String(mChars, mStart, mLength);
        }

        public CharSequence subSequence(int start, int end) {
            if (start < 0 || end < 0 || start > mLength || end > mLength) {
                throw new IndexOutOfBoundsException(start + ", " + end);
            }

            return new String(mChars, start + mStart, end - start);
        }

        public void getChars(int start, int end, char[] buf, int off) {
            if (start < 0 || end < 0 || start > mLength || end > mLength) {
                throw new IndexOutOfBoundsException(start + ", " + end);
            }

            System.arraycopy(mChars, start + mStart, buf, off, end - start);
        }

        public void drawText(Canvas c, int start, int end,
                             float x, float y, Paint p) {
            c.drawText(mChars, start + mStart, end - start, x, y, p);
        }

        public void drawTextRun(Canvas c, int start, int end,
                int contextStart, int contextEnd, float x, float y, boolean isRtl, Paint p) {
            int count = end - start;
            int contextCount = contextEnd - contextStart;
            c.drawTextRun(mChars, start + mStart, count, contextStart + mStart,
                    contextCount, x, y, isRtl, p);
        }

        public float measureText(int start, int end, Paint p) {
            return p.measureText(mChars, start + mStart, end - start);
        }

        public int getTextWidths(int start, int end, float[] widths, Paint p) {
            return p.getTextWidths(mChars, start + mStart, end - start, widths);
        }

        public float getTextRunAdvances(int start, int end, int contextStart,
                int contextEnd, boolean isRtl, float[] advances, int advancesIndex,
                Paint p) {
            int count = end - start;
            int contextCount = contextEnd - contextStart;
            return p.getTextRunAdvances(mChars, start + mStart, count,
                    contextStart + mStart, contextCount, isRtl, advances,
                    advancesIndex);
        }

        public int getTextRunCursor(int contextStart, int contextEnd, int dir,
                int offset, int cursorOpt, Paint p) {
            int contextCount = contextEnd - contextStart;
            return p.getTextRunCursor(mChars, contextStart + mStart,
                    contextCount, dir, offset + mStart, cursorOpt);
        }
    }

    private static final class Marquee {
        // TODO: Add an option to configure this
        private static final float MARQUEE_DELTA_MAX = 0.07f;
        private static final int MARQUEE_DELAY = 1200;
        private static final int MARQUEE_DP_PER_SECOND = 30;

        private static final byte MARQUEE_STOPPED = 0x0;
        private static final byte MARQUEE_STARTING = 0x1;
        private static final byte MARQUEE_RUNNING = 0x2;

        private final WeakReference<TextView> mView;
        private final Choreographer mChoreographer;

        private byte mStatus = MARQUEE_STOPPED;
        private final float mPixelsPerSecond;
        private float mMaxScroll;
        private float mMaxFadeScroll;
        private float mGhostStart;
        private float mGhostOffset;
        private float mFadeStop;
        private int mRepeatLimit;

        private float mScroll;
        private long mLastAnimationMs;

        Marquee(TextView v) {
            final float density = v.getContext().getResources().getDisplayMetrics().density;
            mPixelsPerSecond = MARQUEE_DP_PER_SECOND * density;
            mView = new WeakReference<TextView>(v);
            mChoreographer = Choreographer.getInstance();
        }

        private Choreographer.FrameCallback mTickCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                tick();
            }
        };

        private Choreographer.FrameCallback mStartCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                mStatus = MARQUEE_RUNNING;
                mLastAnimationMs = mChoreographer.getFrameTime();
                tick();
            }
        };

        private Choreographer.FrameCallback mRestartCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (mStatus == MARQUEE_RUNNING) {
                    if (mRepeatLimit >= 0) {
                        mRepeatLimit--;
                    }
                    start(mRepeatLimit);
                }
            }
        };

        void tick() {
            if (mStatus != MARQUEE_RUNNING) {
                return;
            }

            mChoreographer.removeFrameCallback(mTickCallback);

            final TextView textView = mView.get();
            if (textView != null && (textView.isFocused() || textView.isSelected())) {
                long currentMs = mChoreographer.getFrameTime();
                long deltaMs = currentMs - mLastAnimationMs;
                mLastAnimationMs = currentMs;
                float deltaPx = deltaMs / 1000f * mPixelsPerSecond;
                mScroll += deltaPx;
                if (mScroll > mMaxScroll) {
                    mScroll = mMaxScroll;
                    mChoreographer.postFrameCallbackDelayed(mRestartCallback, MARQUEE_DELAY);
                } else {
                    mChoreographer.postFrameCallback(mTickCallback);
                }
                textView.invalidate();
            }
        }

        void stop() {
            mStatus = MARQUEE_STOPPED;
            mChoreographer.removeFrameCallback(mStartCallback);
            mChoreographer.removeFrameCallback(mRestartCallback);
            mChoreographer.removeFrameCallback(mTickCallback);
            resetScroll();
        }

        private void resetScroll() {
            mScroll = 0.0f;
            final TextView textView = mView.get();
            if (textView != null) textView.invalidate();
        }

        void start(int repeatLimit) {
            if (repeatLimit == 0) {
                stop();
                return;
            }
            mRepeatLimit = repeatLimit;
            final TextView textView = mView.get();
            if (textView != null && textView.mLayout != null) {
                mStatus = MARQUEE_STARTING;
                mScroll = 0.0f;
                final int textWidth = textView.getWidth() - textView.getCompoundPaddingLeft() -
                        textView.getCompoundPaddingRight();
                final float lineWidth = textView.mLayout.getLineWidth(0);
                final float gap = textWidth / 3.0f;
                mGhostStart = lineWidth - textWidth + gap;
                mMaxScroll = mGhostStart + textWidth;
                mGhostOffset = lineWidth + gap;
                mFadeStop = lineWidth + textWidth / 6.0f;
                mMaxFadeScroll = mGhostStart + lineWidth + lineWidth;

                textView.invalidate();
                mChoreographer.postFrameCallback(mStartCallback);
            }
        }

        float getGhostOffset() {
            return mGhostOffset;
        }

        float getScroll() {
            return mScroll;
        }

        float getMaxFadeScroll() {
            return mMaxFadeScroll;
        }

        boolean shouldDrawLeftFade() {
            return mScroll <= mFadeStop;
        }

        boolean shouldDrawGhost() {
            return mStatus == MARQUEE_RUNNING && mScroll > mGhostStart;
        }

        boolean isRunning() {
            return mStatus == MARQUEE_RUNNING;
        }

        boolean isStopped() {
            return mStatus == MARQUEE_STOPPED;
        }
    }

    private class ChangeWatcher implements TextWatcher, SpanWatcher {

        private CharSequence mBeforeText;

        public void beforeTextChanged(CharSequence buffer, int start,
                                      int before, int after) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "beforeTextChanged start=" + start
                    + " before=" + before + " after=" + after + ": " + buffer);

            if (AccessibilityManager.getInstance(mContext).isEnabled()
                    && ((!isPasswordInputType(getInputType()) && !hasPasswordTransformationMethod())
                            || shouldSpeakPasswordsForAccessibility())) {
                mBeforeText = buffer.toString();
            }

            TextView.this.sendBeforeTextChanged(buffer, start, before, after);
        }

        public void onTextChanged(CharSequence buffer, int start, int before, int after) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "onTextChanged start=" + start
                    + " before=" + before + " after=" + after + ": " + buffer);
            TextView.this.handleTextChanged(buffer, start, before, after);

            if (AccessibilityManager.getInstance(mContext).isEnabled() &&
                    (isFocused() || isSelected() && isShown())) {
                sendAccessibilityEventTypeViewTextChanged(mBeforeText, start, before, after);
                mBeforeText = null;
            }
        }

        public void afterTextChanged(Editable buffer) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "afterTextChanged: " + buffer);
            TextView.this.sendAfterTextChanged(buffer);

            if (MetaKeyKeyListener.getMetaState(buffer, MetaKeyKeyListener.META_SELECTING) != 0) {
                MetaKeyKeyListener.stopSelecting(TextView.this, buffer);
            }
        }

        public void onSpanChanged(Spannable buf, Object what, int s, int e, int st, int en) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "onSpanChanged s=" + s + " e=" + e
                    + " st=" + st + " en=" + en + " what=" + what + ": " + buf);
            TextView.this.spanChange(buf, what, s, st, e, en);
        }

        public void onSpanAdded(Spannable buf, Object what, int s, int e) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "onSpanAdded s=" + s + " e=" + e
                    + " what=" + what + ": " + buf);
            TextView.this.spanChange(buf, what, -1, s, -1, e);
        }

        public void onSpanRemoved(Spannable buf, Object what, int s, int e) {
            if (DEBUG_EXTRACT) Log.v(LOG_TAG, "onSpanRemoved s=" + s + " e=" + e
                    + " what=" + what + ": " + buf);
            TextView.this.spanChange(buf, what, s, -1, e, -1);
        }
    }
}
