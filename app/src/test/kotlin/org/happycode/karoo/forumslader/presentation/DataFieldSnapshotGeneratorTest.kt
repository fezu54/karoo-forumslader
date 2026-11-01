package org.happycode.karoo.forumslader.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import fi.nikosavola.karooext.testing.hasColorNear
import fi.nikosavola.karooext.testing.inflate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.texts
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ViewConfig
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.application.estimation.BatteryEstimateStore
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.estimation.BatteryEstimate
import org.happycode.karoo.forumslader.extension.ForumsladerExtension
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "night-xhdpi")
class DataFieldSnapshotGeneratorTest {

    @get:Rule
    val karoo = FakeKarooRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        BatteryEstimateStore.clear()
    }

    @After
    fun tearDown() {
        BatteryEstimateStore.clear()
    }

    @Test
    fun `should render standard data field with sufficient route range when navigating with surplus capacity`() {
        // given
        val scenario = SnapshotScenario(
            fileName = "battery-range-standard-sufficient.png",
            config = standardViewConfig(),
            batteryLevel = 80,
            chargeState = ChargeState.DISCHARGING,
            estimatedRangeKm = 80f,
            routeRemainingKm = 50f,
            isSufficientForRoute = true
        )

        // when
        val rendered = renderAndExportSnapshot(scenario)

        // then
        assertSnapshotMatches(
            rendered,
            expectedTexts = listOf("80", "%", "80", "km ✓"),
            expectedLevelColorRes = R.color.karoo_battery_good,
            expectedUnitColorRes = R.color.karoo_status_ok
        )
    }

    @Test
    fun `should render compact data field with sufficient route range when navigating with surplus capacity`() {
        // given
        val scenario = SnapshotScenario(
            fileName = "battery-range-compact-sufficient.png",
            config = compactViewConfig(),
            batteryLevel = 80,
            chargeState = ChargeState.DISCHARGING,
            estimatedRangeKm = 80f,
            routeRemainingKm = 50f,
            isSufficientForRoute = true
        )

        // when
        val rendered = renderAndExportSnapshot(scenario)

        // then
        assertSnapshotMatches(
            rendered,
            expectedTexts = listOf("80", "%", "80", "km ✓"),
            expectedLevelColorRes = R.color.karoo_battery_good,
            expectedUnitColorRes = R.color.karoo_status_ok
        )
    }

    @Test
    fun `should render standard data field with insufficient route range when navigating with range deficit`() {
        // given
        val scenario = SnapshotScenario(
            fileName = "battery-range-standard-insufficient.png",
            config = standardViewConfig(),
            batteryLevel = 40,
            chargeState = ChargeState.DISCHARGING,
            estimatedRangeKm = 40f,
            routeRemainingKm = 70f,
            isSufficientForRoute = false
        )

        // when
        val rendered = renderAndExportSnapshot(scenario)

        // then
        assertSnapshotMatches(
            rendered,
            expectedTexts = listOf("40", "%", "40", "km !"),
            expectedLevelColorRes = R.color.karoo_battery_medium,
            expectedUnitColorRes = R.color.karoo_status_warn
        )
    }

    @Test
    fun `should render compact data field with insufficient route range when navigating with range deficit`() {
        // given
        val scenario = SnapshotScenario(
            fileName = "battery-range-compact-insufficient.png",
            config = compactViewConfig(),
            batteryLevel = 40,
            chargeState = ChargeState.DISCHARGING,
            estimatedRangeKm = 40f,
            routeRemainingKm = 70f,
            isSufficientForRoute = false
        )

        // when
        val rendered = renderAndExportSnapshot(scenario)

        // then
        assertSnapshotMatches(
            rendered,
            expectedTexts = listOf("40", "%", "40", "km !"),
            expectedLevelColorRes = R.color.karoo_battery_medium,
            expectedUnitColorRes = R.color.karoo_status_warn
        )
    }

    @Test
    fun `should render standard data field with charging indicator when dynamo is actively charging`() {
        // given
        val scenario = SnapshotScenario(
            fileName = "battery-range-standard-charging.png",
            config = standardViewConfig(),
            batteryLevel = 75,
            chargeState = ChargeState.CHARGING,
            estimatedRangeKm = null,
            routeRemainingKm = 50f,
            isSufficientForRoute = true
        )

        // when
        val rendered = renderAndExportSnapshot(scenario)

        // then
        assertSnapshotMatches(
            rendered,
            expectedTexts = listOf("75", "⚡%", context.getString(R.string.charge_state_charging)),
            expectedLevelColorRes = R.color.karoo_battery_good,
            expectedRangeColorRes = R.color.karoo_battery_good
        )
    }

    @Test
    fun `should render compact data field with charging indicator when dynamo is actively charging`() {
        // given
        val scenario = SnapshotScenario(
            fileName = "battery-range-compact-charging.png",
            config = compactViewConfig(),
            batteryLevel = 75,
            chargeState = ChargeState.CHARGING,
            estimatedRangeKm = null,
            routeRemainingKm = 50f,
            isSufficientForRoute = true
        )

        // when
        val rendered = renderAndExportSnapshot(scenario)

        // then
        assertSnapshotMatches(
            rendered,
            expectedTexts = listOf("75", "⚡%", context.getString(R.string.charge_state_charging)),
            expectedLevelColorRes = R.color.karoo_battery_good,
            expectedRangeColorRes = R.color.karoo_battery_good
        )
    }

    @Test
    fun `should render standard data field with low battery alert when capacity drops below threshold`() {
        // given
        val scenario = SnapshotScenario(
            fileName = "battery-range-standard-low-battery.png",
            config = standardViewConfig(),
            batteryLevel = 15,
            chargeState = ChargeState.DISCHARGING,
            estimatedRangeKm = 18f,
            routeRemainingKm = 50f,
            isSufficientForRoute = false
        )

        // when
        val rendered = renderAndExportSnapshot(scenario)

        // then
        assertSnapshotMatches(
            rendered,
            expectedTexts = listOf("15", "%", "18", "km !"),
            expectedLevelColorRes = R.color.karoo_battery_low,
            expectedUnitColorRes = R.color.karoo_status_warn
        )
    }

    @Test
    fun `should render compact data field with low battery alert when capacity drops below threshold`() {
        // given
        val scenario = SnapshotScenario(
            fileName = "battery-range-compact-low-battery.png",
            config = compactViewConfig(),
            batteryLevel = 15,
            chargeState = ChargeState.DISCHARGING,
            estimatedRangeKm = 18f,
            routeRemainingKm = 50f,
            isSufficientForRoute = false
        )

        // when
        val rendered = renderAndExportSnapshot(scenario)

        // then
        assertSnapshotMatches(
            rendered,
            expectedTexts = listOf("15", "%", "18", "km !"),
            expectedLevelColorRes = R.color.karoo_battery_low,
            expectedUnitColorRes = R.color.karoo_status_warn
        )
    }

    private fun renderAndExportSnapshot(scenario: SnapshotScenario): RenderedSnapshot {
        val estimate = BatteryEstimate(
            remainingCapacityPct = scenario.batteryLevel,
            avgDischargeRatePctPerKm = if (scenario.chargeState == ChargeState.CHARGING) 0f else 1.0f,
            estimatedRangeKm = scenario.estimatedRangeKm,
            routeRemainingKm = scenario.routeRemainingKm,
            isSufficientForRoute = scenario.isSufficientForRoute,
            chargeState = scenario.chargeState
        )
        BatteryEstimateStore.updateEstimate(estimate)

        val host = karoo.host<ForumsladerExtension>()
        val viewRecorder = host.startView(DataFieldId.BATTERY_LEVEL, scenario.config)

        val values = buildMap {
            put(DataFieldId.FIELD_BATTERY_LEVEL, scenario.batteryLevel.toDouble())
            put(DataFieldId.FIELD_CHARGE_STATE, scenario.chargeState.ordinal.toDouble())
            scenario.estimatedRangeKm?.let { put(DataFieldId.FIELD_RANGE_KM, it.toDouble()) }
            scenario.isSufficientForRoute?.let {
                put(DataFieldId.FIELD_ROUTE_SUFFICIENT, if (it) 1.0 else 0.0)
            }
        }
        val dataTypeId = DataType.dataTypeId(EXTENSION_ID, DataFieldId.BATTERY_LEVEL)
        karoo.system.setDataPoint(DataPoint(dataTypeId, values))
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val remoteViews = viewRecorder.awaitFrame(AWAIT_TIMEOUT_MS)
        val inflatedView = remoteViews.inflate(context, scenario.config)
        val bitmap = drawViewToBitmap(inflatedView, scenario.config)
        val outputFile = exportBitmapToPng(bitmap, scenario.fileName)

        return RenderedSnapshot(
            view = inflatedView,
            bitmap,
            file = outputFile
        )
    }

    private fun drawViewToBitmap(view: View, config: ViewConfig): Bitmap {
        val width = config.viewSize.first
        val height = config.viewSize.second
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            Canvas(bitmap).apply {
                drawColor(KAROO_COCKPIT_BG_COLOR)
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = KAROO_COCKPIT_BORDER_COLOR
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                drawRoundRect(
                    RectF(1f, 1f, width - 1f, height - 1f),
                    8f,
                    8f,
                    borderPaint
                )
                view.draw(this)
            }
        }
    }

    private fun exportBitmapToPng(bitmap: Bitmap, fileName: String): File {
        val rootPath = System.getProperty("project.rootDir") ?: "."
        val outputDir = File(rootPath, "docs/images/data-fields").apply { mkdirs() }
        return File(outputDir, fileName).also { file ->
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }

    private fun assertSnapshotMatches(
        rendered: RenderedSnapshot,
        expectedTexts: List<String>,
        expectedLevelColorRes: Int,
        expectedRangeColorRes: Int = R.color.karoo_text_primary,
        expectedUnitColorRes: Int? = null
    ) {
        assertEquals(expectedTexts, rendered.view.texts())
        assertTrue(rendered.file.exists() && rendered.file.length() > 0L)

        val expectedLevelColor = ContextCompat.getColor(context, expectedLevelColorRes)
        val levelTextView = rendered.view.findViewById<TextView>(R.id.battery_level_value)
        assertEquals(expectedLevelColor, levelTextView.currentTextColor)
        assertTrue(rendered.bitmap.hasColorNear(expectedLevelColor, tolerance = COLOR_MATCH_TOLERANCE))

        val expectedRangeColor = ContextCompat.getColor(context, expectedRangeColorRes)
        val rangeTextView = rendered.view.findViewById<TextView>(R.id.battery_range_value)
        assertEquals(expectedRangeColor, rangeTextView.currentTextColor)

        expectedUnitColorRes?.let { unitColorRes ->
            val expectedUnitColor = ContextCompat.getColor(context, unitColorRes)
            val unitTextView = rendered.view.findViewById<TextView>(R.id.battery_range_unit)
            assertEquals(expectedUnitColor, unitTextView.currentTextColor)
            assertTrue(rendered.bitmap.hasColorNear(expectedUnitColor, tolerance = COLOR_MATCH_TOLERANCE))
        }
    }

    private fun standardViewConfig() = ViewConfig(
        gridSize = 60 to 15,
        viewSize = 400 to 110,
        textSize = 56,
        alignment = ViewConfig.Alignment.CENTER,
        boundariesEnabled = true,
        preview = false
    )

    private fun compactViewConfig() = ViewConfig(
        gridSize = 30 to 15,
        viewSize = 200 to 110,
        textSize = 44,
        alignment = ViewConfig.Alignment.CENTER,
        boundariesEnabled = true,
        preview = false
    )

    private data class SnapshotScenario(
        val fileName: String,
        val config: ViewConfig,
        val batteryLevel: Int,
        val chargeState: ChargeState,
        val estimatedRangeKm: Float?,
        val routeRemainingKm: Float?,
        val isSufficientForRoute: Boolean?
    )

    private data class RenderedSnapshot(
        val view: View,
        val bitmap: Bitmap,
        val file: File
    )

    companion object {
        private const val EXTENSION_ID = "karoo-forumslader"
        private const val AWAIT_TIMEOUT_MS = 5_000L
        private const val COLOR_MATCH_TOLERANCE = 32
        private const val KAROO_COCKPIT_BG_COLOR = 0xFF121212.toInt()
        private const val KAROO_COCKPIT_BORDER_COLOR = 0xFF2C2C2C.toInt()
    }
}
