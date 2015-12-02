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
#include "data/cm/basic/R.h"

#include <gtest/gtest.h>

using namespace android;

namespace {

/**
 * Include a binary resource table.
 *
 * Package: com.android.test.basic
 */
#include "data/cm/basic/basic_arsc.h"

/**
 * Include a binary resource table.
 * This table is an overlay.
 *
 * Package: com.android.test.basic
 */
#include "data/cm/overlay/overlay_arsc.h"

enum { MAY_NOT_BE_BAG = false };

class IdmapTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        ASSERT_EQ(NO_ERROR, mTargetTable.add(basic_arsc, basic_arsc_len));
        ASSERT_EQ(NO_ERROR, mOverlayTable.add(overlay_arsc, overlay_arsc_len));
        char targetName[256] = "com.android.test.basic";
        ASSERT_EQ(NO_ERROR, mTargetTable.createIdmap(mOverlayTable, 0, 0, 0, 0,
                    targetName, targetName, &mData, &mDataSize));
    }

    virtual void TearDown() {
        free(mData);
    }

    ResTable mTargetTable;
    ResTable mOverlayTable;
    void* mData;
    size_t mDataSize;
};

TEST_F(IdmapTest, canLoadIdmap) {
    ASSERT_EQ(NO_ERROR, mTargetTable.add(overlay_arsc, overlay_arsc_len, mData, mDataSize));
}

TEST_F(IdmapTest, overlayOverridesResourceValue) {
    Res_value val;
    ssize_t block = mTargetTable.getResource(base::R::dimen::dimen1, &val, false);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_DIMENSION, val.dataType);
    ASSERT_NE(val.data, 0);

    ASSERT_EQ(NO_ERROR, mTargetTable.add(overlay_arsc, overlay_arsc_len, mData, mDataSize));

    Res_value newVal;
    ssize_t newBlock = mTargetTable.getResource(base::R::dimen::dimen1, &newVal, false);
    ASSERT_GE(newBlock, 0);
    ASSERT_NE(block, newBlock);
    ASSERT_EQ(Res_value::TYPE_DIMENSION, newVal.dataType);
    ASSERT_NE(val.data, newVal.data);
}

TEST_F(IdmapTest, overlaidResourceHasSameName) {
    ASSERT_EQ(NO_ERROR, mTargetTable.add(overlay_arsc, overlay_arsc_len, mData, mDataSize));

    ResTable::resource_name resName;
    ASSERT_TRUE(mTargetTable.getResourceName(base::R::drawable::drawable1, false, &resName));

    ASSERT_TRUE(resName.package != NULL);
    ASSERT_TRUE(resName.type != NULL);
    ASSERT_TRUE(resName.name != NULL);

    EXPECT_EQ(String16("com.android.test.basic"), String16(resName.package, resName.packageLen));
    EXPECT_EQ(String16("drawable"), String16(resName.type, resName.typeLen));
    EXPECT_EQ(String16("drawable1"), String16(resName.name, resName.nameLen));
}

TEST_F(IdmapTest, overlayDoesNotOverlayStringResource) {
    Res_value val;
    ssize_t block = mTargetTable.getResource(base::R::string::test2, &val, false);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_STRING, val.dataType);
    const ResStringPool* pool = mTargetTable.getTableStringBlock(block);
    ASSERT_TRUE(pool != NULL);
    ASSERT_LT(val.data, pool->size());

    size_t strLen;
    const char16_t* targetStr16 = pool->stringAt(val.data, &strLen);
    ASSERT_TRUE(targetStr16 != NULL);
    ASSERT_EQ(String16("test2"), String16(targetStr16, strLen));

    ASSERT_EQ(NO_ERROR, mTargetTable.add(overlay_arsc, overlay_arsc_len, mData, mDataSize));

    ssize_t newBlock = mTargetTable.getResource(base::R::string::test2, &val, false);
    ASSERT_GE(newBlock, 0);
    ASSERT_EQ(block, newBlock);
    // the above check should be enough but just to be sure we'll check the string
    ASSERT_EQ(Res_value::TYPE_STRING, val.dataType);
    pool = mTargetTable.getTableStringBlock(newBlock);
    ASSERT_TRUE(pool != NULL);
    ASSERT_LT(val.data, pool->size());

    targetStr16 = pool->stringAt(val.data, &strLen);
    ASSERT_TRUE(targetStr16 != NULL);
    ASSERT_EQ(String16("test2"), String16(targetStr16, strLen));
}

} // namespace
