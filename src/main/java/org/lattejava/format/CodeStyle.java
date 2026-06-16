/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An immutable representation of the IntelliJ IDEA "Java" code style, parsed from an IntelliJ {@code codeStyle} XML
 * file (for example {@code .idea/codeStyles/Project.xml}).
 * <p>
 * Every option that IntelliJ writes into the XML is retained verbatim in the raw option maps ({@link #javaOption} and
 * {@link #javaCodeStyleOption}) so callers can inspect settings that this formatter does not yet act on. The typed
 * accessors below expose the subset of options that the {@link JavaFormatter} currently applies, each defaulting to the
 * same value IntelliJ uses when the option is absent from the file.
 *
 * @author The Latte Project
 */
public final class CodeStyle {
  private final String schemeName;

  private final Map<String, String> javaSettings;

  private final Map<String, String> indentSettings;

  private final Map<String, String> javaCodeStyleSettings;

  private final String lineSeparator;

  private final List<ImportLayoutEntry> importLayout;

  private final List<ArrangementRule> arrangementRules;

  public CodeStyle(String schemeName, Map<String, String> javaSettings, Map<String, String> indentSettings,
                   Map<String, String> javaCodeStyleSettings, String lineSeparator,
                   List<ImportLayoutEntry> importLayout, List<ArrangementRule> arrangementRules) {
    this.schemeName = schemeName;
    this.javaSettings = Map.copyOf(javaSettings);
    this.indentSettings = Map.copyOf(indentSettings);
    this.javaCodeStyleSettings = Map.copyOf(javaCodeStyleSettings);
    this.lineSeparator = lineSeparator;
    this.importLayout = List.copyOf(importLayout);
    this.arrangementRules = List.copyOf(arrangementRules);
  }

  /**
   * @return the name of the {@code code_scheme} the settings were read from, or {@code "Default"} when unknown.
   */
  public String schemeName() {
    return schemeName;
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Indentation (from the JAVA <indentOptions> block)
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * @return the number of columns used for a single level of indentation (IntelliJ {@code INDENT_SIZE}, default 4).
   */
  public int indentSize() {
    return intOption(indentSettings, "INDENT_SIZE", 4);
  }

  /**
   * @return the indentation used for wrapped/continued lines (IntelliJ {@code CONTINUATION_INDENT_SIZE}, default 8).
   */
  public int continuationIndentSize() {
    return intOption(indentSettings, "CONTINUATION_INDENT_SIZE", 8);
  }

  /**
   * @return the visual width of a tab character (IntelliJ {@code TAB_SIZE}, default 4).
   */
  public int tabSize() {
    return intOption(indentSettings, "TAB_SIZE", 4);
  }

  /**
   * @return {@code true} when tabs should be used for indentation (IntelliJ {@code USE_TAB_CHARACTER}, default false).
   */
  public boolean useTabCharacter() {
    return boolOption(indentSettings, "USE_TAB_CHARACTER", false);
  }

  // ------------------------------------------------------------------------------------------------------------------
  // General layout
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * @return the line separator IntelliJ associates with the scheme; defaults to {@code "\n"} when unspecified.
   */
  public String lineSeparator() {
    return lineSeparator;
  }

  /**
   * @return the right margin / hard wrap column (IntelliJ {@code RIGHT_MARGIN}, default 120).
   */
  public int rightMargin() {
    return intOption(javaSettings, "RIGHT_MARGIN", 120);
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Blank lines
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * @return blank lines kept directly after a class/interface opening brace ({@code BLANK_LINES_AFTER_CLASS_HEADER}).
   */
  public int blankLinesAfterClassHeader() {
    return intOption(javaSettings, "BLANK_LINES_AFTER_CLASS_HEADER", 0);
  }

  /**
   * @return minimum blank lines around a method or constructor (IntelliJ {@code BLANK_LINES_AROUND_METHOD}, default 1).
   */
  public int blankLinesAroundMethod() {
    return intOption(javaSettings, "BLANK_LINES_AROUND_METHOD", 1);
  }

  /**
   * @return minimum blank lines around a field (IntelliJ {@code BLANK_LINES_AROUND_FIELD}, default 0).
   */
  public int blankLinesAroundField() {
    return intOption(javaSettings, "BLANK_LINES_AROUND_FIELD", 0);
  }

  /**
   * @return minimum blank lines around a nested type (IntelliJ {@code BLANK_LINES_AROUND_CLASS}, default 1).
   */
  public int blankLinesAroundClass() {
    return intOption(javaSettings, "BLANK_LINES_AROUND_CLASS", 1);
  }

  /**
   * @return the maximum number of author-written blank lines preserved inside method/initializer bodies (IntelliJ
   *     {@code KEEP_BLANK_LINES_IN_CODE}, default 2).
   */
  public int keepBlankLinesInCode() {
    return intOption(javaSettings, "KEEP_BLANK_LINES_IN_CODE", 2);
  }

  /**
   * @return the maximum number of author-written blank lines preserved between type members (IntelliJ
   *     {@code KEEP_BLANK_LINES_IN_DECLARATIONS}, default 1).
   */
  public int keepBlankLinesInDeclarations() {
    return intOption(javaSettings, "KEEP_BLANK_LINES_IN_DECLARATIONS", 1);
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Spacing and alignment
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * IntelliJ exposes spacing around operators as several per-category options. JavaParser only offers a single global
   * toggle, so the assignment-operator category (the one users most commonly change) is used as the representative.
   *
   * @return {@code true} when spaces should surround binary operators (default true).
   */
  public boolean spaceAroundOperators() {
    return boolOption(javaSettings, "SPACE_AROUND_ASSIGNMENT_OPERATORS", true);
  }

  /**
   * @return {@code true} when chained method calls should be aligned (IntelliJ
   *     {@code ALIGN_MULTILINE_CHAINED_METHODS}, default false).
   */
  public boolean alignMultilineChainedMethods() {
    return boolOption(javaSettings, "ALIGN_MULTILINE_CHAINED_METHODS", false);
  }

  /**
   * @return {@code true} when wrapped method parameters should be column-aligned (IntelliJ
   *     {@code ALIGN_MULTILINE_PARAMETERS}, default true).
   */
  public boolean alignMultilineParameters() {
    return boolOption(javaSettings, "ALIGN_MULTILINE_PARAMETERS", true);
  }

  /**
   * @return {@code true} when {@code case} labels should be indented from the {@code switch} (IntelliJ
   *     {@code INDENT_CASE_FROM_SWITCH}, default true).
   */
  public boolean indentCaseFromSwitch() {
    return boolOption(javaSettings, "INDENT_CASE_FROM_SWITCH", true);
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Imports
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * @return the ordered import layout table; never empty (a sensible IntelliJ-equivalent default is supplied when the
   *     file omits one).
   */
  public List<ImportLayoutEntry> importLayout() {
    return importLayout;
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Member arrangement
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * @return the ordered member-arrangement rules from the {@code <arrangement>} block (IntelliJ's "Rearrange entries"),
   *     or an empty list when the file defines none
   */
  public List<ArrangementRule> arrangementRules() {
    return arrangementRules;
  }

  // ------------------------------------------------------------------------------------------------------------------
  // Raw access
  // ------------------------------------------------------------------------------------------------------------------

  /**
   * @param name an option name from the {@code <codeStyleSettings language="JAVA">} block
   * @return the raw string value, or {@code null} when absent
   */
  public String javaOption(String name) {
    return javaSettings.get(name);
  }

  /**
   * @param name an option name from the {@code <JavaCodeStyleSettings>} block
   * @return the raw string value, or {@code null} when absent
   */
  public String javaCodeStyleOption(String name) {
    return javaCodeStyleSettings.get(name);
  }

  private static int intOption(Map<String, String> map, String name, int defaultValue) {
    String value = map.get(name);
    if (value == null) {
      return defaultValue;
    }

    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static boolean boolOption(Map<String, String> map, String name, boolean defaultValue) {
    String value = map.get(name);
    return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
  }

  /**
   * A single row of the IntelliJ import layout table: either a blank-line separator or a package matcher.
   *
   * @param emptyLine       {@code true} for an {@code <emptyLine/>} row; the package fields are then unused
   * @param packageName     the package prefix to match ({@code ""} is the catch-all), or {@code null} for a separator
   * @param withSubpackages whether sub-packages of {@link #packageName} also match
   * @param isStatic        whether this row matches static imports
   * @param isModule        whether this row matches module imports ({@code import module ...})
   */
  public record ImportLayoutEntry(boolean emptyLine, String packageName, boolean withSubpackages, boolean isStatic,
                                  boolean isModule) {
    public static ImportLayoutEntry separator() {
      return new ImportLayoutEntry(true, null, false, false, false);
    }

    public static ImportLayoutEntry pkg(String packageName, boolean withSubpackages, boolean isStatic,
                                        boolean isModule) {
      return new ImportLayoutEntry(false, packageName, withSubpackages, isStatic, isModule);
    }
  }

  /**
   * How members matched by an {@link ArrangementRule} are ordered within their group.
   */
  public enum ArrangementOrder {
    /** Keep the members in their original relative order. */
    KEEP,
    /** Sort the members alphabetically by name. */
    BY_NAME
  }

  /**
   * A single member-arrangement rule. A member matches the rule when it has all of the rule's condition tokens.
   *
   * @param conditions the IntelliJ tokens that must all be present (for example {@code FIELD}, {@code STATIC},
   *                   {@code PUBLIC}); see {@code MemberArranger} for the tokens a member is described with
   * @param order      how matching members are ordered within the group
   */
  public record ArrangementRule(Set<String> conditions, ArrangementOrder order) {
    public ArrangementRule {
      conditions = Set.copyOf(conditions);
    }
  }
}
