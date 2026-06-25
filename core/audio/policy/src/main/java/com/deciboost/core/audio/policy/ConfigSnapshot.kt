package com.deciboost.core.audio.policy

data class ConfigSnapshot(
    val count: Int,
    val usageHash: Int,
    val hasMediaUsage: Boolean = true,
    val hasNotificationUsage: Boolean = false,
    val hasAssistanceUsage: Boolean = false,
)

enum class ConfigDiffKind {
    NONE,
    COUNT_INCREASED,
    COUNT_DECREASED,
    USAGE_CHANGED,
}

data class ConfigDiff(
    val kind: ConfigDiffKind,
    val previous: ConfigSnapshot?,
    val current: ConfigSnapshot,
)

fun diff(prev: ConfigSnapshot?, next: ConfigSnapshot): ConfigDiff {
    if (prev == null) {
        return ConfigDiff(
            kind = if (next.count > 0) ConfigDiffKind.COUNT_INCREASED else ConfigDiffKind.NONE,
            previous = null,
            current = next,
        )
    }
    val kind = when {
        next.count > prev.count -> ConfigDiffKind.COUNT_INCREASED
        next.count < prev.count -> ConfigDiffKind.COUNT_DECREASED
        next.usageHash != prev.usageHash -> ConfigDiffKind.USAGE_CHANGED
        else -> ConfigDiffKind.NONE
    }
    return ConfigDiff(kind = kind, previous = prev, current = next)
}
