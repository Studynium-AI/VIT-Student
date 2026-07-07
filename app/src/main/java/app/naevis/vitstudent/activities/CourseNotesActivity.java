package app.naevis.vitstudent.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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
import app.naevis.vitstudent.models.CourseNote;

public class CourseNotesActivity extends AppCompatActivity {

    private String courseCode;
    private String courseTitle;
    private LinearLayout layoutBlocks;
    private EditText focusedEditText;
    private int colorOnSurface;

    private final ActivityResultLauncher<String> selectImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    insertImageBlock(uri);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Handle show when locked for lockscreen UI integration
        boolean fromLockscreen = getIntent().getBooleanExtra("from_lockscreen", false);
        if (fromLockscreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true);
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            }
        }

        setContentView(R.layout.activity_course_notes);

        courseCode = getIntent().getStringExtra("course_code");
        courseTitle = getIntent().getStringExtra("course_title");

        if (courseCode == null) {
            Toast.makeText(this, "Error: Course code is missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup views
        TextView tvTitle = findViewById(R.id.tv_course_title);
        TextView tvCode = findViewById(R.id.tv_course_code);
        layoutBlocks = findViewById(R.id.layout_blocks);
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnImportImage = findViewById(R.id.btn_import_image);

        tvTitle.setText(courseTitle != null ? courseTitle : "Course Notes");
        tvCode.setText(courseCode);

        btnBack.setOnClickListener(v -> finish());
        btnImportImage.setOnClickListener(v -> selectImageLauncher.launch("image/*"));

        // Resolve colors
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        colorOnSurface = typedValue.data;

        // Load existing notes
        loadNotes();
    }

    private void loadNotes() {
        AppDatabase.getInstance(this).courseNotesDao().getNoteByCourse(courseCode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        note -> {
                            loadBlocksFromJson(note.content);
                        },
                        throwable -> {
                            // Error or not found (Maybe behaves as empty)
                            loadBlocksFromJson(null);
                        },
                        () -> {
                            // Complete with no elements (empty)
                            loadBlocksFromJson(null);
                        }
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

            // Ensure last block is always an EditText so the user can continue typing
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
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                autoSave();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                focusedEditText = editText;
            }
        });

        return editText;
    }

    private View inflateImageBlock(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }

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
                    findViewById(android.R.id.content),
                    "Image removed",
                    7000
            ).setAction("Undo", undoView -> {
                loadBlocksFromJson(stateBeforeDelete);
                // Trigger auto-save immediately to persist the restored note state
                autoSaveImmediate();
            }).show();
        });

        return view;
    }

    private void insertImageBlock(Uri uri) {
        String path = saveImageToInternalStorage(uri);
        if (path == null) {
            return;
        }

        EditText currentEditText = focusedEditText;

        if (currentEditText != null) {
            int index = layoutBlocks.indexOfChild(currentEditText);
            int cursorPos = currentEditText.getSelectionStart();
            String fullText = currentEditText.getText().toString();

            String beforeText = fullText.substring(0, cursorPos);
            String afterText = fullText.substring(cursorPos);

            // Update current EditText text to beforeText
            currentEditText.setText(beforeText);

            // Create image view layout
            View imageBlockView = inflateImageBlock(path);
            if (imageBlockView != null) {
                // Create new EditText for afterText
                EditText newEditText = createEditTextBlock(afterText);

                layoutBlocks.addView(imageBlockView, index + 1);
                layoutBlocks.addView(newEditText, index + 2);

                newEditText.requestFocus();
                newEditText.setSelection(0);
            }
        } else {
            // Append image block
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
            if (!dir.exists()) {
                dir.mkdirs();
            }

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
                if (!text.isEmpty() && !nextText.isEmpty()) {
                    merged += "\n";
                }
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

    private final android.os.Handler saveHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable saveRunnable = null;

    private void autoSave() {
        if (saveRunnable != null) {
            saveHandler.removeCallbacks(saveRunnable);
        }
        saveRunnable = () -> {
            autoSaveImmediate();
        };
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
                    .subscribe(() -> {
                        // Saved
                    }, throwable -> {
                        throwable.printStackTrace();
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
