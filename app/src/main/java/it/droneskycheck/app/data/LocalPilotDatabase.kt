package it.droneskycheck.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val SingleProfileId = "local-pilot-profile"
private const val SingleOperatorId = "local-uas-operator"

@Entity(tableName = "pilot_profile")
data class PilotProfileEntity(
    @PrimaryKey val id: String = SingleProfileId,
    val firstName: String = "",
    val lastName: String = "",
    val city: String = "",
    val phone: String = "",
    val email: String = "",
    val profilePhoto: String = "",
    val skipPilotCompetencyChecks: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "pilot_certificates")
data class PilotCertificateEntity(
    @PrimaryKey val id: String,
    val issuingAuthority: String = "",
    val certificateNumber: String = "",
    val issueDate: String = "",
    val expiryDate: String = "",
    val categories: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "uas_operator")
data class UasOperatorEntity(
    @PrimaryKey val id: String = SingleOperatorId,
    val type: String = LocalOperatorTypes.Individual,
    val name: String = "",
    val easaOperatorCode: String = "",
    val pec: String = "",
    val insuranceCompany: String = "",
    val insurancePolicyNumber: String = "",
    val insuranceExpiresAt: String = "",
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "local_drones")
data class LocalDroneEntity(
    @PrimaryKey val id: String,
    val manufacturer: String = "",
    val model: String = "",
    val classLabel: String = "",
    val weight: Double? = null,
    val manualMaxWindResistanceMs: Double? = null,
    val serialNumber: String = "",
    val remoteControllers: String = "",
    val batteries: String = "",
    val cameras: String = "",
    val remoteId: Boolean = false,
    val euSts01Registered: Boolean = false,
    val euSts01DeclarationDate: String = "",
    val euSts02Registered: Boolean = false,
    val euSts02DeclarationDate: String = "",
    val notes: String = "",
    val status: String = "active",
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "authorization_drafts")
data class AuthorizationDraftEntity(
    @PrimaryKey val id: String,
    val procedureType: String,
    val procedureVersion: Int,
    val status: String,
    val zoneSnapshotJson: String,
    val operationDataJson: String,
    val pilotSnapshotJson: String,
    val operatorSnapshotJson: String,
    val certificateSnapshotJson: String,
    val droneSnapshotJson: String,
    val requestDataJson: String,
    val missingFieldsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "cached_zone_analyses")
data class CachedZoneAnalysisEntity(
    @PrimaryKey val id: String,
    val lat: Double,
    val lon: Double,
    val normalizedLat: String,
    val normalizedLon: String,
    val analyzedAtUtc: Long,
    val responseJson: String,
    val zoneIds: String = "",
    val notamCodes: String = ""
)

