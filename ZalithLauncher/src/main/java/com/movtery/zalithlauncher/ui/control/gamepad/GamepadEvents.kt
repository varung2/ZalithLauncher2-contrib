/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.control.gamepad

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movtery.layer_controller.event.ClickEvent
import com.movtery.zalithlauncher.game.keycodes.ControlEventKeycode
import com.movtery.zalithlauncher.game.keycodes.mapToControlEvent
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.ui.control.event.LAUNCHER_EVENT_SCROLL_DOWN_SINGLE
import com.movtery.zalithlauncher.ui.control.event.LAUNCHER_EVENT_SCROLL_UP_SINGLE
import com.movtery.zalithlauncher.ui.control.joystick.allAction
import com.movtery.zalithlauncher.ui.control.joystick.directionMapping
import com.movtery.zalithlauncher.viewmodel.GamepadRemapperViewModel
import com.movtery.zalithlauncher.viewmodel.GamepadViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 简单的手柄摇杆、按键事件捕获层
 */
@Composable
fun SimpleGamepadCapture(
    gamepadViewModel: GamepadViewModel
) {
    val view = LocalView.current
    val remapperViewModel: GamepadRemapperViewModel = viewModel()

    GamepadRemapperDialog(
        operation = remapperViewModel.uiOperation,
        changeOperation = { remapperViewModel.uiOperation = it },
        remapperViewModel = remapperViewModel,
        steps = buildRemapperSteps(
            a = true, b = true, x = true, y = true,
            start = true, select = true,
            leftJoystick = true, leftJoystickButton = true,
            rightJoystick = true, rightJoystickButton = true,
            leftShoulder = true, rightShoulder = true,
            leftTrigger = true, rightTrigger = true,
            dpad = true
        )
    )

    //是否正在绑定键值
    val isBinding = remember(remapperViewModel.uiOperation) { remapperViewModel.uiOperation != GamepadRemapOperation.None }
    fun isBinding() = remapperViewModel.uiOperation != GamepadRemapOperation.None

    DisposableEffect(view, gamepadViewModel, isBinding) {
        // Track which devices have been registered to avoid redundant calls
        val registeredDevices = mutableSetOf<String>()
        
        val motionListener = View.OnGenericMotionListener { _, event ->
            // android.util.Log.d("GAMEPAD_RAW", "Motion event received: action=${event.action}, source=${event.source}")
            
            if (isBinding()) {
                remapperViewModel.sendEvent(
                    GamepadRemapperViewModel.Event.Axis(event)
                )
                true
            } else if (event.isGamepadEvent() && event.action == MotionEvent.ACTION_MOVE) {
                // android.util.Log.d("GAMEPAD_RAW", "Gamepad motion detected! deviceName=${event.getDeviceName()}")
                val deviceName = event.getDeviceName()
                
                // Register device name once (only in native mode, only if not already registered)
                // This serves as a fallback for controllers connected AFTER game starts (i.e. hot-plugged)
                if (AllSettings.gamepadNativeMode.getValue() && !registeredDevices.contains(deviceName)) {
                    event.device?.name?.let { realDeviceName ->
                        gamepadViewModel.registerDeviceName(deviceName, realDeviceName)
                        registeredDevices.add(deviceName)
                        // android.util.Log.d("GAMEPAD_RAW", "Fallback registration: deviceName=$deviceName -> $realDeviceName")
                    }
                }
                
                val remapper = remapperViewModel.findMapping(deviceName)
                
                // In native mode, handle input directly even without remapper
                if (AllSettings.gamepadNativeMode.getValue()) {
                    // android.util.Log.d("GAMEPAD_RAW", "Native mode: handling motion")
                    // No remapper needed in native mode - handle directly
                    handleNativeMotionInput(event, gamepadViewModel, deviceName)
                } else {
                    if (remapper == null) {
                    // Keyboard emulation mode needs remapper - prompt for it
                        remapperViewModel.startRemapperUI(deviceName)
                    } else {
                        // Keyboard emulation with remapper
                        remapper.handleMotionEventInput(event, gamepadViewModel, deviceName)
                    }
                }
                true
            } else false
        }

        val keyListener = View.OnKeyListener { _, keyCode, keyEvent ->
            // android.util.Log.d("GAMEPAD_RAW", "Key event received: keyCode=$keyCode, action=${keyEvent.action}, device=${keyEvent.device?.name}")
            
            if (keyEvent.isGamepadKeyEvent()) {
                // android.util.Log.d("GAMEPAD_RAW", "Gamepad key detected! deviceName=${keyEvent.getDeviceName()}")
                if (isBinding()) {
                    remapperViewModel.sendEvent(
                        GamepadRemapperViewModel.Event.Button(keyCode, keyEvent)
                    )
                } else {
                    val deviceName = keyEvent.getDeviceName()
                    
                    // Register device name once (only in native mode, only if not already registered)
                    // This serves as a fallback for controllers connected AFTER game starts (i.e. hot-plugged)
                    if (AllSettings.gamepadNativeMode.getValue() && !registeredDevices.contains(deviceName)) {
                        keyEvent.device?.name?.let { realDeviceName ->
                            gamepadViewModel.registerDeviceName(deviceName, realDeviceName)
                            registeredDevices.add(deviceName)
                            // android.util.Log.d("GAMEPAD_RAW", "Fallback registration: deviceName=$deviceName -> $realDeviceName")
                        }
                    }
                    
                    val remapper = remapperViewModel.findMapping(deviceName)
                    
                    // In native mode, handle input directly even without remapper
                    if (AllSettings.gamepadNativeMode.getValue()) {
                        // android.util.Log.d("GAMEPAD_RAW", "Native mode: handling button keyCode=$keyCode")
                        // No remapper needed in native mode - handle directly
                        handleNativeKeyInput(keyEvent, gamepadViewModel, deviceName)
                    } else {
                        if (remapper == null) {
                            // Keyboard emulation mode needs remapper - prompt for it
                            remapperViewModel.startRemapperUI(deviceName)
                        } else {
                            // Keyboard emulation with remapper
                            remapper.handleKeyEventInput(keyEvent, gamepadViewModel, deviceName)
                        }
                    }
                }
                true
            } else {
                // android.util.Log.d("GAMEPAD_RAW", "Not a gamepad key event")
                false
            }
        }

        view.setOnGenericMotionListener(motionListener)
        view.setOnKeyListener(keyListener)

        onDispose {
            view.setOnGenericMotionListener(null)
            view.setOnKeyListener(null)
        }
    }

    LaunchedEffect(gamepadViewModel.gamepadEngaged, isBinding) {
        withContext(Dispatchers.Default) {
            while (true) {
                try {
                    ensureActive()
                    val binding = withContext(Dispatchers.Main) { isBinding() }
                    if (binding) break

                    // Skip joystick polling in native mode - raw events are handled directly
                    val isNativeMode = AllSettings.gamepadNativeMode.getValue()
                    if (isNativeMode) {
                        delay(100) // Just keep the loop alive for activity checking
                        continue
                    }

                    //检查手柄活动状态
                    val pollLevel = gamepadViewModel.checkGamepadActive()
                    if (pollLevel == GamepadViewModel.PollLevel.Close) break

                    gamepadViewModel.pollJoystick()
                    delay(pollLevel.delayMs)
                } catch (_: CancellationException) {
                    break
                }
            }
        }
    }
}

