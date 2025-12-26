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

package com.movtery.zalithlauncher.viewmodel

import android.view.InputDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.movtery.zalithlauncher.ui.control.gamepad.DpadDirection
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadControlMode
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadMap
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadMapping
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadProfile
import com.movtery.zalithlauncher.ui.control.gamepad.GamepadRemap
import com.movtery.zalithlauncher.ui.control.gamepad.Joystick
import com.movtery.zalithlauncher.ui.control.gamepad.JoystickMode
import com.movtery.zalithlauncher.ui.control.gamepad.JoystickType
import com.movtery.zalithlauncher.ui.control.gamepad.keyMappingMMKV
import com.movtery.zalithlauncher.ui.control.joystick.JoystickDirection
import com.movtery.zalithlauncher.ui.control.gamepad.updateControlMode
import com.movtery.zalithlauncher.ui.control.gamepad.updateEmulationSettings
import com.movtery.zalithlauncher.ui.control.gamepad.updateNativeSettings
import com.movtery.zalithlauncher.utils.logging.Logger
import org.lwjgl.glfw.CallbackBridge
import kotlin.math.abs

private const val BUTTON_PRESS_THRESHOLD = 0.85f

class GamepadViewModel() : ViewModel() {
    private val listeners = mutableListOf<(Event) -> Unit>()

    /**
     * 手柄与键盘按键映射绑定
     */
    private val allKeyMappings = mutableMapOf<Int, TargetKeys>()
    private val allDpadMappings = mutableMapOf<DpadDirection, TargetKeys>()

    /** 左摇杆状态 */
    private val leftJoystick = Joystick(JoystickType.Left)
    /** 右摇杆状态 */
    private val rightJoystick = Joystick(JoystickType.Right)

    private val dpadStates = mutableMapOf<DpadDirection, Boolean>()
    private val buttonStates = mutableMapOf<Int, Boolean>()

    /**
     * 手柄活动状态控制
     */
    var gamepadEngaged by mutableStateOf(false)
        private set

    private var lastActivityTime = System.nanoTime()
    private var pollLevel = PollLevel.Close

    // Add mode tracking
    var controlMode by mutableStateOf(GamepadControlMode.KEYBOARD_EMULATION)
        private set
    
    // Map Android controller to GLFW joystick ID
    private val deviceIdToJoystickId = mutableMapOf<String, Int>()
    private val deviceIdToName = mutableMapOf<String, String>()  // Store real device names
    private var nextJoystickId = 0
    
    // Controller profile management
    private val deviceProfiles = mutableMapOf<String, GamepadProfile>()
    var currentProfile by mutableStateOf<GamepadProfile?>(null)
        private set

    init {
        reloadAllMappings()
    }

    /**
     * 检查并更新手柄是否活动中
     * @return 当前轮询频率等级
     */
    fun checkGamepadActive(): PollLevel {
        val now = System.nanoTime()

        if (
            dpadStates.containsValue(true) ||
            buttonStates.containsValue(true) ||
            leftJoystick.isUsing() ||
            rightJoystick.isUsing()
        ) {
            lastActivityTime = now
        }

        pollLevel = if (now - lastActivityTime < 10_000_000_000L) PollLevel.High else PollLevel.Close
        gamepadEngaged = pollLevel != PollLevel.Close

        return pollLevel
    }

    /** 激活状态更新 */
    private fun onActive() {
        val wasInactive = !gamepadEngaged
        lastActivityTime = System.nanoTime()
        if (wasInactive) {
            gamepadEngaged = true
            pollLevel = PollLevel.High
        }
    }

    fun applyControlMode(mode: GamepadControlMode) {
        controlMode = mode
        
        // Save mode to current profile
        currentProfile?.let { profile ->
            val updatedProfile = profile.updateControlMode(mode)
            GamepadProfile.Companion.run { updatedProfile.save() }
            deviceProfiles[profile.deviceId] = updatedProfile
            currentProfile = updatedProfile
        }
        
        // Clear states when switching modes
        if (mode == GamepadControlMode.NATIVE_GAMEPAD) {
            // Register connected controllers
            registerConnectedControllers()
        }
    }
    
    /**
     * Load or create profile for a device
     */
    fun getOrCreateProfile(deviceId: String, deviceName: String): GamepadProfile {
        return deviceProfiles.getOrPut(deviceId) {
            GamepadProfile.getOrCreate(deviceId, deviceName)
        }
    }
    
