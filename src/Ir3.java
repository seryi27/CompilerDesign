import java.util.*;
import java.math.*;

public class Ir3 {
    public interface Printable {
        String prettyPrint(int indent);
    }

    public static void indent(StringBuilder sb, int indent) {
        while (indent > 0) {
            sb.append("  ");
            indent--;
        }
    }

    public static class Prog {
        public ArrayList<Data> datas;
        public ArrayList<Meth> meths;

        public Prog(List<Data> datas, List<Meth> meths) {
            this.datas = new ArrayList<>(datas);
            this.meths = new ArrayList<>(meths);
        }

        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            for (Data data : datas) {
                sb.append(data.prettyPrint(indent));
            }
            for (Meth meth : meths) {
                sb.append(meth.prettyPrint(indent));
            }
            return sb.toString();
        }
    }

    public static class Data {
        public String cname;
        public ArrayList<DataField> fields;

        public Data(String cname, List<DataField> fields) {
            this.cname = cname;
            this.fields = new ArrayList<>(fields);
        }

        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append("Data3 ").append(cname).append(" {\n");
            for (DataField dataField : fields) {
                sb.append(dataField.prettyPrint(indent+1));
            }
            indent(sb, indent);
            sb.append("}\n");
            return sb.toString();
        }
    }

    public static class DataField {
        public Ast.Typ typ;
        public String name;

        public DataField(Ast.Typ typ, String name) {
            this.typ = typ;
            this.name = name;
        }

        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(typ.prettyPrint(0)).append(" ").append(name).append(";\n");
            return sb.toString();
        }
    }

    /**
     * Represents a local variable / argument / temporary.
     */
    public static class Var {
        public boolean stackSpace;
        public Ast.Typ typ;
        public String name;
        public int reg = -1;

        public Var(Ast.Typ typ, String name) {
            this.typ = typ;
            this.name = name;
        }

        public String prettyPrint() {
            if (reg >= 0)
                return name + " {r" + reg + "}";
            else
                return name;
        }

        @Override
        public String toString() {
            return "Var(" + name + ", " + reg + ")";
        }
    }

    public static class Meth {
        public Ast.Typ retTyp;
        public String name;
        public ArrayList<Boolean> stackSpaces;
        public ArrayList<Var> args;
        public ArrayList<Var> locals;
        public ArrayList<Block> blocks;
        public ArrayList<Block> blocksPre;
        public ArrayList<Block> blocksPost;
        public ArrayList<Block> blocksRpost;

        public Meth(Ast.Typ retTyp, String name) {
            this.retTyp = retTyp;
            this.name = name;
        }

        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(retTyp.prettyPrint(0)).append(" ").append(name).append("(");
            int i = 0;
            for (Var v : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(v.typ.prettyPrint(0)).append(" ").append(v.name);
            }
            sb.append(") {\n");
            for (Var v : locals) {
                indent(sb, indent + 1);
                sb.append(v.typ.prettyPrint(0)).append(" ").append(v.name).append(";\n");
            }
            sb.append("\n");
            for (Block block : blocks) {
                sb.append(block.prettyPrint(indent + 1));
            }
            indent(sb, indent);
            sb.append("}\n");
            return sb.toString();
        }

        public String prettyPrintCfg() {
            StringBuilder sb = new StringBuilder();
            sb.append("digraph cfg {\n");
            for (Ir3.Block b : blocks) {
                if (b.out != null)
                    sb.append("  ").append(b.label.name).append(" -> ").append(b.out.label.name).append(";\n");
                if (b.outCond != null)
                    sb.append("  ").append(b.label.name).append(" -> ").append(b.outCond.label.name).append(";\n");
            }
            sb.append("}\n");
            return sb.toString();
        }
    }

    public static class Block {
        public LabelStmt label; 
        public ArrayList<Stmt> stmts;
        public ArrayList<Block> incoming = new ArrayList<>();
        public int postorderIndex;
        public Block out;
        public Block outCond;

        public Block(List<Stmt> stmts) {
            this.stmts = new ArrayList<>(stmts);
        }

        public boolean isTerminal() {
            return !stmts.isEmpty() && stmts.get(stmts.size() - 1) instanceof Ir3.ReturnStmt;
        }

        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            if (label != null)
                sb.append(label.prettyPrint(indent));
            for (Stmt stmt : stmts) {
                sb.append(stmt.prettyPrint(indent));
            }
            return sb.toString();
        }

        public List<Block> getOutList() {
            ArrayList<Block> outList = new ArrayList<>();
            if (out != null)
                outList.add(out);
            if (outCond != null)
                outList.add(outCond);
            return outList;
        }

        @Override
        public String toString() {
            return "Block(" + (label != null ? label.name : "???") + ")";
        }
    }

    public static abstract class Stmt {
        public abstract String prettyPrint(int indent);
        public abstract List<Ir3.RetVal> getRetVals();

        public ArrayList<Ir3.Var> getUses() {
            ArrayList<Ir3.Var> out = new ArrayList<>();
            for (Ir3.RetVal rv : getRetVals()) {
                if (!(rv instanceof Ir3.VarRetVal))
                    continue;
                out.add(((Ir3.VarRetVal) rv).v);
            }
            return out;
        }

        public List<Ir3.Var> getDefs() {
            return Collections.emptyList();
        }

        public void setDef(int i, Ir3.Var v) {
            throw new IndexOutOfBoundsException();
        }
    }
    
    public static abstract class JumpStmt extends Stmt {
        public LabelStmt label;
    }
    
    public static class LabelStmt extends Stmt {
        public String name;

        public LabelStmt(String name) {
            this.name = name;
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            return name + ":\n";
        }
    }
    
    public static class CmpStmt extends JumpStmt {
        public CondOp op;
        public RetVal a;
        public RetVal b;

        public CmpStmt(CondOp op, RetVal a, RetVal b, LabelStmt label) {
            this.op = op;
            this.a = a;
            this.b = b;
            this.label = label;
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Arrays.asList(a, b);
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append("if (").append(a.prettyPrint()).append(" ").append(op.getSym()).append(" ").
            	append(b.prettyPrint()).append(") goto ").append(label == null ? "[Null]" : label.name).append(";\n");
            return sb.toString();
        }
    }
    public static class GotoStmt extends JumpStmt {
        public GotoStmt(LabelStmt label) {
            this.label = label;
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append("goto ").append(label == null ? "[Null]" : label.name).append(";\n");
            return sb.toString();
        }
    }
    public static class ReadlnStmt extends Stmt {
        private ArrayList<Ir3.Var> defs = new ArrayList<>();

        public ReadlnStmt(Var dst) {
            defs.add(dst);
        }

        public Ir3.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir3.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir3.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append("readln(").append(defs.get(0).prettyPrint()).append(");\n");
            return sb.toString();
        }
    }
    
    public static class PrintlnStmt extends Stmt {
        public RetVal rv;

        public PrintlnStmt(RetVal rv) {
            this.rv = rv;
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Arrays.asList(rv);
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append("println(").append(rv.prettyPrint()).append(");\n");
            return sb.toString();
        }
    }
    public static class BinaryStmt extends Stmt {
        private ArrayList<Ir3.Var> defs = new ArrayList<>();
        public BinaryOp op;
        public RetVal operand1;
        public RetVal operand2;

        public BinaryStmt(Var dst, BinaryOp op, RetVal op1, RetVal op2) {
            defs.add(dst);
            this.op = op;
            this.operand1 = op1;
            this.operand2 = op2;
        }

        public Ir3.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir3.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir3.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Arrays.asList(operand1, operand2);
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(getDst().prettyPrint()).append(" = ").append(operand1.prettyPrint()).append(" ").
            	append(op.getSym()).append(" ").append(operand2.prettyPrint()).append(";\n");
            return sb.toString();
        }
    }
    public static class UnaryStmt extends Stmt {
        private ArrayList<Ir3.Var> defs = new ArrayList<>();
        public Ir3.UnaryOp op;
        public RetVal a;

        public UnaryStmt(Var dst, Ir3.UnaryOp op, RetVal a) {
            defs.add(dst);
            this.op = op;
            this.a = a;
        }

        public Ir3.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir3.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir3.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Arrays.asList(a);
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(getDst().prettyPrint()).append(" = ").append(op.getSym()).append(" ").append(a.prettyPrint()).append(";\n");
            return sb.toString();
        }
    }
    
    public static class FieldAccessStmt extends Stmt {
        private ArrayList<Ir3.Var> defs = new ArrayList<>();
        public RetVal target;
        public String field;

        public FieldAccessStmt(Var dst, RetVal target, String field) {
            defs.add(dst);
            this.target = target;
            this.field = field;
        }

        public Ir3.Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir3.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir3.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Arrays.asList(target);
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(getDst().prettyPrint()).append(" = ").append(target.prettyPrint()).append(".").append(field).append(";\n");
            return sb.toString();
        }
    }
    
    public static class FieldAssignStmt extends Stmt {
        public Var dst;
        public String field;
        public RetVal src;

        public FieldAssignStmt(Var dst, String field, RetVal src) {
            this.dst = dst;
            this.field = field;
            this.src = src;
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Arrays.asList(src);
        }

        @Override
        public ArrayList<Ir3.Var> getUses() {
            ArrayList<Ir3.Var> out = super.getUses();
            out.add(dst);
            return out;
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(dst.prettyPrint()).append(".").append(field).append(" = ").append(src.prettyPrint()).append(";\n");
            return sb.toString();
        }
    }
    
    public static class AssignStmt extends Stmt {
        private ArrayList<Ir3.Var> defs = new ArrayList<>();
        public RetVal src;

        public AssignStmt(Var dest, RetVal src) {
            defs.add(dest);
            this.src = src;
        }

        public Ir3.Var getDest() {
            return defs.get(0);
        }

        @Override
        public List<Ir3.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir3.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Arrays.asList(src);
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(getDest().prettyPrint()).append(" = ").append(src.prettyPrint()).append(";\n");
            return sb.toString();
        }
    }
    public static class ReturnStmt extends Stmt {
        public RetVal rv;

        public ReturnStmt(RetVal rv) {
            this.rv = rv;
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return rv == null ? Collections.emptyList() : Arrays.asList(rv);
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append("return");
            if (rv != null) {
                sb.append(" ").append(rv.prettyPrint());
            }
            sb.append(";\n");
            return sb.toString();
        }
    }
    public static abstract class CallStmt extends Stmt {
        private ArrayList<Ir3.Var> defs = new ArrayList<>();
        public ArrayList<RetVal> args;

        protected CallStmt(Var dst) {
            defs.add(dst);
        }

        public Ir3.Var getDest() {
            return defs.get(0);
        }

        public void setDst(Var v) {
            setDef(0, v);
        }

        @Override
        public List<Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir3.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<RetVal> getRetVals() {
            return args;
        }
    }
    public static class MethodCallStmt extends CallStmt {
        public Meth meth;

        public MethodCallStmt(Var dst, Meth meth, ArrayList<RetVal> args) {
            super(dst);
            this.meth = meth;
            this.args = args;
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            if (getDest() != null) {
                sb.append(getDest().prettyPrint()).append(" = ");
            }
            sb.append(meth.name).append("(");
            int i = 0;
            for (RetVal rv : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(rv.prettyPrint());
            }
            sb.append(");\n");
            return sb.toString();
        }
    }
    public static class ExternCallStmt extends CallStmt {
        public String target;

        public ExternCallStmt(Var dst, String target, ArrayList<RetVal> args) {
            super(dst);
            this.target = target;
            this.args = args;
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            if (getDest() != null) {
                sb.append(getDest().prettyPrint()).append(" = ");
            }
            sb.append(target).append("(");
            int i = 0;
            for (RetVal rv : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(rv.prettyPrint());
            }
            sb.append(");\n");
            return sb.toString();
        }
    }
    public static class CallPrepStmt extends Stmt {
        public ArrayList<Ir3.Var> defs = new ArrayList<>();
        public ArrayList<Ir3.Var> srcs = new ArrayList<>();
        //public int numArgs;

        @Override
        public List<Ir3.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Var v) {
            defs.set(i, v);
        }

        @Override
        public ArrayList<Ir3.Var> getUses() {
            return new ArrayList<>(srcs);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            int i = 0;
            for (Var v : defs) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(v.prettyPrint());
            }
            sb.append(" = ");
            i = 0;
            for (Var v : srcs) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(v.prettyPrint());
            }
            sb.append(";\n");
            return sb.toString();
        }
    }
    public static class NewStmt extends Stmt {
        private ArrayList<Var> defs = new ArrayList<>();
        public Data data;

        public NewStmt(Var dst, Data data) {
            defs.add(dst);
            this.data = data;
        }

        public Var getDest() {
            return defs.get(0);
        }

        @Override
        public List<Ir3.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Ir3.Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(getDest().prettyPrint()).append(" = ").append("new ").append(data.cname).append("();\n");
            return sb.toString();
        }
    }
    public static class PhiStmt extends Stmt {
        private ArrayList<Var> defs = new ArrayList<>();
        public Var originalVar;
        public ArrayList<Var> args;
        public boolean memory; // True if the definition starts out spilled, and needs to then be reloaded

        public PhiStmt(Var dst, int size) {
            defs.add(dst);
            this.originalVar = dst;
            this.args = new ArrayList<>();
            for (int i = 0; i < size; i++)
                this.args.add(null);
        }

        public Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(getDst().prettyPrint()).append(" = ").append(memory ? "PHIMEM" : "PHI").append("(");
            int i = 0;
            for (Var v : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(v == null ? "null" : v.prettyPrint());
            }
            sb.append(");");
            if (memory)
                sb.append(' ').append(getDst().stackSpace);
            sb.append('\n');
            return sb.toString();
        }
    }
    public static class SpillStmt extends Stmt {
        public Var v;

        public SpillStmt(Var v) {
            this.v = v;
        }

        @Override
        public ArrayList<Ir3.Var> getUses() {
            ArrayList<Ir3.Var> out = new ArrayList<>();
            out.add(v);
            return out;
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append("SPILL(").append(v.prettyPrint()).append(", ").append(v.stackSpace).append(");\n");
            return sb.toString();
        }
    }
    public static class ReloadStmt extends Stmt {
        private ArrayList<Var> defs = new ArrayList<>();

        public ReloadStmt(Var dst) {
            defs.add(dst);
        }

        public Var getDst() {
            return defs.get(0);
        }

        @Override
        public List<Ir3.Var> getDefs() {
            return Collections.unmodifiableList(defs);
        }

        @Override
        public void setDef(int i, Var v) {
            defs.set(i, v);
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append(getDst().prettyPrint()).append(" = RELOAD(").append(getDst().stackSpace).append(");\n");
            return sb.toString();
        }
    }
    public static class StackArgument extends Stmt {
        public Var v;
        public int index;

        public StackArgument(Var v, int index) {
            this.v = v;
            this.index = index;
        }

        @Override
        public ArrayList<Ir3.Var> getUses() {
            ArrayList<Ir3.Var> out = new ArrayList<>();
            out.add(v);
            return out;
        }

        @Override
        public List<Ir3.RetVal> getRetVals() {
            return Collections.emptyList();
        }

        @Override
        public String prettyPrint(int indent) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indent);
            sb.append("STACKARG(").append(v.prettyPrint()).append(", ").append(index).append(");\n");
            return sb.toString();
        }
    }

    public static abstract class RetVal {
        public abstract String prettyPrint();

        public abstract Ast.Typ getTyp();
    }
    public static class StringRetVal extends RetVal {
        public final String str;

        public StringRetVal(String str) {
            this.str = str;
        }

        @Override
        public Ast.Typ getTyp() {
            return new Ast.StringTyp();
        }

        @Override
        public String prettyPrint() {
            return "\"" + Ast.escape(str) + "\"";
        }
    }
    public static class IntRetVal extends RetVal {
        public final int i;

        public IntRetVal(int i) {
            this.i = i;
        }

        @Override
        public Ast.Typ getTyp() {
            return new Ast.IntTyp();
        }

        @Override
        public String prettyPrint() {
            return "" + i;
        }
    }
    public static class BoolRetVal extends RetVal {
        public final boolean b;

        public BoolRetVal(boolean b) {
            this.b = b;
        }

        @Override
        public Ast.Typ getTyp() {
            return new Ast.BoolTyp();
        }

        @Override
        public String prettyPrint() {
            return b == true ? "true" : "false";
        }
    }
    public static class NullRetVal extends RetVal {
        @Override
        public Ast.Typ getTyp() {
            return new Ast.NullTyp();
        }

        @Override
        public String prettyPrint() {
            return "NULL";
        }
    }
    public static class VarRetVal extends RetVal {
        public Var v;

        public VarRetVal(Var v) {
            this.v = v;
        }

        @Override
        public Ast.Typ getTyp() {
            return v.typ;
        }

        @Override
        public String prettyPrint() {
            return v.prettyPrint();
        }
    }

    public static enum UnaryOp {
        NEG("-");

        private final String sym;

        private UnaryOp(String sym) {
            this.sym = sym;
        }

        public String getSym() {
            return sym;
        }
    }

    public static enum BinaryOp {
        PLUS("+"),
        MINUS("-"),
        MUL("*"),
        DIV("/"),
        RSB("RSB");

        private final String sym;

        private BinaryOp(String sym) {
            this.sym = sym;
        }

        public String getSym() {
            return sym;
        }
    }

    public static enum CondOp {
        LT("<"),
        GT(">"),
        LE("<="),
        GE(">="),
        EQ("=="),
        NE("!=");

        private final String sym;

        private CondOp(String sym) {
            this.sym = sym;
        }

        public String getSym() {
            return sym;
        }
    }
}
