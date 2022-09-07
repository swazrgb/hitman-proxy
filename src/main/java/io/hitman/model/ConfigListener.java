package io.hitman.model;

public interface ConfigListener {

  default void onLoadoutCreated(String name) {
  }

  default void onLoadoutRemoved(String name) {

  }

  default void onActiveLoadoutChanged(String name) {
  }
}
