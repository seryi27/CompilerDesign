import java.util.*;
import java.io.*;

public class StaticChecker {

    private HashMap<String, ClazzDescr> clazzdescrs = new HashMap<>();

    private StaticChecker() {}
    
    private void initializeChecker(Ast.Program program) throws SemanticErrors {
        ArrayList<SemanticError> errors = new ArrayList<>();

        // Get all class names
        for (Ast.Clazz clazz : program.clazzes) {
            // Check for duplicated class name
            if (clazzExist(clazz.cname)) {
                errors.add(new SemanticError(clazz,
                            "Duplicated class name \"" + clazz.cname
                            + "\", previously defined in "
                            + clazzdescrs.get(clazz.cname).clazz.getPosition()));
                continue;
            }

            ClazzDescr descr = new ClazzDescr(clazz);
            clazzdescrs.put(clazz.cname, descr);
        }

        if (!errors.isEmpty())
            throw new SemanticErrors(errors);

        // Fill in class fields and methods
        // (check for duplicated class fields and method names)
        for (Ast.Clazz clazz : program.clazzes) {
            ClazzDescr descr = clazzdescrs.get(clazz.cname);

            // Process class fields
            for (Ast.VarDecl varDecl : clazz.varDecls) {
                // Validate field type
                if (!isValidType(varDecl.typ)) {
                    errors.add(new SemanticError(varDecl,
                                "invalid field type " + varDecl.typ.prettyPrint(0)));
                }

                // Check for duplicate field names
                if (descr.hasField(varDecl.name)) {
                    errors.add(new SemanticError(varDecl,
                                "duplicated field name \"" + varDecl.name
                                + "\", previously declared in "
                                + descr.fields.get(varDecl.name).getPosition()));
                } else {
                    descr.fields.put(varDecl.name, varDecl);
                }
            }

            // Process class methods
            HashMap<String, HashMap<ArrayList<Ast.Typ>, Ast.Meth>> processedMeths = new HashMap<>();
            for (Ast.Meth meth : clazz.meths) {
                // Validate argument types and check for duplicated argument names
                HashMap<String, Ast.VarDecl> argNames = new HashMap<>();
                ArrayList<Ast.Typ> argTyps = new ArrayList<>();
                for (Ast.VarDecl varDecl : meth.args) {
                    if (argNames.containsKey(varDecl.name)) {
                        errors.add(new SemanticError(varDecl,
                                    "duplicate argument name " + varDecl.name
                                    + ", previously declared in "
                                    + argNames.get(varDecl.name).getPosition()));
                    }

                    if (!isValidType(varDecl.typ)) {
                        errors.add(new SemanticError(varDecl,
                                    "invalid argument type " + varDecl.typ.prettyPrint(0)));
                    }

                    argNames.put(varDecl.name, varDecl);
                    argTyps.add(varDecl.typ);
                }

                if (processedMeths.containsKey(meth.name) && processedMeths.get(meth.name).containsKey(argTyps)) {
                    errors.add(new SemanticError(meth,
                                "duplicated method \"" + meth.name
                                + "\", previously defined in "
                                + processedMeths.get(meth.name).get(argTyps).getPosition()));
                    continue;
                }

                if (!processedMeths.containsKey(meth.name))
                	processedMeths.put(meth.name, new HashMap<>());
                processedMeths.get(meth.name).put(argTyps, meth);
                descr.addMethod(meth.name, meth);
            }
        }

        if (!errors.isEmpty())
            throw new SemanticErrors(errors);
    }

    public static void run(Ast.Program program) throws SemanticErrors {
        StaticChecker pass = new StaticChecker();
        pass.initializeChecker(program);
        ArrayList<SemanticError> errors = new ArrayList<>();
        for (Ast.Clazz clazz : program.clazzes) {
            try {
                pass.runClass(clazz);
            } catch (SemanticErrors sError) {
                errors.addAll(sError.getErrors());
            }
        }
        if (!errors.isEmpty())
            throw new SemanticErrors(errors);
    }

    public static void main(String[] args) throws Exception {
        Ast.Program prog = Parser.parse(new BufferedReader(new FileReader(args[0])));
        try {
            run(prog);
        } catch (SemanticErrors e) {
            for (SemanticError err : e.getErrors()) {
                System.err.println("error:" + err.location + ": " + err.getMessage());
            }
            System.exit(1);
        }
    }

