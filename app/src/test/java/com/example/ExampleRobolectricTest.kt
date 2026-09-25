package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.*
import com.example.ml.FeatureExtractor
import com.example.ml.IsolationForestModel
import com.example.ml.RandomForestModel
import com.example.risk.RiskEngine
import com.example.rules.AbnormalResourceRule
import com.example.rules.PersistentBackgroundRule
import com.example.rules.RuleEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Cyfex", appName)
    }

    @Test
    fun `test abnormal resource rule triggers on high cpu`() {
        val rule = AbnormalResourceRule()
        val app = createSampleApp("com.test.miner")
        val procs = listOf(
            ProcessRecord(
                pid = 5001,
                ppid = 1,
                user = "u0_a11",
                processName = "com.test.miner:worker",
                packageName = "com.test.miner",
                cpuPercent = 75.0,
                vszKb = 2000000L,
                rssKb = 150000L,
                state = "R"
            )
        )
        val result = rule.evaluate(app, procs, emptyList())
        assertTrue("Resource rule must trigger on 75% CPU", result.triggered)
        assertEquals(RiskLevel.CRITICAL, result.severity)
    }

    @Test
    fun `test ml random forest probability computation`() {
        val rf = RandomForestModel()
        val extractor = FeatureExtractor()
        val app = createSampleApp("com.suspicious.app").copy(
            dangerousPermissions = listOf("RECORD_AUDIO", "ACCESS_FINE_LOCATION", "CAMERA", "READ_SMS")
        )
        val vector = extractor.extract(app, emptyList(), emptyList())
        val prob = rf.predictMaliciousProbability(vector)
        assertTrue("Probability must be bounded between 0 and 1", prob in 0.0..1.0)
    }

    @Test
    fun `test risk engine combines scores`() {
        val riskEngine = RiskEngine()
        val app = createSampleApp("com.safe.app")
        val ruleEngine = RuleEngine()
        val ruleResults = ruleEngine.evaluateAll(app, emptyList(), emptyList())
        val breakdown = riskEngine.calculateRisk(app, emptyList(), emptyList(), ruleResults, 0.05, 0.05)

        assertEquals(RiskLevel.SAFE, breakdown.riskLevel)
        assertTrue("Safe app score should be low", breakdown.totalScore < 30)
    }

    private fun createSampleApp(packageName: String): AppSecurityTelemetry {
        return AppSecurityTelemetry(
            packageName = packageName,
            appName = "Sample App",
            versionName = "1.0",
            versionCode = 1L,
            uid = 10100,
            isSystemApp = false,
            installTime = System.currentTimeMillis(),
            updateTime = System.currentTimeMillis(),
            staticMetrics = AppStaticMetrics(
                apkSizeMb = 10.0,
                sha256Hash = "hash123",
                signingCertHash = "cert123",
                dexCount = 1,
                nativeLibsCount = 0,
                isDebuggable = false,
                minSdk = 24,
                targetSdk = 34
            ),
            components = AppComponentInfo(1, 0, 0, 0, 1, 0, 0, 0),
            requestedPermissions = listOf("android.permission.INTERNET"),
            grantedPermissions = listOf("android.permission.INTERNET"),
            dangerousPermissions = emptyList(),
            overallRiskScore = 0,
            riskLevel = RiskLevel.SAFE,
            staticScore = 0,
            runtimeScore = 0,
            networkScore = 0,
            anomalyScore = 0.0,
            mlMaliciousProb = 0.0
        )
    }
}
