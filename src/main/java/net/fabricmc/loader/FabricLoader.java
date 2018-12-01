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

package net.fabricmc.loader;

import com.github.zafarkhaja.semver.Version;
import com.google.gson.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.api.Side;
import net.fabricmc.loader.util.json.SideDeserializer;
import net.fabricmc.loader.util.json.VersionDeserializer;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

public class FabricLoader {
	public static final FabricLoader INSTANCE = new FabricLoader();

	protected static Logger LOGGER = LogManager.getFormatterLogger("Fabric|Loader");
	private static final Gson GSON = new GsonBuilder()
		.registerTypeAdapter(Side.class, new SideDeserializer())
		.registerTypeAdapter(Version.class, new VersionDeserializer())
		.registerTypeAdapter(ModInfo.Links.class, new ModInfo.Links.Deserializer())
		.registerTypeAdapter(ModInfo.Dependency.class, new ModInfo.Dependency.Deserializer())
		.registerTypeAdapter(ModInfo.Person.class, new ModInfo.Person.Deserializer())
		.create();
	private static final JsonParser JSON_PARSER = new JsonParser();

	protected final Map<String, ModContainer> modMap = new HashMap<>();
	protected List<ModContainer> mods = new ArrayList<>();

	private final InstanceStorage instanceStorage = new InstanceStorage();

	private boolean gameInitialized = false;
	private boolean modsLoaded = false;

	private ISidedHandler sidedHandler;

	private File gameDir;
	private File configDir;

	public <T> Collection<T> getInitializers(Class<T> type) {
		return instanceStorage.getInitializers(type);
	}

	// INTERNAL: DO NOT USE
	public void initialize(File gameDir, ISidedHandler sidedHandler) {
		if (gameInitialized) {
			throw new RuntimeException("FabricLoader has already been game-initialized!");
		}

		this.gameDir = gameDir;
		this.sidedHandler = sidedHandler;
		gameInitialized = true;
	}

	public ISidedHandler getSidedHandler() {
		return sidedHandler;
	}

	public File getGameDirectory() {
		return gameDir;
	}

	public File getConfigDirectory() {
		if (configDir == null) {
			configDir = new File(gameDir, "config");
			if (!configDir.exists()) {
				configDir.mkdirs();
			}
		}
		return configDir;
	}

	public void load(File modsDir) {
		if (!checkModsDirectory(modsDir)) {
			return;
		}

		File[] dirFiles = modsDir.listFiles();
		if (dirFiles != null) {
			load(Arrays.asList(dirFiles));
		}
	}

	public void load(Collection<File> modFiles) {
		if (modsLoaded) {
			throw new RuntimeException("FabricLoader has already had mods loaded!");
		}

		List<Pair<ModInfo, File>> existingMods = new ArrayList<>();

		int classpathModsCount = 0;
		if (Boolean.parseBoolean(System.getProperty("fabric.development", "false"))) {
			List<Pair<ModInfo, File>> classpathMods = getClasspathMods();
			existingMods.addAll(classpathMods);
			classpathModsCount = classpathMods.size();
			LOGGER.debug("Found %d classpath mods", classpathModsCount);
		}

		for (File f : modFiles) {
			if (f.isDirectory()) {
				continue;
			}
			if (!f.getPath().endsWith(".jar")) {
				continue;
			}

			ModInfo[] fileMods = getJarMods(f);

			if (Launch.classLoader != null && fileMods.length != 0) {
				try {
					Launch.classLoader.addURL(f.toURI().toURL());
				} catch (MalformedURLException e) {
					LOGGER.error("Unable to load mod from %s", f.getName());
					e.printStackTrace();
					continue;
				}
			}

			for (ModInfo info : fileMods) {
				existingMods.add(Pair.of(info, f));
			}
		}

		LOGGER.debug("Found %d JAR mods", existingMods.size() - classpathModsCount);

		mods:
		for (Pair<ModInfo, File> pair : existingMods) {
			ModInfo mod = pair.getLeft();
			/* if (mod.isLazilyLoaded()) {
				innerMods:
				for (Pair<ModInfo, File> pair2 : existingMods) {
					ModInfo mod2 = pair2.getLeft();
					if (mod == mod2) {
						continue innerMods;
					}
					for (Map.Entry<String, ModInfo.Dependency> entry : mod2.getRequires().entrySet()) {
						String depId = entry.getKey();
						ModInfo.Dependency dep = entry.getValue();
						if (depId.equalsIgnoreCase(mod.getId()) && dep.satisfiedBy(mod)) {
							addMod(mod, pair.getRight(), loaderInitializesMods());
						}
					}
				}
				continue mods;
			} */
			addMod(mod, pair.getRight(), loaderInitializesMods());
		}

		String modText;
		switch (mods.size()) {
			case 0:
				modText = "Loading %d mods";
				break;
			case 1:
				modText = "Loading %d mod: %s";
				break;
			default:
				modText = "Loading %d mods: %s";
				break;
		}

		LOGGER.info(modText, mods.size(), String.join(", ", mods.stream()
			.map(ModContainer::getInfo)
			.map(ModInfo::getId)
			.collect(Collectors.toList())));

		modsLoaded = true;
		onModsPopulated();
	}

	protected void onModsPopulated() {
		checkDependencies();
		sortMods();
		if (loaderInitializesMods()) {
			initializeMods();
		}
	}

	public boolean isModLoaded(String id) {
		return modMap.containsKey(id);
	}

	public List<ModContainer> getMods() {
		return Collections.unmodifiableList(mods);
	}

