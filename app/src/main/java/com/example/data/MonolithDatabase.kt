package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- Entity: ScanTemplate ---
@Entity(tableName = "scan_templates")
data class ScanTemplate(
    @PrimaryKey val name: String,
    val keypointsCount: Int,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// --- Entity: SurveillanceZone ---
@Entity(tableName = "surveillance_zones")
data class SurveillanceZone(
    @PrimaryKey val id: String, // e.g. "A1", "A2"
    val name: String,
    val pointsJson: String // JSON array like: [[100,200], [300,120], ...]
)

// --- Entity: DetectionLog ---
@Entity(tableName = "detection_logs")
data class DetectionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "MOTION" or "MATCH"
    val description: String,
    val details: String
)

// --- Interface: MonolithDao ---
@Dao
interface MonolithDao {
    // Scan templates queries
    @Query("SELECT * FROM scan_templates ORDER BY timestamp DESC")
    fun getAllTemplates(): Flow<List<ScanTemplate>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: ScanTemplate)

    @Query("DELETE FROM scan_templates WHERE name = :name")
    suspend fun deleteTemplateByName(name: String)

    // Surveillance Zones queries
    @Query("SELECT * FROM surveillance_zones")
    fun getAllZones(): Flow<List<SurveillanceZone>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZone(zone: SurveillanceZone)

    @Query("DELETE FROM surveillance_zones WHERE id = :id")
    suspend fun deleteZoneById(id: String)

    @Query("DELETE FROM surveillance_zones")
    suspend fun deleteAllZones()

    // Detection Logs queries
    @Query("SELECT * FROM detection_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllLogs(): Flow<List<DetectionLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DetectionLog)

    @Query("DELETE FROM detection_logs")
    suspend fun clearAllLogs()
}

// --- AppDatabase class ---
@Database(
    entities = [ScanTemplate::class, SurveillanceZone::class, DetectionLog::class],
    version = 1,
    exportSchema = false
)
abstract class MonolithDatabase : RoomDatabase() {
    abstract fun monolithDao(): MonolithDao
}
