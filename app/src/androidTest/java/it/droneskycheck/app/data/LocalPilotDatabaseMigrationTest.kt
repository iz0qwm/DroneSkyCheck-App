package it.droneskycheck.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPilotDatabaseMigrationTest {
    @Test
    fun migration1To4PreservesExistingDataAndCreatesNewSchema() {
        withMigratedDatabase(
            initialVersion = 1,
            prepare = {
                createVersion1Schema()
                insertVersion1PilotProfile()
                insertVersion1Certificate()
                insertVersion1Operator()
                insertVersion1Drone()
            }
        ) { db ->
            assertPilotProfilePreserved(db)
            assertCertificatePreserved(db)
            assertOperatorPreserved(db)
            assertDronePreserved(db, expectedManualWindResistance = null)
            assertEquals(0, db.longFor("SELECT COUNT(*) FROM authorization_drafts"))
            assertCachedZoneAnalysesTableCreated(db)
            assertCachedZoneAnalysisCanBeInserted(db)
        }
    }

    @Test
    fun migration2To4PreservesExistingDataAndCreatesNewSchema() {
        withMigratedDatabase(
            initialVersion = 2,
            prepare = {
                createVersion2Schema()
                insertVersion1PilotProfile()
                insertVersion1Certificate()
                insertVersion1Operator()
                insertVersion1Drone()
                insertVersion2AuthorizationDraft()
            }
        ) { db ->
            assertPilotProfilePreserved(db)
            assertCertificatePreserved(db)
            assertOperatorPreserved(db)
            assertDronePreserved(db, expectedManualWindResistance = null)
            assertAuthorizationDraftPreserved(db)
            assertCachedZoneAnalysesTableCreated(db)
            assertCachedZoneAnalysisCanBeInserted(db)
        }
    }

    @Test
    fun migration3To4PreservesExistingDataAndCreatesCachedZoneAnalyses() {
        withMigratedDatabase(
            initialVersion = 3,
            prepare = {
                createVersion3Schema()
                insertVersion1PilotProfile()
                insertVersion1Certificate()
                insertVersion1Operator()
                insertVersion3Drone()
                insertVersion2AuthorizationDraft()
            }
        ) { db ->
            assertPilotProfilePreserved(db)
            assertCertificatePreserved(db)
            assertOperatorPreserved(db)
            assertDronePreserved(db, expectedManualWindResistance = 10.5)
            assertAuthorizationDraftPreserved(db)
            assertCachedZoneAnalysesTableCreated(db)
            assertCachedZoneAnalysisCanBeInserted(db)
        }
    }

    private fun withMigratedDatabase(
        initialVersion: Int,
        prepare: SQLiteDatabase.() -> Unit,
        assertMigrated: (SupportSQLiteDatabase) -> Unit
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TestDatabaseName)
        val dbFile = context.getDatabasePath(TestDatabaseName)
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).apply {
            prepare()
            version = initialVersion
            close()
        }

        val migrated = Room.databaseBuilder(context, LocalPilotDatabase::class.java, TestDatabaseName)
            .addMigrations(
                LocalPilotDatabase.Migration1To2,
                LocalPilotDatabase.Migration2To3,
                LocalPilotDatabase.Migration3To4
            )
            .allowMainThreadQueries()
            .build()

        try {
            assertMigrated(migrated.openHelper.writableDatabase)
        } finally {
            migrated.close()
            context.deleteDatabase(TestDatabaseName)
        }
    }

    private fun SQLiteDatabase.createVersion1Schema() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS pilot_profile (
                id TEXT NOT NULL,
                firstName TEXT NOT NULL,
                lastName TEXT NOT NULL,
                city TEXT NOT NULL,
                phone TEXT NOT NULL,
                email TEXT NOT NULL,
                profilePhoto TEXT NOT NULL,
                skipPilotCompetencyChecks INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS pilot_certificates (
                id TEXT NOT NULL,
                issuingAuthority TEXT NOT NULL,
                certificateNumber TEXT NOT NULL,
                issueDate TEXT NOT NULL,
                expiryDate TEXT NOT NULL,
                categories TEXT NOT NULL,
                notes TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS uas_operator (
                id TEXT NOT NULL,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                easaOperatorCode TEXT NOT NULL,
                pec TEXT NOT NULL,
                insuranceCompany TEXT NOT NULL,
                insurancePolicyNumber TEXT NOT NULL,
                insuranceExpiresAt TEXT NOT NULL,
                status TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_drones (
                id TEXT NOT NULL,
                manufacturer TEXT NOT NULL,
                model TEXT NOT NULL,
                classLabel TEXT NOT NULL,
                weight REAL,
                serialNumber TEXT NOT NULL,
                remoteControllers TEXT NOT NULL,
                batteries TEXT NOT NULL,
                cameras TEXT NOT NULL,
                remoteId INTEGER NOT NULL,
                euSts01Registered INTEGER NOT NULL,
                euSts01DeclarationDate TEXT NOT NULL,
                euSts02Registered INTEGER NOT NULL,
                euSts02DeclarationDate TEXT NOT NULL,
                notes TEXT NOT NULL,
                status TEXT NOT NULL,
                isSelected INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
    }

    private fun SQLiteDatabase.createVersion2Schema() {
        createVersion1Schema()
        createAuthorizationDraftsTable()
    }

    private fun SQLiteDatabase.createVersion3Schema() {
        createVersion2Schema()
        execSQL("ALTER TABLE local_drones ADD COLUMN manualMaxWindResistanceMs REAL")
    }

    private fun SQLiteDatabase.createAuthorizationDraftsTable() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS authorization_drafts (
                id TEXT NOT NULL,
                procedureType TEXT NOT NULL,
                procedureVersion INTEGER NOT NULL,
                status TEXT NOT NULL,
                zoneSnapshotJson TEXT NOT NULL,
                operationDataJson TEXT NOT NULL,
                pilotSnapshotJson TEXT NOT NULL,
                operatorSnapshotJson TEXT NOT NULL,
                certificateSnapshotJson TEXT NOT NULL,
                droneSnapshotJson TEXT NOT NULL,
                requestDataJson TEXT NOT NULL,
                missingFieldsJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
    }

    private fun SQLiteDatabase.insertVersion1PilotProfile() {
        execSQL(
            """
            INSERT INTO pilot_profile (
                id, firstName, lastName, city, phone, email, profilePhoto,
                skipPilotCompetencyChecks, createdAt, updatedAt
            ) VALUES (
                'local-pilot-profile', 'Raffa', 'Pilot', 'Roma', '+390000000',
                'raffa@example.test', 'files/profile/profile_photo.jpg', 1, 1000, 2000
            )
            """.trimIndent()
        )
    }

    private fun SQLiteDatabase.insertVersion1Certificate() {
        execSQL(
            """
            INSERT INTO pilot_certificates (
                id, issuingAuthority, certificateNumber, issueDate, expiryDate,
                categories, notes, createdAt, updatedAt
            ) VALUES (
                'cert-a2', 'ENAC', 'A2-123', '2026-01-01', '2031-01-01',
                'A2', 'fixture', 1100, 2100
            )
            """.trimIndent()
        )
    }

    private fun SQLiteDatabase.insertVersion1Operator() {
        execSQL(
            """
            INSERT INTO uas_operator (
                id, type, name, easaOperatorCode, pec, insuranceCompany,
                insurancePolicyNumber, insuranceExpiresAt, status, createdAt, updatedAt
            ) VALUES (
                'local-uas-operator', 'individual', 'Operatore test', 'ITA123456789',
                'operatore@example.test', 'Assicurazione test', 'POL123',
                '2027-01-01', 'active', 1200, 2200
            )
            """.trimIndent()
        )
    }

    private fun SQLiteDatabase.insertVersion1Drone() {
        execSQL(
            """
            INSERT INTO local_drones (
                id, manufacturer, model, classLabel, weight, serialNumber,
                remoteControllers, batteries, cameras, remoteId,
                euSts01Registered, euSts01DeclarationDate, euSts02Registered,
                euSts02DeclarationDate, notes, status, isSelected, createdAt, updatedAt
            ) VALUES (
                'drone-mini-4', 'DJI', 'Mini 4 Pro', 'C0', 249.0, 'SN123',
                'RC2', 'B1,B2', '4K', 1, 0, '', 0, '', 'fixture',
                'active', 1, 1300, 2300
            )
            """.trimIndent()
        )
    }

    private fun SQLiteDatabase.insertVersion3Drone() {
        execSQL(
            """
            INSERT INTO local_drones (
                id, manufacturer, model, classLabel, weight, manualMaxWindResistanceMs,
                serialNumber, remoteControllers, batteries, cameras, remoteId,
                euSts01Registered, euSts01DeclarationDate, euSts02Registered,
                euSts02DeclarationDate, notes, status, isSelected, createdAt, updatedAt
            ) VALUES (
                'drone-mini-4', 'DJI', 'Mini 4 Pro', 'C0', 249.0, 10.5,
                'SN123', 'RC2', 'B1,B2', '4K', 1, 0, '', 0, '',
                'fixture', 'active', 1, 1300, 2300
            )
            """.trimIndent()
        )
    }

    private fun SQLiteDatabase.insertVersion2AuthorizationDraft() {
        execSQL(
            """
            INSERT INTO authorization_drafts (
                id, procedureType, procedureVersion, status, zoneSnapshotJson,
                operationDataJson, pilotSnapshotJson, operatorSnapshotJson,
                certificateSnapshotJson, droneSnapshotJson, requestDataJson,
                missingFieldsJson, createdAt, updatedAt
            ) VALUES (
                'draft-1', 'ATM09', 1, 'draft', '{"name":"Zona test"}',
                '{"takeoffLat":41.9}', '{"firstName":"Raffa"}',
                '{"easaOperatorCode":"ITA123456789"}', '[{"id":"cert-a2"}]',
                '{"id":"drone-mini-4"}', '{"selectedDroneId":"drone-mini-4"}',
                '[]', 1400, 2400
            )
            """.trimIndent()
        )
    }

    private fun assertPilotProfilePreserved(db: SupportSQLiteDatabase) {
        assertEquals(1, db.longFor("SELECT COUNT(*) FROM pilot_profile WHERE id = 'local-pilot-profile'"))
        assertEquals("Raffa", db.stringFor("SELECT firstName FROM pilot_profile WHERE id = 'local-pilot-profile'"))
        assertEquals("Pilot", db.stringFor("SELECT lastName FROM pilot_profile WHERE id = 'local-pilot-profile'"))
        assertEquals(1, db.longFor("SELECT skipPilotCompetencyChecks FROM pilot_profile WHERE id = 'local-pilot-profile'"))
        assertEquals(
            "files/profile/profile_photo.jpg",
            db.stringFor("SELECT profilePhoto FROM pilot_profile WHERE id = 'local-pilot-profile'")
        )
    }

    private fun assertCertificatePreserved(db: SupportSQLiteDatabase) {
        assertEquals(1, db.longFor("SELECT COUNT(*) FROM pilot_certificates WHERE id = 'cert-a2'"))
        assertEquals("A2", db.stringFor("SELECT categories FROM pilot_certificates WHERE id = 'cert-a2'"))
        assertEquals("A2-123", db.stringFor("SELECT certificateNumber FROM pilot_certificates WHERE id = 'cert-a2'"))
    }

    private fun assertOperatorPreserved(db: SupportSQLiteDatabase) {
        assertEquals(1, db.longFor("SELECT COUNT(*) FROM uas_operator WHERE id = 'local-uas-operator'"))
        assertEquals(
            "ITA123456789",
            db.stringFor("SELECT easaOperatorCode FROM uas_operator WHERE id = 'local-uas-operator'")
        )
        assertEquals("active", db.stringFor("SELECT status FROM uas_operator WHERE id = 'local-uas-operator'"))
    }

    private fun assertDronePreserved(
        db: SupportSQLiteDatabase,
        expectedManualWindResistance: Double?
    ) {
        assertEquals(1, db.longFor("SELECT COUNT(*) FROM local_drones WHERE id = 'drone-mini-4'"))
        assertEquals("DJI", db.stringFor("SELECT manufacturer FROM local_drones WHERE id = 'drone-mini-4'"))
        assertEquals("Mini 4 Pro", db.stringFor("SELECT model FROM local_drones WHERE id = 'drone-mini-4'"))
        assertEquals(249.0, db.doubleFor("SELECT weight FROM local_drones WHERE id = 'drone-mini-4'"), 0.0)
        assertEquals(1, db.longFor("SELECT isSelected FROM local_drones WHERE id = 'drone-mini-4'"))
        assertColumnExists(db, "local_drones", "manualMaxWindResistanceMs")
        if (expectedManualWindResistance == null) {
            assertTrue(db.isNullFor("SELECT manualMaxWindResistanceMs FROM local_drones WHERE id = 'drone-mini-4'"))
        } else {
            assertEquals(
                expectedManualWindResistance,
                db.doubleFor("SELECT manualMaxWindResistanceMs FROM local_drones WHERE id = 'drone-mini-4'"),
                0.0
            )
        }
    }

    private fun assertAuthorizationDraftPreserved(db: SupportSQLiteDatabase) {
        assertEquals(1, db.longFor("SELECT COUNT(*) FROM authorization_drafts WHERE id = 'draft-1'"))
        assertEquals("ATM09", db.stringFor("SELECT procedureType FROM authorization_drafts WHERE id = 'draft-1'"))
        assertEquals("draft", db.stringFor("SELECT status FROM authorization_drafts WHERE id = 'draft-1'"))
        assertEquals(1, db.longFor("SELECT procedureVersion FROM authorization_drafts WHERE id = 'draft-1'"))
    }

    private fun assertCachedZoneAnalysesTableCreated(db: SupportSQLiteDatabase) {
        assertEquals(
            "cached_zone_analyses",
            db.stringFor("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'cached_zone_analyses'")
        )
        assertEquals(0, db.longFor("SELECT COUNT(*) FROM cached_zone_analyses"))
    }

    private fun assertCachedZoneAnalysisCanBeInserted(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO cached_zone_analyses (
                id, lat, lon, normalizedLat, normalizedLon, analyzedAtUtc,
                responseJson, zoneIds, notamCodes
            ) VALUES (
                '41.90000,12.50000', 41.9, 12.5, '41.90000', '12.50000',
                1787412600000, '{"meta":{"version":"v3"}}', '["zone-1"]', '["W1234/26"]'
            )
            """.trimIndent()
        )

        assertEquals(1, db.longFor("SELECT COUNT(*) FROM cached_zone_analyses"))
        assertEquals(
            """{"meta":{"version":"v3"}}""",
            db.stringFor("SELECT responseJson FROM cached_zone_analyses WHERE id = '41.90000,12.50000'")
        )
    }

    private fun assertColumnExists(db: SupportSQLiteDatabase, table: String, column: String) {
        val exists = db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
                .any { it == column }
        }
        assertTrue("$table.$column should exist", exists)
    }

    private fun SupportSQLiteDatabase.longFor(sql: String): Long =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.doubleFor(sql: String): Double =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getDouble(0)
        }

    private fun SupportSQLiteDatabase.stringFor(sql: String): String {
        val value = query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertNotNull(value)
        return value
    }

    private fun SupportSQLiteDatabase.isNullFor(sql: String): Boolean =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.isNull(0)
        }

    private companion object {
        const val TestDatabaseName = "local-pilot-migration-test"
    }
}
