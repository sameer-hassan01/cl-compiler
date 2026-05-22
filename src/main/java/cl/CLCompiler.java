package cl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Driver for the CL compiler.
 *
 * Two entry points:
 *
 *   {@link #compile(String)} -- programmatic. Runs every phase the
 *   given source allows and returns the result as a structured
 *   {@link CompileResult}. The JavaFX GUI uses this method.
 *
 *   {@link #main(String[])}  -- command line. Reads a CL program
 *   from standard input (terminated by a line whose content is
 *   exactly "endProgram"), then prints the AST, symbol table,
 *   semantic errors (if any) and quadruple table.
 */
public final class CLCompiler {

    private CLCompiler() { }

    /* ---------- programmatic API ---------- */

    public static CompileResult compile(String source) {
        InputStream in = new ByteArrayInputStream(
            source.getBytes(StandardCharsets.UTF_8));
        CLParser parser = new CLParser(in);

        Node root;
        try {
            root = parser.Program();
        } catch (ParseException pe) {
            return new CompileResult(
                CompileResult.Phase.SYNTAX, pe.getMessage(),
                null, null, null, null, null);
        } catch (TokenMgrError tme) {
            return new CompileResult(
                CompileResult.Phase.LEXICAL, tme.getMessage(),
                null, null, null, null, null);
        }

        String astText = ASTPrinter.dump(root);

        SymbolTable       symbols = new SymbolTable();
        SemanticAnalyzer  sem     = new SemanticAnalyzer(symbols);
        sem.run((SimpleNode) root);

        if (sem.errorCount() > 0) {
            return new CompileResult(
                CompileResult.Phase.SEMANTIC, null,
                root, symbols, sem.getErrors(), null, astText);
        }

        IntermediateCodeGenerator icg = new IntermediateCodeGenerator();
        icg.run((SimpleNode) root);

        return new CompileResult(
            CompileResult.Phase.OK, null,
            root, symbols, sem.getErrors(), icg.getCode(), astText);
    }

    /* ---------- CLI ---------- */

    public static void main(String[] args) throws Exception {
        System.out.println("---------------------------------------------------------------");
        System.out.println(" CL Compiler");
        System.out.println(" Paste or type a CL program below.");
        System.out.println(" Input ends after a line whose content is just 'endProgram'");
        System.out.println(" (or press Ctrl+Z then Enter to end input manually).");
        System.out.println("---------------------------------------------------------------");

        BufferedReader br = new BufferedReader(
            new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String        line;
        while ((line = br.readLine()) != null) {
            sb.append(line).append('\n');
            if (line.trim().equals("endProgram")) break;
        }

        System.out.println("---------------------------------------------------------------");

        CompileResult res = compile(sb.toString());

        switch (res.failedAt()) {
            case LEXICAL:
                System.out.println("Lexical Error: " + res.errorMessage());
                return;
            case SYNTAX:
                System.out.println("Syntax Error: "  + res.errorMessage());
                return;
            default:
                System.out.println("[parser] no syntax errors found");
        }

        System.out.println();
        System.out.println("===== Abstract Syntax Tree =====");
        System.out.print(res.astText());

        System.out.println();
        System.out.println("===== Symbol Table =====");
        res.symbols().print();

        if (res.failedAt() == CompileResult.Phase.SEMANTIC) {
            System.out.println();
            System.out.println("===== Semantic Errors =====");
            for (String e : res.semanticErrors()) System.out.println(e);
            System.out.println();
            System.out.println("[abort] semantic analysis failed -- skipping ICG");
            return;
        }

        System.out.println();
        System.out.println("===== Three Address Code =====");
        printQuads(res.quads());
    }

    private static void printQuads(java.util.List<IntermediateCodeGenerator.Quad> code) {
        System.out.printf("%-5s %-8s %-12s %-12s %-12s%n",
                          "#", "OP", "ARG1", "ARG2", "RESULT");
        System.out.println("-------------------------------------------------------");
        for (int i = 0; i < code.size(); i++) {
            IntermediateCodeGenerator.Quad q = code.get(i);
            System.out.printf("%-5d %-8s %-12s %-12s %-12s%n",
                              i, q.op, q.arg1, q.arg2, q.result);
        }
        System.out.println();
        System.out.println("---- statement form ----");
        for (int i = 0; i < code.size(); i++) {
            IntermediateCodeGenerator.Quad q = code.get(i);
            System.out.println(i + ":  " + tac(q));
        }
    }

    private static String tac(IntermediateCodeGenerator.Quad q) {
        if (q.op.equals("="))       return q.result + " = " + q.arg1;
        if (q.op.equals("label"))   return q.arg1   + ":";
        if (q.op.equals("goto"))    return "goto "  + q.result;
        if (q.op.equals("ifFalse")) return "ifFalse " + q.arg1 + " goto " + q.result;
        if (q.op.equals("ifTrue"))  return "ifTrue "  + q.arg1 + " goto " + q.result;
        if (q.op.equals("out"))     return "out "   + q.arg1;
        return q.result + " = " + q.arg1 + " " + q.op + " " + q.arg2;
    }
}
