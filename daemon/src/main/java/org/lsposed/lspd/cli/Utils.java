package org.lsposed.lspd.cli;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.RemoteException;

import org.lsposed.lspd.ICLIService;
import org.lsposed.lspd.models.Application;
import org.lsposed.lspd.service.PackageService;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipFile;

public class Utils {

    public static final String CMDNAME = "cli";

    public enum ERRCODES {
        NOERROR,
        USAGE,
        EMPTY_SCOPE,
        ENABLE_DISABLE,
        SET_SCOPE,
        LS_SCOPE,
        NO_DAEMON,
        REMOTE_ERROR
    }

    private static HashMap<String,PackageInfo> packagesMap;

    private static final int MATCH_ANY_USER = 0x00400000;
    private static final int MATCH_ALL_FLAGS = PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE | PackageManager.MATCH_UNINSTALLED_PACKAGES | MATCH_ANY_USER;


    private static void initPackagesMap(ICLIService managerService) throws RemoteException {
        var packages =
                managerService.getInstalledPackagesFromAllUsers(PackageManager.GET_META_DATA | PackageManager.MATCH_UNINSTALLED_PACKAGES, true).getList();
        packagesMap = new HashMap<>();
        for (var packageInfo: packages) {
            int userid = packageInfo.applicationInfo.uid / 100000;
            packagesMap.put(packageInfo.packageName + "|" + userid, packageInfo);

            if ("android".equals(packageInfo.packageName)) {
                var p = Parcel.obtain();
                packageInfo.writeToParcel(p, 0);
                p.setDataPosition(0);
                PackageInfo system = PackageInfo.CREATOR.createFromParcel(p);
                system.packageName = "system";
                system.applicationInfo.packageName = system.packageName;
                packagesMap.put(system.packageName + "|" + userid, system);
            }
        }
    }

    public static boolean validPackageNameAndUserId(ICLIService managerService, String packageName, int userId) throws RemoteException {
        if (packagesMap == null) {
            initPackagesMap(managerService);
        }

        return packagesMap.containsKey(packageName + "|" + userId);
    }

    public static boolean checkPackageInScope(String sPackageName, List<Application> lstScope) {
        for (var app : lstScope) {
            if (app.packageName.equals(sPackageName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean checkPackageModule(String moduleName, List<Application> lstScope) {
        if (!checkPackageInScope(moduleName, lstScope)) {
            Application app = new Application();
            app.packageName = moduleName;
            app.userId = 0;
            lstScope.add(app);
            return true;
        }
        return false;
    }

    private static boolean isModernModule(ApplicationInfo info) {
        String[] apks;
        if (info.splitSourceDirs != null) {
            apks = Arrays.copyOf(info.splitSourceDirs, info.splitSourceDirs.length + 1);
            apks[info.splitSourceDirs.length] = info.sourceDir;
        } else apks = new String[]{info.sourceDir};
        boolean java_initFound = false;
        for (var apk : apks) {
            try {
                ZipFile zip = new ZipFile(apk);
                if (zip.getEntry("META-INF/xposed/java_init.list") != null) {
                    java_initFound = true;
                    zip.close();
                    break;
                }
                else {
                    zip.close();
                }
            } catch (IOException ignored) {
            }
        }
        return java_initFound;
    }

    private static boolean isLegacyModule(ApplicationInfo info) {
        return info.metaData != null && info.metaData.containsKey("xposedminversion");
    }

    public static boolean isValidXposedModule(String sPackageName) throws RemoteException {
        var appInfo = PackageService.getApplicationInfo(sPackageName, PackageManager.GET_META_DATA | MATCH_ALL_FLAGS, 0);
        return isValidXposedModule(appInfo);
    }

    public static boolean isValidXposedModule(ApplicationInfo appInfo) {
        return isModernModule(appInfo) || isLegacyModule(appInfo);
    }
}
