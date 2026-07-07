package app.naevis.vitstudent.helpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;

public class UpdateInstaller {
    private static final String TAG = "UpdateInstaller";

    /**
     * Checks if the app has permission to install packages from unknown sources.
     */
    public static boolean canInstall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    /**
     * Redirects the user to the system settings screen to enable unknown app installation.
     */
    public static void requestInstallPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, 1234);
        }
    }

    /**
     * Installs the given APK file using a FileProvider content:// URI and ACTION_VIEW intent.
     * This approach reliably opens the system's package installer dialog on all Android versions.
     */
    public static void installApk(Activity activity, File apkFile) {
        Log.d(TAG, "Installing APK via FileProvider: " + apkFile.getAbsolutePath());

        Uri apkUri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apkFile
        );

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        activity.startActivity(installIntent);
    }
}
