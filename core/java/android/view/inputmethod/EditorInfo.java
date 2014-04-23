/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.view.inputmethod;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Printer;

/**
 * An EditorInfo describes several attributes of a text editing object
 * that an input method is communicating with (typically an EditText), most
 * importantly the type of text content it contains.
 */
public class EditorInfo implements InputType, Parcelable {
    /**
     * The content type of the text box, whose bits are defined by
     * {@link InputType}.
     * 
     * @see InputType
     * @see #TYPE_MASK_CLASS
     * @see #TYPE_MASK_VARIATION
     * @see #TYPE_MASK_FLAGS
     */
    public int inputType = TYPE_NULL;

    /**
     * Set of bits in {@link #imeOptions} that provide alternative actions
     * associated with the "enter" key.  This both helps the IME provide
     * better feedback about what the enter key will do, and also allows it
     * to provide alternative mechanisms for providing that command.
     */
    public static final int IME_MASK_ACTION = 0x000000ff;
    
    /**
     * Bits of {@link #IME_MASK_ACTION}: no specific action has been
     * associated with this editor, let the editor come up with its own if
     * it can.
     */
    public static final int IME_ACTION_UNSPECIFIED = 0x00000000;
    
    /**
     * Bits of {@link #IME_MASK_ACTION}: there is no available action.
     */
    public static final int IME_ACTION_NONE = 0x00000001;
    
    /**
     * Bits of {@link #IME_MASK_ACTION}: the action key performs a "go"
     * operation to take the user to the target of the text they typed.
     * Typically used, for example, when entering a URL.
     */
    public static final int IME_ACTION_GO = 0x00000002;
    
    /**
     * Bits of {@link #IME_MASK_ACTION}: the action key performs a "search"
     * operation, taking the user to the results of searching for the text
     * they have typed (in whatever context is appropriate).
     */
    public static final int IME_ACTION_SEARCH = 0x00000003;
    
    /**
     * Bits of {@link #IME_MASK_ACTION}: the action key performs a "send"
     * operation, delivering the text to its target.  This is typically used
     * when composing a message in IM or SMS where sending is immediate.
     */
    public static final int IME_ACTION_SEND = 0x00000004;
    
    /**
     * Bits of {@link #IME_MASK_ACTION}: the action key performs a "next"
     * operation, taking the user to the next field that will accept text.
     */
    public static final int IME_ACTION_NEXT = 0x00000005;
    
    /**
     * Bits of {@link #IME_MASK_ACTION}: the action key performs a "done"
     * operation, typically meaning there is nothing more to input and the
     * IME will be closed.
     */
    public static final int IME_ACTION_DONE = 0x00000006;
    
    /**
     * Bits of {@link #IME_MASK_ACTION}: Like {@link #IME_ACTION_NEXT}, but
     * for moving to the previous field.  This will normally not be used to
     * specify an action (since it precludes {@link #IME_ACTION_NEXT}), but
     * can be returned to the app if it sets {@link #IME_FLAG_NAVIGATE_PREVIOUS}.
     */
    public static final int IME_ACTION_PREVIOUS = 0x00000007;

    /**
     * Flag of {@link #imeOptions}: used to request that the IME never go
     * into fullscreen mode.
     * By default, IMEs may go into full screen mode when they think
     * it's appropriate, for example on small screens in landscape
     * orientation where displaying a software keyboard may occlude
     * such a large portion of the screen that the remaining part is
     * too small to meaningfully display the application UI.
     * If this flag is set, compliant IMEs will never go into full screen mode,
     * and always leave some space to display the application UI.
     * Applications need to be aware that the flag is not a guarantee, and
     * some IMEs may ignore it.
     */
    public static final int IME_FLAG_NO_FULLSCREEN = 0x2000000;

    /**
     * Flag of {@link #imeOptions}: like {@link #IME_FLAG_NAVIGATE_NEXT}, but
     * specifies there is something interesting that a backward navigation
     * can focus on.  If the user selects the IME's facility to backward
     * navigate, this will show up in the application as an {@link #IME_ACTION_PREVIOUS}
     * at {@link InputConnection#performEditorAction(int)
     * InputConnection.performEditorAction(int)}.
     */
    public static final int IME_FLAG_NAVIGATE_PREVIOUS = 0x4000000;

