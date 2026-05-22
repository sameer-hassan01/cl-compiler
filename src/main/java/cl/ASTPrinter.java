package cl;

/**
 * Plain-text AST renderer.
 *
 * The default {@code dump()} that jjtree generates writes to
 * {@code System.out}; this helper builds the same indented tree as
 * a String, which is what the GUI and the {@link CompileResult}
 * want.
 */
public final class ASTPrinter {

    private ASTPrinter() { }

    public static String dump(Node root) {
        StringBuilder sb = new StringBuilder();
        walk(root, "  ", sb);
        return sb.toString();
    }

    private static void walk(Node n, String prefix, StringBuilder sb) {
        sb.append(prefix).append(label(n)).append('\n');
        for (int i = 0; i < n.jjtGetNumChildren(); i++) {
            walk(n.jjtGetChild(i), prefix + " ", sb);
        }
    }

    private static String label(Node n) {
        // strip the "AST" prefix to match the existing console output
        String s = n.getClass().getSimpleName();
        return s.startsWith("AST") ? s.substring(3) : s;
    }
}
