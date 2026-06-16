/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.lattejava.format.CodeStyle;
import org.lattejava.format.CodeStyleParser;
import org.lattejava.format.FormatException;
import org.lattejava.format.JavaFormatter;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Unit tests for specific {@link JavaFormatter} settings and behaviors that do not map cleanly to a golden file under a
 * single default style. Holistic "format this file" cases live in {@link FixtureTest}.
 *
 * @author The Latte Project
 */
public class JavaFormatterTest {
  private static final String LF = "<option name=\"LINE_SEPARATOR\" value=\"&#10;\" />";

  private static final String INDENT_2 =
      "<indentOptions><option name=\"INDENT_SIZE\" value=\"2\" /><option name=\"TAB_SIZE\" value=\"2\" /></indentOptions>";

  // An arrangement that would reorder members, used to prove rearrangement stays off unless requested.
  private static final String ARRANGEMENT = "<arrangement><rules>"
      + "<section><rule><match><METHOD>true</METHOD></match><order>BY_NAME</order></rule></section>"
      + "</rules></arrangement>";

  private static String format(String source, String javaSettings) throws Exception {
    return formatScheme(source, LF + "<codeStyleSettings language=\"JAVA\">" + javaSettings + "</codeStyleSettings>",
        false);
  }

  private static String formatScheme(String source, String schemeInner, boolean rearrange) throws Exception {
    String xml = "<component name=\"ProjectCodeStyleConfiguration\"><code_scheme name=\"T\">" + schemeInner
        + "</code_scheme></component>";
    CodeStyle style = CodeStyleParser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    return new JavaFormatter(style, 25, rearrange).format(source);
  }

  @Test
  public void indentsWithFourSpacesByDefault() throws Exception {
    String result = format("class A{void m(){}}", "");
    assertEquals(result, "class A {\n    void m() {\n    }\n}\n");
  }

  @Test
  public void removesSpacesAroundOperatorsWhenDisabled() throws Exception {
    String settings = "<option name=\"SPACE_AROUND_ASSIGNMENT_OPERATORS\" value=\"false\" />" + INDENT_2;
    String result = format("class A{void m(){int a;a=1+2;}}", settings);
    assertTrue(result.contains("a=1+2;"), result);
  }

  @Test
  public void honorsConfiguredLineSeparator() throws Exception {
    String crlf = "<option name=\"LINE_SEPARATOR\" value=\"&#13;&#10;\" />"
        + "<codeStyleSettings language=\"JAVA\">" + INDENT_2 + "</codeStyleSettings>";
    String result = formatScheme("class A{int x;}", crlf, false);
    assertEquals(result, "class A {\r\n  int x;\r\n}\r\n");
  }

  @Test
  public void doesNotRearrangeMembersByDefault() throws Exception {
    String settings = LF + "<codeStyleSettings language=\"JAVA\">" + INDENT_2 + ARRANGEMENT + "</codeStyleSettings>";
    String result = formatScheme("class A{void zed(){}void abc(){}}", settings, false);
    // Without --rearrange the original order is preserved: zed before abc.
    assertTrue(result.indexOf("void zed") < result.indexOf("void abc"), result);
  }

  @Test
  public void doesNotDuplicateTrailingCommentSharedAcrossNodes() throws Exception {
    // For cramped single-line input JavaParser can attach one comment to two node slots; it must still print once.
    String result = format("class B{int x; // note\nvoid m(){}}", INDENT_2);
    int occurrences = result.split("// note", -1).length - 1;
    assertEquals(occurrences, 1, result);
  }

  @Test(expectedExceptions = FormatException.class)
  public void throwsOnUnparseableSource() throws Exception {
    format("class A { void m( { }", INDENT_2);
  }
}