/**
 * 手柄事件监听者
 * @param listener 事件回调
 */
@Composable
private fun GamepadEventListener(
    gamepadViewModel: GamepadViewModel,
    listener: (GamepadViewModel.Event) -> Unit,
    onDisposeCallback: (() -> Unit)? = null
) {
    DisposableEffect(gamepadViewModel) {
        gamepadViewModel.addListener(listener)
        onDispose {
            onDisposeCallback?.invoke()
            gamepadViewModel.removeListener(listener)
        }
    }
}

/**
 * 统一实现的手柄按键事件监听器
 * @param isGrabbing 用于判断是否处于游戏中，区分游戏内、菜单内的按键绑定
 * @param onKeyEvent 键盘映射事件回调
 * @param onAction 手柄触发任意操作时
 */
@Composable
fun GamepadKeyListener(
    gamepadViewModel: GamepadViewModel,
    isGrabbing: Boolean,
    onKeyEvent: (targets: List<ClickEvent>, pressed: Boolean) -> Unit,
    onAction: () -> Unit
) {
    fun guessEvent(event: String): ClickEvent {
        return when (event) {
            ControlEventKeycode.GLFW_MOUSE_BUTTON_LEFT,
            ControlEventKeycode.GLFW_MOUSE_BUTTON_RIGHT,
            ControlEventKeycode.GLFW_MOUSE_BUTTON_MIDDLE
                -> ClickEvent(type = ClickEvent.Type.LauncherEvent, event)

            SPECIAL_KEY_MOUSE_SCROLL_UP -> ClickEvent(type = ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_UP_SINGLE)
            SPECIAL_KEY_MOUSE_SCROLL_DOWN -> ClickEvent(type = ClickEvent.Type.LauncherEvent, LAUNCHER_EVENT_SCROLL_DOWN_SINGLE)

            else -> ClickEvent(type = ClickEvent.Type.Key, event)
        }
    }

    val inGame by rememberUpdatedState(isGrabbing)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)
    val currentOnAction by rememberUpdatedState(onAction)

    val lastPressKey = remember { mutableStateMapOf<Int, List<ClickEvent>>() }
    val lastPressDpad = remember { mutableStateMapOf<DpadDirection, List<ClickEvent>>() }

    GamepadEventListener(
        gamepadViewModel = gamepadViewModel,
        listener = { event ->
            currentOnAction()

            when (event) {
                is GamepadViewModel.Event.Button -> {
                    if (!event.pressed) {
                        //松开时使用之前记录的按下事件
                        lastPressKey[event.code]?.let { lastEvents ->
                            currentOnKeyEvent(lastEvents, false)
                            lastPressKey.remove(event.code)
                        }
                    } else {
                        gamepadViewModel.findByCode(event.code, inGame)?.let { targets ->
                            val currentEvents = targets.map { guessEvent(it) }
                            lastPressKey[event.code] = currentEvents
                            currentOnKeyEvent(currentEvents, true)
                        }
                    }
                }
                is GamepadViewModel.Event.Dpad -> {
                    if (!event.pressed) {
                        lastPressDpad[event.direction]?.let { lastEvents ->
                            currentOnKeyEvent(lastEvents, false)
                            lastPressDpad.remove(event.direction)
                        }
                    } else {
                        gamepadViewModel.findByDpad(event.direction, inGame)?.let { targets ->
                            val currentEvents = targets.map { guessEvent(it) }
                            lastPressDpad[event.direction] = currentEvents
                            currentOnKeyEvent(currentEvents, true)
                        }
                    }
                }
                else -> {}
            }
        },
        onDisposeCallback = {
            //松开所有正在按下的按键
            lastPressKey.forEach { (_, events) ->
                currentOnKeyEvent(events, false)
            }

            lastPressDpad.forEach { (_, events) ->
                currentOnKeyEvent(events, false)
            }

            lastPressKey.clear()
            lastPressDpad.clear()
        }
    )
}

