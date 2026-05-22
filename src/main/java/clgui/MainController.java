package clgui;

import cl.CLCompiler;
import cl.CompileResult;
import cl.IntermediateCodeGenerator;
import cl.SymbolTable;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainController {

    /* ---------- editor side ---------- */
    @FXML private TextArea  editor;
    @FXML private Label     statusLabel;
    @FXML private ComboBox<String> sampleSelector;

    /* ---------- output tabs ---------- */
    @FXML private TextArea  astArea;
    @FXML private TextArea  tokensArea;
    @FXML private TextArea  errorsArea;
    @FXML private TextArea  tacArea;

    @FXML private TableView<SymbolRow> symbolTable;
    @FXML private TableColumn<SymbolRow, String> colSymLex;
    @FXML private TableColumn<SymbolRow, String> colSymType;
    @FXML private TableColumn<SymbolRow, String> colSymValue;
    @FXML private TableColumn<SymbolRow, String> colSymLine;

    @FXML private TableView<QuadRow> quadTable;
    @FXML private TableColumn<QuadRow, String> colQuadNo;
    @FXML private TableColumn<QuadRow, String> colQuadOp;
    @FXML private TableColumn<QuadRow, String> colQuadArg1;
    @FXML private TableColumn<QuadRow, String> colQuadArg2;
    @FXML private TableColumn<QuadRow, String> colQuadResult;

    @FXML
    public void initialize() {
        // wire table columns to the bean properties
        colSymLex.setCellValueFactory(new PropertyValueFactory<>("lexeme"));
        colSymType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colSymValue.setCellValueFactory(new PropertyValueFactory<>("value"));
        colSymLine.setCellValueFactory(new PropertyValueFactory<>("line"));

        colQuadNo.setCellValueFactory(new PropertyValueFactory<>("no"));
        colQuadOp.setCellValueFactory(new PropertyValueFactory<>("op"));
        colQuadArg1.setCellValueFactory(new PropertyValueFactory<>("arg1"));
        colQuadArg2.setCellValueFactory(new PropertyValueFactory<>("arg2"));
        colQuadResult.setCellValueFactory(new PropertyValueFactory<>("result"));

        // sample programs
        sampleSelector.setItems(FXCollections.observableArrayList(
            "(choose a sample)",
            "Hello / addition",
            "Factorial (loopif)",
            "Switch (switchFor)",
            "All four data types",
            "Type error: string + int",
            "Undeclared variable"));
        sampleSelector.getSelectionModel().selectFirst();
        sampleSelector.setOnAction(e -> {
            String chosen = sampleSelector.getValue();
            String src = Samples.byName(chosen);
            if (src != null) editor.setText(src);
        });

        // start with the addition sample loaded
        editor.setText(Samples.byName("Hello / addition"));
        statusLabel.setText("ready");
    }

    @FXML
    public void onCompile(ActionEvent e) {
        clearOutputs();

        String src = editor.getText();
        if (src == null || src.trim().isEmpty()) {
            statusLabel.setText("editor is empty");
            return;
        }

        CompileResult res = CLCompiler.compile(src);

        // tokens panel -- re-lex the source so the user can see them
        tokensArea.setText(TokenLister.lex(src));

        switch (res.failedAt()) {
            case LEXICAL:
                statusLabel.setText("lexical error");
                errorsArea.setText("Lexical Error: " + res.errorMessage());
                return;
            case SYNTAX:
                statusLabel.setText("syntax error");
                errorsArea.setText("Syntax Error: " + res.errorMessage());
                return;
            default:
        }

        astArea.setText(res.astText());

        ObservableList<SymbolRow> symRows = FXCollections.observableArrayList();
        for (SymbolTable.Entry en : res.symbols().entries()) {
            symRows.add(new SymbolRow(en.lexeme, en.type,
                                     en.value == null ? "-" : en.value,
                                     Integer.toString(en.line)));
        }
        symbolTable.setItems(symRows);

        if (res.failedAt() == CompileResult.Phase.SEMANTIC) {
            StringBuilder sb = new StringBuilder();
            for (String err : res.semanticErrors()) sb.append(err).append('\n');
            errorsArea.setText(sb.toString());
            statusLabel.setText("semantic errors: " + res.semanticErrors().size());
            return;
        }

        ObservableList<QuadRow> qRows = FXCollections.observableArrayList();
        StringBuilder tac = new StringBuilder();
        java.util.List<IntermediateCodeGenerator.Quad> code = res.quads();
        for (int i = 0; i < code.size(); i++) {
            IntermediateCodeGenerator.Quad q = code.get(i);
            qRows.add(new QuadRow(Integer.toString(i), q.op, q.arg1, q.arg2, q.result));
            tac.append(i).append(":  ").append(format(q)).append('\n');
        }
        quadTable.setItems(qRows);
        tacArea.setText(tac.toString());

        statusLabel.setText("compiled ok -- " + code.size() + " quadruples emitted");
    }

    @FXML
    public void onClear(ActionEvent e) {
        editor.clear();
        clearOutputs();
        statusLabel.setText("cleared");
    }

    @FXML
    public void onOpen(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Open CL source");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CL source (*.cl)", "*.cl"));
        java.io.File f = fc.showOpenDialog(editor.getScene().getWindow());
        if (f == null) return;
        try {
            editor.setText(new String(Files.readAllBytes(f.toPath())));
            statusLabel.setText("loaded " + f.getName());
        } catch (IOException ex) {
            statusLabel.setText("open failed: " + ex.getMessage());
        }
    }

    @FXML
    public void onSave(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save CL source");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CL source (*.cl)", "*.cl"));
        fc.setInitialFileName("program.cl");
        java.io.File f = fc.showSaveDialog(editor.getScene().getWindow());
        if (f == null) return;
        try {
            Files.write(f.toPath(), editor.getText().getBytes());
            statusLabel.setText("saved " + f.getName());
        } catch (IOException ex) {
            statusLabel.setText("save failed: " + ex.getMessage());
        }
    }

    private void clearOutputs() {
        astArea.clear();
        tokensArea.clear();
        errorsArea.clear();
        tacArea.clear();
        symbolTable.getItems().clear();
        quadTable.getItems().clear();
    }

    private static String format(IntermediateCodeGenerator.Quad q) {
        if (q.op.equals("="))       return q.result + " = " + q.arg1;
        if (q.op.equals("label"))   return q.arg1   + ":";
        if (q.op.equals("goto"))    return "goto "  + q.result;
        if (q.op.equals("ifFalse")) return "ifFalse " + q.arg1 + " goto " + q.result;
        if (q.op.equals("ifTrue"))  return "ifTrue "  + q.arg1 + " goto " + q.result;
        if (q.op.equals("out"))     return "out "   + q.arg1;
        return q.result + " = " + q.arg1 + " " + q.op + " " + q.arg2;
    }

    /* ---------- row beans for the tables ---------- */

    public static class SymbolRow {
        private final SimpleStringProperty lexeme, type, value, line;
        public SymbolRow(String l, String t, String v, String n) {
            this.lexeme = new SimpleStringProperty(l);
            this.type   = new SimpleStringProperty(t);
            this.value  = new SimpleStringProperty(v);
            this.line   = new SimpleStringProperty(n);
        }
        public String getLexeme() { return lexeme.get(); }
        public String getType()   { return type.get();   }
        public String getValue()  { return value.get();  }
        public String getLine()   { return line.get();   }
    }

    public static class QuadRow {
        private final SimpleStringProperty no, op, arg1, arg2, result;
        public QuadRow(String n, String o, String a, String b, String r) {
            this.no     = new SimpleStringProperty(n);
            this.op     = new SimpleStringProperty(o);
            this.arg1   = new SimpleStringProperty(a);
            this.arg2   = new SimpleStringProperty(b);
            this.result = new SimpleStringProperty(r);
        }
        public String getNo()     { return no.get();     }
        public String getOp()     { return op.get();     }
        public String getArg1()   { return arg1.get();   }
        public String getArg2()   { return arg2.get();   }
        public String getResult() { return result.get(); }
    }
}
