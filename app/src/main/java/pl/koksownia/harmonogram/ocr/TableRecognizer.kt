package pl.koksownia.harmonogram.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Rozpoznaje tekst na zdjęciu (ML Kit, offline) i przekazuje go do [ScheduleParser]. */
object TableRecognizer {

    fun recognize(
        context: Context,
        uri: Uri,
        onResult: (ParseResult) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            onError(e)
            return
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val tokens = mutableListOf<OcrToken>()
                val lines = mutableListOf<String>()
                for (block in text.textBlocks) {
                    for (line in block.lines) {
                        lines += line.text
                        for (element in line.elements) {
                            val box = element.boundingBox ?: continue
                            tokens += OcrToken(
                                element.text,
                                box.left.toFloat(), box.top.toFloat(),
                                box.right.toFloat(), box.bottom.toFloat(),
                            )
                        }
                    }
                }
                onResult(ScheduleParser.parse(tokens, lines))
            }
            .addOnFailureListener { onError(it) }
            .addOnCompleteListener { recognizer.close() }
    }
}
