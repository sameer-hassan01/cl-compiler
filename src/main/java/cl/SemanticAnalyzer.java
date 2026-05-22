package cl;

import java.util.ArrayList;
import java.util.List;

/*
 * Semantic analyser for CL.
 *
 * Walks the JJTree AST and verifies:
 *   1. every identifier used in the code block is declared
 *   2. no identifier is declared twice in the variables block
 *   3. left and right hand side of an assignment have the same type
 *   4. the two sides of a binary expression have the same type
 *      (no implicit conversion -- spec is strict on this)
 *   5. arithmetic operators (+ - * /) are not used on string/char
 *   6. operands of a CondExpr are numeric and same-typed
 *   7. each case literal in switchFor matches the switch variable's
 *      type
 *
 * The values stored on each AST node by CL.jjt are read back here.
 * Errors are collected in a list so the user sees every problem
 * in one go instead of just the first one.
 */
public class SemanticAnalyzer {

    private final SymbolTable    st;
    private final List<String>   errors = new ArrayList<String>();

    public SemanticAnalyzer(SymbolTable st) { this.st = st; }

    public int          errorCount() { return errors.size(); }
    public List<String> getErrors()  { return errors;       }

    public void printErrors() {
        for (String e : errors) System.out.println(e);
    }

    public void run(SimpleNode root) {
        walk(root);
    }

    /* ---------- dispatch ---------- */

    private void walk(Node n) {
        String cls = n.getClass().getSimpleName();

        if (cls.equals("ASTProgram") || cls.equals("ASTVarBlock") ||
            cls.equals("ASTCodeBlock")) {
            for (int i = 0; i < n.jjtGetNumChildren(); i++) walk(n.jjtGetChild(i));
            return;
        }
        if (cls.equals("ASTVarDecl"))    { doVarDecl((SimpleNode) n);    return; }
        if (cls.equals("ASTAssignStmt")) { doAssign((SimpleNode) n);     return; }
        if (cls.equals("ASTLoopStmt"))   { doLoop((SimpleNode) n);       return; }
        if (cls.equals("ASTSwitchStmt")) { doSwitch((SimpleNode) n);     return; }
        if (cls.equals("ASTOutStmt"))    { doOut((SimpleNode) n);        return; }
    }

    /* ---------- declarations ---------- */

    private void doVarDecl(SimpleNode n) {
        Object[] info = (Object[]) n.jjtGetValue();
        String type   = (String)  info[0];
        String name   = (String)  info[1];
        String value  = (String)  info[2];
        int    kind   = (Integer) info[3];
        int    line   = (Integer) info[4];

        String litT = typeOfLiteralKind(kind);

        if (!type.equals(litT)) {
            err(line, "type mismatch in declaration of '" + name +
                      "': declared " + type + " but initial value is " + litT);
        }
        if (!st.declare(name, type, value, line)) {
            err(line, "duplicate declaration of '" + name + "'");
        }
    }

    /* ---------- assignment ---------- */

    private void doAssign(SimpleNode n) {
        Object[] info = (Object[]) n.jjtGetValue();
        String name   = (String)  info[0];
        int    line   = (Integer) info[1];

        SymbolTable.Entry e = st.lookup(name);
        if (e == null) {
            err(line, "identifier '" + name + "' is not declared");
            return;
        }

        SimpleNode expr = (SimpleNode) n.jjtGetChild(0);
        String rhs = checkExpr(expr);
        if (rhs == null) return;     // an inner error already reported

        if (!rhs.equals(e.type)) {
            err(line, "cannot assign " + rhs + " to " + e.type +
                      " variable '" + name + "'");
        }
    }

    /* ---------- expressions ---------- */

