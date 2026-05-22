package clgui;

import cl.CLParserConstants;
import cl.CLParserTokenManager;
import cl.SimpleCharStream;
import cl.Token;
import cl.TokenMgrError;

import java.io.StringReader;

/**
 * Re-runs the CL lexer on a source string just to populate the
 * "Tokens" tab in the GUI. The parser already does this internally
 * but discards the stream as it goes -- this view shows the
 * student what the scanner actually produced.
 */
final class TokenLister {

    private TokenLister() { }

    static String lex(String source) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-6s %-6s %-22s %s%n",
                                "LINE", "COL", "LEXEME", "TOKEN"));
        sb.append("---------------------------------------------------------------\n");

        SimpleCharStream     stream = new SimpleCharStream(new StringReader(source));
        CLParserTokenManager tm     = new CLParserTokenManager(stream);

        try {
            for (Token t = tm.getNextToken();
                 t.kind != CLParserConstants.EOF;
                 t = tm.getNextToken()) {
                sb.append(String.format("%-6d %-6d %-22s %s%n",
                                        t.beginLine, t.beginColumn,
                                        truncate(t.image, 20),
                                        kindName(t.kind)));
            }
        } catch (TokenMgrError tme) {
            sb.append("\n[lexer stopped] ").append(tme.getMessage()).append('\n');
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }

    private static String kindName(int kind) {
        if (kind < 0 || kind >= CLParserConstants.tokenImage.length) return "?";
        String img = CLParserConstants.tokenImage[kind];
        // tokenImage entries look like "\"int\"" for keywords or
        // "<IDENT>" for symbolic ones -- normalise both
        if (img.startsWith("<") && img.endsWith(">")) {
            return img.substring(1, img.length() - 1);
        }
        return img;
    }
}
