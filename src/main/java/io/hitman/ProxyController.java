package io.hitman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hitman.model.LoggedRequest;
import io.hitman.model.LoggedResponse;
import io.hitman.model.SelectedItem;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Controller
@Slf4j
public class ProxyController {

  private static final DateTimeFormatter FILENAME_DTF = DateTimeFormatter
      .ofPattern("yyyy-MM-dd-HH-mm-ss-SSS");
  private static final String VCAP_SUFFIX = "vcap.me";

  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final ConfigStore configStore;
  //  private final Map<String, RepositoryEntry> repository;
//  private final List<RepositoryEntry> repositoryEntries;
  private final Path logDir;

  private final String port;
  private final String portSuffix;

  private final AtomicInteger atomicIdx = new AtomicInteger(0);

  public ProxyController(ResourceLoader resourceLoader, ObjectMapper objectMapper,
      ConfigStore configStore, @Value("${server.port}") String port) {
    this.objectMapper = objectMapper;
    this.configStore = configStore;

    this.logDir = Paths.get("logs", "session-" + now());
    this.port = port;
    this.portSuffix = ":" + port;

    HttpComponentsClientHttpRequestFactory clientHttpRequestFactory = new HttpComponentsClientHttpRequestFactory(
        HttpClientBuilder.create().build());
    restTemplate = new RestTemplate(clientHttpRequestFactory);
    restTemplate.setErrorHandler(new ResponseErrorHandler() {
      @Override
      public boolean hasError(ClientHttpResponse response) throws IOException {
        return false;
      }

      @Override
      public void handleError(ClientHttpResponse response) throws IOException {

      }
    });
  }

  @RequestMapping("/**")
  public ResponseEntity<byte[]> proxy(
      HttpServletRequest request,
      @RequestBody(required = false) byte[] requestBody,
      @RequestHeader(name = "Content-Type", required = false) MediaType requestContentType,
      @RequestHeader(required = false) MultiValueMap<String, String> requestHeaders,
      @RequestParam(required = false) MultiValueMap<String, String> requestParams
  ) throws Exception {
    String host = request.getHeader("Host");
    if (host.endsWith(portSuffix)) {
      host = host.substring(0, host.length() - portSuffix.length());
    }

    if (!host.endsWith(VCAP_SUFFIX)) {
      return ResponseEntity.badRequest()
          .body(("Unknown host: " + host).getBytes(StandardCharsets.UTF_8));
    }

    // config.vcap.me -> config.
    // vcap.me -> vcap.me
    host = host.substring(0, host.length() - VCAP_SUFFIX.length());
    if (host.isEmpty()) {
      host = "config.hitman.io";
    } else {
      host = host.substring(0, host.length() - 1);
    }

    String path = request.getServletPath();
    String method = request.getMethod().toUpperCase();

    boolean isHead = "HEAD".equals(method);

    String proxyUrl = "https://" + host;
    if (path != null) {
      proxyUrl += path;
    }

    if (request.getQueryString() != null) {
      proxyUrl += "?" + request.getQueryString();
    }

    Path dir = this.logDir.resolve(now() + "-" + atomicIdx.getAndIncrement());
    Files.createDirectories(dir);

    String requestBodyName = null;
    String requestBodyPatchedName = null;

    if (requestBody != null) {
      requestBodyName = "request-body";
      requestBodyPatchedName = requestBodyName + "-patched";
      if (requestContentType != null) {
        requestBodyName += "." + requestContentType.getSubtype();
        requestBodyPatchedName += "." + requestContentType.getSubtype();
      }

      Files.write(dir.resolve(requestBodyName), requestBody);
    }

    byte[] origRequestBody = requestBody;
    String lpath = path == null ? null : path.toLowerCase();
    if (lpath != null && "POST".equals(method) && (
        lpath.startsWith(
            "/authentication/api/userchannel/EventsService/SaveAndSynchronizeEvents".toLowerCase())
            ||
            lpath.startsWith(
                "/authentication/api/userchannel/EventsService/SaveEvents".toLowerCase())
    )) {
      JsonNode json = objectMapper.readTree(requestBody);
      for (JsonNode value : json.path("values")) {
        if ("ExitInventory".equals(value.path("Name").asText())) {
          Iterator<JsonNode> itemIterator = value.path("Value").iterator();
          Set<String> knownCoinIds = configStore.getKnownCoinIds();
//          List<String> wantItemIds = wantItemIds();
          while (itemIterator.hasNext()) {
            JsonNode item = itemIterator.next();
            if (knownCoinIds.contains(item.path("InstanceId").asText())) {
              itemIterator.remove();
            }
//            if (wantItemIds.remove(item.path("RepositoryId").asText())) {
//              itemIterator.remove();
//            }
          }
        }
      }

      requestBody = objectMapper.writeValueAsBytes(json);
    }

    boolean didPatchRequest = !Arrays.equals(origRequestBody, requestBody);
    if (requestBody != null && didPatchRequest) {
      Files.write(dir.resolve(requestBodyPatchedName), requestBody);
    }

    Files.write(dir.resolve("request-info.json"),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new LoggedRequest(
            method,
            proxyUrl,
            requestHeaders,
            requestBodyName,
            didPatchRequest ? requestBodyPatchedName : null
        )));

