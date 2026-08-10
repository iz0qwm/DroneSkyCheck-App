package it.droneskycheck.app.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AuthorizationPdfGenerator(
    private val context: Context
) {
    suspend fun generate(draft: AuthorizationDraft): File {
        PDFBoxResourceLoader.init(context)
        val templateBytes = context.assets.open(templateAssetPath(draft.procedureType)).use { it.readBytes() }
        val outputDir = File(context.cacheDir, "authorizations").also { it.mkdirs() }
        val output = File(outputDir, "${draft.procedureType}_${System.currentTimeMillis()}.pdf")

        writeFilledPdf(templateBytes, draft, output)

        return output
    }

    companion object {
        fun templateAssetPath(procedureType: String): String =
            when (procedureType.uppercase(Locale.ROOT)) {
                "ATM09" -> "pdf/mod-atm09-editabile.pdf"
                else -> "pdf/mod-atm05-editabile.pdf"
            }
    }
}

fun writeFilledPdf(templateBytes: ByteArray, draft: AuthorizationDraft, output: File) {
    PDDocument.load(templateBytes).use { document ->
        val form = document.documentCatalog.acroForm ?: PDAcroForm(document).also {
            document.documentCatalog.acroForm = it
        }
        val pdfData = AuthorizationPdfData.fromDraft(draft)
        fillFields(form, fieldMapFor(draft.procedureType), pdfData, draft.procedureType)
        document.save(output)
    }
}

private data class AuthorizationPdfData(
    val to: String,
    val cc: String,
    val requester: String,
    val phone: String,
    val email: String,
    val activityType: String,
    val aircraftType: String,
    val takeoff: String,
    val landing: String,
    val areaDescription: String,
    val zoneName: String,
    val verticalLower: String,
    val verticalUpper: String,
    val airportName: String,
    val airportDistanceNm: String,
    val airportDistanceKm: String,
    val dateTimeActivity: String,
    val note: String,
    val stampNumber: String,
    val stampDate: String,
    val points: List<AuthorizationGeoPoint>
) {
    companion object {
        fun fromDraft(draft: AuthorizationDraft): AuthorizationPdfData {
            val request = draft.requestData
            val operation = draft.operationData
            val zone = org.json.JSONObject(draft.zoneSnapshotJson)
            val authority = zone.optJSONObject("authority")
            val airportName = listOf(request.airportIcao, request.airportCity)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { request.airportName }

            return AuthorizationPdfData(
                to = authority.normalizedAuthorityEmails().joinToString(", "),
                cc = "protocollo@pec.enac.gov.it; mobilita.innovativa@enac.gov.it; aeroporti.spazioaereo@enac.gov.it",
                requester = request.requester.ifBlank { request.name },
                phone = request.phone,
                email = request.contactEmail,
                activityType = request.activityType,
                aircraftType = request.aircraftType,
                takeoff = request.takeoff,
                landing = request.landing,
                areaDescription = request.areaDescription,
                zoneName = request.zoneName,
                verticalLower = request.verticalLower,
                verticalUpper = request.verticalUpper,
                airportName = airportName,
                airportDistanceNm = request.airportDistanceNm,
                airportDistanceKm = request.airportDistanceKm,
                dateTimeActivity = formatDateTimeRange(
                    request.operationStartDate.ifBlank { request.operationStartDateTime.take(10) },
                    request.operationEndDate.ifBlank { request.operationEndDateTime.take(10) }
                ),
                note = request.notes.ifBlank {
                    "Il volo verra effettuato mantenendo sempre il contatto visivo con il drone (VLOS)."
                },
                stampNumber = request.stampNumber,
                stampDate = formatDateForPdf(request.stampDate),
                points = operation.areaPoints
            )
        }
    }
}

private fun fillFields(
    form: PDAcroForm,
    fieldMap: Map<String, String>,
    data: AuthorizationPdfData,
    procedureType: String
) {
    authorizationPdfFieldValues(data, procedureType).forEach { (key, value) ->
        form.setText(fieldMap, key, value)
    }
}

fun authorizationPdfFieldValues(draft: AuthorizationDraft): Map<String, String> =
    authorizationPdfFieldValues(AuthorizationPdfData.fromDraft(draft), draft.procedureType)

private fun authorizationPdfFieldValues(
    data: AuthorizationPdfData,
    procedureType: String
): Map<String, String> {
    val isAtm05 = procedureType.equals("ATM05", ignoreCase = true)
    val isAtm09 = procedureType.equals("ATM09", ignoreCase = true)
    val areaDescription = when {
        isAtm05 -> "Operazioni UAS in area urbana"
        isAtm09 -> wrapPdfText("Area operativa: ${data.areaDescription}")
        else -> data.areaDescription
    }
    val note = when {
        isAtm05 -> wrapPdfText("Area operativa: ${data.areaDescription}\n\n${data.note}")
        else -> wrapPdfText(data.note)
    }

    val values = linkedMapOf(
        "to" to data.to,
        "cc" to data.cc,
        "requester" to data.requester,
        "phone" to data.phone,
        "email" to data.email,
        "activityType" to data.activityType,
        "aircraftType" to data.aircraftType,
        "takeoff" to data.takeoff,
        "landing" to data.landing,
        "areaDescription" to areaDescription,
        "zoneName" to data.zoneName,
        "verticalLower" to data.verticalLower,
        "verticalUpper" to data.verticalUpper,
        "airport_name" to data.airportName,
        "airport_distance_NM" to data.airportDistanceNm,
        "airport_distance_KM" to data.airportDistanceKm,
        "dateTime_activity" to data.dateTimeActivity,
        "note" to note,
        "stampNumber" to data.stampNumber,
        "stampDate" to data.stampDate
    )

    data.points.take(12).forEachIndexed { index, point ->
        val fieldIndex = if (index % 2 == 0) {
            index / 2 + 1
        } else {
            index / 2 + 7
        }
        values["limits_point${fieldIndex}_coord"] =
            "${point.lat.toDms(isLat = true)} ${point.lon.toDms(isLat = false)}"
    }

    return values
}