    /**
     * Set the active controller profile
     */
    fun setActiveProfile(deviceId: String, deviceName: String) {
        val profile = getOrCreateProfile(deviceId, deviceName)
        currentProfile = profile
        GamepadProfile.setActiveProfile(deviceId)
        
        // Apply profile's control mode
        controlMode = profile.lastUsedMode
    }
    
    /**
     * Update current profile with emulation settings
     */
    fun updateCurrentProfileEmulation(
        deadZoneScale: Int? = null,
        cursorSensitivity: Int? = null,
        cameraSensitivity: Int? = null,
        joystickMode: JoystickMode? = null
    ) {
        currentProfile?.let { profile ->
            val updatedProfile = profile.updateEmulationSettings(
                deadZoneScale = deadZoneScale,
                cursorSensitivity = cursorSensitivity,
                cameraSensitivity = cameraSensitivity,
                joystickMode = joystickMode
            )
            GamepadProfile.Companion.run { updatedProfile.save() }
            deviceProfiles[profile.deviceId] = updatedProfile
            currentProfile = updatedProfile
        }
    }
    
    /**
     * Update current profile with native settings
     */
    fun updateCurrentProfileNative(
        sensitivityMultiplier: Float? = null,
        invertX: Boolean? = null,
        invertY: Boolean? = null,
        customDeadzone: Float? = null,
        triggerThreshold: Float? = null
    ) {
        currentProfile?.let { profile ->
            val updatedProfile = profile.updateNativeSettings(
                sensitivityMultiplier = sensitivityMultiplier,
                invertX = invertX,
                invertY = invertY,
                customDeadzone = customDeadzone,
                triggerThreshold = triggerThreshold
            )
            GamepadProfile.Companion.run { updatedProfile.save() }
            deviceProfiles[profile.deviceId] = updatedProfile
            currentProfile = updatedProfile
        }
    }
    
    /**
     * Get all saved profiles
     */
    fun getAllProfiles(): List<GamepadProfile> {
        return GamepadProfile.getAllProfiles()
    }
    
    /**
     * Delete a profile
     */
    fun deleteProfile(deviceId: String) {
        GamepadProfile.delete(deviceId)
        deviceProfiles.remove(deviceId)
        if (currentProfile?.deviceId == deviceId) {
            currentProfile = null
            GamepadProfile.clearActiveProfile()
        }
    }
    
    /**
     * Rename current profile
     */
    fun renameCurrentProfile(newName: String) {
        currentProfile?.let { profile ->
            val updatedProfile = profile.copy(
                profileName = newName,
                modifiedAt = System.currentTimeMillis()
            )
            GamepadProfile.Companion.run { updatedProfile.save() }
            deviceProfiles[profile.deviceId] = updatedProfile
            currentProfile = updatedProfile
        }
    }
    
    /**
     * Register a device name for a device ID
     * This should be called when we receive the first input from a new device (hot-plugged)
     * The function is idempotent - it only registers if not already registered
     */
    fun registerDeviceName(deviceId: String, deviceName: String) {
        if (!deviceIdToName.containsKey(deviceId)) {
            deviceIdToName[deviceId] = deviceName
            Logger.lDebug("[GAMEPAD_KT] Registered device name (hot-plug): deviceId=$deviceId, name=$deviceName")
            
            // Also register in native layer if not already tracked
            // This handles hot-plugged controllers that were connected AFTER enumeration
            if (!deviceIdToJoystickId.containsKey(deviceId)) {
                val jid = nextJoystickId++
                deviceIdToJoystickId[deviceId] = jid
                
                CallbackBridge.sendGamepadConnected(jid, deviceName, 15, 6)
                setActiveProfile(deviceId, deviceName)
                
                Logger.lDebug("[GAMEPAD_KT] Hot-plugged controller registered in native: deviceId=$deviceId, name=$deviceName, jid=$jid")
            }
        }
        // If already registered, do nothing (avoids redundant logging and map updates)
    }
    
    /**
     * Get the device name for a device ID, with fallback
     */
    private fun getDeviceName(deviceId: String): String {
        return deviceIdToName[deviceId] ?: "Unknown Controller"
    }
    