	protected List<Pair<ModInfo, File>> getClasspathMods() {
		List<Pair<ModInfo, File>> mods = new ArrayList<>();

		String javaHome = System.getProperty("java.home");
		String modsDir = new File(getGameDirectory(), "mods").getAbsolutePath();

		URL[] urls = Launch.classLoader.getURLs();
		for (URL url : urls) {
			if (url.getPath().startsWith(javaHome) || url.getPath().startsWith(modsDir)) {
				continue;
			}

			LOGGER.debug("Attempting to find classpath mods from " + url.getPath());
			File f = new File(url.getFile());
			if (f.exists()) {
				if (f.isDirectory()) {
					File modJson = new File(f, "mod.json");
					if (modJson.exists()) {
						try {
							for (ModInfo info : getMods(new FileInputStream(modJson))) {
								mods.add(Pair.of(info, f));
							}
						} catch (FileNotFoundException e) {
							LOGGER.error("Unable to load mod from directory " + f.getPath());
							e.printStackTrace();
						}
					}
				} else if (f.getName().endsWith(".jar")) {
					for (ModInfo info : getJarMods(f)) {
						mods.add(Pair.of(info, f));
					}
				}
			}
		}
		return mods;
	}

	protected boolean loaderInitializesMods() {
		return true;
	}

	protected void addMod(ModInfo info, File originFile, boolean initialize) {
		if (modMap.containsKey(info.getId())) {
			throw new RuntimeException("Duplicate mod ID: " + info.getId() + "! (" + modMap.get(info.getId()).getOriginFile().getName() + ", " + originFile.getName() + ")");
		}

		Side currentSide = getSidedHandler().getSide();
		if ((currentSide == Side.CLIENT && !info.getSide().hasClient()) || (currentSide == Side.SERVER && !info.getSide().hasServer())) {
			return;
		}
		ModContainer container = new ModContainer(info, originFile, initialize);
		mods.add(container);
		modMap.put(info.getId(), container);
	}

	protected void checkDependencies() {
		LOGGER.debug("Validating mod dependencies");

		for (ModContainer mod : mods) {
			dependencies:
			for (Map.Entry<String, ModInfo.Dependency> entry : mod.getInfo().getRequires().entrySet()) {
				String depId = entry.getKey();
				ModInfo.Dependency dep = entry.getValue();
				for (ModContainer mod2 : mods) {
					if (mod == mod2) {
						continue;
					}
					if (depId.equalsIgnoreCase(mod2.getInfo().getId()) && dep.satisfiedBy(mod2.getInfo())) {
						continue dependencies;
					}
				}

				throw new DependencyException(String.format("Mod %s requires %s @ %s", mod.getInfo().getId(), depId, String.join(", ", dep.getVersionMatchers())));
			}

			conflicts:
			for (Map.Entry<String, ModInfo.Dependency> entry : mod.getInfo().getConflicts().entrySet()) {
				String depId = entry.getKey();
				ModInfo.Dependency dep = entry.getValue();
				for (ModContainer mod2 : mods) {
					if (mod == mod2) {
						continue;
					}
					if (!depId.equalsIgnoreCase(mod2.getInfo().getId()) || !dep.satisfiedBy(mod2.getInfo())) {
						continue conflicts;
					}
				}

				throw new DependencyException(String.format("Mod %s conflicts with %s @ %s", mod.getInfo().getId(), depId, String.join(", ", dep.getVersionMatchers())));
			}
		}
	}

	private void sortMods() {
		/* LOGGER.debug("Sorting mods");

		LinkedList<ModContainer> sorted = new LinkedList<>();
		for (ModContainer mod : mods) {
			if (sorted.isEmpty() || mod.getInfo().getRequires().size() == 0) {
				sorted.addFirst(mod);
			} else {
				boolean b = false;
				l1:
				for (int i = 0; i < sorted.size(); i++) {
					for (Map.Entry<String, ModInfo.Dependency> entry : sorted.get(i).getInfo().getRequires().entrySet()) {
						String depId = entry.getKey();
						ModInfo.Dependency dep = entry.getValue();

						if (depId.equalsIgnoreCase(mod.getInfo().getId()) && dep.satisfiedBy(mod.getInfo())) {
							sorted.add(i, mod);
							b = true;
							break l1;
						}
					}
				}

				if (!b) {
					sorted.addLast(mod);
				}
			}
		}

		mods = sorted; */
	}

	private void initializeMods() {
		for (ModContainer mod : mods) {
			for (String in : mod.getInfo().getInitializers()) {
				instanceStorage.instantiate(in, mod.getAdapter(), true);
			}
		}

		getInitializers(ModInitializer.class).forEach(ModInitializer::onInitialize);
	}

	protected static boolean checkModsDirectory(File modsDir) {
		if (!modsDir.exists()) {
			modsDir.mkdirs();
			return false;
		}
		return modsDir.isDirectory();
	}

	protected static ModInfo[] getJarMods(File f) {
		try {
			JarFile jar = new JarFile(f);
			ZipEntry entry = jar.getEntry("mod.json");
			if (entry != null) {
				try (InputStream in = jar.getInputStream(entry)) {
					return getMods(in);
				}
			}

		} catch (Exception e) {
			LOGGER.error("Unable to load mod from %s", f.getName());
			e.printStackTrace();
		}

		return new ModInfo[0];
	}

	protected static ModInfo[] getMods(InputStream in) {
		JsonElement el = JSON_PARSER.parse(new InputStreamReader(in));
		if (el.isJsonObject()) {
			return new ModInfo[] { GSON.fromJson(el, ModInfo.class) };
		} else if (el.isJsonArray()) {
			JsonArray array = el.getAsJsonArray();
			ModInfo[] mods = new ModInfo[array.size()];
			for (int i = 0; i < array.size(); i++) {
				mods[i] = GSON.fromJson(array.get(i), ModInfo.class);
			}
			return mods;
		}

		return new ModInfo[0];
	}

}
