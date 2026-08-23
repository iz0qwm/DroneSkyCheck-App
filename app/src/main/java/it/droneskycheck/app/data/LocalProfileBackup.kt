package it.droneskycheck.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import androidx.room.withTransaction
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class LocalProfileBackupPayload(
    val profile: PilotProfileEntity?,
    val certificates: List<PilotCertificateEntity>,
    val operator: UasOperatorEntity?,
    val drones: List<LocalDroneEntity>,
    val authorizationDrafts: List<AuthorizationDraftEntity>
)

sealed class LocalProfileBackupExportResult {
    data class Success(val fileName: String) : LocalProfileBackupExportResult()
    data class Error(val message: String) : LocalProfileBackupExportResult()
}

sealed class LocalProfileBackupImportResult {
    data class Preview(val summary: LocalProfileBackupSummary) : LocalProfileBackupImportResult()
    data class Success(val summary: LocalProfileBackupSummary) : LocalProfileBackupImportResult()
    data class Error(val message: String) : LocalProfileBackupImportResult()
}

data class LocalProfileBackupSummary(
    val createdAt: String,
    val appVersion: String,
    val profilePresent: Boolean,
    val certificates: Int,
    val drones: Int,
    val authorizationDrafts: Int,
    val attachments: Int
) {
    fun toUiText(): String {
        val parts = listOf(
            "Profilo: ${if (profilePresent) "presente" else "assente"}",
            "Attestati: $certificates",
            "Droni: $drones",
            "Richieste: $authorizationDrafts",
            "Allegati: $attachments"
        )
        return parts.joinToString(" - ")
    }
}

class LocalProfileBackupRepository(
    private val context: Context,
    private val database: LocalPilotDatabase = LocalPilotDatabase.getInstance(context)
) {
    private val dao = database.localPilotDao()

    suspend fun exportBackup(
        output: OutputStream,
        appVersionName: String,
        appVersionCode: Long,
        now: Instant = Instant.now()
    ): LocalProfileBackupExportResult =
        runCatching {
            val payload = LocalProfileBackupPayload(
                profile = dao.getProfileEntity(),
                certificates = dao.getCertificateEntities(),
                operator = dao.getOperatorEntity(),
                drones = dao.getDroneEntities(),
                authorizationDrafts = dao.getAuthorizationDraftEntities()
            )
            val packageData = LocalProfileBackupCodec.createPackage(
                payload = payload,
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                now = now,
                attachmentReader = ::readAttachment
            )
            output.use { target ->
                LocalProfileBackupCodec.writeZip(packageData, target)
            }
            LocalProfileBackupExportResult.Success(defaultFileName(now))
        }.getOrElse { error ->
            LocalProfileBackupExportResult.Error(error.backupMessage("Impossibile esportare il backup."))
        }

    fun previewBackup(input: InputStream): LocalProfileBackupImportResult =
        runCatching {
            val packageData = LocalProfileBackupCodec.readZip(input)
            LocalProfileBackupCodec.validatePackage(packageData)
            LocalProfileBackupImportResult.Preview(LocalProfileBackupCodec.summary(packageData))
        }.getOrElse { error ->
            LocalProfileBackupImportResult.Error(error.backupMessage("Backup non valido o non leggibile."))
        }

    suspend fun importBackup(input: InputStream): LocalProfileBackupImportResult =
        runCatching {
            val packageData = LocalProfileBackupCodec.readZip(input)
            val payload = LocalProfileBackupCodec.validatePackage(packageData)
            val restoredPayload = restoreAttachments(payload, packageData.attachments)
            database.runInTransactionSuspend {
                dao.replaceLocalProfileData(
                    profile = restoredPayload.profile,
                    certificates = restoredPayload.certificates,
                    operator = restoredPayload.operator,
                    drones = restoredPayload.drones,
                    authorizationDrafts = restoredPayload.authorizationDrafts
                )
            }
            LocalProfileBackupImportResult.Success(LocalProfileBackupCodec.summary(packageData))
        }.getOrElse { error ->
            LocalProfileBackupImportResult.Error(error.backupMessage("Impossibile importare il backup."))
        }

    private fun readAttachment(path: String): ByteArray? {
        val file = File(path)
        if (!file.isFile) return null
        return file.readBytes()
    }

    private fun restoreAttachments(
        payload: LocalProfileBackupPayload,
        attachments: Map<String, ByteArray>
    ): LocalProfileBackupPayload {
        val restoredProfile = payload.profile?.let { profile ->
            val attachment = profile.profilePhoto.takeIf { it.startsWith(AttachmentPathPrefix) }
            if (attachment == null) {
                profile
            } else {
                val restoredPath = restoreAttachment(
                    attachmentPath = attachment,
                    bytes = attachments.getValue(attachment),
                    directoryName = "profile",
                    fileName = "profile_photo.jpg"
                )
                profile.copy(profilePhoto = restoredPath)
            }
        }

        val restoredDrafts = payload.authorizationDrafts.map { draft ->
            val requestJson = JSONObject(draft.requestDataJson.ifBlank { "{}" })
            val stampAttachment = requestJson.optString("stampImageLocalPath")
                .takeIf { it.startsWith(AttachmentPathPrefix) }
            if (stampAttachment == null) {
                draft
            } else {
                val extension = stampAttachment.substringAfterLast('.', "jpg")
                    .lowercase(Locale.ROOT)
                    .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                    ?: "jpg"
                val restoredPath = restoreAttachment(
                    attachmentPath = stampAttachment,
                    bytes = attachments.getValue(stampAttachment),
                    directoryName = "authorization_attachments",
                    fileName = "${draft.id.cacheSafeName()}_stamp.$extension"
                )
                requestJson.put("stampImageLocalPath", restoredPath)
                draft.copy(requestDataJson = requestJson.toString())
            }
        }

        return payload.copy(
            profile = restoredProfile,
            authorizationDrafts = restoredDrafts
        )
    }

    private fun restoreAttachment(
        attachmentPath: String,
        bytes: ByteArray,
        directoryName: String,
        fileName: String
    ): String {
        require(attachmentPath.startsWith(AttachmentPathPrefix)) { "Allegato non valido." }
        val directory = File(context.filesDir, directoryName).also { it.mkdirs() }
        val target = File(directory, fileName)
        val temp = File(directory, "$fileName.tmp")
        temp.writeBytes(bytes)
        if (target.exists() && !target.delete()) {
            throw IllegalStateException("Impossibile sostituire l'allegato locale.")
        }
        if (!temp.renameTo(target)) {
            throw IllegalStateException("Impossibile salvare l'allegato locale.")
        }
        return target.absolutePath
    }
}

