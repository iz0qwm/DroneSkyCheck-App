package it.droneskycheck.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class AuthorizationDateWarning(
    val title: String,
    val body: String
)

object AuthorizationDateRules {
    fun leadTimeWarning(
        procedureType: String,
        startDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): AuthorizationDateWarning? {
        val thresholdDays = when (procedureType.uppercase()) {
            "ATM09" -> 30L
            else -> 15L
        }
        val daysUntilStart = ChronoUnit.DAYS.between(today, startDate)
        if (daysUntilStart < 0 || daysUntilStart >= thresholdDays) return null

        return AuthorizationDateWarning(
            title = "Tempi di valutazione",
            body = "La data scelta e molto vicina. L'Amministrazione competente potrebbe richiedere piu tempo per valutare la domanda.\n\n" +
                "Verifica i tempi previsti dall'ente competente prima di inviare la richiesta."
        )
    }

    fun validateDateRange(
        startDate: LocalDate?,
        endDate: LocalDate?,
        today: LocalDate = LocalDate.now()
    ): String? =
        when {
            startDate != null && startDate.isBefore(today) ->
                "La data di inizio non puo essere precedente a oggi."
            startDate != null && endDate != null && endDate.isBefore(startDate) ->
                "La data di fine non puo essere precedente alla data di inizio."
            else -> null
        }
}
