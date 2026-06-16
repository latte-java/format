/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.TraditionalJavadocComment;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.nodeTypes.SwitchNode;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration.ConfigOption;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

/**
 * Extends JavaParser's default pretty printer to apply IntelliJ rules that the stock printer does not implement:
 * <ul>
 *   <li>blank lines between type members, honoring the "blank lines around" minimums and the author's own blank lines
 *       up to the "keep blank lines in declarations" cap;</li>
 *   <li>blank lines inside code blocks, preserving the author's blank lines up to the "keep blank lines in code" cap
 *       (the stock printer discards every blank line within a method body);</li>
 *   <li>arrow-form {@code switch} cases kept on a single line ({@code case X -> y;}) instead of wrapped;</li>
 *   <li>a space before the {@code switch} parenthesis.</li>
 * </ul>
 * The author's blank lines are recovered from the original source line ranges that JavaParser records on every node,
 * which is what lets a from-the-AST reprint keep the blank lines IntelliJ would have kept.
 *
 * @author The Latte Project
 */
final class IntelliJPrettyPrinterVisitor extends DefaultPrettyPrinterVisitor {
  private final CodeStyle style;

  private final MemberArranger arranger;

  private final Map<Node, Comment> trailingComments;

  IntelliJPrettyPrinterVisitor(PrinterConfiguration configuration, CodeStyle style, MemberArranger arranger,
                               Map<Node, Comment> trailingComments) {
    // The superclass constructor builds its own SourcePrinter from the configuration (its constructor is not
    // accessible from this package).
    super(configuration);
    this.style = style;
    this.arranger = arranger;
    this.trailingComments = trailingComments;
  }

  /**
   * Emits {@code node}'s trailing comment, if any, inline (after the node, before its newline).
   */
  private void emitTrailingComment(Node node) {
    Comment comment = trailingComments.get(node);
    if (comment != null) {
      printer.print(" " + TrailingComments.text(comment));
    }
  }

  @Override
  protected void printMembers(NodeList<BodyDeclaration<?>> members, Void arg) {
    List<Comment> orphanComments = members.getParentNode().map(Node::getOrphanComments).orElse(List.of());
    List<BodyDeclaration<?>> ordered = arranger != null ? arranger.arrange(members) : new ArrayList<>(members);
    for (int i = 0; i < ordered.size(); i++) {
      BodyDeclaration<?> member = ordered.get(i);
      int blankLines;
      if (i == 0) {
        blankLines = style.blankLinesAfterClassHeader();
      } else {
        int minimum = Math.max(blankLinesAround(ordered.get(i - 1)), blankLinesAround(member));
        int kept = Math.min(originalBlankLinesBetween(ordered.get(i - 1), member, orphanComments),
            style.keepBlankLinesInDeclarations());
        blankLines = Math.max(minimum, kept);
      }

      for (int b = 0; b < blankLines; b++) {
        printer.println();
      }

      member.accept(this, arg);
      emitTrailingComment(member);
      printer.println();
    }
  }

  @Override
  public void visit(BlockStmt n, Void arg) {
    printOrphanCommentsBeforeThisChildNode(n);
    printComment(n.getComment(), arg);
    printer.println("{");
    printer.indent();
    printStatements(n.getStatements(), n.getOrphanComments(), arg);
    printOrphanCommentsEnding(n);
    printer.unindent();
    printer.print("}");
  }

  @Override
  public void visit(ImportDeclaration n, Void arg) {
    // Mirrors the default import printer, but emits a trailing end-of-line comment inline.
    printOrphanCommentsBeforeThisChildNode(n);
    printComment(n.getComment(), arg);
    printer.print("import ");
    if (n.isStatic()) {
      printer.print("static ");
    }

    if (n.isModule()) {
      printer.print("module ");
    }

    n.getName().accept(this, arg);
    if (n.isAsterisk()) {
      printer.print(".*");
    }

    printer.print(";");
    emitTrailingComment(n);
    printer.println();
    printOrphanCommentsEnding(n);
  }

  @Override
  public void visit(TraditionalJavadocComment n, Void arg) {
    // Keep a single-line javadoc (no internal line breaks) on one line instead of expanding it.
    String content = n.getContent();
    boolean singleLine = content.indexOf('\n') < 0 && content.indexOf('\r') < 0;
    if (singleLine && getOption(ConfigOption.PRINT_COMMENTS).isPresent()
        && getOption(ConfigOption.PRINT_JAVADOC).isPresent()) {
      printOrphanCommentsBeforeThisChildNode(n);
      String inner = content.trim();
      String body = inner.isEmpty() ? " " : " " + inner + " ";
      printer.println(n.getHeader() + body + n.getFooter());
      return;
    }

    super.visit(n, arg);
  }

  @Override
  public void visit(SwitchStmt n, Void arg) {
    printOrphanCommentsBeforeThisChildNode(n);
    printSwitch(n, arg);
  }

  @Override
  public void visit(SwitchExpr n, Void arg) {
    printOrphanCommentsBeforeThisChildNode(n);
    printSwitch(n, arg);
  }

