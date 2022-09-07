package io.hitman;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hitman.model.ConfigListener;
import io.hitman.model.Loadout;
import io.hitman.model.Settings;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.harawata.appdirs.AppDirs;
import net.harawata.appdirs.AppDirsFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ConfigStore {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private Path dataDir;
  private Path settingsPath;

  private Settings settings;

  private Set<ConfigListener> configListeners = new HashSet<>();

  public ConfigStore() throws IOException {
    AppDirs appDirs = AppDirsFactory.getInstance();
    dataDir = Paths.get(appDirs.getUserDataDir("hitman-proxy", null, null, true));
    Files.createDirectories(dataDir);

    settingsPath = dataDir.resolve("settings.json");

    try {
      settings = objectMapper.readValue(settingsPath.toFile(), Settings.class);
    } catch (FileNotFoundException ex) {
      settings = new Settings();
    }
  }

  public Loadout getActiveLoadout() {
    if (settings.getActiveLoadout() == null) {
      setActiveLoadout("Default");
    }

    return getLoadout(settings.getActiveLoadout());
  }

  public Loadout getLoadout(String name) {
    Loadout loadout = settings.getLoadouts().stream()
        .filter(l -> name.equalsIgnoreCase(l.getName()))
        .findFirst().orElse(null);

    if (loadout == null) {
      loadout = new Loadout(name);
      settings.getLoadouts().add(loadout);
      configListeners.forEach(l -> l.onLoadoutCreated(name));
    }

    return loadout;
  }

  public void removeLoadout(String name) {
    List<Loadout> loadouts = settings.getLoadouts();
    boolean changed = loadouts.removeIf(loadout -> loadout.getName().equals(name));

    if (changed) {
      if (Objects.equals(settings.getActiveLoadout(), name)) {
        // If we removed the active loadout...
        settings.setActiveLoadout(null);

        if (loadouts.size() > 0) {
          // ...switch back to the first one in the list
          setActiveLoadout(loadouts.get(0).getName());
        } else {
          // ...otherwise create a new default
          getActiveLoadout();
        }
      }

      configListeners.forEach(l -> l.onLoadoutRemoved(name));

      persist();
    }
  }

  public List<Loadout> getLoadouts() {
    return settings.getLoadouts();
  }

  public void setActiveLoadout(String name) {
    if (!Objects.equals(settings.getActiveLoadout(), name)) {
      settings.setActiveLoadout(name);
      // Getting it here ensures it actually exists...
      getActiveLoadout();
      configListeners.forEach(l -> l.onActiveLoadoutChanged(name));
      persist();
    }
  }

  public Set<String> getKnownCoinIds() {
    return Collections.unmodifiableSet(settings.getKnownCoinIds());
  }

  public void addKnownCoinId(String id) {
    boolean changed = settings.getKnownCoinIds().add(id);
    if (changed) {
      persist();
    }
  }

  public void persist() {
    try {
      objectMapper.writeValue(settingsPath.toFile(), settings);
    } catch (IOException e) {
      // TODO: UI?
      throw new RuntimeException(e);
    }
  }

  public void addConfigListener(ConfigListener configListener) {
    this.configListeners.add(configListener);
  }

  public void removeConfigListener(ConfigListener configListener) {
    this.configListeners.remove(configListener);
  }
}
