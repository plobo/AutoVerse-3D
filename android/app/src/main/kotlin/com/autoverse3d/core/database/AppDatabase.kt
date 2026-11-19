package com.autoverse3d.core.database

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.autoverse3d.domain.ModuleProgress
import com.autoverse3d.domain.model.VehicleConfiguration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "configurations")
data class ConfigurationEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val name: String,
    val paintId: String,
    val wheelId: String,
    val trimId: String,
    val interiorId: String,
    val createdAt: String,
    val updatedAt: String,
    val schemaVersion: Int,
) {
    fun toDomain() = VehicleConfiguration(id, vehicleId, name, paintId, wheelId, trimId, interiorId, createdAt, updatedAt, schemaVersion)

    companion object {
        fun from(c: VehicleConfiguration) = ConfigurationEntity(c.id, c.vehicleId, c.name, c.paintId, c.wheelId, c.trimId, c.interiorId, c.createdAt, c.updatedAt, c.schemaVersion)
    }
}

@Entity(tableName = "module_progress")
data class ModuleProgressEntity(
    @PrimaryKey val moduleId: String,
    /** Comma-separated section ids. */
    val sectionsRead: String,
    val bestQuizPercent: Int?,
    val completed: Boolean,
    val contentVersion: String,
) {
    fun toDomain() = ModuleProgress(moduleId, sectionsRead.split(",").filter { it.isNotBlank() }.toSet(), bestQuizPercent, completed, contentVersion)

    companion object {
        fun from(p: ModuleProgress) = ModuleProgressEntity(p.moduleId, p.sectionsRead.sorted().joinToString(","), p.bestQuizPercent, p.completed, p.contentVersion)
    }
}

/** A downloaded, checksum-verified vehicle asset pack. */
@Entity(tableName = "installed_packs")
data class InstalledPackEntity(
    @PrimaryKey val manifestId: String,
    val vehicleId: String,
    val version: Int,
    val filePath: String,
    val sceneDescriptorPath: String,
    val sizeBytes: Long,
    val checksum: String,
    val installedAt: String,
)

@Dao
interface ConfigurationDao {
    @Query("SELECT * FROM configurations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConfigurationEntity>>

    @Query("SELECT * FROM configurations WHERE id = :id")
    suspend fun get(id: String): ConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: ConfigurationEntity)

    @Query("DELETE FROM configurations WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM module_progress")
    fun observeAll(): Flow<List<ModuleProgressEntity>>

    @Query("SELECT * FROM module_progress WHERE moduleId = :id")
    suspend fun get(id: String): ModuleProgressEntity?

    @Upsert
    suspend fun upsert(entity: ModuleProgressEntity)
}

@Dao
interface PackDao {
    @Query("SELECT * FROM installed_packs")
    fun observeAll(): Flow<List<InstalledPackEntity>>

    @Query("SELECT * FROM installed_packs WHERE vehicleId = :vehicleId ORDER BY version DESC LIMIT 1")
    suspend fun latestFor(vehicleId: String): InstalledPackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InstalledPackEntity)

    @Query("DELETE FROM installed_packs WHERE manifestId = :manifestId")
    suspend fun delete(manifestId: String)
}

@Database(entities = [ConfigurationEntity::class, ModuleProgressEntity::class, InstalledPackEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configurations(): ConfigurationDao
    abstract fun progress(): ProgressDao
    abstract fun packs(): PackDao
}
