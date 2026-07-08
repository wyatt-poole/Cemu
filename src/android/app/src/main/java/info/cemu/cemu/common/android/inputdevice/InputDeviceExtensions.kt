package info.cemu.cemu.common.android.inputdevice

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Vibrator
import android.view.InputDevice
import info.cemu.cemu.common.android.context.getDeviceVibrator
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

fun InputDevice.toControllerInfo(context: Context): NativeInput.ControllerInfo {
    require(isGameController())

    return NativeInput.ControllerInfo(
        id = id,
        descriptor = descriptor,
        name = name,
        hasRumble = hasRumble(context),
        hasMotion = hasMotion(context),
    )
}

fun listGameControllers() = InputDevice.getDeviceIds()
    .mapNotNull { InputDevice.getDevice(it) }
    .filter { it.isGameController() }

fun InputDevice.hasRumble(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager.vibratorIds.isNotEmpty()) {
        return true
    }

    // Some controllers (mostly Bluetooth ones) only expose their rumble
    // through the legacy input device vibrator
    @Suppress("DEPRECATION")
    if (vibrator.hasVibrator()) {
        return true
    }

    // Integrated controllers of handheld devices rumble through the device vibrator
    return !isExternal && context.getDeviceVibrator().hasVibrator()
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

fun Context.hasDeviceMotionSensors(): Boolean {
    val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager? ?: return false

    return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            && sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
}

fun InputDevice.hasMotion(context: Context): Boolean {
    if (findMotionSensorDevice() != null) {
        return true
    }

    // Integrated controllers of handheld devices use the device motion sensors
    return !isExternal && context.hasDeviceMotionSensors()
}

fun InputDevice.hasSensor(type: Int) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    sensorManager.getDefaultSensor(type) != null
} else {
    false
}

fun InputDevice.tryUseVibrator(context: Context, block: Vibrator.() -> Unit) {
    val ownVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager.vibratorIds.isNotEmpty()) {
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        vibrator
    }

    val vibrator = when {
        ownVibrator.hasVibrator() -> ownVibrator
        // Integrated controllers of handheld devices rumble through the device vibrator
        !isExternal -> context.getDeviceVibrator()
        else -> return
    }

    if (!vibrator.hasVibrator()) {
        return
    }

    block(vibrator)
}