    /**
     * Flag of {@link #imeOptions}: used to specify that there is something
     * interesting that a forward navigation can focus on. This is like using
     * {@link #IME_ACTION_NEXT}, except allows the IME to be multiline (with
     * an enter key) as well as provide forward navigation.  Note that some
     * IMEs may not be able to do this, especially when running on a small
     * screen where there is little space.  In that case it does not need to
     * present a UI for this option.  Like {@link #IME_ACTION_NEXT}, if the
     * user selects the IME's facility to forward navigate, this will show up
     * in the application at {@link InputConnection#performEditorAction(int)
     * InputConnection.performEditorAction(int)}.
     */
    public static final int IME_FLAG_NAVIGATE_NEXT = 0x8000000;

    /**
     * Flag of {@link #imeOptions}: used to specify that the IME does not need
     * to show its extracted text UI.  For input methods that may be fullscreen,
     * often when in landscape mode, this allows them to be smaller and let part
     * of the application be shown behind, through transparent UI parts in the
     * fullscreen IME. The part of the UI visible to the user may not be responsive
     * to touch because the IME will receive touch events, which may confuse the
     * user; use {@link #IME_FLAG_NO_FULLSCREEN} instead for a better experience.
     * Using this flag is discouraged and it may become deprecated in the future.
     * Its meaning is unclear in some situations and it may not work appropriately
     * on older versions of the platform.
     */
    public static final int IME_FLAG_NO_EXTRACT_UI = 0x10000000;
    
    /**
     * Flag of {@link #imeOptions}: used in conjunction with one of the actions
     * masked by {@link #IME_MASK_ACTION}, this indicates that the action
     * should not be available as an accessory button on the right of the extracted
     * text when the input method is full-screen. Note that by setting this flag,
     * there can be cases where the action is simply never available to the
     * user. Setting this generally means that you think that in fullscreen mode,
     * where there is little space to show the text, it's not worth taking some
     * screen real estate to display the action and it should be used instead
     * to show more text.
     */
    public static final int IME_FLAG_NO_ACCESSORY_ACTION = 0x20000000;
    
    /**
     * Flag of {@link #imeOptions}: used in conjunction with one of the actions
     * masked by {@link #IME_MASK_ACTION}. If this flag is not set, IMEs will
     * normally replace the "enter" key with the action supplied. This flag
     * indicates that the action should not be available in-line as a replacement
     * for the "enter" key. Typically this is because the action has such a
     * significant impact or is not recoverable enough that accidentally hitting
     * it should be avoided, such as sending a message. Note that
     * {@link android.widget.TextView} will automatically set this flag for you
     * on multi-line text views.
     */
    public static final int IME_FLAG_NO_ENTER_ACTION = 0x40000000;

    /**
     * Flag of {@link #imeOptions}: used to request an IME that is capable of
     * inputting ASCII characters.  The intention of this flag is to ensure that
     * the user can type Roman alphabet characters in a {@link android.widget.TextView}.
     * It is typically used for an account ID or password input. A lot of the time,
     * IMEs are already able to input ASCII even without being told so (such IMEs
     * already respect this flag in a sense), but there are cases when this is not
     * the default. For instance, users of languages using a different script like
     * Arabic, Greek, Hebrew or Russian typically have a keyboard that can't
     * input ASCII characters by default. Applications need to be
     * aware that the flag is not a guarantee, and some IMEs may not respect it.
     * However, it is strongly recommended for IME authors to respect this flag
     * especially when their IME could end up with a state where only languages
     * using non-ASCII are enabled.
     */
    public static final int IME_FLAG_FORCE_ASCII = 0x80000000;

    /**
     * Generic unspecified type for {@link #imeOptions}.
     */
    public static final int IME_NULL = 0x00000000;
    
    /**
     * Extended type information for the editor, to help the IME better
     * integrate with it.
     */
    public int imeOptions = IME_NULL;
    
