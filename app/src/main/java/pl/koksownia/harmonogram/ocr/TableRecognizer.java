package pl.koksownia.harmonogram.ocr;

import android.content.Context;
import android.graphics.Rect;
import android.net.Uri;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.List;

/** Rozpoznaje tekst na zdjęciu (ML Kit, offline) i przekazuje go do {@link ScheduleParser}. */
public final class TableRecognizer {

    public interface Callback {
        void onResult(ParseResult result);

        void onError(Exception e);
    }

    private TableRecognizer() {
    }

    public static void recognize(Context context, Uri uri, Callback callback) {
        InputImage image;
        try {
            image = InputImage.fromFilePath(context, uri);
        } catch (Exception e) {
            callback.onError(e);
            return;
        }
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(text -> {
                    List<OcrToken> tokens = new ArrayList<>();
                    List<String> lines = new ArrayList<>();
                    for (Text.TextBlock block : text.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            lines.add(line.getText());
                            for (Text.Element element : line.getElements()) {
                                Rect box = element.getBoundingBox();
                                if (box == null) continue;
                                tokens.add(new OcrToken(element.getText(),
                                        box.left, box.top, box.right, box.bottom));
                            }
                        }
                    }
                    callback.onResult(ScheduleParser.parse(tokens, lines));
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
}
