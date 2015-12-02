//
// Copyright 2006 The Android Open Source Project
// This code has been modified.  Portions copyright (C) 2010, T-Mobile USA, Inc.
//
// State bundle.  Used to pass around stuff like command-line args.
//
#ifndef __BUNDLE_H
#define __BUNDLE_H

#include <stdlib.h>
#include <utils/Log.h>
#include <utils/threads.h>
#include <utils/List.h>
#include <utils/Errors.h>
#include <utils/String8.h>
#include <utils/Vector.h>

#include "SdkConstants.h"

/*
 * Things we can do.
 */
typedef enum Command {
    kCommandUnknown = 0,
    kCommandVersion,
    kCommandList,
    kCommandDump,
    kCommandAdd,
    kCommandRemove,
    kCommandPackage,
    kCommandCrunch,
    kCommandSingleCrunch,
    kCommandDaemon
} Command;

/*
 * Pseudolocalization methods
 */
typedef enum PseudolocalizationMethod {
    NO_PSEUDOLOCALIZATION = 0,
    PSEUDO_ACCENTED,
    PSEUDO_BIDI,
} PseudolocalizationMethod;

/*
 * Bundle of goodies, including everything specified on the command line.
 */
class Bundle {
public:
    Bundle(void)
        : mCmd(kCommandUnknown), mVerbose(false), mAndroidList(false),
          mForce(false), mGrayscaleTolerance(0), mMakePackageDirs(false),
          mUpdate(false), mExtending(false), mExtendedPackageId(0),
          mRequireLocalization(false), mPseudolocalize(NO_PSEUDOLOCALIZATION),
          mWantUTF16(false), mValues(false), mIncludeMetaData(false),
          mCompressionMethod(0), mJunkPath(false), mOutputAPKFile(NULL),
          mManifestPackageNameOverride(NULL), mInstrumentationPackageNameOverride(NULL),
          mAutoAddOverlay(false), mGenDependencies(false), mNoVersionVectors(false),
          mCrunchedOutputDir(NULL), mProguardFile(NULL),
          mAndroidManifestFile(NULL), mPublicOutputFile(NULL),
          mRClassDir(NULL), mResourceIntermediatesDir(NULL), mManifestMinSdkVersion(NULL),
          mMinSdkVersion(NULL), mTargetSdkVersion(NULL), mMaxSdkVersion(NULL),
          mVersionCode(NULL), mVersionName(NULL), mReplaceVersion(false), mCustomPackage(NULL),
          mExtraPackages(NULL), mMaxResVersion(NULL), mDebugMode(false), mNonConstantId(false),
          mSkipSymbolsWithoutDefaultLocalization(false),
          mProduct(NULL), mUseCrunchCache(false), mErrorOnFailedInsert(false),
          mErrorOnMissingConfigEntry(false), mOutputTextSymbols(NULL),
          mSingleCrunchInputFile(NULL), mSingleCrunchOutputFile(NULL),
          mOutputResourcesApkFile(NULL),
          mInternalZipPath(NULL), mInputAPKFile(NULL),
          mBuildSharedLibrary(false),
          mArgc(0), mArgv(NULL)
        {}
    ~Bundle(void) {}

    /*
     * Set the command value.  Returns "false" if it was previously set.
     */
    Command getCommand(void) const { return mCmd; }
    void setCommand(Command cmd) { mCmd = cmd; }