    private void runClass(Ast.Clazz clazz) throws SemanticErrors {
        // [CDecl]
        ClazzDescr clazzdescr = getClazz(clazz.cname);

        // Set up env
        // Fields and methods are guaranteed to have unique names,
        // although a field can have the same name as a method.
        // In that case, the field overrides the method.
        Environment env = new Environment();
        // Methods first, so that fields override methods
        for (String methodName : clazzdescr.getMethodNames())
            env.put(methodName, clazzdescr.getMethodTyp(methodName), null);

        // Fields next
        for (String fieldName : clazzdescr.getFieldNames())
            env.put(fieldName, clazzdescr.getFieldTyp(fieldName), clazzdescr.getFieldVarDecl(fieldName));

        // Finally, "this", which overrides everything
        env.put("this", new Ast.ClazzTyp(clazz.cname), null);

        // Now type-check methods
        ArrayList<SemanticError> errors = new ArrayList<>();
        for (Ast.Meth meth : clazz.meths) {
            try {
                runMethod(meth, env);
            } catch (SemanticErrors se) {
                errors.addAll(se.getErrors());
            }
        }

        if (!errors.isEmpty())
            throw new SemanticErrors(errors);
    }

    private void runMethod(Ast.Meth meth, Environment env) throws SemanticErrors {
        ArrayList<SemanticError> errors = new ArrayList<>();

        Environment methEnv = new Environment(env);
        for (Ast.VarDecl varDecl : meth.args) {
            methEnv.put(varDecl.name, varDecl.typ, varDecl);
        }
        methEnv.put("Return", meth.retTyp, null);
        for (Ast.VarDecl varDecl : meth.vars) {
            if (!isValidType(varDecl.typ)) {
                errors.add(new SemanticError(varDecl, "invalid variable type " + varDecl.typ.prettyPrint(0)));
                continue;
            }
            methEnv.put(varDecl.name, varDecl.typ, varDecl);
        }

        if (!errors.isEmpty())
            throw new SemanticErrors(errors);

        Ast.Typ bodyTyp = runStmtBlock(meth.stmts, methEnv, meth);
        if (!bodyTyp.isSubtypeOrEquals(meth.retTyp))
            throw new SemanticErrors(meth, "type of return body is not assignable to " + meth.retTyp.prettyPrint(0) + ": " + bodyTyp.prettyPrint(0));
    }

    private Ast.Typ runStmtBlock(List<? extends Ast.Stmt> stmts, Environment env, Ast.Locatable loc) throws SemanticErrors {
        // [Block] without locals
        ArrayList<SemanticError> errors = new ArrayList<>();
        Ast.Typ typ = null;
        for (Ast.Stmt stmt : stmts) {
            try {
                typ = runStmt(stmt, env);
            } catch (SemanticErrors err) {
                errors.addAll(err.getErrors());
            }
        }

        if (!errors.isEmpty())
            throw new SemanticErrors(errors);

        if (typ == null)
            throw new SemanticErrors(loc, "Empty statement blocks are not allowed");

        return typ;
    }