/**
 * 统一实现的手柄摇杆控制视角/鼠标指针的事件监听器
 * @param isGrabbing 判断当前是否在游戏中，若在游戏内，则根据事件中的摇杆类型判定以哪个摇杆的偏移量操作视角
 *                   若在游戏外，则所有摇杆都支持返回偏移量（操控鼠标指针）
 */
@Composable
fun GamepadStickCameraListener(
    gamepadViewModel: GamepadViewModel,
    isGrabbing: Boolean,
    onOffsetEvent: (Offset) -> Unit
) {
    val currentIsGrabbing by rememberUpdatedState(isGrabbing)
    val joystickControlMode by rememberUpdatedState(AllSettings.joystickControlMode.state)
    val onOffsetEvent1 by rememberUpdatedState(onOffsetEvent)

    GamepadEventListener(
        gamepadViewModel = gamepadViewModel,
        listener = { event ->
            if (event is GamepadViewModel.Event.StickOffset) {
                if (currentIsGrabbing) {
                    val cameraStick = when (joystickControlMode) {
                        JoystickMode.RightMovement -> JoystickType.Left
                        JoystickMode.LeftMovement -> JoystickType.Right
                    }
                    if (event.joystickType == cameraStick) {
                        onOffsetEvent1(event.offset)
                    }
                } else {
                    onOffsetEvent1(event.offset)
                }
            }
        }
    )
}

/**
 * 统一实现的手柄摇杆控制玩家移动的事件监听器
 * @param isGrabbing 判断当前是否在游戏中，若在游戏内，则根据事件中的摇杆类型判定以哪个摇杆的方向，控制玩家移动
 * @param onKeyEvent 回调根据 options.txt 内保存的移动键键值转化的控制事件
 */
