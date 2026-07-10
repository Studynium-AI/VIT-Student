package app.naevis.vitstudent.helpers;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports a Markdown string into the note block JSON format used by CourseNotesActivity.
 *
 * Handles:
 *  - Plain text lines → text blocks
 *  - Inline base64 data URI images → decoded to internal storage files → image blocks
 *  - Standard markdown image lines ![alt](path) where path is a local file path
 */
public class MarkdownImporter {

    private static final String TAG = "MarkdownImporter";

    // Matches: ![anything](data:<mime>;base64,<base64data>)
    private static final Pattern DATA_URI_PATTERN =
            Pattern.compile("!\\[([^\\]]*)\\]\\(data:([^;]+);base64,([A-Za-z0-9+/=]+)\\)");

    // Matches: ![anything](some/path)  (non-data URI image)
    private static final Pattern LOCAL_IMAGE_PATTERN =
            Pattern.compile("!\\[([^\\]]*)\\]\\((?!data:)([^)]+)\\)");

    /**
     * Parse a markdown string into note blocks JSON.
     *
     * @param markdownContent The full markdown text
     * @param context         Used for writing decoded images to internal storage
     * @param appendMode      If true, returned JSON should be appended; caller handles merging
     * @return blocks JSON string compatible with CourseNotesActivity
     */
    public static String importFromMarkdown(String markdownContent, Context context) {
        JSONArray blocks = new JSONArray();
        if (markdownContent == null || markdownContent.trim().isEmpty()) {
            return blocks.toString();
        }

        try {
            String[] lines = markdownContent.split("\\r?\\n", -1);
            StringBuilder currentText = new StringBuilder();

            for (String line : lines) {
                // Check if this line is an image (data URI)
                Matcher dataMatcher = DATA_URI_PATTERN.matcher(line.trim());
                if (dataMatcher.find()) {
                    // Flush accumulated text first
                    if (currentText.length() > 0) {
                        appendTextBlock(blocks, currentText.toString().trim());
                        currentText.setLength(0);
                    }

                    // Decode and save the image
                    String mimeType = dataMatcher.group(2);
                    String base64Data = dataMatcher.group(3);
                    String savedPath = decodeAndSaveImage(context, base64Data, mimeType);

                    if (savedPath != null) {
                        appendImageBlock(blocks, savedPath);
                    } else {
                        Log.w(TAG, "Skipped image: failed to decode/save");
                    }
                    continue;
                }

                // Check if this line is a local image reference
                Matcher localMatcher = LOCAL_IMAGE_PATTERN.matcher(line.trim());
                if (localMatcher.find()) {
                    if (currentText.length() > 0) {
                        appendTextBlock(blocks, currentText.toString().trim());
                        currentText.setLength(0);
                    }
                    String path = localMatcher.group(2);
                    if (path != null && new File(path).exists()) {
                        appendImageBlock(blocks, path);
                    }
                    // Otherwise skip (file not on this device)
                    continue;
                }

                // Regular text line
                currentText.append(line).append("\n");
            }

            // Flush any remaining text
            if (currentText.length() > 0) {
                String remaining = currentText.toString().trim();
                if (!remaining.isEmpty()) {
                    appendTextBlock(blocks, remaining);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "importFromMarkdown failed", e);
        }

        return blocks.toString();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static void appendTextBlock(JSONArray blocks, String text) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "text");
            obj.put("value", text);
            blocks.put(obj);
        } catch (Exception e) {
            Log.e(TAG, "appendTextBlock", e);
        }
    }

    private static void appendImageBlock(JSONArray blocks, String path) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", "image");
            obj.put("value", path);
            blocks.put(obj);
        } catch (Exception e) {
            Log.e(TAG, "appendImageBlock", e);
        }
    }

    /**
     * Decode a base64 image string and save it to internal storage.
     *
     * @param context  Application context
     * @param base64   The base64-encoded image data (no line breaks expected)
     * @param mimeType MIME type such as "image/jpeg" or "image/png"
     * @return Absolute path to the saved file, or null on failure
     */
    private static String decodeAndSaveImage(Context context, String base64, String mimeType) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);

            File dir = new File(context.getFilesDir(), "CourseNotes");
            if (!dir.exists()) dir.mkdirs();

            String ext = extensionForMime(mimeType);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(new Date());
            String fileName = "IMG_" + timestamp + ext;
            File file = new File(dir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(bytes);
            fos.close();

            return file.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "decodeAndSaveImage failed", e);
            return null;
        }
    }

    private static String extensionForMime(String mimeType) {
        if (mimeType == null) return ".jpg";
        switch (mimeType) {
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "image/bmp": return ".bmp";
            default: return ".jpg";
        }
    }
}