    private Ast.Typ runStmt(Ast.Stmt stmt, Environment env) throws SemanticErrors {
    	
        if (stmt instanceof Ast.IfStmt) {
            List<Ast.Stmt> thenStmts = ((Ast.IfStmt) stmt).thenStmts;
            List<Ast.Stmt> elseStmts = ((Ast.IfStmt) stmt).elseStmts;
            ArrayList<SemanticError> errors = new ArrayList<>();
            Ast.Expr cond = ((Ast.IfStmt) stmt).cond;

            try {
                Ast.Typ condTyp = runExpr(cond, env);
                if (!condTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemanticError(cond, "If Statement: not assignable to Bool: " + condTyp.prettyPrint(0));
            } catch (SemanticError se) {
                errors.add(se);
            }

            Ast.Typ thenStmtsTyp = null, elseStmtsTyp = null;
            try {
                thenStmtsTyp = runStmtBlock(thenStmts, env, stmt);
            } catch (SemanticErrors se) {
                errors.addAll(se.getErrors());
            }

            try {
                elseStmtsTyp = runStmtBlock(elseStmts, env, stmt);
            } catch (SemanticErrors se) {
                errors.addAll(se.getErrors());
            }

            if (!errors.isEmpty())
                throw new SemanticErrors(errors);

            if (!(thenStmtsTyp.isSubtypeOrEquals(elseStmtsTyp) || elseStmtsTyp.isSubtypeOrEquals(thenStmtsTyp)))
                throw new SemanticErrors(stmt, "If Statement: type of thenBlock (" + thenStmtsTyp.prettyPrint(0) + ") does not match elseBlock (" + elseStmtsTyp.prettyPrint(0) + ")");

            return thenStmtsTyp.isSubtypeOrEquals(elseStmtsTyp) ? elseStmtsTyp : thenStmtsTyp;
            
        } else if (stmt instanceof Ast.WhileStmt) {
            List<Ast.Stmt> thenStmts = ((Ast.WhileStmt) stmt).stmts;
            Ast.Expr cond = ((Ast.WhileStmt) stmt).cond;
            ArrayList<SemanticError> errors = new ArrayList<>();
            
            try {
                Ast.Typ condTyp = runExpr(cond, env);
                if (!condTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemanticError(cond, "While Statement: not assignable to Bool: " + condTyp.prettyPrint(0));
            } catch (SemanticError se) {
                errors.add(se);
            }

            Ast.Typ thenStmtsTyp = null;
            try {
                thenStmtsTyp = runStmtBlock(thenStmts, env, stmt);
            } catch (SemanticErrors se) {
                errors.addAll(se.getErrors());
            }

            if (!errors.isEmpty())
                throw new SemanticErrors(errors);

            assert thenStmtsTyp != null;
            return thenStmtsTyp;
            
        } else if (stmt instanceof Ast.ReadlnStmt) {
            String ident = ((Ast.ReadlnStmt) stmt).ident;

            if (!env.contains(ident))
                throw new SemanticErrors(stmt, "Readln Statement: unknown symbol: " + ident);

            Ast.Typ identTyp = env.get(ident);
            if (!(identTyp instanceof Ast.IntTyp || identTyp instanceof Ast.BoolTyp || identTyp instanceof Ast.StringTyp))
                throw new SemanticErrors(stmt, "ReadlnStmt: type of " + ident + " is not Int, Bool or String. Instead is: " + identTyp.prettyPrint(0));
            ((Ast.ReadlnStmt) stmt).v = env.getVarDecl(ident);

            return new Ast.VoidTyp();
            
        } else if (stmt instanceof Ast.PrintlnStmt) {
            Ast.Expr expr = ((Ast.PrintlnStmt) stmt).expr;
            Ast.Typ exprTyp;
            try {
                exprTyp = runExpr(expr, env);
            } catch (SemanticError se) {
                throw new SemanticErrors(Arrays.asList(se));
            }

            if (!(exprTyp.isSubtypeOrEquals(new Ast.IntTyp()) || exprTyp.isSubtypeOrEquals(new Ast.BoolTyp()) || exprTyp.isSubtypeOrEquals(new Ast.StringTyp())))
                throw new SemanticErrors(stmt, "Println Statement: type of expr is not assignable to Int, Bool or String. Instead is: " + exprTyp.prettyPrint(0));

            return new Ast.VoidTyp();
        } else if (stmt instanceof Ast.VarAssignStmt) {
            String lhs = ((Ast.VarAssignStmt) stmt).lhs;
            Ast.Expr rhs = ((Ast.VarAssignStmt) stmt).rhs;

            ArrayList<SemanticError> errors = new ArrayList<>();
            Ast.Typ lhsTyp = null;
            if (env.contains(lhs))
                lhsTyp = env.get(lhs);
            else
                errors.add(new SemanticError(stmt, "Variable Assignment Statement: unknown symbol: " + lhs));

            Ast.Typ rhsTyp = null;
            try {
                rhsTyp = runExpr(rhs, env);
            } catch (SemanticError se) {
                errors.add(se);
            }

            if (!errors.isEmpty())
                throw new SemanticErrors(errors);

            assert lhsTyp != null;
            assert rhsTyp != null;
            if (!rhsTyp.isSubtypeOrEquals(lhsTyp))
                throw new SemanticErrors(stmt, "Variable Assignment Statement: type of right-hand side (" + rhsTyp.prettyPrint(0) + ") is not assignable to left-hand side (" + lhsTyp.prettyPrint(0) + ")");

            ((Ast.VarAssignStmt) stmt).lhsVar = env.getVarDecl(lhs);
            return new Ast.VoidTyp();
            
        } else if (stmt instanceof Ast.FieldAssignStmt) {
            Ast.Expr lhsExpr = ((Ast.FieldAssignStmt) stmt).lhsExpr;
            String lhsField = ((Ast.FieldAssignStmt) stmt).lhsField;
            Ast.Expr rhsExpr = ((Ast.FieldAssignStmt) stmt).rhs;

            ArrayList<SemanticError> errors = new ArrayList<>();
            Ast.Typ lhsExprTyp, lhsFieldTyp = null;
            try {
                lhsExprTyp = runExpr(lhsExpr, env);
                if (!(lhsExprTyp instanceof Ast.ClazzTyp))
                    throw new SemanticError(lhsExpr, "field access in Field Assignment Statement: expected class type, got: " + lhsExprTyp.prettyPrint(0));

                String cname = ((Ast.ClazzTyp) lhsExprTyp).cname;
                ClazzDescr clazz = getClazz(cname);

                if (!clazz.hasField(lhsField))
                    throw new SemanticError(lhsExpr, "field access in Field Assignment Statement: no such field in " + cname + ": " + lhsField);

                lhsFieldTyp = clazz.getFieldTyp(lhsField);
            } catch (SemanticError se) {
                errors.add(se);
            }

            Ast.Typ rhsTyp = null;
            try {
                rhsTyp = runExpr(rhsExpr, env);
            } catch (SemanticError se) {
                errors.add(se);
            }

            if (!errors.isEmpty())
                throw new SemanticErrors(errors);

            assert lhsFieldTyp != null;
            assert rhsTyp != null;
            if (!rhsTyp.isSubtypeOrEquals(lhsFieldTyp))
                throw new SemanticErrors(stmt, "Field Assignment Statement: type of rhs (" + rhsTyp.prettyPrint(0) + ") is not assignable to lhs (" + lhsFieldTyp.prettyPrint(0) + ")");

            return new Ast.VoidTyp();
            
        } else if (stmt instanceof Ast.ReturnStmt) {
            Ast.Expr expr = ((Ast.ReturnStmt) stmt).expr;
            Ast.Typ retTyp = env.get("Return");
            assert retTyp != null;
            if (retTyp instanceof Ast.VoidTyp) {
                if (expr != null)
                    throw new SemanticErrors(stmt, "cannot return a value in a method returning Void");
                return new Ast.VoidTyp();
                
            } else {
                if (expr == null)
                    throw new SemanticErrors(stmt, "must return a value in a method returning non-Void");

                Ast.Typ exprTyp;
                try {
                    exprTyp = runExpr(expr, env);
                } catch (SemanticError se) {
                    throw new SemanticErrors(Arrays.asList(se));
                }

                if (!exprTyp.isSubtypeOrEquals(retTyp))
                    throw new SemanticErrors(expr, "return: " + exprTyp.prettyPrint(0) + " is not assignable to " + retTyp.prettyPrint(0));

                return retTyp;
            }
            
        } else if (stmt instanceof Ast.CallStmt) {
            Ast.Expr target = ((Ast.CallStmt) stmt).target;
            List<Ast.Expr> args = ((Ast.CallStmt) stmt).args;

            Ast.CallExpr callExpr = new Ast.CallExpr(target, args);
            callExpr.setPosition(stmt.getPosition());
            Ast.Typ callTyp;
            try {
                callTyp = runExpr(callExpr, env);
            } catch (SemanticError se) {
                throw new SemanticErrors(Arrays.asList(se));
            }

            ((Ast.CallStmt) stmt).targetMeth = callExpr.meth;
            return callTyp;
        } else {
            throw new AssertionError("ERROR: Java doesn't support Abstract Data Types");
        }
    }

    private Ast.Typ runExpr(Ast.Expr expr, Environment env) throws SemanticError {
    	if (expr instanceof Ast.IntLitExpr) {
        	return new Ast.IntTyp();
    	} else if (expr instanceof Ast.StringLitExpr) {
            return new Ast.StringTyp();
        } else if (expr instanceof Ast.BoolLitExpr) {
            return new Ast.BoolTyp();
        } else if (expr instanceof Ast.NullLitExpr) {
            return new Ast.NullTyp();
        } else if (expr instanceof Ast.IdentExpr) {
            String name = ((Ast.IdentExpr) expr).name;
            if (env.contains(name)) {
                expr.typ = env.get(name);
                ((Ast.IdentExpr) expr).v = env.getVarDecl(name);
                return expr.typ;
            } else
                throw new SemanticError(expr, "unknown symbol: " + name);
        } else if (expr instanceof Ast.ThisExpr) {
            if (env.contains("this")) {
                return env.get("this");
            } else
                throw new SemanticError(expr, "unknown symbol: this");
        } else if (expr instanceof Ast.UnaryExpr) {
            Ast.UnaryOp operator = ((Ast.UnaryExpr) expr).op;
            Ast.Expr a = ((Ast.UnaryExpr) expr).expr;
            Ast.Typ aTyp = runExpr(a, env);

            // TODOOOOO: Check this part
            switch (operator) {
            case NEG:
                if (!aTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemanticError(expr, "NEG: expected assignable to Int, got: " + aTyp.prettyPrint(0));
                expr.typ = new Ast.IntTyp();
                return expr.typ;
            case LNOT:
                if (!aTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemanticError(expr, "LNOT: expected assignable to Bool, got: " + aTyp.prettyPrint(0));
                return new Ast.BoolTyp();
            default:
                throw new AssertionError("ERROR");
            }
        } else if (expr instanceof Ast.BinaryExpr) {
            Ast.BinaryOp operator = ((Ast.BinaryExpr) expr).op;
            Ast.Expr lexp = ((Ast.BinaryExpr) expr).lexp;
            Ast.Expr rexp = ((Ast.BinaryExpr) expr).rexp;
            Ast.Typ lexpTyp = runExpr(lexp, env);
            Ast.Typ rexpTyp = runExpr(rexp, env);

            switch (operator) {
            case PLUS:
            case MINUS:
            case MUL:
            case DIV:
                if (!lexpTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemanticError(expr, "" + operator + ": expected assignable to Int on lhs, got: " + lexpTyp.prettyPrint(0));
                if (!rexpTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemanticError(expr, "" + operator + ": expected assignable to Int on rhs, got: " + rexpTyp.prettyPrint(0));
                return new Ast.IntTyp();
            case LT:
            case GT:
            case LE:
            case GE:
                if (!lexpTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemanticError(expr, "" + operator + ": expected assignable to Int on lhs, got: " + lexpTyp.prettyPrint(0));
                if (!rexpTyp.isSubtypeOrEquals(new Ast.IntTyp()))
                    throw new SemanticError(expr, "" + operator + ": expected assignable to Int on rhs, got: " + rexpTyp.prettyPrint(0));
                return new Ast.BoolTyp();
            case EQ:
            case NE:
                // [Rel] enhanced for subtyping
                // lhs is assignable to rhs or rhs is assignable to lhs
                if (!(lexpTyp.isSubtypeOrEquals(rexpTyp) || rexpTyp.isSubtypeOrEquals(lexpTyp)))
                    throw new SemanticError(expr, "" + operator + ": expected LHS/RHS types to be compatible, got lhs: " + lexpTyp.prettyPrint(0) + " and rhs: " + rexpTyp.prettyPrint(0));
                return new Ast.BoolTyp();
            case LAND:
            case LOR:
                // [Bool]
                if (!lexpTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemanticError(expr, "" + operator + ": expected assignable to Bool on lhs, got: " + lexpTyp.prettyPrint(0));
                if (!rexpTyp.isSubtypeOrEquals(new Ast.BoolTyp()))
                    throw new SemanticError(expr, "" + operator + ": expected assignable to Bool on rhs, got: " + rexpTyp.prettyPrint(0));
                return new Ast.BoolTyp();
            default:
                throw new AssertionError("BUG");
            }
        } else if (expr instanceof Ast.DotExpr) {
            Ast.Expr target = ((Ast.DotExpr) expr).target;
            Ast.Typ targetTyp = runExpr(target, env);
            String ident = ((Ast.DotExpr) expr).ident;

            if (!(targetTyp instanceof Ast.ClazzTyp))
                throw new SemanticError(expr, "field access: expected class type, got: " + targetTyp.prettyPrint(0));

            String cname = ((Ast.ClazzTyp) targetTyp).cname;
            ClazzDescr clazz = getClazz(cname);
            if (!clazz.hasField(ident))
                throw new SemanticError(expr, "field access: no such field in " + cname + ": " + ident);

            return clazz.getFieldTyp(ident);
            
        } else if (expr instanceof Ast.CallExpr) {
            Ast.Expr target = ((Ast.CallExpr) expr).target;
            List<Ast.Expr> args = ((Ast.CallExpr) expr).args;
            if (target instanceof Ast.IdentExpr) {
                // [LocalCall]
                String targetName = ((Ast.IdentExpr) target).name;

                if (!env.contains(targetName))
                    throw new SemanticError(target, "unknown symbol: " + targetName);

                Ast.Typ targetTyp = env.get(targetName);
                if (!(targetTyp instanceof Ast.PolyFuncTyp))
                    throw new SemanticError(expr, "call: expected method, got: " + targetTyp.prettyPrint(0));

                // Construct argTyps of call
                ArrayList<Ast.Typ> argTyps = new ArrayList<>();
                for (Ast.Expr arg : args) {
                    argTyps.add(runExpr(arg, env));
                }

                // Lookup method based on signature, taking into account subtyping
                List<Ast.FuncTyp> candidates = ((Ast.PolyFuncTyp) targetTyp).candidates(argTyps);
                if (candidates.isEmpty())
                    throw new SemanticError(expr, "no matching method signature for " + prettyPrintArgTyps(argTyps) + ". Candidates are: " + targetTyp.prettyPrint(0));
                else if (candidates.size() > 1)
                    throw new SemanticError(expr, "ambiguous method call for " + prettyPrintArgTyps(argTyps) + ". Matching candidates are: " + new Ast.PolyFuncTyp(candidates).prettyPrint(0));

                Ast.FuncTyp matchingTyp = candidates.get(0);
                expr.typ = matchingTyp.retTyp;
                ((Ast.CallExpr) expr).meth = matchingTyp.meth;
                return expr.typ;
            } else if (target instanceof Ast.DotExpr) {
                // [GlobalCall]
                // IMPORTANT: Note we are doing _method_ access here,
                // as opposed to field access in the default handling of DotExpr

                Ast.Expr targetTarget = ((Ast.DotExpr) target).target;
                String targetIdent = ((Ast.DotExpr) target).ident;
                Ast.Typ targetTargetTyp = runExpr(targetTarget, env);

                if (!(targetTargetTyp instanceof Ast.ClazzTyp))
                    throw new SemanticError(target, "method access: expected class type, got: " + targetTargetTyp.prettyPrint(0));

                String cname = ((Ast.ClazzTyp) targetTargetTyp).cname;
                ClazzDescr clazz = getClazz(cname);

                // Lookup method based on name
                if (!clazz.hasMethod(targetIdent))
                    throw new SemanticError(target, "method access: no such method in class " + cname + ": " + targetIdent);

                // Construct argTyps of call
                ArrayList<Ast.Typ> argTyps = new ArrayList<>();
                for (Ast.Expr arg : args) {
                    argTyps.add(runExpr(arg, env));
                }

                // Lookup method based on signature, taking into account subtyping
                Ast.PolyFuncTyp methodTyp = clazz.getMethodTyp(targetIdent);
                List<Ast.FuncTyp> candidates = methodTyp.candidates(argTyps);
                if (candidates.isEmpty())
                    throw new SemanticError(target, "method access: no matching method signature for " + cname + "." + targetIdent + " with " + prettyPrintArgTyps(argTyps) + ". Candidates are: " + methodTyp.prettyPrint(0));
                else if (candidates.size() > 1)
                    throw new SemanticError(target, "method access: too many candidates for " + cname + "." + targetIdent + " with " + prettyPrintArgTyps(argTyps) + ". Matching candidates are: " + new Ast.PolyFuncTyp(candidates).prettyPrint(0));

                Ast.FuncTyp matchingMethodTyp = candidates.get(0);
                expr.typ = matchingMethodTyp.retTyp;
                ((Ast.CallExpr) expr).meth = matchingMethodTyp.meth;
                return expr.typ;
            } else {
                throw new SemanticError(expr, "call: target expression cannot be called");
            }
        } else if (expr instanceof Ast.NewExpr) {
            if (!clazzExist(((Ast.NewExpr) expr).cname))
                throw new SemanticError(expr, "no such class: " + ((Ast.NewExpr) expr).cname);
            return new Ast.ClazzTyp(((Ast.NewExpr) expr).cname);
        } else {
            throw new AssertionError("BUG: This is what happens when Java doesn't support ADTs :-(");
        }
    }

    private static String prettyPrintArgTyps(List<? extends Ast.Typ> argTyps) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        sb.append("[");
        for (Ast.Typ argTyp : argTyps) {
            if (i++ > 0)
                sb.append(" * ");
            sb.append(argTyp);
        }
        sb.append(" ]");
        return sb.toString();
    }

    private boolean isValidType(Ast.Typ typ) {
        if (typ instanceof Ast.ClazzTyp) {
            return clazzExist(((Ast.ClazzTyp)typ).cname);
        } else {
            return true;
        }
    }

    private boolean clazzExist(String cname) {
        return clazzdescrs.containsKey(cname);
    }

    private ClazzDescr getClazz(String cname) {
        return clazzdescrs.get(cname);
    }

    private static class ClazzDescr {
        private final Ast.Clazz clazz;
        private HashMap<String, Ast.VarDecl> fields = new HashMap<>();
        private HashMap<String, ArrayList<Ast.FuncTyp>> meths = new HashMap<>();

        private ClazzDescr(Ast.Clazz clazz) {
            this.clazz = clazz;
        }

        private boolean hasField(String name) {
            return fields.containsKey(name);
        }

        private void addField(Ast.VarDecl varDecl) {
            fields.put(varDecl.name, varDecl);
        }

        private Set<String> getFieldNames() {
            return fields.keySet();
        }

        private Ast.Typ getFieldTyp(String name) {
            return fields.get(name).typ;
        }

        private Ast.VarDecl getFieldVarDecl(String name) {
            return fields.get(name);
        }

        private boolean hasMethod(String name) {
            return meths.containsKey(name);
        }

        private void addMethod(String name, Ast.Meth meth) {
            if (!meths.containsKey(meth.name))
                meths.put(meth.name, new ArrayList<>());

            meths.get(meth.name).add(new Ast.FuncTyp(meth));
        }

        private Ast.PolyFuncTyp getMethodTyp(String name) {
            return new Ast.PolyFuncTyp(meths.get(name));
        }

        private Set<String> getMethodNames() {
            return meths.keySet();
        }
    }

    private static class Environment {
        private final Environment parent;
        private final HashMap<String, Ast.Typ> names = new HashMap<>();
        private final HashMap<String, Ast.VarDecl> varDecls = new HashMap<>();

        private Environment() {
            this(null);
        }

        private Environment(Environment parent) {
            this.parent = parent;
        }

        private void put(String name, Ast.Typ typ, Ast.VarDecl varDecl) {
            names.put(name, typ);
            varDecls.put(name, varDecl);
        }

        private boolean contains(String name) {
            if (names.containsKey(name))
                return true;
            else if (parent != null)
                return parent.contains(name);
            else
                return false;
        }

        private Ast.Typ get(String name) {
            if (names.containsKey(name))
                return names.get(name);
            else
                return parent.get(name);
        }

        private Ast.VarDecl getVarDecl(String name) {
            if (varDecls.containsKey(name))
                return varDecls.get(name);
            else
                return parent.getVarDecl(name);
        }
    }

    public static class SemanticError extends Exception {
        public final Ast.Location location;

        public SemanticError(Ast.Locatable loc, String message) {
            super(message);
            this.location = loc.getPosition();
        }
    }

    public static class SemanticErrors extends Exception {
        private ArrayList<SemanticError> errors;

        public SemanticErrors(List<SemanticError> errors) {
            super("sem errors failed");
            this.errors = new ArrayList<>(errors);
        }

        public SemanticErrors(Ast.Locatable loc, String message) {
            this(Arrays.asList(new SemanticError(loc, message)));
        }

        public List<SemanticError> getErrors() {
            return Collections.unmodifiableList(errors);
        }
    }
}