private fun PDAcroForm.setText(fieldMap: Map<String, String>, key: String, value: String) {
    val fieldName = fieldMap[key] ?: return
    getField(fieldName)?.setValue(value)
}

private fun fieldMapFor(procedureType: String): Map<String, String> =
    if (procedureType.equals("ATM09", ignoreCase = true)) {
        Atm09FieldMap
    } else {
        Atm05FieldMap
    }

private val Atm05FieldMap = mapOf(
    "to" to "to",
    "cc" to "cc",
    "requester" to "requester",
    "phone" to "phone",
    "email" to "email",
    "activityType" to "activityType",
    "aircraftType" to "aircraftType",
    "takeoff" to "takeoff",
    "landing" to "landing",
    "areaDescription" to "areaDescription",
    "zoneName" to "zoneName",
    "limits_point1_coord" to "limits_point1_coord",
    "limits_point2_coord" to "limits_point2_coord",
    "limits_point3_coord" to "limits_point3_coord",
    "limits_point4_coord" to "limits_point4_coord",
    "limits_point5_coord" to "limits_point5_coord",
    "limits_point6_coord" to "limits_point6_coord",
    "limits_point7_coord" to "limits_point7_coord",
    "limits_point8_coord" to "limits_point8_coord",
    "limits_point9_coord" to "limits_point9_coord",
    "limits_point10_coord" to "limits_point10_coord",
    "limits_point11_coord" to "limits_point11_coord",
    "limits_point12_coord" to "limits_point12_coord",
    "verticalLower" to "verticalLower",
    "verticalUpper" to "verticalUpper",
    "airport_name" to "airport_name",
    "airport_distance_NM" to "airport_distance_NM",
    "airport_distance_KM" to "airport_distance_KM",
    "dateTime_activity" to "dateTime_activity",
    "note" to "note",
    "stampNumber" to "stampNumber",
    "stampDate" to "stampDate"
)

private val Atm09FieldMap = Atm05FieldMap

private fun formatDateTimeRange(start: String, end: String): String {
    val parts = mutableListOf<String>()
    if (start.isNotBlank()) parts += "Dal ${formatDateForPdf(start)}"
    if (end.isNotBlank()) parts += "Al ${formatDateForPdf(end)}"
    return parts.joinToString("\n")
}

private fun formatDateForPdf(value: String): String =
    parseLocalDate(value)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY))
        ?: parseLocalDateTime(value)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY))
        ?: value

private fun parseLocalDate(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()

private fun parseLocalDateTime(value: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(value) }.getOrNull()

private fun wrapPdfText(text: String, maxLen: Int = 80): String {
    val lines = mutableListOf<String>()
    text.split('\n').forEach { paragraph ->
        var current = ""
        paragraph.split(' ').forEach { word ->
            if ((current + word).length > maxLen) {
                lines += current.trim()
                current = ""
            }
            current += "$word "
        }
        if (current.isNotBlank()) lines += current.trim()
    }
    return lines.joinToString("\n")
}

private fun Double.toDms(isLat: Boolean): String {
    val dir = if (isLat) {
        if (this >= 0) "N" else "S"
    } else {
        if (this >= 0) "E" else "W"
    }
    val abs = kotlin.math.abs(this)
    val deg = kotlin.math.floor(abs).toInt()
    val minFloat = (abs - deg) * 60.0
    val min = kotlin.math.floor(minFloat).toInt()
    val sec = (minFloat - min) * 60.0
    return String.format(Locale.US, "%d deg %d'%.2f\" %s", deg, min, sec, dir)
}

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        optString(index).takeIf { it.isNotBlank() }
    }
}

private fun org.json.JSONObject?.normalizedAuthorityEmails(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        optJSONArray("emails").toStringList().forEach { add(it) }
        optString("contact").parseAuthorityJson()?.optJSONArray("emails").toStringList().forEach { add(it) }
        optString("contact").takeIf { it.isNotBlank() && !it.trim().startsWith("{") }?.let { add(it) }
    }
        .flatMap { it.split(',', ';') }
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("{") }
        .distinctBy { it.lowercase() }
}

private fun String.parseAuthorityJson(): org.json.JSONObject? =
    trim()
        .takeIf { it.startsWith("{") && it.endsWith("}") }
        ?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
