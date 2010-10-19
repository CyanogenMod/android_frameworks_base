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

package android.util;

import java.util.Calendar;
import java.util.List;
import java.util.Random;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.CountDownTimer;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.ContactsContract.PhoneLookup;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.R;

public class PhoneGoggles {

    public static final int CONFIRMATION_MODE_NONE = 0;
    public static final int CONFIRMATION_MODE_PROMPT = 1;
    public static final int CONFIRMATION_MODE_MATHS = 2;

    private static final int COUNTDOWN_IN_MILLIS = 10000;
    private static final int PROBLEMS_TO_SOLVE = 3;

    public static enum Level {
        EASY,
        MEDIUM,
        HARD;
    }

    public static void processCommunication(Context context,
            List<String> phoneNumbers, Runnable onCommunicate,
            Runnable onCancel, int title,  int titleUnlocked, int dialogContent,
            int communicationUnauthorized, int communicate, int cancel) {

        String appId = context.getApplicationInfo().packageName;
        final ContentResolver contentResolver = context.getContentResolver();
        final boolean gogglesEnabled = Settings.System.getInt(contentResolver,
                Settings.System.PHONE_GOGGLES_ENABLED, 0) != 0;
        final boolean communicationGogglesEnabled = Settings.System.getInt(contentResolver,
                Settings.System.PHONE_GOGGLES_APP_ENABLED + "_" + appId, 0) != 0;
        final boolean useCustom = Settings.System.getInt(contentResolver,
                Settings.System.PHONE_GOGGLES_USE_CUSTOM + "_" + appId, 0) != 0;

        if (!useCustom) {
            appId = "default";
        }

        final boolean inPhoneGoggles = gogglesEnabled && communicationGogglesEnabled
        && PhoneGoggles.inPhoneGoggles(contentResolver, appId);

        /** If we are in phone goggles, then several things have to be done */
        if (inPhoneGoggles) {
            /** We first check is the number has to be filtered */
            boolean areFiltered = PhoneGoggles.areFiltered(contentResolver,
                    phoneNumbers, appId);

            if (areFiltered) {
                int confirmationGogglesMode = Settings.System.getInt(contentResolver,
                        Settings.System.PHONE_GOGGLES_CONFIRMATION_MODE + "_" + appId,
                        0);

                switch (confirmationGogglesMode) {
                    case CONFIRMATION_MODE_PROMPT:
                        showSimpleDialog(context, onCommunicate, onCancel,
                                title, dialogContent, communicate, cancel, appId);
                        return;
                    case CONFIRMATION_MODE_NONE:
                        showToast(context, communicationUnauthorized, onCancel);
                        return;
                    case CONFIRMATION_MODE_MATHS:
                        showPhoneGogglesDialog(context, onCommunicate, onCancel,
                                title, titleUnlocked, communicate, cancel, appId);
                        return;
                }
            }
        }

        onCommunicate.run();
    }

    /**
     *  Checks if phone goggles is active
     */
    private static boolean inPhoneGoggles(ContentResolver contentResolver,
            String appId) {

        int gogglesStart = Settings.System.getInt(contentResolver,
                Settings.System.PHONE_GOGGLES_START + "_" + appId, 1320);
        int gogglesEnd = Settings.System.getInt(contentResolver,
                Settings.System.PHONE_GOGGLES_END + "_" + appId, 300);

        if (gogglesStart != gogglesEnd) {
            // Get the date in "phone goggles" format.
            Calendar calendar = Calendar.getInstance();
            int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 +
            calendar.get(Calendar.MINUTE);

            if (gogglesEnd < gogglesStart) {
                // Starts at night, ends in the morning.
                return ((minutes > gogglesStart) || (minutes < gogglesEnd));
            } else {
                return ((minutes > gogglesStart) && (minutes < gogglesEnd));
            }
        }
        return false;
    }