data class LocalProfileBackupPackage(
    val json: JSONObject,
    val attachments: Map<String, ByteArray>
)

object LocalProfileBackupCodec {
    fun createPackage(
        payload: LocalProfileBackupPayload,
        appVersionName: String,
        appVersionCode: Long,
        now: Instant,
        attachmentReader: (String) -> ByteArray?
    ): LocalProfileBackupPackage {
        val attachments = linkedMapOf<String, ByteArray>()
        val profileJson = payload.profile?.toBackupJson()?.also { json ->
            val photoPath = payload.profile.profilePhoto
            if (photoPath.isNotBlank()) {
                attachmentReader(photoPath)?.let { bytes ->
                    val attachmentPath = "attachments/profile/profile_photo.${photoPath.extensionOrDefault("jpg")}"
                    attachments[attachmentPath] = bytes
                    json.put("profilePhoto", attachmentPath)
                } ?: json.put("profilePhoto", "")
            }
        }
        val draftJson = payload.authorizationDrafts.map { draft ->
            val json = draft.toBackupJson()
            val requestJson = JSONObject(draft.requestDataJson.ifBlank { "{}" })
            val stampPath = requestJson.optString("stampImageLocalPath")
            if (stampPath.isNotBlank()) {
                attachmentReader(stampPath)?.let { bytes ->
                    val attachmentPath = "attachments/authorization_drafts/${draft.id.cacheSafeName()}_stamp.${stampPath.extensionOrDefault("jpg")}"
                    attachments[attachmentPath] = bytes
                    requestJson.put("stampImageLocalPath", attachmentPath)
                    json.put("requestDataJson", requestJson.toString())
                } ?: run {
                    requestJson.put("stampImageLocalPath", "")
                    json.put("requestDataJson", requestJson.toString())
                }
            }
            json
        }

        val root = JSONObject()
            .put("appId", BackupAppId)
            .put("backupFormatVersion", BackupFormatVersion)
            .put("createdAt", DateTimeFormatter.ISO_INSTANT.format(now))
            .put("appVersion", appVersionName)
            .put("appVersionCode", appVersionCode)
            .put("data", JSONObject()
                .put("profile", profileJson ?: JSONObject.NULL)
                .put("certificates", JSONArray().also { array ->
                    payload.certificates.forEach { array.put(it.toBackupJson()) }
                })
                .put("operator", payload.operator?.toBackupJson() ?: JSONObject.NULL)
                .put("drones", JSONArray().also { array ->
                    payload.drones.forEach { array.put(it.toBackupJson()) }
                })
                .put("authorizationDrafts", JSONArray().also { array ->
                    draftJson.forEach { array.put(it) }
                })
            )

        return LocalProfileBackupPackage(root, attachments)
    }

