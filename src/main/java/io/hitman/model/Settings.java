package io.hitman.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class Settings {

  private long configVersion = 1;
  private Set<String> knownCoinIds = new HashSet<>();
  private List<Loadout> loadouts = new ArrayList<>();
  private String activeLoadout;
}
