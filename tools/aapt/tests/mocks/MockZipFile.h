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

#ifndef ANDROID_MOCK_ZIP_FILE
#define ANDROID_MOCK_ZIP_FILE

#include "ZipFile.h"
#include "gmock/gmock.h"

class MockZipFile : public android::ZipFile {
public:
    MOCK_CONST_METHOD0(getNumEntries, int());
    MOCK_CONST_METHOD1(getEntryByIndex, android::ZipEntry* (int idx));
};

#endif // ANDROID_MOCK_ZIP_FILE
