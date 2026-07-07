package app.naevis.vitstudent.helpers;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

public class UpdateDownloader {
    private static final String TAG = "UpdateDownloader";

    public interface DownloadListener {
        void onProgress(int progressPercent);
        void onSuccess(File downloadedFile);
        void onFailure(String errorMessage);
    }

    private final Context context;
    private final DownloadManager downloadManager;
    private long downloadId = -1;
    private DownloadListener listener;
    private Handler progressHandler;
    private Runnable progressRunnable;
    private BroadcastReceiver downloadCompleteReceiver;
    private File destinationFile;

    public UpdateDownloader(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) this.context.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public void downloadApk(String url, String versionName, DownloadListener listener) {
        this.listener = listener;

        // Prepare destination file in external cache directory
        File cacheDir = context.getExternalCacheDir();
        if (cacheDir == null) {
            cacheDir = context.getCacheDir();
        }
        destinationFile = new File(cacheDir, "VIT_Student_" + versionName + ".apk");
        if (destinationFile.exists()) {
            destinationFile.delete();
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                .setTitle("VIT Student Update " + versionName)
                .setDescription("Downloading latest update...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(destinationFile));

        try {
            downloadId = downloadManager.enqueue(request);
        } catch (Exception e) {
            Log.e(TAG, "Failed to enqueue download request", e);
            if (listener != null) {
                listener.onFailure("Failed to start download: " + e.getMessage());
            }
            return;
        }

        registerCompleteReceiver();
        startProgressPolling();
    }

    private void startProgressPolling() {
        progressHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (downloadId == -1) return;

                DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
                try (Cursor cursor = downloadManager.query(query)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int bytesDownloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                        int bytesTotalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);

                        if (bytesDownloadedIdx != -1 && bytesTotalIdx != -1 && statusIdx != -1) {
                            int bytesDownloaded = cursor.getInt(bytesDownloadedIdx);
                            int bytesTotal = cursor.getInt(bytesTotalIdx);
                            int status = cursor.getInt(statusIdx);

                            if (status == DownloadManager.STATUS_FAILED) {
                                int reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON);
                                int reason = reasonIdx != -1 ? cursor.getInt(reasonIdx) : -1;
                                handleFailure("Download failed with code: " + reason);
                                return;
                            }

                            if (bytesTotal > 0) {
                                int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                                if (listener != null) {
                                    listener.onProgress(progress);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error querying download progress", e);
                }

                progressHandler.postDelayed(this, 500);
            }
        };
        progressHandler.post(progressRunnable);
    }

    private void registerCompleteReceiver() {
        downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    verifyDownloadSuccess();
                }
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(downloadCompleteReceiver, filter);
        }
    }

    private void verifyDownloadSuccess() {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                if (statusIdx != -1) {
                    int status = cursor.getInt(statusIdx);
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cleanup();
                        if (listener != null) {
                            listener.onSuccess(destinationFile);
                        }
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking download status on completion", e);
        }
        handleFailure("Download completed but verification failed.");
    }

    private void handleFailure(String errorMessage) {
        cleanup();
        if (listener != null) {
            listener.onFailure(errorMessage);
        }
    }

    public void cleanup() {
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
            progressHandler = null;
            progressRunnable = null;
        }

        if (downloadCompleteReceiver != null) {
            try {
                context.unregisterReceiver(downloadCompleteReceiver);
            } catch (Exception ignored) {
            }
            downloadCompleteReceiver = null;
        }
        
        downloadId = -1;
    }
}
