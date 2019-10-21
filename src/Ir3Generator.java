import java.util.*;
import java.io.*;

public class Ir3Generator {
    private ArrayList<Ir3.Data> datas = new ArrayList<>();
    private ArrayList<Ir3.Meth> meths = new ArrayList<>();

    private HashMap<String, Ir3.Data> dataMap = new HashMap<>();

    private IdentityHashMap<Ast.VarDecl, Ir3.Var> decl2Var; // For args and locals
    private IdentityHashMap<Ast.VarDecl, String> decl2Field; // For fields

    // Filed with ALL methods of ALL classes
    private IdentityHashMap<Ast.Meth, Ir3.Meth> meth2Meth = new IdentityHashMap<>();

    // The var used for "this"
    private Ir3.Var thisVar;
    private ArrayList<Ir3.Var> ir3Locals; // Locals and temporaries
    private int tmpCounter;
    private int labelCounter;

    public static void main(String[] args) throws Exception {
        Ast.Program prog = parser.parse(new BufferedReader(new FileReader(args[0])));
        try {
            StaticChecker.run(prog);
        } catch (StaticChecker.SemanticErrors e) {
            for (StaticChecker.SemanticError err : e.getErrors()) {
                System.err.println("error:" + err.location + ": " + err.getMessage());
            }
            System.exit(1);
        }
        Ir3.Prog irProg = run(prog);
        System.out.print(irProg.prettyPrint(0));
    }

    public static Ir3.Prog run(Ast.Program prog) {
        Ir3Generator translator = new Ir3Generator();

        // Create data
        for (Ast.Clazz clazz : prog.clazzes) {
            ArrayList<Ir3.DataField> fields = new ArrayList<>();
            for (Ast.VarDecl varDecl : clazz.varDecls) {
                fields.add(new Ir3.DataField(varDecl.typ, varDecl.name));
            }
            Ir3.Data irData = new Ir3.Data(clazz.cname, fields);
            translator.datas.add(irData);
            translator.dataMap.put(clazz.cname, irData);
        }

        // Fill in stub methods, while generating unique names for them
        for (Ast.Clazz clazz : prog.clazzes) {
            Set<String> processedNames = new HashSet<>();
            for (Ast.Meth meth : clazz.meths) {
                String name;
                if (meth.name == "main") {
                    name = "main";
                } else if (processedNames.contains(meth.name)) {
                    int ctr = 1;
                    while (processedNames.contains(meth.name + "_" + ctr))
                        ctr++;
                    String newName = meth.name + "_" + ctr;
                    processedNames.add(newName);
                    name = "%" + clazz.cname + "_" + newName;
                } else {
                    processedNames.add(meth.name);
                    name = "%" + clazz.cname + "_" + meth.name;
                }

                translator.meth2Meth.put(meth, new Ir3.Meth(meth.retTyp, name));
            }
        }

        for (Ast.Clazz clazz : prog.clazzes) {
            translator.runClass(clazz);
        }

        return new Ir3.Prog(translator.datas, translator.meths);
    }

