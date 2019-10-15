

import java_cup.runtime.ComplexSymbolFactory;
import java.util.*;

public class Ast {

	public interface Printable {
		String prettyPrint(int indents);
	}

    public static void indent(StringBuilder sb, int indent) {
        while (indent > 0) {
            sb.append("\t");
            indent--;
        }
    }
    
    public static class Location extends ComplexSymbolFactory.Location  {
    
        public Location(int line, int column) {
            super(line,column);
        }
        @Override
        public String toString() {
            return "{\"line\": " + getLine() + ", \"column\": " + getColumn() + "}";
        }
    }

    public interface Locatable {
        Location getPosition();
        void setPosition(Location position);
    }

    // A program consists on a list of classes
    public static class Program implements Printable {
        public final List<Clazz> clazzes;

        public Program(List<Clazz> clazzes) {
            this.clazzes = Collections.unmodifiableList(new ArrayList<>(clazzes));
        }

        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            for (Clazz clazz : clazzes) {
                sb.append(clazz.prettyPrint(indents));
            }
            return sb.toString();
        }
    }

    // A class consist on a name, a list of variables and a list of methods
    public static class Clazz implements Printable, Locatable {
        public final String cname;
        public final List<VarDecl> varDecls;
        public final List<Meth> meths;
        private Location position;

        public Clazz(String cname, List<VarDecl> varDecls, List<Meth> meths) {
            this.cname = cname;
            this.varDecls = Collections.unmodifiableList(new ArrayList<>(varDecls));
            this.meths = Collections.unmodifiableList(new ArrayList<>(meths));
        }
        
        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append("class ").append(cname).append(" {\n\n");
            for (VarDecl varDecl : varDecls) {
            	indent(sb, indents+1);
                sb.append(varDecl.typ.prettyPrint(0)).append(" ").append(varDecl.name);
                sb.append(";\n");
            }
            if (varDecls.size() > 0) {
            	sb.append("\n");
            }
            for (Meth meth : meths) {
                sb.append(meth.prettyPrint(indents + 1)).append("\n");
            }
            indent(sb, indents);
            sb.append("}\n\n");
            return sb.toString();
        }
        
        @Override
        public Location getPosition() {
            return position;
        }

        @Override
        public void setPosition(Location position) {
            this.position = position;
        }
    }

    public static class VarDecl implements Locatable {
        public final Typ typ;
        public final String name;
        private Location position;

        public VarDecl(Typ typ, String name) {
            this.typ = typ;
            this.name = name;
        }
        
        @Override
        public Location getPosition() {
            return position;
        }

        @Override
        public void setPosition(Location position) {
            this.position = position;
        }
    }

    public static class Meth implements Printable, Locatable {
        public final Typ retTyp;
        public final String name;
        public final List<VarDecl> args;
        public final List<VarDecl> vars;
        public final List<Stmt> stmts;
        private Location position;

        public Meth(Typ retTyp, String name, List<VarDecl> args, List<VarDecl> vars, List<Stmt> stmts) {
            this.retTyp = retTyp;
            this.name = name;
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
            this.vars = Collections.unmodifiableList(new ArrayList<>(vars));
            this.stmts = Collections.unmodifiableList(new ArrayList<>(stmts));
        }

        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append(retTyp.prettyPrint(0)).append(" ").append(name).append(" (");
            int i = 0;
            for (VarDecl varDecl : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(varDecl.typ.prettyPrint(0)).append(" ").append(varDecl.name);
            }
            sb.append(") {\n");
            for (VarDecl varDecl : vars) {
            	indent(sb, indents+1);
                sb.append(varDecl.typ.prettyPrint(0)).append(" ").append(varDecl.name);
                sb.append(";\n");
            }
            sb.append("\n");
            for (Stmt stmt : stmts) {
                sb.append(stmt.prettyPrint(indents+1));
            }
			indent(sb, indents);
			sb.append("}\n");
            return sb.toString();
        }
        
        @Override
        public Location getPosition() {
            return position;
        }

        @Override
        public void setPosition(Location position) {
            this.position = position;
        }
    }

    public static abstract class Typ implements Printable {
        public abstract boolean isSubtypeOrEquals(Typ x);
    }

    public static class IntTyp extends Typ {

        public String prettyPrint(int indents) {
            return "Int";
        }
        
        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }

    public static class BoolTyp extends Typ {

        public String prettyPrint(int indents) {
            return "Bool";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }
    public static class StringTyp extends Typ {

        public String prettyPrint(int indents) {
            return "String";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }

    public static class VoidTyp extends Typ {

        public String prettyPrint(int indents) {
            return "Void";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }

    public static class NullTyp extends Typ {

        public String prettyPrint(int indents) {
            return "Null";
        }

        @Override
        public int hashCode() {
            return getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return true;
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            if (equals(o))
                return true;

            return o instanceof StringTyp || o instanceof ClazzTyp;
        }
    }

    public static class ClazzTyp extends Typ {
        public final String cname;

        public ClazzTyp(String cname) {
            this.cname = cname;
        }
        
        public String prettyPrint(int indents) {
            return cname;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getClass(), cname);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return cname.equals(((ClazzTyp)o).cname);
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }

    public static class FuncTyp extends Typ {
        public final List<Typ> argTyps;
        public final Typ retTyp;
        public final Ast.Meth meth;

        public FuncTyp(List<Typ> argTyps, Typ retTyp, Ast.Meth meth) {
            this.argTyps = Collections.unmodifiableList(new ArrayList<>(argTyps));
            this.retTyp = retTyp;
            this.meth = meth;
        }

        public FuncTyp(Ast.Meth meth) {
            ArrayList<Ast.Typ> argTyps = new ArrayList<>();
            for (Ast.VarDecl varDecl : meth.args)
                argTyps.add(varDecl.typ);

            this.argTyps = Collections.unmodifiableList(argTyps);
            this.retTyp = meth.retTyp;
            this.meth = meth;
        }
        
        public boolean canCallWith(List<Typ> argTyps) {
            if (argTyps.size() != this.argTyps.size())
                return false;
            for (int i = 0; i < this.argTyps.size(); i++) {
                if (!argTyps.get(i).isSubtypeOrEquals(this.argTyps.get(i)))
                    return false;
            }
            return true;
        }

        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int i = 0;
            for (Typ argTyp : argTyps) {
                if (i++ > 0)
                    sb.append(" * ");
                sb.append(argTyp);
            }
            sb.append(" -> ");
            sb.append(retTyp);
            sb.append(" ]");
            return sb.toString();
        }
        
        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }

    public static class PolyFuncTyp extends Typ {
        public final List<FuncTyp> funcTyps;

        public PolyFuncTyp(List<FuncTyp> funcTyps) {
            this.funcTyps = Collections.unmodifiableList(new ArrayList<>(funcTyps));
        }
        
        public List<FuncTyp> candidates(List<Typ> argTyps) {
            ArrayList<FuncTyp> out = new ArrayList<>();
            for (FuncTyp funcTyp : funcTyps) {
                if (funcTyp.canCallWith(argTyps))
                    out.add(funcTyp);
            }
            return out;
        }

        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (FuncTyp funcTyp : funcTyps) {
                if (i++ > 0)
                    sb.append(" | ");
                sb.append(funcTyp);
            }
            sb.append("]");
            return sb.toString();
        }

        @Override
        public int hashCode() {
            return Objects.hash(funcTyps);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            return funcTyps.equals(((PolyFuncTyp)o).funcTyps);
        }

        @Override
        public boolean isSubtypeOrEquals(Typ o) {
            return equals(o);
        }
    }

    public static enum UnaryOp {
        NEG("-", "NEG", 5),
        LNOT("!", "LNOT", 5); 

        private final String sym;
        private final String ident;
        private final int prec;

        private UnaryOp(String sym, String ident, int prec) {
            this.sym = sym;
            this.ident = ident;
            this.prec = prec;
        }

        public String getSym() {
            return sym;
        }

        public int getPrec() {
            return prec;
        }
    }

    public static enum BinaryOp {
        PLUS("+", "PLUS", 3), // Arithmetic +
        MINUS("-", "MINUS", 3), // Arithmetic -
        MUL("*", "MUL", 4), // Arithmetic *
        DIV("/", "DIV", 4), // Arithmetic /
        LT("<", "LT", 2), // <
        GT(">", "GT", 2), // >
        LE("<=", "LE", 2), // <=
        GE(">=", "GE", 2), // ?=
        EQ("==", "EQ", 2), // ==
        NE("!=", "NE", 2), // !=
        LAND("&&", "LAND", 1), // Logical &&
        LOR("||", "LOR", 0); // Logical ||

        private final String sym;
        private final String ident;
        private final int prec;

        private BinaryOp(String sym, String ident, int prec) {
            this.sym = sym;
            this.ident = ident;
            this.prec = prec;
        }

        public String getSym() {
            return sym;
        }

        public int getPrec() {
            return prec;
        }
    }

    public static abstract class Expr implements Printable, Locatable {
        public Typ typ;
        private Location position;
        
        @Override
        public Location getPosition() {
            return position;
        }

        @Override
        public void setPosition(Location position) {
            this.position = position;
        }
    }

    public static class StringLitExpr extends Expr {
        public final String str;

        public StringLitExpr(String str) {
            this.str = str;
        }

        public String prettyPrint(int indents) {
            return "\"" + escape(str) + "\"";
        }
    }

    public static class IntLitExpr extends Expr {
        public final int i;

        public IntLitExpr(int i) {
            this.i = i;
        }

        public String prettyPrint(int indents) {
            return "" + i;
        }
    }

    public static class BoolLitExpr extends Expr {
        public final boolean b;

        public BoolLitExpr(boolean b) {
            this.b = b;
        }

        public String prettyPrint(int indents) {
            return b ? "true" : "false";
        }
    }

    public static class NullLitExpr extends Expr {

        public String prettyPrint(int indents) {
            return "null";
        }
    }

    public static class IdentExpr extends Expr {
        public final String name;
        public VarDecl v; // Resolved VarDecl

        public IdentExpr(String name) {
            this.name = name;
        }

        public String prettyPrint(int indents) {
            return name;
        }
    }

    public static class ThisExpr extends Expr {

        public String prettyPrint(int indents) {
            return "this";
        }
    }

    public static class UnaryExpr extends Expr {
        public final UnaryOp op;
        public final Expr expr;

        public UnaryExpr(UnaryOp op, Expr expr) {
            this.op = op;
            this.expr = expr;
        }

        public String prettyPrint(int indents) {
            return "" + op.getSym() + expr.prettyPrint(0);
        }
    }

    public static class BinaryExpr extends Expr {
        public final BinaryOp op;
        public final Expr lexp;
        public final Expr rexp;

        public BinaryExpr(BinaryOp op, Expr lexp, Expr rexp) {
            this.op = op;
            this.lexp = lexp;
            this.rexp = rexp;
        }

        public String prettyPrint(int indents) {
            return lexp.prettyPrint(0) + " " + op.getSym() + " " + rexp.prettyPrint(0);
        }
    }

    public static class DotExpr extends Expr {
        public final Expr target;
        public final String ident;

        public DotExpr(Expr target, String ident) {
            this.target = target;
            this.ident = ident;
        }

        public String prettyPrint(int indents) {
            return target.prettyPrint(0) + "." + ident;
        }
    }

    public static class CallExpr extends Expr {
        public final Expr target;
        public final List<Expr> args;
        public Meth meth; // Resolved meth

        public CallExpr(Expr target, List<Expr> args) {
            this.target = target;
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
        }

        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            sb.append(target.prettyPrint(0)).append("(");
            int i = 0;
            for (Expr expr : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(expr.prettyPrint(0));
            }
            sb.append(")");
            return sb.toString();
        }
    }

    public static class NewExpr extends Expr {
        public final String cname;

        public NewExpr(String cname) {
            this.cname = cname;
        }

        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            sb.append("new").append(cname).append("()");
            return sb.toString();
        }

    }

    public static abstract class Stmt implements Printable, Locatable {
        private Location position;
    	
    	public String prettyPrint(int indents){
    		return "";
    	}
    	
    	 @Override
         public Location getPosition() {
             return position;
         }

         @Override
         public void setPosition(Location position) {
             this.position = position;
         }
    }
    
    

    public static class IfStmt extends Stmt {
        public final Expr cond;
        public final List<Stmt> thenStmts;
        public final List<Stmt> elseStmts;

        public IfStmt(Expr cond, List<Stmt> thenStmts, List<Stmt> elseStmts) {
            this.cond = cond;
            this.thenStmts = Collections.unmodifiableList(new ArrayList<>(thenStmts));
            this.elseStmts = Collections.unmodifiableList(new ArrayList<>(elseStmts));
        }
	
        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append("if (").append(cond.prettyPrint(0)).append(") {\n");
            for (Stmt stmt : thenStmts) {
                sb.append(stmt.prettyPrint(indents + 1));
            }
            indent(sb, indents);
            sb.append("} else {\n");
            for (Stmt stmt : elseStmts) {
                sb.append(stmt.prettyPrint(indents + 1));
            }
            indent(sb, indents);
            sb.append("}\n");
            return sb.toString();
        }
    }

    public static class WhileStmt extends Stmt {
        public final Expr cond;
        public final List<Stmt> stmts;

        public WhileStmt(Expr cond, List<Stmt> stmts) {
            this.cond = cond;
            this.stmts = Collections.unmodifiableList(new ArrayList<>(stmts));
        }

        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append("while (").append(cond.prettyPrint(0)).append(") {\n");
            for (Stmt stmt : stmts) {
                sb.append(stmt.prettyPrint(indents + 1));
            }
            indent(sb, indents);
            sb.append("}\n");
            return sb.toString();
        }
    }

    public static class ReadlnStmt extends Stmt {
        public final String ident;
        public VarDecl v; // Resolved ident

        public ReadlnStmt(String ident) {
            this.ident = ident;
        }
        
        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append("readln(").append(ident).append(");\n");
            return sb.toString();
        }
    }

    public static class PrintlnStmt extends Stmt {
        public final Expr expr;

        public PrintlnStmt(Expr expr) {
            this.expr = expr;
        }

		@Override
        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append("println(").append(expr.prettyPrint(0)).append(");\n");
            return sb.toString();
        }
    }

    public static class VarAssignStmt extends Stmt {
        public final String lhs;
        public final Expr rhs;
        public VarDecl lhsVar; // Resolved lhs ident

        public VarAssignStmt(String lhs, Expr rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

		@Override
        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append(lhs).append(" = ").append(rhs.prettyPrint(0)).append(";\n");
            return sb.toString();
        }
    }

    public static class FieldAssignStmt extends Stmt {
        public final Expr lhsExpr;
        public final String lhsField;
        public final Expr rhsExpr;

        public FieldAssignStmt(Expr lhsExpr, String lhsField, Expr rhs) {
            this.lhsExpr = lhsExpr;
            this.lhsField = lhsField;
            this.rhsExpr = rhs;
        }

		@Override
        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append(lhsExpr.prettyPrint(0)).append(".").append(lhsField);
            sb.append(" = ");
            sb.append(rhsExpr.prettyPrint(0)).append(";\n");
            return sb.toString();
        }
    }

    public static class ReturnStmt extends Stmt {
        public final Expr expr;

        public ReturnStmt(Expr expr) {
            this.expr = expr;
        }

		@Override
        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append("return");
            if (expr != null) {
                sb.append(' ').append(expr.prettyPrint(0));
            }
            sb.append(";\n");
            return sb.toString();
        }
    }

    public static class CallStmt extends Stmt {
        public final Expr target;
        public final List<Expr> args;
        public Meth targetMeth; // Resolved target meth

        public CallStmt(Expr target, List<Expr> args) {
            this.target = target;
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
        }

		@Override
        public String prettyPrint(int indents) {
            StringBuilder sb = new StringBuilder();
            indent(sb, indents);
            sb.append(target.prettyPrint(0)).append("(");
            int i = 0;
            for (Expr arg : args) {
                if (i++ > 0)
                    sb.append(", ");
                sb.append(arg.prettyPrint(0));
            }
            sb.append(");\n");
            return sb.toString();
        }
    }

    public static String escape(String str) {
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (c == '\'')
                sb.append("\\'");
            else if (c == '\"')
                sb.append("\\\"");
            else if (c == '\r')
                sb.append("\\r");
            else if (c == '\n')
                sb.append("\\n");
            else if (c == '\t')
                sb.append("\\t");
            else if (c == '\\')
                sb.append("\\\\");
            else if (c == '\b')
                sb.append("\\b");
            else if (c < 32 || c >= 127)
                sb.append(String.format("\\x%02x", (int)c));
            else
                sb.append(c);
        }
        return sb.toString();
    }

    public static String doParen(String out, int requiredPrec, int currentPrec) {
        return currentPrec >= requiredPrec ? "(" + out + ")" : out;
    }
}
