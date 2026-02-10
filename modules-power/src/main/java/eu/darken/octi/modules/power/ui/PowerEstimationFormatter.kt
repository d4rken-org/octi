package eu.darken.octi.modules.power.ui

import android.content.Context
import android.text.format.DateUtils
import eu.darken.octi.modules.power.R
import eu.darken.octi.modules.power.core.PowerInfo
import eu.darken.octi.modules.power.core.PowerInfo.Status
import java.time.Duration
import java.time.Instant

class PowerEstimationFormatter(private val context: Context) {

    fun format(power: PowerInfo): String = when {
        power.status == Status.FULL && power.chargeIO.fullSince != null -> {
            context.getString(
                R.string.module_power_battery_full_since_x,
                DateUtils.getRelativeTimeSpanString(power.chargeIO.fullSince!!.toEpochMilli())
            )
        }

        power.status == Status.CHARGING
                && power.chargeIO.fullAt != null
                && Duration.between(Instant.now(), power.chargeIO.fullAt).isNegative -> {
            context.getString(
                R.string.module_power_battery_full_since_x,
                DateUtils.getRelativeTimeSpanString(power.chargeIO.fullAt!!.toEpochMilli())
            )
        }

        power.status == Status.CHARGING && power.chargeIO.fullAt != null -> {
            context.getString(
                R.string.module_power_battery_full_in_x,
                DateUtils.getRelativeTimeSpanString(
                    power.chargeIO.fullAt!!.toEpochMilli(),
                    Instant.now().toEpochMilli(),
                    DateUtils.MINUTE_IN_MILLIS,
                )
            )
        }

        power.status == Status.DISCHARGING && power.chargeIO.emptyAt != null -> {
            context.getString(
                R.string.module_power_battery_empty_in_x,
                DateUtils.getRelativeTimeSpanString(
                    power.chargeIO.emptyAt!!.toEpochMilli(),
                    Instant.now().toEpochMilli(),
                    DateUtils.MINUTE_IN_MILLIS,
                )
            )
        }

        else -> context.getString(R.string.module_power_battery_estimation_na)
    }
}
