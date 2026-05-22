package cl;

import java.util.Collections;
import java.util.List;

/**
 * Result of running {@link CLCompiler#compile(String)}.
 *
 * Holds the output of every phase the compiler is able to run on
 * the given source. A phase is included if (and only if) the
 * previous phase succeeded:
 *
 *   - lexical / syntax errors  -> nothing else is populated
 *   - semantic errors          -> AST and symbol table are
 *                                 populated but the IR list is empty
 *   - clean compile            -> every field is populated
 *
 * This is the same control flow as the CLI does, just exposed as a
 * data object so a GUI or a test can consume it.
 */
public final class CompileResult {

    public enum Phase { LEXICAL, SYNTAX, SEMANTIC, OK }

    private final Phase                                    failedAt;
    private final String                                   errorMessage;
    private final Node                                     ast;
    private final SymbolTable                              symbols;
    private final List<String>                             semanticErrors;
    private final List<IntermediateCodeGenerator.Quad>     quads;
    private final String                                   astText;

    CompileResult(Phase failedAt,
                  String errorMessage,
                  Node ast,
                  SymbolTable symbols,
                  List<String> semanticErrors,
                  List<IntermediateCodeGenerator.Quad> quads,
                  String astText) {
        this.failedAt       = failedAt;
        this.errorMessage   = errorMessage;
        this.ast            = ast;
        this.symbols        = symbols;
        this.semanticErrors = semanticErrors == null ? Collections.<String>emptyList() : semanticErrors;
        this.quads          = quads          == null ? Collections.<IntermediateCodeGenerator.Quad>emptyList() : quads;
        this.astText        = astText        == null ? "" : astText;
    }

    public boolean ok()              { return failedAt == Phase.OK; }
    public Phase   failedAt()        { return failedAt; }
    public String  errorMessage()    { return errorMessage; }
    public Node    ast()             { return ast; }
    public String  astText()         { return astText; }
    public SymbolTable symbols()     { return symbols; }
    public List<String> semanticErrors()                  { return semanticErrors; }
    public List<IntermediateCodeGenerator.Quad> quads()   { return quads; }
}
