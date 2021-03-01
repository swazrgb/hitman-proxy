package io.hitman;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class LoggedRequest {
  private final String method;
  private final String url;
  private final Map<String, List<String>> headers;
  private final String requestBodyName;
  private final String requestBodyPatchedName;
}