@Dao
interface LocalPilotDao {
    @Query("SELECT * FROM pilot_profile WHERE id = :id LIMIT 1")
    suspend fun getProfileEntity(id: String = SingleProfileId): PilotProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: PilotProfileEntity)

    @Query("DELETE FROM pilot_profile")
    suspend fun clearProfiles()

    @Query("SELECT * FROM pilot_certificates ORDER BY expiryDate ASC, createdAt ASC")
    suspend fun getCertificateEntities(): List<PilotCertificateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCertificate(entity: PilotCertificateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCertificates(entities: List<PilotCertificateEntity>)

    @Query("DELETE FROM pilot_certificates WHERE id = :id")
    suspend fun deleteCertificateById(id: String)

    @Query("DELETE FROM pilot_certificates")
    suspend fun clearCertificates()

    @Query("SELECT * FROM uas_operator WHERE id = :id LIMIT 1")
    suspend fun getOperatorEntity(id: String = SingleOperatorId): UasOperatorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOperator(entity: UasOperatorEntity)

    @Query("DELETE FROM uas_operator")
    suspend fun clearOperators()

    @Query("SELECT * FROM local_drones WHERE status != 'deleted' ORDER BY isSelected DESC, manufacturer ASC, model ASC")
    suspend fun getDroneEntities(): List<LocalDroneEntity>

    @Query("SELECT * FROM local_drones WHERE id = :id LIMIT 1")
    suspend fun getDroneEntity(id: String): LocalDroneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrone(entity: LocalDroneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrones(entities: List<LocalDroneEntity>)

    @Query("DELETE FROM local_drones WHERE id = :id")
    suspend fun deleteDroneById(id: String)

    @Query("DELETE FROM local_drones")
    suspend fun clearDrones()

    @Query("UPDATE local_drones SET isSelected = 0")
    suspend fun clearSelectedDrone()

    @Query("UPDATE local_drones SET isSelected = 1 WHERE id = :id")
    suspend fun setSelectedDrone(id: String)

    @Query("SELECT * FROM authorization_drafts ORDER BY updatedAt DESC")
    suspend fun getAuthorizationDraftEntities(): List<AuthorizationDraftEntity>

    @Query("SELECT * FROM authorization_drafts WHERE id = :id LIMIT 1")
    suspend fun getAuthorizationDraftEntity(id: String): AuthorizationDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuthorizationDraft(entity: AuthorizationDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAuthorizationDrafts(entities: List<AuthorizationDraftEntity>)

    @Query("DELETE FROM authorization_drafts WHERE id = :id")
    suspend fun deleteAuthorizationDraftById(id: String)

    @Query("DELETE FROM authorization_drafts")
    suspend fun clearAuthorizationDrafts()

    @Transaction
    suspend fun replaceLocalProfileData(
        profile: PilotProfileEntity?,
        certificates: List<PilotCertificateEntity>,
        operator: UasOperatorEntity?,
        drones: List<LocalDroneEntity>,
        authorizationDrafts: List<AuthorizationDraftEntity>
    ) {
        clearProfiles()
        clearCertificates()
        clearOperators()
        clearDrones()
        clearAuthorizationDrafts()
        profile?.let { upsertProfile(it) }
        upsertCertificates(certificates)
        operator?.let { upsertOperator(it) }
        upsertDrones(drones)
        upsertAuthorizationDrafts(authorizationDrafts)
    }

    @Query(
        """
        SELECT * FROM cached_zone_analyses
        WHERE normalizedLat = :normalizedLat AND normalizedLon = :normalizedLon
        LIMIT 1
        """
    )
    suspend fun getCachedZoneAnalysis(normalizedLat: String, normalizedLon: String): CachedZoneAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedZoneAnalysis(entity: CachedZoneAnalysisEntity)

    @Query(
        """
        DELETE FROM cached_zone_analyses
        WHERE id NOT IN (
            SELECT id FROM cached_zone_analyses
            ORDER BY analyzedAtUtc DESC
            LIMIT :keep
        )
        """
    )
    suspend fun trimCachedZoneAnalyses(keep: Int)
}

@Database(
    entities = [
        PilotProfileEntity::class,
        PilotCertificateEntity::class,
        UasOperatorEntity::class,
        LocalDroneEntity::class,
        AuthorizationDraftEntity::class,
        CachedZoneAnalysisEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class LocalPilotDatabase : RoomDatabase() {
    abstract fun localPilotDao(): LocalPilotDao

    companion object {
        @Volatile
        private var instance: LocalPilotDatabase? = null

        fun getInstance(context: Context): LocalPilotDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    LocalPilotDatabase::class.java,
                    "dsc-local-pilot.db"
                )
                    .addMigrations(Migration1To2)
                    .addMigrations(Migration2To3)
                    .addMigrations(Migration3To4)
                    .build()
                    .also { instance = it }
            }

        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `authorization_drafts` (
                        `id` TEXT NOT NULL,
                        `procedureType` TEXT NOT NULL,
                        `procedureVersion` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `zoneSnapshotJson` TEXT NOT NULL,
                        `operationDataJson` TEXT NOT NULL,
                        `pilotSnapshotJson` TEXT NOT NULL,
                        `operatorSnapshotJson` TEXT NOT NULL,
                        `certificateSnapshotJson` TEXT NOT NULL,
                        `droneSnapshotJson` TEXT NOT NULL,
                        `requestDataJson` TEXT NOT NULL,
                        `missingFieldsJson` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_drones` ADD COLUMN `manualMaxWindResistanceMs` REAL")
            }
        }

        val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_zone_analyses` (
                        `id` TEXT NOT NULL,
                        `lat` REAL NOT NULL,
                        `lon` REAL NOT NULL,
                        `normalizedLat` TEXT NOT NULL,
                        `normalizedLon` TEXT NOT NULL,
                        `analyzedAtUtc` INTEGER NOT NULL,
                        `responseJson` TEXT NOT NULL,
                        `zoneIds` TEXT NOT NULL,
                        `notamCodes` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
