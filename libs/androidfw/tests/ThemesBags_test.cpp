/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <androidfw/ResourceTypes.h>

#include <utils/String8.h>
#include <utils/String16.h>
#include "TestHelpers.h"
#include "data/system/R.h"
#include "data/app/R.h"
#include "data/bags/R.h"

#include <gtest/gtest.h>

using namespace android;

namespace {

/**
 * Include a binary resource table.
 *
 * Package: android
 */
#include "data/system/system_arsc.h"

/**
 * Include a binary resource table.
 *
 * Package: com.android.app
 */
#include "data/app/app_arsc.h"

/**
 * Include a binary resource table.
 * This table is an overlay.
 *
 * Package: com.android.test.bags
 */
#include "data/bags/bags_arsc.h"

enum { MAY_NOT_BE_BAG = false };

class BagsTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        ASSERT_EQ(NO_ERROR, mTargetTable.add(app_arsc, app_arsc_len));
        ASSERT_EQ(NO_ERROR, mOverlayTable.add(bags_arsc, bags_arsc_len));
        char targetName[256] = "com.android.app";
        ASSERT_EQ(NO_ERROR, mTargetTable.createIdmap(mOverlayTable, 0, 0, 0, 0,
                    targetName, targetName, &mData, &mDataSize));
        ASSERT_EQ(NO_ERROR, mTargetTable.add(system_arsc, system_arsc_len));
    }

    virtual void TearDown() {
        free(mData);
    }

    ResTable mTargetTable;
    ResTable mOverlayTable;
    void* mData;
    size_t mDataSize;
};

TEST_F(BagsTest, canLoadIdmap) {
    ASSERT_EQ(NO_ERROR, mTargetTable.add(bags_arsc, bags_arsc_len, mData, mDataSize));
}

TEST_F(BagsTest, overlayOverridesStyleAttribute) {
    ASSERT_EQ(NO_ERROR, mTargetTable.add(bags_arsc, bags_arsc_len, mData, mDataSize));

    ResTable::Theme theme2(mTargetTable);
    ASSERT_EQ(NO_ERROR, theme2.applyStyle(app::R::style::Theme_Two));

    Res_value val;
    ASSERT_GE(theme2.getAttribute(android::R::attr::background, &val), 0);
    ASSERT_EQ(Res_value::TYPE_INT_COLOR_RGB8, val.dataType);
    ASSERT_EQ(uint32_t(0xff0000ff), val.data);
}

TEST_F(BagsTest, overlayCanResolveReferencesToOwnPackage) {
    ASSERT_EQ(NO_ERROR, mTargetTable.add(bags_arsc, bags_arsc_len, mData, mDataSize));

    ResTable::Theme theme2(mTargetTable);
    ASSERT_EQ(NO_ERROR, theme2.applyStyle(app::R::style::Theme_Two));

    Res_value attr;
    ssize_t block = theme2.getAttribute(android::R::attr::foreground, &attr);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_REFERENCE, attr.dataType);
    ASSERT_EQ(uint32_t(bags::R::color::magenta), attr.data);
    Res_value val;
    block = mTargetTable.getResource(attr.data, &val, false);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_INT_COLOR_RGB8, val.dataType);
    ASSERT_EQ(uint32_t(0xffff00ff), val.data);
}

TEST_F(BagsTest, overlayCanReferenceOwnStyle) {
    ASSERT_EQ(NO_ERROR, mTargetTable.add(bags_arsc, bags_arsc_len, mData, mDataSize));

    ResTable::Theme theme3(mTargetTable);
    ASSERT_EQ(NO_ERROR, theme3.applyStyle(app::R::style::Theme_Three));

    Res_value attr;
    ssize_t block = theme3.getAttribute(android::R::attr::foreground, &attr);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_REFERENCE, attr.dataType);
    ASSERT_EQ(uint32_t(bags::R::color::cyan), attr.data);
    Res_value val;
    block = mTargetTable.getResource(attr.data, &val, false);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_INT_COLOR_RGB8, val.dataType);
    ASSERT_EQ(uint32_t(0xff00ffff), val.data);

    // verify that we still get the parent attribute for background
    block = theme3.getAttribute(android::R::attr::background, &attr);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_INT_COLOR_RGB8, attr.dataType);
    ASSERT_EQ(uint32_t(0xffff0000), attr.data);
}

TEST_F(BagsTest, overlaidStyleContainsMissingAttributes) {
    const uint32_t SOME_DIMEN_VALUE = 0x00003001;

    ASSERT_EQ(NO_ERROR, mTargetTable.add(bags_arsc, bags_arsc_len, mData, mDataSize));

    ResTable::Theme theme(mTargetTable);
    ASSERT_EQ(NO_ERROR, theme.applyStyle(app::R::style::Theme_Four));

    // First let's make sure we have the themed style by checking the background attribute
    Res_value attr;
    ssize_t block = theme.getAttribute(android::R::attr::background, &attr);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_INT_COLOR_RGB8, attr.dataType);
    ASSERT_EQ(uint32_t(0xffaabbcc), attr.data);

    // Now check if the someDimen attribute in the parent was merged in correctly since the theme
    // does not contain this attribute in the overlaid style
    block = theme.getAttribute(android::R::attr::some_dimen, &attr);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_DIMENSION, attr.dataType);
    ASSERT_EQ(SOME_DIMEN_VALUE, attr.data);
}

TEST_F(BagsTest, protectedAttributeNotOverlaid) {
    ASSERT_EQ(NO_ERROR, mTargetTable.add(bags_arsc, bags_arsc_len, mData, mDataSize));

    ResTable::Theme theme(mTargetTable);
    ASSERT_EQ(NO_ERROR, theme.applyStyle(app::R::style::Theme_Two));
    Res_value val;
    ASSERT_GE(theme.getAttribute(android::R::attr::windowNoTitle, &val), 0);
    ASSERT_EQ(Res_value::TYPE_INT_BOOLEAN, val.dataType);
    ASSERT_NE(0, val.data);
}

}
