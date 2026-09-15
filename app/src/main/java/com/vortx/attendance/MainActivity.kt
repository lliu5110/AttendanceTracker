package com.vortx.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vortx.attendance.data.IdKind
import com.vortx.attendance.data.Person
import com.vortx.attendance.data.ScanOutcome
import com.vortx.attendance.data.UnlinkedScan
import com.vortx.attendance.data.presentOn
import com.vortx.attendance.data.ranked
import com.vortx.attendance.data.toKey
import com.vortx.attendance.ui.AddDayDialog
import com.vortx.attendance.ui.AdminPasswordDialog
import com.vortx.attendance.ui.AttendanceScreen
import com.vortx.attendance.ui.AttendanceTheme
import com.vortx.attendance.ui.ClearAttendanceDialog
import com.vortx.attendance.ui.DeleteConfirmDialog
import com.vortx.attendance.ui.DeleteUnlinkedDialog
import com.vortx.attendance.ui.HomeScreen
import com.vortx.attendance.ui.Ink
import com.vortx.attendance.ui.ManualEntryDialog
import com.vortx.attendance.ui.MatchIdDialog
import com.vortx.attendance.ui.NfcState
import com.vortx.attendance.ui.NfcTapDialog
import com.vortx.attendance.ui.PeopleScreen
import com.vortx.attendance.ui.PersonEditorDialog
import com.vortx.attendance.ui.ScannerDialog
import com.vortx.attendance.ui.WipeEverythingDialog
import com.vortx.attendance.ui.nfcState
import com.vortx.attendance.ui.short
import com.vortx.attendance.util.Haptic
import com.vortx.attendance.util.Haptics
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AttendanceTheme { AttendanceApp() }
        }
    }
}

private enum class Tab(val label: String) {
    Today("Today"),
    Log("Attendance"),
    Roster("People")
}

/** An identifier waiting to be attributed to someone. */
private data class Pending(val value: String, val kind: IdKind)