    fun writeZip(packageData: LocalProfileBackupPackage, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BackupJsonEntry))
            zip.write(packageData.json.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            packageData.attachments.forEach { (path, bytes) ->
                validateAttachmentPath(path)
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun readZip(input: InputStream): LocalProfileBackupPackage {
        var backupJson: JSONObject? = null
        val attachments = linkedMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val bytes = zip.readBytesLimited(MaxEntryBytes)
                when {
                    name == BackupJsonEntry -> backupJson = JSONObject(bytes.toString(Charsets.UTF_8))
                    name.startsWith(AttachmentPathPrefix) -> {
                        validateAttachmentPath(name)
                        attachments[name] = bytes
                    }
                    else -> throw IllegalArgumentException("Il file contiene elementi non riconosciuti.")
                }
                zip.closeEntry()
            }
        }
        return LocalProfileBackupPackage(
            json = backupJson ?: throw IllegalArgumentException("Backup incompleto: manca backup.json."),
            attachments = attachments
        )
    }

    fun validatePackage(packageData: LocalProfileBackupPackage): LocalProfileBackupPayload {
        val root = packageData.json
        require(root.optString("appId") == BackupAppId) { "Il file non e un backup Drone Sky Check." }
        val version = root.optInt("backupFormatVersion", -1)
        require(version == BackupFormatVersion) { "Versione backup non supportata: $version." }
        val data = root.optJSONObject("data") ?: throw IllegalArgumentException("Backup incompleto: dati mancanti.")
        val payload = LocalProfileBackupPayload(
            profile = data.optJSONObject("profile")?.toPilotProfileEntity(),
            certificates = data.requiredArray("certificates").mapObjects { it.toPilotCertificateEntity() },
            operator = data.optJSONObject("operator")?.toUasOperatorEntity(),
            drones = data.requiredArray("drones").mapObjects { it.toLocalDroneEntity() },
            authorizationDrafts = data.requiredArray("authorizationDrafts").mapObjects { it.toAuthorizationDraftEntity() }
        )
        payload.referencedAttachments().forEach { path ->
            require(packageData.attachments.containsKey(path)) { "Backup corrotto: allegato mancante." }
        }
        return payload
    }

    fun summary(packageData: LocalProfileBackupPackage): LocalProfileBackupSummary {
        val data = packageData.json.optJSONObject("data") ?: JSONObject()
        return LocalProfileBackupSummary(
            createdAt = packageData.json.optString("createdAt"),
            appVersion = packageData.json.optString("appVersion"),
            profilePresent = !data.isNull("profile"),
            certificates = data.optJSONArray("certificates")?.length() ?: 0,
            drones = data.optJSONArray("drones")?.length() ?: 0,
            authorizationDrafts = data.optJSONArray("authorizationDrafts")?.length() ?: 0,
            attachments = packageData.attachments.size
        )
    }

    private fun LocalProfileBackupPayload.referencedAttachments(): List<String> =
        buildList {
            profile?.profilePhoto?.takeIf { it.startsWith(AttachmentPathPrefix) }?.let(::add)
            authorizationDrafts.forEach { draft ->
                val requestJson = JSONObject(draft.requestDataJson.ifBlank { "{}" })
                requestJson.optString("stampImageLocalPath")
                    .takeIf { it.startsWith(AttachmentPathPrefix) }
                    ?.let(::add)
            }
        }
}