    private void runClass(Ast.Clazz clazz) {
        // save all the class variable declarations
        decl2Field = new IdentityHashMap<>();
        for (Ast.VarDecl varDecl : clazz.varDecls) {
            decl2Field.put(varDecl, varDecl.name);
        }

        // Process methods
        for (Ast.Meth meth : clazz.meths) {
            Ir3.Meth ir3Meth = meth2Meth.get(meth);
            meths.add(ir3Meth);

            tmpCounter = 0;
            labelCounter = 0;
            decl2Var = new IdentityHashMap<>();
            ArrayList<Ir3.Var> ir3Args = new ArrayList<>();
            ir3Locals = new ArrayList<>();
            Set<String> processedNames = new HashSet<>();

            thisVar = new Ir3.Var(new Ast.ClazzTyp(clazz.cname), "this");
            ir3Args.add(thisVar);

            // Generate arguments
            for (Ast.VarDecl arg : meth.args) {
                Ir3.Var ir3Var = new Ir3.Var(arg.typ, arg.name);
                ir3Args.add(ir3Var);
                decl2Var.put(arg, ir3Var);
                processedNames.add(arg.name);
            }

            // Generate local variables
            for (Ast.VarDecl v : meth.vars) {
                String name = v.name;
                if (processedNames.contains(name)) {
                    int ctr = 2;
                    while (processedNames.contains(name + "__" + ctr))
                        ctr++;
                    String newName = name + "__" + ctr;
                    processedNames.add(newName);
                    processedNames.add(name);
                    name = newName;
                } else {
                    processedNames.add(name);
                }

                Ir3.Var ir3Var = new Ir3.Var(v.typ, name);
                ir3Locals.add(ir3Var);
                decl2Var.put(v, ir3Var);
            }

            // Generate statements

            ArrayList<Ir3.Stmt> ir3Stmts = new ArrayList<>();
            StmtBlock StmtBlock = runStmtBlock(meth.stmts);
            ir3Stmts.addAll(StmtBlock.stmts);
            // If we have jumps, add a label
            if (!StmtBlock.jumps.isEmpty()) {
                Ir3.LabelStmt label = genLabel();
                ir3Stmts.add(label);
                putLabels(StmtBlock.jumps, label);
            }

            // Check for a return at the end
            if (!ir3Stmts.isEmpty() && !(ir3Stmts.get(ir3Stmts.size() - 1) instanceof Ir3.ReturnStmt)) {
                // Add a return
                if (!(meth.retTyp instanceof Ast.VoidTyp))
                    throw new AssertionError("ERR: control reaches end of method with non-void return type");
                ir3Stmts.add(new Ir3.ReturnStmt(null));
            }

            // Complete!
            ir3Meth.args = ir3Args;
            ir3Meth.locals = ir3Locals;
            ir3Meth.blocks = new ArrayList<>();
            ir3Meth.blocks.add(new Ir3.Block(ir3Stmts));
        }
    }

    private StmtBlock runStmtBlock(List<Ast.Stmt> stmts) {
        ArrayList<Ir3.Stmt> ir3Stmts = new ArrayList<>();
        StmtBlock stmtBlock = null;
        
        for (Ast.Stmt stmt : stmts) {
            stmtBlock = runStmt(stmt);
            ir3Stmts.addAll(stmtBlock.stmts);
            if (!stmtBlock.jumps.isEmpty()) {
                Ir3.LabelStmt label = genLabel();
                ir3Stmts.add(label);
                putLabels(stmtBlock.jumps, label);
            }
        }

        ArrayList<Ir3.JumpStmt> jumps = stmtBlock != null ? stmtBlock.jumps : new ArrayList<>();
        return new StmtBlock(ir3Stmts, jumps);
    }