    /**
     * A string supplying additional information options that are
     * private to a particular IME implementation.  The string must be
     * scoped to a package owned by the implementation, to ensure there are
     * no conflicts between implementations, but other than that you can put
     * whatever you want in it to communicate with the IME.  For example,
     * you could have a string that supplies an argument like
     * <code>"com.example.myapp.SpecialMode=3"</code>.  This field is can be
     * filled in from the {@link android.R.attr#privateImeOptions}
     * attribute of a TextView.
     */
    public String privateImeOptions = null;
    
    /**
     * In some cases an IME may be able to display an arbitrary label for
     * a command the user can perform, which you can specify here. This is
     * typically used as the label for the action to use in-line as a replacement
     * for the "enter" key (see {@link #actionId}). Remember the key where
     * this will be displayed is typically very small, and there are significant
     * localization challenges to make this fit in all supported languages. Also
     * you can not count absolutely on this being used, as some IMEs may
     * ignore this.
     */
    public CharSequence actionLabel = null;
    
    /**
     * If {@link #actionLabel} has been given, this is the id for that command
     * when the user presses its button that is delivered back with
     * {@link InputConnection#performEditorAction(int)
     * InputConnection.performEditorAction()}.
     */
    public int actionId = 0;
    
    /**
     * The text offset of the start of the selection at the time editing
     * began; -1 if not known. Keep in mind some IMEs may not be able
     * to give their full feature set without knowing the cursor position;
     * avoid passing -1 here if you can.
     */
    public int initialSelStart = -1;
    
    /**
     * The text offset of the end of the selection at the time editing
     * began; -1 if not known. Keep in mind some IMEs may not be able
     * to give their full feature set without knowing the cursor position;
     * avoid passing -1 here if you can.
     */
    public int initialSelEnd = -1;
    
    /**
     * The capitalization mode of the first character being edited in the
     * text.  Values may be any combination of
     * {@link TextUtils#CAP_MODE_CHARACTERS TextUtils.CAP_MODE_CHARACTERS},
     * {@link TextUtils#CAP_MODE_WORDS TextUtils.CAP_MODE_WORDS}, and
     * {@link TextUtils#CAP_MODE_SENTENCES TextUtils.CAP_MODE_SENTENCES}, though
     * you should generally just take a non-zero value to mean start out in
     * caps mode.
     */
    public int initialCapsMode = 0;
    
    /**
     * The "hint" text of the text view, typically shown in-line when the
     * text is empty to tell the user what to enter.
     */
    public CharSequence hintText;
    
    /**
     * A label to show to the user describing the text they are writing.
     */
    public CharSequence label;
    
    /**
     * Name of the package that owns this editor.
     */
    public String packageName;
    
    /**
     * Identifier for the editor's field.  This is optional, and may be
     * 0.  By default it is filled in with the result of
     * {@link android.view.View#getId() View.getId()} on the View that
     * is being edited.
     */
    public int fieldId;
    
    /**
     * Additional name for the editor's field.  This can supply additional
     * name information for the field.  By default it is null.  The actual
     * contents have no meaning.
     */
    public String fieldName;
    
    /**
     * Any extra data to supply to the input method.  This is for extended
     * communication with specific input methods; the name fields in the
     * bundle should be scoped (such as "com.mydomain.im.SOME_FIELD") so
     * that they don't conflict with others.  This field can be
     * filled in from the {@link android.R.attr#editorExtras}
     * attribute of a TextView.
     */
    public Bundle extras;
    