    @SuppressWarnings("unchecked")
    private String checkExpr(SimpleNode expr) {
        List<String> ops = (List<String>) expr.jjtGetValue();
        if (ops == null) ops = new ArrayList<String>();

        String result = checkTerm((SimpleNode) expr.jjtGetChild(0));
        if (result == null) return null;

        for (int i = 0; i < ops.size(); i++) {
            String rhs = checkTerm((SimpleNode) expr.jjtGetChild(i + 1));
            if (rhs == null) return null;

            int line = expr.jjtGetFirstToken().beginLine;
            if (!rhs.equals(result)) {
                err(line, "type mismatch in expression: '" + ops.get(i) +
                          "' between " + result + " and " + rhs);
                return null;
            }
            // '-' on strings makes no sense; '+' on strings could be
            // concatenation, but the spec keeps the language strict so
            // we reject both for now.
            if (result.equals("string") || result.equals("char")) {
                err(line, "operator '" + ops.get(i) +
                          "' not allowed on " + result);
                return null;
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private String checkTerm(SimpleNode term) {
        List<String> ops = (List<String>) term.jjtGetValue();
        if (ops == null) ops = new ArrayList<String>();

        String result = checkFactor((SimpleNode) term.jjtGetChild(0));
        if (result == null) return null;

        for (int i = 0; i < ops.size(); i++) {
            String rhs = checkFactor((SimpleNode) term.jjtGetChild(i + 1));
            if (rhs == null) return null;

            int line = term.jjtGetFirstToken().beginLine;
            if (!rhs.equals(result)) {
                err(line, "type mismatch in expression: '" + ops.get(i) +
                          "' between " + result + " and " + rhs);
                return null;
            }
            if (result.equals("string") || result.equals("char")) {
                err(line, "operator '" + ops.get(i) +
                          "' not allowed on " + result);
                return null;
            }
        }
        return result;
    }

    private String checkFactor(SimpleNode f) {
        Object[] info = (Object[]) f.jjtGetValue();
        String   kind = (String)  info[0];

        if (kind.equals("paren")) {
            return checkExpr((SimpleNode) f.jjtGetChild(0));
        }
        if (kind.equals("id")) {
            String name = (String)  info[1];
            int    line = (Integer) info[2];
            SymbolTable.Entry e = st.lookup(name);
            if (e == null) {
                err(line, "identifier '" + name + "' is not declared");
                return null;
            }
            return e.type;
        }
        return kind;     // int / float / string / char literal
    }

    /* ---------- loop ---------- */

    private void doLoop(SimpleNode n) {
        SimpleNode cond = (SimpleNode) n.jjtGetChild(0);
        checkCond(cond);
        for (int i = 1; i < n.jjtGetNumChildren(); i++) {
            walk(n.jjtGetChild(i));
        }
    }

    private void checkCond(SimpleNode cond) {
        Object[] info = (Object[]) cond.jjtGetValue();
        String op   = (String)  info[0];
        int    line = (Integer) info[1];

        String t1 = operandType((SimpleNode) cond.jjtGetChild(0));
        String t2 = operandType((SimpleNode) cond.jjtGetChild(1));
        if (t1 == null || t2 == null) return;

        if (!t1.equals(t2)) {
            err(line, "type mismatch in condition '" + op +
                      "': " + t1 + " vs " + t2);
            return;
        }
        if (!t1.equals("int") && !t1.equals("float")) {
            err(line, "condition operand must be numeric (int/float), found " + t1);
        }
    }

    private String operandType(SimpleNode op) {
        Object[] info = (Object[]) op.jjtGetValue();
        String image = (String)  info[0];
        int    kind  = (Integer) info[1];
        int    line  = (Integer) info[2];

        if (kind == CLParserConstants.IDENT) {
            SymbolTable.Entry e = st.lookup(image);
            if (e == null) {
                err(line, "identifier '" + image + "' is not declared");
                return null;
            }
            return e.type;
        }
        if (kind == CLParserConstants.INTEGER)  return "int";
        if (kind == CLParserConstants.FLOATING) return "float";
        return null;
    }

    /* ---------- switchFor ---------- */

    private void doSwitch(SimpleNode n) {
        Object[] info = (Object[]) n.jjtGetValue();
        String name  = (String)  info[0];
        int    line  = (Integer) info[1];

        SymbolTable.Entry e = st.lookup(name);
        if (e == null) {
            err(line, "switch variable '" + name + "' is not declared");
            return;
        }
        String varT = e.type;

        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            Node ch = n.jjtGetChild(i);
            String cls = ch.getClass().getSimpleName();

            if (cls.equals("ASTCaseClause")) {
                SimpleNode cc = (SimpleNode) ch;
                Object[] ci  = (Object[]) cc.jjtGetValue();
                int    kind  = (Integer) ci[1];
                int    cline = (Integer) ci[2];
                String litT  = typeOfLiteralKind(kind);
                if (!litT.equals(varT)) {
                    err(cline, "case literal of type " + litT +
                               " does not match switch variable type " + varT);
                }
                walk(cc.jjtGetChild(0));     // assign inside the case
            } else if (cls.equals("ASTAssignStmt")) {
                // "other" branch
                walk(ch);
            }
        }
    }

    /* ---------- output ---------- */

    private void doOut(SimpleNode n) {
        if (n.jjtGetNumChildren() > 0) {
            checkExpr((SimpleNode) n.jjtGetChild(0));
        }
    }

    /* ---------- helpers ---------- */

    private String typeOfLiteralKind(int kind) {
        if (kind == CLParserConstants.INTEGER)  return "int";
        if (kind == CLParserConstants.FLOATING) return "float";
        if (kind == CLParserConstants.STRLIT)   return "string";
        if (kind == CLParserConstants.CHARLIT)  return "char";
        return "?";
    }

    private void err(int line, String msg) {
        errors.add("Semantic Error at line " + line + ": " + msg);
    }
}
