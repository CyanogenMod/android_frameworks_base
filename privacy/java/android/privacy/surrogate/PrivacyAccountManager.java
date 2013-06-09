/**
 * Copyright (C) 2012 Svyatoslav Hresyk
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses>.
 */

package android.privacy.surrogate;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.IAccountManager;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.ServiceManager;
import android.privacy.IPrivacySettingsManager;
import android.privacy.PrivacySettings;
import android.privacy.PrivacySettingsManager;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Provides privacy handling for {@link android.accounts.AccountManager}
 * @author Svyatoslav Hresyk
 * {@hide}
 */
public final class PrivacyAccountManager extends AccountManager {
    
    private static final String TAG = "PrivacyAccountManager";
    
    private Context context;
    
    private PrivacySettingsManager pSetMan;

    /** {@hide} */
    public PrivacyAccountManager(Context context, IAccountManager service) {
        super(context, service);
        this.context = context;
//        pSetMan = (PrivacySettingsManager) context.getSystemService("privacy");
        pSetMan = new PrivacySettingsManager(context, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));  
    }

    /** {@hide} */
    public PrivacyAccountManager(Context context, IAccountManager service, Handler handler) {
        super(context, service, handler);
        this.context = context;
//        pSetMan = (PrivacySettingsManager) context.getSystemService("privacy");
        pSetMan = new PrivacySettingsManager(context, IPrivacySettingsManager.Stub.asInterface(ServiceManager.getService("privacy")));        
    }

    /**
     * GET_ACCOUNTS
     */
    
    @Override
    public Account[] getAccounts() {
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output_label;
        Account[] output;
        
        if (pSet != null && pSet.getAccountsSetting() != PrivacySettings.REAL) {
            output_label = "[empty accounts list]";
            output = new Account[0];
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_ACCOUNTS_LIST, null, pSet);
        } else {
            output_label = "[real value]";
            output = super.getAccounts(); 
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_ACCOUNTS_LIST, null, pSet);
        }
        
//        Log.d(TAG, "getAccounts - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " + output_label);        
        return output;
    }

    @Override
    public Account[] getAccountsByType(String type) {
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();        
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);
        String output_label;
        Account[] output;
        
        if (pSet != null && pSet.getAccountsSetting() != PrivacySettings.REAL) {
            output_label = "[empty accounts list]";
            output = new Account[0];
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_ACCOUNTS_LIST, null, pSet);
        } else {
            output_label = "[real value]";
            output = super.getAccountsByType(type);
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_ACCOUNTS_LIST, null, pSet);
        }
        
//        Log.d(TAG, "getAccountsByType - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " + output_label);        
        return output;
    }
    
    @Override
    public AccountManagerFuture<Boolean> hasFeatures(Account account, String[] features,
            AccountManagerCallback<Boolean> callback, Handler handler) {
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();        
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);        
        String output_label;
        AccountManagerFuture<Boolean> output;
        
        if (pSet != null && pSet.getAccountsSetting() != PrivacySettings.REAL) {
            output_label = "[false]";
            output = new PrivacyAccountManagerFuture<Boolean>(false);
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_ACCOUNTS_LIST, null, pSet);      
        } else {
            output_label = "[real value]";
            output = super.hasFeatures(account, features, callback, handler);
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_ACCOUNTS_LIST, null, pSet);            
        }
        
//        Log.d(TAG, "hasFeatures - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " + output_label);        
        return output;
    }

    @Override
    public AccountManagerFuture<Account[]> getAccountsByTypeAndFeatures(String type, String[] features,
            AccountManagerCallback<Account[]> callback, Handler handler) {
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();        
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);       
        String output_label;
        AccountManagerFuture<Account[]> output;
        
        if (pSet != null && pSet.getAccountsSetting() != PrivacySettings.REAL) {
            output_label = "[false]";
            output = new PrivacyAccountManagerFuture<Account[]>(new Account[0]);
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_ACCOUNTS_LIST, null, pSet);      
        } else {
            output_label = "[real value]";
            output = super.getAccountsByTypeAndFeatures(type, features, callback, handler);
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_ACCOUNTS_LIST, null, pSet);            
        }
        
