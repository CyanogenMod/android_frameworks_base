/*
 * Copyright (C) 2006-2007 The Android Open Source Project
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

#undef LOG_TAG
#define LOG_TAG "Database"

#include <utils/Log.h>
#include <utils/String8.h>
#include <utils/String16.h>

#include <jni.h>
#include <JNIHelp.h>
#include <android_runtime/AndroidRuntime.h>

#include <sqlite3.h>
#include <sqlite3_android.h>
#include <string.h>
#include <utils/Log.h>
#include <utils/threads.h>
#include <utils/List.h>
#include <utils/Errors.h>
#include <ctype.h>

#include <stdio.h>
#include <libgen.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <netdb.h>
#include <sys/ioctl.h>

#include "sqlite3_exception.h"

#define UTF16_STORAGE 0
#define INVALID_VERSION -1
#define SQLITE_SOFT_HEAP_LIMIT (4 * 1024 * 1024)
#define ANDROID_TABLE "android_metadata"
/* uncomment the next line to force-enable logging of all statements */
// #define DB_LOG_STATEMENTS

namespace android {

enum {
    OPEN_READWRITE          = 0x00000000,
    OPEN_READONLY           = 0x00000001,
    OPEN_READ_MASK          = 0x00000001,
    NO_LOCALIZED_COLLATORS  = 0x00000010,
    CREATE_IF_NECESSARY     = 0x10000000
};

static jfieldID offset_db_handle;

static char *createStr(const char *path) {
    int len = strlen(path);
    char *str = (char *)malloc(len + 1);
    strncpy(str, path, len);
    str[len] = NULL;
    return str;
}

static void sqlLogger(void *databaseName, int iErrCode, const char *zMsg) {
    // skip printing this message if it is due to certain types of errors
    if (iErrCode == SQLITE_CONSTRAINT) return;
    LOGI("sqlite returned: error code = %d, msg = %s\n", iErrCode, zMsg);
}

// register the logging func on sqlite. needs to be done BEFORE any sqlite3 func is called.
static void registerLoggingFunc(const char *path) {
    static bool loggingFuncSet = false;
    if (loggingFuncSet) {
        return;
    }

    LOGV("Registering sqlite logging func \n");
    int err = sqlite3_config(SQLITE_CONFIG_LOG, &sqlLogger, (void *)createStr(path));
    if (err != SQLITE_OK) {
        LOGE("sqlite_config failed error_code = %d. THIS SHOULD NEVER occur.\n", err);
        return;
    }
    loggingFuncSet = true;
}

static int use_wal_mode (char const *path8)
{
    char *temp = basename(path8);
    int i;
    const char *wal_dbs[] = {
         "database_test.db","contacts2.db","calendar.db",\
         "telephony.db","launcher.db","user_dict.db",\
         "downloads.db", "mmssms.db", "internal.db", \
         "EmailProvider.db","alarms.db","EmailProviderBody.db",\
         "btopp.db","picasa.db",\
         "webviewCache.db","browser.db", NULL};

    const char *wal_dbs_nosync[] = {
         "webview.db", "quadrant.db", \
         "MyDatabase.db", NULL};

    for (i = 0 ; wal_dbs[i]!= NULL ; i++) {
        if(strcmp(temp, wal_dbs[i]) == 0)
            return 1;
    }

    for (i = 0 ; wal_dbs_nosync[i]!= NULL ; i++) {
        if(strcmp(temp, wal_dbs_nosync[i]) == 0)
            return 2;
    }

    return 0;
}

/* public native void dbopen(String path, int flags, String locale); */
static void dbopen(JNIEnv* env, jobject object, jstring pathString, jint flags)
{
    int err;
    sqlite3 * handle = NULL;
    sqlite3_stmt * statement = NULL;
    char const * path8 = env->GetStringUTFChars(pathString, NULL);
    int sqliteFlags;
    // Error code handling for SQLite exec
    char* zErrMsg = NULL;

    // register the logging func on sqlite. needs to be done BEFORE any sqlite3 func is called.
    registerLoggingFunc(path8);

    // convert our flags into the sqlite flags
    if (flags & CREATE_IF_NECESSARY) {
        sqliteFlags = SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE;
    } else if (flags & OPEN_READONLY) {
        sqliteFlags = SQLITE_OPEN_READONLY;
    } else {
        sqliteFlags = SQLITE_OPEN_READWRITE;
    }

    err = sqlite3_open_v2(path8, &handle, sqliteFlags, NULL);
    if (err != SQLITE_OK) {
        LOGE("sqlite3_open_v2(\"%s\", &handle, %d, NULL) failed\n", path8, sqliteFlags);
        throw_sqlite3_exception(env, handle);
        goto done;
    }

    // WAL is a new rollback method available in SQLite v3.7+. WAL speeds up writes to
    // SQLite databases. WAL cannot be used with Read Only databases or databases opened
    // in read only mode.

    // Check if DB can use WAL mode; Open in WAL mode for non-ReadOnly DBs
    if(!(flags & OPEN_READONLY) && (use_wal_mode(path8))) {
        // Configure databases to run in WAL mode.
        err = sqlite3_exec(handle,"PRAGMA journal_mode = WAL;",
                           NULL, NULL,&zErrMsg);
        if (SQLITE_OK != err) {
           LOGE("sqlite3_exec - Failed to set WAL mode for [%s] \n", path8);
           err = sqlite3_exec(handle,"PRAGMA journal_mode = DELETE;",
                           NULL, NULL,&zErrMsg);
           if(SQLITE_OK != err) {
               LOGE("sqlite3_exec - Failed to set DELETE mode for [%s] \n", path8);
               throw_sqlite3_exception(env, handle);
               goto done;
           }
        }
        else {
            // Set autocheckpoint = 100 pages
            err = sqlite3_wal_autocheckpoint(handle,
                                             100);
            if (SQLITE_OK != err) {
               LOGE("sqlite3_exec to set WAL autocheckpoint failed\n");
               throw_sqlite3_exception(env, handle);
               goto done;
            } else if (use_wal_mode(path8) == 2) {
                /* Try to disable fsyncs. We don't care if it fails */
                sqlite3_exec(handle,"PRAGMA synchronous = OFF;",
                           NULL, NULL,&zErrMsg);
            }
        }
    }

    // The soft heap limit prevents the page cache allocations from growing
    // beyond the given limit, no matter what the max page cache sizes are
    // set to. The limit does not, as of 3.5.0, affect any other allocations.
    sqlite3_soft_heap_limit(SQLITE_SOFT_HEAP_LIMIT);

    // Set the default busy handler to retry for 1000ms and then return SQLITE_BUSY
    err = sqlite3_busy_timeout(handle, 1000 /* ms */);
    if (err != SQLITE_OK) {
        LOGE("sqlite3_busy_timeout(handle, 1000) failed for \"%s\"\n", path8);
        throw_sqlite3_exception(env, handle);
        goto done;
    }

#ifdef DB_INTEGRITY_CHECK
    static const char* integritySql = "pragma integrity_check(1);";
    err = sqlite3_prepare_v2(handle, integritySql, -1, &statement, NULL);
    if (err != SQLITE_OK) {
        LOGE("sqlite_prepare_v2(handle, \"%s\") failed for \"%s\"\n", integritySql, path8);
        throw_sqlite3_exception(env, handle);
        goto done;
    }

    // first is OK or error message
    err = sqlite3_step(statement);
    if (err != SQLITE_ROW) {
        LOGE("integrity check failed for \"%s\"\n", integritySql, path8);
        throw_sqlite3_exception(env, handle);
        goto done;
    } else {
        const char *text = (const char*)sqlite3_column_text(statement, 0);
        if (strcmp(text, "ok") != 0) {
            LOGE("integrity check failed for \"%s\": %s\n", integritySql, path8, text);
            jniThrowException(env, "android/database/sqlite/SQLiteDatabaseCorruptException", text);
            goto done;
        }
    }
#endif

    err = register_android_functions(handle, UTF16_STORAGE);
    if (err) {
        throw_sqlite3_exception(env, handle);
        goto done;
    }

    LOGV("Opened '%s' - %p\n", path8, handle);
    env->SetIntField(object, offset_db_handle, (int) handle);
    handle = NULL;  // The caller owns the handle now.

done:
    // Release allocated resources
    if (path8 != NULL) env->ReleaseStringUTFChars(pathString, path8);
    if (statement != NULL) sqlite3_finalize(statement);
    if (handle != NULL) sqlite3_close(handle);
}

static char *getDatabaseName(JNIEnv* env, sqlite3 * handle, jstring databaseName) {
    char const *path = env->GetStringUTFChars(databaseName, NULL);
    if (path == NULL) {
        LOGE("Failure in getDatabaseName(). VM ran out of memory?\n");
        return NULL; // VM would have thrown OutOfMemoryError
    }
    char *dbNameStr = createStr(path);
    env->ReleaseStringUTFChars(databaseName, path);
    return dbNameStr;
}

static void sqlTrace(void *databaseName, const char *sql) {
    LOGI("sql_statement|%s|%s\n", (char *)databaseName, sql);
}

/* public native void enableSqlTracing(); */
static void enableSqlTracing(JNIEnv* env, jobject object, jstring databaseName)
{
    sqlite3 * handle = (sqlite3 *)env->GetIntField(object, offset_db_handle);
    sqlite3_trace(handle, &sqlTrace, (void *)getDatabaseName(env, handle, databaseName));
}

static void sqlProfile(void *databaseName, const char *sql, sqlite3_uint64 tm) {
    double d = tm/1000000.0;
    LOGI("elapsedTime4Sql|%s|%.3f ms|%s\n", (char *)databaseName, d, sql);
}

/* public native void enableSqlProfiling(); */
static void enableSqlProfiling(JNIEnv* env, jobject object, jstring databaseName)
{
    sqlite3 * handle = (sqlite3 *)env->GetIntField(object, offset_db_handle);
    sqlite3_profile(handle, &sqlProfile, (void *)getDatabaseName(env, handle, databaseName));
}


/* public native void close(); */
static void dbclose(JNIEnv* env, jobject object)
{
    sqlite3 * handle = (sqlite3 *)env->GetIntField(object, offset_db_handle);

    if (handle != NULL) {
        // release the memory associated with the traceFuncArg in enableSqlTracing function
        void *traceFuncArg = sqlite3_trace(handle, &sqlTrace, NULL);
        if (traceFuncArg != NULL) {
            free(traceFuncArg);
        }
        // release the memory associated with the traceFuncArg in enableSqlProfiling function
        traceFuncArg = sqlite3_profile(handle, &sqlProfile, NULL);
        if (traceFuncArg != NULL) {
            free(traceFuncArg);
        }
        LOGV("Closing database: handle=%p\n", handle);
        int result = sqlite3_close(handle);
        if (result == SQLITE_OK) {
            LOGV("Closed %p\n", handle);
            env->SetIntField(object, offset_db_handle, 0);
        } else {
            // This can happen if sub-objects aren't closed first.  Make sure the caller knows.
            throw_sqlite3_exception(env, handle);
            LOGE("sqlite3_close(%p) failed: %d\n", handle, result);
        }
    }
}

/* public native void native_execSQL(String sql); */
static void native_execSQL(JNIEnv* env, jobject object, jstring sqlString)
{
    int err;
    int stepErr;
    sqlite3_stmt * statement = NULL;
    sqlite3 * handle = (sqlite3 *)env->GetIntField(object, offset_db_handle);
    jchar const * sql = env->GetStringChars(sqlString, NULL);
    jsize sqlLen = env->GetStringLength(sqlString);

    if (sql == NULL || sqlLen == 0) {
        jniThrowException(env, "java/lang/IllegalArgumentException", "You must supply an SQL string");
        return;
    }

    err = sqlite3_prepare16_v2(handle, sql, sqlLen * 2, &statement, NULL);

    env->ReleaseStringChars(sqlString, sql);

    if (err != SQLITE_OK) {
        char const * sql8 = env->GetStringUTFChars(sqlString, NULL);
        LOGE("Failure %d (%s) on %p when preparing '%s'.\n", err, sqlite3_errmsg(handle), handle, sql8);
        throw_sqlite3_exception(env, handle, sql8);
        env->ReleaseStringUTFChars(sqlString, sql8);
        return;
    }

    stepErr = sqlite3_step(statement);
    err = sqlite3_finalize(statement);

    if (stepErr != SQLITE_DONE) {
        if (stepErr == SQLITE_ROW) {
            throw_sqlite3_exception(env, "Queries cannot be performed using execSQL(), use query() instead.");
        } else {
            char const * sql8 = env->GetStringUTFChars(sqlString, NULL);
            LOGE("Failure %d (%s) on %p when executing '%s'\n", err, sqlite3_errmsg(handle), handle, sql8);
            throw_sqlite3_exception(env, handle, sql8);
            env->ReleaseStringUTFChars(sqlString, sql8);

        }
    } else
#ifndef DB_LOG_STATEMENTS
    IF_LOGV()
#endif
    {
        char const * sql8 = env->GetStringUTFChars(sqlString, NULL);
        LOGV("Success on %p when executing '%s'\n", handle, sql8);
        env->ReleaseStringUTFChars(sqlString, sql8);
    }
}

/* native long lastInsertRow(); */
static jlong lastInsertRow(JNIEnv* env, jobject object)
{
    sqlite3 * handle = (sqlite3 *)env->GetIntField(object, offset_db_handle);

    return sqlite3_last_insert_rowid(handle);
}

/* native int lastChangeCount(); */
static jint lastChangeCount(JNIEnv* env, jobject object)
{
    sqlite3 * handle = (sqlite3 *)env->GetIntField(object, offset_db_handle);

    return sqlite3_changes(handle);
}

/* native int native_getDbLookaside(); */
static jint native_getDbLookaside(JNIEnv* env, jobject object)
{
    sqlite3 * handle = (sqlite3 *)env->GetIntField(object, offset_db_handle);
    int pCur = -1;
    int unused;
    sqlite3_db_status(handle, SQLITE_DBSTATUS_LOOKASIDE_USED, &pCur, &unused, 0);
    return pCur;
}

/* set locale in the android_metadata table, install localized collators, and rebuild indexes */
static void native_setLocale(JNIEnv* env, jobject object, jstring localeString, jint flags)
{
    if ((flags & NO_LOCALIZED_COLLATORS)) return;

    int err;
    char const* locale8 = env->GetStringUTFChars(localeString, NULL);
    sqlite3 * handle = (sqlite3 *)env->GetIntField(object, offset_db_handle);
    sqlite3_stmt* stmt = NULL;
    char** meta = NULL;
    int rowCount, colCount;
    char* dbLocale = NULL;

    // create the table, if necessary and possible
    if (!(flags & OPEN_READONLY)) {
        static const char *createSql ="CREATE TABLE IF NOT EXISTS " ANDROID_TABLE " (locale TEXT)";
        err = sqlite3_exec(handle, createSql, NULL, NULL, NULL);
        if (err != SQLITE_OK) {
            LOGE("CREATE TABLE " ANDROID_TABLE " failed\n");
            throw_sqlite3_exception(env, handle);
            goto done;
        }
    }

    // try to read from the table
    static const char *selectSql = "SELECT locale FROM " ANDROID_TABLE " LIMIT 1";
    err = sqlite3_get_table(handle, selectSql, &meta, &rowCount, &colCount, NULL);
    if (err != SQLITE_OK) {
        LOGE("SELECT locale FROM " ANDROID_TABLE " failed\n");
        throw_sqlite3_exception(env, handle);
        goto done;
    }

    dbLocale = (rowCount >= 1) ? meta[colCount] : NULL;

    if (dbLocale != NULL && !strcmp(dbLocale, locale8)) {
        // database locale is the same as the desired locale; set up the collators and go
        err = register_localized_collators(handle, locale8, UTF16_STORAGE);
        if (err != SQLITE_OK) throw_sqlite3_exception(env, handle);
        goto done;   // no database changes needed
    }

    if ((flags & OPEN_READONLY)) {
        // read-only database, so we're going to have to put up with whatever we got
        // For registering new index. Not for modifing the read-only database.
        err = register_localized_collators(handle, locale8, UTF16_STORAGE);
        if (err != SQLITE_OK) throw_sqlite3_exception(env, handle);
        goto done;
    }

    // need to update android_metadata and indexes atomically, so use a transaction...
    err = sqlite3_exec(handle, "BEGIN TRANSACTION", NULL, NULL, NULL);
    if (err != SQLITE_OK) {
        LOGE("BEGIN TRANSACTION failed setting locale\n");
        throw_sqlite3_exception(env, handle);
        goto done;
    }

    err = register_localized_collators(handle, locale8, UTF16_STORAGE);
    if (err != SQLITE_OK) {
        LOGE("register_localized_collators() failed setting locale\n");
        throw_sqlite3_exception(env, handle);
        goto rollback;
    }

    err = sqlite3_exec(handle, "DELETE FROM " ANDROID_TABLE, NULL, NULL, NULL);
    if (err != SQLITE_OK) {
        LOGE("DELETE failed setting locale\n");
        throw_sqlite3_exception(env, handle);
        goto rollback;
    }

    static const char *sql = "INSERT INTO " ANDROID_TABLE " (locale) VALUES(?);";
    err = sqlite3_prepare_v2(handle, sql, -1, &stmt, NULL);
    if (err != SQLITE_OK) {
        LOGE("sqlite3_prepare_v2(\"%s\") failed\n", sql);
        throw_sqlite3_exception(env, handle);
        goto rollback;
    }

    err = sqlite3_bind_text(stmt, 1, locale8, -1, SQLITE_TRANSIENT);
    if (err != SQLITE_OK) {
        LOGE("sqlite3_bind_text() failed setting locale\n");
        throw_sqlite3_exception(env, handle);
        goto rollback;
    }

    err = sqlite3_step(stmt);
    if (err != SQLITE_OK && err != SQLITE_DONE) {
        LOGE("sqlite3_step(\"%s\") failed setting locale\n", sql);
        throw_sqlite3_exception(env, handle);
        goto rollback;
    }

    err = sqlite3_exec(handle, "REINDEX LOCALIZED", NULL, NULL, NULL);
    if (err != SQLITE_OK) {
        LOGE("REINDEX LOCALIZED failed\n");
        throw_sqlite3_exception(env, handle);
        goto rollback;
    }

    // all done, yay!
    err = sqlite3_exec(handle, "COMMIT TRANSACTION", NULL, NULL, NULL);
    if (err != SQLITE_OK) {
        LOGE("COMMIT TRANSACTION failed setting locale\n");
        throw_sqlite3_exception(env, handle);
        goto done;
    }

rollback:
    if (err != SQLITE_OK) {
        sqlite3_exec(handle, "ROLLBACK TRANSACTION", NULL, NULL, NULL);
    }

done:
    if (locale8 != NULL) env->ReleaseStringUTFChars(localeString, locale8);
    if (stmt != NULL) sqlite3_finalize(stmt);
    if (meta != NULL) sqlite3_free_table(meta);
}

static jint native_releaseMemory(JNIEnv *env, jobject clazz)
{
    // Attempt to release as much memory from the
    return sqlite3_release_memory(SQLITE_SOFT_HEAP_LIMIT);
}

static JNINativeMethod sMethods[] =
{
    /* name, signature, funcPtr */
    {"dbopen", "(Ljava/lang/String;I)V", (void *)dbopen},
    {"dbclose", "()V", (void *)dbclose},
    {"enableSqlTracing", "(Ljava/lang/String;)V", (void *)enableSqlTracing},
    {"enableSqlProfiling", "(Ljava/lang/String;)V", (void *)enableSqlProfiling},
    {"native_execSQL", "(Ljava/lang/String;)V", (void *)native_execSQL},
    {"lastInsertRow", "()J", (void *)lastInsertRow},
    {"lastChangeCount", "()I", (void *)lastChangeCount},
    {"native_setLocale", "(Ljava/lang/String;I)V", (void *)native_setLocale},
    {"native_getDbLookaside", "()I", (void *)native_getDbLookaside},
    {"releaseMemory", "()I", (void *)native_releaseMemory},
};

int register_android_database_SQLiteDatabase(JNIEnv *env)
{
    jclass clazz;

    clazz = env->FindClass("android/database/sqlite/SQLiteDatabase");
    if (clazz == NULL) {
        LOGE("Can't find android/database/sqlite/SQLiteDatabase\n");
        return -1;
    }

    offset_db_handle = env->GetFieldID(clazz, "mNativeHandle", "I");
    if (offset_db_handle == NULL) {
        LOGE("Can't find SQLiteDatabase.mNativeHandle\n");
        return -1;
    }

    return AndroidRuntime::registerNativeMethods(env, "android/database/sqlite/SQLiteDatabase", sMethods, NELEM(sMethods));
}

/* throw a SQLiteException with a message appropriate for the error in handle */
void throw_sqlite3_exception(JNIEnv* env, sqlite3* handle) {
    throw_sqlite3_exception(env, handle, NULL);
}

/* throw a SQLiteException with the given message */
void throw_sqlite3_exception(JNIEnv* env, const char* message) {
    throw_sqlite3_exception(env, NULL, message);
}

/* throw a SQLiteException with a message appropriate for the error in handle
   concatenated with the given message
 */
void throw_sqlite3_exception(JNIEnv* env, sqlite3* handle, const char* message) {
    if (handle) {
        throw_sqlite3_exception(env, sqlite3_errcode(handle),
                                sqlite3_errmsg(handle), message);
    } else {
        // we use SQLITE_OK so that a generic SQLiteException is thrown;
        // any code not specified in the switch statement below would do.
        throw_sqlite3_exception(env, SQLITE_OK, "unknown error", message);
    }
}

/* throw a SQLiteException for a given error code */
void throw_sqlite3_exception_errcode(JNIEnv* env, int errcode, const char* message) {
    if (errcode == SQLITE_DONE) {
        throw_sqlite3_exception(env, errcode, NULL, message);
    } else {
        char temp[21];
        sprintf(temp, "error code %d", errcode);
        throw_sqlite3_exception(env, errcode, temp, message);
    }
}

/* throw a SQLiteException for a given error code, sqlite3message, and
   user message
 */
void throw_sqlite3_exception(JNIEnv* env, int errcode,
                             const char* sqlite3Message, const char* message) {
    const char* exceptionClass;
    switch (errcode) {
        case SQLITE_IOERR:
            exceptionClass = "android/database/sqlite/SQLiteDiskIOException";
            break;
        case SQLITE_CORRUPT:
            exceptionClass = "android/database/sqlite/SQLiteDatabaseCorruptException";
            break;
        case SQLITE_CONSTRAINT:
           exceptionClass = "android/database/sqlite/SQLiteConstraintException";
           break;
        case SQLITE_ABORT:
           exceptionClass = "android/database/sqlite/SQLiteAbortException";
           break;
        case SQLITE_DONE:
           exceptionClass = "android/database/sqlite/SQLiteDoneException";
           break;
        case SQLITE_FULL:
           exceptionClass = "android/database/sqlite/SQLiteFullException";
           break;
        case SQLITE_MISUSE:
           exceptionClass = "android/database/sqlite/SQLiteMisuseException";
           break;
        default:
           exceptionClass = "android/database/sqlite/SQLiteException";
           break;
    }

    if (sqlite3Message != NULL && message != NULL) {
        char* fullMessage = (char *)malloc(strlen(sqlite3Message) + strlen(message) + 3);
        if (fullMessage != NULL) {
            strcpy(fullMessage, sqlite3Message);
            strcat(fullMessage, ": ");
            strcat(fullMessage, message);
            jniThrowException(env, exceptionClass, fullMessage);
            free(fullMessage);
        } else {
            jniThrowException(env, exceptionClass, sqlite3Message);
        }
    } else if (sqlite3Message != NULL) {
        jniThrowException(env, exceptionClass, sqlite3Message);
    } else {
        jniThrowException(env, exceptionClass, message);
    }
}


} // namespace android