    RequestEntity.BodyBuilder requestBuilder = RequestEntity
        .method(HttpMethod.valueOf(isHead ? "GET" : method), URI.create(proxyUrl));
    for (Entry<String, List<String>> headerEntries : requestHeaders.entrySet()) {
      if (headerEntries.getKey().equalsIgnoreCase("Host")) {
        continue;
      }

      requestBuilder
          .header(headerEntries.getKey(), headerEntries.getValue().toArray(new String[0]));
    }

    RequestEntity<?> requestEntity;
    if (requestBody == null) {
      requestEntity = requestBuilder.build();
    } else {
      requestEntity = requestBuilder.body(requestBody);
    }

    log.info("Processing request for {} -> {}", host, requestEntity);

    ResponseEntity<byte[]> responseEntity = restTemplate.exchange(requestEntity, byte[].class);

    byte[] responseBody = responseEntity.getBody();
    byte[] origResponseBody = responseBody;
    boolean isResponseJson = responseBody != null && MediaType.APPLICATION_JSON
        .isCompatibleWith(responseEntity.getHeaders().getContentType());

    String responseBodyName = null;
    String responseBodyPatchedName = null;
    if (responseBody != null) {
      responseBodyName = "response-body";
      responseBodyPatchedName = responseBodyName + "-patched";

      if (requestContentType != null) {
        responseBodyName += "." + requestContentType.getSubtype();
        responseBodyPatchedName += "." + requestContentType.getSubtype();
      }

      Files.write(dir.resolve(responseBodyName), responseBody);
    }

    if (isResponseJson) {
      String strBody = new String(responseBody, StandardCharsets.UTF_8);

      if ("config.hitman.io".equals(host)) {
//        strBody = strBody.replaceAll("(?i)https://([^/\"']+.hitman.io)", "http://$1.vcap.me:" + port);
        strBody = strBody.replaceAll("https://hm3-service.hitman.io", "http://hm3-service.hitman.io.vcap.me");
      }

      if ("/profiles/page/Stashpoint".equalsIgnoreCase(path)) {
        ObjectNode json = (ObjectNode) objectMapper.readTree(strBody);

        JsonNode items = json.path("data").path("LoadoutItemsData").path("Items");
        for (JsonNode item : items) {
          JsonNode itemProperties = item.path("Item").path("Unlockable").path("Properties");
          if (itemProperties.isMissingNode()) {
            continue;
          }

          // dda002e9-02b1-4208-82a5-cf059f3c79cf = coin
          if ("dda002e9-02b1-4208-82a5-cf059f3c79cf"
              .equals(itemProperties.path("RepositoryId").asText())) {
            String instanceId = item.get("Item").get("InstanceId").asText();
            if (instanceId != null && !instanceId.isEmpty()) {
              configStore.addKnownCoinId(instanceId);
              ArrayNode repositoryAssets = (ArrayNode) itemProperties.path("RepositoryAssets");
              repositoryAssets.removeAll();
              wantItemIds().forEach(repositoryAssets::add);
            }
          }
        }

        strBody = objectMapper.writeValueAsString(json);
      }

      // Patch the items in during planning
      if ("/profiles/page/Planning".equalsIgnoreCase(path)) {
        ObjectNode json = (ObjectNode) objectMapper.readTree(strBody);

        // Allow all kinds of starts
        for (JsonNode entrance : json.path("data").path("Entrances")) {
          JsonNode loadoutSettings = entrance.path("Properties").path("LoadoutSettings");
          if (loadoutSettings.path("GearSlotsEnabledCount").isNumber()) {
            ((ObjectNode) loadoutSettings).put("GearSlotsEnabledCount", 2);
            ((ObjectNode) loadoutSettings).put("GearSlotsAllowContainers", true);
            ((ObjectNode) loadoutSettings).put("ConcealedWeaponSlotEnabled", true);
          }
        }

        // Add the items instead of coin
        JsonNode loadoutData = json.path("data").path("LoadoutData");
//        if (!loadoutData.isMissingNode() && !loadoutData.isNull()) {
        for (JsonNode loadout : loadoutData) {
          if (loadout == null || !"gear".equals(loadout.path("SlotName").asText())) {
            continue;
          }

          JsonNode item = loadout
              .path("Recommended")
              .path("item");

          if (item.isMissingNode()) {
              continue;
            }

            JsonNode itemProperties = item
                .path("Unlockable")
                .path("Properties");

            if (itemProperties.isMissingNode()) {
              continue;
            }

            // dda002e9-02b1-4208-82a5-cf059f3c79cf = coin
            if ("dda002e9-02b1-4208-82a5-cf059f3c79cf"
                .equals(itemProperties.path("RepositoryId").asText())) {
              String instanceId = item.get("InstanceId").asText();
              if (instanceId != null && !instanceId.isEmpty()) {
                configStore.addKnownCoinId(instanceId);
                ArrayNode repositoryAssets = (ArrayNode) itemProperties.path("RepositoryAssets");
                repositoryAssets.removeAll();
                wantItemIds().forEach(repositoryAssets::add);
              }
            }
//          }
        }

        strBody = objectMapper.writeValueAsString(json);
      }

      responseBody = strBody.getBytes(StandardCharsets.UTF_8);
    }

