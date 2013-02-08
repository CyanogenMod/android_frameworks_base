
package com.android.server.privacy;

import android.app.AppGlobals;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Binder;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import dalvik.system.DexClassLoader;

import java.io.File;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * provides an SPI interface to allow an application with write access to
 * <code>/data/privacy/PrivacyManager.apk</code> to install an implementation
 * for PrivacyManagerSPI. Allows hot deployment of new
 * <code>PrivacyManager.apk</code> files.
 * 
 * @hide
 */
public class PrivacyManager extends android.privacy.IPrivacyManager.Stub {

    public final static String SERVICE_NAME = PrivacyManager.class.getSimpleName();
    public final static String TAG = SERVICE_NAME;

    private static final String PRIVACY_MANAGER_SPI_MAIN_CLASS = "PrivacyManager-SPI";
    private static final String SPI_PREFIX = "PrivacyManager.";
    private static final String SPI_APK = "/data/privacy/PrivacyManager.apk";

    private final Context m_context;
    private PrivacyManagerSPI m_spi;
    private FileObserver m_observer;

    public PrivacyManager(Context context) {
        m_context = context;
    }

    public void systemReady() {
        m_observer = new FileObserver(new File(SPI_APK).getParent(), FileObserver.CLOSE_WRITE) {
            @Override
            public void onEvent(int event, String path) {
                Slog.d(TAG, "event=" + event);
                loadSPI();
            }
        };
        m_observer.startWatching();
        loadSPI();
    }

    /**
     * expose findResource with public access because we have to load a manifest
     * from an apk file
     */
    private static class MyDexClassLoader extends DexClassLoader {
        public MyDexClassLoader(String dexPath, String optimizedDirectory, String libraryPath,
                ClassLoader parent) {
            super(dexPath, optimizedDirectory, libraryPath, parent);
        }

        @Override
        public URL findResource(String name) {
            return super.findResource(name);
        }
    }

    /*
     * update m_spi create new empty dir in /cache/spi/ + SPI_PREFIX + lastMod/
     * otherwise the dynamic loading of new dex-files fails because the cache is
     * overwritten
     */
    private void loadSPI() {
        try {
            String cache = m_context.getCacheDir().getAbsolutePath();

            final File dex_file = new File(SPI_APK);
            long dex_lastmod = dex_file.lastModified();
            Slog.d(TAG, "cache=" + cache + " dex last mod " + dex_lastmod);

            final File cache_dir = new File(cache);
            if (!cache_dir.isDirectory()) {
                Slog.e(TAG, cache_dir.toString() + " is not a directory");
                return;
            }
            final File spi_cache_dir = new File(cache_dir, "spi");
            if (!spi_cache_dir.isDirectory()) {
                spi_cache_dir.mkdir();
            }
            if (!spi_cache_dir.isDirectory()) {
                Slog.e(TAG, "can't create spi cache dir " + spi_cache_dir);
                return;
            }

            final File tmp_cache_dir = new File(spi_cache_dir, SPI_PREFIX + dex_lastmod);

            if (!tmp_cache_dir.isDirectory()) {
                tmp_cache_dir.mkdir();
            }
            if (!tmp_cache_dir.isDirectory()) {
                Slog.e(TAG, "can't create tmp spi cache dir " + tmp_cache_dir);
                return;
            }

            MyDexClassLoader cl = new MyDexClassLoader(SPI_APK, tmp_cache_dir.getAbsolutePath(),
                    null, ClassLoader.getSystemClassLoader());
            URL res = cl.findResource(JarFile.MANIFEST_NAME);
            if (res == null) {
                Slog.e(TAG, "can't find manifest");
                return;
            }
            InputStream manifest_stream = res.openStream();
            Manifest mani = new Manifest(manifest_stream);
            manifest_stream.close();

            String spi_cls = mani.getMainAttributes().getValue(PRIVACY_MANAGER_SPI_MAIN_CLASS);
            if (spi_cls == null) {
                throw new IllegalStateException("spi name missing in manifest");
            }
            Class<?> pmcls = cl.loadClass(spi_cls);
            Constructor<?> pmc = pmcls.getConstructor(Context.class);
            m_spi = (PrivacyManagerSPI) pmc.newInstance(m_context);
            Slog.d(TAG, "new PrivacyManagerSPI installed " + m_spi);

            // find all old tmp dirs
            for (File old : spi_cache_dir.listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String filename) {
                    return filename.startsWith(SPI_PREFIX);
                }
            })) {
                if (old.equals(tmp_cache_dir))
                    continue; // skip
                Slog.d(TAG, "delete " + old);
                deleteRecursive(old);
            }

        } catch (Throwable e) {
            Slog.e(TAG, "error setting up PrivacyManagerSPI", e);
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory())
            for (File child : f.listFiles())
                deleteRecursive(child);

        f.delete();
    }

    @Override
    public IBinder getPrivacySubstituteService(String service) throws RemoteException {
        if (service == null || m_spi == null)
            return null;

        try {
            String[] packages = AppGlobals.getPackageManager().getPackagesForUid(
                    Binder.getCallingUid());
            if (packages == null)
                return null;
            for (String p : packages) {
                IBinder res = m_spi.getPrivacySubstituteService(service, p);
                if (res != null)
                    return res;
            }
        } catch (Throwable t) {
            Slog.e(TAG, "error in getPrivacyStub", t);
        }

        return null;
    }

    @Override
    public PackageInfo filterPackageInfo(PackageInfo info, String packageName, int flags)
            throws RemoteException {

        if (info == null || packageName == null || m_spi == null)
            return info;

        return m_spi.filterPackageInfo(info, packageName, flags);
    }

    @Override
    public int[] filterPackageGids(int[] gids, String packageName) throws RemoteException {
        if (gids == null || packageName == null || m_spi == null)
            return gids;
        return m_spi.filterPackageGids(gids, packageName);
    }

    @Override
    public boolean filterGrantedPermission(String permName, String packageName)
            throws RemoteException {
        if (permName == null || packageName == null || m_spi == null)
            return true;
        return m_spi.filterGrantedPermission(permName, packageName);
    }

    @Override
    public IBinder getConfig() throws RemoteException {
        m_context.enforceCallingPermission("android.permission.MANAGE_PRIVACY_SETTINGS",
                "PrivacyManager Config");
        return m_spi.getConfig();
    }
}
