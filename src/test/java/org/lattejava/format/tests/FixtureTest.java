/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format.tests;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.lattejava.format.CodeStyle;
import org.lattejava.format.CodeStyleParser;
import org.lattejava.format.JavaFormatter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Golden-file tests. Each directory under {@code src/test/resources/fixtures} is one case holding an
 * {@code input.java} and the {@code expected.java} it should format to. A fixture may add a {@code style.xml} (otherwise
 * the shared {@code default-style.xml} is used) and an {@code options.properties} (keys {@code rearrange} and
 * {@code javaVersion}).
 * <p>
 * Each fixture is also checked for idempotency: re-formatting {@code expected.java} must reproduce it unchanged.
 *
 * @author The Latte Project
 */
public class FixtureTest {
  @DataProvider(name = "fixtures")
  public Object[][] fixtures() throws Exception {
    Path root = fixturesRoot();
    List<Object[]> cases = new ArrayList<>();
    try (Stream<Path> entries = Files.list(root)) {
      List<Path> directories = entries.filter(Files::isDirectory).sorted().toList();
      for (Path directory : directories) {
        if (Files.isRegularFile(directory.resolve("input.java"))
            && Files.isRegularFile(directory.resolve("expected.java"))) {
          cases.add(new Object[]{directory.getFileName().toString(), directory});
        }
      }
    }

    if (cases.isEmpty()) {
      throw new IllegalStateException("No fixtures found under " + root);
    }

    return cases.toArray(new Object[0][]);
  }

  @Test(dataProvider = "fixtures")
  public void formatsInputToExpected(String name, Path directory) throws Exception {
    String input = Files.readString(directory.resolve("input.java"));
    String expected = Files.readString(directory.resolve("expected.java"));
    JavaFormatter formatter = formatterFor(directory);

    assertEquals(formatter.format(input), expected, "Fixture '" + name + "': input did not format to expected");
  }

  @Test(dataProvider = "fixtures")
  public void formattingIsIdempotent(String name, Path directory) throws Exception {
    String expected = Files.readString(directory.resolve("expected.java"));
    JavaFormatter formatter = formatterFor(directory);

    assertEquals(formatter.format(expected), expected, "Fixture '" + name + "': expected output is not stable");
  }

  private static JavaFormatter formatterFor(Path directory) throws Exception {
    Path stylePath = directory.resolve("style.xml");
    if (!Files.isRegularFile(stylePath)) {
      stylePath = directory.getParent().resolve("default-style.xml");
    }

    CodeStyle style = CodeStyleParser.parse(stylePath);

    Properties options = new Properties();
    Path optionsPath = directory.resolve("options.properties");
    if (Files.isRegularFile(optionsPath)) {
      try (var in = Files.newInputStream(optionsPath)) {
        options.load(in);
      }
    }

    boolean rearrange = Boolean.parseBoolean(options.getProperty("rearrange", "false"));
    int javaVersion = Integer.parseInt(options.getProperty("javaVersion", "25"));
    return new JavaFormatter(style, javaVersion, rearrange);
  }

  private static Path fixturesRoot() throws Exception {
    URL url = FixtureTest.class.getResource("/fixtures");
    if (url != null && "file".equals(url.getProtocol())) {
      Path path = Path.of(url.toURI());
      if (Files.isDirectory(path)) {
        return path;
      }
    }

    // Fall back to the source tree relative to the working directory.
    Path source = Path.of(System.getProperty("user.dir"), "src", "test", "resources", "fixtures");
    if (Files.isDirectory(source)) {
      return source;
    }

    throw new IllegalStateException("Could not locate the fixtures directory (looked at resource /fixtures and "
        + source + ")");
  }
}
