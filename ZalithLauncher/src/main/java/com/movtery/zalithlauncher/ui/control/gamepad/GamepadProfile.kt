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

import android.os.Parcelable
import com.tencent.mmkv.MMKV
import kotlinx.parcelize.Parcelize

/**
 * Controller profile that stores user settings for both keyboard emulation and native modes
 * Profiles persist across mode switches and are saved per-controller
 */
@Parcelize
data class GamepadProfile(
    /** Unique profile identifier (typically device descriptor) */
    val deviceId: String,
    
    /** Profile name set by user (defaults to device name) */
    val profileName: String,
    
    /** Keyboard emulation mode settings */
    val emulationSettings: EmulationSettings = EmulationSettings(),
    
    /** Native mode settings */
    val nativeSettings: NativeSettings = NativeSettings(),
    
    /** Last used control mode for this profile */
    val lastUsedMode: GamepadControlMode = GamepadControlMode.KEYBOARD_EMULATION,
    
    /** Profile creation timestamp */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Last modified timestamp */
    val modifiedAt: Long = System.currentTimeMillis()
) : Parcelable {
    
    @Parcelize
    data class EmulationSettings(
        /** Dead zone scale percentage (50-200) */
        val deadZoneScale: Int = 100,
        
        /** Cursor sensitivity percentage (25-300) */
        val cursorSensitivity: Int = 100,
        
        /** Camera sensitivity percentage (25-300) */
        val cameraSensitivity: Int = 100,
        
        /** Joystick control mode (Left/Right movement) */
        val joystickMode: JoystickMode = JoystickMode.LeftMovement,
        
        /** Custom button mappings (stored separately in keyMappingMMKV) */
        val hasCustomMappings: Boolean = false
    ) : Parcelable
    
    @Parcelize
    data class NativeSettings(
        /** Native mode sensitivity multiplier (0.1 - 2.0) */
        val sensitivityMultiplier: Float = 1.0f,
        
        /** Invert X axis for camera */
        val invertX: Boolean = false,
        
        /** Invert Y axis for camera */
        val invertY: Boolean = false,
        
        /** Custom deadzone override (null = use system default) */
        val customDeadzone: Float? = null,
        
        /** Trigger threshold (0.0 - 1.0) */
        val triggerThreshold: Float = 0.1f
    ) : Parcelable
    
    companion object {
        private const val PROFILE_PREFIX = "gamepad_profile_"
        private const val ACTIVE_PROFILE_KEY = "gamepad_active_profile"
        
        /**
         * Get MMKV instance for profile storage
         */
        fun profileMMKV(): MMKV = MMKV.mmkvWithID("gamepad_profiles")
        
        /**
         * Save profile to storage
         */
        fun GamepadProfile.save() {
            val mmkv = profileMMKV()
            mmkv.encode(PROFILE_PREFIX + deviceId, this)
        }
        
        /**
         * Load profile from storage
         */
        fun load(deviceId: String): GamepadProfile? {
            val mmkv = profileMMKV()
            return mmkv.decodeParcelable(PROFILE_PREFIX + deviceId, GamepadProfile::class.java)
        }
        
        /**
         * Get or create profile for device
         */
        fun getOrCreate(deviceId: String, deviceName: String): GamepadProfile {
            return load(deviceId) ?: GamepadProfile(
                deviceId = deviceId,
                profileName = deviceName
            ).also { it.save() }
        }
        
        /**
         * Delete profile
         */
        fun delete(deviceId: String) {
            val mmkv = profileMMKV()
            mmkv.removeValueForKey(PROFILE_PREFIX + deviceId)
        }
        
        /**
         * Get all saved profiles
         */
        fun getAllProfiles(): List<GamepadProfile> {
            val mmkv = profileMMKV()
            return mmkv.allKeys()
                ?.filter { it.startsWith(PROFILE_PREFIX) }
                ?.mapNotNull { key ->
                    mmkv.decodeParcelable(key, GamepadProfile::class.java)
                }
                ?: emptyList()
        }
        
        /**
         * Set active profile (currently connected controller)
         */
        fun setActiveProfile(deviceId: String) {
            val mmkv = profileMMKV()
            mmkv.encode(ACTIVE_PROFILE_KEY, deviceId)
        }
        
        /**
         * Get active profile device ID
         */
        fun getActiveProfileId(): String? {
            val mmkv = profileMMKV()
            return mmkv.decodeString(ACTIVE_PROFILE_KEY)
        }
        
        /**
         * Clear active profile
         */
        fun clearActiveProfile() {
            val mmkv = profileMMKV()
            mmkv.removeValueForKey(ACTIVE_PROFILE_KEY)
        }
    }
}

/**
 * Extension function to update profile with new settings
 */
fun GamepadProfile.updateEmulationSettings(
    deadZoneScale: Int? = null,
    cursorSensitivity: Int? = null,
    cameraSensitivity: Int? = null,
    joystickMode: JoystickMode? = null
): GamepadProfile {
    return copy(
        emulationSettings = emulationSettings.copy(
            deadZoneScale = deadZoneScale ?: emulationSettings.deadZoneScale,
            cursorSensitivity = cursorSensitivity ?: emulationSettings.cursorSensitivity,
            cameraSensitivity = cameraSensitivity ?: emulationSettings.cameraSensitivity,
            joystickMode = joystickMode ?: emulationSettings.joystickMode
        ),
        modifiedAt = System.currentTimeMillis()
    )
}

/**
 * Extension function to update native mode settings
 */
fun GamepadProfile.updateNativeSettings(
    sensitivityMultiplier: Float? = null,
    invertX: Boolean? = null,
    invertY: Boolean? = null,
    customDeadzone: Float? = null,
    triggerThreshold: Float? = null
): GamepadProfile {
    return copy(
        nativeSettings = nativeSettings.copy(
            sensitivityMultiplier = sensitivityMultiplier ?: nativeSettings.sensitivityMultiplier,
            invertX = invertX ?: nativeSettings.invertX,
            invertY = invertY ?: nativeSettings.invertY,
            customDeadzone = customDeadzone ?: nativeSettings.customDeadzone,
            triggerThreshold = triggerThreshold ?: nativeSettings.triggerThreshold
        ),
        modifiedAt = System.currentTimeMillis()
    )
}

/**
 * Extension function to update control mode
 */
fun GamepadProfile.updateControlMode(mode: GamepadControlMode): GamepadProfile {
    return copy(
        lastUsedMode = mode,
        modifiedAt = System.currentTimeMillis()
    )
}

