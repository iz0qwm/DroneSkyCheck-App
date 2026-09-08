package it.droneskycheck.app.integration

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import it.droneskycheck.app.data.DscLogger
import it.droneskycheck.app.data.airawareness.SharedPreferencesAirAwarenessSessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AirSenseIntegrationState(
    val installed: Boolean = false,
    val receiverActive: Boolean = false
)

interface AirAwarenessStatusPublisher {
    fun publishAirAwarenessStatus(active: Boolean)
}

object NoOpAirAwarenessStatusPublisher : AirAwarenessStatusPublisher {
    override fun publishAirAwarenessStatus(active: Boolean) = Unit
}

class DscEcosystemIntegrationClient(context: Context) : AirAwarenessStatusPublisher {
    private val appContext = context.applicationContext

    val airSenseState: StateFlow<AirSenseIntegrationState> = AirSenseStatusHolder.state

    fun refreshAirSenseStatus() {
        val installed = appContext.isPackageInstalled(EcosystemContract.AirSensePackage)
        AirSenseStatusHolder.update(installed = installed, receiverActive = false)
        if (!installed) return

        appContext.sendEcosystemBroadcast(
            component = EcosystemContract.AirSenseReceiver,
            action = EcosystemContract.QueryAirSenseStatus
        )
    }

    fun openAirSense(): Boolean {
        if (!appContext.isPackageInstalled(EcosystemContract.AirSensePackage)) return false
        return runCatching {
            appContext.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(EcosystemContract.AirSenseActivity)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
    }

    override fun publishAirAwarenessStatus(active: Boolean) {
        appContext.sendEcosystemBroadcast(
            component = EcosystemContract.AirSenseReceiver,
            action = EcosystemContract.DscStatus,
            status = if (active) {
                EcosystemContract.AirAwarenessActive
            } else {
                EcosystemContract.AirAwarenessInactive
            }
        )
    }
}

class DscEcosystemStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            EcosystemContract.AirSenseStatus -> {
                val installed = context.isPackageInstalled(EcosystemContract.AirSensePackage)
                val active = when (intent.getStringExtra(EcosystemContract.ExtraStatus)) {
                    EcosystemContract.ReceiverActive -> true
                    EcosystemContract.ReceiverInactive -> false
                    else -> return
                } && installed
                AirSenseStatusHolder.update(installed = installed, receiverActive = active)
            }

            EcosystemContract.QueryDscStatus -> {
                val session = SharedPreferencesAirAwarenessSessionStore(context).load()
                val active = session != null &&
                    !session.closePending &&
                    session.doa.endTimeMillis > System.currentTimeMillis()
                context.sendEcosystemBroadcast(
                    component = EcosystemContract.AirSenseReceiver,
                    action = EcosystemContract.DscStatus,
                    status = if (active) {
                        EcosystemContract.AirAwarenessActive
                    } else {
                        EcosystemContract.AirAwarenessInactive
                    }
                )
            }
        }
    }
}

private object AirSenseStatusHolder {
    private val mutableState = MutableStateFlow(AirSenseIntegrationState())
    val state: StateFlow<AirSenseIntegrationState> = mutableState.asStateFlow()

    fun update(installed: Boolean, receiverActive: Boolean) {
        val updated = AirSenseIntegrationState(installed, receiverActive)
        if (mutableState.value == updated) return
        mutableState.value = updated
        DscLogger.debug(
            EcosystemContract.LogTag,
            "AirSense installed=$installed receiverActive=$receiverActive"
        )
    }
}

private object EcosystemContract {
    const val LogTag = "DSC_ECOSYSTEM"
    const val AirSensePackage = "org.kwos.airsense"
    const val ExtraStatus = "it.droneskycheck.ecosystem.extra.STATUS"

    const val QueryAirSenseStatus = "it.droneskycheck.ecosystem.action.QUERY_AIRSENSE_STATUS"
    const val AirSenseStatus = "it.droneskycheck.ecosystem.action.AIRSENSE_STATUS"
    const val QueryDscStatus = "it.droneskycheck.ecosystem.action.QUERY_DSC_STATUS"
    const val DscStatus = "it.droneskycheck.ecosystem.action.DSC_STATUS"

    const val ReceiverActive = "RECEIVER_ACTIVE"
    const val ReceiverInactive = "RECEIVER_INACTIVE"
    const val AirAwarenessActive = "AIR_AWARENESS_ACTIVE"
    const val AirAwarenessInactive = "AIR_AWARENESS_INACTIVE"

    val AirSenseReceiver = ComponentName(
        AirSensePackage,
        "org.kwos.airsense.EcosystemStatusReceiver"
    )
    val AirSenseActivity = ComponentName(
        AirSensePackage,
        "org.kwos.airsense.MainActivity"
    )
}

private fun Context.sendEcosystemBroadcast(
    component: ComponentName,
    action: String,
    status: String? = null
) {
    runCatching {
        sendBroadcast(
            Intent(action)
                .setComponent(component)
                .apply { status?.let { putExtra(EcosystemContract.ExtraStatus, it) } }
        )
    }.onFailure { error ->
        DscLogger.debug(
            EcosystemContract.LogTag,
            "broadcast unavailable action=$action reason=${error.javaClass.simpleName}"
        )
    }
}

private fun Context.isPackageInstalled(packageName: String): Boolean =
    try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getApplicationInfo(
                packageName,
                PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        info.enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
