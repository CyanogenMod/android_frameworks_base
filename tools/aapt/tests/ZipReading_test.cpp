/*
 * Copyright (C) 2014 The CyanogenMod Project
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

#include <utils/String8.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>
#include <utils/KeyedVector.h>

#include "mocks/MockZipFile.h"
#include "mocks/MockZipEntry.h"

#include "AaptConfig.h"
#include "ConfigDescription.h"
#include "TestHelper.h"

#include "AaptAssets.h"

using android::String8;
using namespace testing;

// A path to an apk that would be considered valid
#define VALID_APK_FILE "/valid/valid.apk"

// Internal zip path to a dir that aapt is being asked to compile
#define COMPILING_OVERLAY_DIR "/assets/overlays/com.interesting.app"

// Internal zip path to a valid resource. aapt is expected to compile this resource.
#define COMPILING_OVERLAY_FILE COMPILING_OVERLAY_DIR "/res/drawable-xxhdpi/foo.png";

// Internal zip path to another overlay dir that is NOT being compiled
#define NOT_COMPILING_OVERLAY_DIR "/assets/overlays/com.boring.app"

// Internal zip path to a resource for an overlay that is NOT compiling. aapt is expected to ignore
#define NOT_COMPILING_OVERLAY_FILE COMPILING_OVERLAY_DIR "/assets/overlays/com.boring.app"

static ::testing::AssertionResult TestParse(const String8& input, ConfigDescription* config=NULL) {
    if (AaptConfig::parse(String8(input), config)) {
        return ::testing::AssertionSuccess() << input << " was successfully parsed";
    }
    return ::testing::AssertionFailure() << input << " could not be parsed";
}

static ::testing::AssertionResult TestParse(const char* input, ConfigDescription* config=NULL) {
    return TestParse(String8(input), config);
}

TEST(ZipReadingTest, TestValidZipEntryIsAdded) {
    MockZipFile zip;
    MockZipEntry entry1;
    const char* zipFile = VALID_APK_FILE;
    const char* validFilename = COMPILING_OVERLAY_FILE;

    EXPECT_CALL(entry1, getFileName())
        .WillRepeatedly(Return(validFilename));

    EXPECT_CALL(zip, getNumEntries())
        .Times(1)
        .WillRepeatedly(Return(1));

    EXPECT_CALL(zip, getEntryByIndex(_))
        .Times(1)
        .WillOnce(Return(&entry1));

    sp<AaptAssets> assets = new AaptAssets();
    Bundle bundle;
    bundle.setInternalZipPath(COMPILING_OVERLAY_DIR);
    ssize_t count = assets->slurpResourceZip(&bundle, &zip, zipFile);

    Vector<sp<AaptDir> > dirs = assets->resDirs();
    EXPECT_EQ(1, dirs.size());
    EXPECT_EQ(1, count);
}

TEST(ZipReadingTest, TestDifferentThemeEntryNotAdded) {
    MockZipFile zip;
    MockZipEntry entry1;
    const char* zipFile = VALID_APK_FILE;
    const char* invalidFile = NOT_COMPILING_OVERLAY_FILE;

    EXPECT_CALL(entry1, getFileName())
        .WillRepeatedly(Return(invalidFile));

    EXPECT_CALL(zip, getNumEntries())
        .WillRepeatedly(Return(1));

    EXPECT_CALL(zip, getEntryByIndex(_))
        .Times(1)
        .WillOnce(Return(&entry1));

    sp<AaptAssets> assets = new AaptAssets();
    Bundle bundle;
    bundle.setInternalZipPath(COMPILING_OVERLAY_DIR);
    ssize_t count = assets->slurpResourceZip(&bundle, &zip, zipFile);

    Vector<sp<AaptDir> > dirs = assets->resDirs();
    EXPECT_EQ(0, dirs.size());
    EXPECT_EQ(0, count);
}

TEST(ZipReadingTest, TestOutsideEntryMarkedInvalid) {
    Bundle bundle;
    bundle.setInternalZipPath("VALID_OVERLAY_DIR");
    MockZipEntry invalidEntry;
    const char* invalidFile = NOT_COMPILING_OVERLAY_FILE;

    EXPECT_CALL(invalidEntry, getFileName())
        .WillRepeatedly(Return(invalidFile));

    sp<AaptAssets> assets = new AaptAssets();
    bool result = assets->isEntryValid(&bundle, &invalidEntry);

    EXPECT_FALSE(result);
}

TEST(ZipReadingTest, TestNullEntryIsInvalid) {
    Bundle bundle;
    bundle.setInternalZipPath(COMPILING_OVERLAY_DIR);
    MockZipEntry invalidEntry;
    const char* invalidFile = NOT_COMPILING_OVERLAY_FILE;

    EXPECT_CALL(invalidEntry, getFileName())
        .WillRepeatedly(Return(invalidFile));

    sp<AaptAssets> assets = new AaptAssets();
    bool result = assets->isEntryValid(&bundle, NULL);

    EXPECT_FALSE(result);
}

TEST(ZipReadingTest, TestDirectoryEntryMarkedInvalid) {
    Bundle bundle;
    bundle.setInternalZipPath(COMPILING_OVERLAY_DIR);
    MockZipEntry invalidEntry2;
    // Add a "/" signifying this is a dir entry not a file entry.
    const char* dir2 = COMPILING_OVERLAY_DIR"/";
    EXPECT_CALL(invalidEntry2, getFileName())
        .WillRepeatedly(Return(dir2));

    sp<AaptAssets> assets = new AaptAssets();
    bool result2 = assets->isEntryValid(&bundle, &invalidEntry2);

    EXPECT_FALSE(result2);
}
