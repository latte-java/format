# format

A self-contained Java source code formatter that reads an **IntelliJ IDEA code style XML** file and reformats Java
files in place — no IntelliJ installation required.

Latte projects historically formatted their source by shelling out to a locally-installed
`IntelliJ IDEA.app/Contents/bin/format.sh`. This tool replaces that dependency with a small, distributable formatter
that understands the same `.idea/codeStyles/Project.xml` files.

## How it works

The formatter parses each `.java` file into an abstract syntax tree with
[JavaParser](https://javaparser.org) and re-renders it through a printer that is configured from the settings parsed out
of the IntelliJ code style XML. Because the output is generated from the AST, the original layout is fully replaced by
the configured style (indentation, spacing, blank lines, import ordering, and so on).

JavaParser is the only runtime dependency. It is dual-licensed under **LGPL-3.0 or Apache-2.0**; this project uses it
under the **Apache-2.0** option, which is compatible with the project's MIT license.

## Building

This is a [Latte](https://lattejava.org) project (Java 25):

```bash
latte build      # compile and jar
latte test       # run the TestNG suite
```

Most formatting behavior is covered by golden-file fixtures under `src/test/resources/fixtures/`: each directory holds an
`input.java` and the `expected.java` it must format to (plus an optional `style.xml` and `options.properties`). Every
fixture is also re-formatted from its `expected.java` to verify the formatter is idempotent.

## Command-line usage

Build a runnable bundle, then use its launcher. `latte bundle` assembles `build/bundle/` with the formatter jar and its
dependencies under `lib/` and the `format.sh` launcher (it honors `JAVA_HOME` and `JAVA_OPTS`):

```bash
latte bundle
build/bundle/format.sh [options] <directory> [code-style.xml]
```

Examples:

```bash
# Format every .java file under src/, discovering the code style automatically
build/bundle/format.sh src

# Format with an explicit code style file
build/bundle/format.sh --style team-style.xml src/main/java

# Reorder members too (IntelliJ's "Rearrange entries")
build/bundle/format.sh --rearrange src

# Check formatting without modifying files (exit code 1 if anything needs formatting)
build/bundle/format.sh --check src
```

Options:

| Option                | Description                                                                               |
|-----------------------|-------------------------------------------------------------------------------------------|
| `-s`, `--style <f>`   | IntelliJ code style XML to apply (also accepted as a positional second argument).         |
| `--java-version <n>`  | Java language level to parse with (default `25`).                                          |
| `--rearrange`         | Reorder type members per the style's `<arrangement>` rules (IntelliJ's "Rearrange entries"); off by default. |
| `--check`             | Do not modify files; print what would change and exit `1` if anything is unformatted.     |
| `-h`, `--help`        | Show usage.                                                                                |

**Code style discovery.** When `--style` is omitted, the tool looks for `.idea/codeStyle/Project.xml` and then
`.idea/codeStyles/Project.xml`, searching the target directory and walking up through its parents. If none is found, it
falls back to IntelliJ's built-in defaults. Hidden directories (those whose name starts with `.`, such as `.git` and
`.idea`) are skipped while scanning for files. Files that fail to parse are reported and skipped; they are never
corrupted.

You can also format this project's own source with `latte format`.

## Library usage

```java
import java.nio.file.Path;
import org.lattejava.format.CodeStyle;
import org.lattejava.format.CodeStyleParser;
import org.lattejava.format.JavaFormatter;

CodeStyle style = CodeStyleParser.parse(Path.of(".idea/codeStyles/Project.xml"));
JavaFormatter formatter = new JavaFormatter(style);
// Or, to also reorder members per the <arrangement> rules (Java 25 source):
// JavaFormatter formatter = new JavaFormatter(style, 25, true);

String formatted = formatter.format(sourceString);   // format a string
formatter.formatFile(Path.of("Foo.java"));            // rewrite a file in place if it changes
```

## Code style settings that are applied

Every option IntelliJ writes into the XML is preserved and readable via `CodeStyle#javaOption` /
`CodeStyle#javaCodeStyleOption`. The settings that actually change the output are:

- **Indentation** — `INDENT_SIZE`, `USE_TAB_CHARACTER` (one tab per level), and the line separator (`LINE_SEPARATOR`).
- **Blank lines** — `BLANK_LINES_AFTER_CLASS_HEADER`, `BLANK_LINES_AROUND_METHOD`, `BLANK_LINES_AROUND_FIELD`,
  `BLANK_LINES_AROUND_CLASS`, plus `KEEP_BLANK_LINES_IN_CODE` and `KEEP_BLANK_LINES_IN_DECLARATIONS` (the author's own
  blank lines are recovered from the original source line ranges and kept up to these caps).
- **Spacing** — operator spacing (`SPACE_AROUND_*_OPERATORS`) and a space before the `switch` parenthesis; arrow-form
  `switch` cases are kept on one line.
- **`switch`** — `INDENT_CASE_FROM_SWITCH`.
- **Imports** — reordered and grouped to match the `IMPORT_LAYOUT_TABLE` (with the `<emptyLine/>` separators), sorted
  alphabetically within each group. When the file omits a layout table, IntelliJ's default (non-static imports, a blank
  line, then static imports) is used.
- **Member arrangement** — with `--rearrange`, type members (fields, constructors, methods, nested types) are reordered
  to match the `<arrangement>` rules, exactly like IntelliJ's "Rearrange entries". Each member is placed in the first
  rule it matches (by kind, visibility, and modifiers), and sorted `BY_NAME` within a rule when the rule says so. This
  is off by default, mirroring IntelliJ where Reformat and Rearrange are separate actions.
- **Comments** are preserved, including their placement: end-of-line ("trailing") comments such as `int x = 1; // note`
  stay on the same line, leading comments stay above their code, and a single-line `/** javadoc */` is kept on one line
  rather than expanded.

## Known limitations

This is an AST re-print, not a port of IntelliJ's formatting engine, so a few behaviors differ:

- **No right-margin wrapping.** JavaParser does not wrap lines at `RIGHT_MARGIN`, so long statements, parameter lists,
  and method chains are kept on a single line rather than wrapped. (For this reason the alignment-on-wrap options
  `ALIGN_MULTILINE_PARAMETERS` / `ALIGN_MULTILINE_CHAINED_METHODS` are parsed but not applied.)
- **A few comment placements still differ.** A trailing comment on a construct that expands to multiple lines (such as
  `void m() {} // note`, where the body becomes `{ }` on two lines) is moved to its own line above the construct, to
  keep formatting idempotent. A leading inline block comment (`/* x */ foo();`) is printed on its own line, and
  decorative blank lines inside a run of consecutive comments are not always preserved.
- **Member rearrangement is conservative about field order.** Reordering fields and initializer blocks can change
  initialization (execution) order, so when a type has a field initializer that references another field of the same
  type — or mixes initializer blocks with field initializers — that type is left in its original order rather than
  risk changing behavior. Interface members are matched by their explicit modifiers only (implicit `public` is not
  inferred).
- **Module-import grouping** (`import module ...`) is not specially placed in the import layout.
- Brace placement is emitted in IntelliJ's default end-of-line style; `*_BRACE_STYLE` next-line variants are parsed but
  not applied.

Unmapped options are parsed into `CodeStyle` and easy to wire up later; the design keeps the configuration model and the
printer cleanly separated.

## License

MIT — see [LICENSE](LICENSE).