    fun updateButtonNative(deviceId: String, button: Int, pressed: Boolean) {
        val deviceName = getDeviceName(deviceId)
        val jid = deviceIdToJoystickId.getOrPut(deviceId) { 
            val id = nextJoystickId++
            // Notify Minecraft about new controller with real device name
            CallbackBridge.sendGamepadConnected(id, deviceName, 15, 6)
            
            // Load or create profile for this device
            setActiveProfile(deviceId, deviceName)
            
            Logger.lDebug("[GAMEPAD_KT] Registered new controller: deviceId=$deviceId, name=$deviceName, jid=$id")
            
            id
        }
        
        // Logger.lDebug("[GAMEPAD_KT] Button: jid=$jid, button=$button, pressed=$pressed")
        
        // Map Android button to GLFW button
        val glfwButton = mapAndroidButtonToGLFW(button)
        CallbackBridge.sendGamepadButton(jid, glfwButton, pressed)
    }
    
    fun updateAxisNative(deviceId: String, axis: Int, value: Float) {
        val deviceName = getDeviceName(deviceId)
        val jid = getJoystickId(deviceId)
        
        // Handle D-pad HAT axes specially - convert to button presses
        if (axis == GamepadRemap.MotionHatX.code) {
            // HAT_X: -1 = left, 0 = center, +1 = right
            updateDpadButton(deviceId, 14, value < -0.5f)  // DPAD_LEFT
            updateDpadButton(deviceId, 12, value > 0.5f)   // DPAD_RIGHT
            return  // Don't send as axis
        }
        
        if (axis == GamepadRemap.MotionHatY.code) {
            // HAT_Y: -1 = up, 0 = center, +1 = down
            updateDpadButton(deviceId, 11, value < -0.5f)  // DPAD_UP
            updateDpadButton(deviceId, 13, value > 0.5f)   // DPAD_DOWN
            return  // Don't send as axis
        }
        
        // Apply profile settings (sensitivity, inversion, etc.)
        var adjustedValue = value
        currentProfile?.nativeSettings?.let { settings ->
            adjustedValue *= settings.sensitivityMultiplier
            
            // Apply axis inversion based on axis type
            val isXAxis = axis == GamepadRemap.MotionX.code || axis == GamepadRemap.MotionZ.code
            val isYAxis = axis == GamepadRemap.MotionY.code || axis == GamepadRemap.MotionRZ.code
            
            if (isXAxis && settings.invertX) adjustedValue = -adjustedValue
            if (isYAxis && settings.invertY) adjustedValue = -adjustedValue
        }
        
        // Only log significant axis movements to avoid spam
        // if (abs(adjustedValue) > 0.01f) {
        //     Logger.lDebug("[GAMEPAD_KT] Axis: jid=$jid, axis=$axis, value=%.3f".format(adjustedValue))
        // }
        
        // Map Android axis to GLFW axis
        val glfwAxis = mapAndroidAxisToGLFW(axis)
        CallbackBridge.sendGamepadAxis(jid, glfwAxis, adjustedValue)
    }
    
    fun onControllerDisconnected(deviceId: String) {
        deviceIdToJoystickId.remove(deviceId)?.let { jid ->
            CallbackBridge.sendGamepadDisconnected(jid)
        }
    }
    
    private fun mapAndroidButtonToGLFW(androidButton: Int): Int {
        // GLFW gamepad button mapping (standard gamepad layout)
        // See: https://www.glfw.org/docs/3.3/group__gamepad__buttons.html
        return when (androidButton) {
            GamepadRemap.ButtonA.code -> 0  // GLFW_GAMEPAD_BUTTON_A
            GamepadRemap.ButtonB.code -> 1  // GLFW_GAMEPAD_BUTTON_B
            GamepadRemap.ButtonX.code -> 2  // GLFW_GAMEPAD_BUTTON_X
            GamepadRemap.ButtonY.code -> 3  // GLFW_GAMEPAD_BUTTON_Y
            GamepadRemap.ButtonL1.code -> 4   // GLFW_GAMEPAD_BUTTON_LEFT_BUMPER
            GamepadRemap.ButtonR1.code -> 5  // GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER
            GamepadRemap.ButtonSelect.code -> 6  // GLFW_GAMEPAD_BUTTON_BACK
            GamepadRemap.ButtonStart.code -> 7   // GLFW_GAMEPAD_BUTTON_START
            GamepadRemap.ButtonLeftStick.code -> 9   // GLFW_GAMEPAD_BUTTON_LEFT_THUMB (FIXED: was 8)
            GamepadRemap.ButtonRightStick.code -> 10  // GLFW_GAMEPAD_BUTTON_RIGHT_THUMB (FIXED: was 9)
            // D-pad buttons (can come as button events or HAT axes)
            android.view.KeyEvent.KEYCODE_DPAD_UP -> 11    // GLFW_GAMEPAD_BUTTON_DPAD_UP
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 12 // GLFW_GAMEPAD_BUTTON_DPAD_RIGHT
            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 13  // GLFW_GAMEPAD_BUTTON_DPAD_DOWN
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> 14  // GLFW_GAMEPAD_BUTTON_DPAD_LEFT
            else -> androidButton  // Fallback
        }
    }
    
