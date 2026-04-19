package com.tabletopcontrol.map

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapCalibrationServiceTest {

    private val service = MapCalibrationService()

    @Test
    fun `parse map calibration returns typed validation error for bad scale`() {
        val result = service.parseMapCalibration(
            scaleText = "0",
            offsetXText = "10",
            offsetYText = "-4",
        )

        assertTrue(result is MapResult.Failure)
        val error = (result as MapResult.Failure).error
        assertEquals(
            MapOperationError.Validation(
                field = MapInputField.SCALE,
                reason = "Scale must be a positive number.",
            ),
            error,
        )
        assertEquals("Scale must be a positive number.", error.toUserMessage())
    }

    @Test
    fun `guided step2 requires step1 calibration`() {
        val result = service.guidedStep2(
            step1Calibration = null,
            worldX = 120.0,
            worldY = 140.0,
            canvasCenterX = 100.0,
            canvasCenterY = 100.0,
            gridCellPixels = 50.0,
        )

        assertTrue(result is MapResult.Failure)
        assertEquals(
            MapOperationError.GuidedCalibrationBaseMissing,
            (result as MapResult.Failure).error,
        )
    }
}
