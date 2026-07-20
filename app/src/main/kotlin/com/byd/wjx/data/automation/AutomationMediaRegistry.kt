package com.byd.wjx.data.automation

internal object AutomationMediaRegistry {
    @Volatile var outsideOwner: String? = null
    @Volatile var musicOwner: String? = null
    @Volatile var videoOwner: String? = null

    fun ruleId(owner: String?): Long? {
        val value = owner ?: return null
        if (!value.startsWith("automation:")) return null
        return value.substringAfter("automation:").substringBefore(':').toLongOrNull()
    }
}
