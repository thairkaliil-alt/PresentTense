package com.allinone.blocker.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** The available friction layers for Strict Mode. */
enum class FrictionType { PIN, COOLDOWN, TYPING_PLEDGE, MATH_PUZZLE, WORD_SCRAMBLE, LOCATION_LOCK, PLAN_LOCK }

/** Order challenges are presented in when more than one is stacked. */
val FRICTION_ORDER = listOf(
    FrictionType.COOLDOWN,
    FrictionType.MATH_PUZZLE,
    FrictionType.WORD_SCRAMBLE,
    FrictionType.PIN,
    FrictionType.TYPING_PLEDGE
    // LOCATION_LOCK and PLAN_LOCK are not challenges — they're automatic
    // enforcers, not in this order list. See StrictModeGate for how each
    // one blocks differently: LOCATION_LOCK blocks based on GPS, PLAN_LOCK
    // blocks based on a self-chosen timer.
)

/** A single named geofence zone. */
data class LocationZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 200f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("latitude", latitude)
        put("longitude", longitude)
        put("radiusMeters", radiusMeters)
    }

    companion object {
        fun fromJson(o: JSONObject) = LocationZone(
            id = o.getString("id"),
            name = o.getString("name"),
            latitude = o.getDouble("latitude"),
            longitude = o.getDouble("longitude"),
            radiusMeters = o.optDouble("radiusMeters", 200.0).toFloat()
        )
    }
}

data class StrictModeConfig(
    val enabled: Boolean = false,
    val activeFrictions: Set<FrictionType> = emptySet(),
    val pinHash: String = "",
    val cooldownSeconds: Int = 30,
    val pledgePhrase: String = "I am choosing to break my focus and give in to distraction.",
    val maxBreaksPerSession: Int = 2,
    val breakDurationMinutes: Int = 5,
    val locationZones: List<LocationZone> = emptyList(),
    // true while the device is inside at least one geofence zone
    val insideZone: Boolean = false,
    // Active Plan (PLAN_LOCK): while planActiveUntil is in the future, Strict
    // Mode's own settings are frozen — no turning off a friction, no PIN
    // change, no deleting a zone, no disabling Strict Mode itself. There is
    // deliberately no "indefinite" option here, unlike manual Lockdown — every
    // plan has a hard end time chosen up front, so this can never become a
    // lock with no way out. See StrictModeGate.isSettingsLockedByPlan().
    val planActiveUntil: Long = 0L,
    val planLabel: String = ""
) {
    /** True while an Active Plan countdown is still running. */
    fun isPlanActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        planActiveUntil > nowMillis

    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("activeFrictions", JSONArray(activeFrictions.map { it.name }))
        put("pinHash", pinHash)
        put("cooldownSeconds", cooldownSeconds)
        put("pledgePhrase", pledgePhrase)
        put("maxBreaksPerSession", maxBreaksPerSession)
        put("breakDurationMinutes", breakDurationMinutes)
        put("locationZones", JSONArray(locationZones.map { it.toJson() }))
        put("insideZone", insideZone)
        put("planActiveUntil", planActiveUntil)
        put("planLabel", planLabel)
    }

    companion object {
        // Custom plan length is capped here so a typo (e.g. extra zero) can't
        // accidentally create a multi-year lock with no way to weaken it.
        const val MAX_CUSTOM_PLAN_DAYS = 14

        fun fromJson(o: JSONObject): StrictModeConfig {
            val frictions = mutableSetOf<FrictionType>()
            o.optJSONArray("activeFrictions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { frictions.add(FrictionType.valueOf(arr.getString(i))) }
                }
            }
            val zones = mutableListOf<LocationZone>()
            o.optJSONArray("locationZones")?.let { arr ->
                for (i in 0 until arr.length()) {
                    runCatching { zones.add(LocationZone.fromJson(arr.getJSONObject(i))) }
                }
            }
            return StrictModeConfig(
                enabled = o.optBoolean("enabled", false),
                activeFrictions = frictions,
                pinHash = o.optString("pinHash", ""),
                cooldownSeconds = o.optInt("cooldownSeconds", 30),
                pledgePhrase = o.optString(
                    "pledgePhrase",
                    "I am choosing to break my focus and give in to distraction."
                ),
                maxBreaksPerSession = o.optInt("maxBreaksPerSession", 2),
                breakDurationMinutes = o.optInt("breakDurationMinutes", 5),
                locationZones = zones,
                insideZone = o.optBoolean("insideZone", false),
                planActiveUntil = o.optLong("planActiveUntil", 0L),
                planLabel = o.optString("planLabel", "")
            )
        }
    }
}

/** One-way hash for the PIN — the digits themselves are never stored or compared directly. */
object PinHasher {
    private const val SALT = "allinone-blocker-pin-salt"

    fun hash(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((SALT + pin).toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, expectedHash: String): Boolean = hash(pin) == expectedHash
}

/**
 * Funnels any action that should be protected by Strict Mode through a
 * challenge first. UI call sites use [guard] instead of running the action
 * directly; the app root watches [pendingAction] and shows the unlock flow
 * whenever it's non-null.
 *
 * Location Lock is enforced here: if the device is currently inside a zone
 * and LOCATION_LOCK is an active friction, the gate becomes completely
 * impassable — no challenge is shown, the action is simply blocked.
 */
object StrictModeGate {

    private val _pendingAction = MutableStateFlow<(() -> Unit)?>(null)
    val pendingAction: StateFlow<(() -> Unit)?> = _pendingAction

    fun guard(action: () -> Unit) {
        val config = BlockerRepository.strictMode.value

        // If location lock is active AND device is inside a zone → hard block, no bypass
        if (FrictionType.LOCATION_LOCK in config.activeFrictions && config.insideZone) {
            return
        }

        if (!config.enabled || config.activeFrictions.isEmpty()) {
            StreakRepository.recordSuccessfulDisable()
            action()
        } else {
            _pendingAction.value = action
        }
    }

    fun confirm() {
        val action = _pendingAction.value
        _pendingAction.value = null
        if (action != null) {
            StreakRepository.recordSuccessfulDisable()
            action()
        }
    }

    fun cancel() {
        _pendingAction.value = null
    }

    /**
     * True while an Active Plan (PLAN_LOCK) countdown is running. This is
     * deliberately separate from [guard]: [guard] protects *using* the
     * blocker (ending lockdown, removing a blocked app, etc.) with a
     * challenge. This instead freezes the Strict Mode *settings screen*
     * itself — no challenge, no bypass, because the whole point of a plan
     * is that you committed to it before you could second-guess yourself.
     *
     * It always has a defined end (see [StrictModeConfig.MAX_CUSTOM_PLAN_DAYS]),
     * so unlike Location Lock this is never a true no-exit state — waiting
     * it out is always an option, by design.
     */
    fun isSettingsLockedByPlan(config: StrictModeConfig = BlockerRepository.strictMode.value): Boolean =
        FrictionType.PLAN_LOCK in config.activeFrictions && config.isPlanActive()
}
