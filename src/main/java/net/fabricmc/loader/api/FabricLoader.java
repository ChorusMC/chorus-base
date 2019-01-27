/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.loader.api;

import net.fabricmc.api.EnvType;

/**
 * The public-facing FabricLoader instance.
 *
 * TODO: When we get a change to break this:
 * - remove getMods()List
 * - move ModInfo to net.fabricmc.api.loader
 * - add a way to get the ModContainer information, but do not expose
 *   ModContainer itself! Use another interface or simply separate methods
 */
public interface FabricLoader {
	@SuppressWarnings("deprecation")
	static FabricLoader getInstance() {
		if (net.fabricmc.loader.FabricLoader.INSTANCE == null) {
			throw new RuntimeException("Accessed FabricLoader too early!");
		}

		return net.fabricmc.loader.FabricLoader.INSTANCE;
	}

	/**
	 * Checks if a mod with a given ID is loaded.
	 * @param id The ID of the mod, as defined in fabric.mod.json.
	 * @return Whether or not the mod is present in this FabricLoader instance.
	 */
	boolean isModLoaded(String id);

	/**
	 * Get the current environment type.
	 * @return The current environment type.
	 */
	EnvType getEnvironmentType();

	/**
	 * Get the current game instance. Can represent a Minecraft client or
	 * server object. As such, the exact return is dependent on the
	 * current environment type.
	 * @return A client or server instance object.
	 */
	Object getGameInstance();
}
