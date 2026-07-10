package app.naevis.vitstudent.helpers;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

/**
 * Exports a course note (block JSON) to a self-contained Markdown file.
 * Images are embedded inline as base64 data URIs so the .md file is fully
 * portable — no external file references.
 *
 * Format spec (from user):
 *   ![alt text](data:<mime-type>;base64,<base64-data>)
 *
 * MIME type is detected dynamically from the file header bytes.
 * Encoding is lossless; base64 string contains no line breaks.
 */
public class MarkdownExporter {

    private static final String TAG = "MarkdownExporter";
    private static final long WARN_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    public static class ExportResult {
        public final File file;
        /** True if the output exceeds the 5 MB warning threshold */
        public final boolean largeFileWarning;
        public final int skippedImages;

        public ExportResult(File file, boolean largeFileWarning, int skippedImages) {
            this.file = file;
            this.largeFileWarning = largeFileWarning;
            this.skippedImages = skippedImages;
        }
    }

    /**
     * Export a note to Markdown.
     *
     * @param context     Application context
     * @param courseTitle The title used as the H1 heading and filename
     * @param blocksJson  JSON string representing the note blocks
     * @return ExportResult containing the output file and metadata
     * @throws Exception if the export fails
     */
    public static ExportResult exportToMarkdown(Context context, String courseTitle, String blocksJson) throws Exception {
        // Prepare output directory in cache
        File outDir = new File(context.getCacheDir(), "CourseNotesExport");
        if (!outDir.exists()) outDir.mkdirs();

        // Sanitize filename
        String safeName = sanitizeFilename(courseTitle != null ? courseTitle : "Note");
        File outFile = new File(outDir, safeName + ".md");

        StringBuilder md = new StringBuilder();

        // H1 heading from course title
        if (courseTitle != null && !courseTitle.isEmpty()) {
            md.append("# ").append(courseTitle).append("\n\n");
        }

        int skippedImages = 0;

        if (blocksJson != null && !blocksJson.trim().isEmpty()) {
            JSONArray blocks = new JSONArray(blocksJson);
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                String type = block.optString("type", "text");
                String value = block.optString("value", "");

                if ("text".equals(type)) {
                    if (!value.isEmpty()) {
                        md.append(value).append("\n\n");
                    }
                } else if ("image".equals(type)) {
                    String imagePath = value;
                    try {
                        String embeddedImage = encodeImageToDataUri(imagePath);
                        if (embeddedImage != null) {
                            md.append("![image](").append(embeddedImage).append(")\n\n");
                        } else {
                            skippedImages++;
                            Log.w(TAG, "Skipped missing/unreadable image: " + imagePath);
                        }
                    } catch (Exception e) {
                        skippedImages++;
                        Log.w(TAG, "Skipped image due to error: " + imagePath + " — " + e.getMessage());
                    }
                }
            }
        }

        // Write to file
        byte[] content = md.toString().getBytes("UTF-8");
        FileOutputStream fos = new FileOutputStream(outFile);
        fos.write(content);
        fos.close();

        boolean largeFileWarning = outFile.length() > WARN_SIZE_BYTES;

        return new ExportResult(outFile, largeFileWarning, skippedImages);
    }

    /**
     * Extract only the text content from blocks, no images.
     * Used for the "Copy Text" option.
     */
    public static String extractPlainText(String blocksJson) {
        if (blocksJson == null || blocksJson.trim().isEmpty()) return "";
        try {
            JSONArray blocks = new JSONArray(blocksJson);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < blocks.length(); i++) {
                JSONObject block = blocks.getJSONObject(i);
                if ("text".equals(block.optString("type", "text"))) {
                    String value = block.optString("value", "");
                    if (!value.isEmpty()) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(value);
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "extractPlainText failed", e);
            return "";
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Reads an image file, detects its MIME type, and returns a data URI string.
     * Returns null if the file does not exist or cannot be read.
     */
    private static String encodeImageToDataUri(String imagePath) throws IOException {
        if (imagePath == null) return null;
        File file = new File(imagePath);
        if (!file.exists() || !file.isFile()) return null;

        byte[] bytes = readFileBytes(file);
        if (bytes == null || bytes.length == 0) return null;

        String mimeType = detectMimeType(file, bytes);

        // Base64 encode — NO_WRAP ensures no line breaks in the output string
        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);

        return "data:" + mimeType + ";base64," + base64;
    }

    private static byte[] readFileBytes(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        fis.close();
        return baos.toByteArray();
    }

    private static String detectMimeType(File file, byte[] bytes) {
        // Try URLConnection sniffing using file name
        String guessed = URLConnection.guessContentTypeFromName(file.getName());
        if (guessed != null && guessed.startsWith("image/")) return guessed;

        // Fallback: magic bytes
        if (bytes.length >= 4) {
            if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return "image/jpeg";
            if (bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
            if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "image/gif";
            if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F') return "image/webp";
        }

        // Safe fallback
        return "image/jpeg";
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-.]", "_").replaceAll("_+", "_").trim();
    }
}
