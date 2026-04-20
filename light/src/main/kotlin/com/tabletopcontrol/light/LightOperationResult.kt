package com.tabletopcontrol.light

/**
 * Result model for recoverable light-control operations.
 *
 * UI-facing code can use these failures for consistent inline status messages
 * and dialog presentation without relying on exceptions for normal validation.
 */
sealed interface LightOperationResult {
    data object Applied : LightOperationResult

    sealed interface Failure : LightOperationResult {
        val operatorMessage: String
        val details: String?
    }

    sealed interface StateUpdateFailure : Failure
    sealed interface SerialFailure : Failure

    data class InvalidColor(val value: String) : StateUpdateFailure {
        override val operatorMessage: String =
            "Color must be a CSS hex value like #FFAA00 or #FA0."
        override val details: String = "Received: $value"
    }

    data class InvalidEffectSpeed(val value: Int) : StateUpdateFailure {
        override val operatorMessage: String =
            "Effect speed must stay between 0 and 255."
        override val details: String = "Received: $value"
    }

    data class InvalidEffectIntensity(val value: Int) : StateUpdateFailure {
        override val operatorMessage: String =
            "Effect intensity must stay between 0 and 255."
        override val details: String = "Received: $value"
    }

    data class InvalidBrightness(val value: Double) : StateUpdateFailure {
        override val operatorMessage: String =
            "Brightness must stay between 0% and 100%."
        override val details: String = "Received: $value"
    }

    data class InvalidPreset(val value: Int) : StateUpdateFailure {
        override val operatorMessage: String =
            "Preset ID must be between 1 and 250."
        override val details: String = "Received: $value"
    }

    data class InvalidPresetText(val value: String) : StateUpdateFailure {
        override val operatorMessage: String =
            "Enter a whole-number preset ID between 1 and 250."
        override val details: String? =
            value.takeIf { it.isNotBlank() }?.let { "Received: $it" }
    }

    data object MissingSerialPort : SerialFailure {
        override val operatorMessage: String =
            "Choose a serial port before connecting."
        override val details: String? = null
    }

    data class InvalidBaudRate(val value: String) : SerialFailure {
        override val operatorMessage: String =
            "Baud rate must be a whole number such as 115200."
        override val details: String = "Received: $value"
    }

    data class SerialConnectionFailed(
        val portName: String,
        override val details: String?,
    ) : SerialFailure {
        override val operatorMessage: String =
            "Could not connect to serial port $portName."
    }

    data class SerialWriteFailed(
        override val details: String?,
    ) : SerialFailure {
        override val operatorMessage: String =
            "Could not send the latest light update to the WLED device."
    }
}
