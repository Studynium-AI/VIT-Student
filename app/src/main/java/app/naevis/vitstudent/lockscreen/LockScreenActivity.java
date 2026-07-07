package app.naevis.vitstudent.lockscreen;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import app.naevis.vitstudent.R;
import app.naevis.vitstudent.helpers.AppDatabase;
import app.naevis.vitstudent.models.Task;
import app.naevis.vitstudent.models.Timetable;

public class LockScreenActivity extends AppCompatActivity {

    private TextView clockTime, clockDate, emptyState;
    private RecyclerView rvClasses;
    private LockScreenClassesAdapter adapter;
    private CompositeDisposable disposables = new CompositeDisposable();

    private final android.content.BroadcastReceiver unlockReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                finish();
            }
        }
    };

    private boolean isUiVisible = false;
    private final android.os.Handler updateHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            Date date = new Date();
            clockTime.setText(new SimpleDateFormat("h:mm a", Locale.getDefault()).format(date));
            clockDate.setText(new SimpleDateFormat("EEEE, MMMM dd", Locale.getDefault()).format(date));
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            updateHandler.postDelayed(this, 10000);
        }
    };

    private android.view.GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure it shows over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(unlockReceiver, filter);
        }
        
        setContentView(R.layout.activity_lock_screen);

        getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setBackgroundBlurRadius(80);
        }

        // Initialize double tap to sleep detector
        gestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                turnOffScreen();
                return true;
            }
        });

        boolean isTestMode = getIntent().hasExtra("test_day");
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        
        if (isTestMode) {
            isUiVisible = true;
        } else {
            isUiVisible = pm != null && pm.isInteractive() && km != null && km.isKeyguardLocked();
        }

        clockTime = findViewById(R.id.tv_clock_time);
        clockDate = findViewById(R.id.tv_clock_date);
        emptyState = findViewById(R.id.tv_empty_state);
        rvClasses = findViewById(R.id.rv_lock_screen_classes);
        
        findViewById(R.id.btn_dismiss).setOnClickListener(v -> finish());

        rvClasses.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LockScreenClassesAdapter(this, this::showAddTaskDialog);
        rvClasses.setAdapter(adapter);

        setUiVisible(isUiVisible);
        loadTimetable();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void turnOffScreen() {
        android.app.admin.DevicePolicyManager dpm = (android.app.admin.DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName adminComponent = new android.content.ComponentName(this, LockScreenAdminReceiver.class);
        
        if (dpm != null && dpm.isAdminActive(adminComponent)) {
            dpm.lockNow();
        } else {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Permission Required")
                    .setMessage("To turn off the screen by double tapping, please activate Device Admin permission for this app.")
                    .setPositiveButton("Activate", (dialog, which) -> {
                        Intent intent = new Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
                        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Allows putting the phone to sleep on double tap.");
                        startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkKeyguardState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkKeyguardState();
        loadTimetable();
        updateHandler.removeCallbacks(updateRunnable);
        updateHandler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateHandler.removeCallbacks(updateRunnable);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            checkKeyguardState();
        } else {
            // Hide the UI when losing focus (e.g. incoming call, expanded notification heads-up, etc.)
            setUiVisible(false);
        }
    }

    private boolean isPhoneCallActive() {
        try {
            android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && tm.getCallState() != android.telephony.TelephonyManager.CALL_STATE_IDLE) {
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void checkKeyguardState() {
        if (getIntent().hasExtra("test_day")) {
            setUiVisible(true);
            return;
        }

        if (isPhoneCallActive()) {
            setUiVisible(false);
            finish();
            return;
        }
        
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        boolean isLocked = km != null && km.isKeyguardLocked();
        
        if (pm != null && pm.isInteractive()) {
            if (isLocked) {
                setUiVisible(true);
            } else {
                setUiVisible(false);
                finish();
            }
        }
    }

    private void setUiVisible(boolean visible) {
        isUiVisible = visible;
        View root = findViewById(R.id.root_layout);
        if (root != null) {
            root.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // Removed old clockThread

    private void loadTimetable() {
        int day;
        if (getIntent().hasExtra("test_day")) {
            day = getIntent().getIntExtra("test_day", 1);
        } else {
            int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
            if (dayOfWeek == Calendar.SUNDAY) {
                day = 7;
            } else {
                day = dayOfWeek - 1;
            }
        }
        
        disposables.add(
            AppDatabase.getInstance(this).timetableDao().get(day)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(classes -> {
                    if (classes.isEmpty()) {
                        emptyState.setVisibility(View.VISIBLE);
                        rvClasses.setVisibility(View.GONE);
                    } else {
                        emptyState.setVisibility(View.GONE);
                        rvClasses.setVisibility(View.VISIBLE);
                        adapter.setClasses(app.naevis.vitstudent.models.Timetable.combineLabs(classes));
                    }
                }, error -> {
                    error.printStackTrace();
                })
        );
    }

    private void showAddTaskDialog(String courseCode, String courseTitle) {
        app.naevis.vitstudent.helpers.TaskDialogHelper.showAddTaskDialog(this, courseCode, courseTitle, () -> {
            if (adapter != null) {
                adapter.clearTasksCache();
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposables.clear();
        try {
            unregisterReceiver(unlockReceiver);
        } catch (Exception ignored) {}
    }
}