//        Log.d(TAG, "getAccountsByTypeAndFeatures - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " + output_label);           
        return output;
    }
    
    /**
     * USE_CREDENTIALS
     */
    
    @Override
    public String blockingGetAuthToken(Account account, String authTokenType, boolean notifyAuthFailure)
            throws OperationCanceledException, IOException, AuthenticatorException {
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();        
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);    
        String output;
        
        if (pSet != null && pSet.getAccountsAuthTokensSetting() != PrivacySettings.REAL) {
            output = null;
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_AUTH_TOKENS, null, pSet);      
        } else {
            output = super.blockingGetAuthToken(account, authTokenType, notifyAuthFailure);
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_AUTH_TOKENS, null, pSet);      
        }
        
//        Log.d(TAG, "blockingGetAuthToken - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " 
//                + (output == null ? "[null]" : output));
        return output;
    }

    @Override
    public AccountManagerFuture<Bundle> getAuthToken(Account account, String authTokenType, boolean notifyAuthFailure,
            AccountManagerCallback<Bundle> callback, Handler handler) {
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();        
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);   
        String output_label;
        AccountManagerFuture<Bundle> output;
        
        if (pSet != null && pSet.getAccountsAuthTokensSetting() != PrivacySettings.REAL) {
            output_label = "[empty]";
            output = new PrivacyAccountManagerFuture<Bundle>(new Bundle());
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_AUTH_TOKENS, null, pSet);      
        } else {
            output_label = "[real value]";
            output = super.getAuthToken(account, authTokenType, notifyAuthFailure, callback, handler);
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_AUTH_TOKENS, null, pSet);      
        }
        
//        Log.d(TAG, "getAuthToken - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " + output_label);           
        return output;
    }

    @Override
    public AccountManagerFuture<Bundle> getAuthToken(Account account, String authTokenType, Bundle options,
            Activity activity, AccountManagerCallback<Bundle> callback, Handler handler) {
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();        
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);   
        String output_label;
        AccountManagerFuture<Bundle> output;
        
        if (pSet != null && pSet.getAccountsAuthTokensSetting() != PrivacySettings.REAL) {
            output_label = "[empty]";
            output = new PrivacyAccountManagerFuture<Bundle>(new Bundle());
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_AUTH_TOKENS, null, pSet);      
        } else {
            output_label = "[real value]";
            output = super.getAuthToken(account, authTokenType, options, activity, callback, handler);
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_AUTH_TOKENS, null, pSet);      
        }
        
//        Log.d(TAG, "getAuthToken - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " + output_label);           
        return output;
    }

    /**
     * MANAGE_ACCOUNTS
     */
    
    @Override
    public AccountManagerFuture<Bundle> getAuthTokenByFeatures(String accountType, String authTokenType,
            String[] features, Activity activity, Bundle addAccountOptions, Bundle getAuthTokenOptions,
            AccountManagerCallback<Bundle> callback, Handler handler) {
        String packageName = context.getPackageName();
        int uid = Binder.getCallingUid();        
        PrivacySettings pSet = pSetMan.getSettings(packageName, uid);   
        String output_label;
        AccountManagerFuture<Bundle> output;
        
        if (pSet != null && pSet.getAccountsAuthTokensSetting() != PrivacySettings.REAL) {
            output_label = "[empty]";
            output = new PrivacyAccountManagerFuture<Bundle>(new Bundle());
            pSetMan.notification(packageName, uid, PrivacySettings.EMPTY, PrivacySettings.DATA_AUTH_TOKENS, null, pSet);      
        } else {
            output_label = "[real value]";
            output = super.getAuthTokenByFeatures(accountType, authTokenType, features, activity, addAccountOptions,
                    getAuthTokenOptions, callback, handler);
            pSetMan.notification(packageName, uid, PrivacySettings.REAL, PrivacySettings.DATA_AUTH_TOKENS, null, pSet);      
        }
        
//        Log.d(TAG, "getAuthTokenByFeatures - " + context.getPackageName() + " (" + Binder.getCallingUid() + ") output: " + output_label);           
        return output;
    }
    
    /**
     * Helper class. Used for returning custom values to AccountManager callers.
     */
    private class PrivacyAccountManagerFuture<V> implements AccountManagerFuture<V> {
        
        private V result;
        
        public PrivacyAccountManagerFuture(V result) {
            this.result = result;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public V getResult() throws OperationCanceledException, IOException, AuthenticatorException {
            return result;
        }

        @Override
        public V getResult(long timeout, TimeUnit unit) throws OperationCanceledException, IOException,
                AuthenticatorException {
            return result;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }
        
    }
}
