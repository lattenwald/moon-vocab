package org.lattenwald.moonvocab.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lattenwald.moonvocab.App
import org.lattenwald.moonvocab.data.llm.ConnectionTest
import org.lattenwald.moonvocab.data.llm.ModelCatalog
import org.lattenwald.moonvocab.data.llm.TestResult
import org.lattenwald.moonvocab.data.settings.LlmService
import org.lattenwald.moonvocab.data.settings.Settings
import org.lattenwald.moonvocab.data.settings.SettingsSnapshot
import org.lattenwald.moonvocab.domain.settings.ServiceList
import org.lattenwald.moonvocab.domain.settings.ServiceTemplate
import org.lattenwald.moonvocab.domain.settings.add
import org.lattenwald.moonvocab.domain.settings.delete
import org.lattenwald.moonvocab.domain.settings.duplicate
import org.lattenwald.moonvocab.domain.settings.select

enum class ServiceField { NAME, ADDRESS, KEY, MODEL }

data class ModelList(val serviceId: String, val models: List<String>)

sealed interface ServiceTest {
    data object Idle : ServiceTest
    data class Running(val serviceId: String) : ServiceTest
    data class Done(val serviceId: String, val result: TestResult) : ServiceTest
}

class ServicesViewModel(
    private val settings: Settings,
    private val catalog: ModelCatalog,
    private val connectionTest: ConnectionTest,
    private val io: CoroutineDispatcher,
    private val appScope: CoroutineScope,
) : ViewModel() {
    val snapshot: StateFlow<SettingsSnapshot?> = settings.snapshot
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // A fair lock reached undispatched from the main thread: writes land in keystroke order, and a test sees them all.
    private val writes = Mutex()

    private val _models = MutableStateFlow(ModelList("", emptyList()))
    val models: StateFlow<ModelList> = _models.asStateFlow()
    private var modelsFrom: Pair<String, String>? = null
    private var modelsJob: Job? = null

    fun loadModels(id: String) {
        modelsJob?.cancel()
        modelsJob = viewModelScope.launch {
            val service = writes.withLock { withContext(io) { settings.current() } }.services.firstOrNull { it.id == id } ?: return@launch
            val from = service.baseUrl to service.apiKey
            if (_models.value.serviceId == id && from == modelsFrom && _models.value.models.isNotEmpty()) return@launch
            modelsFrom = from
            _models.value = ModelList(id, emptyList())
            _models.value = ModelList(id, withContext(io) { catalog.models(service.baseUrl, service.apiKey) })
        }
    }

    private val _test = MutableStateFlow<ServiceTest>(ServiceTest.Idle)
    val test: StateFlow<ServiceTest> = _test.asStateFlow()
    private var testJob: Job? = null

    fun select(id: String) = write { s -> s.withList(s.list().select(id)) }

    fun add(template: ServiceTemplate, name: String, onAdded: (String) -> Unit) {
        val newId = UUID.randomUUID().toString()
        write(onDone = { onAdded(newId) }) { s -> s.withList(s.list().add(template, name, newId)) }
    }

    fun duplicate(id: String, name: String, onAdded: (String) -> Unit) {
        val newId = UUID.randomUUID().toString()
        write(onDone = { onAdded(newId) }) { s -> s.withList(s.list().duplicate(id, name, newId)) }
    }

    fun delete(id: String) = write { s -> s.withList(s.list().delete(id)) }

    fun edit(id: String, field: ServiceField, value: String) {
        if (field != ServiceField.NAME) stopTest(id)
        write { s ->
            s.copy(
                services = s.services.map {
                    if (it.id != id) {
                        it
                    } else {
                        when (field) {
                            ServiceField.NAME -> it.copy(name = value)
                            ServiceField.ADDRESS -> it.copy(baseUrl = value)
                            ServiceField.KEY -> it.copy(apiKey = value)
                            ServiceField.MODEL -> it.copy(model = value)
                        }
                    }
                },
            )
        }
    }

    fun test(id: String) {
        testJob?.cancel()
        _test.value = ServiceTest.Running(id)
        testJob = viewModelScope.launch {
            val s = writes.withLock { withContext(io) { settings.current() } }
            val service: LlmService? = s.services.firstOrNull { it.id == id }
            _test.value = if (service == null) ServiceTest.Idle else ServiceTest.Done(id, connectionTest.run(service, s.targetLanguage))
        }
    }

    private fun stopTest(id: String) {
        val current = _test.value
        val shown = (current as? ServiceTest.Running)?.serviceId ?: (current as? ServiceTest.Done)?.serviceId
        if (shown != id) return
        testJob?.cancel()
        _test.value = ServiceTest.Idle
    }

    // In appScope, so a write queued when the page closes still lands.
    private fun write(onDone: (() -> Unit)? = null, transform: (SettingsSnapshot) -> SettingsSnapshot) {
        val written = appScope.async(start = CoroutineStart.UNDISPATCHED) {
            try {
                writes.withLock { withContext(io) { settings.update(transform) } }
                true
            } catch (e: IOException) {
                Log.e(TAG, "settings write failed", e)
                false
            }
        }
        if (onDone != null) viewModelScope.launch { if (written.await()) onDone() }
    }

    private fun SettingsSnapshot.list() = ServiceList(services, activeServiceId)

    private fun SettingsSnapshot.withList(list: ServiceList) = copy(services = list.services, activeServiceId = list.activeId)

    companion object {
        private const val TAG = "ServicesViewModel"
        const val MAX_SUGGESTIONS = 50

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App
                val c = app.container
                ServicesViewModel(c.settings, c.modelCatalog, c.connectionTest, c.io, c.appScope)
            }
        }
    }
}
