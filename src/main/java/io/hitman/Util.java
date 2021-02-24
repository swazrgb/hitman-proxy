package io.hitman;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;

public class Util {

  public static String base64MD5(byte[] body) {
    MessageDigest md5;
    try {
      md5 = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    if (body != null) {
      md5.update(body);
    }

    byte[] digest = md5.digest();
    return Base64.getEncoder().encodeToString(digest);
  }

  @SneakyThrows
  public static String loadFile(String path) {
    return FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8);
  }
}