    /*
     * Command modifiers.  Not all modifiers are appropriate for all
     * commands.
     */
    bool getVerbose(void) const { return mVerbose; }
    void setVerbose(bool val) { mVerbose = val; }
    bool getAndroidList(void) const { return mAndroidList; }
    void setAndroidList(bool val) { mAndroidList = val; }
    bool getForce(void) const { return mForce; }
    void setForce(bool val) { mForce = val; }
    void setGrayscaleTolerance(int val) { mGrayscaleTolerance = val; }
    int  getGrayscaleTolerance() const { return mGrayscaleTolerance; }
    bool getMakePackageDirs(void) const { return mMakePackageDirs; }
    void setMakePackageDirs(bool val) { mMakePackageDirs = val; }
    bool getUpdate(void) const { return mUpdate; }
    void setUpdate(bool val) { mUpdate = val; }
    bool getExtending(void) const { return mExtending; }
    void setExtending(bool val) { mExtending = val; }
    int getExtendedPackageId(void) const { return mExtendedPackageId; }
    void setExtendedPackageId(int val) { mExtendedPackageId = val; }
    bool getRequireLocalization(void) const { return mRequireLocalization; }
    void setRequireLocalization(bool val) { mRequireLocalization = val; }
    short getPseudolocalize(void) const { return mPseudolocalize; }
    void setPseudolocalize(short val) { mPseudolocalize = val; }
    void setWantUTF16(bool val) { mWantUTF16 = val; }
    bool getValues(void) const { return mValues; }
    void setValues(bool val) { mValues = val; }
    bool getIncludeMetaData(void) const { return mIncludeMetaData; }
    void setIncludeMetaData(bool val) { mIncludeMetaData = val; }
    int getCompressionMethod(void) const { return mCompressionMethod; }
    void setCompressionMethod(int val) { mCompressionMethod = val; }
    bool getJunkPath(void) const { return mJunkPath; }
    void setJunkPath(bool val) { mJunkPath = val; }
    const char* getOutputAPKFile() const { return mOutputAPKFile; }
    void setOutputAPKFile(const char* val) { mOutputAPKFile = val; }
    const char* getOutputResApk() { return mOutputResourcesApkFile; }
    const char* getInputAPKFile() { return mInputAPKFile; }
    void setInputAPKFile(const char* val) { mInputAPKFile = val; }
    void setOutputResApk(const char* val) { mOutputResourcesApkFile = val; }
    const char* getManifestPackageNameOverride() const { return mManifestPackageNameOverride; }
    void setManifestPackageNameOverride(const char * val) { mManifestPackageNameOverride = val; }
    const char* getInstrumentationPackageNameOverride() const { return mInstrumentationPackageNameOverride; }
    void setInstrumentationPackageNameOverride(const char * val) { mInstrumentationPackageNameOverride = val; }
    bool getAutoAddOverlay() { return mAutoAddOverlay; }
    void setAutoAddOverlay(bool val) { mAutoAddOverlay = val; }
    bool getGenDependencies() { return mGenDependencies; }
    void setGenDependencies(bool val) { mGenDependencies = val; }
    bool getErrorOnFailedInsert() { return mErrorOnFailedInsert; }
    void setErrorOnFailedInsert(bool val) { mErrorOnFailedInsert = val; }
    bool getErrorOnMissingConfigEntry() { return mErrorOnMissingConfigEntry; }
    void setErrorOnMissingConfigEntry(bool val) { mErrorOnMissingConfigEntry = val; }
    const android::String8& getPlatformBuildVersionCode() { return mPlatformVersionCode; }
    void setPlatformBuildVersionCode(const android::String8& code) { mPlatformVersionCode = code; }
    const android::String8& getPlatformBuildVersionName() { return mPlatformVersionName; }
    void setPlatformBuildVersionName(const android::String8& name) { mPlatformVersionName = name; }

    bool getUTF16StringsOption() {
        return mWantUTF16 || !isMinSdkAtLeast(SDK_FROYO);
    }

    /*
     * Input options.
     */
    const android::Vector<const char*>& getAssetSourceDirs() const { return mAssetSourceDirs; }
    void addAssetSourceDir(const char* dir) { mAssetSourceDirs.insertAt(dir,0); }
    const char* getCrunchedOutputDir() const { return mCrunchedOutputDir; }
    void setCrunchedOutputDir(const char* dir) { mCrunchedOutputDir = dir; }
    const char* getProguardFile() const { return mProguardFile; }
    void setProguardFile(const char* file) { mProguardFile = file; }
    const android::Vector<const char*>& getResourceSourceDirs() const { return mResourceSourceDirs; }
    void addResourceSourceDir(const char* dir) { mResourceSourceDirs.insertAt(dir,0); }
    const char* getAndroidManifestFile() const { return mAndroidManifestFile; }
    void setAndroidManifestFile(const char* file) { mAndroidManifestFile = file; }
    const char* getPublicOutputFile() const { return mPublicOutputFile; }
    void setPublicOutputFile(const char* file) { mPublicOutputFile = file; }
    const char* getRClassDir() const { return mRClassDir; }
    void setRClassDir(const char* dir) { mRClassDir = dir; }
    const android::String8& getConfigurations() const { return mConfigurations; }
    void addConfigurations(const char* val) { if (mConfigurations.size() > 0) { mConfigurations.append(","); mConfigurations.append(val); } else { mConfigurations = val; } }
    const android::String8& getPreferredDensity() const { return mPreferredDensity; }
    void setPreferredDensity(const char* val) { mPreferredDensity = val; }
    void addSplitConfigurations(const char* val) { mPartialConfigurations.add(android::String8(val)); }
    const android::Vector<android::String8>& getSplitConfigurations() const { return mPartialConfigurations; }
    const char* getResourceIntermediatesDir() const { return mResourceIntermediatesDir; }
    void setResourceIntermediatesDir(const char* dir) { mResourceIntermediatesDir = dir; }
    const android::Vector<android::String8>& getPackageIncludes() const { return mPackageIncludes; }
    void addPackageInclude(const char* file) { mPackageIncludes.add(android::String8(file)); }
    const android::Vector<const char*>& getJarFiles() const { return mJarFiles; }
    void addJarFile(const char* file) { mJarFiles.add(file); }
    const android::Vector<const char*>& getNoCompressExtensions() const { return mNoCompressExtensions; }
    void addNoCompressExtension(const char* ext) { mNoCompressExtensions.add(ext); }
    void setFeatureOfPackage(const char* str) { mFeatureOfPackage = str; }
    const android::String8& getFeatureOfPackage() const { return mFeatureOfPackage; }
    void setFeatureAfterPackage(const char* str) { mFeatureAfterPackage = str; }
    const android::String8& getFeatureAfterPackage() const { return mFeatureAfterPackage; }

