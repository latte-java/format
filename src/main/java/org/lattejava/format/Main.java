/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line entry point. Formats every {@code .java} file under a directory in place, using a code style read from an
 * IntelliJ IDEA code style XML file.
 *
 * <pre>{@code
 * Usage: latte-format [options] <directory> [code-style.xml]
 *
 *   <directory>             Directory to search recursively for .java files
 *   [code-style.xml]        IntelliJ code style file (optional positional form of --style)
 *
 * Options:
 *   -s, --style <file>      IntelliJ code style XML to apply. When omitted, the tool looks for
 *                           .idea/codeStyle/Project.xml (then .idea/codeStyles/Project.xml),
 *                           searching the target directory and its parents.
 *       --java-version <n>  Java language level to parse with (default 25).
 *       --rearrange         Reorder type members per the style's {@code <arrangement>} rules.
 *       --check             Do not modify files; exit 1 if any file is not already formatted.
 *   -h, --help              Show this help.
 * }</pre>
 *
 * @author The Latte Project
 */
public final class Main {
  private static final String DEFAULT_STYLE_PATH = ".idea/codeStyle/Project.xml";

  private static final String DEFAULT_STYLE_PATH_ALT = ".idea/codeStyles/Project.xml";

  private Main() {
  }

  public static void main(String[] args) {
    int exitCode = run(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  static int run(String[] args) {
    Options options;
    try {
      options = Options.parse(args);
    } catch (IllegalArgumentException e) {
      System.err.println("format: " + e.getMessage());
      System.err.println("Try 'format --help' for usage.");
      return 2;
    }

    if (options.help) {
      printUsage(System.out);
      return 0;
    }

    if (options.directory == null) {
      System.err.println("format: a directory is required");
      System.err.println("Try 'format --help' for usage.");
      return 2;
    }

    Path directory = options.directory;
    if (!Files.isDirectory(directory)) {
      System.err.println("format: not a directory: " + directory);
      return 2;
    }

    Path stylePath = options.stylePath != null ? options.stylePath : findDefaultStyle(directory);
    CodeStyle style;
    if (stylePath == null) {
      System.out.println("No IntelliJ code style file found; using IntelliJ's built-in defaults.");
      style = CodeStyleParser.defaultCodeStyle();
    } else if (!Files.isRegularFile(stylePath)) {
      System.err.println("format: code style file not found: " + stylePath);
      return 2;
    } else {
      try {
        style = CodeStyleParser.parse(stylePath);
        System.out.println("Using code style '" + style.schemeName() + "' from " + stylePath);
      } catch (IOException e) {
        System.err.println("format: could not read code style file " + stylePath + ": " + e.getMessage());
        return 2;
      }
    }

    List<Path> files;
    try {
      files = findJavaFiles(directory);
    } catch (IOException e) {
      System.err.println("format: could not scan " + directory + ": " + e.getMessage());
      return 2;
    }

    if (files.isEmpty()) {
      System.out.println("No .java files found under " + directory);
      return 0;
    }

    JavaFormatter formatter = new JavaFormatter(style, options.javaVersion, options.rearrange);
    int changed = 0;
    int failed = 0;
    for (Path file : files) {
      try {
        if (options.check) {
          if (!isFormatted(formatter, file)) {
            System.out.println("Would reformat: " + file);
            changed++;
          }
        } else if (formatter.formatFile(file)) {
          System.out.println("Formatted: " + file);
          changed++;
        }
      } catch (FormatException e) {
        System.err.println("Skipped (parse error): " + file + " - " + e.getMessage());
        failed++;
      } catch (IOException | UncheckedIOException e) {
        System.err.println("Skipped (I/O error): " + file + " - " + e.getMessage());
        failed++;
      }
    }

    printSummary(options, files.size(), changed, failed);

    if (failed > 0) {
      return 2;
    }

    if (options.check && changed > 0) {
      return 1;
    }

    return 0;
  }

  private static boolean isFormatted(JavaFormatter formatter, Path file) throws IOException {
    String original = Files.readString(file);
    return formatter.format(original).equals(original);
  }

  private static void printSummary(Options options, int total, int changed, int failed) {
    StringBuilder summary = new StringBuilder();
    summary.append(options.check ? "Checked " : "Processed ").append(total)
        .append(total == 1 ? " file: " : " files: ");
    summary.append(changed).append(options.check ? " need formatting" : " formatted");
    summary.append(", ").append(total - changed - failed).append(" already formatted");
    if (failed > 0) {
      summary.append(", ").append(failed).append(" skipped");
    }

    System.out.println(summary);
  }

  /**
   * Looks for the default code style file by name, starting at {@code start} and walking up to the filesystem root.
   */
  private static Path findDefaultStyle(Path start) {
    Path current = start.toAbsolutePath().normalize();
    while (current != null) {
      for (String candidate : new String[]{DEFAULT_STYLE_PATH, DEFAULT_STYLE_PATH_ALT}) {
        Path path = current.resolve(candidate);
        if (Files.isRegularFile(path)) {
          return path;
        }
      }

      current = current.getParent();
    }

    return null;
  }

  /**
   * Recursively collects {@code .java} files, skipping hidden directories (those whose name starts with {@code .},
   * such as {@code .git} and {@code .idea}).
   */
  private static List<Path> findJavaFiles(Path directory) throws IOException {
    List<Path> files = new ArrayList<>();
    Files.walkFileTree(directory, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
        if (!dir.equals(directory) && name.startsWith(".")) {
          return FileVisitResult.SKIP_SUBTREE;
        }

        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (file.getFileName().toString().endsWith(".java")) {
          files.add(file);
        }

        return FileVisitResult.CONTINUE;
      }
    });

    files.sort(Path::compareTo);
    return files;
  }

