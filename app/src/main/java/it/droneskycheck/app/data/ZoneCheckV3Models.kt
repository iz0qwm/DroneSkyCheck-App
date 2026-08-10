package it.droneskycheck.app.data

data class ZoneCheckV3Response(
    val position: Position,
    val verdict: Verdict,
    val zones: List<ZoneInfo>,
    val blockers: List<Issue>,
    val warnings: List<Issue>,
    val baseline: Baseline,
    val meta: Meta,
    val responsibleZone: ResponsibleZone? = null
)

data class Position(
    val lat: Double,
    val lon: Double
)

data class Verdict(
    val status: String,
    val maxAltitudeMetersAgl: Int,
    val source: String?,
    val explanation: String,
    val baselineMetersAgl: Int? = null,
    val isBaseline: Boolean? = null,
    val responsibleZoneId: String? = null,
    val responsibleZoneName: String? = null
)

data class ZoneInfo(
    val id: String? = null,
    val name: String?,
    val code: String? = null,
    val family: String?,
    val type: String?,
    val classification: String? = null,
    val limitMetersAgl: Int?,
    val verticalLimits: VerticalLimits? = null,
    val description: String?,
    val official: OfficialInfo? = null,
    val info: ZoneNarrative? = null,
    val validity: ValidityInfo? = null,
    val authorization: AuthorizationInfo? = null,
    val authority: AuthorityInfo? = null,
    val operationalStatus: String? = null,
    val notams: List<NotamInfo> = emptyList(),
    val enr: EnrInfo? = null,
    val sup: SupInfo? = null,
    val uasGeographicalZone: UasGeographicalZoneInfo? = null,
    val blockers: List<Issue> = emptyList(),
    val warnings: List<Issue> = emptyList(),
    val enriched: List<KeyValueInfo> = emptyList(),
    val authorizationRequired: Boolean?,
    val activeNow: Boolean?,
    val isVerdictSource: Boolean? = null
)

data class Issue(
    val code: String?,
    val zoneName: String?,
    val message: String? = null,
    val severity: String? = null,
    val explanation: String? = null,
    val operationalMeaning: String? = null
)

data class Baseline(
    val maxAltitudeMetersAgl: Int,
    val representedAsZone: Boolean
)

data class Meta(
    val engine: String,
    val version: String
)

data class ResponsibleZone(
    val id: String?,
    val name: String?,
    val reason: String?
)

data class VerticalLimits(
    val lower: String?,
    val upper: String?,
    val lowerMetersAgl: Int?,
    val upperMetersAgl: Int?
)

data class OfficialInfo(
    val sourceText: String?,
    val sourceReference: String?,
    val qLine: String?,
    val fields: List<KeyValueInfo> = emptyList()
)

data class ZoneNarrative(
    val summary: String?,
    val explanation: String?,
    val operationalMeaning: String?
)

data class ValidityInfo(
    val activeNow: Boolean?,
    val validFrom: String?,
    val validTo: String?,
    val schedule: String?,
    val interpretedSchedule: String?,
    val nextActivation: String? = null,
    val explanation: String?,
    val future: Boolean?,
    val expired: Boolean?
)

data class ScheduleInfo(
    val raw: String?,
    val human: String?,
    val activeNow: Boolean?,
    val explanation: String?
)

data class TemporalBarEntry(
    val active: Boolean?,
    val activeRatio: Float? = null,
    val segments: List<TemporalBarSegment> = emptyList()
)

data class TemporalBarSegment(
    val start: Float,
    val end: Float
)

data class AuthorizationInfo(
    val required: Boolean?,
    val requirement: String?,
    val operationMode: String?,
    val operationCategory: String?,
    val requiredLicense: String?,
    val explanation: String?,
    val applicability: String? = null,
    val resolutionStatus: String? = null,
    val procedures: List<AuthorizationProcedure> = emptyList(),
    val additionalRequirements: List<AuthorizationAdditionalRequirement> = emptyList(),
    val reasonCodes: List<String> = emptyList(),
    val blockingReasons: List<AuthorizationBlockingReason> = emptyList(),
    val resolverVersion: Int? = null
)

data class AuthorizationProcedure(
    val type: String?,
    val version: Int?,
    val label: String?,
    val reasonCode: String?
)

data class AuthorizationAdditionalRequirement(
    val type: String?,
    val label: String?,
    val reasonCode: String?
)

data class AuthorizationBlockingReason(
    val code: String?
)

data class AuthorityInfo(
    val name: String?,
    val code: String?,
    val contact: String?,
    val source: String?,
    val emails: List<String> = emptyList(),
    val note: String? = null
)

data class NotamInfo(
    val code: String?,
    val fir: String?,
    val location: String?,
    val zoneReference: String?,
    val activityType: String?,
    val severity: String?,
    val summary: String?,
    val explanation: String?,
    val operationalMeaning: String?,
    val blockingReason: String?,
    val schedule: ScheduleInfo?,
    val official: OfficialInfo?,
    val validity: ValidityInfo?,
    val blockers: List<Issue> = emptyList(),
    val warnings: List<Issue> = emptyList()
)

data class EnrInfo(
    val code: String?,
    val name: String?,
    val description: String?,
    val limitText: String?,
    val notes: String?,
    val classification: String?,
    val activationType: String?,
    val operationMode: String?,
    val operationCategory: String?,
    val requiredLicense: String?,
    val authorizationRequired: Boolean?,
    val schedule: ScheduleInfo?,
    val authority: AuthorityInfo?,
    val official: OfficialInfo?,
    val validity: ValidityInfo?,
    val explanation: String?,
    val operationalMeaning: String?,
    val weekSchedule: List<TemporalBarEntry> = emptyList(),
    val daySchedule: List<Boolean?> = emptyList()
)

data class SupInfo(
    val title: String?,
    val reference: String?,
    val generality: String?,
    val description: String?,
    val operationMode: String?,
    val operationCategory: String?,
    val requiredLicense: String?,
    val authorizationRequired: Boolean?,
    val authority: AuthorityInfo?,
    val official: OfficialInfo?,
    val validity: ValidityInfo?,
    val authorization: AuthorizationInfo?,
    val explanation: String?,
    val operationalMeaning: String?,
    val blockers: List<Issue> = emptyList(),
    val warnings: List<Issue> = emptyList()
)

data class UasGeographicalZoneInfo(
    val id: String?,
    val generality: String?,
    val description: String?,
    val schedule: String?,
    val operationMode: String?,
    val operationCategory: String?,
    val requiredLicense: String?,
    val authorizationRequired: Boolean?,
    val authority: AuthorityInfo?,
    val confidence: String?
)

data class KeyValueInfo(
    val key: String,
    val value: String
)