@Composable
private fun AttendanceApp(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val people by vm.people.collectAsState()
    val unlinked by vm.unlinked.collectAsState()
    val adminMode by vm.adminMode.collectAsState()

    var tab by remember { mutableStateOf(Tab.Today) }

    // Dialog state
    var showNfcDialog by remember { mutableStateOf(false) }
    var nfcFeedback by remember { mutableStateOf<String?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var showAdminPrompt by remember { mutableStateOf(false) }
    var adminError by remember { mutableStateOf(false) }
    var showAddPerson by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Person?>(null) }
    var deleteTarget by remember { mutableStateOf<Person?>(null) }
    var clearTarget by remember { mutableStateOf<Person?>(null) }
    var unlinkedTarget by remember { mutableStateOf<UnlinkedScan?>(null) }
    var addDayTarget by remember { mutableStateOf<Person?>(null) }
    var showWipeConfirm by remember { mutableStateOf(false) }

    val today = LocalDate.now()

    // Derived here, not in the ViewModel, so reading them registers a snapshot read on
    // `people` no matter which tab is showing.
    val ranked = remember(people) { people.ranked() }
    val presentToday = remember(people, today) { people.presentOn(today) }

    // NFC can be toggled in system settings while the app sits in the background,
    // so re-check on every resume rather than caching it once.
    var nfc by remember { mutableStateOf(nfcState(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nfc = nfcState(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun toast(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message = message, withDismissAction = false)
        }
    }

    // ------------------------------------------------------------ CSV pickers

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(vm.exportCsv().toByteArray())
            }
        }.onSuccess {
            toast("Exported ${people.size} ${if (people.size == 1) "person" else "people"}")
        }.onFailure {
            toast("Export failed. Pick a different folder and try again.")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.onSuccess { text ->
            if (text.isNullOrBlank()) {
                toast("That file was empty.")
            } else {
                val result = vm.importCsv(text)
                toast(
                    when {
                        result.added == 0 && result.merged == 0 -> "Nothing to import — no rows recognised."
                        result.merged == 0 -> "Added ${result.added}"
                        result.added == 0 -> "Updated ${result.merged}"
                        else -> "Added ${result.added}, updated ${result.merged}"
                    }
                )
            }
        }.onFailure {
            toast("Couldn't read that file.")
        }
    }

    fun defaultExportName(): String =
        "attendance-${today.format(DateTimeFormatter.ISO_LOCAL_DATE)}.csv"

    // ------------------------------------------------------------ check-in

    /**
     * Shared by all three input routes. [fromNfcDialog] keeps the tap dialog open on a
     * clean result so a queue can check in back to back, reporting inside the dialog
     * because a Snackbar cannot draw above it.
     */
    fun handleValue(value: String, kind: IdKind, fromNfcDialog: Boolean) {
        when (val outcome = vm.submitScan(value, kind)) {
            is ScanOutcome.MarkedPresent -> {
                Haptics.play(context, Haptic.Success)
                val msg = "${outcome.person.name} marked present"
                if (fromNfcDialog) nfcFeedback = msg else toast(msg)
            }
            is ScanOutcome.AlreadyPresent -> {
                Haptics.play(context, Haptic.Duplicate)
                val msg = "${outcome.person.name} already checked in today"
                if (fromNfcDialog) nfcFeedback = msg else toast(msg)
            }
            is ScanOutcome.NeedsName -> {
                Haptics.play(context, Haptic.Unknown)
                // Needs a decision, so the reader steps aside.
                showNfcDialog = false
                nfcFeedback = null
                pending = Pending(outcome.value, outcome.kind)
            }
        }
    }

    // ------------------------------------------------------------ chrome

    Scaffold(
        containerColor = Ink.Base,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(containerColor = Ink.Raised, contentColor = Ink.Primary, snackbarData = data)
            }
        },
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/csv", "*/*")) }) {
                    Icon(
                        Icons.Rounded.FileUpload,
                        contentDescription = "Import a CSV",
                        tint = Ink.Secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { exportLauncher.launch(defaultExportName()) }) {
                    Icon(
                        Icons.Rounded.FileDownload,
                        contentDescription = "Export a CSV",
                        tint = Ink.Secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        bottomBar = { BottomTabs(selected = tab, onSelect = { tab = it }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Today -> HomeScreen(
                    today = today,
                    presentToday = presentToday,
                    rosterSize = people.size,
                    nfcReady = nfc == NfcState.Ready,
                    onTapToCheckIn = {
                        nfc = nfcState(context)
                        nfcFeedback = null
                        showNfcDialog = true
                    },
                    onScanBarcode = { showScanner = true }
                )

                Tab.Log -> AttendanceScreen(
                    ranked = ranked,
                    adminMode = adminMode,
                    onDeleteDate = { person, date ->
                        vm.deleteDate(person.uid, date.toKey())
                        toast("Removed ${date.short()} from ${person.name}")
                    },
                    onAddDate = { addDayTarget = it },
                    onClearAttendance = { clearTarget = it }
                )

                Tab.Roster -> PeopleScreen(
                    people = people,
                    unlinked = unlinked,
                    adminMode = adminMode,
                    onAddPerson = { showAddPerson = true },
                    onEdit = { editTarget = it },
                    onDelete = { deleteTarget = it },
                    onAdminToggle = {
                        if (adminMode) {
                            vm.lockAdmin()
                            toast("Admin locked")
                        } else {
                            adminError = false
                            showAdminPrompt = true
                        }
                    },
                    onResolveUnlinked = { pending = Pending(it.value, it.kind) },
                    onDeleteUnlinked = { unlinkedTarget = it },
                    onWipeEverything = { showWipeConfirm = true }
                )
            }
        }
    }

    // ------------------------------------------------------------ dialogs

    if (showNfcDialog) {
        NfcTapDialog(
            state = nfc,
            feedback = nfcFeedback,
            onTag = { uid -> handleValue(uid, IdKind.Nfc, fromNfcDialog = true) },
            onUseBarcode = {
                showNfcDialog = false
                nfcFeedback = null
                showScanner = true
            },
            onEnterManually = {
                showNfcDialog = false
                nfcFeedback = null
                showManualEntry = true
            },
            onDismiss = {
                showNfcDialog = false
                nfcFeedback = null
            }
        )
    }

    if (showScanner) {
        ScannerDialog(
            onCode = { code ->
                showScanner = false
                handleValue(code, IdKind.Barcode, fromNfcDialog = false)
            },
            onManualEntry = {
                showScanner = false
                showManualEntry = true
            },
            onDismiss = { showScanner = false }
        )
    }

    if (showManualEntry) {
        ManualEntryDialog(
            onSubmit = { code ->
                showManualEntry = false
                handleValue(code, IdKind.Barcode, fromNfcDialog = false)
            },
            onDismiss = { showManualEntry = false }
        )
    }

    pending?.let { p ->
        MatchIdDialog(
            value = p.value,
            kind = p.kind,
            people = people,
            onCreate = { name, otherId ->
                val person = vm.linkToNewPerson(p.value, p.kind, name, otherId)
                pending = null
                toast("${person.name} added and marked present")
            },
            onAttach = { uid ->
                val person = vm.linkToExistingPerson(p.value, p.kind, uid)
                pending = null
                if (person != null) toast("${p.kind.label} linked to ${person.name}")
            },
            onDismiss = { pending = null }
        )
    }

    if (showAdminPrompt) {
        AdminPasswordDialog(
            error = adminError,
            onSubmit = { entered ->
                if (vm.tryUnlockAdmin(entered)) {
                    showAdminPrompt = false
                    adminError = false
                    toast("Admin unlocked")
                } else {
                    adminError = true
                }
            },
            onDismiss = {
                showAdminPrompt = false
                adminError = false
            }
        )
    }

    if (showAddPerson) {
        PersonEditorDialog(
            existing = null,
            onSave = { name, nfcId, barcode ->
                vm.addPerson(name, nfcId, barcode)
                showAddPerson = false
                toast("$name added")
            },
            onRemoveId = { _, _ -> },
            onDismiss = { showAddPerson = false }
        )
    }

    editTarget?.let { target ->
        // Re-read from state so ID edits inside the dialog show immediately.
        val live = people.firstOrNull { it.uid == target.uid } ?: target
        PersonEditorDialog(
            existing = live,
            onSave = { name, nfcId, barcode ->
                vm.rename(live.uid, name)
                nfcId?.let { vm.addId(live.uid, it, IdKind.Nfc) }
                barcode?.let { vm.addId(live.uid, it, IdKind.Barcode) }
                editTarget = null
            },
            onRemoveId = { value, kind -> vm.removeId(live.uid, value, kind) },
            onDismiss = { editTarget = null }
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            person = target,
            onConfirm = {
                vm.deletePerson(target.uid)
                deleteTarget = null
                toast("${target.name} deleted")
            },
            onDismiss = { deleteTarget = null }
        )
    }

    clearTarget?.let { target ->
        ClearAttendanceDialog(
            person = target,
            onConfirm = {
                vm.clearAttendance(target.uid)
                clearTarget = null
                toast("${target.name}'s attendance cleared")
            },
            onDismiss = { clearTarget = null }
        )
    }

    unlinkedTarget?.let { scan ->
        DeleteUnlinkedDialog(
            scan = scan,
            onConfirm = {
                vm.deleteUnlinked(scan.value, scan.kind)
                unlinkedTarget = null
                toast("${scan.value} discarded")
            },
            onDismiss = { unlinkedTarget = null }
        )
    }

    addDayTarget?.let { target ->
        AddDayDialog(
            person = target,
            today = today,
            onConfirm = { date ->
                val added = vm.addDate(target.uid, date.toKey())
                addDayTarget = null
                toast(
                    if (added) "Added ${date.short()} to ${target.name}"
                    else "${target.name} was already marked for ${date.short()}"
                )
            },
            onDismiss = { addDayTarget = null }
        )
    }

    if (showWipeConfirm) {
        WipeEverythingDialog(
            peopleCount = people.size,
            dayCount = people.sumOf { it.visitCount },
            unmatchedCount = unlinked.size,
            onConfirm = {
                vm.wipeEverything()
                showWipeConfirm = false
                tab = Tab.Today
                toast("Everything erased")
            },
            onDismiss = { showWipeConfirm = false }
        )
    }
}

/**
 * Bottom navigation. The active tab is marked with a dot rather than a filled pill,
 * echoing the attendance dots that are the app's main visual device.
 */
@Composable
private fun BottomTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(Ink.Base)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Tab.entries.forEach { item ->
                val active = item == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onSelect(item) }
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(if (active) Ink.Primary else Color.Transparent, CircleShape)
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) Ink.Primary else Ink.Muted
                    )
                }
            }
        }
    }
}
