package com.example.data

import kotlinx.coroutines.flow.Flow

class MonolithRepository(private val dao: MonolithDao) {
    val allTemplates: Flow<List<ScanTemplate>> = dao.getAllTemplates()
    val allZones: Flow<List<SurveillanceZone>> = dao.getAllZones()
    val allLogs: Flow<List<DetectionLog>> = dao.getAllLogs()

    suspend fun insertTemplate(template: ScanTemplate) {
        dao.insertTemplate(template)
    }

    suspend fun deleteTemplateByName(name: String) {
        dao.deleteTemplateByName(name)
    }

    suspend fun insertZone(zone: SurveillanceZone) {
        dao.insertZone(zone)
    }

    suspend fun deleteZoneById(id: String) {
        dao.deleteZoneById(id)
    }

    suspend fun clearAllZones() {
        dao.deleteAllZones()
    }

    suspend fun insertLog(log: DetectionLog) {
        dao.insertLog(log)
    }

    suspend fun clearLogs() {
        dao.clearAllLogs()
    }
}
