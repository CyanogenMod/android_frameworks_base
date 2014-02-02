/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2014 The CyanogenMod Project
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
#include "data/override/R.h"

#include <gtest/gtest.h>

using namespace android;

namespace {

#include "data/override/override_arsc.h"

TEST(PackageIdOverrideTest, shouldOverridePackageId) {
    const uint32_t pkgIdOverride = 0x42;
    ResTable table;
    ASSERT_EQ(NO_ERROR, table.add(override_arsc, override_arsc_len, NULL, 0, -1, false,
            pkgIdOverride));

    Res_value val;
    // we should not be able to retrieve the resource using the build time package id
    uint32_t resId = override::R::string::string1;
    ssize_t block = table.getResource(resId, &val, false);
    ASSERT_LT(block, 0);

    // now make sure we can access the resource using the runtime package id
    resId = (override::R::string::string1 & 0x00ffffff) | (pkgIdOverride << 24);
    block = table.getResource(resId, &val, false);
    ASSERT_GE(block, 0);
    ASSERT_EQ(Res_value::TYPE_STRING, val.dataType);
    const ResStringPool* pool = table.getTableStringBlock(block);
    ASSERT_TRUE(pool != NULL);
    ASSERT_LT(val.data, pool->size());

    size_t strLen;
    const char16_t* targetStr16 = pool->stringAt(val.data, &strLen);
    ASSERT_TRUE(targetStr16 != NULL);
    ASSERT_EQ(String16("string1"), String16(targetStr16, strLen));
}

}
