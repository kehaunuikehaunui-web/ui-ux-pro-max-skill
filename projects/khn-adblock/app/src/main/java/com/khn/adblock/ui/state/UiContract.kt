package com.khn.adblock.ui.state

/*
 * UI <-> logic contract for KHN AdBlock.
 *
 * Owned by the coordinator. The Android Developer produces these states from a
 * ViewModel and implements [AdBlockActions]; the UI/UX Designer renders them with
 * stateless composables. Neither side edits this file alone: if a field or an
 * action is missing, report it and the coordinator updates the contract.
 */

enum class ProtectionStatus { OFF, STARTING, ON, STOPPING, ERROR }

enum class UpstreamDns { CLOUDFLARE, QUAD9, GOOGLE, ADGUARD }

enum class ListUpdateState { IDLE, UPDATING, SUCCESS, FAILED }

data class BlockedEvent(
    val domain: String,
    val timestampMillis: Long,
)

data class DomainCount(
    val domain: String,
    val count: Int,
)

/** One day of the 7-day history; [dayStartMillis] is local midnight of that day. */
data class DailyCount(
    val dayStartMillis: Long,
    val blocked: Int,
    val queries: Int,
)

data class FilterListUi(
    val id: String,
    val name: String,
    val description: String,
    val ruleCount: Int,
    val enabled: Boolean,
    val isBuiltIn: Boolean,
)

data class DashboardUiState(
    val status: ProtectionStatus = ProtectionStatus.OFF,
    /** Human-readable error when [status] is ERROR, already localized. */
    val errorMessage: String? = null,
    val blockedToday: Long = 0,
    val queriesToday: Long = 0,
    val blockedAllTime: Long = 0,
    /** Number of domains currently loaded in the blocker. */
    val activeRules: Int = 0,
    /** Most recent first, at most 50 entries. */
    val recentBlocked: List<BlockedEvent> = emptyList(),
    /**
     * True when Android "Private DNS" is in strict (hostname) mode, which sends DNS
     * outside the local VPN and defeats the blocker. The UI must explain it and offer
     * [AdBlockActions.openPrivateDnsSettings].
     */
    val privateDnsWarning: Boolean = false,
    /** Show the explanation shown before the system VPN permission dialog. */
    val showVpnRationale: Boolean = false,
)

data class StatsUiState(
    /** 24 buckets, index 0 = 23 hours ago, index 23 = current hour. */
    val hourlyBlocked: List<Int> = List(24) { 0 },
    val hourlyQueries: List<Int> = List(24) { 0 },
    /** Last 7 days, oldest first. */
    val dailyHistory: List<DailyCount> = emptyList(),
    /** Most blocked first, at most 20 entries. */
    val topBlockedDomains: List<DomainCount> = emptyList(),
)

data class SettingsUiState(
    val startOnBoot: Boolean = false,
    val upstreamDns: UpstreamDns = UpstreamDns.CLOUDFLARE,
    val filterLists: List<FilterListUi> = emptyList(),
    /** Domains never blocked (user allowlist), sorted alphabetically. */
    val allowlist: List<String> = emptyList(),
    val lastListUpdateMillis: Long? = null,
    val listUpdateState: ListUpdateState = ListUpdateState.IDLE,
    val appVersion: String = "",
)

interface AdBlockActions {
    /** Turns protection on (may trigger the VPN rationale / permission flow) or off. */
    fun toggleProtection()
    /** User accepted the rationale; the activity then launches the system VPN dialog. */
    fun confirmVpnRationale()
    fun dismissVpnRationale()
    fun openPrivateDnsSettings()
    fun setStartOnBoot(enabled: Boolean)
    fun setUpstreamDns(dns: UpstreamDns)
    fun setFilterListEnabled(id: String, enabled: Boolean)
    fun updateFilterLists()
    /** Returns false when [domain] is not a valid hostname (UI shows an error). */
    fun addAllowlistDomain(domain: String): Boolean
    fun removeAllowlistDomain(domain: String)
    fun clearStatistics()
}
