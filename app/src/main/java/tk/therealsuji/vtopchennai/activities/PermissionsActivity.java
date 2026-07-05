package tk.therealsuji.vtopchennai.activities;

import android.Manifest;
import android.app.AlarmManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;

import tk.therealsuji.vtopchennai.R;
import tk.therealsuji.vtopchennai.helpers.SettingsRepository;
import tk.therealsuji.vtopchennai.lockscreen.LockScreenAdminReceiver;

public class PermissionsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_NOTIFICATIONS = 101;

    private TextView tvStatusNotifications, tvStatusOverlay, tvStatusAlarms, tvStatusAdmin;
    private MaterialButton btnNotifications, btnOverlay, btnAlarms, btnAdmin, btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permissions);

        tvStatusNotifications = findViewById(R.id.tv_status_notifications);
        tvStatusOverlay = findViewById(R.id.tv_status_overlay);
        tvStatusAlarms = findViewById(R.id.tv_status_alarms);
        tvStatusAdmin = findViewById(R.id.tv_status_admin);

        btnNotifications = findViewById(R.id.btn_grant_notifications);
        btnOverlay = findViewById(R.id.btn_grant_overlay);
        btnAlarms = findViewById(R.id.btn_grant_alarms);
        btnAdmin = findViewById(R.id.btn_grant_admin);
        btnContinue = findViewById(R.id.btn_continue);

        btnNotifications.setOnClickListener(v -> requestNotifications());
        btnOverlay.setOnClickListener(v -> requestOverlay());
        btnAlarms.setOnClickListener(v -> requestExactAlarms());
        btnAdmin.setOnClickListener(v -> requestDeviceAdmin());

        btnContinue.setOnClickListener(v -> {
            SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(this);
            sharedPreferences.edit().putBoolean("permissions_intro_shown", true).apply();

            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (SettingsRepository.isSignedIn(getApplicationContext())) {
                intent.setClass(PermissionsActivity.this, MainActivity.class);
            } else {
                SettingsRepository.signOut(getApplicationContext());
                intent.setClass(PermissionsActivity.this, LoginActivity.class);
            }
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionsStatus();
    }

    private void updatePermissionsStatus() {
        // 1. Notifications
        boolean hasNotifications = SettingsRepository.hasNotificationPermission(this);
        updateStatus(tvStatusNotifications, btnNotifications, hasNotifications);

        // 2. Overlay
        boolean hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
        updateStatus(tvStatusOverlay, btnOverlay, hasOverlay);

        // 3. Exact Alarms
        boolean hasAlarms = hasExactAlarmPermission();
        updateStatus(tvStatusAlarms, btnAlarms, hasAlarms);

        // 4. Device Admin
        boolean hasAdmin = isDeviceAdminActive();
        updateStatus(tvStatusAdmin, btnAdmin, hasAdmin);
    }

    private void updateStatus(TextView statusText, MaterialButton button, boolean granted) {
        if (granted) {
            statusText.setText("Granted");
            statusText.setTextColor(android.graphics.Color.parseColor("#4CAF50")); // Green
            button.setEnabled(false);
            button.setText("Permission Granted");
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
            button.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        } else {
            statusText.setText("Missing");
            statusText.setTextColor(android.graphics.Color.parseColor("#FF9F0A")); // Orange
            button.setEnabled(true);
            button.setText("Grant Permission");
            button.setStrokeColor(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.m3_sys_color_light_primary)));
            button.setTextColor(getResources().getColor(R.color.m3_sys_color_light_primary));
        }
    }

    private boolean hasExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            return alarmManager != null && alarmManager.canScheduleExactAlarms();
        }
        return true;
    }

    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, LockScreenAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(adminComponent);
    }

    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_NOTIFICATIONS);
        } else {
            Toast.makeText(this, "Notification permission is automatically granted on this Android version.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Overlay permission is automatically granted on this Android version.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestExactAlarms() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "Exact alarm permission is automatically granted on this Android version.", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestDeviceAdmin() {
        ComponentName adminComponent = new ComponentName(this, LockScreenAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allows putting the phone to sleep on lock screen double-tap.");
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied.", Toast.LENGTH_SHORT).show();
            }
            updatePermissionsStatus();
        }
    }
}
