package pl.koksownia.harmonogram.ocr;

/** Słowo rozpoznane przez OCR wraz z położeniem na zdjęciu (w pikselach). */
public final class OcrToken {
    public final String text;
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public OcrToken(String text, float left, float top, float right, float bottom) {
        this.text = text;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float cx() {
        return (left + right) / 2f;
    }

    public float cy() {
        return (top + bottom) / 2f;
    }

    public float height() {
        return bottom - top;
    }
}
