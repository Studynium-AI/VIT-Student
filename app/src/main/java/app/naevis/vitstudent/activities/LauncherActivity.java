package app.naevis.vitstudent.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import app.naevis.vitstudent.helpers.SettingsRepository;

/**
 * A trampoline activity to navigate the user to the right screen
 */
public class LauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (this.getIntent().getExtras() != null) {
            intent.putExtras(this.getIntent().getExtras());
        }

        android.content.SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(this);
        boolean introShown = sharedPreferences.getBoolean("permissions_intro_shown", false);

        if (!introShown) {
            intent.setClass(LauncherActivity.this, PermissionsActivity.class);
        } else if (SettingsRepository.isSignedIn(this.getApplicationContext())) {
            intent.setClass(LauncherActivity.this, MainActivity.class);
        } else {
            SettingsRepository.signOut(this.getApplicationContext());   // Delete old data
            intent.setClass(LauncherActivity.this, LoginActivity.class);
        }

        startActivity(intent);
        finish();
    }
}
