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

package android.text.method;

import com.android.internal.R;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.*;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;

/**
 * Dialog for choosing accented characters related to a base character.
 */
public class CharacterPickerDialog extends Dialog
        implements OnItemClickListener, OnClickListener, OnKeyListener {
    private View mView;
    private Editable mText;
    private String mOptions;
    private boolean mInsert;
    private LayoutInflater mInflater;
    private Button mCancelButton;
    private GridView mGrid;
    private int mKeyCode;
    private QwertyKeyListener mParent;

    /**
     * Creates a new CharacterPickerDialog that presents the specified
     * <code>options</code> for insertion or replacement (depending on
     * the sense of <code>insert</code>) into <code>text</code>.
     */
    public CharacterPickerDialog(QwertyKeyListener parent,
                                 Context context, View view,
                                 Editable text, String options,
                                 boolean insert, int keyCode) {
        super(context, com.android.internal.R.style.Theme_Panel);

        mView = view;
        mText = text;
        mOptions = options;
        mInsert = insert;
        mKeyCode = keyCode;
        mParent = parent;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.token = mView.getApplicationWindowToken();
        params.type = params.TYPE_APPLICATION_ATTACHED_DIALOG;
        params.flags = params.flags | Window.FEATURE_NO_TITLE;

        setContentView(R.layout.character_picker);

        mGrid = (GridView) findViewById(R.id.characterPicker);
        mGrid.setAdapter(new OptionsAdapter(getContext()));
        mGrid.setOnItemClickListener(this);
        mGrid.setOnKeyListener(this);

        mCancelButton = (Button) findViewById(R.id.cancel);
        mCancelButton.setOnClickListener(this);
        mCancelButton.setOnKeyListener(this);
    }

    /**
     * Handles clicks on the character buttons.
     */
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        String result = String.valueOf(mOptions.charAt(position));
        replaceCharacterAndClose(result);
    }

    /**
     * Handles keypresses: navigating keys are not handled, cancelling keys
     * dismiss the dialog without action, all other keys finish the dialog
     * and allow the user to continue inputting text.
     */
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == mKeyCode) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                mKeyCode = -1;
            }
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_ESCAPE ||
                keyCode == KeyEvent.KEYCODE_CLEAR) {
                dismiss();
            } else {
                if (keyCode != KeyEvent.KEYCODE_DEL) {
                    onClick(mGrid.getSelectedView());
                }
                return mParent.onKeyDown(mView, mText, keyCode, event);
            }
        }
        return false;
    }


    private void replaceCharacterAndClose(CharSequence replace) {
        int selEnd = Selection.getSelectionEnd(mText);
        if (mInsert || selEnd == 0) {
            mText.insert(selEnd, replace);
        } else {
            mText.replace(selEnd - 1, selEnd, replace);
        }

        dismiss();
    }

    /**
     * Handles clicks on the Cancel button.
     */
    public void onClick(View v) {
        if (v == mCancelButton) {
            dismiss();
        } else if (v instanceof Button) {
            CharSequence result = ((Button) v).getText();
            replaceCharacterAndClose(result);
        }
    }

    private class OptionsAdapter extends BaseAdapter {

        public OptionsAdapter(Context context) {
            super();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Button b = (Button)
                mInflater.inflate(R.layout.character_picker_button, null);
            b.setText(String.valueOf(mOptions.charAt(position)));
            b.setOnClickListener(CharacterPickerDialog.this);
            return b;
        }

        public final int getCount() {
            return mOptions.length();
        }

        public final Object getItem(int position) {
            return String.valueOf(mOptions.charAt(position));
        }

        public final long getItemId(int position) {
            return position;
        }
    }
}