  @Override
  public void visit(SwitchEntry n, Void arg) {
    printOrphanCommentsBeforeThisChildNode(n);
    printComment(n.getComment(), arg);

    boolean arrow = n.getType() != SwitchEntry.Type.STATEMENT_GROUP;
    String separator = arrow ? " ->" : ":";
    if (n.getLabels().isEmpty()) {
      printer.print("default" + separator);
    } else {
      printer.print("case ");
      for (Iterator<Expression> i = n.getLabels().iterator(); i.hasNext(); ) {
        Expression label = i.next();
        label.accept(this, arg);
        if (i.hasNext()) {
          printer.print(", ");
        }
      }

      // `case null, default -> ...` (JEP 441).
      if (n.getLabels().isNonEmpty() && n.isDefault()) {
        printer.print(", default");
      }

      if (n.getGuard().isPresent()) {
        printer.print(" when ");
        n.getGuard().get().accept(this, arg);
      }

      printer.print(separator);
    }

    if (arrow) {
      // Arrow rules keep their single statement (expression, block or throw) on the same line.
      printer.print(" ");
      if (n.getStatements().isNonEmpty()) {
        n.getStatements().get(0).accept(this, arg);
      }

      printer.println();
    } else {
      printer.println();
      printer.indent();
      printStatements(n.getStatements(), n.getOrphanComments(), arg);
      printer.unindent();
    }
  }

  /**
   * Mirrors the default enum printer, but replaces the unconditional blank line that the stock visitor emits after the
   * opening brace with the configured {@code BLANK_LINES_AFTER_CLASS_HEADER} count.
   */
  @Override
  public void visit(EnumDeclaration n, Void arg) {
    printOrphanCommentsBeforeThisChildNode(n);
    printComment(n.getComment(), arg);
    printMemberAnnotations(n.getAnnotations(), arg);
    printModifiers(n.getModifiers());
    printer.print("enum ");
    n.getName().accept(this, arg);
    if (!n.getImplementedTypes().isEmpty()) {
      printer.print(" implements ");
      for (Iterator<ClassOrInterfaceType> i = n.getImplementedTypes().iterator(); i.hasNext(); ) {
        ClassOrInterfaceType c = i.next();
        c.accept(this, arg);
        if (i.hasNext()) {
          printer.print(", ");
        }
      }
    }

    printer.println(" {");
    printer.indent();
    if (n.getEntries().isNonEmpty()) {
      boolean alignVertically = n.getEntries().size() > getOption(ConfigOption.MAX_ENUM_CONSTANTS_TO_ALIGN_HORIZONTALLY)
          .get().asInteger() || n.getEntries().stream().anyMatch(e -> e.getComment().isPresent());
      for (int b = 0; b < style.blankLinesAfterClassHeader(); b++) {
        printer.println();
      }

      for (Iterator<EnumConstantDeclaration> i = n.getEntries().iterator(); i.hasNext(); ) {
        EnumConstantDeclaration e = i.next();
        e.accept(this, arg);
        if (i.hasNext()) {
          if (alignVertically) {
            printer.println(",");
          } else {
            printer.print(", ");
          }
        }
      }
    }

    if (!n.getMembers().isEmpty()) {
      printer.println(";");
      printMembers(n.getMembers(), arg);
    } else if (!n.getEntries().isEmpty()) {
      printer.println();
    }

    printer.unindent();
    printer.print("}");
  }

  private void printSwitch(SwitchNode n, Void arg) {
    printComment(n.getComment(), arg);
    printer.print("switch (");
    n.getSelector().accept(this, arg);
    printer.println(") {");
    boolean indentCase = getOption(ConfigOption.INDENT_CASE_IN_SWITCH).isPresent();
    if (indentCase) {
      printer.indent();
    }

    for (SwitchEntry e : n.getEntries()) {
      e.accept(this, arg);
    }

    if (indentCase) {
      printer.unindent();
    }

    printer.print("}");
  }

  /**
   * Prints a list of statements, each on its own line, preserving the author's blank lines between them up to the
   * "keep blank lines in code" cap.
   */
  private void printStatements(NodeList<Statement> statements, List<Comment> orphanComments, Void arg) {
    for (int i = 0; i < statements.size(); i++) {
      if (i > 0) {
        int blankLines = Math.min(originalBlankLinesBetween(statements.get(i - 1), statements.get(i), orphanComments),
            style.keepBlankLinesInCode());
        for (int b = 0; b < blankLines; b++) {
          printer.println();
        }
      }

      statements.get(i).accept(this, arg);
      emitTrailingComment(statements.get(i));
      printer.println();
    }
  }

  private int blankLinesAround(BodyDeclaration<?> member) {
    if (member instanceof FieldDeclaration) {
      return style.blankLinesAroundField();
    }

    if (member instanceof TypeDeclaration<?>) {
      return style.blankLinesAroundClass();
    }

    // Methods, constructors, initializer blocks and annotation members.
    return style.blankLinesAroundMethod();
  }

  /**
   * @return the number of blank lines that separated two nodes in the original source, or {@code 0} when the source
   *     positions are unavailable
   */
  private int originalBlankLinesBetween(Node previous, Node next, List<Comment> orphanComments) {
    if (previous.getEnd().isEmpty() || next.getBegin().isEmpty()) {
      return 0;
    }

    int previousEnd = previous.getEnd().get().line;
    int nextBegin = effectiveBeginLine(next);

    // An orphan comment (for example the first of several consecutive line comments) is printed between the two nodes,
    // so the first such comment - not the next node - is the real end of the gap.
    for (Comment comment : orphanComments) {
      if (comment.getBegin().isPresent()) {
        int line = comment.getBegin().get().line;
        if (line > previousEnd && line < nextBegin) {
          nextBegin = line;
        }
      }
    }

    return Math.max(0, nextBegin - previousEnd - 1);
  }

  /**
   * @return the first source line a node occupies, accounting for a leading comment that is printed before it
   */
  private int effectiveBeginLine(Node node) {
    int begin = node.getBegin().map(position -> position.line).orElse(0);
    if (node.getComment().isPresent() && node.getComment().get().getBegin().isPresent()) {
      begin = Math.min(begin, node.getComment().get().getBegin().get().line);
    }

    return begin;
  }
}
