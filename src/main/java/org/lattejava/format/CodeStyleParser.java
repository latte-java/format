/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.lattejava.format.CodeStyle.ArrangementOrder;
import org.lattejava.format.CodeStyle.ArrangementRule;
import org.lattejava.format.CodeStyle.ImportLayoutEntry;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads an IntelliJ IDEA {@code codeStyle} XML file into a {@link CodeStyle}. The reader is tolerant: missing blocks and
 * options simply fall back to IntelliJ's defaults, and any option IntelliJ wrote is preserved in the raw maps even when
 * the formatter does not act on it.
 *
 * @author The Latte Project
 */
public final class CodeStyleParser {
  private CodeStyleParser() {
  }

  /**
   * Parses the IntelliJ code style XML at the given path.
   *
   * @param file the {@code .xml} file to read
   * @return the parsed style
   * @throws IOException if the file cannot be read or is not well-formed XML
   */
  public static CodeStyle parse(Path file) throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      return parse(in);
    }
  }

  /**
   * Parses IntelliJ code style XML from the given stream.
   *
   * @param in the XML stream
   * @return the parsed style
   * @throws IOException if the stream cannot be read or is not well-formed XML
   */
  public static CodeStyle parse(InputStream in) throws IOException {
    Document document = readDocument(in);
    Element scheme = firstChildNamed(document.getDocumentElement(), "code_scheme");
    if (scheme == null) {
      // Not a recognizable code style file; fall back to defaults rather than failing the whole run.
      return defaultCodeStyle();
    }

    String schemeName = attr(scheme, "name", "Default");
    String lineSeparator = readLineSeparator(scheme);

    Map<String, String> javaSettings = new LinkedHashMap<>();
    Map<String, String> indentSettings = new LinkedHashMap<>();
    List<ArrangementRule> arrangementRules = List.of();
    Element java = findCodeStyleSettings(scheme, "JAVA");
    if (java != null) {
      readOptions(java, javaSettings);
      Element indentOptions = firstChildNamed(java, "indentOptions");
      if (indentOptions != null) {
        readOptions(indentOptions, indentSettings);
      }

      arrangementRules = readArrangement(java);
    }

    Map<String, String> javaCodeStyleSettings = new LinkedHashMap<>();
    List<ImportLayoutEntry> importLayout = null;
    Element javaCodeStyle = firstChildNamed(scheme, "JavaCodeStyleSettings");
    if (javaCodeStyle != null) {
      readOptions(javaCodeStyle, javaCodeStyleSettings);
      importLayout = readImportLayout(javaCodeStyle);
    }

    if (importLayout == null || importLayout.isEmpty()) {
      importLayout = defaultImportLayout();
    }

    return new CodeStyle(schemeName, javaSettings, indentSettings, javaCodeStyleSettings, lineSeparator, importLayout,
        arrangementRules);
  }

  /**
   * @return a {@link CodeStyle} that mirrors IntelliJ's built-in defaults, for use when no code style file exists.
   */
  public static CodeStyle defaultCodeStyle() {
    return new CodeStyle("Default", Map.of(), Map.of(), Map.of(), "\n", defaultImportLayout(), List.of());
  }

  private static String readLineSeparator(Element scheme) {
    for (Element option : childrenNamed(scheme, "option")) {
      if ("LINE_SEPARATOR".equals(option.getAttribute("name")) && option.hasAttribute("value")) {
        return option.getAttribute("value");
      }
    }

    return "\n";
  }

  /**
   * Reads the direct {@code <option name=".." value=".."/>} children of {@code parent} into {@code target}. Options that
   * carry nested values rather than a {@code value} attribute (such as {@code IMPORT_LAYOUT_TABLE}) are skipped here and
   * handled by dedicated readers.
   */
  private static void readOptions(Element parent, Map<String, String> target) {
    for (Element option : childrenNamed(parent, "option")) {
      String name = option.getAttribute("name");
      if (!name.isEmpty() && option.hasAttribute("value")) {
        target.put(name, option.getAttribute("value"));
      }
    }
  }

  private static Element findCodeStyleSettings(Element scheme, String language) {
    for (Element settings : childrenNamed(scheme, "codeStyleSettings")) {
      if (language.equals(settings.getAttribute("language"))) {
        return settings;
      }
    }

    return null;
  }

  private static List<ImportLayoutEntry> readImportLayout(Element javaCodeStyle) {
    for (Element option : childrenNamed(javaCodeStyle, "option")) {
      if (!"IMPORT_LAYOUT_TABLE".equals(option.getAttribute("name"))) {
        continue;
      }

      Element value = firstChildNamed(option, "value");
      if (value == null) {
        return null;
      }

      List<ImportLayoutEntry> entries = new ArrayList<>();
      for (Element entry : childElements(value)) {
        switch (entry.getTagName()) {
          case "emptyLine" -> entries.add(ImportLayoutEntry.separator());
          case "package" -> entries.add(ImportLayoutEntry.pkg(attr(entry, "name", ""),
              boolAttr(entry, "withSubpackages", true), boolAttr(entry, "static", false),
              boolAttr(entry, "module", false)));
          default -> {
            // Unknown layout row; ignore it.
          }
        }
      }

      return entries;
    }

    return null;
  }

  /**
   * Reads the {@code <arrangement>} member-ordering rules, flattening the sections into a single ordered list of rules.
   */
  private static List<ArrangementRule> readArrangement(Element java) {
    Element arrangement = firstChildNamed(java, "arrangement");
    if (arrangement == null) {
      return List.of();
    }

    Element rules = firstChildNamed(arrangement, "rules");
    if (rules == null) {
      return List.of();
    }

    List<ArrangementRule> result = new ArrayList<>();
    for (Element section : childrenNamed(rules, "section")) {
      for (Element rule : childrenNamed(section, "rule")) {
        Element match = firstChildNamed(rule, "match");
        if (match == null) {
          continue;
        }

        Set<String> conditions = new LinkedHashSet<>();
        collectConditions(match, conditions);
        if (conditions.isEmpty()) {
          continue;
        }

        ArrangementOrder order = ArrangementOrder.KEEP;
        Element orderElement = firstChildNamed(rule, "order");
        if (orderElement != null && orderElement.getTextContent() != null
            && orderElement.getTextContent().toUpperCase(Locale.ROOT).contains("NAME")) {
          order = ArrangementOrder.BY_NAME;
        }

        result.add(new ArrangementRule(conditions, order));
      }
    }

    return result;
  }

  /**
   * Collects positive condition tokens (such as {@code FIELD}, {@code PUBLIC}, {@code STATIC}) from a {@code <match>}
   * element, descending through the {@code <AND>} / {@code <OR>} grouping elements.
   */
  private static void collectConditions(Element element, Set<String> conditions) {
    for (Element child : childElements(element)) {
      String tag = child.getTagName();
      if (tag.equals("AND") || tag.equals("OR") || tag.equals("NOT")) {
        collectConditions(child, conditions);
      } else {
        String text = child.getTextContent();
        if (text == null || text.trim().equalsIgnoreCase("true")) {
          conditions.add(tag);
        }
      }
    }
  }

  /**
   * IntelliJ's default import layout: all non-static imports, a blank line, then all static imports.
   */
  private static List<ImportLayoutEntry> defaultImportLayout() {
    return List.of(ImportLayoutEntry.pkg("", true, false, false), ImportLayoutEntry.separator(),
        ImportLayoutEntry.pkg("", true, true, false));
  }

  // ------------------------------------------------------------------------------------------------------------------
  // DOM helpers
  // ------------------------------------------------------------------------------------------------------------------

  private static Document readDocument(InputStream in) throws IOException {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // Harden against XML external entity (XXE) attacks; these files are plain configuration.
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setExpandEntityReferences(false);
      factory.setNamespaceAware(false);

      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(in);
      document.getDocumentElement().normalize();
      return document;
    } catch (ParserConfigurationException | SAXException e) {
      throw new IOException("Could not parse the IntelliJ code style XML: " + e.getMessage(), e);
    }
  }

  private static List<Element> childElements(Element parent) {
    List<Element> result = new ArrayList<>();
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {
        result.add((Element) node);
      }
    }

    return result;
  }

  private static List<Element> childrenNamed(Element parent, String tagName) {
    List<Element> result = new ArrayList<>();
    for (Element child : childElements(parent)) {
      if (child.getTagName().equals(tagName)) {
        result.add(child);
      }
    }

    return result;
  }

  private static Element firstChildNamed(Element parent, String tagName) {
    for (Element child : childElements(parent)) {
      if (child.getTagName().equals(tagName)) {
        return child;
      }
    }

    return null;
  }

  private static String attr(Element element, String name, String defaultValue) {
    return element.hasAttribute(name) ? element.getAttribute(name) : defaultValue;
  }

  private static boolean boolAttr(Element element, String name, boolean defaultValue) {
    return element.hasAttribute(name) ? Boolean.parseBoolean(element.getAttribute(name)) : defaultValue;
  }
}