@Composable
fun GamepadStickMovementListener(
    gamepadViewModel: GamepadViewModel,
    isGrabbing: Boolean,
    onKeyEvent: (event: ClickEvent, pressed: Boolean) -> Unit
) {
    val currentIsGrabbing by rememberUpdatedState(isGrabbing)
    val joystickControlMode by rememberUpdatedState(AllSettings.joystickControlMode.state)
    val currentOnKeyEvent by rememberUpdatedState(onKeyEvent)

    //缓存已按下的事件，目的是当游戏进入菜单后，能够清除状态
    //避免回到游戏时出现一直移动的问题
    val allPressEvent = remember { mutableStateSetOf<String>() }

    fun sendKeyEvent(
        mcKey: String,
        defaultValue: String,
        pressed: Boolean
    ) {
        mapToControlEvent(mcKey, defaultValue)?.let { event ->
            if (pressed) {
                allPressEvent.add(event)
            } else {
                allPressEvent.remove(event)
            }

            currentOnKeyEvent(
                ClickEvent(type = ClickEvent.Type.Key, event),
                pressed
            )
        }
    }

    fun clearPressedEvent() {
        if (allPressEvent.isNotEmpty()) {
            allPressEvent.forEach { event ->
                currentOnKeyEvent(
                    ClickEvent(type = ClickEvent.Type.Key, event),
                    false
                )
            }
            allPressEvent.clear()
        }
    }

    GamepadEventListener(
        gamepadViewModel = gamepadViewModel,
        listener = { event ->
            if (event is GamepadViewModel.Event.StickDirection) {
                if (!currentIsGrabbing) {
                    clearPressedEvent()
                    return@GamepadEventListener
                }

                val movementStick = when (joystickControlMode) {
                    JoystickMode.RightMovement -> JoystickType.Right
                    JoystickMode.LeftMovement -> JoystickType.Left
                }

                if (event.joystickType != movementStick) return@GamepadEventListener

                allAction.forEach { (key, defaultValue) ->
                    sendKeyEvent(key, defaultValue, false)
                }

                directionMapping[event.direction]?.forEach { (key, defaultValue) ->
                    sendKeyEvent(key, defaultValue, true)
                }
            }
        },
        onDisposeCallback = {
            clearPressedEvent()
        }
    )
}

/**
 * 通过事件获取获取设备名称
 */
fun MotionEvent.getDeviceName(): String {
    return device.descriptor
}

/**
 * 通过事件获取获取设备名称
 */
fun KeyEvent.getDeviceName(): String {
    return device.descriptor
}

/**
 * 检查触摸事件是否来自手柄
 */
fun MotionEvent.isGamepadEvent(): Boolean {
    return isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            isFromSource(InputDevice.SOURCE_DPAD) ||
            isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)
}

fun MotionEvent.isJoystickMoving(): Boolean {
    return isFromSource(InputDevice.SOURCE_JOYSTICK) && action == MotionEvent.ACTION_MOVE
}

fun KeyEvent.isGamepadKeyEvent(): Boolean {
    val isGamepad = isFromSource(InputDevice.SOURCE_GAMEPAD) ||
            (device != null && device.supportsSource(InputDevice.SOURCE_GAMEPAD))
    
    val isDpad = isDpadKeyEvent()
    
    val result = isGamepad || isDpad
    
    // if (keyCode in 96..110) { // BUTTON_A to BUTTON_MODE range
    //     android.util.Log.d("GAMEPAD_CHECK", "isGamepadKeyEvent: keyCode=$keyCode, isGamepad=$isGamepad, isDpad=$isDpad, result=$result, source=${source}, device=${device?.name}")
    // }

    return result
}

private fun KeyEvent.isDpadKeyEvent(): Boolean {
    return (isFromSource(InputDevice.SOURCE_GAMEPAD) && isFromSource(InputDevice.SOURCE_DPAD)) &&
            device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
}

fun InputDevice?.isGamepadDevice(): Boolean {
    if (this == null) return false
    return this.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            this.supportsSource(InputDevice.SOURCE_JOYSTICK)
}

fun MotionEvent.findTriggeredAxis(): Int? {
    return supportedAxis.find { axis ->
        getAxisValue(axis) >= 0.85
    }
}

/**
 * Handle native gamepad motion input directly (without remapper)
 */
