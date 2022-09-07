package io.hitman;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hitman.model.RepositoryEntry;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class RepositoryLoader {

  @Getter(lazy = true)
  private final List<RepositoryEntry> repository = load();

  @Getter(lazy = true)
  private final Map<String, RepositoryEntry> repositoryEntryMap = loadMap();

  private final ResourceLoader resourceLoader;

  public RepositoryLoader(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @SneakyThrows
  private List<RepositoryEntry> load() {
    assert resourceLoader != null;

    Resource resource = resourceLoader.getResource("classpath:/repository-stripped.json");
    try (InputStream is = resource.getInputStream()) {
      List<RepositoryEntry> list = new ObjectMapper()
          .readValue(is, new TypeReference<List<RepositoryEntry>>() {
          });

      return Collections.unmodifiableList(list);
    }
  }

  private Map<String, RepositoryEntry> loadMap() {
    Map<String, RepositoryEntry> map = new HashMap<>();
    for (RepositoryEntry repositoryEntry : getRepository()) {
      map.put(repositoryEntry.getId(), repositoryEntry);
    }

    return Collections.unmodifiableMap(map);
  }
}
