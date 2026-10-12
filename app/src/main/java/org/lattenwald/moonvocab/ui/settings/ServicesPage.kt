package org.lattenwald.moonvocab.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lattenwald.moonvocab.R
import org.lattenwald.moonvocab.data.settings.LlmService
import org.lattenwald.moonvocab.domain.settings.SERVICE_TEMPLATES
import org.lattenwald.moonvocab.domain.settings.ServiceTemplate
import org.lattenwald.moonvocab.ui.inbox.forInboxList
import org.lattenwald.moonvocab.ui.inbox.inboxColors
import org.lattenwald.moonvocab.ui.inbox.segmentedRowShapes

private val RowPadding = PaddingValues(start = 6.dp, top = 10.dp, end = 16.dp, bottom = 10.dp)

@Composable
fun ServicesPage(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    vm: ServicesViewModel = viewModel(factory = ServicesViewModel.Factory),
) {
    val colors = inboxColors()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = colors.screen,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.services_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.card_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.screen),
            )
        },
    ) { padding ->
        val s = snapshot ?: return@Scaffold
        val themeShapes = MaterialTheme.shapes
        MaterialTheme(shapes = remember(themeShapes) { themeShapes.forInboxList() }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                itemsIndexed(s.services, key = { _, service -> service.id }) { index, service ->
                    ServiceRow(
                        index,
                        s.services.size,
                        service,
                        active = service.id == s.activeServiceId,
                        containerColor = colors.row,
                        onSelect = { vm.select(service.id) },
                        onOpen = { onOpen(service.id) },
                    )
                }
                item(key = "add") { AddService(onAdd = { template, name -> vm.add(template, name, onOpen) }) }
                item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ServiceRow(
    index: Int,
    count: Int,
    service: LlmService,
    active: Boolean,
    containerColor: Color,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    SegmentedListItem(
        selected = active,
        onClick = onOpen,
        shapes = segmentedRowShapes(index, count),
        modifier = Modifier.padding(
            start = 12.dp,
            top = if (index > 0) ListItemDefaults.SegmentedGap else 0.dp,
            end = 12.dp,
        ),
        colors = ListItemDefaults.segmentedColors(containerColor = containerColor, selectedContainerColor = containerColor),
        contentPadding = RowPadding,
        leadingContent = { RadioButton(selected = active, onClick = onSelect) },
        supportingContent = {
            Text(
                service.model.ifBlank { stringResource(R.string.services_no_model) },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null) },
    ) {
        Text(serviceTitle(service), fontSize = 16.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun serviceTitle(service: LlmService): String =
    service.name.ifBlank { service.baseUrl }.ifBlank { stringResource(R.string.service_unnamed) }

@Composable
private fun AddService(onAdd: (ServiceTemplate, String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val customName = stringResource(R.string.service_custom)
    Box(Modifier.padding(start = 16.dp, top = 16.dp)) {
        FilledTonalButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.services_add), Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (template in SERVICE_TEMPLATES) {
                val name = template.name.ifEmpty { customName }
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        open = false
                        onAdd(template, name)
                    },
                )
            }
        }
    }
}
