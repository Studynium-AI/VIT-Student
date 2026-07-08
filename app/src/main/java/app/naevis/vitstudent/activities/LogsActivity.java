package app.naevis.vitstudent.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.analytics.FirebaseAnalytics;

import app.naevis.vitstudent.R;
import app.naevis.vitstudent.helpers.SettingsRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class LogsActivity extends AppCompatActivity {

    private ListView listView;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_dialog_logs);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_logs);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_logs);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_logs) {
                SharedPreferences prefs = SettingsRepository.getSharedPreferences(this);
                prefs.edit().remove("sync_logs_json").apply();
                loadLogs();
                Toast.makeText(this, "Logs cleared.", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        listView = findViewById(R.id.list_view_logs);
        tvEmpty = findViewById(R.id.tv_empty_logs);

        loadLogs();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Firebase Analytics Logging
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, "LogsActivity");
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "Sync Logs");
        FirebaseAnalytics.getInstance(this).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);
    }

    private void loadLogs() {
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(this);
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
                convertView = LayoutInflater.from(LogsActivity.this).inflate(R.layout.layout_item_log, parent, false);
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
