# Harmonogram komór

Aplikacja na Androida (Java) dla obsługi baterii koksowniczej. Robisz zdjęcie tabeli
„Raport Obsługi Komór” (ekran *Zmianowy Raport Obsługi Komór*), a aplikacja:

1. **Rozpoznaje tabelę** (OCR offline – Google ML Kit) i wyciąga z niej **numer komory** oraz
   **planowaną godzinę wypychu** (kolumna *Plan. Godz. Wypch.*), a także datę raportu.
2. **Układa je w tabeli** – każdy wiersz można poprawić, usunąć, dodać ręcznie albo oznaczyć jako wypchnięty.
3. **Widżet na ekranie głównym** pokazuje **3 najbliższe komory** do wypchnięcia.
4. **Stałe powiadomienie** pokazuje **następną komorę**, odliczanie do jej planowanej godziny
   (po przekroczeniu – opóźnienie) i przycisk **„Wypchnięto”**, który zatwierdza komorę
   i przechodzi do następnej. Przycisk **„Cofnij”** odwraca ostatnie zatwierdzenie.

## Instalacja

Każdy push buduje APK w GitHub Actions. Gotowy plik `harmonogram-komor.apk` jest w zakładce
**Releases** repozytorium (najnowsze wydanie). Pobierz go na telefon i zainstaluj
(trzeba zezwolić na instalację z nieznanych źródeł). Kolejne wersje instalują się jako aktualizacja.

Widżet: przytrzymaj palec na ekranie głównym → *Widżety* → *Harmonogram komór* → *Najbliższe komory*.

Przy pierwszym uruchomieniu zezwól na powiadomienia (Android 13+).

## Wskazówki do zdjęcia

- Kadruj tak, żeby kolumny *Nr Komory* i *Plan. Godz. Wypch.* były ostre i w całości widoczne.
- Zdjęcie możesz zrobić lekko pod kątem – parser to toleruje – ale im prościej, tym lepiej.
- Po wczytaniu porównaj tabelę ze zdjęciem; błędny wiersz popraw stuknięciem.

## Budowanie lokalnie

Wymagany Android SDK (API 35) i JDK 17:

```
./gradlew testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Struktura

- `ocr/ScheduleParser.java` – logika wyciągania (komora, godzina) z wyniku OCR (testy w `app/src/test`).
- `ocr/TableRecognizer.java` – uruchomienie ML Kit na zdjęciu.
- `Schedule.java`, `Chamber.java`, `ScheduleStore.java` – zapis harmonogramu i stanu wypchnięć.
- `NotificationHelper.java`, `PushReceiver.java` – powiadomienie z przyciskami.
- `ScheduleWidgetProvider.java` – widżet ekranu głównego.
- `MainActivity.java` – ekran z tabelą.
