package info.cemu.cemu.common.android.inputdevice

import android.hardware.Sensor
import android.os.Build
import android.os.Vibrator
import android.view.InputDevice
import info.cemu.cemu.common.collections.mapNotNull
import info.cemu.cemu.nativeinterface.NativeInput

fun InputDevice.isGameController(): Boolean {
    if (isVirtual) {
        return false
    }

    return (sources and InputDevice.SOURCE_GAMEPAD)== InputDevice.SOURCE_GAMEPAD
            || (sources and InputDevice.SOURCE_JOYSTICK)== InputDevice.SOURCE_JOYSTICK
            || (sources and InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD
}

fun InputDevice.toControllerInfo(): NativeInput.ControllerInfo {
    require(isGameController())

    return NativeInput.ControllerInfo(
        id = id,
        descriptor = descriptor,
        name = name,
        hasRumble = hasRumble(),
        hasMotion = hasMotion(),
    )
}

fun listGameControllers() = InputDevice.getDeviceIds()
    .mapNotNull { InputDevice.getDevice(it) }
    .filter { it.isGameController() }

fun InputDevice.hasRumble(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager.vibratorIds.isNotEmpty()) {
        return true
    }

    // Some controllers (mostly Bluetooth ones) only expose their rumble
    // through the legacy input device vibrator
    @Suppress("DEPRECATION")
    return vibrator.hasVibrator()
}

// Motion sensors of external (e.g. Bluetooth) controllers may be exposed through
// a sibling input device that shares the descriptor of the game controller device
fun InputDevice.findMotionSensorDevice(): InputDevice? {
    if (hasSensor(Sensor.TYPE_ACCELEROMETER) && hasSensor(Sensor.TYPE_GYROSCOPE)) {
        return this
    }

    return InputDevice.getDeviceIds()
        .mapNotNull { InputDevice.getDevice(it) }
        .firstOrNull {
            it.id != id && it.descriptor == descriptor
                    && it.hasSensor(Sensor.TYPE_ACCELEROMETER) && it.hasSensor(Sensor.TYPE_GYROSCOPE)
        }
}

fun InputDevice.hasMotion() = findMotionSensorDevice() != null

fun InputDevice.hasSensor(type: Int) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    sensorManager.getDefaultSensor(type) != null
} else {
    false
}

fun InputDevice.tryUseVibrator(block: Vibrator.() -> Unit) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager.vibratorIds.isNotEmpty()) {
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        vibrator
    }

    if (!vibrator.hasVibrator()) {
        return
    }

    block(vibrator)
}
