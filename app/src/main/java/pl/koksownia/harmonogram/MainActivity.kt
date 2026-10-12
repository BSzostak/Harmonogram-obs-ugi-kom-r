package pl.koksownia.harmonogram

import android.Manifest
import android.app.DatePickerDialog
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import pl.koksownia.harmonogram.databinding.ActivityMainBinding
import pl.koksownia.harmonogram.databinding.DialogEditBinding
import pl.koksownia.harmonogram.ocr.ParseResult
import pl.koksownia.harmonogram.ocr.ScheduleParser
import pl.koksownia.harmonogram.ocr.TableRecognizer
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ChamberAdapter

    // Zmiany z powiadomienia (przycisk „Wypchnięto”) odświeżają otwartą aplikację.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { render() }
    }

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) runOcr(photoUri())
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runOcr(uri)
    }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) ScheduleUpdater.refresh(this)
            else toast("Bez zgody na powiadomienia nie pokażę następnej komory w pasku powiadomień")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ChamberAdapter(::onRowClicked)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        binding.list.adapter = adapter

        binding.btnCamera.setOnClickListener { openCamera() }
        binding.btnGallery.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnPushNext.setOnClickListener {
            ScheduleStore.markNextDone(this)
            ScheduleUpdater.refresh(this)
        }
        binding.txtDate.setOnClickListener { pickDate() }

        NotificationHelper.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.canNotify(this)) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        ScheduleStore.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        render()
        ScheduleUpdater.refresh(this)
    }

    override fun onStop() {
        ScheduleStore.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onStop()
    }

    private fun render() {
        val s = ScheduleStore.load(this)
        adapter.submit(s)
        binding.txtDate.text = "Data: " + s.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "  ✎"
        binding.txtEmpty.visibility = if (s.chambers.isEmpty()) View.VISIBLE else View.GONE
        binding.header.visibility = if (s.chambers.isEmpty()) View.GONE else View.VISIBLE

        val next = s.nextIndex?.let { s.chambers[it] }
        binding.btnPushNext.isEnabled = next != null
        binding.btnPushNext.text = when {
            next != null -> "Wypchnięto komorę ${next.number}  (${next.planTime})"
            s.chambers.isEmpty() -> "Brak komór"
            else -> "Wszystkie komory wypchnięte"
        }
        s.nextIndex?.let { (binding.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(maxOf(0, it - 2), 0) }
    }

    // ---------- OCR ----------

    // Stała ścieżka, więc adres zdjęcia przetrwa nawet zamknięcie aplikacji przez system w trakcie robienia zdjęcia.
    private fun photoUri(): Uri {
        val dir = File(cacheDir, "images").apply { mkdirs() }
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", File(dir, "harmonogram.jpg"))
    }

    private fun openCamera() = takePicture.launch(photoUri())

    private fun runOcr(uri: Uri) {
        setBusy(true, "Rozpoznawanie tabeli…")
        TableRecognizer.recognize(
            this, uri,
            onResult = { result ->
                setBusy(false)
                onOcrResult(result)
            },
            onError = { e ->
                setBusy(false)
                toast("Błąd rozpoznawania: ${e.message}")
            },
        )
    }

    private fun onOcrResult(result: ParseResult) {
        if (result.rows.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Nie rozpoznano tabeli")
                .setMessage("Zrób zdjęcie bliżej i prosto, tak żeby kolumny „Nr komory” i „Plan. godz. wypch.” były ostre. Komory możesz też dodać ręcznie z menu.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val date = result.date ?: LocalDate.now()
        val preview = result.rows.joinToString("\n") { "${it.chamber}  –  ${it.planTime}" }
        val apply = {
            ScheduleStore.save(this, Schedule(date, result.rows.map { Chamber(it.chamber, it.planTime) }))
            ScheduleUpdater.refresh(this)
            render()
            toast("Wczytano ${result.rows.size} komór. Sprawdź je – stuknij wiersz, aby poprawić.")
        }
        AlertDialog.Builder(this)
            .setTitle("Rozpoznano ${result.rows.size} komór ($date)")
            .setMessage(preview + "\n\nZastąpić bieżący harmonogram?")
            .setPositiveButton("Zastąp") { _, _ -> apply() }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun setBusy(busy: Boolean, message: String? = null) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnCamera.isEnabled = !busy
        binding.btnGallery.isEnabled = !busy
        binding.txtStatus.text = message
        binding.txtStatus.visibility = if (message != null) View.VISIBLE else View.GONE
    }

    // ---------- Edycja ----------

    private fun onRowClicked(index: Int) {
        val s = ScheduleStore.load(this)
        val c = s.chambers.getOrNull(index) ?: return
        val options = arrayOf(
            "Edytuj",
            if (c.done) "Oznacz jako niewypchniętą" else "Oznacz jako wypchniętą",
            "Usuń",
        )
        AlertDialog.Builder(this)
            .setTitle("Komora ${c.number}  •  ${c.planTime}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditDialog(index)
                    1 -> modify { list ->
                        list[index] = c.copy(doneAt = if (c.done) null else System.currentTimeMillis())
                    }
                    2 -> modify { list -> list.removeAt(index) }
                }
            }
            .show()
    }

    /** index == null oznacza dodanie nowej komory na końcu. */
    private fun showEditDialog(index: Int?) {
        val s = ScheduleStore.load(this)
        val existing = index?.let { s.chambers.getOrNull(it) }
        val d = DialogEditBinding.inflate(LayoutInflater.from(this))
        d.editChamber.setText(existing?.number ?: "")
        d.editTime.setText(existing?.planTime ?: "")

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Dodaj komorę" else "Edytuj komorę")
            .setView(d.root)
            .setPositiveButton("Zapisz", null)
            .setNegativeButton("Anuluj", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val number = d.editChamber.text.toString().trim()
                val time = ScheduleParser.parseTime(
                    ScheduleParser.normalize(d.editTime.text.toString().trim()),
                    allowCompact = true,
                )
                when {
                    number.isEmpty() -> d.editChamber.error = "Podaj numer komory"
                    time == null -> d.editTime.error = "Podaj godzinę, np. 6:10"
                    else -> {
                        modify { list ->
                            if (index == null) list.add(Chamber(number, time))
                            else list[index] = list[index].copy(number = number, planTime = time)
                        }
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun modify(block: (MutableList<Chamber>) -> Unit) {
        ScheduleStore.update(this) { s ->
            val list = s.chambers.toMutableList()
            block(list)
            s.copy(chambers = list)
        }
        ScheduleUpdater.refresh(this)
        render()
    }

    private fun pickDate() {
        val d = ScheduleStore.load(this).date
        DatePickerDialog(this, { _, y, m, day ->
            ScheduleStore.update(this) { it.copy(date = LocalDate.of(y, m + 1, day)) }
            ScheduleUpdater.refresh(this)
            render()
        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    // ---------- Menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_ADD, 0, R.string.menu_add)
        menu.add(0, MENU_UNDO, 1, R.string.menu_undo)
        menu.add(0, MENU_RESET, 2, R.string.menu_reset)
        menu.add(0, MENU_CLEAR, 3, R.string.menu_clear)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_ADD -> showEditDialog(null)
            MENU_UNDO -> {
                if (ScheduleStore.undoLast(this) == null) toast("Nic do cofnięcia")
                ScheduleUpdater.refresh(this)
                render()
            }
            MENU_RESET -> confirm("Odznaczyć wszystkie komory?") {
                modify { list -> list.replaceAll { it.copy(doneAt = null) } }
            }
            MENU_CLEAR -> confirm("Usunąć cały harmonogram?") {
                modify { list -> list.clear() }
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun confirm(message: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("Tak") { _, _ -> action() }
            .setNegativeButton("Nie", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        private const val MENU_ADD = 1
        private const val MENU_UNDO = 2
        private const val MENU_RESET = 3
        private const val MENU_CLEAR = 4
    }
}
