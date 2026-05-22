package clgui;

/** Hard-coded CL programs the playground offers in its dropdown. */
final class Samples {

    static String byName(String label) {
        if (label == null) return null;
        switch (label) {
            case "Hello / addition":
                return ADDITION;
            case "Factorial (loopif)":
                return FACTORIAL;
            case "Switch (switchFor)":
                return SWITCH;
            case "All four data types":
                return TYPES;
            case "Type error: string + int":
                return TYPE_ERR;
            case "Undeclared variable":
                return UNDECLARED;
            default:
                return null;
        }
    }

    private static final String ADDITION =
        "// simple integer addition\n" +
        "startProgram\n" +
        "\n" +
        "  variables:\n" +
        "    int abc    = 3;\n" +
        "    int xyz    = 4;\n" +
        "    int result = 0;\n" +
        "\n" +
        "  code:\n" +
        "    result = abc + xyz;\n" +
        "    outString(result);\n" +
        "\n" +
        "endProgram\n";

    private static final String FACTORIAL =
        "// factorial using loopif\n" +
        "startProgram\n" +
        "  variables:\n" +
        "    int n    = 5;\n" +
        "    int fact = 1;\n" +
        "  code:\n" +
        "    loopif n > 0 holds\n" +
        "      fact = fact * n;\n" +
        "      n    = n - 1;\n" +
        "    endloop\n" +
        "    outString(fact);\n" +
        "endProgram\n";

    private static final String SWITCH =
        "// switch on grade\n" +
        "startProgram\n" +
        "  variables:\n" +
        "    int grade = 2;\n" +
        "    int prize = 0;\n" +
        "  code:\n" +
        "    switchFor (grade)\n" +
        "      case 1 : prize = 100;\n" +
        "      case 2 : prize = 50;\n" +
        "      case 3 : prize = 25;\n" +
        "      other  : prize = 0;\n" +
        "    endswitchFor\n" +
        "    outString(prize);\n" +
        "endProgram\n";

    private static final String TYPES =
        "// uses all four data types\n" +
        "startProgram\n" +
        "  variables:\n" +
        "    int    age   = 21;\n" +
        "    float  pi    = 3.14;\n" +
        "    string name  = \"haroon\";\n" +
        "    char   grade = 'A';\n" +
        "  code:\n" +
        "    age = age + 1;\n" +
        "    outString(name);\n" +
        "    outString(grade);\n" +
        "    outString(pi);\n" +
        "    outString(age);\n" +
        "endProgram\n";

    private static final String TYPE_ERR =
        "// adding a string and an int -> rejected\n" +
        "startProgram\n" +
        "  variables:\n" +
        "    string b = \"hi\";\n" +
        "    int    c = 5;\n" +
        "    string a = \"\";\n" +
        "  code:\n" +
        "    a = b + c;\n" +
        "endProgram\n";

    private static final String UNDECLARED =
        "// y is used but never declared\n" +
        "startProgram\n" +
        "  variables:\n" +
        "    int x = 1;\n" +
        "  code:\n" +
        "    y = x + 1;\n" +
        "endProgram\n";

    private Samples() { }
}
