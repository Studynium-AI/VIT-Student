package app.naevis.vitstudent.fragments;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.ImageView;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.ArrayAdapter;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import org.json.JSONArray;
import org.json.JSONObject;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.firebase.analytics.FirebaseAnalytics;

import app.naevis.vitstudent.R;
import app.naevis.vitstudent.helpers.UpdateChecker;
import app.naevis.vitstudent.fragments.dialogs.UpdateDialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import app.naevis.vitstudent.activities.LoginActivity;
import app.naevis.vitstudent.adapters.AnnouncementItemAdapter;
import app.naevis.vitstudent.adapters.ProfileGroupAdapter;
import app.naevis.vitstudent.helpers.SettingsRepository;

public class ProfileFragment extends Fragment {
    /*
        User Related Profile Items
     */
    private final ItemData[] personalProfileItems = {
            new ItemData(
                    R.drawable.ic_courses,
                    R.string.courses,
                    context -> SettingsRepository.openViewPagerFragment(
                            (FragmentActivity) context,
                            R.string.courses,
                            ViewPagerFragment.TYPE_COURSES
                    ),
                    null
            ),
            new ItemData(
                    R.drawable.ic_exams,
                    R.string.exam_schedule,
                    context -> SettingsRepository.openViewPagerFragment(
                            (FragmentActivity) context,
                            R.string.exam_schedule,
                            ViewPagerFragment.TYPE_EXAMS
                    ),
                    null
            ),
            new ItemData(
                    R.drawable.ic_receipts,
                    R.string.receipts,
                    context -> SettingsRepository.openRecyclerViewFragment(
                            (FragmentActivity) context,
                            R.string.receipts,
                            RecyclerViewFragment.TYPE_RECEIPTS
                    ),
                    null
            ),
            new ItemData(
                    R.drawable.ic_staff,
                    R.string.staff,
                    context -> SettingsRepository.openViewPagerFragment(
                            (FragmentActivity) context,
                            R.string.staff,
                            ViewPagerFragment.TYPE_STAFF
                    ),
                    null
            ),
            new ItemData(
                    R.drawable.ic_sync,
                    R.string.sync_data,
                    context -> getParentFragmentManager().setFragmentResult("syncData", new Bundle()),
                    profileItem -> {
                        ProgressBar progressBar = new ProgressBar(profileItem.getContext());
                        RelativeLayout extraContainer = profileItem.findViewById(R.id.relative_layout_extra_container);
                        extraContainer.addView(progressBar);

                        getParentFragmentManager().setFragmentResultListener("syncDataState", this, (requestKey, result) -> {
                            if (result.getBoolean("isLoading")) {
                                profileItem.setEnabled(false);
                                extraContainer.setVisibility(View.VISIBLE);
                            } else {
                                profileItem.setEnabled(true);
                                extraContainer.setVisibility(View.GONE);
                            }
                        });
                    }
            ),
            new ItemData(
                    R.drawable.ic_sync,
                    "Auto-Sync Data",
                    "Auto-Sync is disabled",
                    context -> showAutoSyncSettingsDialog(context),
                    profileItem -> setupAutoSyncInit(profileItem)
            )
    };

