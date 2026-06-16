/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.printer.configuration.Indentation;
import com.github.javaparser.printer.configuration.Indentation.IndentType;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

/**
 * Formats Java source using the rules described by a {@link CodeStyle} that was read from an IntelliJ IDEA code style
 * file. Source is parsed into an AST with JavaParser and re-rendered by a printer configured from the style, so the
 * output reflects the configured indentation, spacing, blank lines, import ordering and so on rather than the input's
 * original layout.
 * <p>
 * A single instance is reusable for many files but is not thread-safe.
 *
 * @author The Latte Project
 */
public final class JavaFormatter {
  private final CodeStyle style;

  private final JavaParser parser;

  private final PrinterConfiguration printerConfiguration;

  private final MemberArranger arranger;

  /**
   * Creates a formatter targeting Java 25 source, without member rearrangement.
   *
   * @param style the code style to apply
   */
  public JavaFormatter(CodeStyle style) {
    this(style, 25, false);
  }

  /**
   * Creates a formatter for a specific Java language level, without member rearrangement.
   *
   * @param style       the code style to apply
   * @param javaVersion the Java feature version the source should be parsed against (for example {@code 21} or
   *                    {@code 25})
   */
  public JavaFormatter(CodeStyle style, int javaVersion) {
    this(style, javaVersion, false);
  }

  /**
   * Creates a formatter.
   *
   * @param style       the code style to apply
   * @param javaVersion the Java feature version the source should be parsed against (for example {@code 21} or
   *                    {@code 25})
   * @param rearrange   {@code true} to reorder type members according to the style's {@code <arrangement>} rules
   *                    (IntelliJ's "Rearrange entries"); when {@code false}, members keep their original order
   */
  public JavaFormatter(CodeStyle style, int javaVersion, boolean rearrange) {
    this.style = style;

    ParserConfiguration parserConfiguration = new ParserConfiguration().setLanguageLevel(languageLevelFor(javaVersion));
    this.parser = new JavaParser(parserConfiguration);
    this.printerConfiguration = buildPrinterConfiguration();
    this.arranger = rearrange && !style.arrangementRules().isEmpty()
        ? new MemberArranger(style.arrangementRules())
        : null;
  }

  /**
   * Formats a single compilation unit.
   *
   * @param source the Java source to format
   * @return the formatted source, terminated by exactly one line separator
   * @throws FormatException if the source cannot be parsed
   */
  public String format(String source) {
    ParseResult<CompilationUnit> result = parser.parse(source);
    if (!result.isSuccessful() || result.getResult().isEmpty()) {
      String problems = result.getProblems().stream()
          .map(problem -> problem.getVerboseMessage())
          .collect(Collectors.joining("; "));
      throw new FormatException("Could not parse the Java source: " + problems);
    }

    CompilationUnit unit = result.getResult().get();

    // Detach end-of-line ("trailing") comments so they can be re-emitted inline rather than above the next line. This
    // mutates the freshly-parsed unit, so the map is rebuilt per file.
    Map<Node, Comment> trailingComments = TrailingComments.extract(unit);
    DefaultPrettyPrinter printer = new DefaultPrettyPrinter(
        configuration -> new IntelliJPrettyPrinterVisitor(configuration, style, arranger, trailingComments),
        printerConfiguration);

    String formatted = printer.print(unit);

    // Collapse any trailing whitespace and guarantee a single trailing newline, as IntelliJ does.
    return formatted.stripTrailing() + style.lineSeparator();
  }

  /**
   * Reads, formats, and rewrites a file in place, but only when formatting actually changes its contents.
   *
   * @param file the {@code .java} file to format
   * @return {@code true} if the file was changed, {@code false} if it was already formatted
   * @throws IOException     if the file cannot be read or written
   * @throws FormatException if the source cannot be parsed
   */
  public boolean formatFile(Path file) throws IOException {
    String original = Files.readString(file, StandardCharsets.UTF_8);
    String formatted = format(original);
    if (formatted.equals(original)) {
      return false;
    }

    Files.writeString(file, formatted, StandardCharsets.UTF_8);
    return true;
  }

  /**
   * @return the style this formatter applies
   */
  public CodeStyle style() {
    return style;
  }

  private PrinterConfiguration buildPrinterConfiguration() {
    DefaultPrinterConfiguration configuration = new DefaultPrinterConfiguration();

    // Indentation: one tab per level when using tabs (the tab's display width is a separate setting), otherwise
    // INDENT_SIZE spaces per level.
    Indentation indentation = style.useTabCharacter()
        ? new Indentation(IndentType.TABS, 1)
        : new Indentation(IndentType.SPACES, style.indentSize());
    configuration.addOption(new DefaultConfigurationOption(ConfigOption.INDENTATION, indentation));

    // Line separator.
    configuration.addOption(
        new DefaultConfigurationOption(ConfigOption.END_OF_LINE_CHARACTER, style.lineSeparator()));

    // Spaces around operators (on by default in a fresh configuration).
    if (!style.spaceAroundOperators()) {
      configuration.removeOption(new DefaultConfigurationOption(ConfigOption.SPACE_AROUND_OPERATORS));
    }

    // Indent case labels from the switch (on by default in a fresh configuration).
    if (!style.indentCaseFromSwitch()) {
      configuration.removeOption(new DefaultConfigurationOption(ConfigOption.INDENT_CASE_IN_SWITCH));
    }

    // NOTE: IntelliJ's ALIGN_MULTILINE_PARAMETERS / ALIGN_MULTILINE_CHAINED_METHODS only take effect once a construct
    // is wrapped because it exceeds the right margin. JavaParser does not perform right-margin wrapping, and its
    // COLUMN_ALIGN_PARAMETERS / COLUMN_ALIGN_FIRST_METHOD_CHAIN options instead force *every* multi-argument call or
    // chain onto separate lines unconditionally, which does not match IntelliJ. They are therefore intentionally left
    // disabled; see CodeStyle#alignMultilineParameters for the parsed-but-not-applied values.

    // Order imports according to the IntelliJ import layout table.
    configuration.addOption(new DefaultConfigurationOption(ConfigOption.SORT_IMPORTS_STRATEGY,
        new IntelliJImportStrategy(style.importLayout())));

    return configuration;
  }

  private static LanguageLevel languageLevelFor(int javaVersion) {
    return switch (javaVersion) {
      case 8 -> LanguageLevel.JAVA_8;
      case 11 -> LanguageLevel.JAVA_11;
      case 17 -> LanguageLevel.JAVA_17;
      case 21 -> LanguageLevel.JAVA_21;
      case 24 -> LanguageLevel.JAVA_24;
      case 26 -> LanguageLevel.JAVA_26;
      default -> LanguageLevel.JAVA_25;
    };
  }
}
