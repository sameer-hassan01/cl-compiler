# CL Compiler -- Build Pipeline and Working Pipeline

This file explains, end to end, how the project is put together and
how a CL source program flows through it.

There are two pipelines to understand:

1. **Build pipeline** -- the steps Maven runs to turn `CL.jjt` and
   the hand-written Java sources into a working compiler + GUI jar.
2. **Working pipeline** -- the steps the compiler runs every time
   it gets a CL program (either pasted into the GUI or sent to the
   stdin CLI).

---

## 1. Build pipeline

`mvn package` (or `build.bat`) does everything in one shot:

```
            +-----------------------+
            |   src/main/jjtree/    |
            |        CL.jjt         |   <-- grammar + scanner + AST hooks
            +-----------+-----------+
                        |
                        | step 1 : javacc-maven-plugin (jjtree goal)
                        v
   +---------------------------------------------+
   | target/generated-sources/jjtree/cl/         |
   |   ASTProgram, ASTVarDecl, ASTAssignStmt,    |
   |   ASTExpr, ASTTerm, ASTFactor, ... (15 +)   |
   |   SimpleNode, Node, ...                     |
   +-----------+---------------------------------+
                        |
                        | step 2 : javacc-maven-plugin (javacc goal)
                        v
   +---------------------------------------------+
   | target/generated-sources/javacc/cl/         |
   |   CLParser.java                             |
   |   CLParserConstants.java                    |
   |   CLParserTokenManager.java                 |
   |   Token, SimpleCharStream, ParseException   |
   |   TokenMgrError                             |
   +-----------+---------------------------------+
                        |
                        | step 3 : maven-compiler-plugin
                        |     + src/main/java/cl/*.java
                        |     + src/main/java/clgui/*.java
                        v
            +-----------------------+
            | target/classes/       |
            +-----------+-----------+
                        |
                        | step 4 : maven-shade-plugin
                        v
   +---------------------------------------------+
   | target/cl-compiler-1.0.0.jar                |
   |  (fat jar: compiler + GUI + JavaFX runtime) |
   +---------------------------------------------+
```

### What each Maven plugin does

| Plugin                              | What it does                                                                            |
| ----------------------------------- | --------------------------------------------------------------------------------------- |
| `org.codehaus.mojo:javacc-maven-plugin` | runs JJTree, then JavaCC, generating the lexer, parser and AST node classes        |
| `org.apache.maven.plugins:maven-compiler-plugin` | compiles every `.java` (hand-written + generated) to `target/classes/`     |
| `org.openjfx:javafx-maven-plugin`   | provides `mvn javafx:run` to launch the GUI from sources                                |
| `org.apache.maven.plugins:maven-shade-plugin` | bundles compiled classes + JavaFX runtime into a single executable fat jar      |

Hand-written sources:

```
src/main/java/cl/CLCompiler.java                 -- orchestration + CLI main
src/main/java/cl/CompileResult.java              -- data class
src/main/java/cl/SymbolTable.java                -- data structure
src/main/java/cl/SemanticAnalyzer.java           -- checks
src/main/java/cl/IntermediateCodeGenerator.java  -- TAC + quadruples
src/main/java/cl/ASTPrinter.java                 -- tree -> string

src/main/java/clgui/App.java                     -- JavaFX entry
src/main/java/clgui/Launcher.java                -- fat-jar shim
src/main/java/clgui/MainController.java          -- FXML controller
src/main/java/clgui/TokenLister.java             -- lexer view helper
src/main/java/clgui/Samples.java                 -- preloaded examples

src/main/resources/clgui/main.fxml               -- UI layout
src/main/resources/clgui/styles.css              -- dark theme
```

---

## 2. Working pipeline (runtime)

The same `CLCompiler.compile(String source) -> CompileResult` path
is used whether the source comes from the GUI editor or from stdin.

```
   +-----------------------+
   |  CL source (text)     |
   +-----------+-----------+
               |
               v
   +-----------------------+      char stream -> token stream
   |  Scanner / Lexer      |      (CLParserTokenManager)
   |  - skip whitespace    |
   |  - skip // comments   |
   |  - recognise keywords,|
   |    IDENT, INTEGER,    |
   |    FLOATING, STRLIT,  |
   |    CHARLIT, ops, etc. |
   +-----------+-----------+
               |   (re-lexed once more for the GUI Tokens tab
               |    via TokenLister)
               v
   +-----------------------+      tokens -> AST
   |  Parser (LL(1))       |      (CLParser + JJTree hooks)
   |  - top-down predictive|
   |  - one method per     |
   |    non-terminal       |
   |  - builds AST nodes   |
   |    as side effect     |
   +-----------+-----------+
               |
               | (syntax / lexical errors -> CompileResult with
               |  failedAt = SYNTAX / LEXICAL, stop)
               v
   +-----------------------+      AST -> (table + errors)
   |  SemanticAnalyzer     |
   |  - declare all vars   |   creates --> SymbolTable
   |    into symbol table  |               (lexeme, type,
   |  - walk code block:   |                value, line)
   |     * undeclared use  |
   |     * type mismatch   |
   |     * dup decl        |
   |     * illegal op on   |
   |       string / char   |
   |     * cond / switch   |
   |       type rules      |
   +-----------+-----------+
               |
               | (one or more semantic errors -> CompileResult
               |  with failedAt = SEMANTIC, IR skipped)
               v
   +-----------------------+      AST -> quadruple array
   | IntermediateCodeGen   |
   |   walk AST in         |
   |   post-order, emit:   |
   |     (op,arg1,arg2,res)|
   |   into ArrayList<Quad>|
   |                       |
   |   loopif -> labels +  |
   |             ifFalse + |
   |             goto      |
   |                       |
   |   switchFor -> dispatch
   |     table + per-case  |
   |     labels + goto end |
   +-----------+-----------+
               |
               v
   +--------------------------------------------+
   | CompileResult { ast, astText, symbols,     |
   |                 semanticErrors, quads }    |
   +--+-----------------+-----------------------+
      |                 |
      v                 v
   GUI tabs          stdin CLI
   (Tokens / AST /   (prints AST, symbol
    Symbol Table /    table, errors,
    Errors /          quadruple table and
    Quadruples /      TAC statement form
    TAC text)         to the console)
```

### What each phase reads and writes

| Phase                       | Reads                       | Writes                                  |
| --------------------------- | --------------------------- | --------------------------------------- |
| Scanner                     | character stream            | token stream                            |
| Parser                      | token stream                | AST + SyntaxError                       |
| Symbol Table construction   | VarBlock subtree of AST     | SymbolTable entries                     |
| Semantic analyser           | full AST + SymbolTable      | list of SemanticError messages          |
| Intermediate code generator | full AST + SymbolTable      | ArrayList of Quad                       |

### Error handling

The pipeline short-circuits at the first failed phase:

* a **lexical error** is reported by `TokenMgrError`; nothing else
  runs.
* a **syntax error** is reported by `ParseException`; nothing else
  runs.
* **semantic errors** are collected as a list and printed all
  together; if even one was reported the intermediate code
  generation phase is skipped.