    /**
     *  Checks if one of the given numbers is filtered
     */
    private static boolean areFiltered(ContentResolver contentResolver,
            List<String> numbers, String appId) {

        boolean mWorkFiltered = Settings.System.getInt(contentResolver,
                Settings.System.PHONE_GOGGLES_WORK_FILTERED + "_" + appId, 0) != 0;
        boolean mMobileFiltered = Settings.System.getInt(contentResolver,
                Settings.System.PHONE_GOGGLES_MOBILE_FILTERED + "_" + appId, 0) != 0;
        boolean mOtherFiltered = Settings.System.getInt(contentResolver,
                Settings.System.PHONE_GOGGLES_OTHER_FILTERED + "_" + appId, 0) != 0;

        for (String number : numbers) {
            /* We first retrieves the callee's phone number */
            Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(number));
            /* We then nicely ask the type of phone it is */
            Cursor cursor = contentResolver.query(uri, new String[] {PhoneLookup.TYPE},
                    null, null, null);
            while (cursor.moveToNext()) {
                int type = cursor.getInt(cursor.getColumnIndex(PhoneLookup.TYPE));
                /* If it is a 'work' number, then we have to ask the user
                 * if he is really sure about doing this call */
                if((type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
                        || (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK_PAGER)) {
                    if (mWorkFiltered) {
                        return true;
                    }
                }
                else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                    if (mMobileFiltered) {
                        return true;
                    }
                }
                else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE) {
                    if (mWorkFiltered || mMobileFiltered) {
                        return true;
                    }
                }
                else {
                    if (mOtherFiltered) {
                        return true;
                    }
                }
            }
            cursor.close();
        }

        return false;
    }

    private static void showToast(Context context, int toastContent,
            Runnable onCancel) {
        Toast.makeText(context, toastContent, Toast.LENGTH_LONG).show();
        onCancel.run();
    }

    private static void showSimpleDialog(Context context,
            final Runnable onCommunicate, final Runnable onCancel,
            int title, int dialogContent, int communicate, int cancel,
            String appId) {

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context);
        dialogBuilder.setTitle(title).setMessage(dialogContent)
        .setCancelable(false)
        .setPositiveButton(communicate, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onCommunicate.run();
            }
        })
        .setNegativeButton(cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onCancel.run();
            }
        });

        dialogBuilder.show();
    }

    private static void showPhoneGogglesDialog(Context context,
            final Runnable onCommunicate, final Runnable onCancel,
            int title,  int titleUnlocked, int communicate, int cancel,
            String appId) {
        final Dialog dialog = new Dialog(context);
        final Resources resources = context.getResources();
        Level level;
        int levelInt = Settings.System.getInt(context.getContentResolver(),
                Settings.System.PHONE_GOGGLES_MATHS_LEVEL + "_" + appId, 1);

        switch (levelInt) {
            case 0:
                level = Level.EASY;
                break;
            case 2:
                level = Level.HARD;
                break;
            default:
                level = Level.MEDIUM;
        }

        dialog.setContentView(R.layout.dialog_phone_goggles);
        dialog.setTitle(title);
        dialog.setCancelable(true);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface arg0) {
                onCancel.run();
            }
        });
        Button communicateButton = (Button)dialog.findViewById(
                R.id.phone_goggles_communicate_button);
        Button cancelButton = (Button)dialog.findViewById(
                R.id.phone_goggles_cancel_button);
        TextView descriptionView = (TextView)dialog.findViewById(
                R.id.phone_goggles_description);
        EditText editText = (EditText)dialog.findViewById(
                R.id.phone_goggles_edittext);
        communicateButton.setText(communicate);
        cancelButton.setText(cancel);
        descriptionView.setText(resources.getString(
                R.string.phone_goggles_description, PROBLEMS_TO_SOLVE,
                (COUNTDOWN_IN_MILLIS / 1000)));
        communicateButton.setEnabled(false);
        communicateButton.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                onCommunicate.run();
                dialog.dismiss();
            }
        });
        cancelButton.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                dialog.cancel();
            }
        });
        editText.addTextChangedListener(new PhoneGogglesDialogWatcher(resources,
                dialog, level, PROBLEMS_TO_SOLVE, titleUnlocked));

        dialog.show();
    }

    private static Problem generateProblem(Level level) {
        int operand, result;
        Random random = new Random();
        Problem problem = new Problem();
        StringBuilder problemBuilder = new StringBuilder();

        switch (level) {
            case EASY:
                operand = random.nextInt(2);
                break;
            case HARD:
                operand = random.nextInt(4);
                break;
            default:
                operand = random.nextInt(3);
        }

        if (operand == 0) {                // Operand: +
            int value1 = random.nextInt(19) + 1;
            int value2 = random.nextInt(19) + 1;
            result = value1 + value2;
            problemBuilder.append(value1).append(" + ").append(value2);
        } else if (operand == 1) {         // Operand: -
            int value1 = random.nextInt(19) + 1;
            int value2 = random.nextInt(19) + 1;
            // Avoid negative values
            if (value1 < value2) {
                result = value2 - value1;
                problemBuilder.append(value2).append(" - ").append(value1);
            } else {
                result = value1 - value2;
                problemBuilder.append(value1).append(" - ").append(value2);
            }
        } else if (operand == 2) {         // Operand: *
            int value1 = random.nextInt(9) + 1;
            int value2 = random.nextInt(9) + 1;
            result = value1 * value2;
            problemBuilder.append(value1).append(" * ").append(value2);
        } else {                        // Operand: /
            int rand1 = random.nextInt(9) + 1;
            int rand2 = random.nextInt(9) + 1;
            int value1 = rand1 * rand2;
            int value2 = rand2;
            result = rand1;
            problemBuilder.append(value1).append(" / ").append(value2);
        }

        problem.expectedResult = result;
        problem.problem = problemBuilder.toString();

        return problem;
    }

    private static class Problem {
        private String problem;
        private int expectedResult;
    }

    private static class PhoneGogglesDialogWatcher implements TextWatcher {

        private final Dialog mDialog;
        private final Level mLevel;
        private final int mAttempts;
        private final EditText mEditText;
        private final TextView mProblemView;
        private final TextView mProblemCountView;
        private final Resources mResources;
        private final int mTitleUnlockedResId;
        private final CountDownTimer mTimer;

        private Problem mProblem;
        private int mCount;
        private boolean mStarted;

        public PhoneGogglesDialogWatcher(Resources resources, Dialog dialog,
                Level level, int attempts, int titleUnlocked) {
            mResources = resources;
            mDialog = dialog;
            mLevel = level;
            mAttempts = attempts;
            mEditText = (EditText)dialog.findViewById(
                    R.id.phone_goggles_edittext);
            mProblemView = (TextView)dialog.findViewById(
                    R.id.phone_goggles_problem);
            mTitleUnlockedResId = titleUnlocked;
            mProblemCountView = (TextView)dialog.findViewById(
                    R.id.phone_goggles_problem_count);
            mCount = 0;
            mStarted = false;
            mTimer = new CountDownTimer(COUNTDOWN_IN_MILLIS, 1000) {
                @Override
                public void onTick(long millis) {
                    int seconds = (int)(millis / 1000);
                    mDialog.setTitle(mResources.getQuantityString(
                            R.plurals.phone_goggles_countdown,
                            seconds, seconds));
                }
                @Override
                public void onFinish() {
                    mDialog.hide();
                    mDialog.cancel();
                }
            };
            setProblem();
        }

        public void afterTextChanged(Editable editable) {
            if (!mStarted) {
                mTimer.start();
            }

            mStarted = true;

            try {
                int result = Integer.parseInt(editable.toString());
                if (result == mProblem.expectedResult) {
                    mCount++;
                    if (mCount == mAttempts) {
                        mTimer.cancel();
                        Button communicateButton = (Button)mDialog.findViewById(
                                R.id.phone_goggles_communicate_button);
                        communicateButton.setEnabled(true);
                        mDialog.setTitle(mTitleUnlockedResId);
                    } else {
                        setProblem();
                    }
                }
            } catch (NumberFormatException e) {}
        }

        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
                int arg3) {}

        public void onTextChanged(CharSequence arg0, int arg1, int arg2,
                int arg3) {}

        private void setProblem() {
            mProblem = generateProblem(mLevel);
            mEditText.setText(new String());
            mProblemCountView.setText(mResources.getString(
                    R.string.phone_goggles_problem_count, (mCount + 1)));
            mProblemView.setText(mProblem.problem);
        }
    }

}