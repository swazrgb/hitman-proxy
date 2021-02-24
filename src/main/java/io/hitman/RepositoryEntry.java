package io.hitman;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryEntry {
  @JsonProperty("ID_")
  private String id;

  @JsonProperty("CommonName")
  private String commonName;

  @JsonProperty("Title")
  private String title;

  @JsonProperty("TokenID")
  private String tokenId;
}
