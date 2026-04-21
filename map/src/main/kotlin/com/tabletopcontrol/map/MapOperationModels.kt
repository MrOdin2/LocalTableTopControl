package com.tabletopcontrol.map

sealed interface MapResult<out T> {
    data class Success<T>(val value: T) : MapResult<T>

    data class Failure(val error: MapOperationError) : MapResult<Nothing>

    companion object {
        fun <T> success(value: T): MapResult<T> = Success(value)

        fun failure(error: MapOperationError): MapResult<Nothing> = Failure(error)
    }
}

enum class MapInputField {
    CELL_SIZE,
    SCALE,
    OFFSET_X,
    OFFSET_Y,
    GUIDED_TILE_SPAN,
    CONE_ANGLE,
    MEASUREMENT_UNITS,
    MEASUREMENT_LABEL,
}

sealed interface MapOperationError {
    data class Validation(
        val field: MapInputField,
        val reason: String,
    ) : MapOperationError

    data class UnsupportedMeasurementUnits(val value: String) : MapOperationError

    data class MeasurementNotFound(val id: String) : MapOperationError

    data object GuidedCalibrationBaseMissing : MapOperationError

    data object GuidedCalibrationTargetTooClose : MapOperationError

    data object TokenDragNotActive : MapOperationError

    data class ImageLoadFailed(
        val resourcePath: String,
        val causeMessage: String? = null,
    ) : MapOperationError
}

fun MapOperationError.toUserMessage(): String =
    when (this) {
        is MapOperationError.Validation -> reason
        is MapOperationError.UnsupportedMeasurementUnits ->
            "Measurement units must be one of: ft, m."
        is MapOperationError.MeasurementNotFound ->
            "That measurement is no longer available."
        MapOperationError.GuidedCalibrationBaseMissing ->
            "Complete Step 1 before applying the guided scale adjustment."
        MapOperationError.GuidedCalibrationTargetTooClose ->
            "Move the second point at least one screen pixel away from the centre point on the selected axis."
        MapOperationError.TokenDragNotActive ->
            "Start dragging a token before moving it."
        is MapOperationError.ImageLoadFailed -> {
            val details = causeMessage?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            "Failed to load map image from '$resourcePath'$details"
        }
    }

inline fun <T> MapResult<T>.onSuccess(block: (T) -> Unit): MapResult<T> {
    if (this is MapResult.Success) {
        block(value)
    }
    return this
}

inline fun <T> MapResult<T>.onFailure(block: (MapOperationError) -> Unit): MapResult<T> {
    if (this is MapResult.Failure) {
        block(error)
    }
    return this
}

fun <T> MapResult<T>.getOrNull(): T? =
    when (this) {
        is MapResult.Success -> value
        is MapResult.Failure -> null
    }