    private StmtBlock runStmt(Ast.Stmt stmt) {
        ArrayList<Ir3.Stmt> ir3Stmts = new ArrayList<>();
        ArrayList<Ir3.JumpStmt> jumps = new ArrayList<>();

        // IF
        if (stmt instanceof Ast.IfStmt) {
            CondBlock condSect = runCond(((Ast.IfStmt) stmt).cond, false);
            StmtBlock thenSect = runStmtBlock(((Ast.IfStmt) stmt).thenStmts);
            StmtBlock elseSect = runStmtBlock(((Ast.IfStmt) stmt).elseStmts);

            ir3Stmts.addAll(condSect.stmts);

            if (!condSect.trueJumps.isEmpty()) {
                Ir3.LabelStmt trueLabel = genLabel();
                ir3Stmts.add(trueLabel);
                putLabels(condSect.trueJumps, trueLabel);
            }

            // Then section
            ir3Stmts.addAll(thenSect.stmts);
            jumps.addAll(thenSect.jumps);

            if (stmtsFlop(thenSect.stmts)) {
                Ir3.GotoStmt thenGoto = new Ir3.GotoStmt(null);
                ir3Stmts.add(thenGoto);
                jumps.add(thenGoto);
            }

            if (!condSect.falseJumps.isEmpty()) {
                Ir3.LabelStmt falseLabel = genLabel();
                ir3Stmts.add(falseLabel);
                putLabels(condSect.falseJumps, falseLabel);
            }

            // Else section
            ir3Stmts.addAll(elseSect.stmts);
            jumps.addAll(elseSect.jumps);

            return new StmtBlock(ir3Stmts, jumps);
            
        // WHILE
        } else if (stmt instanceof Ast.WhileStmt) {
            CondBlock CondBlock = runCond(((Ast.WhileStmt) stmt).cond, false);
            StmtBlock StmtBlock = runStmtBlock(((Ast.WhileStmt) stmt).stmts);

            // First add a label
            Ir3.LabelStmt topLabel = genLabel();
            ir3Stmts.add(topLabel);

            // Then check condition
            ir3Stmts.addAll(CondBlock.stmts);

            // If the conditional check has a true jump, add a label
            if (!CondBlock.trueJumps.isEmpty()) {
                Ir3.LabelStmt trueLabel = genLabel();
                ir3Stmts.add(trueLabel);
                putLabels(CondBlock.trueJumps, trueLabel);
            }

            // add the body
            ir3Stmts.addAll(StmtBlock.stmts);

            // If loop body flop, go back to top
            if (stmtsFlop(StmtBlock.stmts))
                ir3Stmts.add(new Ir3.GotoStmt(topLabel));

            jumps.addAll(CondBlock.falseJumps);
            jumps.addAll(StmtBlock.jumps);
            return new StmtBlock(ir3Stmts, jumps);
            
        //READLN
        } else if (stmt instanceof Ast.ReadlnStmt) {
            Ast.VarDecl var = ((Ast.ReadlnStmt) stmt).v;

            if (decl2Var.containsKey(var)) {
                // Is a local/arg.
                ir3Stmts.add(new Ir3.ReadlnStmt(decl2Var.get(var)));
                return new StmtBlock(ir3Stmts, jumps);
                
            } else if (decl2Field.containsKey(var)) {
                // Is a field.
                Ir3.Var dst = genTemporalVar(var.typ);
                ir3Stmts.add(new Ir3.ReadlnStmt(dst));
                String fName = decl2Field.get(var);
                ir3Stmts.add(new Ir3.FieldAssignStmt(thisVar, fName, new Ir3.VarRetVal(dst)));
                return new StmtBlock(ir3Stmts, jumps);
            } else {
                throw new AssertionError("ERR: no resolved VarDecl with ReadlnStmt");
            }
            
        //PRINTLN
        } else if (stmt instanceof Ast.PrintlnStmt) {
            RetValBlock retVal = runRetVal(((Ast.PrintlnStmt) stmt).expr);
            ir3Stmts.addAll(retVal.stmts);
            ir3Stmts.add(new Ir3.PrintlnStmt(retVal.retVal));
            return new StmtBlock(ir3Stmts, jumps);
            
        //VAR ASSIGNMENT
        } else if (stmt instanceof Ast.VarAssignStmt) {
            Ast.VarDecl leftVarDecl = ((Ast.VarAssignStmt) stmt).lhsVar;

            RetValBlock rightSec = runRetVal(((Ast.VarAssignStmt) stmt).rhs);
            ir3Stmts.addAll(rightSec.stmts);

            if (decl2Var.containsKey(leftVarDecl)) {
                // Is a local/arg.
                ir3Stmts.add(new Ir3.AssignStmt(decl2Var.get(leftVarDecl), rightSec.retVal));
                return new StmtBlock(ir3Stmts, jumps);
            } else if (decl2Field.containsKey(leftVarDecl)) {
                // Is a field.
                ir3Stmts.add(new Ir3.FieldAssignStmt(thisVar, decl2Field.get(leftVarDecl), rightSec.retVal));
                return new StmtBlock(ir3Stmts, jumps);
            } else {
                throw new AssertionError("ERR: no resolved VarDecl with VarAssignStmt");
            }
            
        //FIELD ASSIGNMENT
        } else if (stmt instanceof Ast.FieldAssignStmt) {

            RetValBlock rightSec = runRetVal(((Ast.FieldAssignStmt) stmt).rhsExpr);
            ir3Stmts.addAll(rightSec.stmts);

            RetValBlock leftSec = runRetVal(((Ast.FieldAssignStmt) stmt).lhsExpr);
            ir3Stmts.addAll(leftSec.stmts);

            Ir3.Var leftVar = ((Ir3.VarRetVal) leftSec.retVal).v;
            ir3Stmts.add(new Ir3.FieldAssignStmt(leftVar, ((Ast.FieldAssignStmt) stmt).lhsField, rightSec.retVal));
            return new StmtBlock(ir3Stmts, jumps);
            
        //RETURN 
        } else if (stmt instanceof Ast.ReturnStmt) {
            Ir3.RetVal rv = null;
            if (((Ast.ReturnStmt) stmt).expr != null) {
                RetValBlock exprSec = runRetVal(((Ast.ReturnStmt) stmt).expr);
                ir3Stmts.addAll(exprSec.stmts);
                rv = exprSec.retVal;
            }
            ir3Stmts.add(new Ir3.ReturnStmt(rv));
            return new StmtBlock(ir3Stmts, jumps);
            
        //CALL
        } else if (stmt instanceof Ast.CallStmt) {
            Ast.Expr target = ((Ast.CallStmt) stmt).target;
            
            assert ((Ast.CallStmt) stmt).targetMeth != null;

            Ir3.Meth irMeth = meth2Meth.get(((Ast.CallStmt) stmt).targetMeth);
            assert irMeth != null;
            ArrayList<Ir3.RetVal> argRvals = new ArrayList<>();

            if (target instanceof Ast.IdentExpr) {
                argRvals.add(new Ir3.VarRetVal(thisVar));
                
            } else if (target instanceof Ast.DotExpr) {
                RetValBlock targetSec = runRetVal(((Ast.DotExpr) target).target);
                ir3Stmts.addAll(targetSec.stmts);
                argRvals.add(targetSec.retVal);
            } else {
                throw new AssertionError("ERR");
            }

            for (Ast.Expr argExpr : ((Ast.CallStmt) stmt).args) {
                RetValBlock argSec = runRetVal(argExpr);
                ir3Stmts.addAll(argSec.stmts);
                argRvals.add(argSec.retVal);
            }

            ir3Stmts.add(new Ir3.MethodCallStmt(null, irMeth, argRvals));
            return new StmtBlock(ir3Stmts, jumps);

        } else {
            throw new AssertionError("ERR");
        }
    }

