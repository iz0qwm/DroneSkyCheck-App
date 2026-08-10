package it.droneskycheck.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AuthorizationDateRulesTest {
    private val today = LocalDate.of(2026, 8, 10)

    @Test
    fun `ATM05 start today plus 10 days warns`() {
        val warning = AuthorizationDateRules.leadTimeWarning("ATM05", today.plusDays(10), today)

        assertNotNull(warning)
    }

    @Test
    fun `ATM05 start today plus 15 days does not warn`() {
        val warning = AuthorizationDateRules.leadTimeWarning("ATM05", today.plusDays(15), today)

        assertNull(warning)
    }

    @Test
    fun `ATM09 start today plus 20 days warns`() {
        val warning = AuthorizationDateRules.leadTimeWarning("ATM09", today.plusDays(20), today)

        assertNotNull(warning)
    }

    @Test
    fun `ATM09 start today plus 30 days does not warn`() {
        val warning = AuthorizationDateRules.leadTimeWarning("ATM09", today.plusDays(30), today)

        assertNull(warning)
    }

    @Test
    fun `end date before start date is invalid`() {
        val error = AuthorizationDateRules.validateDateRange(
            startDate = today.plusDays(2),
            endDate = today.plusDays(1),
            today = today
        )

        assertEquals("La data di fine non puo essere precedente alla data di inizio.", error)
    }
}
