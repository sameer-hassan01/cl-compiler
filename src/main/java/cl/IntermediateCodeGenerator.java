package cl;

import java.util.ArrayList;
import java.util.List;

/*
 * Three-Address-Code generator for CL.
 *
 * Walks the same AST built by JJTree. For each instruction it
 * appends one Quad (op, arg1, arg2, result) to the `code` array.
 * At the end the whole array is dumped on the screen in two forms:
 *
 *   - quadruple table (the format used in the assignment PDF)
 *   - readable statement form, e.g.  t1 = b * c
 *
 * Conventions used here:
 *
 *   simple assign           =   value  --     dest
 *   binary op (+,-,*,/)    op   a      b      tN
 *   relational op          op   a      b      tN     (used by loopif)
 *   label                label  Lname  --     --
 *   unconditional goto    goto  --     --     Lname
 *   conditional goto    ifFalse tN     --     Lname
 *                       ifTrue  tN     --     Lname
 *   output                out   value  --     --
 */
public class IntermediateCodeGenerator {

    public static class Quad {
        public final String op, arg1, arg2, result;
        public Quad(String op, String arg1, String arg2, String result) {
            this.op = op; this.arg1 = arg1; this.arg2 = arg2; this.result = result;
        }
    }

    private final List<Quad> code = new ArrayList<Quad>();
    private int tempCount  = 0;
    private int labelCount = 0;

    public List<Quad> getCode() { return code; }

    public void run(SimpleNode root) {
        walk(root);
    }

    /* ---------- dispatch ---------- */

    private void walk(Node n) {
        String cls = n.getClass().getSimpleName();

        if (cls.equals("ASTProgram") || cls.equals("ASTCodeBlock") ||
            cls.equals("ASTVarBlock")) {
            for (int i = 0; i < n.jjtGetNumChildren(); i++) walk(n.jjtGetChild(i));
            return;
        }
        if (cls.equals("ASTVarDecl"))    { genVarDecl((SimpleNode) n);  return; }
        if (cls.equals("ASTAssignStmt")) { genAssign((SimpleNode) n);   return; }
        if (cls.equals("ASTLoopStmt"))   { genLoop((SimpleNode) n);     return; }
        if (cls.equals("ASTSwitchStmt")) { genSwitch((SimpleNode) n);   return; }
        if (cls.equals("ASTOutStmt"))    { genOut((SimpleNode) n);      return; }
    }

    /* ---------- declarations and assignments ---------- */

    private void genVarDecl(SimpleNode n) {
        Object[] info = (Object[]) n.jjtGetValue();
        // emit  name = literal
        emit("=", (String) info[2], "--", (String) info[1]);
    }

    private void genAssign(SimpleNode n) {
        Object[] info = (Object[]) n.jjtGetValue();
        String lhs   = (String) info[0];
        String rhs   = genExpr((SimpleNode) n.jjtGetChild(0));
        emit("=", rhs, "--", lhs);
    }

    /* ---------- expressions ---------- */

