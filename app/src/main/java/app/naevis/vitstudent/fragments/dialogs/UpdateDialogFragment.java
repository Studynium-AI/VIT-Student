package app.naevis.vitstudent.fragments.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.noties.markwon.Markwon;
import app.naevis.vitstudent.R;
import app.naevis.vitstudent.helpers.SettingsRepository;
import app.naevis.vitstudent.helpers.UpdateDownloader;
import app.naevis.vitstudent.helpers.UpdateInstaller;

import java.io.File;

public class UpdateDialogFragment extends DialogFragment {
    private static final String TAG = "UpdateDialogFragment";

    private String versionName;
    private String releaseNotes;
    private String downloadUrl;
    private boolean isForceUpdate;

    private CheckBox checkboxSkipVersion;
    private LinearLayout layoutProgress;
    private ProgressBar progressBarDownload;
    private TextView textViewProgressPercent;
    private View buttonUpdate;
    private View buttonCancel;

    private UpdateDownloader downloader;
    private File downloadedApkFile;
    private boolean isDownloading = false;

    public UpdateDialogFragment() {
        // Required empty public constructor
    }

    public static UpdateDialogFragment newInstance(String versionName, String releaseNotes, String downloadUrl, boolean isForceUpdate) {
        Bundle args = new Bundle();
        UpdateDialogFragment fragment = new UpdateDialogFragment();

        args.putString("versionName", versionName);
        args.putString("releaseNotes", releaseNotes);
        args.putString("downloadUrl", downloadUrl);
        args.putBoolean("isForceUpdate", isForceUpdate);

        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View dialogFragment = inflater.inflate(R.layout.layout_dialog_update, container, false);
        Bundle args = getArguments();

        if (args != null) {
            this.versionName = args.getString("versionName");
            this.releaseNotes = args.getString("releaseNotes");
            this.downloadUrl = args.getString("downloadUrl");
            this.isForceUpdate = args.getBoolean("isForceUpdate");
        }

        TextView description = dialogFragment.findViewById(R.id.text_view_description);
        description.setText(Html.fromHtml(this.requireContext().getString(R.string.update_message, this.versionName), Html.FROM_HTML_MODE_LEGACY));

        TextView releaseNotesView = dialogFragment.findViewById(R.id.text_view_release_notes);
        Markwon markwon = Markwon.create(this.requireContext());
        markwon.setMarkdown(releaseNotesView, this.releaseNotes);

        checkboxSkipVersion = dialogFragment.findViewById(R.id.checkbox_skip_version);
        layoutProgress = dialogFragment.findViewById(R.id.layout_progress);
        progressBarDownload = dialogFragment.findViewById(R.id.progress_bar_download);
        textViewProgressPercent = dialogFragment.findViewById(R.id.text_view_progress_percent);
        buttonUpdate = dialogFragment.findViewById(R.id.button_update);
        buttonCancel = dialogFragment.findViewById(R.id.button_cancel);

        if (isForceUpdate) {
            checkboxSkipVersion.setVisibility(View.GONE);
            buttonCancel.setVisibility(View.GONE);
            setCancelable(false);
        } else {
            buttonCancel.setOnClickListener(view -> {
                if (checkboxSkipVersion.isChecked()) {
                    SharedPreferences prefs = SettingsRepository.getSharedPreferences(requireContext());
                    prefs.edit().putString("skipped_update_version", versionName).apply();
                }
                this.dismiss();
            });
        }

        buttonUpdate.setOnClickListener(view -> startUpdateDownload());

        return dialogFragment;
    }

    private void startUpdateDownload() {
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Update URL is invalid.", Toast.LENGTH_SHORT).show();
            return;
        }

        isDownloading = true;
        setCancelable(false);
        buttonUpdate.setVisibility(View.GONE);
        buttonCancel.setVisibility(View.GONE);
        checkboxSkipVersion.setVisibility(View.GONE);
        layoutProgress.setVisibility(View.VISIBLE);

        downloader = new UpdateDownloader(requireContext());
        downloader.downloadApk(downloadUrl, versionName, new UpdateDownloader.DownloadListener() {
            @Override
            public void onProgress(int progressPercent) {
                progressBarDownload.setProgress(progressPercent);
                textViewProgressPercent.setText("Downloading: " + progressPercent + "%");
            }

            @Override
            public void onSuccess(File downloadedFile) {
                isDownloading = false;
                downloadedApkFile = downloadedFile;
                
                // Proceed to install check
                checkAndInstall();
            }

            @Override
            public void onFailure(String errorMessage) {
                isDownloading = false;
                Toast.makeText(requireContext(), "Download failed: " + errorMessage, Toast.LENGTH_LONG).show();
                resetUI();
            }
        });
    }

    private void checkAndInstall() {
        if (downloadedApkFile == null || !downloadedApkFile.exists()) {
            Toast.makeText(requireContext(), "Downloaded APK file not found.", Toast.LENGTH_SHORT).show();
            resetUI();
            return;
        }

        if (UpdateInstaller.checkInstallPermission(requireContext())) {
            try {
                UpdateInstaller.installApk(requireContext(), downloadedApkFile);
                dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Installation failed", e);
                Toast.makeText(requireContext(), "Install failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                resetUI();
            }
        } else {
            Toast.makeText(requireContext(), "Please enable 'Install unknown apps' permission to complete the update.", Toast.LENGTH_LONG).show();
            UpdateInstaller.requestInstallPermission(requireContext());
        }
    }

    private void resetUI() {
        if (downloader != null) {
            downloader.cleanup();
        }
        layoutProgress.setVisibility(View.GONE);
        buttonUpdate.setVisibility(View.VISIBLE);
        if (!isForceUpdate) {
            buttonCancel.setVisibility(View.VISIBLE);
            checkboxSkipVersion.setVisibility(View.VISIBLE);
            setCancelable(true);
        } else {
            setCancelable(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // If we were waiting for the user to grant unknown source install permission
        if (!isDownloading && downloadedApkFile != null && downloadedApkFile.exists()) {
            if (UpdateInstaller.checkInstallPermission(requireContext())) {
                checkAndInstall();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (downloader != null) {
            downloader.cleanup();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }
}