    private RetValBlock runRetVal(Ast.Expr expr) {
    	
        if (expr instanceof Ast.StringLitExpr) {
            return new RetValBlock(new Ir3.StringRetVal(((Ast.StringLitExpr) expr).str), new ArrayList<>());
            
        } else if (expr instanceof Ast.IntLitExpr) {
            return new RetValBlock(new Ir3.IntRetVal(((Ast.IntLitExpr) expr).i), new ArrayList<>());
            
        } else if (expr instanceof Ast.BoolLitExpr) {
            return new RetValBlock(new Ir3.BoolRetVal(((Ast.BoolLitExpr) expr).b), new ArrayList<>());
            
        } else if (expr instanceof Ast.NullLitExpr) {
            return new RetValBlock(new Ir3.NullRetVal(), new ArrayList<>());
            
        } else if (expr instanceof Ast.IdentExpr) {
            Ast.VarDecl varDecl = ((Ast.IdentExpr) expr).v;
            if (decl2Var.containsKey(varDecl)) {
                return new RetValBlock(new Ir3.VarRetVal(decl2Var.get(varDecl)), new ArrayList<>());
            } else if (decl2Field.containsKey(varDecl)) {
                ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
                Ir3.Var temp = genTemporalVar(expr.typ);
                stmts.add(new Ir3.FieldAccessStmt(temp, new Ir3.VarRetVal(thisVar), decl2Field.get(varDecl)));
                return new RetValBlock(new Ir3.VarRetVal(temp), stmts);
            } else {
                throw new AssertionError("ERR: no VarDecl tied to ident");
            }
            
        } else if (expr instanceof Ast.ThisExpr) {
            return new RetValBlock(new Ir3.VarRetVal(thisVar), new ArrayList<>());
            
        } else if (expr instanceof Ast.UnaryExpr) {

            switch (((Ast.UnaryExpr) expr).op) {
            case LNOT: {
                CondBlock CondBlock = runCond(((Ast.UnaryExpr) expr).expr, true);
                ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
                Ir3.Var dst = genTemporalVar(expr.typ);
                Ir3.LabelStmt trueLabel = null, falseLabel = null;
                if (!CondBlock.trueJumps.isEmpty()) {
                    trueLabel = genLabel();
                    putLabels(CondBlock.trueJumps, trueLabel);
                }
                if (!CondBlock.falseJumps.isEmpty()) {
                    falseLabel = genLabel();
                    putLabels(CondBlock.falseJumps, falseLabel);
                }

                stmts.addAll(CondBlock.stmts);

                if (trueLabel != null && falseLabel != null) {
                    Ir3.LabelStmt restLabel = genLabel();
                    stmts.add(trueLabel);
                    stmts.add(new Ir3.AssignStmt(dst, new Ir3.BoolRetVal(true)));
                    stmts.add(new Ir3.GotoStmt(restLabel));
                    stmts.add(falseLabel);
                    stmts.add(new Ir3.AssignStmt(dst, new Ir3.BoolRetVal(false)));
                    stmts.add(restLabel);
                } else if (falseLabel != null) {
                    stmts.add(falseLabel);
                    stmts.add(new Ir3.AssignStmt(dst, new Ir3.BoolRetVal(false)));
                } else if (trueLabel != null) {
                    stmts.add(trueLabel);
                    stmts.add(new Ir3.AssignStmt(dst, new Ir3.BoolRetVal(true)));
                } else {
                    throw new AssertionError("ERR");
                }

                return new RetValBlock(new Ir3.VarRetVal(dst), stmts);
            }
            case NEG: {
                RetValBlock valBlock = runRetVal(((Ast.UnaryExpr) expr).expr);
                Ir3.Var dst = genTemporalVar(expr.typ);
                ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
                stmts.addAll(valBlock.stmts);
                stmts.add(new Ir3.UnaryStmt(dst, Ir3.UnaryOp.NEG, valBlock.retVal));
                return new RetValBlock(new Ir3.VarRetVal(dst), stmts);
            }
            default:
                throw new AssertionError("ERR");
            }
            
        } else if (expr instanceof Ast.BinaryExpr) {

            switch (((Ast.BinaryExpr) expr).op) {
            case LT:
            case GT:
            case LE:
            case GE:
            case EQ:
            case NE:
            case LAND:
            case LOR: {
                CondBlock CondBlock = runCond(expr, false);
                ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
                Ir3.Var dst = genTemporalVar(new Ast.BoolTyp());
                Ir3.LabelStmt tLabel = null, fLabel = null;
                if (!CondBlock.trueJumps.isEmpty()) {
                    tLabel = genLabel();
                    putLabels(CondBlock.trueJumps, tLabel);
                }
                if (!CondBlock.falseJumps.isEmpty()) {
                    fLabel = genLabel();
                    putLabels(CondBlock.falseJumps, fLabel);
                }

                stmts.addAll(CondBlock.stmts);

                if (tLabel != null && fLabel != null) {
                    Ir3.LabelStmt restLabel = genLabel();
                    stmts.add(tLabel);
                    stmts.add(new Ir3.AssignStmt(dst, new Ir3.BoolRetVal(true)));
                    stmts.add(new Ir3.GotoStmt(restLabel));
                    stmts.add(fLabel);
                    stmts.add(new Ir3.AssignStmt(dst, new Ir3.BoolRetVal(false)));
                    stmts.add(restLabel);
                } else if (tLabel != null) {
                    stmts.add(tLabel);
                    stmts.add(new Ir3.AssignStmt(dst, new Ir3.BoolRetVal(true)));
                } else if (fLabel != null) {
                    stmts.add(fLabel);
                    stmts.add(new Ir3.AssignStmt(dst, new Ir3.BoolRetVal(false)));
                } else {
                    throw new AssertionError("ERR");
                }

                return new RetValBlock(new Ir3.VarRetVal(dst), stmts);
            }
            default:
                Ir3.BinaryOp ir3Op;

                switch (((Ast.BinaryExpr) expr).op) {
                case PLUS:
                    ir3Op = Ir3.BinaryOp.PLUS;
                    break;
                case MINUS:
                    ir3Op = Ir3.BinaryOp.MINUS;
                    break;
                case MUL:
                    ir3Op = Ir3.BinaryOp.MUL;
                    break;
                case DIV:
                    ir3Op = Ir3.BinaryOp.DIV;
                    break;
                default:
                    throw new AssertionError("ERR");
                }

                RetValBlock leftSec = runRetVal(((Ast.BinaryExpr) expr).lexp);
                RetValBlock rightSec = runRetVal(((Ast.BinaryExpr) expr).rexp);
                Ir3.Var dst = genTemporalVar(new Ast.IntTyp());
                ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
                stmts.addAll(leftSec.stmts);
                stmts.addAll(rightSec.stmts);
                stmts.add(new Ir3.BinaryStmt(dst, ir3Op, leftSec.retVal, rightSec.retVal));
                return new RetValBlock(new Ir3.VarRetVal(dst), stmts);
            }
            
        } else if (expr instanceof Ast.DotExpr) {
            RetValBlock targetChunk = runRetVal(((Ast.DotExpr) expr).target);
            Ir3.Var temp = genTemporalVar(expr.typ);
            ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
            stmts.addAll(targetChunk.stmts);
            stmts.add(new Ir3.FieldAccessStmt(temp, targetChunk.retVal, ((Ast.DotExpr) expr).ident));
            return new RetValBlock(new Ir3.VarRetVal(temp), stmts);
            
        } else if (expr instanceof Ast.CallExpr) {
            Ast.Expr target = ((Ast.CallExpr) expr).target;
            assert ((Ast.CallExpr) expr).meth != null;

            Ir3.Meth irMeth = meth2Meth.get(((Ast.CallExpr) expr).meth);
            assert irMeth != null;
            ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
            ArrayList<Ir3.RetVal> argRvals = new ArrayList<>();
            if (target instanceof Ast.IdentExpr) {
                // Use our current "this"
                argRvals.add(new Ir3.VarRetVal(thisVar));
            } else if (target instanceof Ast.DotExpr) {
                // Use the lhs of the dotexpr as the "this"
                RetValBlock targetBlock = runRetVal(((Ast.DotExpr) target).target);
                stmts.addAll(targetBlock.stmts);
                argRvals.add(targetBlock.retVal);
            } else {
                throw new AssertionError("ERR");
            }
            // The rest of the args
            for (Ast.Expr argExpr : ((Ast.CallExpr) expr).args) {
                RetValBlock argSec = runRetVal(argExpr);
                stmts.addAll(argSec.stmts);
                argRvals.add(argSec.retVal);
            }
            Ir3.Var temp = genTemporalVar(expr.typ);
            stmts.add(new Ir3.MethodCallStmt(temp, irMeth, argRvals));
            return new RetValBlock(new Ir3.VarRetVal(temp), stmts);
            
        } else if (expr instanceof Ast.NewExpr) {
            Ir3.Var temp = genTemporalVar(new Ast.ClazzTyp(((Ast.NewExpr) expr).cname));
            ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
            stmts.add(new Ir3.NewStmt(temp, dataMap.get(((Ast.NewExpr) expr).cname)));
            return new RetValBlock(new Ir3.VarRetVal(temp), stmts);
        } else {
            throw new AssertionError("ERR");
        }
    }

