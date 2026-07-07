package app.naevis.vitstudent.helpers;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import app.naevis.vitstudent.receivers.UpdateInstallReceiver;

public class UpdateInstaller {
    private static final String TAG = "UpdateInstaller";
    private static final String ACTION_INSTALL_STATUS = "app.naevis.vitstudent.ACTION_INSTALL_STATUS";

    /**
     * Checks if the app has permission to install packages from unknown sources.
     */
    public static boolean checkInstallPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    /**
     * Redirects the user to the system settings screen to enable unknown app installation.
     */
    public static void requestInstallPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        }
    }

    /**
     * Installs the given APK file using PackageInstaller.Session.
     */
    public static void installApk(Context context, File apkFile) throws Exception {
        Log.d(TAG, "Starting PackageInstaller session for: " + apkFile.getAbsolutePath());
        
        Context appContext = context.getApplicationContext();
        PackageInstaller packageInstaller = appContext.getPackageManager().getPackageInstaller();
        
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(appContext.getPackageName());
        
        int sessionId;
        try {
            sessionId = packageInstaller.createSession(params);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create PackageInstaller session", e);
            throw e;
        }

        try (PackageInstaller.Session session = packageInstaller.openSession(sessionId)) {
            try (OutputStream out = session.openWrite("package_install_session", 0, -1);
                 InputStream in = new FileInputStream(apkFile)) {
                byte[] buffer = new byte[65536];
                int length;
                while ((length = in.read(buffer)) != -1) {
                    out.write(buffer, 0, length);
                }
                session.fsync(out);
            }

            // Create Intent to broadcast status
            Intent intent = new Intent(appContext, UpdateInstallReceiver.class);
            intent.setAction(ACTION_INSTALL_STATUS);
            intent.setPackage(appContext.getPackageName());

            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pendingFlags |= PendingIntent.FLAG_MUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    appContext,
                    sessionId,
                    intent,
                    pendingFlags
            );

            session.commit(pendingIntent.getIntentSender());
            Log.d(TAG, "PackageInstaller session committed successfully.");
        }
    }
}
