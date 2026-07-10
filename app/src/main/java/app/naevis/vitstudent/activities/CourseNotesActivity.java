package app.naevis.vitstudent.activities;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import app.naevis.vitstudent.R;
import app.naevis.vitstudent.helpers.AppDatabase;
import app.naevis.vitstudent.helpers.MarkdownExporter;
import app.naevis.vitstudent.helpers.MarkdownImporter;
import app.naevis.vitstudent.models.CourseNote;

public class CourseNotesActivity extends AppCompatActivity {

    private String courseCode;
    private String courseTitle;
    private LinearLayout layoutBlocks;
    private EditText focusedEditText;
    private int colorOnSurface;
    private boolean isExternalImport = false;

    // FAB state
    private boolean fabExpanded = false;
    private ImageButton fabAdd;
    private LinearLayout layoutFabCamera, layoutFabGallery, layoutFabMarkdown;
    private View fabScrim;

    // Camera capture: we must store the temp file URI
    private Uri cameraTempUri;

    // ── Activity Result Launchers ─────────────────────────────────────────────

    private final ActivityResultLauncher<String> selectImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (uri != null) insertImageBlock(uri); }
    );

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && cameraTempUri != null) {
                    insertImageBlock(cameraTempUri);
                }
            }
    );

    private final ActivityResultLauncher<String[]> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            perms -> {
                Boolean granted = perms.get(Manifest.permission.CAMERA);
                if (Boolean.TRUE.equals(granted)) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private final ActivityResultLauncher<String> markdownFileLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent() {
                @androidx.annotation.NonNull
                @Override
                public Intent createIntent(@androidx.annotation.NonNull Context context, @androidx.annotation.NonNull String input) {
                    Intent intent = super.createIntent(context, input);
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                            "text/markdown", "text/x-markdown", "text/plain", "application/octet-stream"
                    });
                    return intent;
                }
            },
            uri -> {
                if (uri != null) {
                    String fileName = getFileNameFromUri(uri);
                    if (fileName == null || !(fileName.toLowerCase().endsWith(".md") || fileName.toLowerCase().endsWith(".markdown"))) {
                        Toast.makeText(this, "Only Markdown files (.md, .markdown) are allowed", Toast.LENGTH_LONG).show();
                    } else {
                        importMarkdownFromUri(uri);
                    }
                }
            }
    );

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        boolean fromLockscreen = getIntent().getBooleanExtra("from_lockscreen", false);
        if (fromLockscreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true);
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }
        }

        setContentView(R.layout.activity_course_notes);

        // Check if we were opened via an external share/open intent with a .md file
        Intent incoming = getIntent();
        String action = incoming != null ? incoming.getAction() : null;
        Uri externalUri = null;
        if (Intent.ACTION_VIEW.equals(action)) {
            externalUri = incoming.getData();
        } else if (Intent.ACTION_SEND.equals(action)) {
            externalUri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
        }

        isExternalImport = (externalUri != null);

        courseCode = getIntent().getStringExtra("course_code");
        courseTitle = getIntent().getStringExtra("course_title");

        if (externalUri == null && courseCode == null) {
            Toast.makeText(this, "Error: Course code is missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Bind views
        TextView tvTitle = findViewById(R.id.tv_course_title);
        TextView tvCode = findViewById(R.id.tv_course_code);
        layoutBlocks = findViewById(R.id.layout_blocks);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnShare = findViewById(R.id.btn_share);
        fabAdd = findViewById(R.id.fab_add);
        layoutFabCamera = findViewById(R.id.layout_fab_camera);
        layoutFabGallery = findViewById(R.id.layout_fab_gallery);
        layoutFabMarkdown = findViewById(R.id.layout_fab_markdown);
        fabScrim = findViewById(R.id.fab_scrim);

        if (courseCode != null) {
            tvTitle.setText(courseTitle != null ? courseTitle : "Course Notes");
            tvCode.setText(courseCode);
        }

        // Resolve colors
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        colorOnSurface = typedValue.data;

        // Back
        btnBack.setOnClickListener(v -> finish());

        // Share
        btnShare.setOnClickListener(v -> showShareBottomSheet());

        // Main FAB
        fabAdd.setOnClickListener(v -> toggleFabMenu());

        // Mini-FABs
        ImageButton fabCamera = layoutFabCamera.findViewById(R.id.fab_camera);
        ImageButton fabGallery = layoutFabGallery.findViewById(R.id.fab_gallery);
        ImageButton fabMarkdown = layoutFabMarkdown.findViewById(R.id.fab_import_markdown);

        fabCamera.setOnClickListener(v -> { collapseFabMenu(); requestCameraAndCapture(); });
        fabGallery.setOnClickListener(v -> { collapseFabMenu(); selectImageLauncher.launch("image/*"); });
        fabMarkdown.setOnClickListener(v -> { collapseFabMenu(); markdownFileLauncher.launch("*/*"); });

        // Scrim dismisses the FAB menu
        fabScrim.setOnClickListener(v -> collapseFabMenu());

        if (externalUri != null) {
            // Opened from outside: prompt course picker, then import
            final Uri finalUri = externalUri;
            String inferredName = inferCourseNameFromUri(finalUri);
            handleExternalMarkdownImport(finalUri, inferredName);
        } else {
            loadNotes();
        }
    }

    // ── FAB Menu ──────────────────────────────────────────────────────────────

    private void toggleFabMenu() {
        if (fabExpanded) collapseFabMenu();
        else expandFabMenu();
    }

    private void expandFabMenu() {
        fabExpanded = true;
        fabScrim.setVisibility(View.VISIBLE);
        fabScrim.setAlpha(0f);
        fabScrim.animate().alpha(1f).setDuration(200).start();

        // Rotate + icon to ×
        fabAdd.animate().rotation(45f).setDuration(200).setInterpolator(new AccelerateDecelerateInterpolator()).start();

        showMiniFab(layoutFabCamera, 0);
        showMiniFab(layoutFabGallery, 60);
        showMiniFab(layoutFabMarkdown, 120);
    }

    private void collapseFabMenu() {
        fabExpanded = false;
        fabScrim.animate().alpha(0f).setDuration(200).withEndAction(() -> fabScrim.setVisibility(View.GONE)).start();
        fabAdd.animate().rotation(0f).setDuration(200).setInterpolator(new AccelerateDecelerateInterpolator()).start();

        hideMiniFab(layoutFabCamera);
        hideMiniFab(layoutFabGallery);
        hideMiniFab(layoutFabMarkdown);
    }

    private void showMiniFab(View layout, long delay) {
        layout.setVisibility(View.VISIBLE);
        layout.setAlpha(0f);
        layout.setTranslationY(30f);
        layout.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(180)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void hideMiniFab(View layout) {
        layout.animate()
                .alpha(0f)
                .translationY(30f)
                .setDuration(150)
                .withEndAction(() -> {
                    layout.setVisibility(View.INVISIBLE);
                    layout.setAlpha(0f);
                    layout.setTranslationY(0f);
                })
                .start();
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void requestCameraAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(new String[]{Manifest.permission.CAMERA});
        }
    }

    private void launchCamera() {
        try {
            File dir = new File(getFilesDir(), "CourseNotes");
            if (!dir.exists()) dir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File photoFile = new File(dir, "CAM_" + timestamp + ".jpg");
            cameraTempUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
            cameraLauncher.launch(cameraTempUri);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to launch camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    private void showShareBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);

        // Build a simple bottom sheet manually
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(0, 24, 0, 48);

        // Title
        TextView title = new TextView(this);
        title.setText("Share Note");
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(64, 24, 64, 24);
        title.setTextColor(com.google.android.material.color.MaterialColors.getColor(title, com.google.android.material.R.attr.colorOnSurface));
        container.addView(title);

        // Divider
        View div = new View(this);
        div.setLayoutParams(new android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
        div.setBackgroundColor(com.google.android.material.color.MaterialColors.getColor(div, com.google.android.material.R.attr.colorOutlineVariant));
        container.addView(div);

        // Option 1: Copy Text
        addShareOption(container, "📋  Copy text",
                "Only text will be copied — images are not included",
                v -> {
                    dialog.dismiss();
                    copyTextToClipboard();
                });

        // Option 2: Export Markdown
        addShareOption(container, "📄  Export as Markdown",
                "A self-contained .md file — text and images both included",
                v -> {
                    dialog.dismiss();
                    exportAndShareMarkdown();
                });

        dialog.setContentView(container);
        dialog.show();
    }

    private void addShareOption(android.widget.LinearLayout parent, String label, String subtitle, View.OnClickListener listener) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.setPadding(64, 24, 64, 24);
        row.setBackground(obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground}).getDrawable(0));
        row.setClickable(true);
        row.setFocusable(true);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(16f);
        tvLabel.setTextColor(com.google.android.material.color.MaterialColors.getColor(tvLabel, com.google.android.material.R.attr.colorOnSurface));
        row.addView(tvLabel);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText(subtitle);
        tvSubtitle.setTextSize(12f);
        tvSubtitle.setTextColor(com.google.android.material.color.MaterialColors.getColor(tvSubtitle, com.google.android.material.R.attr.colorOnSurfaceVariant));
        tvSubtitle.setPadding(0, 4, 0, 0);
        row.addView(tvSubtitle);

        row.setOnClickListener(listener);
        parent.addView(row);
    }

    private void copyTextToClipboard() {
        String text = MarkdownExporter.extractPlainText(getBlocksJsonString());
        if (text.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Course Notes", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void exportAndShareMarkdown() {
        String blocksJson = getBlocksJsonString();
        io.reactivex.rxjava3.core.Single.fromCallable(() ->
                        MarkdownExporter.exportToMarkdown(this, courseTitle, blocksJson))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result.largeFileWarning) {
                        Toast.makeText(this, "Note: The exported file is large (>5 MB) because of embedded images.", Toast.LENGTH_LONG).show();
                    }
                    if (result.skippedImages > 0) {
                        Toast.makeText(this, result.skippedImages + " image(s) could not be embedded and were skipped.", Toast.LENGTH_LONG).show();
                    }
                    shareMarkdownFile(result.file);
                }, throwable -> {
                    Toast.makeText(this, "Export failed: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void shareMarkdownFile(File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/markdown");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, courseTitle + " Notes");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share as Markdown"));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Markdown Import ───────────────────────────────────────────────────────

    private void importMarkdownFromUri(Uri uri) {
        io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                    InputStream is = getContentResolver().openInputStream(uri);
                    if (is == null) throw new Exception("Could not open file");
                    byte[] bytes = org.apache.commons.io.IOUtils.toByteArray(is);
                    is.close();
                    return new String(bytes, "UTF-8");
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(content -> {
                    // Bypass confirm dialog since we concatenate/insert at cursor without replacing
                    processMarkdownImport(content);
                }, throwable -> {
                    Toast.makeText(this, "Failed to read file: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private String appendBlocks(String existingJson, String newJson) {
        try {
            org.json.JSONArray existingArray = (existingJson == null || existingJson.trim().isEmpty())
                    ? new org.json.JSONArray()
                    : new org.json.JSONArray(existingJson);
            org.json.JSONArray newArray = (newJson == null || newJson.trim().isEmpty())
                    ? new org.json.JSONArray()
                    : new org.json.JSONArray(newJson);

            for (int i = 0; i < newArray.length(); i++) {
                existingArray.put(newArray.getJSONObject(i));
            }
            return existingArray.toString();
        } catch (Exception e) {
            android.util.Log.e("CourseNotesActivity", "appendBlocks failed", e);
            return existingJson;
        }
    }

    private void insertMarkdownBlocksAtCursor(String newBlocksJson) {
        if (newBlocksJson == null || newBlocksJson.trim().isEmpty()) return;
        try {
            org.json.JSONArray newBlocks = new org.json.JSONArray(newBlocksJson);
            if (newBlocks.length() == 0) return;

            EditText currentEditText = focusedEditText;
            if (currentEditText != null) {
                int index = layoutBlocks.indexOfChild(currentEditText);
                int cursorPos = currentEditText.getSelectionStart();
                String fullText = currentEditText.getText().toString();

                String beforeText = fullText.substring(0, cursorPos);
                String afterText = fullText.substring(cursorPos);

                currentEditText.setText(beforeText);

                int insertIndex = index + 1;
                for (int i = 0; i < newBlocks.length(); i++) {
                    org.json.JSONObject block = newBlocks.getJSONObject(i);
                    String type = block.getString("type");
                    String value = block.getString("value");

                    if ("text".equals(type)) {
                        EditText et = createEditTextBlock(value);
                        layoutBlocks.addView(et, insertIndex);
                        insertIndex++;
                    } else if ("image".equals(type)) {
                        View imageBlockView = inflateImageBlock(value);
                        if (imageBlockView != null) {
                            layoutBlocks.addView(imageBlockView, insertIndex);
                            insertIndex++;
                        }
                    }
                }

                EditText newEditText = createEditTextBlock(afterText);
                layoutBlocks.addView(newEditText, insertIndex);
                newEditText.requestFocus();
                newEditText.setSelection(0);
            } else {
                for (int i = 0; i < newBlocks.length(); i++) {
                    org.json.JSONObject block = newBlocks.getJSONObject(i);
                    String type = block.getString("type");
                    String value = block.getString("value");

                    if ("text".equals(type)) {
                        layoutBlocks.addView(createEditTextBlock(value));
                    } else if ("image".equals(type)) {
                        View imageBlockView = inflateImageBlock(value);
                        if (imageBlockView != null) {
                            layoutBlocks.addView(imageBlockView);
                        }
                    }
                }
                ensureLastBlockIsText();
            }

            mergeConsecutiveEditTexts();
            autoSave();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to insert markdown blocks", Toast.LENGTH_SHORT).show();
        }
    }

    private void processMarkdownImport(String markdownContent) {
        io.reactivex.rxjava3.core.Single.fromCallable(() ->
                        MarkdownImporter.importFromMarkdown(markdownContent, this))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(newBlocksJson -> {
                    // In-app FAB import: insert at cursor
                    insertMarkdownBlocksAtCursor(newBlocksJson);
                    Toast.makeText(this, "Notes imported at cursor", Toast.LENGTH_SHORT).show();
                }, throwable -> {
                    Toast.makeText(this, "Import failed: " + throwable.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void processExternalMarkdownImport(Uri uri, String code, String title) {
        // Run entirely on a background thread to avoid RxJava chaining issues
        new Thread(() -> {
            try {
                // 1. Parse the markdown file into blocks JSON
                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) throw new Exception("Could not open file");
                byte[] bytes = org.apache.commons.io.IOUtils.toByteArray(is);
                is.close();
                String markdownContent = new String(bytes, "UTF-8");
                String newBlocksJson = MarkdownImporter.importFromMarkdown(markdownContent, this);

                // 2. Load existing note from DB (synchronously on this bg thread)
                AppDatabase db = AppDatabase.getInstance(this);
                CourseNote existingNote = null;
                try {
                    existingNote = db.courseNotesDao().getNoteByCourse(code).blockingGet();
                } catch (Exception ignored) {
                    // No existing note - existingNote stays null
                }

                // 3. Merge or create
                String mergedJson;
                if (existingNote != null && existingNote.content != null && !existingNote.content.trim().isEmpty()) {
                    mergedJson = appendBlocks(existingNote.content, newBlocksJson);
                } else {
                    mergedJson = newBlocksJson;
                }

                // 4. Save to DB
                CourseNote noteToSave = new CourseNote();
                noteToSave.courseCode = code;
                noteToSave.content = mergedJson;
                db.courseNotesDao().insert(noteToSave).blockingAwait();

                // 5. Update UI on main thread
                final String finalJson = mergedJson;
                runOnUiThread(() -> {
                    courseCode = code;
                    courseTitle = title;
                    TextView tvTitle = findViewById(R.id.tv_course_title);
                    TextView tvCode = findViewById(R.id.tv_course_code);
                    if (tvTitle != null) tvTitle.setText(title != null ? title : "Course Notes");
                    if (tvCode != null) tvCode.setText(code);
                    loadBlocksFromJson(finalJson);
                    Toast.makeText(this, "Notes imported successfully!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                android.util.Log.e("CourseNotesActivity", "processExternalMarkdownImport failed", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ── External Import (Open With / Share) ───────────────────────────────────

    private String normalizeForMatching(String input) {
        if (input == null) return "";
        return input.toLowerCase()
                .replaceAll("[^a-z0-9]", "") // Remove all non-alphanumeric chars
                .trim();
    }

    /** Try to guess the course name from the .md filename (strip .md extension). */
    private String inferCourseNameFromUri(Uri uri) {
        if (uri == null) return null;
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try {
                android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx);
                    }
                    cursor.close();
                }
            } catch (Exception ignored) {}
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        if (name != null) {
            try {
                name = android.net.Uri.decode(name);
            } catch (Exception ignored) {}
        }
        if (name != null && name.toLowerCase().endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }

    private String readFirstLineOfMarkdown(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"))) {
            String firstLine = reader.readLine();
            if (firstLine != null) {
                firstLine = firstLine.trim();
                if (firstLine.startsWith("# ")) {
                    return firstLine.substring(2).trim();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getFileNameFromUri(Uri uri) {
        if (uri == null) return null;
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    /**
     * Handle a .md file opened from outside the app.
     * Try to auto-detect the matching course; show course picker dialog.
     */
    private void handleExternalMarkdownImport(Uri uri, String inferredName) {
        io.reactivex.rxjava3.core.Single.zip(
                AppDatabase.getInstance(this).coursesDao().getCoursesWithTitles().subscribeOn(Schedulers.io()),
                io.reactivex.rxjava3.core.Single.fromCallable(() -> {
                    String header = readFirstLineOfMarkdown(uri);
                    return header != null ? header : "";
                }).subscribeOn(Schedulers.io()),
                (courses, headerTitle) -> new Object[] { courses, headerTitle }
        )
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(result -> {
            @SuppressWarnings("unchecked")
            java.util.List<app.naevis.vitstudent.models.CourseBasicInfo> courses =
                    (java.util.List<app.naevis.vitstudent.models.CourseBasicInfo>) result[0];
            String headerTitle = (String) result[1];

            if (courses.isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("No Courses Found")
                        .setMessage("To import Markdown notes, you must first sync your timetable/course data inside the app.\n\nPlease open the app, sync your VTOP account data, and then import this file.")
                        .setPositiveButton("OK", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
                return;
            }

            // Try to find best match
            app.naevis.vitstudent.models.CourseBasicInfo matched = null;
            int defaultIdx = 0;

            // 1. Try to match by header title first
            if (!headerTitle.isEmpty()) {
                String normHeader = normalizeForMatching(headerTitle);
                for (int i = 0; i < courses.size(); i++) {
                    app.naevis.vitstudent.models.CourseBasicInfo c = courses.get(i);
                    String normTitle = normalizeForMatching(c.title);
                    String normCode = normalizeForMatching(c.code);
                    if ((!normTitle.isEmpty() && (normTitle.contains(normHeader) || normHeader.contains(normTitle))) ||
                            (!normCode.isEmpty() && (normCode.contains(normHeader) || normHeader.contains(normCode)))) {
                        matched = c;
                        defaultIdx = i;
                        break;
                    }
                }
            }

            // 2. Fall back to inferred filename if no header match
            if (matched == null && inferredName != null) {
                String normInferred = normalizeForMatching(inferredName);
                if (!normInferred.isEmpty()) {
                    for (int i = 0; i < courses.size(); i++) {
                        app.naevis.vitstudent.models.CourseBasicInfo c = courses.get(i);
                        String normTitle = normalizeForMatching(c.title);
                        String normCode = normalizeForMatching(c.code);
                        if ((!normTitle.isEmpty() && (normTitle.contains(normInferred) || normInferred.contains(normTitle))) ||
                                (!normCode.isEmpty() && (normCode.contains(normInferred) || normInferred.contains(normCode)))) {
                            matched = c;
                            defaultIdx = i;
                            break;
                        }
                    }
                }
            }

            // Build list of course labels
            String[] labels = new String[courses.size()];
            for (int i = 0; i < courses.size(); i++) {
                labels[i] = (courses.get(i).title != null ? courses.get(i).title : "") + "  [" + courses.get(i).code + "]";
            }

            final int[] selectedIdx = {defaultIdx};

            String dialogTitle = "Import Markdown";
            if (matched != null) {
                dialogTitle = "Import to: " + matched.title;
            } else if (inferredName != null && !inferredName.isEmpty()) {
                dialogTitle = "Import " + inferredName;
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle(dialogTitle)
                    .setSingleChoiceItems(labels, selectedIdx[0], (d, i) -> selectedIdx[0] = i)
                    .setNegativeButton("Cancel", (d, w) -> finish())
                    .setPositiveButton("Import", (d, w) -> {
                        app.naevis.vitstudent.models.CourseBasicInfo chosen = courses.get(selectedIdx[0]);
                        processExternalMarkdownImport(uri, chosen.code, chosen.title);
                    })
                    .show();
        }, error -> {
            Toast.makeText(this, "Could not load courses: " + error.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        });
    }

    // ── Notes Loading ─────────────────────────────────────────────────────────

    private void loadNotes() {
        AppDatabase.getInstance(this).courseNotesDao().getNoteByCourse(courseCode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        note -> loadBlocksFromJson(note.content),
                        throwable -> loadBlocksFromJson(null),
                        () -> loadBlocksFromJson(null)
                );
    }

    private void loadBlocksFromJson(String jsonString) {
        layoutBlocks.removeAllViews();
        focusedEditText = null;

        if (jsonString == null || jsonString.trim().isEmpty()) {
            EditText initialEt = createEditTextBlock("");
            layoutBlocks.addView(initialEt);
            initialEt.requestFocus();
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String type = obj.getString("type");
                String value = obj.getString("value");

                if ("text".equals(type)) {
                    EditText et = createEditTextBlock(value);
                    layoutBlocks.addView(et);
                } else if ("image".equals(type)) {
                    View imageBlock = inflateImageBlock(value);
                    if (imageBlock != null) {
                        layoutBlocks.addView(imageBlock);
                    }
                }
            }
            ensureLastBlockIsText();
        } catch (Exception e) {
            e.printStackTrace();
            EditText initialEt = createEditTextBlock("");
            layoutBlocks.addView(initialEt);
            initialEt.requestFocus();
        }
    }

    private void ensureLastBlockIsText() {
        if (layoutBlocks.getChildCount() == 0) {
            layoutBlocks.addView(createEditTextBlock(""));
            return;
        }
        View lastChild = layoutBlocks.getChildAt(layoutBlocks.getChildCount() - 1);
        if (!(lastChild instanceof EditText)) {
            layoutBlocks.addView(createEditTextBlock(""));
        }
    }

    private EditText createEditTextBlock(String initialText) {
        EditText editText = new EditText(this);
        editText.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        editText.setText(initialText);
        editText.setHint("Tap to type notes...");
        editText.setBackground(null);
        editText.setPadding(16, 16, 16, 16);
        editText.setTextSize(16);
        editText.setTextColor(colorOnSurface);
        editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);

        editText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { autoSave(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) focusedEditText = editText;
        });

        return editText;
    }

    private View inflateImageBlock(String path) {
        File file = new File(path);
        if (!file.exists()) return null;

        View view = getLayoutInflater().inflate(R.layout.item_note_image, layoutBlocks, false);
        view.setTag(path);

        ImageView imageView = view.findViewById(R.id.iv_note_image);
        imageView.setImageURI(Uri.fromFile(file));

        imageView.setOnClickListener(v -> {
            Intent intent = new Intent(CourseNotesActivity.this, PhotoViewerActivity.class);
            intent.putExtra("image_path", path);
            startActivity(intent);
        });

        View btnDelete = view.findViewById(R.id.btn_delete_image);
        btnDelete.setOnClickListener(v -> {
            final String stateBeforeDelete = getBlocksJsonString();
            layoutBlocks.removeView(view);
            mergeConsecutiveEditTexts();
            ensureLastBlockIsText();
            autoSave();
            com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content), "Image removed", 7000
            ).setAction("Undo", undoView -> {
                loadBlocksFromJson(stateBeforeDelete);
                autoSaveImmediate();
            }).show();
        });

        return view;
    }

    private void insertImageBlock(Uri uri) {
        String path = saveImageToInternalStorage(uri);
        if (path == null) return;

        EditText currentEditText = focusedEditText;

        if (currentEditText != null) {
            int index = layoutBlocks.indexOfChild(currentEditText);
            int cursorPos = currentEditText.getSelectionStart();
            String fullText = currentEditText.getText().toString();

            String beforeText = fullText.substring(0, cursorPos);
            String afterText = fullText.substring(cursorPos);

            currentEditText.setText(beforeText);

            View imageBlockView = inflateImageBlock(path);
            if (imageBlockView != null) {
                EditText newEditText = createEditTextBlock(afterText);
                layoutBlocks.addView(imageBlockView, index + 1);
                layoutBlocks.addView(newEditText, index + 2);
                newEditText.requestFocus();
                newEditText.setSelection(0);
            }
        } else {
            View imageBlockView = inflateImageBlock(path);
            if (imageBlockView != null) {
                EditText newEditText = createEditTextBlock("");
                layoutBlocks.addView(imageBlockView);
                layoutBlocks.addView(newEditText);
                newEditText.requestFocus();
            }
        }
        autoSave();
    }

    private String saveImageToInternalStorage(Uri uri) {
        try {
            File dir = new File(getFilesDir(), "CourseNotes");
            if (!dir.exists()) dir.mkdirs();

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
            String fileName = "IMG_" + timeStamp + ".jpg";
            File file = new File(dir, fileName);

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream != null) {
                org.apache.commons.io.FileUtils.copyInputStreamToFile(inputStream, file);
                inputStream.close();
                return file.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to import image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    private void mergeConsecutiveEditTexts() {
        for (int i = 0; i < layoutBlocks.getChildCount() - 1; i++) {
            View first = layoutBlocks.getChildAt(i);
            View second = layoutBlocks.getChildAt(i + 1);
            if (first instanceof EditText && second instanceof EditText) {
                EditText etFirst = (EditText) first;
                EditText etSecond = (EditText) second;
                String text = etFirst.getText().toString();
                String nextText = etSecond.getText().toString();
                String merged = text;
                if (!text.isEmpty() && !nextText.isEmpty()) merged += "\n";
                merged += nextText;
                etFirst.setText(merged);
                layoutBlocks.removeView(etSecond);
                i--;
            }
        }
    }

    private String getBlocksJsonString() {
        try {
            JSONArray jsonArray = new JSONArray();
            for (int i = 0; i < layoutBlocks.getChildCount(); i++) {
                View child = layoutBlocks.getChildAt(i);
                if (child instanceof EditText) {
                    EditText et = (EditText) child;
                    JSONObject obj = new JSONObject();
                    obj.put("type", "text");
                    obj.put("value", et.getText().toString());
                    jsonArray.put(obj);
                } else if (child.getTag() != null) {
                    JSONObject obj = new JSONObject();
                    obj.put("type", "image");
                    obj.put("value", child.getTag().toString());
                    jsonArray.put(obj);
                }
            }
            return jsonArray.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    // ── Auto-Save ────────────────────────────────────────────────────────────

    private final android.os.Handler saveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable saveRunnable = null;

    private void autoSave() {
        if (saveRunnable != null) saveHandler.removeCallbacks(saveRunnable);
        saveRunnable = this::autoSaveImmediate;
        saveHandler.postDelayed(saveRunnable, 500);
    }

    private void autoSaveImmediate() {
        try {
            String jsonContent = getBlocksJsonString();
            CourseNote note = new CourseNote();
            note.courseCode = courseCode;
            note.content = jsonContent;

            AppDatabase.getInstance(CourseNotesActivity.this).courseNotesDao().insert(note)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> {}, Throwable::printStackTrace);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
