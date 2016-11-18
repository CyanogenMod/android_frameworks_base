/*
 * Copyright (C) 2016 The CyanogenMod Project
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

/*
#define PATH_WHITELIST_EXTRA_H \
    "/proc/apid", \
    "/proc/aprf",
*/

// Overload this file in your device specific config if you need
// to add extra whitelisted paths.
// WARNING: Only use this if necessary. Custom inits should be
// checked for leaked file descriptors before even considering
// this.
// In order to add your files, copy the whole file (don't forget the copyright notice!),
// uncomment the #define above and change the paths inside to match your requirements