    /**
     * Ensure that the data in this EditorInfo is compatible with an application
     * that was developed against the given target API version.  This can
     * impact the following input types:
     * {@link InputType#TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS},
     * {@link InputType#TYPE_TEXT_VARIATION_WEB_PASSWORD},
     * {@link InputType#TYPE_NUMBER_VARIATION_NORMAL},
     * {@link InputType#TYPE_NUMBER_VARIATION_PASSWORD}.
     *
     * <p>This is called by the framework for input method implementations;
     * you should not generally need to call it yourself.
     *
     * @param targetSdkVersion The API version number that the compatible
     * application was developed against.
     */
    public final void makeCompatible(int targetSdkVersion) {
        if (targetSdkVersion < android.os.Build.VERSION_CODES.HONEYCOMB) {
            switch (inputType&(TYPE_MASK_CLASS|TYPE_MASK_VARIATION)) {
                case TYPE_CLASS_TEXT|TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS:
                    inputType = TYPE_CLASS_TEXT|TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                            | (inputType&TYPE_MASK_FLAGS);
                    break;
                case TYPE_CLASS_TEXT|TYPE_TEXT_VARIATION_WEB_PASSWORD:
                    inputType = TYPE_CLASS_TEXT|TYPE_TEXT_VARIATION_PASSWORD
                            | (inputType&TYPE_MASK_FLAGS);
                    break;
                case TYPE_CLASS_NUMBER|TYPE_NUMBER_VARIATION_NORMAL:
                case TYPE_CLASS_NUMBER|TYPE_NUMBER_VARIATION_PASSWORD:
                    inputType = TYPE_CLASS_NUMBER
                            | (inputType&TYPE_MASK_FLAGS);
                    break;
            }
        }
    }

    public final void formalTextInput(boolean forceLong) {
        if (forceLong) {
            switch (inputType&(TYPE_MASK_CLASS|TYPE_MASK_VARIATION)) {
                case TYPE_CLASS_TEXT|TYPE_TEXT_VARIATION_SHORT_MESSAGE:
                    inputType = TYPE_CLASS_TEXT|TYPE_TEXT_VARIATION_LONG_MESSAGE
                            | (inputType&TYPE_MASK_FLAGS);
            }
        }
    }

    /**
     * Write debug output of this object.
     */
    public void dump(Printer pw, String prefix) {
        pw.println(prefix + "inputType=0x" + Integer.toHexString(inputType)
                + " imeOptions=0x" + Integer.toHexString(imeOptions)
                + " privateImeOptions=" + privateImeOptions);
        pw.println(prefix + "actionLabel=" + actionLabel
                + " actionId=" + actionId);
        pw.println(prefix + "initialSelStart=" + initialSelStart
                + " initialSelEnd=" + initialSelEnd
                + " initialCapsMode=0x"
                + Integer.toHexString(initialCapsMode));
        pw.println(prefix + "hintText=" + hintText
                + " label=" + label);
        pw.println(prefix + "packageName=" + packageName
                + " fieldId=" + fieldId
                + " fieldName=" + fieldName);
        pw.println(prefix + "extras=" + extras);
    }
    
    /**
     * Used to package this object into a {@link Parcel}.
     * 
     * @param dest The {@link Parcel} to be written.
     * @param flags The flags used for parceling.
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(inputType);
        dest.writeInt(imeOptions);
        dest.writeString(privateImeOptions);
        TextUtils.writeToParcel(actionLabel, dest, flags);
        dest.writeInt(actionId);
        dest.writeInt(initialSelStart);
        dest.writeInt(initialSelEnd);
        dest.writeInt(initialCapsMode);
        TextUtils.writeToParcel(hintText, dest, flags);
        TextUtils.writeToParcel(label, dest, flags);
        dest.writeString(packageName);
        dest.writeInt(fieldId);
        dest.writeString(fieldName);
        dest.writeBundle(extras);
    }

    /**
     * Used to make this class parcelable.
     */
    public static final Parcelable.Creator<EditorInfo> CREATOR = new Parcelable.Creator<EditorInfo>() {
        public EditorInfo createFromParcel(Parcel source) {
            EditorInfo res = new EditorInfo();
            res.inputType = source.readInt();
            res.imeOptions = source.readInt();
            res.privateImeOptions = source.readString();
            res.actionLabel = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            res.actionId = source.readInt();
            res.initialSelStart = source.readInt();
            res.initialSelEnd = source.readInt();
            res.initialCapsMode = source.readInt();
            res.hintText = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            res.label = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
            res.packageName = source.readString();
            res.fieldId = source.readInt();
            res.fieldName = source.readString();
            res.extras = source.readBundle();
            return res;
        }

        public EditorInfo[] newArray(int size) {
            return new EditorInfo[size];
        }
    };

    public int describeContents() {
        return 0;
    }

}
