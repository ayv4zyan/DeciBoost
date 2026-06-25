package com.deciboost.app.debug

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.deciboost.app.service.BoostServiceClient
import com.deciboost.core.domain.BoostController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/** Debug/test trampoline for adb and instrumented tests. */
@AndroidEntryPoint
class BoostTrampolineActivity : AppCompatActivity() {

    @Inject lateinit var controller: BoostController
    @Inject lateinit var serviceClient: BoostServiceClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val boost = intent.getIntExtra(EXTRA_BOOST, 150).coerceIn(100, 200)
        runBlocking {
            serviceClient.ensureRunning().getOrThrow()
            controller.setBoostPercent(boost)
        }
        finish()
    }

    companion object {
        const val ACTION_SET_BOOST = "com.deciboost.SET_BOOST"
        const val EXTRA_BOOST = "boost"
    }
}
