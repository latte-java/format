/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.printer.configuration.ImportOrderingStrategy;
import org.lattejava.format.CodeStyle.ImportLayoutEntry;

/**
 * Orders imports according to an IntelliJ {@code IMPORT_LAYOUT_TABLE}. Imports are bucketed into the layout's
 * {@code <emptyLine/>}-separated sections, ordered within each section by the layout rows they matched, and sorted
 * alphabetically inside each row (matching IntelliJ's behavior).
 * <p>
 * Each returned group is rendered by JavaParser followed by a single blank line, so returning one group per
 * blank-line-separated section reproduces the layout's spacing exactly. Imports that match no row are never dropped:
 * they are emitted, sorted, in a trailing group.
 *
 * @author The Latte Project
 */
final class IntelliJImportStrategy implements ImportOrderingStrategy {
  private final List<ImportLayoutEntry> layout;

  private boolean sortImportsAlphabetically = true;

  IntelliJImportStrategy(List<ImportLayoutEntry> layout) {
    this.layout = layout;
  }

  @Override
  public List<NodeList<ImportDeclaration>> sortImports(NodeList<ImportDeclaration> imports) {
    // Build the list of package-matching rows, remembering which blank-line-separated bucket each one falls in.
    List<PackageRow> rows = new ArrayList<>();
    int bucket = 0;
    int bucketCount = 1;
    for (ImportLayoutEntry entry : layout) {
      if (entry.emptyLine()) {
        bucket++;
        bucketCount = Math.max(bucketCount, bucket + 1);
      } else {
        rows.add(new PackageRow(entry, bucket, rows.size()));
      }
    }

    // Each row collects the imports that matched it; unmatched imports go to a separate trailing list.
    List<List<ImportDeclaration>> rowImports = new ArrayList<>();
    for (int i = 0; i < rows.size(); i++) {
      rowImports.add(new ArrayList<>());
    }
    List<ImportDeclaration> unmatched = new ArrayList<>();

    for (ImportDeclaration imp : imports) {
      int best = bestMatch(rows, imp);
      if (best < 0) {
        unmatched.add(imp);
      } else {
        rowImports.get(best).add(imp);
      }
    }

    // Assemble one group per bucket, preserving layout row order and sorting within each row.
    List<NodeList<ImportDeclaration>> groups = new ArrayList<>();
    for (int b = 0; b < bucketCount; b++) {
      NodeList<ImportDeclaration> group = new NodeList<>();
      for (int r = 0; r < rows.size(); r++) {
        if (rows.get(r).bucket == b) {
          List<ImportDeclaration> matched = rowImports.get(r);
          sort(matched);
          group.addAll(matched);
        }
      }

      groups.add(group);
    }

    if (!unmatched.isEmpty()) {
      sort(unmatched);
      groups.add(new NodeList<>(unmatched));
    }

    return groups;
  }

  private int bestMatch(List<PackageRow> rows, ImportDeclaration imp) {
    String name = imp.getNameAsString();
    boolean isStatic = imp.isStatic();

    int bestIndex = -1;
    int bestLength = -1;
    for (PackageRow row : rows) {
      ImportLayoutEntry entry = row.entry;
      if (entry.isStatic() != isStatic) {
        continue;
      }

      // Module-import rows are only matched by module imports, which JavaParser surfaces as regular imports; treat
      // them as non-matching so module rows never capture ordinary imports.
      if (entry.isModule()) {
        continue;
      }

      if (!matches(entry, name)) {
        continue;
      }

      int length = entry.packageName().length();
      if (length > bestLength) {
        bestLength = length;
        bestIndex = row.index;
      }
    }

    return bestIndex;
  }

  private static boolean matches(ImportLayoutEntry entry, String importName) {
    String prefix = entry.packageName();
    if (prefix.isEmpty()) {
      return true;
    }

    if (entry.withSubpackages()) {
      return importName.equals(prefix) || importName.startsWith(prefix + ".");
    }

    return packageOf(importName).equals(prefix);
  }

  private static String packageOf(String importName) {
    int lastDot = importName.lastIndexOf('.');
    return lastDot < 0 ? "" : importName.substring(0, lastDot);
  }

  private void sort(List<ImportDeclaration> imports) {
    if (sortImportsAlphabetically) {
      imports.sort((a, b) -> a.getNameAsString().compareTo(b.getNameAsString()));
    }
  }

  @Override
  public void setSortImportsAlphabetically(boolean sortAlphabetically) {
    this.sortImportsAlphabetically = sortAlphabetically;
  }

  @Override
  public boolean isSortImportsAlphabetically() {
    return sortImportsAlphabetically;
  }

  private record PackageRow(ImportLayoutEntry entry, int bucket, int index) {
  }
}