    private fun mapAndroidAxisToGLFW(androidAxis: Int): Int {
        // GLFW gamepad axis mapping
        // See: https://www.glfw.org/docs/3.3/group__gamepad__axes.html
        return when (androidAxis) {
            GamepadRemap.MotionX.code -> 0     // GLFW_GAMEPAD_AXIS_LEFT_X
            GamepadRemap.MotionY.code -> 1     // GLFW_GAMEPAD_AXIS_LEFT_Y
            GamepadRemap.MotionZ.code -> 2     // GLFW_GAMEPAD_AXIS_RIGHT_X
            GamepadRemap.MotionRZ.code -> 3    // GLFW_GAMEPAD_AXIS_RIGHT_Y
            GamepadRemap.MotionLeftTrigger.code -> 4   // GLFW_GAMEPAD_AXIS_LEFT_TRIGGER
            GamepadRemap.MotionRightTrigger.code -> 5  // GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER
            else -> androidAxis
        }
    }
    
    // Track D-pad button states to convert HAT axes to button events
    private val dpadButtonStates = mutableMapOf<Int, Boolean>()
    
    /**
     * Update D-pad button state and send button event if state changed
     * Converts HAT axis values to discrete button presses
     */
    private fun updateDpadButton(deviceId: String, glfwButton: Int, pressed: Boolean) {
        val key = deviceId.hashCode() xor glfwButton
        val oldState = dpadButtonStates[key] ?: false
        if (oldState != pressed) {
            dpadButtonStates[key] = pressed
            // Logger.lDebug("[GAMEPAD_KT] D-pad button: button=$glfwButton, pressed=$pressed")
            CallbackBridge.sendGamepadButton(getJoystickId(deviceId), glfwButton, pressed)
        }
    }
    
    /**
     * Get or create joystick ID for a device
     */
    private fun getJoystickId(deviceId: String): Int {
        return deviceIdToJoystickId.getOrPut(deviceId) {
            val id = nextJoystickId++
            val deviceName = getDeviceName(deviceId)
            CallbackBridge.sendGamepadConnected(id, deviceName, 15, 6)
            setActiveProfile(deviceId, deviceName)
            Logger.lDebug("[GAMEPAD_KT] Registered new controller: deviceId=$deviceId, name=$deviceName, jid=$id")
            id
        }
    }
    
