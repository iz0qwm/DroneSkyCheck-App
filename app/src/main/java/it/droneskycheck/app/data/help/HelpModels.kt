package it.droneskycheck.app.data.help

data class HelpManifest(
    val schemaVersion: Int,
    val contentVersion: Int,
    val updatedAt: String?,
    val onboardingVersion: Int,
    val onboardingSteps: List<HelpOnboardingStep>,
    val topics: List<HelpTopic>
) {
    fun topic(id: String): HelpTopic? =
        topics.firstOrNull { it.id == id }

    companion object {
        fun empty(): HelpManifest =
            HelpManifest(
                schemaVersion = 1,
                contentVersion = 0,
                updatedAt = null,
                onboardingVersion = 0,
                onboardingSteps = emptyList(),
                topics = emptyList()
            )
    }
}

data class HelpOnboardingStep(
    val id: String,
    val target: HelpTourTarget,
    val action: HelpTourAction = HelpTourAction.NONE,
    val title: String,
    val text: String,
    val order: Int
)

enum class HelpTourTarget(val wireName: String) {
    MAP("map"),
    ZONES_BUTTON("zones_button"),
    LOCATION_BUTTON("location_button"),
    TRAFFIC_BUTTON("traffic_button"),
    PROFILE_BUTTON("profile_button"),
    SELECTED_POINT_PANEL("selected_point_panel"),
    WEATHER_ACTION("weather_action"),
    FLIGHT_OPPORTUNITY_CARD("flight_opportunity_card");

    companion object {
        fun fromWireName(value: String?): HelpTourTarget? =
            entries.firstOrNull { it.wireName == value?.trim()?.lowercase() }
    }
}

enum class HelpTourAction(val wireName: String) {
    NONE("none"),
    OPEN_ZONES("open_zones"),
    OPEN_PROFILE("open_profile"),
    OPEN_TRAFFIC("open_traffic"),
    OPEN_SELECTED_POINT_DETAILS("open_selected_point_details"),
    OPEN_WEATHER("open_weather");

    companion object {
        fun fromWireName(value: String?): HelpTourAction? {
            val normalized = value?.trim()?.lowercase().orEmpty()
            if (normalized.isBlank()) return NONE
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}

data class HelpTopic(
    val id: String,
    val title: String,
    val summary: String,
    val introduction: String? = null,
    val blocks: List<HelpContentBlock> = emptyList(),
    val image: String? = null,
    val imageAlt: String? = null,
    val introducedInVersion: Int? = null,
    val order: Int = 0
)

sealed interface HelpContentBlock {
    data class Paragraph(val text: String) : HelpContentBlock
    data class BulletList(val items: List<String>) : HelpContentBlock
    data class Note(val text: String) : HelpContentBlock
    data class Image(val src: String, val alt: String? = null) : HelpContentBlock
}

data class ActiveHelpOnboarding(
    val reason: HelpOnboardingReason,
    val steps: List<HelpOnboardingStep>,
    val session: HelpTourSession,
    val currentIndex: Int = 0
) {
    val currentStep: HelpOnboardingStep?
        get() = steps.getOrNull(currentIndex)

    val isFirstStep: Boolean
        get() = currentIndex <= 0

    val isLastStep: Boolean
        get() = currentIndex >= steps.lastIndex
}

enum class HelpOnboardingReason {
    FIRST_RUN,
    REPLAY_REQUESTED,
    NEW_FEATURES_AVAILABLE
}

data class HelpOnboardingDecision(
    val firstRun: Boolean,
    val replayRequested: Boolean,
    val newFeaturesAvailable: Boolean,
    val shouldShowFullTour: Boolean
)

object HelpOnboardingPolicy {
    fun evaluate(
        lastSeenOnboardingVersion: Int,
        lastSeenContentVersion: Int,
        manifest: HelpManifest,
        replayRequested: Boolean
    ): HelpOnboardingDecision {
        val firstRun = lastSeenOnboardingVersion <= 0
        val newFeaturesAvailable =
            !firstRun &&
                (manifest.onboardingVersion > lastSeenOnboardingVersion ||
                    manifest.contentVersion > lastSeenContentVersion)
        return HelpOnboardingDecision(
            firstRun = firstRun,
            replayRequested = replayRequested,
            newFeaturesAvailable = newFeaturesAvailable,
            shouldShowFullTour = replayRequested || firstRun
        )
    }
}
