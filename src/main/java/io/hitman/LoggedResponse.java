package io.hitman;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class LoggedResponse {
  private final Map<String, List<String>> headers;
  private final String responseBodyName;
  private final String responseBodyPatchedName;
}
