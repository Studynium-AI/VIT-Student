package app.naevis.vitstudent.fragments.dialogs;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.appbar.MaterialToolbar;

import app.naevis.vitstudent.R;
import app.naevis.vitstudent.helpers.SettingsRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class LogDialogFragment extends DialogFragment {

    private ListView listView;
    private TextView tvEmpty;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog_Centered);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setWindowAnimations(android.R.style.Animation_Dialog);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_dialog_logs, container, false);

        MaterialToolbar toolbar = view.findViewById(R.id.toolbar_logs);
        toolbar.setNavigationOnClickListener(v -> dismiss());
        toolbar.inflateMenu(R.menu.menu_logs);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_logs) {
                SharedPreferences prefs = SettingsRepository.getSharedPreferences(requireContext());
                prefs.edit().remove("sync_logs_json").apply();
                loadLogs();
                Toast.makeText(requireContext(), "Logs cleared.", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        listView = view.findViewById(R.id.list_view_logs);
        tvEmpty = view.findViewById(R.id.tv_empty_logs);

        loadLogs();

        return view;
    }

    private void loadLogs() {
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(requireContext());
        String logsJson = prefs.getString("sync_logs_json", "[]");
        List<LogItem> logList = new ArrayList<>();

        try {
            JSONArray array = new JSONArray(logsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                logList.add(new LogItem(
                        obj.optString("timestamp"),
                        obj.optString("type"),
                        obj.optString("title"),
                        obj.optString("message")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (logList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
            listView.setAdapter(new LogAdapter(logList));
        }
    }

    private static class LogItem {
        final String timestamp;
        final String type;
        final String title;
        final String message;

        LogItem(String timestamp, String type, String title, String message) {
            this.timestamp = timestamp;
            this.type = type;
            this.title = title;
            this.message = message;
        }
    }

    private class LogAdapter extends android.widget.BaseAdapter {
        private final List<LogItem> items;

        LogAdapter(List<LogItem> items) {
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_item_log, parent, false);
            }

            LogItem item = items.get(position);

            TextView tvTitle = convertView.findViewById(R.id.tv_log_title);
            TextView tvStatus = convertView.findViewById(R.id.tv_log_status);
            TextView tvTimestamp = convertView.findViewById(R.id.tv_log_timestamp);
            TextView tvMessage = convertView.findViewById(R.id.tv_log_message);

            tvTitle.setText(item.title);
            tvTimestamp.setText(item.timestamp);
            tvMessage.setText(item.message);

            if ("SUCCESS".equalsIgnoreCase(item.type)) {
                tvStatus.setText("✓ SUCCESS");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            } else {
                tvStatus.setText("✗ FAILURE");
                tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"));
            }

            return convertView;
        }
    }
}
