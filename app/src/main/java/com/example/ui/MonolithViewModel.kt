package com.example.ui

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.DetectionLog
import com.example.data.MonolithDatabase
import com.example.data.MonolithRepository
import com.example.data.ScanTemplate
import com.example.data.SurveillanceZone
import com.example.server.EmbeddedWebServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Locale

class MonolithViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val database: MonolithDatabase by lazy {
        Room.databaseBuilder(
            application,
            MonolithDatabase::class.java,
            "monolith_secure_db"
        ).fallbackToDestructiveMigration().build()
    }

    private val repository: MonolithRepository by lazy {
        MonolithRepository(database.monolithDao())
    }

    // Reactively observe from Room DB (Mandate)
    val templates: StateFlow<List<ScanTemplate>> = repository.allTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val zones: StateFlow<List<SurveillanceZone>> = repository.allZones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<DetectionLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI State Configurations ---
    private val _serverState = MutableStateFlow("ВЫКЛЮЧЕН")
    val serverState: StateFlow<String> = _serverState.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _isVoiceEnabled = MutableStateFlow(true)
    val isVoiceEnabled: StateFlow<Boolean> = _isVoiceEnabled.asStateFlow()

    private val _selectedTemplateName = MutableStateFlow("")
    val selectedTemplateName: StateFlow<String> = _selectedTemplateName.asStateFlow()

    private val _sensitivity = MutableStateFlow(25)
    val sensitivity: StateFlow<Int> = _sensitivity.asStateFlow()

    private val _minArea = MutableStateFlow(500)
    val minArea: StateFlow<Int> = _minArea.asStateFlow()

    // --- SIMULATED CAMERA ENGINE ---
    // Represents conveyer positioning offsets for the simulated viewer (X coordinate loop 0..100)
    private val _simulatedConveyorOffset = MutableStateFlow(0f)
    val simulatedConveyorOffset: StateFlow<Float> = _simulatedConveyorOffset.asStateFlow()

    private val _simulatedMotionActive = MutableStateFlow(false)
    val simulatedMotionActive: StateFlow<Boolean> = _simulatedMotionActive.asStateFlow()

    private val _matchedTemplateName = MutableStateFlow<String?>(null)
    val matchedTemplateName: StateFlow<String?> = _matchedTemplateName.asStateFlow()

    private val _mockTelemetryFps = MutableStateFlow(14.8f)
    val mockTelemetryFps: StateFlow<Float> = _mockTelemetryFps.asStateFlow()

    // Web Server instance
    private var webServer: EmbeddedWebServer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false
    private var conveyorJob: Job? = null

    init {
        // Initialize Text to Speech for Industrial Voice Alerts (immersive fallback)
        textToSpeech = TextToSpeech(application, this)

        // Initialize Embedded JVM Server
        webServer = EmbeddedWebServer(application, database.monolithDao()) { name ->
            viewModelScope.launch {
                addSystemLog("WEBSERVER", "Новый скан-шаблон зарегистрирован по API: $name")
            }
        }

        // Start conveyor engine simulation loop (provides full-screen visual telemetry inside builders)
        startSimulationEngine()

        // Insert mock data if DB is empty
        viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            repository.allTemplates.collect { list ->
                if (list.isEmpty()) {
                    repository.insertTemplate(ScanTemplate("CHIP_A9_GOLD", 432, 480, 360))
                    repository.insertTemplate(ScanTemplate("RFID_MODULE_V2", 120, 480, 360))
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            repository.allZones.collect { list ->
                if (list.isEmpty()) {
                    // Coordinates: a polygon representing Zone 1 in 1080p scale coordinates
                    val pts = "[[100,200],[980,200],[980,880],[100,880]]"
                    repository.insertZone(SurveillanceZone("Z1", "Коридор A1", pts))
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("ru", "RU"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                textToSpeech?.language = Locale.ENGLISH
            }
            isTtsInitialized = true
            speakVoiceAlert("Система детекции Монолит v1500 запущена. Ожидание сигналов.")
        }
    }

    fun speakVoiceAlert(text: String) {
        if (!_isVoiceEnabled.value || !isTtsInitialized) return
        try {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "MONOLITH_ALERT")
        } catch (e: Exception) {
            Log.e("TTS", "Error vocalizing statement: ${e.message}")
        }
    }

    private fun startSimulationEngine() {
        conveyorJob = viewModelScope.launch(Dispatchers.Default) {
            var counter = 0
            while (true) {
                delay(100)
                // Uptime ticking animations
                counter += 10
                _simulatedConveyorOffset.value = (counter % 1000).toFloat()

                // Simulating match events when target object hits conveyer sweet spot (X on 400..500)
                val pos = counter % 1000
                if (pos in 400..600) {
                    val activeT = templates.value
                    if (activeT.isNotEmpty()) {
                        val selected = _selectedTemplateName.value
                        val matchedObj = if (selected.isNotEmpty()) {
                            selected
                        } else {
                            activeT[pos % activeT.size].name
                        }
                        
                        if (_matchedTemplateName.value != matchedObj) {
                            _matchedTemplateName.value = matchedObj
                            triggerLogAndSpeech("MATCH", "Совпадение сигнатуры", "Объект '$matchedObj' идентифицирован в центре сканирования.")
                        }
                    }
                } else {
                    _matchedTemplateName.value = null
                }

                // Simulate brief random movement alerts inside Zone
                if (pos in 150..300) {
                    if (!_simulatedMotionActive.value) {
                        _simulatedMotionActive.value = true
                        triggerLogAndSpeech("MOTION", "Активность в зоне", "Детектор выявил изменение площади поверхности в секторе Z1.")
                    }
                } else {
                    if (pos > 300) {
                        _simulatedMotionActive.value = false
                    }
                }

                // Jitter FPS telemetry briefly
                _mockTelemetryFps.value = 14.5f + kotlin.random.Random.nextDouble(0.1, 0.8).toFloat()
            }
        }
    }

    private fun triggerLogAndSpeech(type: String, title: String, message: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Write to Room DB directly
            val logItem = DetectionLog(type = type, description = title, details = message)
            repository.insertLog(logItem)
            
            // Speak alert
            if (type == "MATCH") {
                speakVoiceAlert("Захвачен объект: $message")
            } else if (type == "MOTION") {
                speakVoiceAlert("Внимание! Движение в секторе Z1")
            }
        }
    }

    // --- Actions called from UI ---
    fun toggleWebServer() {
        viewModelScope.launch {
            val nextState = !_isServerRunning.value
            _isServerRunning.value = nextState
            if (nextState) {
                webServer?.sensitivity = _sensitivity.value
                webServer?.minArea = _minArea.value
                webServer?.selectedTemplate = _selectedTemplateName.value
                webServer?.start()
                _serverState.value = webServer?.getUptimeState() ?: "АКТИВЕН"
                addSystemLog("SYSTEM", "Локальный веб-пульт запущен на порту 5002.")
                speakVoiceAlert("Фоновый веб сервер активирован.")
            } else {
                webServer?.stop()
                _serverState.value = "ВЫКЛЮЧЕН"
                addSystemLog("SYSTEM", "Веб-сервер остановлен.")
                speakVoiceAlert("Локальный веб сервер отключен.")
            }
        }
    }

    fun toggleVoiceEnabled() {
        _isVoiceEnabled.value = !_isVoiceEnabled.value
        addSystemLog("SYSTEM", if (_isVoiceEnabled.value) "Голосовые алармы ВКЛ." else "Голосовые алармы ВЫКЛ.")
    }

    fun updateSensitivity(value: Int) {
        _sensitivity.value = value
        webServer?.sensitivity = value
    }

    fun updateMinArea(value: Int) {
        _minArea.value = value
        webServer?.minArea = value
    }

    fun selectActiveTemplate(name: String) {
        _selectedTemplateName.value = name
        webServer?.selectedTemplate = name
        addSystemLog("TRACKER", "Выбран шаблон слежения: ${if (name.isEmpty()) "Все" else name}")
    }

    fun saveNewTemplate(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val keypoints = (150..450).random()
            val template = ScanTemplate(name, keypoints, 480, 360)
            repository.insertTemplate(template)
            addSystemLog("TRACKER", "Создан новый скан-шаблон сигнатуры: $name ($keypoints точек)")
            speakVoiceAlert("Успешно добавлен новый скан: $name")
        }
    }

    fun deleteTemplate(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTemplateByName(name)
            if (_selectedTemplateName.value == name) {
                _selectedTemplateName.value = ""
            }
            addSystemLog("TRACKER", "Удален скан-шаблон: $name")
        }
    }

    fun addCustomZone(id: String, coordinatesList: List<Pair<Float, Float>>) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonArr = JSONArray()
            coordinatesList.forEach { pt ->
                jsonArr.put(JSONArray(listOf(pt.first, pt.second)))
            }
            val zone = SurveillanceZone(id, "Сектор $id", jsonArr.toString())
            repository.insertZone(zone)
            addSystemLog("SURVEILLANCE", "Зарегистрирована новая активная зона: $id")
            speakVoiceAlert("Активная зона $id успешно записана.")
        }
    }

    fun clearAllZones() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAllZones()
            addSystemLog("SURVEILLANCE", "Все зоны контроля стерты.")
            speakVoiceAlert("Все зоны безопасности аннулированы.")
        }
    }

    fun clearHistoryLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearLogs()
            addSystemLog("SYSTEM", "Журнал обнаружений очищен.")
        }
    }

    private fun addSystemLog(module: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val logItem = DetectionLog(type = "SYSTEM", description = module, details = text)
            repository.insertLog(logItem)
        }
    }

    override fun onCleared() {
        super.onCleared()
        conveyorJob?.cancel()
        webServer?.stop()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
