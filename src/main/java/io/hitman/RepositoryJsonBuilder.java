package io.hitman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hitman.model.RepositoryEntry;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepositoryJsonBuilder {

  public static void main(String[] args) throws Exception {
    new RepositoryJsonBuilder().run();
  }

  private static final ObjectMapper objectMapper = new ObjectMapper();

  private Map<Long, TranslationEntry> translations;

  @Data
  private static class TranslationEntry {

    private final long hash;
    private final String string;
    private final Path file;
  }

  public Map<Long, TranslationEntry> loadTranslations() throws IOException {
    String folder = "D:\\tools\\rpkg\\out2\\LOCR";
    Map<Long, TranslationEntry> translations = new HashMap<>();

    List<Path> translationFiles = Files.walk(Paths.get(folder))
        .filter(p -> !Files.isDirectory(p))
        .filter(p -> p.toString().toLowerCase().endsWith(".json"))
        .sorted(new NaturalOrderComparator<>())
        .collect(Collectors.toList());

    for (Path translationFile : translationFiles) {
      log.info("Parsing translation file: {}", translationFile);
      ArrayNode languages = (ArrayNode) objectMapper.readTree(translationFile.toFile());
      for (JsonNode languageNode : languages) {
        Iterator<JsonNode> it = languageNode.iterator();
        String language = it.next().path("Language").asText();
        if (!"en".equalsIgnoreCase(language)) {
          continue;
        }

        while (it.hasNext()) {
          JsonNode entry = it.next();
          TranslationEntry translationEntry = new TranslationEntry(
              entry.get("StringHash").asLong(),
              entry.get("String").asText(),
              translationFile
          );

          translations.put(translationEntry.getHash(), translationEntry);
        }
      }

    }

    return translations;
  }

  private long crc32(String value) {
    CRC32 crc32 = new CRC32();
    crc32.update(value.toUpperCase().getBytes(StandardCharsets.UTF_8));
    return crc32.getValue();
  }

  private void translate(ObjectNode entry, String key) {
    TranslationEntry translationEntry = translations.get(crc32(entry.get(key).asText()));
    if (translationEntry != null) {
      entry.put(key + "_en", translationEntry.getString());
//    } else {
//      log.warn("Failed to translate {}: {}", key, entry.get(key).asText());
    }
  }

  private void run() throws Exception {
    String repoPath = "D:\\tools\\rpkg\\out2\\chunk0patch2\\REPO\\00204D1AFD76AB13.REPO";
    translations = loadTranslations();

    ArrayNode repo;
    try (FileInputStream fis = new FileInputStream(repoPath)) {
      repo = (ArrayNode) objectMapper.readTree(fis);
    }

    List<RepositoryEntry> entries = new ArrayList<>();
    Iterator<JsonNode> it = repo.iterator();
    while (it.hasNext()) {
      ObjectNode entry = (ObjectNode) it.next();
      String itemType = entry.path("ItemType").asText("");
      boolean isDetonator = "eDetonator".equalsIgnoreCase(itemType) && "IGT_Gadget_Detonator"
          .equalsIgnoreCase(entry.path("GripAnimType").asText());
      if (isDetonator) {
        log.warn("Skipping: {}", entry.path("CommonName").asText());
      }

      if (itemType.isEmpty() || isDetonator) {
        it.remove();
        continue;
      }

      translate(entry, "Name_LOC");
      translate(entry, "Description");
      entries.add(objectMapper.convertValue(entry, RepositoryEntry.class));
    }

    try (FileOutputStream fos = new FileOutputStream("src/main/resources/repository.json")) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(fos, repo);
    }

    try (FileOutputStream fos = new FileOutputStream(
        "src/main/resources/repository-stripped.json")) {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(fos, entries);
    }
  }
}
