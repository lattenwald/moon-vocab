package org.lattenwald.moonvocab.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.data.llm.TestResult
import org.lattenwald.moonvocab.data.settings.LlmService
import org.lattenwald.moonvocab.ui.inbox.inboxColors

@Composable
fun ServicePage(
    id: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    vm: ServicesViewModel = viewModel(factory = ServicesViewModel.Factory),
) {
    val colors = inboxColors()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val services = snapshot?.services
    val service = services?.firstOrNull { it.id == id }
    val gone = services != null && service == null
    LaunchedEffect(gone) { if (gone) onBack() }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = colors.screen,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                    }
                },
                actions = {
                    if (service != null) {
                        val copyName = stringResource(R.string.service_copy_name, service.name)
                        MoreActions(
                            canDelete = services.size > 1,
                            onDuplicate = { vm.duplicate(id, copyName, onOpen) },
                            onDelete = { confirmDelete = true },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
            )
        },
    ) { padding ->
        if (service == null) return@Scaffold
        // Padding outside the scroll, so a focused field is brought into view above the keyboard.
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        ) {
            ServiceForm(service, vm)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.service_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(id)
                }) { Text(stringResource(R.string.service_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.service_cancel)) } },
        )
    }
}

@Composable
private fun MoreActions(canDelete: Boolean, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.service_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.service_duplicate)) },
                onClick = {
                    open = false
                    onDuplicate()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.service_delete)) },
                onClick = {
                    open = false
                    onDelete()
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                enabled = canDelete,
            )
        }
    }
}

@Composable
fun ServiceForm(
    service: LlmService,
    vm: ServicesViewModel,
    modifier: Modifier = Modifier,
    fields: Set<ServiceField> = ServiceField.entries.toSet(),
) {
    val id = service.id
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ServiceField.NAME in fields) {
            TextRow(id, service.name, R.string.service_name, KeyboardType.Text, KeyboardCapitalization.Sentences) {
                vm.edit(id, ServiceField.NAME, it)
            }
        }
        if (ServiceField.ADDRESS in fields) {
            TextRow(id, service.baseUrl, R.string.service_address, KeyboardType.Uri) { vm.edit(id, ServiceField.ADDRESS, it) }
        }
        if (ServiceField.KEY in fields) KeyField(id, service.apiKey) { vm.edit(id, ServiceField.KEY, it) }
        if (ServiceField.MODEL in fields) {
            LaunchedEffect(id) { vm.watch(id) }
            val models by vm.models.collectAsStateWithLifecycle()
            ModelField(id, service.model, models) { vm.edit(id, ServiceField.MODEL, it) }
        }
        val test by vm.test.collectAsStateWithLifecycle()
        val running = test is ServiceTest.Running
        FilledTonalButton(onClick = { vm.test(id) }, enabled = !running) {
            Icon(painterResource(R.drawable.ic_network_check), contentDescription = null)
            Text(stringResource(R.string.service_test), Modifier.padding(start = 8.dp))
        }
        TestLine(test, id)
    }
}

// Local state keyed by the service id: a write never moves the cursor.
@Composable
private fun TextRow(
    id: String,
    initial: String,
    label: Int,
    keyboard: KeyboardType,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onChange: (String) -> Unit,
) {
    var value by remember(id) { mutableStateOf(initial) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = false,
            keyboardType = keyboard,
            imeAction = ImeAction.Next,
        ),
    )
}

@Composable
private fun KeyField(id: String, initial: String, onChange: (String) -> Unit) {
    var value by remember(id) { mutableStateOf(initial) }
    var shown by remember(id) { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.service_key)) },
        supportingText = { Text(stringResource(R.string.service_key_help)) },
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(
                    painterResource(if (shown) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (shown) R.string.service_hide_key else R.string.service_show_key),
                )
            }
        },
        singleLine = true,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
    )
}

@Composable
private fun ModelField(id: String, initial: String, models: List<String>, onChange: (String) -> Unit) {
    var value by remember(id) { mutableStateOf(initial) }
    var expanded by remember(id) { mutableStateOf(false) }
    val query = value.trim()
    val matches = remember(models, query) {
        models.filter { it != query && it.contains(query, ignoreCase = true) }.take(ServicesViewModel.MAX_SUGGESTIONS)
    }
    val set: (String) -> Unit = {
        value = it
        onChange(it)
    }
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                set(it)
                expanded = true
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
            label = { Text(stringResource(R.string.service_model)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            for (model in matches) {
                DropdownMenuItem(
                    text = { Text(model, maxLines = 1) },
                    onClick = {
                        set(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TestLine(test: ServiceTest, id: String) {
    when (test) {
        is ServiceTest.Running -> if (test.serviceId == id) {
            ResultBox {
                LinearWavyProgressIndicator(Modifier.width(56.dp))
                Text(stringResource(R.string.service_testing), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        is ServiceTest.Done -> if (test.serviceId == id) {
            val ok = test.result is TestResult.Ok
            ResultBox {
                Icon(
                    painterResource(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error),
                    contentDescription = null,
                    tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(resultText(test.result), Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        ServiceTest.Idle -> Unit
    }
}

@Composable
private fun ResultBox(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(inboxColors().row)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun resultText(result: TestResult): String = when (result) {
    is TestResult.Ok -> {
        val seconds = String.format(LocalConfiguration.current.locales[0], "%.1f", result.millis / 1000.0)
        stringResource(R.string.service_test_ok, result.word, result.translation, seconds)
    }
    is TestResult.Failed -> when (result.kind) {
        LlmException.Stop.Kind.AUTH -> stringResource(R.string.service_test_auth)
        LlmException.Stop.Kind.CREDITS -> stringResource(R.string.service_test_credits)
        LlmException.Stop.Kind.CONFIG -> stringResource(R.string.service_test_config)
        null -> stringResource(R.string.service_test_failed, result.message)
    }
    TestResult.NoTranslation -> stringResource(R.string.service_test_failed, stringResource(R.string.service_test_no_translation))
}