    const char*  getManifestMinSdkVersion() const { return mManifestMinSdkVersion; }
    void setManifestMinSdkVersion(const char*  val) { mManifestMinSdkVersion = val; }
    const char*  getMinSdkVersion() const { return mMinSdkVersion; }
    void setMinSdkVersion(const char*  val) { mMinSdkVersion = val; }
    const char*  getTargetSdkVersion() const { return mTargetSdkVersion; }
    void setTargetSdkVersion(const char*  val) { mTargetSdkVersion = val; }
    const char*  getMaxSdkVersion() const { return mMaxSdkVersion; }
    void setMaxSdkVersion(const char*  val) { mMaxSdkVersion = val; }
    const char*  getVersionCode() const { return mVersionCode; }
    void setVersionCode(const char*  val) { mVersionCode = val; }
    const char* getVersionName() const { return mVersionName; }
    void setVersionName(const char* val) { mVersionName = val; }
    bool getReplaceVersion() { return mReplaceVersion; }
    void setReplaceVersion(bool val) { mReplaceVersion = val; }
    const android::String8& getRevisionCode() { return mRevisionCode; }
    void setRevisionCode(const char* val) { mRevisionCode = android::String8(val); }
    const char* getCustomPackage() const { return mCustomPackage; }
    void setCustomPackage(const char* val) { mCustomPackage = val; }
    const char* getExtraPackages() const { return mExtraPackages; }
    void setExtraPackages(const char* val) { mExtraPackages = val; }
    const char* getMaxResVersion() const { return mMaxResVersion; }
    void setMaxResVersion(const char * val) { mMaxResVersion = val; }
    bool getDebugMode() const { return mDebugMode; }
    void setDebugMode(bool val) { mDebugMode = val; }
    bool getNonConstantId() const { return mNonConstantId; }
    void setNonConstantId(bool val) { mNonConstantId = val; }
    bool getSkipSymbolsWithoutDefaultLocalization() const { return mSkipSymbolsWithoutDefaultLocalization; }
    void setSkipSymbolsWithoutDefaultLocalization(bool val) { mSkipSymbolsWithoutDefaultLocalization = val; }
    const char* getProduct() const { return mProduct; }
    void setProduct(const char * val) { mProduct = val; }
    void setUseCrunchCache(bool val) { mUseCrunchCache = val; }
    bool getUseCrunchCache() const { return mUseCrunchCache; }
    const char* getOutputTextSymbols() const { return mOutputTextSymbols; }
    void setOutputTextSymbols(const char* val) { mOutputTextSymbols = val; }
    const char* getSingleCrunchInputFile() const { return mSingleCrunchInputFile; }
    void setSingleCrunchInputFile(const char* val) { mSingleCrunchInputFile = val; }
    const char* getSingleCrunchOutputFile() const { return mSingleCrunchOutputFile; }
    void setSingleCrunchOutputFile(const char* val) { mSingleCrunchOutputFile = val; }
    void setInternalZipPath(const char* val) { mInternalZipPath = val; }
    const char* getInternalZipPath() const { return mInternalZipPath; }
    bool getBuildSharedLibrary() const { return mBuildSharedLibrary; }
    void setBuildSharedLibrary(bool val) { mBuildSharedLibrary = val; }
    void setNoVersionVectors(bool val) { mNoVersionVectors = val; }
    bool getNoVersionVectors() const { return mNoVersionVectors; }

