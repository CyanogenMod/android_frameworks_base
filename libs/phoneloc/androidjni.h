/*
 * Copyright (C) 2012 - 2014 The MoKee OpenSource Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#ifndef __ANDROID_JNI_H__
#define __ANDROID_JNI_H__

/*
 * Global header for android jni call
 *
 * Created by cytown martincz
 * Last edited 2013.3.24
 *
 */

/**
 * This define the reg class for jni call
 */
#define JNIREG_CLASS "android/mokee/location/PhoneLocation"

JNIEXPORT jstring JNICALL
getPhoneLocationJni( JNIEnv* env, jclass thiz, jstring phone );

/**
 * Table of methods associated with a single class.
 */
static JNINativeMethod gMethods[] = {
    {
        "getPhoneLocationJni", "(Ljava/lang/String;)Ljava/lang/String;",
        (void*) getPhoneLocationJni
    },
    /* <<----Functions for sync end--------------------------------- */
};
#endif