    /**
     * Proactively enumerate all connected gamepads and register them in native registry.
     * This ensures controllers are registered BEFORE Minecraft queries for joystick names.
     */
    fun enumerateAndRegisterControllers() {
        Logger.lDebug("[GAMEPAD_KT] Enumerating all connected controllers...")
        
        val deviceIds = InputDevice.getDeviceIds()
        var registeredCount = 0
        
        for (deviceId in deviceIds) {
            val device = InputDevice.getDevice(deviceId) ?: continue
            
            // Check if this is a gamepad/joystick
            val isGamepad = (device.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                           (device.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            
            if (!isGamepad) continue
            
            // IMPORTANT: Use device.descriptor (same as KeyEvent.getDeviceName() in GamepadEvents.kt)
            // This is a hash/descriptor string, NOT the integer device.id
            val deviceDescriptor = device.descriptor
            val deviceName = device.name ?: "Unknown Controller"
            
            Logger.lDebug("[GAMEPAD_KT] Found gamepad: descriptor=$deviceDescriptor, name=$deviceName, id=${device.id}")
            
            // Store device name
            deviceIdToName[deviceDescriptor] = deviceName
            
            // Register in native if not already registered
            if (!deviceIdToJoystickId.containsKey(deviceDescriptor)) {
                val jid = nextJoystickId++
                deviceIdToJoystickId[deviceDescriptor] = jid
                
                // Notify native layer to populate joystickRegistry
                CallbackBridge.sendGamepadConnected(jid, deviceName, 15, 6)
                
                // Load or create profile
                setActiveProfile(deviceDescriptor, deviceName)
                
                Logger.lDebug("[GAMEPAD_KT] Registered controller during enumeration: descriptor=$deviceDescriptor, name=$deviceName, jid=$jid")
                registeredCount++
            } else {
                // Already tracked - just re-register in native to ensure consistency
                val jid = deviceIdToJoystickId[deviceDescriptor]!!
                CallbackBridge.sendGamepadConnected(jid, deviceName, 15, 6)
                Logger.lDebug("[GAMEPAD_KT] Re-registered existing controller: descriptor=$deviceDescriptor, name=$deviceName, jid=$jid")
            }
        }
        
        Logger.lDebug("[GAMEPAD_KT] Controller enumeration complete. New registrations: $registeredCount, Total tracked: ${deviceIdToJoystickId.size}")
    }
    
    private fun registerConnectedControllers() {
        // Called when switching to native mode
        // Now delegates to the enumeration method
        enumerateAndRegisterControllers()
    }


    fun reloadAllMappings() {
        allKeyMappings.clear()
        allDpadMappings.clear()

        val mmkv = keyMappingMMKV()
        GamepadMap.entries.forEach { entry ->
            val mapping = mmkv.decodeParcelable(entry.identifier, GamepadMapping::class.java)
                ?: GamepadMapping(
                    key = entry.gamepad,
                    dpadDirection = entry.dpadDirection,
                    targetsInGame = entry.defaultKeysInGame,
                    targetsInMenu = entry.defaultKeysInMenu
                )
            addInMappingsMap(mapping)
        }
    }

    private fun addInMappingsMap(mapping: GamepadMapping) {
        val target = TargetKeys(mapping.targetsInGame, mapping.targetsInMenu)
        mapping.dpadDirection?.let { allDpadMappings[it] = target } ?: run {
            allKeyMappings[mapping.key] = target
        }
    }

    /**
     * 重置手柄与键盘按键映射绑定
     */
    fun resetMapping(gamepadMap: GamepadMap, inGame: Boolean) =
        applyMapping(gamepadMap, inGame, useDefault = true)

    /**
     * 为指定手柄映射设置目标键盘映射
     */
    fun saveMapping(gamepadMap: GamepadMap, targets: Set<String>, inGame: Boolean) =
        applyMapping(gamepadMap, inGame, customTargets = targets)

    /**
     * 保存或重置手柄与键盘按键映射绑定
     * @param gamepadMap 手柄映射对象
     * @param inGame 是否为游戏内映射（true 为游戏内，false 为菜单内）
     * @param customTargets 自定义目标键
     * @param useDefault 是否使用默认按键
     */
    private fun applyMapping(
        gamepadMap: GamepadMap,
        inGame: Boolean,
        customTargets: Set<String>? = null,
        useDefault: Boolean = false
    ) {
        val dpad = gamepadMap.dpadDirection
        val existing = if (dpad != null) allDpadMappings[dpad] else allKeyMappings[gamepadMap.gamepad]

        val (targetsInGame, targetsInMenu) = if (inGame) {
            val newTargets = customTargets ?: if (useDefault) gamepadMap.defaultKeysInGame else emptySet()
            newTargets to (existing?.inMenu ?: emptySet())
        } else {
            (existing?.inGame ?: emptySet()) to (customTargets ?: if (useDefault) gamepadMap.defaultKeysInMenu else emptySet())
        }

        GamepadMapping(
            key = gamepadMap.gamepad,
            dpadDirection = dpad,
            targetsInGame = targetsInGame,
            targetsInMenu = targetsInMenu
        ).also { it.save(gamepadMap.identifier) }
    }

    private fun GamepadMapping.save(identifier: String) {
        addInMappingsMap(this)
        keyMappingMMKV().encode(identifier, this)
    }

    /**
     * 根据手柄按键键值获取对应的键盘映射代码
     * @return 若未找到，则返回null
     */
    fun findByCode(key: Int, inGame: Boolean) =
        allKeyMappings[key]?.getKeys(inGame)

    /**
     * 根据手柄方向键获取对应的键盘映射代码
     * @return 若未找到，则返回null
     */
    fun findByDpad(dir: DpadDirection, inGame: Boolean) =
        allDpadMappings[dir]?.getKeys(inGame)

    /**
     * 根据手柄映射获取对应的键盘映射代码
     * @return 若未找到，则返回null
     */
    fun findByMap(map: GamepadMap, inGame: Boolean) =
        (map.dpadDirection?.let { allDpadMappings[it] } ?: allKeyMappings[map.gamepad])
            ?.getKeys(inGame)

    fun updateButton(code: Int, pressed: Boolean) {
        onActive()
        if (updateState(buttonStates, code, pressed)) {
            sendEvent(Event.Button(code, pressed))
        }
    }

    fun updateMotion(axisCode: Int, value: Float) {
        onActive()
        when (axisCode) {
            //更新摇杆状态
            GamepadRemap.MotionX.code -> leftJoystick.updateState(horizontal = value)
            GamepadRemap.MotionY.code -> leftJoystick.updateState(vertical = value)
            GamepadRemap.MotionZ.code -> rightJoystick.updateState(horizontal = value)
            GamepadRemap.MotionRZ.code -> rightJoystick.updateState(vertical = value)
        }

        when (axisCode) {
            //更新左右触发器状态
            GamepadRemap.MotionLeftTrigger.code,
            GamepadRemap.MotionRightTrigger.code
                -> updateButton(axisCode, value > BUTTON_PRESS_THRESHOLD)

            //更新方向键状态
            GamepadRemap.MotionHatX.code -> {
                updateDpad(DpadDirection.Left, value < -BUTTON_PRESS_THRESHOLD)
                updateDpad(DpadDirection.Right, value > BUTTON_PRESS_THRESHOLD)
            }
            GamepadRemap.MotionHatY.code -> {
                updateDpad(DpadDirection.Up, value < -BUTTON_PRESS_THRESHOLD)
                updateDpad(DpadDirection.Down, value > BUTTON_PRESS_THRESHOLD)
            }
        }
    }

    private fun updateDpad(direction: DpadDirection, pressed: Boolean) {
        if (updateState(dpadStates, direction, pressed)) {
            sendEvent(Event.Dpad(direction, pressed))
        }
    }

    private fun <K> updateState(map: MutableMap<K, Boolean>, key: K, new: Boolean): Boolean {
        val old = map[key]
        return if (old != new) {
            map[key] = new
            true
        } else false
    }

    /**
     * 轮询调用，持续发送当前拥有的摇杆状态
     */
    fun pollJoystick() {
        leftJoystick.onTick(::sendEvent)
        rightJoystick.onTick(::sendEvent)
    }

    private fun sendEvent(event: Event) {
        listeners.forEach { listener ->
            listener(event)
        }
    }

    /**
     * 添加一个事件监听者，在事件发送时立即回调
     */
    fun addListener(listener: (Event) -> Unit) {
        listeners.add(listener)
    }

    /**
     * 移除已添加的事件监听者
     */
    fun removeListener(listener: (Event) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * 便于记录目标键盘映射的数据类
     */
    private data class TargetKeys(
        val inGame: Set<String>,
        val inMenu: Set<String>
    ) {
        fun getKeys(isInGame: Boolean) = if (isInGame) inGame else inMenu
    }

    sealed interface Event {
        /**
         * 手柄按钮按下/松开事件
         * @param code 经过映射转化后的标准按钮键值
         */
        data class Button(val code: Int, val pressed: Boolean) : Event

        /**
         * 手柄摇杆偏移量事件
         * @param joystickType 摇杆类型（左、右）
         */
        data class StickOffset(val joystickType: JoystickType, val offset: Offset) : Event

        /**
         * 手柄摇杆方向变更事件
         * @param joystickType 摇杆类型（左、右）
         */
        data class StickDirection(val joystickType: JoystickType, val direction: JoystickDirection) : Event

        /**
         * 手柄方向键按下/松开事件
         * @param direction 方向
         */
        data class Dpad(val direction: DpadDirection, val pressed: Boolean) : Event
    }

    enum class PollLevel(val delayMs: Long) {
        /**
         * 高轮询等级：16ms延迟 ≈ 60fps
         */
        High(16L),

        /**
         * 不进行轮询
         */
        Close(10_000L)
    }
}