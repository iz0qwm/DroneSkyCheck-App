package it.droneskycheck.app.data.traffic

data class TrafficAlertEvent(
    val primaryTargetId: String,
    val triggeredTargetIds: Set<String>,
    val triggeredCount: Int,
    val createdAtMillis: Long
)

object TrafficAlertDefaults {
    const val AlertCooldownMs = 60_000L
    const val TargetMemoryRetentionMs = 120_000L
}

class TrafficAlertController(
    private val alertCooldownMs: Long = TrafficAlertDefaults.AlertCooldownMs,
    private val targetMemoryRetentionMs: Long = TrafficAlertDefaults.TargetMemoryRetentionMs
) {
    private val targetStates = mutableMapOf<String, TargetAlertState>()

    fun update(
        assessments: Map<String, TrafficAssessment>,
        nowMillis: Long
    ): TrafficAlertEvent? {
        pruneExpired(nowMillis)

        val eligibleTargetIds = assessments
            .filter { (targetId, assessment) ->
                val state = targetStates[targetId]
                assessment.relevance == TrafficRelevance.ATTENTION &&
                    state?.previousRelevance != TrafficRelevance.ATTENTION &&
                    state.cooldownPermits(nowMillis)
            }
            .keys
            .toSet()

        assessments.forEach { (targetId, assessment) ->
            val previous = targetStates[targetId]
            targetStates[targetId] = TargetAlertState(
                previousRelevance = assessment.relevance,
                lastAlertAt = if (targetId in eligibleTargetIds) {
                    nowMillis
                } else {
                    previous?.lastAlertAt
                },
                lastSeenAt = nowMillis
            )
        }

        if (eligibleTargetIds.isEmpty()) return null
        val primaryTargetId = selectPrimaryTrafficAttentionTargetId(
            targetIds = eligibleTargetIds,
            assessments = assessments
        ) ?: eligibleTargetIds.minOrNull() ?: return null

        return TrafficAlertEvent(
            primaryTargetId = primaryTargetId,
            triggeredTargetIds = eligibleTargetIds,
            triggeredCount = eligibleTargetIds.size,
            createdAtMillis = nowMillis
        )
    }

    fun trackedTargetCount(): Int = targetStates.size

    private fun pruneExpired(nowMillis: Long) {
        val iterator = targetStates.entries.iterator()
        while (iterator.hasNext()) {
            val (_, state) = iterator.next()
            if (nowMillis - state.lastSeenAt > targetMemoryRetentionMs) {
                iterator.remove()
            }
        }
    }

    private fun TargetAlertState?.cooldownPermits(nowMillis: Long): Boolean {
        val lastAlertAt = this?.lastAlertAt ?: return true
        return nowMillis - lastAlertAt >= alertCooldownMs
    }

    private data class TargetAlertState(
        val previousRelevance: TrafficRelevance,
        val lastAlertAt: Long?,
        val lastSeenAt: Long
    )
}

fun selectPrimaryTrafficAttentionTargetId(
    targetIds: Collection<String>,
    assessments: Map<String, TrafficAssessment>,
    currentDistanceByTargetId: Map<String, Double?> = emptyMap()
): String? =
    targetIds
        .filter { targetId ->
            assessments[targetId]?.relevance == TrafficRelevance.ATTENTION
        }
        .minWithOrNull(
            compareBy<String>(
                { targetId -> assessments[targetId]?.timeToCpaSec?.takeIf { it > 0.0 } ?: Double.POSITIVE_INFINITY },
                { targetId -> assessments[targetId]?.cpaDistanceM ?: Double.POSITIVE_INFINITY },
                { targetId ->
                    assessments[targetId]?.currentDistanceM
                        ?: currentDistanceByTargetId[targetId]
                        ?: Double.POSITIVE_INFINITY
                },
                { targetId -> targetId }
            )
        )