    /*
     * Set and get the file specification.
     *
     * Note this does NOT make a copy of argv.
     */
    void setFileSpec(char* const argv[], int argc) {
        mArgc = argc;
        mArgv = argv;
    }
    int getFileSpecCount(void) const { return mArgc; }
    const char* getFileSpecEntry(int idx) const { return mArgv[idx]; }
    void eatArgs(int n) {
        if (n > mArgc) n = mArgc;
        mArgv += n;
        mArgc -= n;
    }

#if 0
    /*
     * Package count.  Nothing to do with anything else here; this is
     * just a convenient place to stuff it so we don't have to pass it
     * around everywhere.
     */
    int getPackageCount(void) const { return mPackageCount; }
    void setPackageCount(int val) { mPackageCount = val; }
#endif

    /* Certain features may only be available on a specific SDK level or
     * above. SDK levels that have a non-numeric identifier are assumed
     * to be newer than any SDK level that has a number designated.
     */
    bool isMinSdkAtLeast(int desired) {
        /* If the application specifies a minSdkVersion in the manifest
         * then use that. Otherwise, check what the user specified on
         * the command line. If neither, it's not available since
         * the minimum SDK version is assumed to be 1.
         */
        const char *minVer;
        if (mManifestMinSdkVersion != NULL) {
            minVer = mManifestMinSdkVersion;
        } else if (mMinSdkVersion != NULL) {
            minVer = mMinSdkVersion;
        } else {
            return false;
        }

        char *end;
        int minSdkNum = (int)strtol(minVer, &end, 0);
        if (*end == '\0') {
            if (minSdkNum < desired) {
                return false;
            }
        }
        return true;
    }

private:
    /* commands & modifiers */
    Command     mCmd;
    bool        mVerbose;
    bool        mAndroidList;
    bool        mForce;
    int         mGrayscaleTolerance;
    bool        mMakePackageDirs;
    bool        mUpdate;
    bool        mExtending;
    int         mExtendedPackageId;
    bool        mRequireLocalization;
    short       mPseudolocalize;
    bool        mWantUTF16;
    bool        mValues;
    bool        mIncludeMetaData;
    int         mCompressionMethod;
    bool        mJunkPath;
    const char* mOutputAPKFile;
    const char* mManifestPackageNameOverride;
    const char* mInstrumentationPackageNameOverride;
    bool        mAutoAddOverlay;
    bool        mGenDependencies;
    bool        mNoVersionVectors;
    const char* mCrunchedOutputDir;
    const char* mProguardFile;
    const char* mAndroidManifestFile;
    const char* mPublicOutputFile;
    const char* mRClassDir;
    const char* mResourceIntermediatesDir;
    android::String8 mConfigurations;
    android::String8 mPreferredDensity;
    android::Vector<android::String8> mPartialConfigurations;
    android::Vector<android::String8> mPackageIncludes;
    android::Vector<const char*> mJarFiles;
    android::Vector<const char*> mNoCompressExtensions;
    android::Vector<const char*> mAssetSourceDirs;
    android::Vector<const char*> mResourceSourceDirs;

    android::String8 mFeatureOfPackage;
    android::String8 mFeatureAfterPackage;
    android::String8 mRevisionCode;
    const char* mManifestMinSdkVersion;
    const char* mMinSdkVersion;
    const char* mTargetSdkVersion;
    const char* mMaxSdkVersion;
    const char* mVersionCode;
    const char* mVersionName;
    bool        mReplaceVersion;
    const char* mCustomPackage;
    const char* mExtraPackages;
    const char* mMaxResVersion;
    bool        mDebugMode;
    bool        mNonConstantId;
    bool        mSkipSymbolsWithoutDefaultLocalization;
    const char* mProduct;
    bool        mUseCrunchCache;
    bool        mErrorOnFailedInsert;
    bool        mErrorOnMissingConfigEntry;
    const char* mOutputTextSymbols;
    const char* mSingleCrunchInputFile;
    const char* mSingleCrunchOutputFile;
    const char* mOutputResourcesApkFile;
    const char* mInternalZipPath;
    const char* mInputAPKFile;
    bool        mBuildSharedLibrary;
    android::String8 mPlatformVersionCode;
    android::String8 mPlatformVersionName;

    /* file specification */
    int         mArgc;
    char* const* mArgv;

#if 0
    /* misc stuff */
    int         mPackageCount;
#endif

};

#endif // __BUNDLE_H
