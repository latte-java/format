/*
 * Copyright (c) 2026, The Latte Project
 *
 * Licensed under the MIT License.
 */
package org.lattejava.format;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.stmt.BlockStmt;

/**
 * Handles "trailing" comments — line or block comments that sit at the end of the same line as the code they follow,
 * such as {@code int x = 1; // the value}.
 * <p>
 * JavaParser stores such a comment in its node's single (leading) comment slot or as an orphan comment, so the default
 * printer emits it on its own line <em>above</em> the next statement. To keep it on the original line, the trailing
 * comments are detached from the AST here and recorded against the node they follow; {@link IntelliJPrettyPrinterVisitor}
 * then re-emits each one inline, immediately after printing its node.
 *
 * @author The Latte Project
 */
final class TrailingComments {
  private TrailingComments() {
  }

  /**
   * Finds every trailing comment, detaches it from the AST, and returns a map from each node to the comment that
   * trailed it.
   *
   * @param unit the compilation unit to scan (it is mutated: trailing comments are removed)
   * @return an identity map of node to its trailing comment
   */
  static Map<Node, Comment> extract(CompilationUnit unit) {
    Map<Node, Comment> trailing = new IdentityHashMap<>();
    Set<Comment> toDetach = Collections.newSetFromMap(new IdentityHashMap<>());

    // Identify trailing comments. getAllComments() can return the same comment instance more than once, so dedupe.
    Set<Comment> processed = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Comment comment : new ArrayList<>(unit.getAllComments())) {
      if (!processed.add(comment)) {
        continue;
      }

      if (!(comment instanceof LineComment) && !(comment instanceof BlockComment)) {
        continue;
      }

      Node node = comment.getCommentedNode().orElse(null);
      if (node == null || !isTrailing(comment, node) || !printsOnOneLine(node)) {
        continue;
      }

      trailing.put(node, comment);
      toDetach.add(comment);
    }

    if (!toDetach.isEmpty()) {
      // Detach each trailing comment from the AST so the default printer does not also emit it. A comment can be
      // referenced by more than one node's comment slot (JavaParser does this for some cramped single-line inputs),
      // so clear every slot that holds one, then drop any remaining orphan references.
      for (Node node : unit.findAll(Node.class)) {
        node.getComment().filter(toDetach::contains).ifPresent(found -> node.setComment(null));
      }

      for (Comment comment : toDetach) {
        comment.remove();
      }
    }

    return trailing;
  }

  /**
   * @return the comment rendered as inline text (for example {@code // the value} or {@code /* note *​/})
   */
  static String text(Comment comment) {
    return (comment.getHeader() + comment.getContent() + comment.getFooter()).stripTrailing();
  }

  /**
   * @return {@code true} when the node prints on a single line, so that a comment after it stays trailing on a
   *     re-format (keeping the formatter idempotent). Nodes that contain a block — methods/constructors with bodies,
   *     control-flow statements, anonymous-class initializers — or type declarations expand to multiple lines, so a
   *     comment after them is left to the default handling instead.
   */
  private static boolean printsOnOneLine(Node node) {
    return !(node instanceof TypeDeclaration) && node.findFirst(BlockStmt.class).isEmpty();
  }

  private static boolean isTrailing(Comment comment, Node node) {
    if (comment.getBegin().isEmpty() || node.getEnd().isEmpty()) {
      return false;
    }

    // The comment starts on the node's last line, to the right of where the node ends.
    return comment.getBegin().get().line == node.getEnd().get().line
        && comment.getBegin().get().column > node.getEnd().get().column;
  }
}
