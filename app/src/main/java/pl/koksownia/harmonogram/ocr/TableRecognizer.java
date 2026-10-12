package pl.koksownia.harmonogram.ocr;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.exifinterface.media.ExifInterface;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Rozpoznaje tekst na zdjęciu (ML Kit, offline) i przekazuje go do {@link ScheduleParser}. */
public final class TableRecognizer {

    /** Dłuższy bok zdjęcia po zmniejszeniu – wystarcza do odczytu tabeli, a nie zapycha pamięci. */
    private static final int MAX_SIDE = 2560;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** Wywołania zawsze na wątku głównym. */
    public interface Callback {
        void onResult(ParseResult result);

        void onError(Throwable e);
    }

    private TableRecognizer() {
    }

    public static void recognize(Context context, Uri uri, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            InputImage image;
            try {
                image = loadImage(app.getContentResolver(), uri);
            } catch (Throwable e) { // także OutOfMemoryError
                MAIN.post(() -> callback.onError(e));
                return;
            }
            MAIN.post(() -> process(image, callback));
        });
    }

    private static void process(InputImage image, Callback callback) {
        TextRecognizer recognizer;
        try {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        } catch (Throwable e) {
            callback.onError(e);
            return;
        }
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    try {
                        callback.onResult(ScheduleParser.parse(tokensOf(text), linesOf(text)));
                    } catch (Throwable e) {
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(callback::onError)
                .addOnCompleteListener(task -> {
                    try {
                        recognizer.close();
                    } catch (Exception ignored) {
                        // nic do zrobienia
                    }
                });
    }

    private static List<OcrToken> tokensOf(Text text) {
        List<OcrToken> tokens = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                for (Text.Element element : line.getElements()) {
                    Rect box = element.getBoundingBox();
                    if (box == null) continue;
                    tokens.add(new OcrToken(element.getText(), box.left, box.top, box.right, box.bottom));
                }
            }
        }
        return tokens;
    }

    private static List<String> linesOf(Text text) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) {
            for (Text.Line line : block.getLines()) lines.add(line.getText());
        }
        return lines;
    }

    /** Wczytuje zdjęcie zmniejszone do {@link #MAX_SIDE} i z obrotem odczytanym z EXIF. */
    private static InputImage loadImage(ContentResolver resolver, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = open(resolver, uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new IOException("Nie udało się odczytać zdjęcia");
        }

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = open(resolver, uri)) {
            bitmap = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bitmap == null) throw new IOException("Nie udało się odczytać zdjęcia");

        int longSide = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longSide > MAX_SIDE) {
            float scale = (float) MAX_SIDE / longSide;
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
            if (scaled != bitmap) bitmap.recycle();
            bitmap = scaled;
        }

        int rotation = 0;
        try (InputStream in = open(resolver, uri)) {
            rotation = new ExifInterface(in).getRotationDegrees();
        } catch (Exception ignored) {
            // brak EXIF – bez obrotu
        }
        return InputImage.fromBitmap(bitmap, rotation);
    }

    private static InputStream open(ContentResolver resolver, Uri uri) throws IOException {
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IOException("Nie można otworzyć zdjęcia");
        return in;
    }
}