    private CondBlock runCond(Ast.Expr expr, boolean negation) {
        ArrayList<Ir3.Stmt> stmts = new ArrayList<>();
        ArrayList<Ir3.JumpStmt> trueJumps = new ArrayList<>();
        ArrayList<Ir3.JumpStmt> falseJumps = new ArrayList<>();

        if (expr instanceof Ast.StringLitExpr || expr instanceof Ast.ThisExpr) {
            // Always true (or false)
            Ir3.GotoStmt gotoStmt = new Ir3.GotoStmt(null);
            stmts.add(gotoStmt);
            
            if (negation) falseJumps.add(gotoStmt);
            else trueJumps.add(gotoStmt);
            
            return new CondBlock(stmts, trueJumps, falseJumps);
            
        } else if (expr instanceof Ast.IntLitExpr) {
            Ir3.GotoStmt gotoStmt = new Ir3.GotoStmt(null);
            stmts.add(gotoStmt);
            if (((Ast.IntLitExpr) expr).i == 0) {
            	
                if (negation) trueJumps.add(gotoStmt);
                else falseJumps.add(gotoStmt);
            } else {
            	
                if (negation) falseJumps.add(gotoStmt);
                else trueJumps.add(gotoStmt);
            }
            return new CondBlock(stmts, trueJumps, falseJumps);
            
        } else if (expr instanceof Ast.BoolLitExpr) {
            Ir3.GotoStmt gotoStmt = new Ir3.GotoStmt(null);
            stmts.add(gotoStmt);
            // Always true/false dependending on value
            if (!((Ast.BoolLitExpr) expr).b) {
            	
                if (negation) trueJumps.add(gotoStmt);
                else falseJumps.add(gotoStmt);
            } else {
            	
                if (negation) falseJumps.add(gotoStmt);
                else trueJumps.add(gotoStmt);
            }
            return new CondBlock(stmts, trueJumps, falseJumps);
            
        } else if (expr instanceof Ast.NullLitExpr) {
            Ir3.GotoStmt gotoStmt = new Ir3.GotoStmt(null);
            stmts.add(gotoStmt);
            
            if (negation) trueJumps.add(gotoStmt);
            else falseJumps.add(gotoStmt);
            
            return new CondBlock(stmts, trueJumps, falseJumps);
            
        } else if (expr instanceof Ast.IdentExpr || expr instanceof Ast.DotExpr || expr instanceof Ast.CallExpr || expr instanceof Ast.NewExpr) {
            // Depends on the value
            RetValBlock RetValBlock = runRetVal(expr);
            stmts.addAll(RetValBlock.stmts);
            Ir3.CmpStmt tstStmt = new Ir3.CmpStmt(Ir3.CondOp.NE, RetValBlock.retVal, new Ir3.IntRetVal(0), null);
            stmts.add(tstStmt);
            
            if (negation) falseJumps.add(tstStmt);
            else trueJumps.add(tstStmt);
            
            Ir3.GotoStmt gotoStmt = new Ir3.GotoStmt(null);
            stmts.add(gotoStmt);
            
            if (negation) trueJumps.add(gotoStmt);
            else falseJumps.add(gotoStmt);
            
            return new CondBlock(stmts, trueJumps, falseJumps);
            
        } else if (expr instanceof Ast.UnaryExpr) {

            switch (((Ast.UnaryExpr) expr).op) {
            case LNOT:
                return runCond(((Ast.UnaryExpr) expr).expr, !negation);
            default:
                // Non-logical operator: Depends on the value
                RetValBlock retValBlock = runRetVal(expr);
                stmts.addAll(retValBlock.stmts);
                Ir3.CmpStmt tstStmt = new Ir3.CmpStmt(Ir3.CondOp.NE, retValBlock.retVal, new Ir3.IntRetVal(0), null);
                stmts.add(tstStmt);
                
                if (negation) falseJumps.add(tstStmt);
                else trueJumps.add(tstStmt);
                
                Ir3.GotoStmt gotoStmt = new Ir3.GotoStmt(null);
                stmts.add(gotoStmt);
                
                if (negation) trueJumps.add(gotoStmt);
                else falseJumps.add(gotoStmt);
                
                return new CondBlock(stmts, trueJumps, falseJumps);
            }
        } else if (expr instanceof Ast.BinaryExpr) {
            Ast.BinaryOp op = ((Ast.BinaryExpr) expr).op;

            // Negate the operator
            if (negation) {
                switch (op) {
                case LAND:
                    op = Ast.BinaryOp.LOR;
                    break;
                case LOR:
                    op = Ast.BinaryOp.LAND;
                    break;
                case LT:
                    op = Ast.BinaryOp.GE;
                    break;
                case GT:
                    op = Ast.BinaryOp.LE;
                    break;
                case LE:
                    op = Ast.BinaryOp.GT;
                    break;
                case GE:
                    op = Ast.BinaryOp.LT;
                    break;
                case EQ:
                    op = Ast.BinaryOp.NE;
                    break;
                case NE:
                    op = Ast.BinaryOp.EQ;
                    break;
                }
            }

            switch (op) {
            case LAND: { // &&
                CondBlock lhsSec = runCond(((Ast.BinaryExpr) expr).lexp, negation);
                CondBlock rhsSec = runCond(((Ast.BinaryExpr) expr).rexp, negation);
                // Create a label for rexp
                Ir3.LabelStmt rhsLabel = genLabel();
                putLabels(lhsSec.trueJumps, rhsLabel);
                stmts.addAll(lhsSec.stmts);
                stmts.add(rhsLabel);
                stmts.addAll(rhsSec.stmts);

                trueJumps.addAll(rhsSec.trueJumps);
                falseJumps.addAll(lhsSec.falseJumps);
                falseJumps.addAll(rhsSec.falseJumps);
                return new CondBlock(stmts, trueJumps, falseJumps);
            }
            case LOR: { // ||
                CondBlock lhsSec = runCond(((Ast.BinaryExpr) expr).lexp, negation);
                CondBlock rhsSec = runCond(((Ast.BinaryExpr) expr).rexp, negation);
                // Create a label for rexp
                Ir3.LabelStmt rhsLabel = genLabel();
                putLabels(lhsSec.falseJumps, rhsLabel);
                stmts.addAll(lhsSec.stmts);
                stmts.add(rhsLabel);
                stmts.addAll(rhsSec.stmts);

                trueJumps.addAll(lhsSec.trueJumps);
                trueJumps.addAll(rhsSec.trueJumps);
                falseJumps.addAll(rhsSec.falseJumps);
                return new CondBlock(stmts, trueJumps, falseJumps);
            }
            case LT:
            case GT:
            case LE:
            case GE:
            case EQ:
            case NE: {
                Ir3.CondOp condOp;

                switch (op) {
                case LT:
                    condOp = Ir3.CondOp.LT;
                    break;
                case GT:
                    condOp = Ir3.CondOp.GT;
                    break;
                case LE:
                    condOp = Ir3.CondOp.LE;
                    break;
                case GE:
                    condOp = Ir3.CondOp.GE;
                    break;
                case EQ:
                    condOp = Ir3.CondOp.EQ;
                    break;
                case NE:
                    condOp = Ir3.CondOp.NE;
                    break;
                default:
                    throw new AssertionError("BUG");
                }

                // NOTE: we don't need to negate because the condOp is already negated
                RetValBlock lhsSec = runRetVal(((Ast.BinaryExpr) expr).lexp);
                RetValBlock rhsSec = runRetVal(((Ast.BinaryExpr) expr).rexp);
                stmts.addAll(lhsSec.stmts);
                stmts.addAll(rhsSec.stmts);
                Ir3.CmpStmt cmpStmt = new Ir3.CmpStmt(condOp, lhsSec.retVal, rhsSec.retVal, null);
                stmts.add(cmpStmt);
                trueJumps.add(cmpStmt);
                Ir3.GotoStmt gotoStmt = new Ir3.GotoStmt(null);
                stmts.add(gotoStmt);
                falseJumps.add(gotoStmt);
                return new CondBlock(stmts, trueJumps, falseJumps);
            }
            default: {
                // Non-logical operator: Depends on the value
                RetValBlock RetValBlock = runRetVal(expr);
                stmts.addAll(RetValBlock.stmts);
                Ir3.CmpStmt tstStmt = new Ir3.CmpStmt(Ir3.CondOp.NE, RetValBlock.retVal, new Ir3.IntRetVal(0), null);
                stmts.add(tstStmt);
                
                if (negation) falseJumps.add(tstStmt);
                else trueJumps.add(tstStmt);
                
                Ir3.GotoStmt gotoStmt = new Ir3.GotoStmt(null);
                stmts.add(gotoStmt);
                
                if (negation) trueJumps.add(gotoStmt);
                else falseJumps.add(gotoStmt);
                
                return new CondBlock(stmts, trueJumps, falseJumps);
            }
            }
        } else {
            throw new AssertionError("ERR");
        }
    }

