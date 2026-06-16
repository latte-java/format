/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.lattejava.format.CodeStyle;
import org.lattejava.format.CodeStyle.ImportLayoutEntry;
import org.lattejava.format.CodeStyleParser;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link CodeStyleParser}.
 *
 * @author The Latte Project
 */
public class CodeStyleParserTest {
  private static CodeStyle parse(String xml) throws Exception {
    return CodeStyleParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  public void readsIndentationAndGeneralOptions() throws Exception {
    CodeStyle style = parse("""
        <component name="ProjectCodeStyleConfiguration">
          <code_scheme name="MyTeam" version="173">
            <option name="LINE_SEPARATOR" value="&#10;" />
            <codeStyleSettings language="JAVA">
              <option name="RIGHT_MARGIN" value="100" />
              <option name="BLANK_LINES_AROUND_FIELD" value="1" />
              <option name="ALIGN_MULTILINE_CHAINED_METHODS" value="true" />
              <indentOptions>
                <option name="INDENT_SIZE" value="2" />
                <option name="CONTINUATION_INDENT_SIZE" value="4" />
                <option name="TAB_SIZE" value="2" />
                <option name="USE_TAB_CHARACTER" value="false" />
              </indentOptions>
            </codeStyleSettings>
          </code_scheme>
        </component>
        """);

    assertEquals(style.schemeName(), "MyTeam");
    assertEquals(style.lineSeparator(), "\n");
    assertEquals(style.indentSize(), 2);
    assertEquals(style.continuationIndentSize(), 4);
    assertEquals(style.tabSize(), 2);
    assertFalse(style.useTabCharacter());
    assertEquals(style.rightMargin(), 100);
    assertEquals(style.blankLinesAroundField(), 1);
    assertTrue(style.alignMultilineChainedMethods());

    // A retained-but-unmapped raw option is still readable.
    assertEquals(style.javaOption("RIGHT_MARGIN"), "100");
  }

  @Test
  public void appliesIntelliJDefaultsForMissingOptions() throws Exception {
    CodeStyle style = parse("""
        <component name="ProjectCodeStyleConfiguration">
          <code_scheme name="Bare" />
        </component>
        """);

    assertEquals(style.indentSize(), 4);
    assertEquals(style.continuationIndentSize(), 8);
    assertEquals(style.tabSize(), 4);
    assertFalse(style.useTabCharacter());
    assertEquals(style.rightMargin(), 120);
    assertEquals(style.blankLinesAroundMethod(), 1);
    assertEquals(style.blankLinesAroundField(), 0);
    assertEquals(style.blankLinesAfterClassHeader(), 0);
    assertEquals(style.lineSeparator(), "\n");
  }

  @Test
  public void readsImportLayoutTable() throws Exception {
    CodeStyle style = parse("""
        <component name="ProjectCodeStyleConfiguration">
          <code_scheme name="Imports">
            <JavaCodeStyleSettings>
              <option name="IMPORT_LAYOUT_TABLE">
                <value>
                  <package name="java" withSubpackages="true" static="false" />
                  <package name="javax" withSubpackages="true" static="false" />
                  <emptyLine />
                  <package name="" withSubpackages="true" static="false" />
                  <emptyLine />
                  <package name="" withSubpackages="true" static="true" />
                </value>
              </option>
            </JavaCodeStyleSettings>
          </code_scheme>
        </component>
        """);

    List<ImportLayoutEntry> layout = style.importLayout();
    assertEquals(layout.size(), 6);
    assertEquals(layout.get(0).packageName(), "java");
    assertFalse(layout.get(0).isStatic());
    assertTrue(layout.get(0).withSubpackages());
    assertTrue(layout.get(2).emptyLine());
    assertEquals(layout.get(3).packageName(), "");
    assertTrue(layout.get(5).isStatic());
  }

  @Test
  public void suppliesDefaultImportLayoutWhenAbsent() throws Exception {
    CodeStyle style = parse("""
        <component name="ProjectCodeStyleConfiguration">
          <code_scheme name="NoImports" />
        </component>
        """);

    List<ImportLayoutEntry> layout = style.importLayout();
    // Default: non-static catch-all, blank line, static catch-all.
    assertEquals(layout.size(), 3);
    assertFalse(layout.get(0).isStatic());
    assertTrue(layout.get(1).emptyLine());
    assertTrue(layout.get(2).isStatic());
  }

  @Test
  public void readsArrangementRules() throws Exception {
    CodeStyle style = parse("""
        <component name="ProjectCodeStyleConfiguration">
          <code_scheme name="Arranged">
            <codeStyleSettings language="JAVA">
              <arrangement>
                <rules>
                  <section>
                    <rule>
                      <match>
                        <AND>
                          <FIELD>true</FIELD>
                          <STATIC>true</STATIC>
                        </AND>
                      </match>
                      <order>BY_NAME</order>
                    </rule>
                  </section>
                  <section>
                    <rule>
                      <match>
                        <CONSTRUCTOR>true</CONSTRUCTOR>
                      </match>
                    </rule>
                  </section>
                </rules>
              </arrangement>
            </codeStyleSettings>
          </code_scheme>
        </component>
        """);

    var rules = style.arrangementRules();
    assertEquals(rules.size(), 2);
    assertEquals(rules.get(0).conditions(), java.util.Set.of("FIELD", "STATIC"));
    assertEquals(rules.get(0).order(), CodeStyle.ArrangementOrder.BY_NAME);
    assertEquals(rules.get(1).conditions(), java.util.Set.of("CONSTRUCTOR"));
    assertEquals(rules.get(1).order(), CodeStyle.ArrangementOrder.KEEP);
  }

  @Test
  public void hasNoArrangementRulesWhenAbsent() throws Exception {
    CodeStyle style = parse("""
        <component name="ProjectCodeStyleConfiguration">
          <code_scheme name="Plain" />
        </component>
        """);
    assertTrue(style.arrangementRules().isEmpty());
  }

  @Test
  public void unrecognizedFileFallsBackToDefaults() throws Exception {
    CodeStyle style = parse("<root><not-a-scheme/></root>");
    assertEquals(style.schemeName(), "Default");
    assertEquals(style.indentSize(), 4);
  }
}