fun defaultFileName(now: Instant = Instant.now()): String {
    val date = LocalDate.ofInstant(now, ZoneId.systemDefault())
    return "DroneSkyCheck_Backup_$date.dscbackup"
}

private suspend fun LocalPilotDatabase.runInTransactionSuspend(block: suspend () -> Unit) {
    withTransaction {
        block()
    }
}

private fun PilotProfileEntity.toBackupJson(): JSONObject =
    JSONObject()
        .put("firstName", firstName)
        .put("lastName", lastName)
        .put("city", city)
        .put("phone", phone)
        .put("email", email)
        .put("profilePhoto", profilePhoto)
        .put("skipPilotCompetencyChecks", skipPilotCompetencyChecks)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun JSONObject.toPilotProfileEntity(): PilotProfileEntity =
    PilotProfileEntity(
        firstName = optString("firstName"),
        lastName = optString("lastName"),
        city = optString("city"),
        phone = optString("phone"),
        email = optString("email"),
        profilePhoto = optString("profilePhoto"),
        skipPilotCompetencyChecks = optBoolean("skipPilotCompetencyChecks", false),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )

private fun PilotCertificateEntity.toBackupJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("issuingAuthority", issuingAuthority)
        .put("certificateNumber", certificateNumber)
        .put("issueDate", issueDate)
        .put("expiryDate", expiryDate)
        .put("categories", categories)
        .put("notes", notes)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun JSONObject.toPilotCertificateEntity(): PilotCertificateEntity =
    PilotCertificateEntity(
        id = requiredString("id"),
        issuingAuthority = optString("issuingAuthority"),
        certificateNumber = optString("certificateNumber"),
        issueDate = optString("issueDate"),
        expiryDate = optString("expiryDate"),
        categories = optString("categories"),
        notes = optString("notes"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )

private fun UasOperatorEntity.toBackupJson(): JSONObject =
    JSONObject()
        .put("type", type)
        .put("name", name)
        .put("easaOperatorCode", easaOperatorCode)
        .put("pec", pec)
        .put("insuranceCompany", insuranceCompany)
        .put("insurancePolicyNumber", insurancePolicyNumber)
        .put("insuranceExpiresAt", insuranceExpiresAt)
        .put("status", status)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun JSONObject.toUasOperatorEntity(): UasOperatorEntity =
    UasOperatorEntity(
        type = optString("type", LocalOperatorTypes.Individual),
        name = optString("name"),
        easaOperatorCode = optString("easaOperatorCode"),
        pec = optString("pec"),
        insuranceCompany = optString("insuranceCompany"),
        insurancePolicyNumber = optString("insurancePolicyNumber"),
        insuranceExpiresAt = optString("insuranceExpiresAt"),
        status = optString("status", "active"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )

private fun LocalDroneEntity.toBackupJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("manufacturer", manufacturer)
        .put("model", model)
        .put("classLabel", classLabel)
        .putNullable("weight", weight)
        .putNullable("manualMaxWindResistanceMs", manualMaxWindResistanceMs)
        .put("serialNumber", serialNumber)
        .put("remoteControllers", remoteControllers)
        .put("batteries", batteries)
        .put("cameras", cameras)
        .put("remoteId", remoteId)
        .put("euSts01Registered", euSts01Registered)
        .put("euSts01DeclarationDate", euSts01DeclarationDate)
        .put("euSts02Registered", euSts02Registered)
        .put("euSts02DeclarationDate", euSts02DeclarationDate)
        .put("notes", notes)
        .put("status", status)
        .put("isSelected", isSelected)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun JSONObject.toLocalDroneEntity(): LocalDroneEntity =
    LocalDroneEntity(
        id = requiredString("id"),
        manufacturer = optString("manufacturer"),
        model = optString("model"),
        classLabel = optString("classLabel"),
        weight = optNullableDouble("weight"),
        manualMaxWindResistanceMs = optNullableDouble("manualMaxWindResistanceMs"),
        serialNumber = optString("serialNumber"),
        remoteControllers = optString("remoteControllers"),
        batteries = optString("batteries"),
        cameras = optString("cameras"),
        remoteId = optBoolean("remoteId", false),
        euSts01Registered = optBoolean("euSts01Registered", false),
        euSts01DeclarationDate = optString("euSts01DeclarationDate"),
        euSts02Registered = optBoolean("euSts02Registered", false),
        euSts02DeclarationDate = optString("euSts02DeclarationDate"),
        notes = optString("notes"),
        status = optString("status", "active"),
        isSelected = optBoolean("isSelected", false),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )

private fun AuthorizationDraftEntity.toBackupJson(): JSONObject =
    JSONObject()
        .put("id", id)
        .put("procedureType", procedureType)
        .put("procedureVersion", procedureVersion)
        .put("status", status)
        .put("zoneSnapshotJson", zoneSnapshotJson)
        .put("operationDataJson", operationDataJson)
        .put("pilotSnapshotJson", pilotSnapshotJson)
        .put("operatorSnapshotJson", operatorSnapshotJson)
        .put("certificateSnapshotJson", certificateSnapshotJson)
        .put("droneSnapshotJson", droneSnapshotJson)
        .put("requestDataJson", requestDataJson)
        .put("missingFieldsJson", missingFieldsJson)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

private fun JSONObject.toAuthorizationDraftEntity(): AuthorizationDraftEntity {
    validateJsonString("zoneSnapshotJson")
    validateJsonString("operationDataJson")
    validateJsonString("pilotSnapshotJson")
    validateJsonString("operatorSnapshotJson")
    validateJsonString("certificateSnapshotJson")
    validateJsonString("droneSnapshotJson")
    validateJsonString("requestDataJson")
    validateJsonString("missingFieldsJson")
    return AuthorizationDraftEntity(
        id = requiredString("id"),
        procedureType = requiredString("procedureType"),
        procedureVersion = optInt("procedureVersion", 1),
        status = optString("status", AuthorizationDraftStatuses.Draft),
        zoneSnapshotJson = optString("zoneSnapshotJson"),
        operationDataJson = optString("operationDataJson"),
        pilotSnapshotJson = optString("pilotSnapshotJson"),
        operatorSnapshotJson = optString("operatorSnapshotJson"),
        certificateSnapshotJson = optString("certificateSnapshotJson"),
        droneSnapshotJson = optString("droneSnapshotJson"),
        requestDataJson = optString("requestDataJson"),
        missingFieldsJson = optString("missingFieldsJson"),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        updatedAt = optLong("updatedAt", System.currentTimeMillis())
    )
}

private fun JSONObject.validateJsonString(key: String) {
    val value = optString(key)
    if (value.isBlank()) throw IllegalArgumentException("Backup incompleto: $key mancante.")
    try {
        if (value.trim().startsWith("[")) JSONArray(value) else JSONObject(value)
    } catch (error: JSONException) {
        throw IllegalArgumentException("Backup corrotto: $key non valido.", error)
    }
}

private fun JSONObject.requiredArray(key: String): JSONArray =
    optJSONArray(key) ?: throw IllegalArgumentException("Backup incompleto: $key mancante.")

private fun JSONObject.requiredString(key: String): String =
    optString(key).takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Backup incompleto: $key mancante.")

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { index ->
        val json = optJSONObject(index)
            ?: throw IllegalArgumentException("Backup corrotto: struttura dati non valida.")
        transform(json)
    }

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (isNull(key)) null else optDouble(key)

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    put(key, value ?: JSONObject.NULL)

private fun InputStream.readBytesLimited(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) throw IllegalArgumentException("Backup troppo grande.")
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun validateAttachmentPath(path: String) {
    require(path.startsWith(AttachmentPathPrefix)) { "Percorso allegato non valido." }
    require(!path.contains("..") && !path.startsWith("/") && !path.contains('\\')) {
        "Percorso allegato non valido."
    }
}

private fun String.extensionOrDefault(default: String): String =
    substringAfterLast('.', default)
        .lowercase(Locale.ROOT)
        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
        ?: default

private fun String.cacheSafeName(): String =
    lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]+"), "_").ifBlank { "attachment" }

private fun Throwable.backupMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

private const val BackupAppId = "it.droneskycheck.app"
private const val BackupFormatVersion = 1
private const val BackupJsonEntry = "backup.json"
private const val AttachmentPathPrefix = "attachments/"
private const val MaxEntryBytes = 25 * 1024 * 1024
