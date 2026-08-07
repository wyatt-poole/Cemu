package info.cemu.cemu.settings.input.device

import android.os.VibrationEffect
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import info.cemu.cemu.R
import info.cemu.cemu.common.android.context.getDeviceVibrator
import info.cemu.cemu.common.settings.AppSettingsStore
import info.cemu.cemu.common.ui.components.ScreenContent
import info.cemu.cemu.common.ui.components.Slider
import info.cemu.cemu.common.ui.components.Toggle
import info.cemu.cemu.common.ui.localization.controllerTypeToString
import info.cemu.cemu.common.ui.localization.tr
import info.cemu.cemu.nativeinterface.NativeInput
import kotlinx.coroutines.launch

private val ControllerIndexChoices = (0..<NativeInput.MAX_CONTROLLERS).toList()

private val MotionControllerTypes = setOf(
    NativeInput.EmulatedControllerType.VPAD,
    NativeInput.EmulatedControllerType.PRO,
    NativeInput.EmulatedControllerType.WIIMOTE,
)

@Composable
fun DeviceInputSettingsScreen(navigateBack: () -> Unit) {
    DisposableEffect(Unit) {
        onDispose {
            NativeInput.saveInputs()
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vibrator = remember { context.getDeviceVibrator() }

    var deviceControllerIndices by remember {
        mutableStateOf(NativeInput.getDeviceControllerIndices().toSet())
    }

    val appSettings by AppSettingsStore.dataStore.data.collectAsState(initial = null)
    val isMotionEnabled = appSettings?.emulationSettings?.isMotionEnabled ?: false

    ScreenContent(
        appBarText = tr("Device settings"),
        navigateBack = navigateBack,
    ) {
        Text(
            text = tr("Use this device's motion sensors and vibrator for the selected controller ports"),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp),
        )

        ControllerIndexChoices.forEach { index ->
            Toggle(
                label = tr("Controller {0}", index + 1),
                checked = index in deviceControllerIndices,
                enabled = !NativeInput.isControllerDisabled(index),
                description = controllerTypeToString(NativeInput.getControllerType(index)),
                onCheckedChanged = { enabled ->
                    NativeInput.setDeviceControllerEnabled(index, enabled)
                    deviceControllerIndices = NativeInput.getDeviceControllerIndices().toSet()
                },
            )
        }

        val hasPortWithoutMotionSupport = deviceControllerIndices.any {
            NativeInput.getControllerType(it) !in MotionControllerTypes
        }

        if (hasPortWithoutMotionSupport) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = tr("To use motion input, the controller type must be set to Wii U GamePad, Wii U Pro Controller or Wiimote"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Toggle(
            label = tr("Enable motion"),
            checked = isMotionEnabled,
            description = tr("Use the device motion sensors for motion input. Can also be toggled from the in-game menu"),
            onCheckedChanged = { enabled ->
                scope.launch {
                    AppSettingsStore.dataStore.updateData {
                        it.copy(emulationSettings = it.emulationSettings.copy(isMotionEnabled = enabled))
                    }
                }
            },
        )

        Slider(
            label = tr("Rumble"),
            initialValue = { (NativeInput.getDeviceRumble() * 100f).toInt() },
            steps = 19,
            valueFrom = 0,
            valueTo = 100,
            labelFormatter = { "$it%" },
            onValueChange = {
                val rumble = it / 100f

                NativeInput.setDeviceRumble(rumble)

                val amplitude = (255 * rumble).toInt()
                vibrator.cancel()
                if (amplitude in 1..255) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500L, amplitude))
                }
            },
        )
    }
}