    @SuppressWarnings("unchecked")
    private String genExpr(SimpleNode expr) {
        List<String> ops = (List<String>) expr.jjtGetValue();
        if (ops == null) ops = new ArrayList<String>();

        String cur = genTerm((SimpleNode) expr.jjtGetChild(0));
        for (int i = 0; i < ops.size(); i++) {
            String r = genTerm((SimpleNode) expr.jjtGetChild(i + 1));
            String t = newTemp();
            emit(ops.get(i), cur, r, t);
            cur = t;
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private String genTerm(SimpleNode term) {
        List<String> ops = (List<String>) term.jjtGetValue();
        if (ops == null) ops = new ArrayList<String>();

        String cur = genFactor((SimpleNode) term.jjtGetChild(0));
        for (int i = 0; i < ops.size(); i++) {
            String r = genFactor((SimpleNode) term.jjtGetChild(i + 1));
            String t = newTemp();
            emit(ops.get(i), cur, r, t);
            cur = t;
        }
        return cur;
    }

    private String genFactor(SimpleNode f) {
        Object[] info = (Object[]) f.jjtGetValue();
        String kind = (String) info[0];
        if (kind.equals("paren")) {
            return genExpr((SimpleNode) f.jjtGetChild(0));
        }
        return (String) info[1];     // image of literal or identifier
    }

    /* ---------- loop ---------- */

    private void genLoop(SimpleNode n) {
        String start = newLabel();
        String end   = newLabel();

        emit("label", start, "--", "--");
        String cond = genCond((SimpleNode) n.jjtGetChild(0));
        emit("ifFalse", cond, "--", end);

        for (int i = 1; i < n.jjtGetNumChildren(); i++) {
            walk(n.jjtGetChild(i));
        }

        emit("goto",  "--", "--", start);
        emit("label", end,  "--", "--");
    }

    private String genCond(SimpleNode cond) {
        Object[] info = (Object[]) cond.jjtGetValue();
        String op = (String) info[0];
        String a  = operandImage((SimpleNode) cond.jjtGetChild(0));
        String b  = operandImage((SimpleNode) cond.jjtGetChild(1));
        String t  = newTemp();
        emit(op, a, b, t);
        return t;
    }

    private String operandImage(SimpleNode op) {
        Object[] info = (Object[]) op.jjtGetValue();
        return (String) info[0];
    }

    /* ---------- switchFor ---------- */

    private void genSwitch(SimpleNode n) {
        Object[] info = (Object[]) n.jjtGetValue();
        String var = (String) info[0];

        List<SimpleNode> cases = new ArrayList<SimpleNode>();
        SimpleNode other = null;
        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            Node ch  = n.jjtGetChild(i);
            String c = ch.getClass().getSimpleName();
            if (c.equals("ASTCaseClause"))    cases.add((SimpleNode) ch);
            else if (c.equals("ASTAssignStmt")) other = (SimpleNode) ch;
        }

        String[] caseLabels = new String[cases.size()];
        String otherLabel = newLabel();
        String endLabel   = newLabel();

        // dispatch: compare var with each case literal, jump if equal
        for (int i = 0; i < cases.size(); i++) {
            caseLabels[i] = newLabel();
            Object[] ci = (Object[]) cases.get(i).jjtGetValue();
            String lit  = (String)  ci[0];
            String t    = newTemp();
            emit("==", var, lit, t);
            emit("ifTrue", t, "--", caseLabels[i]);
        }
        emit("goto", "--", "--", otherLabel);

        // bodies
        for (int i = 0; i < cases.size(); i++) {
            emit("label", caseLabels[i], "--", "--");
            walk(cases.get(i).jjtGetChild(0));     // the assignment
            emit("goto", "--", "--", endLabel);
        }

        emit("label", otherLabel, "--", "--");
        if (other != null) walk(other);
        emit("label", endLabel, "--", "--");
    }

    /* ---------- output ---------- */

    private void genOut(SimpleNode n) {
        if (n.jjtGetNumChildren() > 0) {
            String v = genExpr((SimpleNode) n.jjtGetChild(0));
            emit("out", v, "--", "--");
        }
    }

    /* ---------- emit + helpers ---------- */

    private void emit(String op, String a, String b, String r) {
        code.add(new Quad(op, a, b, r));
    }

    private String newTemp()  { return "t" + (++tempCount);  }
    private String newLabel() { return "L" + (++labelCount); }

    /* ---------- printing ---------- */

    public void print() {
        // quadruple form (matches the assignment PDF format)
        System.out.printf("%-5s %-8s %-12s %-12s %-12s%n", "#", "OP", "ARG1", "ARG2", "RESULT");
        System.out.println("-------------------------------------------------------");
        for (int i = 0; i < code.size(); i++) {
            Quad q = code.get(i);
            System.out.printf("%-5d %-8s %-12s %-12s %-12s%n",
                              i, q.op, q.arg1, q.arg2, q.result);
        }

        // readable form
        System.out.println();
        System.out.println("---- statement form ----");
        for (int i = 0; i < code.size(); i++) {
            System.out.println(i + ":  " + asText(code.get(i)));
        }
    }

    private String asText(Quad q) {
        if (q.op.equals("="))       return q.result + " = " + q.arg1;
        if (q.op.equals("label"))   return q.arg1   + ":";
        if (q.op.equals("goto"))    return "goto "  + q.result;
        if (q.op.equals("ifFalse")) return "ifFalse " + q.arg1 + " goto " + q.result;
        if (q.op.equals("ifTrue"))  return "ifTrue "  + q.arg1 + " goto " + q.result;
        if (q.op.equals("out"))     return "out "   + q.arg1;
        return q.result + " = " + q.arg1 + " " + q.op + " " + q.arg2;
    }
}
