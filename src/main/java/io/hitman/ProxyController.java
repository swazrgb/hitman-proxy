package io.hitman;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.HttpClientBuilder;
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
  private static final String VCAP_SUFFIX = ".vcap.me";

  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final Map<String, RepositoryEntry> repository;
  private final List<RepositoryEntry> repositoryEntries;
  private final Path logDir;

  private final AtomicInteger atomicIdx = new AtomicInteger(0);

  public ProxyController(ResourceLoader resourceLoader, ObjectMapper objectMapper)
      throws IOException {
    this.objectMapper = objectMapper;

    try (InputStream is = resourceLoader.getResource("classpath:/repository.json")
        .getInputStream()) {
      Map<String, RepositoryEntry> repository = new ObjectMapper()
          .readValue(is, new TypeReference<Map<String, RepositoryEntry>>() {
          });

      this.repository = Collections.unmodifiableMap(repository);
      this.repositoryEntries = Collections.unmodifiableList(new ArrayList<>(repository.values()));
      this.logDir = Paths.get("logs", "session-" + now());
    }

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
    if (!host.endsWith(VCAP_SUFFIX)) {
      return ResponseEntity.badRequest()
          .body(("Unknown host: " + host).getBytes(StandardCharsets.UTF_8));
    }

    host = host.substring(0, host.length() - VCAP_SUFFIX.length());
    if (host.equals("config")) {
      host += ".hitman.io";
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
    if (requestBody != null) {
      requestBodyName = "request-body";
      if (requestContentType != null) {
        requestBodyName += "." + requestContentType.getSubtype();
      }

      if (requestContentType != null && requestContentType
          .isCompatibleWith(MediaType.APPLICATION_JSON)) {
        // Reformat json for easy viewing
        Files.write(dir.resolve(requestBodyName), objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsBytes(objectMapper.readTree(requestBody)));
      } else {
        Files.write(dir.resolve(requestBodyName), requestBody);
      }
    }

    Files.write(dir.resolve("request-info.json"),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new LoggedRequest(
            method,
            proxyUrl,
            requestHeaders,
            requestBodyName
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

      if (isResponseJson) {
        // Reformat json for easy viewing
        Files.write(dir.resolve(responseBodyName), objectMapper.writerWithDefaultPrettyPrinter()
            .writeValueAsBytes(objectMapper.readTree(responseBody)));
      } else {
        Files.write(dir.resolve(responseBodyName), responseBody);
      }
    }

    if (isResponseJson) {
      String strBody = new String(responseBody, StandardCharsets.UTF_8);

      if ("config.hitman.io".equals(host)) {
        strBody = strBody.replaceAll("(?i)https://([^/\"]+)", "http://$1.vcap.me");
      }

      if ("/profiles/page/Planning".equalsIgnoreCase(path)) {
        ObjectNode json = (ObjectNode) objectMapper.readTree(strBody);
        ArrayNode loadoutData = (ArrayNode) json.path("data").path("LoadoutData");
        if (loadoutData != null) {
          for (JsonNode loadout : loadoutData) {
            if (loadout == null || !"gear".equals(loadout.path("SlotName").asText())) {
              continue;
            }

            JsonNode item = loadout
                .path("Recommended")
                .path("item")
                .path("Unlockable")
                .path("Properties");

            if (item.isMissingNode()) {
              continue;
            }

            // dda002e9-02b1-4208-82a5-cf059f3c79cf = coin
            if ("dda002e9-02b1-4208-82a5-cf059f3c79cf".equals(item.path("RepositoryId").asText())) {
              ArrayNode repositoryAssets = (ArrayNode) item.path("RepositoryAssets");
              ArrayNode gameAssets = (ArrayNode) item.path("GameAssets");
              repositoryAssets.removeAll();

              wantItemNames().forEach(name -> {
                for (RepositoryEntry repositoryEntry : repositoryEntries) {
                  if (name.equalsIgnoreCase(repositoryEntry.getTitle()) ||
                      name.equalsIgnoreCase(repositoryEntry.getCommonName())) {
                    repositoryAssets.add(repositoryEntry.getId());
                    return;
                  }
                }
              });
            }
          }
        }

        strBody = objectMapper.writeValueAsString(json);
      }

      responseBody = strBody.getBytes(StandardCharsets.UTF_8);
    }

    boolean didPatchResponse = !Arrays.equals(origResponseBody, responseBody);

    if (responseBody != null && didPatchResponse) {
      if (isResponseJson) {
        // Reformat json for easy viewing
        Files.write(dir.resolve(responseBodyPatchedName),
            objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(objectMapper.readTree(responseBody)));
      } else {
        Files.write(dir.resolve(responseBodyPatchedName), responseBody);
      }
    }

    Files.write(dir.resolve("response-info.json"),
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(new LoggedResponse(
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

  private List<String> wantItemNames() {
    List<String> names = new ArrayList<>();

    add(names, "(Dartgun) Sick", 1);
    add(names, "(Dartgun) Kalmer 1 - Tranquilizer", 1);
    add(names, "(Tool) Lock pick S3", 1);
    add(names, "(Tool) Crowbar Professional", 1);
    add(names, "Lethal Poison Syringe", 100);
    add(names, "Sedative Syringe", 100);
    add(names, "Emetic Poison Syringe", 100);
    add(names, "Remote Explosive RubberDuck", 35);
    add(names, "Remote Explosive RubberDuck S2", 35);
    add(names, "Remote Explosive RubberDuck STA", 35);
    add(names, "SONY PREORDER WHITE REMOTE RUBBERDUCK EXPLOSIVE", 35);

    return names;
  }

  private void add(List<String> names, String name, int amount) {
    for (int i = 0; i < amount; i++) {
      names.add(name);
    }
  }

  private String now() {
    return LocalDateTime.now().format(FILENAME_DTF);
  }
}
