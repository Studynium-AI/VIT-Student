package app.naevis.vitstudent.activities;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import app.naevis.vitstudent.R;

public class PhotoViewerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow showing over keyguard/lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        setContentView(R.layout.activity_photo_viewer);

        String imagePath = getIntent().getStringExtra("image_path");
        if (imagePath == null) {
            Toast.makeText(this, "Error: Image path is missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ImageView imageView = findViewById(R.id.iv_photo_fullscreen);
        ImageButton btnBack = findViewById(R.id.btn_photo_back);

        File file = new File(imagePath);
        if (file.exists()) {
            imageView.setImageURI(Uri.fromFile(file));
        } else {
            Toast.makeText(this, "Error: Image file not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnBack.setOnClickListener(v -> finish());
    }
}
