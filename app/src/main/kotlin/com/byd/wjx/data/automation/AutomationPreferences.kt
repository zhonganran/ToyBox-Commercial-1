package com.byd.wjx.data.automation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Owns migration markers for the automation database seed data. */
@Singleton
class AutomationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isStarterTemplateCleanupComplete(): Boolean =
        preferences.getBoolean(KEY_STARTER_TEMPLATE_CLEANUP_DONE, false)

    fun markStarterTemplateCleanupComplete() {
        preferences.edit()
            .putBoolean(KEY_STARTER_TEMPLATE_CLEANUP_DONE, true)
            .putBoolean(KEY_TEMPLATES_INSERTED, true)
            .apply()
    }

    companion object {
        const val PREFERENCES_NAME = "automation"
        private const val KEY_STARTER_TEMPLATE_CLEANUP_DONE = "starter_templates_cleanup_done"
        private const val KEY_TEMPLATES_INSERTED = "templates_inserted"
    }
}