  private static void printUsage(java.io.PrintStream out) {
    out.println("""
        Usage: latte-format [options] <directory> [code-style.xml]

          <directory>             Directory to search recursively for .java files.
          [code-style.xml]        IntelliJ code style file (optional positional form of --style).

        Options:
          -s, --style <file>      IntelliJ code style XML to apply. When omitted, the tool looks
                                  for .idea/codeStyle/Project.xml (then .idea/codeStyles/Project.xml),
                                  searching the target directory and its parents.
              --java-version <n>  Java language level to parse with (default 25).
              --rearrange         Reorder type members per the style's <arrangement> rules
                                  (IntelliJ's "Rearrange entries"). Off by default.
              --check             Do not modify files; exit 1 if any file is not already formatted.
          -h, --help              Show this help.""");
  }

  /**
   * Parsed command-line options.
   */
  private static final class Options {
    Path directory;

    Path stylePath;

    int javaVersion = 25;

    boolean check;

    boolean rearrange;

    boolean help;

    static Options parse(String[] args) {
      Options options = new Options();
      List<String> positionals = new ArrayList<>();
      for (int i = 0; i < args.length; i++) {
        String arg = args[i];
        switch (arg) {
          case "-h", "--help" -> options.help = true;
          case "--check" -> options.check = true;
          case "--rearrange" -> options.rearrange = true;
          case "-s", "--style" -> options.stylePath = Path.of(requireValue(args, ++i, arg));
          case "--java-version" -> options.javaVersion = parseVersion(requireValue(args, ++i, arg));
          default -> {
            if (arg.startsWith("-") && !arg.equals("-")) {
              throw new IllegalArgumentException("unknown option: " + arg);
            }

            positionals.add(arg);
          }
        }
      }

      if (positionals.size() > 2) {
        throw new IllegalArgumentException("too many arguments: " + String.join(" ", positionals));
      }

      if (!positionals.isEmpty()) {
        options.directory = Path.of(positionals.get(0));
      }

      if (positionals.size() == 2 && options.stylePath == null) {
        options.stylePath = Path.of(positionals.get(1));
      }

      return options;
    }

    private static String requireValue(String[] args, int index, String option) {
      if (index >= args.length) {
        throw new IllegalArgumentException("missing value for " + option);
      }

      return args[index];
    }

    private static int parseVersion(String value) {
      try {
        return Integer.parseInt(value.trim());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("invalid --java-version: " + value);
      }
    }
  }
}
