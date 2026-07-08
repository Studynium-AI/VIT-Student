package app.naevis.vitstudent.fragments.dialogs;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import io.noties.markwon.Markwon;
import app.naevis.vitstudent.R;
import app.naevis.vitstudent.helpers.SettingsRepository;

public class UpdateDialogFragment extends DialogFragment {
    private static final String TAG = "UpdateDialogFragment";

    private String versionName;
    private String releaseNotes;
    private String downloadUrl;
    private boolean isForceUpdate;

    private CheckBox checkboxSkipVersion;
    private View buttonUpdate;
    private View buttonCancel;

    public UpdateDialogFragment() {
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

        buttonUpdate.setOnClickListener(view -> {
            if (downloadUrl != null && !downloadUrl.isEmpty()) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Failed to open release page.", Toast.LENGTH_SHORT).show();
                }
            } else {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Studynium-AI/VIT-Student/releases/latest"));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Failed to open release page.", Toast.LENGTH_SHORT).show();
                }
            }
            if (!isForceUpdate) {
                dismiss();
            }
        });

        return dialogFragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }
}
