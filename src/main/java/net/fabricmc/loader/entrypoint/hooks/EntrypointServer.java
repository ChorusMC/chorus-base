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

package net.fabricmc.loader.entrypoint.hooks;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.FabricLoader;

import java.io.File;

public final class EntrypointServer {
	public static void start(File runDir, Object gameInstance) {
		if (runDir == null) {
			runDir = new File(".");
		}

		FabricLoader.INSTANCE.initialize(runDir, gameInstance);
		FabricLoader.INSTANCE.load();
		FabricLoader.INSTANCE.freeze();
		FabricLoader.INSTANCE.getInitializers(ModInitializer.class).forEach(ModInitializer::onInitialize);
		FabricLoader.INSTANCE.getInitializers(DedicatedServerModInitializer.class).forEach(DedicatedServerModInitializer::onInitializeServer);
	}
}
