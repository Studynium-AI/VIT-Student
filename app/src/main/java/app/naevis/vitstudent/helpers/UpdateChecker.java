package app.naevis.vitstudent.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import app.naevis.vitstudent.BuildConfig;

public class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String GITHUB_RELEASE_API = "https://api.github.com/repos/Studynium-AI/VIT-Student/releases/latest";

    public static class UpdateInfo {
        public final boolean isUpdateAvailable;
        public final String latestVersion;
        public final String releaseNotes;
        public final String downloadUrl;
        public final boolean isForceUpdate;

        public UpdateInfo(boolean isUpdateAvailable, String latestVersion, String releaseNotes, String downloadUrl, boolean isForceUpdate) {
            this.isUpdateAvailable = isUpdateAvailable;
            this.latestVersion = latestVersion;
            this.releaseNotes = releaseNotes;
            this.downloadUrl = downloadUrl;
            this.isForceUpdate = isForceUpdate;
        }
    }

    /**
     * Checks if a new update is available on GitHub.
     *
     * @param context App context
     * @param forceCheck If true, bypasses the "Skip this version" check
     * @return RxJava Single containing the update details
     */
    public static Single<UpdateInfo> checkForUpdates(Context context, boolean forceCheck) {
        return Single.fromCallable(() -> {
            SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
            
            // Check if mock updates are enabled for local testing
            if (prefs.getBoolean("mock_update_enabled", false)) {
                String mockVersion = prefs.getString("mock_update_version", "2.0.0");
                String mockNotes = prefs.getString("mock_update_notes", "### Mock Update\n- Added simulated local updates!\n- Critical security patches included.");
                String mockUrl = prefs.getString("mock_update_url", "https://github.com/Studynium-AI/VIT-Student/releases/download/v1.0.0/app-debug.apk");
                boolean mockCritical = prefs.getBoolean("mock_update_critical", false);
                
                boolean isNewer = isNewerVersion(BuildConfig.VERSION_NAME, mockVersion);
                boolean isSkipped = prefs.getString("skipped_update_version", "").equals(mockVersion);
                
                return new UpdateInfo(isNewer && (forceCheck || !isSkipped), mockVersion, mockNotes, mockUrl, mockCritical);
            }

            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(GITHUB_RELEASE_API)
                    .header("User-Agent", "VIT-Student-App")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "GitHub API returned error: " + response.code());
                    return new UpdateInfo(false, null, null, null, false);
                }

                String bodyString = response.body() != null ? response.body().string() : null;
                if (bodyString == null || bodyString.isEmpty()) {
                    return new UpdateInfo(false, null, null, null, false);
                }

                JSONObject releaseJson = new JSONObject(bodyString);
                String tagName = releaseJson.optString("tag_name", "");
                String releaseNotes = releaseJson.optString("body", "");
                
                // Determine if this is a critical/force update (e.g. title or body contains [critical] or [force])
                String name = releaseJson.optString("name", "").toLowerCase();
                String bodyLower = releaseNotes.toLowerCase();
                boolean isForceUpdate = name.contains("[critical]") || name.contains("[force]") 
                        || bodyLower.contains("[critical]") || bodyLower.contains("[force]");

                // Extract the download URL of the first .apk asset
                String downloadUrl = null;
                JSONArray assets = releaseJson.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }

                if (tagName.isEmpty() || downloadUrl == null) {
                    Log.w(TAG, "No valid tag name or APK asset found in the latest release.");
                    return new UpdateInfo(false, null, null, null, false);
                }

                boolean isNewer = isNewerVersion(BuildConfig.VERSION_NAME, tagName);
                boolean isSkipped = prefs.getString("skipped_update_version", "").equals(tagName);

                return new UpdateInfo(isNewer && (forceCheck || !isSkipped), tagName, releaseNotes, downloadUrl, isForceUpdate);
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch update info from GitHub", e);
                return new UpdateInfo(false, null, null, null, false);
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * Semver comparison logic: returns true if newVersion > currentVersion
     */
    public static boolean isNewerVersion(String currentVersion, String newVersion) {
        if (currentVersion == null || newVersion == null) return false;
        if (currentVersion.startsWith("v")) currentVersion = currentVersion.substring(1);
        if (newVersion.startsWith("v")) newVersion = newVersion.substring(1);

        String[] vals1 = currentVersion.split("\\.");
        String[] vals2 = newVersion.split("\\.");
        
        int i = 0;
        while (i < vals1.length && i < vals2.length && vals1[i].equals(vals2[i])) {
            i++;
        }
        
        if (i < vals1.length && i < vals2.length) {
            try {
                int diff = Integer.valueOf(vals2[i]).compareTo(Integer.valueOf(vals1[i]));
                return diff > 0;
            } catch (NumberFormatException e) {
                return newVersion.compareTo(currentVersion) > 0;
            }
        }
        
        return vals2.length > vals1.length;
    }
}