private fun handleNativeMotionInput(
    event: MotionEvent,
    gamepadViewModel: GamepadViewModel,
    deviceId: String
) {
    // android.util.Log.d("GAMEPAD_NATIVE", "handleNativeMotionInput called, deviceId=$deviceId")
    
    if (!event.isJoystickMoving()) {
        // android.util.Log.d("GAMEPAD_NATIVE", "Not a joystick moving event, returning")
        return
    }
    
    // android.util.Log.d("GAMEPAD_NATIVE", "Processing joystick axes")
    
    // Send all axes directly to native gamepad
    val axes = listOf(
        MotionEvent.AXIS_X to GamepadRemap.MotionX.code,
        MotionEvent.AXIS_Y to GamepadRemap.MotionY.code,
        MotionEvent.AXIS_Z to GamepadRemap.MotionZ.code,
        MotionEvent.AXIS_RZ to GamepadRemap.MotionRZ.code,
        MotionEvent.AXIS_LTRIGGER to GamepadRemap.MotionLeftTrigger.code,
        MotionEvent.AXIS_RTRIGGER to GamepadRemap.MotionRightTrigger.code,
        MotionEvent.AXIS_HAT_X to GamepadRemap.MotionHatX.code,
        MotionEvent.AXIS_HAT_Y to GamepadRemap.MotionHatY.code
    )
    
    axes.forEach { (androidAxis, standardAxis) ->
        val value = event.getAxisValue(androidAxis)
        if (kotlin.math.abs(value) > 0.01f) {
            // android.util.Log.d("GAMEPAD_NATIVE", "Calling updateAxisNative: axis=$standardAxis, value=$value")
        }
        gamepadViewModel.updateAxisNative(deviceId, standardAxis, value)
    }
}

/**
 * Handle native gamepad key input directly (without remapper)
 */
private fun handleNativeKeyInput(
    event: KeyEvent,
    gamepadViewModel: GamepadViewModel,
    deviceId: String
) {
    // android.util.Log.d("GAMEPAD_NATIVE", "handleNativeKeyInput called, keyCode=${event.keyCode}, deviceId=$deviceId")
    
    if (!event.isGamepadKeyEvent()) {
        // android.util.Log.d("GAMEPAD_NATIVE", "Not a gamepad key event, returning")
        return
    }
    if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
        // android.util.Log.d("GAMEPAD_NATIVE", "Unknown keycode, returning")
        return
    }
    if (event.repeatCount > 0) {
        // android.util.Log.d("GAMEPAD_NATIVE", "Repeat event, returning")
        return
    }
    
    // Map standard Android keycodes directly to button codes
    val buttonCode = when (event.keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> GamepadRemap.ButtonA.code
        KeyEvent.KEYCODE_BUTTON_B -> GamepadRemap.ButtonB.code
        KeyEvent.KEYCODE_BUTTON_X -> GamepadRemap.ButtonX.code
        KeyEvent.KEYCODE_BUTTON_Y -> GamepadRemap.ButtonY.code
        KeyEvent.KEYCODE_BUTTON_L1 -> GamepadRemap.ButtonL1.code
        KeyEvent.KEYCODE_BUTTON_R1 -> GamepadRemap.ButtonR1.code
        KeyEvent.KEYCODE_BUTTON_L2 -> GamepadRemap.MotionLeftTrigger.code
        KeyEvent.KEYCODE_BUTTON_R2 -> GamepadRemap.MotionRightTrigger.code
        KeyEvent.KEYCODE_BUTTON_THUMBL -> GamepadRemap.ButtonLeftStick.code
        KeyEvent.KEYCODE_BUTTON_THUMBR -> GamepadRemap.ButtonRightStick.code
        KeyEvent.KEYCODE_BUTTON_START -> GamepadRemap.ButtonStart.code
        KeyEvent.KEYCODE_BUTTON_SELECT -> GamepadRemap.ButtonSelect.code
        // D-pad buttons (may come as key events on some controllers) - passthrough
        KeyEvent.KEYCODE_DPAD_UP -> KeyEvent.KEYCODE_DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        else -> {
            // android.util.Log.d("GAMEPAD_NATIVE", "Unmapped keyCode=${event.keyCode}, returning")
            return
        }
    }
    
    // android.util.Log.d("GAMEPAD_NATIVE", "Calling updateButtonNative: buttonCode=$buttonCode, pressed=${event.action == KeyEvent.ACTION_DOWN}")
    
    val pressed = event.action == KeyEvent.ACTION_DOWN
    gamepadViewModel.updateButtonNative(deviceId, buttonCode, pressed)
}