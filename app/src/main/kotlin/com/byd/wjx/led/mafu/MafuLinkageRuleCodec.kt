package com.byd.wjx.led.mafu

import com.byd.wjx.led.LedLinkageCatalog
import com.byd.wjx.led.LedLinkageTriggerType
import org.json.JSONArray
import org.json.JSONObject

internal object MafuLinkageRuleCodec {
    fun encode(rules: List<MafuLinkageRule>): String = JSONArray().apply {
        rules.forEach { rule ->
            put(JSONObject().apply {
                put("id", rule.id)
                put("enabled", rule.enabled)
                put("triggerType", rule.triggerType.name)
                put("conditionValue", rule.conditionValue)
                put("steps", JSONArray().apply {
                    rule.steps.forEach { step ->
                        put(JSONObject().apply {
                            put("remoteKey", step.remoteKey.keyNumber)
                            put("durationSeconds", step.durationSeconds)
                        })
                    }
                })
            })
        }
    }.toString()

    fun decode(raw: String, model: MafuRemoteModel): List<MafuLinkageRule>? = runCatching {
        val array = JSONArray(raw)
        val seenConditions = linkedSetOf<Pair<LedLinkageTriggerType, String>>()
        val seenIds = linkedSetOf<String>()
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val type = enumValueOrNull<LedLinkageTriggerType>(json.optString("triggerType"))
                    ?: continue
                val condition = json.optString("conditionValue")
                if (LedLinkageCatalog.options(type).none { it.value == condition }) continue
                if (!seenConditions.add(type to condition)) continue

                val stepsJson = json.optJSONArray("steps") ?: JSONArray()
                val steps = buildList {
                    for (stepIndex in 0 until stepsJson.length()) {
                        val stepJson = stepsJson.optJSONObject(stepIndex) ?: continue
                        val key = MafuRemoteKey.fromKeyNumber(stepJson.optInt("remoteKey", -1))
                            ?: continue
                        add(
                            MafuLinkageStep(
                                remoteKey = key,
                                durationSeconds = stepJson.optInt("durationSeconds", 5),
                            ).normalized(model)
                        )
                    }
                }
                if (steps.isEmpty()) continue

                val id = json.optString("id").ifBlank { "restored_${index}_${type.name}_$condition" }
                if (!seenIds.add(id)) continue
                add(
                    MafuLinkageRule(
                        id = id,
                        enabled = json.optBoolean("enabled", true),
                        triggerType = type,
                        conditionValue = condition,
                        steps = steps,
                    ).normalized(model)
                )
            }
        }
    }.getOrNull()

    private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }
}
