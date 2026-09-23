package com.example.memorysteps

import androidx.test.core.app.ApplicationProvider
import com.example.memorysteps.data.LearningDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** UI tests run on the development emulator; reset before Activity creation. */
class FreshLearningRecords : ExternalResource() {
    override fun before() = runBlocking(Dispatchers.IO) {
        LearningDatabase.get(ApplicationProvider.getApplicationContext()).clearAllTables()
    }
}
