package tk.therealsuji.vtopchennai;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import tk.therealsuji.vtopchennai.helpers.SettingsRepository;

public class VTOP extends Application {
    @Override
    public void onCreate() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                java.io.File file = new java.io.File(getExternalFilesDir(null), "crash.log");
                java.io.FileWriter writer = new java.io.FileWriter(file);
                java.io.PrintWriter printWriter = new java.io.PrintWriter(writer);
                throwable.printStackTrace(printWriter);
                printWriter.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(1);
            }
        });

        DynamicColors.applyToActivitiesIfAvailable(this);

        int theme = SettingsRepository.getTheme(this);
        if (theme == SettingsRepository.THEME_DAY) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (theme == SettingsRepository.THEME_NIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        super.onCreate();
    }
}
