package io.hitman.model;

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

  @JsonProperty("Name_LOC")
  private String nameLoc;

  @JsonProperty("Name_LOC_en")
  private String nameLocEn;

  @JsonProperty("Description")
  private String description;

  @JsonProperty("Description_en")
  private String descriptionEn;

  @JsonProperty("Title")
  private String title;

  @JsonProperty("TokenID")
  private String tokenId;
}
