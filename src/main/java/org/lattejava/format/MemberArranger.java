/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.nodeTypes.NodeWithModifiers;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import org.lattejava.format.CodeStyle.ArrangementOrder;
import org.lattejava.format.CodeStyle.ArrangementRule;

/**
 * Reorders the members of a type the way IntelliJ's "Rearrange entries" action does, using the {@code <arrangement>}
 * rules from the code style. Each member is matched against the rules in order, grouped by the first rule it matches,
 * and sorted within that group when the rule says {@code BY_NAME}; unmatched members keep their relative order at the
 * end.
 * <p>
 * Reordering members is safe in Java with one exception: the textual order of field initializers and initializer blocks
 * determines their execution order. To avoid changing behavior, a type is left untouched when one of its field
 * initializers references another field of the same type, or when it mixes initializer blocks with field initializers.
 *
 * @author The Latte Project
 */
final class MemberArranger {
  private final List<ArrangementRule> rules;

  MemberArranger(List<ArrangementRule> rules) {
    this.rules = rules;
  }

  /**
   * @return the members reordered per the arrangement rules, or a copy in the original order when there are no rules or
   *     reordering the type would not be safe
   */
  List<BodyDeclaration<?>> arrange(NodeList<BodyDeclaration<?>> members) {
    List<BodyDeclaration<?>> original = new ArrayList<>(members);
    if (rules.isEmpty() || original.size() < 2 || !isSafeToReorder(original)) {
      return original;
    }

    List<Indexed> indexed = new ArrayList<>(original.size());
    for (int i = 0; i < original.size(); i++) {
      indexed.add(new Indexed(original.get(i), i, matchingRuleIndex(attributesOf(original.get(i)))));
    }

    indexed.sort((a, b) -> {
      if (a.ruleIndex != b.ruleIndex) {
        return Integer.compare(a.ruleIndex, b.ruleIndex);
      }

      ArrangementOrder order = a.ruleIndex < rules.size() ? rules.get(a.ruleIndex).order() : ArrangementOrder.KEEP;
      if (order == ArrangementOrder.BY_NAME) {
        int byName = nameOf(a.member).compareToIgnoreCase(nameOf(b.member));
        if (byName != 0) {
          return byName;
        }
      }

      // Stable: equal keys keep their original relative order.
      return Integer.compare(a.originalIndex, b.originalIndex);
    });

    List<BodyDeclaration<?>> result = new ArrayList<>(original.size());
    for (Indexed entry : indexed) {
      result.add(entry.member);
    }

    return result;
  }

  private int matchingRuleIndex(Set<String> attributes) {
    for (int r = 0; r < rules.size(); r++) {
      if (attributes.containsAll(rules.get(r).conditions())) {
        return r;
      }
    }

    // Members that match no rule sort to the end, keeping their original relative order.
    return rules.size();
  }

  /**
   * @return the IntelliJ arrangement tokens that describe a member: its kind, visibility, and modifiers
   */
  private static Set<String> attributesOf(BodyDeclaration<?> member) {
    Set<String> attributes = new HashSet<>();
    attributes.add(kindOf(member));

    boolean hasVisibility = false;
    if (member instanceof NodeWithModifiers<?> withModifiers) {
      for (Modifier modifier : withModifiers.getModifiers()) {
        switch (modifier.getKeyword()) {
          case PUBLIC -> {
            attributes.add("PUBLIC");
            hasVisibility = true;
          }
          case PROTECTED -> {
            attributes.add("PROTECTED");
            hasVisibility = true;
          }
          case PRIVATE -> {
            attributes.add("PRIVATE");
            hasVisibility = true;
          }
          case STATIC -> attributes.add("STATIC");
          case FINAL -> attributes.add("FINAL");
          case ABSTRACT -> attributes.add("ABSTRACT");
          case TRANSIENT -> attributes.add("TRANSIENT");
          case VOLATILE -> attributes.add("VOLATILE");
          case SYNCHRONIZED -> attributes.add("SYNCHRONIZED");
          case NATIVE -> attributes.add("NATIVE");
          default -> {
            // Other modifiers are not arrangement tokens.
          }
        }
      }
    }

    // Initializer blocks carry only a static flag, not a modifier list.
    if (member instanceof InitializerDeclaration initializer && initializer.isStatic()) {
      attributes.add("STATIC");
    }

    if (!hasVisibility) {
      attributes.add("PACKAGE_PRIVATE");
    }

    return attributes;
  }

  private static String kindOf(BodyDeclaration<?> member) {
    if (member instanceof FieldDeclaration) {
      return "FIELD";
    }

    if (member instanceof ConstructorDeclaration || member instanceof CompactConstructorDeclaration) {
      return "CONSTRUCTOR";
    }

    if (member instanceof MethodDeclaration) {
      return "METHOD";
    }

    if (member instanceof InitializerDeclaration) {
      return "INITIALIZER_BLOCK";
    }

    if (member instanceof EnumDeclaration) {
      return "ENUM";
    }

    if (member instanceof AnnotationDeclaration) {
      return "INTERFACE";
    }

    if (member instanceof ClassOrInterfaceDeclaration type) {
      return type.isInterface() ? "INTERFACE" : "CLASS";
    }

    // Records and anything else are treated as classes.
    return member instanceof RecordDeclaration ? "CLASS" : "CLASS";
  }

  private static String nameOf(BodyDeclaration<?> member) {
    if (member instanceof FieldDeclaration field) {
      return field.getVariables().isEmpty() ? "" : field.getVariable(0).getNameAsString();
    }

    if (member instanceof NodeWithSimpleName<?> named) {
      return named.getNameAsString();
    }

    return "";
  }

  /**
   * @return {@code false} when reordering the members could change field-initialization or initializer-block execution
   *     order, in which case the type must be left in its original order
   */
  private static boolean isSafeToReorder(List<BodyDeclaration<?>> members) {
    Set<String> fieldNames = new HashSet<>();
    List<FieldDeclaration> fields = new ArrayList<>();
    boolean hasInitializerBlock = false;
    for (BodyDeclaration<?> member : members) {
      if (member instanceof FieldDeclaration field) {
        fields.add(field);
        for (VariableDeclarator variable : field.getVariables()) {
          fieldNames.add(variable.getNameAsString());
        }
      } else if (member instanceof InitializerDeclaration) {
        hasInitializerBlock = true;
      }
    }

    boolean anyFieldInitializer = false;
    for (FieldDeclaration field : fields) {
      for (VariableDeclarator variable : field.getVariables()) {
        if (variable.getInitializer().isEmpty()) {
          continue;
        }

        anyFieldInitializer = true;
        for (SimpleName name : variable.getInitializer().get().findAll(SimpleName.class)) {
          if (fieldNames.contains(name.getIdentifier())) {
            // A field initializer references another field; their order is significant.
            return false;
          }
        }
      }
    }

    // An initializer block interleaves with field initializers in execution order.
    return !(hasInitializerBlock && anyFieldInitializer);
  }

  private record Indexed(BodyDeclaration<?> member, int originalIndex, int ruleIndex) {
  }
}