    /*
        Application Related Profile Items
     */
    private final ItemData[] applicationProfileItems = {
            new ItemData(
                    R.drawable.ic_clock,
                    "Lock Screen Settings",
                    "Manage lock screen timetable",
                    context -> {
                        SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(context);
                        View view = getLayoutInflater().inflate(R.layout.layout_dialog_lockscreen, null);
                        
                        com.google.android.material.materialswitch.MaterialSwitch lockSwitch = view.findViewById(R.id.switch_lock_screen);
                        com.google.android.material.materialswitch.MaterialSwitch activeHoursSwitch = view.findViewById(R.id.switch_active_hours);
                        View activeHoursSettings = view.findViewById(R.id.layout_active_hours_settings);
                        TextView tvStartTime = view.findViewById(R.id.tv_active_start_time);
                        TextView tvEndTime = view.findViewById(R.id.tv_active_end_time);

                        lockSwitch.setChecked(sharedPreferences.getBoolean("enableLockScreen", false));
                        activeHoursSwitch.setChecked(app.naevis.vitstudent.lockscreen.ScheduleHelper.isScheduleEnabled(context));
                        activeHoursSettings.setVisibility(activeHoursSwitch.isChecked() ? View.VISIBLE : View.GONE);

                        final int[] startH = {app.naevis.vitstudent.lockscreen.ScheduleHelper.getStartHour(context)};
                        final int[] startM = {app.naevis.vitstudent.lockscreen.ScheduleHelper.getStartMinute(context)};
                        final int[] endH = {app.naevis.vitstudent.lockscreen.ScheduleHelper.getEndHour(context)};
                        final int[] endM = {app.naevis.vitstudent.lockscreen.ScheduleHelper.getEndMinute(context)};

                        java.util.Calendar cal = java.util.Calendar.getInstance();
                        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());

                        Runnable updateTimeTexts = () -> {
                            cal.set(java.util.Calendar.HOUR_OF_DAY, startH[0]);
                            cal.set(java.util.Calendar.MINUTE, startM[0]);
                            tvStartTime.setText(timeFormat.format(cal.getTime()));

                            cal.set(java.util.Calendar.HOUR_OF_DAY, endH[0]);
                            cal.set(java.util.Calendar.MINUTE, endM[0]);
                            tvEndTime.setText(timeFormat.format(cal.getTime()));
                        };
                        updateTimeTexts.run();

                        lockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (isChecked) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                                    Toast.makeText(context, "Please grant overlay permission for Lock Screen UI", Toast.LENGTH_LONG).show();
                                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:" + context.getPackageName()));
                                    context.startActivity(intent);
                                    buttonView.setChecked(false);
                                    return;
                                }
                                sharedPreferences.edit().putBoolean("enableLockScreen", true).apply();
                                app.naevis.vitstudent.lockscreen.ScheduleHelper.rescheduleAlarms(context);
                                
                                if (app.naevis.vitstudent.lockscreen.ScheduleHelper.isWithinScheduleWindow(context)) {
                                    Intent serviceIntent = new Intent(context, app.naevis.vitstudent.lockscreen.LockScreenService.class);
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent);
                                    } else {
                                        context.startService(serviceIntent);
                                    }
                                }
                            } else {
                                sharedPreferences.edit().putBoolean("enableLockScreen", false).apply();
                                app.naevis.vitstudent.lockscreen.ScheduleHelper.cancelAlarms(context);
                                Intent serviceIntent = new Intent(context, app.naevis.vitstudent.lockscreen.LockScreenService.class);
                                context.stopService(serviceIntent);
                            }
                        });

                        activeHoursSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            activeHoursSettings.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                            app.naevis.vitstudent.lockscreen.ScheduleHelper.saveSchedule(context, isChecked, startH[0], startM[0], endH[0], endM[0]);
                            if (lockSwitch.isChecked()) {
                                app.naevis.vitstudent.lockscreen.ScheduleHelper.rescheduleAlarms(context);
                                Intent serviceIntent = new Intent(context, app.naevis.vitstudent.lockscreen.LockScreenService.class);
                                if (app.naevis.vitstudent.lockscreen.ScheduleHelper.isWithinScheduleWindow(context)) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        context.startForegroundService(serviceIntent);
                                    } else {
                                        context.startService(serviceIntent);
                                    }
                                } else {
                                    context.stopService(serviceIntent);
                                }
                            }
                        });

                        view.findViewById(R.id.btn_select_start_time).setOnClickListener(v -> {
                            new android.app.TimePickerDialog(context, (tpView, hourOfDay, minute) -> {
                                startH[0] = hourOfDay;
                                startM[0] = minute;
                                updateTimeTexts.run();
                                app.naevis.vitstudent.lockscreen.ScheduleHelper.saveSchedule(context, activeHoursSwitch.isChecked(), startH[0], startM[0], endH[0], endM[0]);
                                if (lockSwitch.isChecked() && activeHoursSwitch.isChecked()) {
                                    app.naevis.vitstudent.lockscreen.ScheduleHelper.rescheduleAlarms(context);
                                }
                            }, startH[0], startM[0], false).show();
                        });

                        view.findViewById(R.id.btn_select_end_time).setOnClickListener(v -> {
                            new android.app.TimePickerDialog(context, (tpView, hourOfDay, minute) -> {
                                endH[0] = hourOfDay;
                                endM[0] = minute;
                                updateTimeTexts.run();
                                app.naevis.vitstudent.lockscreen.ScheduleHelper.saveSchedule(context, activeHoursSwitch.isChecked(), startH[0], startM[0], endH[0], endM[0]);
                                if (lockSwitch.isChecked() && activeHoursSwitch.isChecked()) {
                                    app.naevis.vitstudent.lockscreen.ScheduleHelper.rescheduleAlarms(context);
                                }
                            }, endH[0], endM[0], false).show();
                        });

                        new MaterialAlertDialogBuilder(context)
                                .setView(view)
                                .setTitle("Lock Screen")
                                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                                .show();
                    }
            ),
            new ItemData(
                    R.drawable.ic_clock,
                    "Test Lock Screen UI",
                    "Launch Lock Screen Activity manually",
                    context -> {
                        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
                        new MaterialAlertDialogBuilder(context)
                                .setTitle("Select Day to Test")
                                .setItems(days, (dialog, which) -> {
                                    int testDay = which + 1; // Monday (0) -> 1, Sunday (6) -> 7
                                    Intent lockIntent = new Intent(context, app.naevis.vitstudent.lockscreen.LockScreenActivity.class);
                                    lockIntent.putExtra("test_day", testDay);
                                    context.startActivity(lockIntent);
                                })
                                .show();
                    }
            ),
            new ItemData(
                    R.drawable.ic_profile,
                    "Permissions Setup",
                    "Configure required and optional app permissions",
                    context -> {
                        Intent intent = new Intent(context, app.naevis.vitstudent.activities.PermissionsActivity.class);
                        context.startActivity(intent);
                    }
            ),
            new ItemData(
                    R.drawable.ic_appearance,
                    R.string.appearance,
                    context -> {
                        String[] themes = {
                                context.getString(R.string.light),
                                context.getString(R.string.dark),
                                context.getString(R.string.system)
                        };

                        SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(context);

                        int checkedItem = 2;
                        String theme = sharedPreferences.getString("appearance", "system");

                        if (theme.equals("light")) {
                            checkedItem = 0;
                        } else if (theme.equals("dark")) {
                            checkedItem = 1;
                        }

                        View appearanceView = getLayoutInflater().inflate(R.layout.layout_dialog_apperance, null);
                        MaterialSwitch amoledSwitch = appearanceView.findViewById(R.id.switch_amoled_mode);
                        amoledSwitch.setChecked(sharedPreferences.getBoolean("amoledMode", false));
                        amoledSwitch.setOnCheckedChangeListener((compoundButton, isAmoledModeEnabled) -> {
                            sharedPreferences.edit().putBoolean("amoledMode", isAmoledModeEnabled).apply();
                            Bundle applyDynamicColors = new Bundle();
                            applyDynamicColors.putBoolean("amoledMode", isAmoledModeEnabled);
                            getParentFragmentManager().setFragmentResult("applyDynamicColors", applyDynamicColors);
                        });

                        new MaterialAlertDialogBuilder(context)
                                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.cancel())
                                .setSingleChoiceItems(themes, checkedItem, (dialogInterface, i) -> {
                                    if (i == 0) {
                                        sharedPreferences.edit().putString("appearance", "light").apply();
                                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                                    } else if (i == 1) {
                                        sharedPreferences.edit().putString("appearance", "dark").apply();
                                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                                    } else {
                                        sharedPreferences.edit().remove("appearance").apply();
                                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                                    }

                                    dialogInterface.dismiss();
                                })
                                .setView(appearanceView)
                                .setTitle(R.string.appearance)
                                .show();
                    },
                    null
            ),
            new ItemData(
                    R.drawable.ic_notifications,
                    R.string.notifications,
                    context -> {
                        Intent intent = new Intent();
                        intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                        intent.putExtra("app_package", context.getPackageName());
                        intent.putExtra("app_uid", context.getApplicationInfo().uid);
                        intent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());

                        context.startActivity(intent);
                    },
                    null
            ),
            new ItemData(
                    R.drawable.ic_privacy,
                    R.string.privacy,
                    context -> SettingsRepository.openWebViewActivity(
                            context,
                            context.getString(R.string.privacy),
                            SettingsRepository.APP_PRIVACY_URL
                    ),
                    null
            ),
            new ItemData(
                    R.drawable.ic_feedback,
                    R.string.send_feedback,
                    context -> {
                        View bottomSheetLayout = View.inflate(context, R.layout.layout_bottom_sheet_feedback, null);
                        bottomSheetLayout.findViewById(R.id.text_view_contact_developer).setOnClickListener(view -> SettingsRepository.openBrowser(context, SettingsRepository.DEVELOPER_BASE_URL));
                        bottomSheetLayout.findViewById(R.id.text_view_open_issue).setOnClickListener(view -> SettingsRepository.openBrowser(context, SettingsRepository.GITHUB_ISSUE_URL));
                        bottomSheetLayout.findViewById(R.id.text_view_request_feature).setOnClickListener(view -> SettingsRepository.openBrowser(context, SettingsRepository.GITHUB_FEATURE_URL));

                        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
                        bottomSheetDialog.setContentView(bottomSheetLayout);
                        bottomSheetDialog.show();
                    },
                    null
            ),
            new ItemData(
                    R.drawable.ic_share,
                    R.string.share,
                    context -> {
                        Intent intent = new Intent();
                        intent.setAction(Intent.ACTION_SEND);
                        intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.share_subject));
                        intent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text, SettingsRepository.GITHUB_RELEASE_URL));
                        intent.setType("text/plain");

                        Intent shareIntent = Intent.createChooser(intent, context.getString(R.string.share_title));
                        context.startActivity(shareIntent);
                    },
                    null
            ),
            new ItemData(
                    R.drawable.ic_update_available,
                    "Check for Updates",
                    "Check if a newer version of the app is available",
                    context -> {
                        Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show();
                        UpdateChecker.checkForUpdates(context, true)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(updateInfo -> {
                                    if (updateInfo.isUpdateAvailable) {
                                        FragmentManager fragmentManager = ((FragmentActivity) context).getSupportFragmentManager();
                                        FragmentTransaction transaction = fragmentManager.beginTransaction();
                                        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                                        transaction.add(android.R.id.content, UpdateDialogFragment.newInstance(
                                                updateInfo.latestVersion,
                                                updateInfo.releaseNotes,
                                                updateInfo.downloadUrl,
                                                updateInfo.isForceUpdate
                                        )).addToBackStack(null).commit();
                                    } else {
                                        Toast.makeText(context, "You are on the latest version.", Toast.LENGTH_SHORT).show();
                                    }
                                }, throwable -> {
                                    Toast.makeText(context, "Failed to check for updates.", Toast.LENGTH_SHORT).show();
                                });
                    }
            ),
            new ItemData(
                    R.drawable.ic_sign_out,
                    R.string.sign_out,
                    context -> {
                        AlertDialog signOutDialog = new MaterialAlertDialogBuilder(context)
                                .setMessage(R.string.sign_out_text)
                                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.cancel())
                                .setPositiveButton(R.string.sign_out, (dialogInterface, i) -> {
                                    SettingsRepository.signOut(context);
                                    context.startActivity(new Intent(context, LoginActivity.class));
                                    ((Activity) context).finish();
                                })
                                .setTitle(R.string.sign_out)
                                .create();

                        if (!SettingsRepository.isMoodleSignedIn(requireContext())) {
                            signOutDialog.show();
                            return;
                        }

                        AlertDialog moodleSignOutDialog = new MaterialAlertDialogBuilder(context)
                                .setMessage(R.string.moodle_sign_out_text)
                                .setNegativeButton(R.string.cancel, (dialogInterface, i) -> dialogInterface.cancel())
                                .setPositiveButton(R.string.sign_out, (dialogInterface, i) -> {
                                    SettingsRepository.signOutMoodle(requireContext());
                                    Toast.makeText(context, "You've signed out of Moodle.", Toast.LENGTH_SHORT).show();
                                })
                                .setTitle(R.string.sign_out)
                                .create();

                        View bottomSheetLayout = View.inflate(context, R.layout.layout_bottom_sheet_sign_out, null);
                        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
                        bottomSheetDialog.setContentView(bottomSheetLayout);

                        bottomSheetLayout.findViewById(R.id.text_view_sign_out_moodle).setOnClickListener(view -> {
                            bottomSheetDialog.dismiss();
                            moodleSignOutDialog.show();
                        });
                        bottomSheetLayout.findViewById(R.id.text_view_sign_out_app).setOnClickListener(view -> {
                            bottomSheetDialog.dismiss();
                            signOutDialog.show();
                        });

                        bottomSheetDialog.show();
                    },
                    null)
    };

    private final ItemData[][] profileItems = {
            personalProfileItems,
            applicationProfileItems
    };

    private final int[] profileGroups = {
            R.string.personal,
            R.string.application
    };

    /*
        App announcements
     */
    private final ItemData[] announcementItems = {
            new ItemData(
                    R.drawable.ic_whats_new,
                    "VIT Student is now Open Source!",
                    "Click to view the source code.",
                    context -> SettingsRepository.openBrowser(context, SettingsRepository.GITHUB_BASE_URL)
            )
    };

    private View autoSyncProfileItemView = null;

    private void setupAutoSyncInit(View profileItem) {
        autoSyncProfileItemView = profileItem;
        Context context = profileItem.getContext();
        RelativeLayout extraContainer = profileItem.findViewById(R.id.relative_layout_extra_container);
        extraContainer.removeAllViews();

        com.google.android.material.materialswitch.MaterialSwitch toggle = new com.google.android.material.materialswitch.MaterialSwitch(context);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        toggle.setLayoutParams(params);

        SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
        toggle.setChecked(prefs.getBoolean("auto_sync_enabled", false));

        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("auto_sync_enabled", isChecked).apply();
            if (isChecked) {
                String apiKey = prefs.getString("gemini_api_key", "");
                if (apiKey.isEmpty()) {
                    buttonView.setChecked(false);
                    prefs.edit().putBoolean("auto_sync_enabled", false).apply();
                    Toast.makeText(context, "Please configure Gemini API key first", Toast.LENGTH_LONG).show();
                    showAutoSyncSettingsDialog(context);
                } else {
                    app.naevis.vitstudent.receivers.AutoSyncReceiver.scheduleNextAutoSync(context);
                    Toast.makeText(context, "Auto-Sync Enabled", Toast.LENGTH_SHORT).show();
                }
            } else {
                app.naevis.vitstudent.receivers.AutoSyncReceiver.cancelAutoSync(context);
                Toast.makeText(context, "Auto-Sync Disabled", Toast.LENGTH_SHORT).show();
            }
            updateAutoSyncStatusText(profileItem);
        });

        extraContainer.addView(toggle);
        extraContainer.setVisibility(View.VISIBLE);

        updateAutoSyncStatusText(profileItem);
    }

    private void updateAutoSyncStatusText(View profileItem) {
        if (profileItem == null) return;
        TextView description = profileItem.findViewById(R.id.text_view_description);
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(profileItem.getContext());
        boolean enabled = prefs.getBoolean("auto_sync_enabled", false);

        if (!enabled) {
            description.setText("Auto-Sync is disabled");
            description.setVisibility(View.VISIBLE);
            return;
        }

        long lastTime = prefs.getLong("auto_sync_last_time", 0);
        String lastStatus = prefs.getString("auto_sync_last_status", "");

        if (lastTime == 0) {
            description.setText("Last sync: Never");
        } else {
            CharSequence relativeTime = android.text.format.DateUtils.getRelativeTimeSpanString(
                    lastTime,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
            );
            String statusSuffix = lastStatus.isEmpty() ? "" : " (" + lastStatus + ")";
            description.setText("Last sync: " + relativeTime + statusSuffix);
        }
        description.setVisibility(View.VISIBLE);
    }

    private void showAutoSyncSettingsDialog(Context context) {
        SharedPreferences prefs = SettingsRepository.getSharedPreferences(context);
        View view = getLayoutInflater().inflate(R.layout.layout_dialog_auto_sync, null);

        EditText etApiKey = view.findViewById(R.id.et_api_key);
        ImageView ivStatus = view.findViewById(R.id.iv_api_status);
        TextView tvError = view.findViewById(R.id.tv_api_error);
        Spinner spinner = view.findViewById(R.id.spinner_interval);

        String currentApiKey = prefs.getString("gemini_api_key", "");
        etApiKey.setText(currentApiKey);

        String[] intervals = {"2 hours", "4 hours", "6 hours", "8 hours", "12 hours"};
        Integer[] intervalHours = {2, 4, 6, 8, 12};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, intervals);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        int currentInterval = prefs.getInt("auto_sync_interval_hours", 2);
        int selectedIndex = 0;
        for (int i = 0; i < intervalHours.length; i++) {
            if (intervalHours[i] == currentInterval) {
                selectedIndex = i;
                break;
            }
        }
        spinner.setSelection(selectedIndex);

        if (!currentApiKey.isEmpty()) {
            validateGeminiApiKey(currentApiKey, ivStatus, tvError);
        }

        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        final Runnable[] runnable = {null};

        etApiKey.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (runnable[0] != null) {
                    handler.removeCallbacks(runnable[0]);
                }
                runnable[0] = () -> validateGeminiApiKey(s.toString(), ivStatus, tvError);
                handler.postDelayed(runnable[0], 800);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle("Auto-Sync Settings")
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.submit, (dialog, which) -> {
                    String newKey = etApiKey.getText().toString().trim();
                    int newInterval = intervalHours[spinner.getSelectedItemPosition()];

                    prefs.edit()
                            .putString("gemini_api_key", newKey)
                            .putInt("auto_sync_interval_hours", newInterval)
                            .apply();

                    if (prefs.getBoolean("auto_sync_enabled", false)) {
                        app.naevis.vitstudent.receivers.AutoSyncReceiver.scheduleNextAutoSync(context);
                    }
                    if (autoSyncProfileItemView != null) {
                        updateAutoSyncStatusText(autoSyncProfileItemView);
                    }
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void validateGeminiApiKey(String apiKey, ImageView ivStatus, TextView tvError) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            ivStatus.setVisibility(View.GONE);
            tvError.setVisibility(View.GONE);
            return;
        }

        io.reactivex.rxjava3.core.Single.fromCallable(() -> {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            JSONObject jsonPayload = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject contentObj = new JSONObject();
            JSONArray parts = new JSONArray();
            JSONObject textPart = new JSONObject();
            textPart.put("text", "Hello");
            parts.put(textPart);
            contentObj.put("parts", parts);
            contents.put(contentObj);
            jsonPayload.put("contents", contents);

            okhttp3.RequestBody body = okhttp3.RequestBody.create(
                    jsonPayload.toString(),
                    okhttp3.MediaType.parse("application/json; charset=utf-8")
            );

            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    return "SUCCESS";
                } else {
                    try {
                        JSONObject errJson = new JSONObject(responseBody);
                        JSONObject err = errJson.getJSONObject("error");
                        return "ERROR: " + err.getString("message");
                    } catch (Exception ignored) {
                        return "ERROR: " + response.code() + " " + response.message();
                    }
                }
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(result -> {
            if ("SUCCESS".equals(result)) {
                ivStatus.setVisibility(View.VISIBLE);
                ivStatus.setImageResource(R.drawable.ic_done);
                ivStatus.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.GREEN));
                tvError.setVisibility(View.GONE);
            } else {
                ivStatus.setVisibility(View.VISIBLE);
                ivStatus.setImageResource(R.drawable.ic_close);
                ivStatus.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
                tvError.setVisibility(View.VISIBLE);
                tvError.setText(result.replace("ERROR: ", ""));
                tvError.setTextColor(android.graphics.Color.RED);
            }
        }, throwable -> {
            ivStatus.setVisibility(View.VISIBLE);
            ivStatus.setImageResource(R.drawable.ic_close);
            ivStatus.setImageTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("Error: " + throwable.getLocalizedMessage());
            tvError.setTextColor(android.graphics.Color.RED);
        });
    }

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public void onResume() {
        super.onResume();

        // Firebase Analytics Logging
        Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.SCREEN_CLASS, "ProfileFragment");
        bundle.putString(FirebaseAnalytics.Param.SCREEN_NAME, "Profile");
        FirebaseAnalytics.getInstance(this.requireContext()).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle);

        getParentFragmentManager().setFragmentResultListener("launchSubFragment", this, (requestKey, result) -> {
            String subFragment = result.getString("subFragment");

            if (subFragment.equals("ExamSchedule")) {
                SettingsRepository.openViewPagerFragment(
                        requireActivity(),
                        R.string.exam_schedule,
                        ViewPagerFragment.TYPE_EXAMS
                );
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View profileFragment = inflater.inflate(R.layout.fragment_profile, container, false);

        View appBarLayout = profileFragment.findViewById(R.id.app_bar);
        View profileView = profileFragment.findViewById(R.id.nested_scroll_view_profile);

        getParentFragmentManager().setFragmentResultListener("customInsets", this, (requestKey, result) -> {
            int systemWindowInsetLeft = result.getInt("systemWindowInsetLeft");
            int systemWindowInsetTop = result.getInt("systemWindowInsetTop");
            int systemWindowInsetRight = result.getInt("systemWindowInsetRight");
            int bottomNavigationHeight = result.getInt("bottomNavigationHeight");
            float pixelDensity = getResources().getDisplayMetrics().density;

            appBarLayout.setPadding(
                    systemWindowInsetLeft,
                    systemWindowInsetTop,
                    systemWindowInsetRight,
                    0
            );

            profileView.setPaddingRelative(
                    systemWindowInsetLeft,
                    0,
                    systemWindowInsetRight,
                    (int) (bottomNavigationHeight + 20 * pixelDensity)
            );

            // Only one listener can be added per requestKey, so we create a duplicate
            getParentFragmentManager().setFragmentResult("customInsets2", result);
        });

        RecyclerView announcements = profileFragment.findViewById(R.id.recycler_view_announcements);
        RecyclerView profileGroups = profileFragment.findViewById(R.id.recycler_view_profile_groups);

        announcements.setAdapter(new AnnouncementItemAdapter(announcementItems));
        profileGroups.setAdapter(new ProfileGroupAdapter(this.profileGroups, this.profileItems));

        return profileFragment;
    }

    public static class ItemData {
        public final int iconId, titleId;
        public final String title;
        public String description;
        public final OnClickListener onClickListener;
        public final OnInitListener onInitListener;

        public ItemData(@DrawableRes int iconId, @StringRes int titleId, OnClickListener onClickListener, OnInitListener onInitListener) {
            this.iconId = iconId;
            this.titleId = titleId;
            this.onClickListener = onClickListener;
            this.onInitListener = onInitListener;

            this.title = null;
            this.description = null;
        }

        public ItemData(@DrawableRes int iconId, String title, String description, OnClickListener onClickListener) {
            this.iconId = iconId;
            this.title = title;
            this.description = description;
            this.onClickListener = onClickListener;

            this.titleId = 0;
            this.onInitListener = null;
        }

        public ItemData(@DrawableRes int iconId, String title, String description, OnClickListener onClickListener, OnInitListener onInitListener) {
            this.iconId = iconId;
            this.title = title;
            this.description = description;
            this.onClickListener = onClickListener;
            this.onInitListener = onInitListener;

            this.titleId = 0;
        }

        public interface OnClickListener {
            void onClick(Context context);
        }

        public interface OnInitListener {
            void onInit(View profileItem);
        }
    }
}
