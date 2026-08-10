package it.droneskycheck.app.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AuthorizationPdfGenerator(
    private val context: Context
) {
    suspend fun generate(draft: AuthorizationDraft): File {
        PDFBoxResourceLoader.init(context)
        val templateBytes = downloadTemplate(draft.procedureType)
        val outputDir = File(context.cacheDir, "generated-pdfs").also { it.mkdirs() }
        val output = File(outputDir, "${draft.procedureType}_${System.currentTimeMillis()}.pdf")

        PDDocument.load(templateBytes).use { document ->
            val form = document.documentCatalog.acroForm ?: PDAcroForm(document).also {
                document.documentCatalog.acroForm = it
            }
            val pdfData = AuthorizationPdfData.fromDraft(draft)
            fillFields(form, fieldMapFor(draft.procedureType), pdfData, draft.procedureType)
            document.save(output)
        }

        return output
    }

    private fun downloadTemplate(procedureType: String): ByteArray {
        val fileName = when (procedureType.uppercase(Locale.ROOT)) {
            "ATM09" -> "mod-atm09-editabile.pdf"
            else -> "mod-atm05-editabile.pdf"
        }
        val connection = (URL("$TemplateBaseUrl/$fileName").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TimeoutMillis
            readTimeout = TimeoutMillis
            setRequestProperty("Accept", "application/pdf")
        }

        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Template PDF HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TemplateBaseUrl = "https://solarmonitor.kwos.org/pdf"
        const val TimeoutMillis = 15_000
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
                to = authority?.optString("contact").orEmpty(),
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
                    request.operationStartDateTime,
                    request.operationEndDateTime
                ),
                note = request.notes.ifBlank {
                    "Il volo verra effettuato mantenendo sempre il contatto visivo con il drone (VLOS)."
                },
                stampNumber = request.stampNumber,
                stampDate = request.stampDate.ifBlank { stampDateFrom(request.operationStartDateTime) },
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

    form.setText(fieldMap, "to", data.to)
    form.setText(fieldMap, "cc", data.cc)
    form.setText(fieldMap, "requester", data.requester)
    form.setText(fieldMap, "phone", data.phone)
    form.setText(fieldMap, "email", data.email)
    form.setText(fieldMap, "activityType", data.activityType)
    form.setText(fieldMap, "aircraftType", data.aircraftType)
    form.setText(fieldMap, "takeoff", data.takeoff)
    form.setText(fieldMap, "landing", data.landing)
    form.setText(fieldMap, "areaDescription", areaDescription)
    form.setText(fieldMap, "zoneName", data.zoneName)
    form.setText(fieldMap, "verticalLower", data.verticalLower)
    form.setText(fieldMap, "verticalUpper", data.verticalUpper)
    form.setText(fieldMap, "airport_name", data.airportName)
    form.setText(fieldMap, "airport_distance_NM", data.airportDistanceNm)
    form.setText(fieldMap, "airport_distance_KM", data.airportDistanceKm)
    form.setText(fieldMap, "dateTime_activity", data.dateTimeActivity)
    form.setText(fieldMap, "note", note)
    form.setText(fieldMap, "stampNumber", data.stampNumber)
    form.setText(fieldMap, "stampDate", data.stampDate)

    data.points.take(12).forEachIndexed { index, point ->
        val fieldIndex = if (index % 2 == 0) {
            index / 2 + 1
        } else {
            index / 2 + 7
        }
        form.setText(
            fieldMap,
            "limits_point${fieldIndex}_coord",
            "${point.lat.toDms(isLat = true)} ${point.lon.toDms(isLat = false)}"
        )
    }
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
    if (start.isNotBlank()) parts += "Dal ${formatDateTimeForPdf(start)}"
    if (end.isNotBlank()) parts += "Al ${formatDateTimeForPdf(end)}"
    return parts.joinToString("\n")
}

private fun formatDateTimeForPdf(value: String): String =
    parseLocalDateTime(value)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'ore' HH:mm", Locale.ITALY))
        ?: value

private fun stampDateFrom(value: String): String =
    parseLocalDateTime(value)?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALY)).orEmpty()

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