    boolean didPatchResponse = !Arrays.equals(origResponseBody, responseBody);

    if (responseBody != null && didPatchResponse) {
        Files.write(dir.resolve(responseBodyPatchedName), responseBody);
    }

    Files.write(dir.resolve("response-info.json"),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new LoggedResponse(
            responseEntity.getStatusCodeValue(),
            responseEntity.getHeaders(),
            responseBodyName,
            didPatchResponse ? responseBodyPatchedName : null
        )));

    ResponseEntity.BodyBuilder resultBody = ResponseEntity.status(responseEntity.getStatusCode());

    HttpHeaders resultHeaders = new HttpHeaders();
    resultHeaders.addAll(responseEntity.getHeaders());

    resultHeaders.remove("Content-Length");

    if (resultHeaders.containsKey("Content-MD5")) {
      resultHeaders.set("Content-MD5", Util.base64MD5(responseBody));
    }

    resultBody.headers(resultHeaders);

    if (isHead) {
      return resultBody.build();
    }

    return resultBody.body(responseBody);
  }

  private List<String> wantItemIds() {
    List<String> result = new ArrayList<>();
    for (SelectedItem entry : configStore.getActiveLoadout().getEntries()) {
      if (entry.getAmount() <= 0) {
        continue;
      }

      for (int i = 0; i < entry.getAmount(); i++) {
        result.add(entry.getId());
      }
    }

    return result;

//    return wantItemNames().stream()
//        .map(name -> {
//          for (RepositoryEntry repositoryEntry : repositoryEntries) {
//            if (name.equalsIgnoreCase(repositoryEntry.getTitle()) ||
//                name.equalsIgnoreCase(repositoryEntry.getCommonName())) {
//              return repositoryEntry.getId();
//            }
//          }
//
//          return null;
//        })
//        .filter(Objects::nonNull)
//        .collect(Collectors.toList());
  }

//  private List<String> wantItemNames() {
//    List<String> names = new ArrayList<>();
//
//    // (Dartgun) Tracker
//    // (Dartgun) Cure I
//    // (Dartgun) Cure II
//    // (Device) Remote Cure Gas Large
//    // (Device) Remote Emetic Gas
//    // (Device) Remote Lethal Gas
//    // (Device) Remote Sedative Gas
////    add(names, "(Dartgun) Tracker", 1);
//    add(names, "(Tool) Coin Cure", 10);
////    add(names, "Electronics Crate", 1);
////    add(names, "MAGIC_WATCH", 2); // buggy & cheat engine can do this too
//    add(names, "(Device) Remote Emetic Gas", 25);
//    add(names, "(Device) Remote Lethal Gas", 100);
//    add(names, "(Device) Remote Sedative Gas", 25);
//
//    add(names, "(Dartgun) Sick", 1);
//    add(names, "(Dartgun) Kalmer 1 - Tranquilizer", 1);
//    add(names, "(Pistol) The Wall Piecer", 1);
//    add(names, "(Tool) Lock pick S3", 1);
//    add(names, "(Tool) Crowbar Professional", 1);
//    add(names, "(S3) Tool_Keycard Hacker Electrical MK III", 10);
//    add(names, "Lethal Poison Syringe", 100);
//    add(names, "Lethal Pills", 25);
////    add(names, "Sedative Syringe", 10);
//    add(names, "Sedative Pills", 10);
////    add(names, "Emetic Poison Syringe", 10);
//    add(names, "Emetic Pills", 10);
//    add(names, "Remote Explosive RubberDuck", 25);
//    add(names, "Remote Explosive RubberDuck S2", 25);
//    add(names, "(S3) ICA Device Semtex Remote Explosive MK III", 100);
////    add(names, "Remote Explosive RubberDuck STA", 25);
////    add(names, "SONY PREORDER WHITE REMOTE RUBBERDUCK EXPLOSIVE", 25);
//
//    return names;
//  }

//  private void add(List<String> names, String name, int amount) {
//    for (int i = 0; i < amount; i++) {
//      names.add(name);
//    }
//  }

  private String now() {
    return LocalDateTime.now().format(FILENAME_DTF);
  }
}
