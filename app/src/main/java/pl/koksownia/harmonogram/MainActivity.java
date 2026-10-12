package pl.koksownia.harmonogram;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import pl.koksownia.harmonogram.databinding.ActivityMainBinding;
import pl.koksownia.harmonogram.databinding.DialogEditBinding;
import pl.koksownia.harmonogram.ocr.ParseResult;
import pl.koksownia.harmonogram.ocr.ParsedRow;
import pl.koksownia.harmonogram.ocr.ScheduleParser;
import pl.koksownia.harmonogram.ocr.TableRecognizer;

public class MainActivity extends AppCompatActivity {

    private static final int MENU_ADD = 1;
    private static final int MENU_UNDO = 2;
    private static final int MENU_RESET = 3;
    private static final int MENU_CLEAR = 4;

    private interface ListEdit {
        void apply(List<Chamber> list);
    }

    private ActivityMainBinding binding;
    private ChamberAdapter adapter;
    private LinearLayoutManager layoutManager;

    // Zmiany z powiadomienia (przycisk „Wypchnięto”) odświeżają otwartą aplikację.
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (prefs, key) -> runOnUiThread(this::render);

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok)) runOcr(photoUri());
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) runOcr(uri);
            });

    private final ActivityResultLauncher<String> requestNotifications =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    ScheduleUpdater.refresh(this);
                } else {
                    toast("Bez zgody na powiadomienia nie pokażę następnej komory w pasku powiadomień");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashLog.install(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        int pad = Math.round(12 * getResources().getDisplayMetrics().density);
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        // Białe ikony na pasku stanu (pod nim jest ceglasty pasek aplikacji).
        WindowCompat.getInsetsController(getWindow(), binding.getRoot()).setAppearanceLightStatusBars(false);
        // Od Androida 15 aplikacja rysuje się pod paskami systemowymi – odsuwamy treść o ich wysokość.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            binding.toolbar.setPadding(bars.left, bars.top, bars.right, 0);
            binding.content.setPadding(pad + bars.left, pad, pad + bars.right, pad + bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        adapter = new ChamberAdapter(this::onRowClicked);
        layoutManager = new LinearLayoutManager(this);
        binding.list.setLayoutManager(layoutManager);
        binding.list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        binding.list.setAdapter(adapter);

        binding.btnCamera.setOnClickListener(v -> takePicture.launch(photoUri()));
        binding.btnGallery.setOnClickListener(v -> pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        binding.btnPushNext.setOnClickListener(v -> {
            ScheduleStore.markNextDone(this);
            ScheduleUpdater.refresh(this);
        });
        binding.txtDate.setOnClickListener(v -> pickDate());

        showLastCrash();
        NotificationHelper.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canNotify(this)) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ScheduleStore.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener);
        render();
        ScheduleUpdater.refresh(this);
    }

    @Override
    protected void onStop() {
        ScheduleStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
        super.onStop();
    }

    private void render() {
        Schedule s = ScheduleStore.load(this);
        adapter.submit(s);
        binding.txtDate.setText("Data: " + s.date + "  ✎");
        boolean empty = s.chambers.isEmpty();
        binding.txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.header.setVisibility(empty ? View.GONE : View.VISIBLE);

        int next = s.nextIndex();
        binding.btnPushNext.setEnabled(next >= 0);
        if (next >= 0) {
            Chamber c = s.chambers.get(next);
            binding.btnPushNext.setText("Wypchnięto komorę " + c.number + "  (" + c.planTime + ")");
            layoutManager.scrollToPositionWithOffset(Math.max(0, next - 2), 0);
        } else {
            binding.btnPushNext.setText(empty ? "Brak komór" : "Wszystkie komory wypchnięte");
        }
    }

    // ---------- OCR ----------

    /** Stała ścieżka, więc adres zdjęcia przetrwa nawet zamknięcie aplikacji przez system w trakcie robienia zdjęcia. */
    private Uri photoUri() {
        File dir = new File(getCacheDir(), "images");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider",
                new File(dir, "harmonogram.jpg"));
    }

    private void runOcr(Uri uri) {
        setBusy(true, "Rozpoznawanie tabeli…");
        TableRecognizer.recognize(this, uri, new TableRecognizer.Callback() {
            @Override
            public void onResult(ParseResult result) {
                setBusy(false, null);
                onOcrResult(result);
            }

            @Override
            public void onError(Throwable e) {
                setBusy(false, null);
                if (e instanceof OutOfMemoryError) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Nie udało się odczytać zdjęcia")
                            .setMessage("Za mało pamięci na to zdjęcie – spróbuj zrzutu ekranu albo mniejszego zdjęcia.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    showError("Nie udało się odczytać zdjęcia", CrashLog.describe(MainActivity.this, e));
                }
            }
        });
    }

    private void onOcrResult(ParseResult result) {
        if (result.rows.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Nie rozpoznano tabeli")
                    .setMessage("Zrób zdjęcie bliżej i prosto, tak żeby kolumny „Nr komory” i "
                            + "„Plan. godz. wypch.” były ostre. Komory możesz też dodać ręcznie z menu.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        LocalDate date = result.date != null ? result.date : LocalDate.now();
        StringBuilder preview = new StringBuilder();
        List<Chamber> chambers = new ArrayList<>();
        for (ParsedRow r : result.rows) {
            preview.append(r.chamber).append("  –  ").append(r.planTime).append('\n');
            chambers.add(new Chamber(r.chamber, r.planTime));
        }
        new AlertDialog.Builder(this)
                .setTitle("Rozpoznano " + result.rows.size() + " komór (" + date + ")")
                .setMessage(preview + "\nZastąpić bieżący harmonogram?")
                .setPositiveButton("Zastąp", (d, w) -> {
                    ScheduleStore.save(this, new Schedule(date, chambers));
                    ScheduleUpdater.refresh(this);
                    render();
                    toast("Wczytano " + chambers.size() + " komór. Sprawdź je – stuknij wiersz, aby poprawić.");
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void setBusy(boolean busy, String message) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.btnCamera.setEnabled(!busy);
        binding.btnGallery.setEnabled(!busy);
        binding.txtStatus.setText(message);
        binding.txtStatus.setVisibility(message != null ? View.VISIBLE : View.GONE);
    }

    // ---------- Edycja ----------

    private void onRowClicked(int index) {
        Schedule s = ScheduleStore.load(this);
        if (index < 0 || index >= s.chambers.size()) return;
        Chamber c = s.chambers.get(index);
        String[] options = {
                "Edytuj",
                c.isDone() ? "Oznacz jako niewypchniętą" : "Oznacz jako wypchniętą",
                "Usuń",
        };
        new AlertDialog.Builder(this)
                .setTitle("Komora " + c.number + "  •  " + c.planTime)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        showEditDialog(index);
                    } else if (which == 1) {
                        modify(list -> list.set(index,
                                c.withDoneAt(c.isDone() ? null : System.currentTimeMillis())));
                    } else {
                        modify(list -> list.remove(index));
                    }
                })
                .show();
    }

    /** index == -1 oznacza dodanie nowej komory na końcu. */
    private void showEditDialog(int index) {
        Schedule s = ScheduleStore.load(this);
        Chamber existing = index >= 0 && index < s.chambers.size() ? s.chambers.get(index) : null;
        DialogEditBinding d = DialogEditBinding.inflate(getLayoutInflater());
        d.editChamber.setText(existing != null ? existing.number : "");
        d.editTime.setText(existing != null ? existing.planTime : "");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Dodaj komorę" : "Edytuj komorę")
                .setView(d.getRoot())
                .setPositiveButton("Zapisz", null)
                .setNegativeButton("Anuluj", null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String number = d.editChamber.getText().toString().trim();
            String time = ScheduleParser.parseTime(
                    ScheduleParser.normalize(d.editTime.getText().toString().trim()), true);
            if (number.isEmpty()) {
                d.editChamber.setError("Podaj numer komory");
            } else if (time == null) {
                d.editTime.setError("Podaj godzinę, np. 6:10");
            } else {
                modify(list -> {
                    if (existing == null) list.add(new Chamber(number, time));
                    else list.set(index, list.get(index).withData(number, time));
                });
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    private void modify(ListEdit edit) {
        ScheduleStore.update(this, s -> {
            List<Chamber> list = new ArrayList<>(s.chambers);
            edit.apply(list);
            return s.withChambers(list);
        });
        ScheduleUpdater.refresh(this);
        render();
    }

    private void pickDate() {
        LocalDate d = ScheduleStore.load(this).date;
        new DatePickerDialog(this, (view, y, m, day) -> {
            ScheduleStore.update(this, s -> s.withDate(LocalDate.of(y, m + 1, day)));
            ScheduleUpdater.refresh(this);
            render();
        }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()).show();
    }

    // ---------- Menu ----------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ADD, 0, R.string.menu_add);
        menu.add(0, MENU_UNDO, 1, R.string.menu_undo);
        menu.add(0, MENU_RESET, 2, R.string.menu_reset);
        menu.add(0, MENU_CLEAR, 3, R.string.menu_clear);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_ADD) {
            showEditDialog(-1);
        } else if (id == MENU_UNDO) {
            if (ScheduleStore.undoLast(this) == null) toast("Nic do cofnięcia");
            ScheduleUpdater.refresh(this);
            render();
        } else if (id == MENU_RESET) {
            confirm("Odznaczyć wszystkie komory?", () -> modify(list -> {
                for (int i = 0; i < list.size(); i++) list.set(i, list.get(i).withDoneAt(null));
            }));
        } else if (id == MENU_CLEAR) {
            confirm("Usunąć cały harmonogram?", () -> modify(List::clear));
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void confirm(String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("Tak", (d, w) -> action.run())
                .setNegativeButton("Nie", null)
                .show();
    }

    /** Jeśli aplikacja się wcześniej zamknęła z błędem – pokaż jego treść. */
    private void showLastCrash() {
        String crash = CrashLog.take(this);
        if (crash != null) showError("Aplikacja zamknęła się z błędem", crash);
    }

    /** Okno z treścią błędu (łącznie z przyczyną) i przyciskiem do skopiowania. */
    private void showError(String title, String crash) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(crash.length() > 3000 ? crash.substring(0, 3000) + "…" : crash)
                .setPositiveButton("Kopiuj", (d, w) -> {
                    ClipboardManager cm = getSystemService(ClipboardManager.class);
                    cm.setPrimaryClip(ClipData.newPlainText("Błąd", crash));
                    toast("Skopiowano – wklej to w wiadomości do autora");
                })
                .setNegativeButton("Zamknij", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
