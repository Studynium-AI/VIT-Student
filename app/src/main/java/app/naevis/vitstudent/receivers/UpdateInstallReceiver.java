package app.naevis.vitstudent.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

public class UpdateInstallReceiver extends BroadcastReceiver {
    private static final String TAG = "UpdateInstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        Log.d(TAG, "Install status received: " + status + " (" + message + ")");

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // This is critical: launch the system confirmation activity
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;

            case PackageInstaller.STATUS_SUCCESS:
                Toast.makeText(context, "App updated successfully!", Toast.LENGTH_SHORT).show();
                break;

            case PackageInstaller.STATUS_FAILURE:
                Toast.makeText(context, "Update failed: " + getReadableErrorMessage(intent, message), Toast.LENGTH_LONG).show();
                break;

            case PackageInstaller.STATUS_FAILURE_ABORTED:
                Toast.makeText(context, "Update cancelled.", Toast.LENGTH_SHORT).show();
                break;

            case PackageInstaller.STATUS_FAILURE_STORAGE:
                Toast.makeText(context, "Update failed: Out of storage space.", Toast.LENGTH_LONG).show();
                break;

            case PackageInstaller.STATUS_FAILURE_CONFLICT:
                Toast.makeText(context, "Update failed: Signature mismatch or package conflict. Please reinstall the app.", Toast.LENGTH_LONG).show();
                break;

            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE:
                Toast.makeText(context, "Update failed: Incompatible version.", Toast.LENGTH_LONG).show();
                break;

            case PackageInstaller.STATUS_FAILURE_INVALID:
                Toast.makeText(context, "Update failed: Invalid APK package.", Toast.LENGTH_LONG).show();
                break;

            default:
                Toast.makeText(context, "Update failed: Installation error.", Toast.LENGTH_LONG).show();
                break;
        }
    }

    private String getReadableErrorMessage(Intent intent, String defaultMsg) {
        int extraStatus = intent.getIntExtra("android.content.pm.extra.LEGACY_STATUS", 0);
        if (extraStatus != 0) {
            return "Legacy error code (" + extraStatus + ")";
        }
        return defaultMsg != null ? defaultMsg : "Unknown error";
    }
}