    private Ir3.Var genTemporalVar(Ast.Typ typ) {
        String name = "_t" + tmpCounter;
        tmpCounter++;
        Ir3.Var var = new Ir3.Var(typ, name);
        ir3Locals.add(var);
        return var;
    }

    private Ir3.LabelStmt genLabel() {
        return new Ir3.LabelStmt("L" + labelCounter++);
    }

    private static void putLabels(List<Ir3.JumpStmt> jumps, Ir3.LabelStmt label) {
        for (Ir3.JumpStmt jump : jumps)
            jump.label = label;
    }

    private static boolean stmtsFlop(List<? extends Ir3.Stmt> stmts) {
        if (stmts.isEmpty())
            return true;

        Ir3.Stmt lastStmt = stmts.get(stmts.size() - 1);
        if (lastStmt instanceof Ir3.ReturnStmt || lastStmt instanceof Ir3.GotoStmt)
            return false;

        return true;
    }

    private static class RetValBlock {
        private final Ir3.RetVal retVal;
        private final ArrayList<Ir3.Stmt> stmts;

        private RetValBlock(Ir3.RetVal rv, ArrayList<Ir3.Stmt> stmts) {
            this.retVal = rv;
            this.stmts = stmts;
        }
    }

    private static class CondBlock {
        private final ArrayList<Ir3.Stmt> stmts;
        private final ArrayList<Ir3.JumpStmt> trueJumps;
        private final ArrayList<Ir3.JumpStmt> falseJumps;

        private CondBlock(ArrayList<Ir3.Stmt> stmts, ArrayList<Ir3.JumpStmt> trueJumps, ArrayList<Ir3.JumpStmt> falseJumps) {
            this.stmts = stmts;
            this.trueJumps = trueJumps;
            this.falseJumps = falseJumps;
        }
    }

    private static class StmtBlock {
        private final ArrayList<Ir3.Stmt> stmts;
        private final ArrayList<Ir3.JumpStmt> jumps;

        private StmtBlock(ArrayList<Ir3.Stmt> stmts, ArrayList<Ir3.JumpStmt> jumps) {
            this.stmts = stmts;
            this.jumps = jumps;
        }
    }
}
