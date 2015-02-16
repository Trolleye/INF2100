package no.uio.ifi.alboc.syntax;

/**
 * Module for the syntax for the programming language AlboC
 * @author Uy Tran
 * @version 27.11.2014
 */
import no.uio.ifi.alboc.alboc.AlboC;
import no.uio.ifi.alboc.code.Code;
import no.uio.ifi.alboc.error.Error;
import no.uio.ifi.alboc.log.Log;
import no.uio.ifi.alboc.scanner.Scanner;
import no.uio.ifi.alboc.scanner.Token;
import static no.uio.ifi.alboc.scanner.Token.*;
import no.uio.ifi.alboc.types.*;

/**
 * Creates a syntax tree by parsing an AlboC program; 
 * prints the parse tree (if requested);
 * checks it;
 * generates executable code. 
 */
public class Syntax {
    static DeclList library;
    static Program program;

    public static void init() {
        library = new GlobalDeclList();
        FuncDecl funcDec = new FuncDecl("putchar");
        funcDec.body = new FuncBody();
        ParamDecl parDec = new ParamDecl("a");
        parDec.type = Types.intType;
        funcDec.type = Types.intType;
        ParamDeclList dl = new ParamDeclList();
        dl.addDecl(parDec);
        funcDec.funcParams = dl;
        library.addDecl(funcDec);

        dl = new ParamDeclList();
        funcDec = new FuncDecl("putint");
        funcDec.body = new FuncBody();
        parDec = new ParamDecl("a");
        parDec.type = Types.intType;
        funcDec.type = Types.intType;
        dl.addDecl(parDec);
        funcDec.funcParams = dl;
        library.addDecl(funcDec);

        dl = new ParamDeclList();
        funcDec = new FuncDecl("getint");
        funcDec.body = new FuncBody();
        funcDec.type = Types.intType;
        funcDec.funcParams = dl;
        library.addDecl(funcDec);

        dl = new ParamDeclList();
        funcDec = new FuncDecl("getchar");
        funcDec.body = new FuncBody();
        funcDec.type = Types.intType;
        funcDec.funcParams = dl;
        library.addDecl(funcDec);

        dl = new ParamDeclList();
        funcDec = new FuncDecl("exit");
        funcDec.body = new FuncBody();
        parDec = new ParamDecl("a");
        parDec.type = Types.intType;
        funcDec.type = Types.intType;
        dl.addDecl(parDec);
        funcDec.funcParams = dl;
        library.addDecl(funcDec);
    }

    public static void finish() {}

    public static void checkProgram() {
	program.check(library);
    }

    public static void genCode() {
	program.genCode(null);
    }

    public static void parseProgram() {
	program = Program.parse();
    }

    public static void printProgram() {
	program.printTree();
    }
}

/*
 * Super class for all syntactic units.
 * (This class is not mentioned in the syntax diagrams.)
 */
abstract class SyntaxUnit {
    int lineNum;

    SyntaxUnit() {
	lineNum = Scanner.curLine;
    }

    abstract void check(DeclList curDecls);
    abstract void genCode(FuncDecl curFunc);
    abstract void printTree();

    void error(String message) {
	Error.error(lineNum, message);
    }
}

/*
 * A <program>
 */
class Program extends SyntaxUnit {
    DeclList progDecls;
	
    @Override void check(DeclList curDecls) {
       progDecls.check(curDecls);

       if (! AlboC.noLink) {
	    // Check that 'main' has been declared properly:
        Declaration px = progDecls.firstDecl;
        while (px != null){
            if(px.name.equals("main"))
                break;
            px = px.nextDecl;
        }

        if(px == null){
            error("No main-function found");
        }
        Log.noteBinding("main",px.lineNum, px.lineNum);
	    // OK
    }
   }
		
    @Override void genCode(FuncDecl curFunc) {
        Code.genInstr("",".data","","");
        progDecls.genCode(null);
    }

    static Program parse() {
       Log.enterParser("<program>");

       Program p = new Program();
       p.progDecls = GlobalDeclList.parse();
       if (Scanner.curToken != eofToken)
           Error.expected("A declaration");
       if(Scanner.state.equals("comment")){
           Error.error("unclosed comment starting at line: " + Scanner.startCommentLine);
       }

       Log.leaveParser("</program>");
       return p;
    }

    @Override void printTree() {
	progDecls.printTree();
    }
}

/**
 * A declaration list.
 * (This class is not mentioned in the syntax diagrams.)
 */
abstract class DeclList extends SyntaxUnit {
    Declaration firstDecl = null;
    Declaration lastDecl;
    DeclList outerScope;

    DeclList () {}

    @Override void check(DeclList curDecls) {
       outerScope = curDecls;

       Declaration dx = firstDecl;
       while (dx != null){
           dx.check(this);  dx = dx.nextDecl;
       }
    }

    /**
     * Metoden iterer gjennom lenkelisten for å kalle på printTree metoden i alle deklarasjonene.
     */
    @Override void printTree() {
        Declaration px = firstDecl;
        while(px != null){
            px.printTree();
            px = px.nextDecl;
        }
    }

    /**
     * Metoden legger til deklarasjoner på enden av lenkelisten
     * @param d deklarasjonen som skal inn i lenkelisten
     */
    void addDecl(Declaration d) {
        if(firstDecl == null){
            firstDecl = d;
        }else{
            lastDecl = firstDecl;
            while(lastDecl.nextDecl != null){
                if(lastDecl.name.equals(d.name))
                    Error.error("Can't declare more than one variable with the same name: " + d.name);
                lastDecl = lastDecl.nextDecl;
            }
            lastDecl.nextDecl = d;
        }
    }

    int dataSize() {
        Declaration dx = firstDecl;
        int res = 0;

        while (dx != null) 
           res += dx.declSize();  dx = dx.nextDecl;

        return res;
    }

    Declaration findDecl(String name, SyntaxUnit use) {
        Declaration px = firstDecl;
        DeclList oPx = this;
        done: while(oPx != null){
            px = oPx.firstDecl;
            while(px != null){
                if(name.equals(px.name))
                    break done;
                px = px.nextDecl;
            }
            oPx = oPx.outerScope;
        }
        return px;
    }
}

/*
 * A list of global declarations. 
 * (This class is not mentioned in the syntax diagrams.)
 */
class GlobalDeclList extends DeclList {
    @Override void genCode(FuncDecl curFunc) {
        Declaration px = firstDecl;
        boolean b = true;
        while(px != null){
            px.genCode(curFunc);
            if(b && px.nextDecl instanceof FuncDecl){
                Code.genInstr("",".text","","");
                b = false;
            }
            px = px.nextDecl;
        }
    }

    static GlobalDeclList parse() {
        GlobalDeclList gdl = new GlobalDeclList();

        while (Scanner.curToken == intToken) 
           gdl.addDecl(Declaration.parse(DeclType.parse()));

        return gdl;
    }
}

/*
 * A list of local declarations. 
 * (This class is not mentioned in the syntax diagrams.)
 */
class LocalDeclList extends DeclList {
    @Override void genCode(FuncDecl curFunc) {}

    static LocalDeclList parse() {
        LocalDeclList ldl = new LocalDeclList();

        while (Scanner.curToken == intToken) 
            ldl.addDecl(LocalVarDecl.parse(DeclType.parse()));

        return ldl;
    }
}

/*
 * A list of parameter declarations. 
 * (This class is not mentioned in the syntax diagrams.)
 */
class ParamDeclList extends DeclList {
    @Override void genCode(FuncDecl curFunc) {
        Declaration px = firstDecl;
        while(px != null){
            px.genCode(curFunc);
            px = px.nextDecl;
        }
    }

    static ParamDeclList parse() {
        ParamDeclList pdl = new ParamDeclList();

        while (Scanner.curToken == intToken) {
            pdl.addDecl(Declaration.parse(DeclType.parse()));
            if(Scanner.curToken != rightParToken)
                Scanner.skip(commaToken);
        }

        return pdl;
    }

    @Override void printTree() {
	Declaration px = firstDecl;
	while (px != null) {
	    px.printTree();  px = px.nextDecl;
	    if (px != null) Log.wTree(", ");
	}
    }
}

/*
 * A <type>
 */
class DeclType extends SyntaxUnit {
    int numStars = 0;
    Type type;

    @Override void check(DeclList curDecls) {
	type = Types.intType;
	for (int i = 1;  i <= numStars;  ++i)
	    type = new PointerType(type);
    }

    @Override void genCode(FuncDecl curFunc) {}

    static DeclType parse() {
	Log.enterParser("<type>");

	DeclType dt = new DeclType();

	Scanner.skip(intToken);

	while (Scanner.curToken == starToken) {
	    ++dt.numStars;
	    Scanner.readNext();
	}

	Log.leaveParser("</type>");
	return dt;
    }

    @Override void printTree() {
	Log.wTree("int");
	for (int i = 1;  i <= numStars;  ++i) Log.wTree("*");
    }
}

/*
 * Any kind of declaration.
 */
abstract class Declaration extends SyntaxUnit {
    String name, assemblerName;
    DeclType typeSpec;
    Type type;
    boolean visible = false;
    Declaration nextDecl = null;

    Declaration(String n) {
	name = n;
    }

    abstract int declSize();

    /**
     * Denne metoden går gjennom en sjekk for hva slags deklarasjon Scanner spytter ut for så å kalle på den riktige parse metoden.
     * Metoden tar så vare på {@paramref ts} ved hjelp av typeSpec variabelen til slutt.  
     * @param dt typen til brukerfunksjonen
     */
    static Declaration parse(DeclType dt) {
	Declaration d = null;
	if (Scanner.curToken==nameToken && Scanner.nextToken==leftParToken) {
	    d = FuncDecl.parse(dt);
    } else if (Scanner.curToken==nameToken && (Scanner.nextToken==commaToken || Scanner.nextToken==rightParToken)){
        d = ParamDecl.parse(dt);
	} else if (Scanner.curToken == nameToken) {
	    d = GlobalVarDecl.parse(dt);
	} else {
	    Error.expected("A declaration name");
	}
	d.typeSpec = dt;
	return d;
    }

    /**
     * checkWhetherVariable: Utility method to check whether this Declaration is
     * really a variable. The compiler must check that a name is used properly;
     * for instance, using a variable name a in "a()" is illegal.
     * This is handled in the following way:
     * <ul>
     * <li> When a name a is found in a setting which implies that should be a
     *      variable, the parser will first search for a's declaration d.
     * <li> The parser will call d.checkWhetherVariable(this).
     * <li> Every sub-class of Declaration will implement a checkWhetherVariable.
     *      If the declaration is indeed a variable, checkWhetherVariable will do
     *      nothing, but if it is not, the method will give an error message.
     * </ul>
     * Examples
     * <dl>
     *  <dt>GlobalVarDecl.checkWhetherVariable(...)</dt>
     *  <dd>will do nothing, as everything is all right.</dd>
     *  <dt>FuncDecl.checkWhetherVariable(...)</dt>
     *  <dd>will give an error message.</dd>
     * </dl>
     */
    abstract void checkWhetherVariable(SyntaxUnit use);

    /**
     * checkWhetherFunction: Utility method to check whether this Declaration
     * is really a function.
     * 
     * @param nParamsUsed Number of parameters used in the actual call.
     *                    (The method will give an error message if the
     *                    function was used with too many or too few parameters.)
     * @param use From where is the check performed?
     * @see   checkWhetherVariable
     */
    abstract void checkWhetherFunction(int nParamsUsed, SyntaxUnit use);
}

/**
 * A <var decl>
 */
abstract class VarDecl extends Declaration {
    boolean isArray = false;
    int numElems = 0;

    VarDecl(String n) {
	super(n);
    }

    @Override void check(DeclList curDecls) {
        int count = 0;
        if(this instanceof ParamDecl){
            Declaration cur = curDecls.firstDecl;
            while(!cur.name.equals(name)){
                count += cur.type.size();
                cur = cur.nextDecl;
            }
            count += cur.type.size();
            assemblerName = ""+(count+4) + "(%ebp)";
        }else if(this instanceof LocalVarDecl){
            Declaration cur = curDecls.firstDecl;
            while(!cur.name.equals(name)){
                count += cur.type.size();
                cur = cur.nextDecl;
            }
            count += cur.type.size();
            assemblerName = ""+(count)*(-1) + "(%ebp)";
        }
    }

    @Override void printTree() {
        typeSpec.printTree();
        Log.wTree(" " + name);
        if(isArray) Log.wTree("["+numElems+"]");
        Log.wTreeLn(";");
    }

    @Override int declSize() {
	return type.size();
    }

    @Override void checkWhetherFunction(int nParamsUsed, SyntaxUnit use) {
	use.error(name + " is a variable and no function!");
    }
	
    @Override void checkWhetherVariable(SyntaxUnit use) {
	// OK
    }
}

/*
 * A global <var decl>.
 */
class GlobalVarDecl extends VarDecl {

    GlobalVarDecl(String n) {
	super(n);
	assemblerName = (AlboC.underscoredGlobals() ? "_" : "") + n;
    }

    @Override void genCode(FuncDecl curFunc) {
        if(!isArray){
            Code.genInstr("",".globl",name,"");
            Code.genInstr(name,".fill","1,4,0","");
        }else{
            Code.genInstr("",".globl",name,"");
            Code.genInstr(name,".fill",numElems+",4,0","");
        }
    }

    static GlobalVarDecl parse(DeclType dt) {
        Log.enterParser("<var decl>");
        GlobalVarDecl gvd = new GlobalVarDecl(Scanner.curName);
        Scanner.skip(nameToken);

        if(Scanner.curToken == leftBracketToken){
            gvd.isArray = true;
            Scanner.skip(leftBracketToken);
            gvd.numElems = Scanner.curNum;
            gvd.type = Types.intType;
            if(dt.numStars > 0){
                int count = dt.numStars;
                while(count > 0){
                    gvd.type = new PointerType(gvd.type);
                    count--;
                }
            }
            gvd.type = new ArrayType(gvd.type, gvd.numElems);
            Scanner.skip(numberToken);
            Scanner.skip(rightBracketToken);
        }else if(dt.numStars > 0){
            gvd.type = Types.intType;
            int count = dt.numStars;
            while(count > 0){
                gvd.type = new PointerType(gvd.type);
                count--;
            }
        }else
            gvd.type = Types.intType;

        Scanner.skip(semicolonToken);
        Log.leaveParser("</var decl>");
        return gvd;
    }
}

/*
 * A local <var decl>.
 */
class LocalVarDecl extends VarDecl {
    LocalVarDecl(String n) {
	super(n); 
    }

    @Override void genCode(FuncDecl curFunc) {}

    static LocalVarDecl parse(DeclType dt) {
        Log.enterParser("<var decl>");

        LocalVarDecl lvd = new LocalVarDecl(Scanner.curName);
        Scanner.skip(nameToken);

        if(Scanner.curToken == leftBracketToken){
            lvd.isArray = true;
            Scanner.skip(leftBracketToken);
            lvd.numElems = Scanner.curNum;
            lvd.type = Types.intType;
            if(dt.numStars > 0){
                int count = dt.numStars;
                while(count > 0){
                    lvd.type = new PointerType(lvd.type);
                    count--;
                }
            }
            lvd.type = new ArrayType(lvd.type, lvd.numElems);
            Scanner.skip(numberToken);
            Scanner.skip(rightBracketToken);
        }else if(dt.numStars > 0){
            lvd.type = Types.intType;
            int count = dt.numStars;
            while(count > 0){
                lvd.type = new PointerType(lvd.type);
                count--;
            }
        }else
            lvd.type = Types.intType;
        lvd.typeSpec = dt;

        Scanner.skip(semicolonToken);
        Log.leaveParser("</var decl>");
        return lvd;
    }
}

/*
 * A <param decl>
 */
class ParamDecl extends VarDecl {
    ParamDecl(String n) {
	super(n);
    }

    @Override void genCode(FuncDecl curFunc) {}

    static ParamDecl parse(DeclType dt) {
        Log.enterParser("<param decl>");

        ParamDecl pd = new ParamDecl(Scanner.curName);
        Scanner.skip(nameToken);

        if(dt.numStars > 0){
            pd.type = Types.intType;
            int count = dt.numStars;
            while(count > 0){
                pd.type = new PointerType(pd.type);
                count--;
            }
        }else
            pd.type = Types.intType;

        Log.leaveParser("</param decl>");
        return pd;
    }

    @Override void printTree() {
       typeSpec.printTree();  Log.wTree(" "+name);
    }
}

/*
 * A <func body>.
 */
class FuncBody extends SyntaxUnit {
    LocalDeclList decls;
    StatmList statList;

    @Override void check(DeclList curDecls) {
        decls.check(curDecls);
        statList.check(decls);
    }

    @Override void genCode(FuncDecl curFunc) {
        statList.genCode(curFunc);
    }

    static FuncBody parse() {
    Log.enterParser("<func body>");

    Scanner.skip(leftCurlToken);
    FuncBody fb = new FuncBody();
    fb.decls = LocalDeclList.parse();
    fb.statList = StatmList.parse();
    Scanner.skip(rightCurlToken);

    Log.leaveParser("</func body>");
    return fb;
    }

    @Override void printTree() {
        decls.printTree();
        statList.printTree();
    }
}

/*
 * A <func decl>
 */
class FuncDecl extends Declaration {
    ParamDeclList funcParams;
    String exitLabel;
    FuncBody body;
	
    FuncDecl(String n) {
	// Used for user functions:
	super(n);
	assemblerName = (AlboC.underscoredGlobals() ? "_" : "") + n;
    }

    @Override int declSize() {
	return 0;
    }

    @Override void check(DeclList curDecls) {
        funcParams.check(curDecls);
        body.check(funcParams);

    }

    @Override void checkWhetherFunction(int nParamsUsed, SyntaxUnit use) {
        int count = 0;
        Declaration px = funcParams.firstDecl;
        while(px != null){
            px = px.nextDecl;
            count++;
        }

        if(nParamsUsed == count){
            // OK
        }else if(nParamsUsed < count){
            use.error("Too few arguments in function " + name + " expected " + count + " arguments, found " + nParamsUsed);
        }
    }
	
    @Override void checkWhetherVariable(SyntaxUnit use) {
        use.error(name + " is a function and no variable!");
    }

    @Override void genCode(FuncDecl curFunc) {
        Code.genInstr("",".globl",name,"Start function " + name);
        int count = 0;
        Declaration px = body.decls.firstDecl;
        while(px != null){
            count += px.type.size();
            px = px.nextDecl;
        }

        Code.genInstr(name,"enter","$" + count + ",$0","");
        if(body != null)
            body.genCode(this);
        Code.genInstr(".exit$"+name,"","","");
        Code.genInstr("","leave","","");
        Code.genInstr("","ret","","End Function" + name);
    }

    static FuncDecl parse(DeclType dt) {
        Log.enterParser("<func decl>");

        FuncDecl fd = new FuncDecl(Scanner.curName);
        Scanner.skip(nameToken);
        Scanner.skip(leftParToken);
        fd.funcParams = ParamDeclList.parse();
        Scanner.skip(rightParToken);
        fd.body = FuncBody.parse();

        Log.leaveParser("</func decl>");
        return fd;
    }

    @Override void printTree() {
        typeSpec.printTree();
        Log.wTree(" " + name + "(");
        funcParams.printTree();
        Log.wTreeLn(") {");
        Log.indentTree();
        body.printTree();
        Log.outdentTree();
        Log.wTreeLn("}");
        Log.wTreeLn();
    }
}

/*
 * A <statm list>.
 */
class StatmList extends SyntaxUnit {
    Statement first;

    @Override void check(DeclList curDecls) {
        Statement last = first;

        while(last != null){
            last.check(curDecls);
            last = last.nextStatm;
        }
    }

    @Override void genCode(FuncDecl curFunc) {
        Statement cur = first;
        while(cur != null){
            cur.genCode(curFunc);
            cur = cur.nextStatm;
        }
    }

    /**
     * Metoden oppretter en lenkeliste av Statement-objekter ved å få objektene til å peke på hverandre så lenge Scanner spytter ut statementrelaterte tokens
     */
    static StatmList parse() {
	Log.enterParser("<statm list>");

	StatmList sl = new StatmList();

	Statement lastStatm;
    sl.first = Statement.parse();
    lastStatm = sl.first;
	while (Scanner.curToken != rightCurlToken) {
        lastStatm.nextStatm = Statement.parse();
        lastStatm = lastStatm.nextStatm;
	}

	Log.leaveParser("</statm list>");
	return sl;
    }

    /**
     * Metoden iterer gjennom lenkelisten og kjører printTree for alle Statement-objeketene
     */
    @Override void printTree() {
        Statement cur = first;
        while(cur != null){
            cur.printTree();
            cur = cur.nextStatm;
        }
    }
}

/*
 * A <statement>.
 */
abstract class Statement extends SyntaxUnit {
    Statement nextStatm;

    static Statement parse() {
	Log.enterParser("<statement>");

	Statement s = null;
	if (Scanner.curToken==nameToken && 
	    Scanner.nextToken==leftParToken) {
        s = CallStatm.parse();
	} else if (Scanner.curToken==nameToken || 
        Scanner.curToken==starToken) {
        s = AssignStatm.parse();
	} else if (Scanner.curToken == forToken) {
        s = ForStatm.parse();
	} else if (Scanner.curToken == ifToken) {
	    s = IfStatm.parse();
	} else if (Scanner.curToken == returnToken) {
        s = ReturnStatm.parse();
	} else if (Scanner.curToken == whileToken) {
	    s = WhileStatm.parse();
	} else if (Scanner.curToken == semicolonToken) {
	    s = EmptyStatm.parse();
	} else {
	    Error.expected("A statement");
	}

	Log.leaveParser("</statement>");
	return s;
    }
}

/*
 * An <empty statm>.
 */
class EmptyStatm extends Statement {
    @Override void check(DeclList curDecls) {
        // OK
    }

    @Override void genCode(FuncDecl curFunc) {}

    static EmptyStatm parse() {
        Log.enterParser("<empty statm>");

        Scanner.skip(semicolonToken);
        EmptyStatm es = new EmptyStatm();

        Log.leaveParser("</empty statm>");
	return es;
    }

    @Override void printTree() {
        Log.wTreeLn(";");
    }
}	

/*
 * A <for-statm>.
 */
class ForStatm extends Statement {
    Assignment first;
    Expression test;
    Assignment second;
    StatmList body;

    @Override void check(DeclList curDecls) {
        first.check(curDecls);
        test.check(curDecls);
        Log.noteTypeCheck("for (...; t; ...) ...", test.type, "t", lineNum);
        if (test.type instanceof ValueType) {
        // OK
        } else {
           error("For-test must be a value.");
        }
        second.check(curDecls);
        body.check(curDecls);
    }

    @Override void genCode(FuncDecl curFunc) {
        String testLabel = Code.getLocalLabel(), 
        endLabel  = Code.getLocalLabel();

        first.genCode(curFunc);
        Code.genInstr(testLabel, "", "", "Start for-statement");
        test.genCode(curFunc);
        Code.genInstr("", "cmpl", "$0,%eax", "");
        Code.genInstr("", "je", endLabel, "");
        body.genCode(curFunc);
        second.genCode(curFunc);
        Code.genInstr("", "jmp", testLabel, "");
        Code.genInstr(endLabel, "", "", "End for-statement");
    }

    static ForStatm parse() {
        ForStatm fs = new ForStatm();
        Log.enterParser("<for-statm>");

        Scanner.skip(forToken);
        Scanner.skip(leftParToken); // for(
        fs.first = Assignment.parse();
        Scanner.skip(semicolonToken);// for(assignment;
        fs.test = Expression.parse();
        Scanner.skip(semicolonToken);// for(assignement; expression;
        fs.second = Assignment.parse();
        Scanner.skip(rightParToken); // for(assignement; expression; assignment)
        Scanner.skip(leftCurlToken); 
        fs.body = StatmList.parse();
        Scanner.skip(rightCurlToken);

        Log.leaveParser("</for-statm>");
        return fs;
    }

    @Override void printTree() {
        Log.wTree("for (");
        first.printTree();
        Log.wTree(";");
        test.printTree();
        Log.wTree(";");
        second.printTree();
        Log.wTreeLn(") {");
        Log.indentTree();
        body.printTree();
        Log.wTreeLn();
        Log.outdentTree();
        Log.wTreeLn("}");
    }
}

/*
 * An <if-statm>.
 */
class IfStatm extends Statement {
    Expression exp;
    StatmList ifBody;
    StatmList elseBody = null;

    @Override void check(DeclList curDecls) {
        exp.check(curDecls);
        Log.noteTypeCheck("if (t) ...", exp.type, "t", lineNum);
        if (exp.type instanceof ValueType) {
        // OK
        } else {
           error("If-test must be a value.");
        }
        ifBody.check(curDecls);
        if(elseBody != null)
            elseBody.check(curDecls);
    }

    @Override void genCode(FuncDecl curFunc) {
        String endLabel = Code.getLocalLabel(); 
        String elseLabel = "";
        exp.genCode(curFunc);
        Code.genInstr("", "", "", "Start if-statement");
        Code.genInstr("", "cmpl", "$0,%eax", "");
        if(elseBody != null){
            elseLabel = Code.getLocalLabel();
            Code.genInstr("", "je", elseLabel, "");
        }else{
            Code.genInstr("", "je", endLabel, "");
        }
        ifBody.genCode(curFunc);
        if(elseBody != null){
            Code.genInstr("", "jmp", endLabel, "");
            Code.genInstr(elseLabel, "", "", "Start else-statement");
            elseBody.genCode(curFunc);
        }
        Code.genInstr(endLabel, "", "", "End if-statement");
    }

    static IfStatm parse() {
        Log.enterParser("<if-statm>");

        IfStatm is = new IfStatm();
        Scanner.skip(ifToken);
        Scanner.skip(leftParToken); // if(
        is.exp = Expression.parse();
        Scanner.skip(rightParToken); // if(expression)
        Scanner.skip(leftCurlToken);
        is.ifBody = StatmList.parse();
        Scanner.skip(rightCurlToken);
        if(Scanner.curToken == elseToken){ //else-check
            Log.enterParser("<else-part>");
            Scanner.skip(elseToken);
            Scanner.skip(leftCurlToken);
            is.elseBody = StatmList.parse();
            Scanner.skip(rightCurlToken);
            Log.leaveParser("</else-part>");
        }

        Log.leaveParser("</if-statm>");
        return is;
    }

    @Override void printTree() {
        Log.wTree("if (");
        exp.printTree();
        Log.wTreeLn("){");
        Log.indentTree();
        ifBody.printTree();
        
        Log.wTreeLn();
        Log.outdentTree();
        Log.wTree("}");
        if(elseBody != null){
            Log.wTreeLn(" else {");
            Log.indentTree();
            elseBody.printTree();
            Log.outdentTree();
            Log.wTreeLn("}");
        }
    }
}

/*
 * A <return-statm>.
 */
class ReturnStatm extends Statement{
    Expression retVal;

    @Override void check(DeclList curDecls) {
        retVal.check(curDecls);
    }

    @Override void genCode(FuncDecl curFunc) {
        if(retVal.type == Types.intType || curFunc.type == retVal.type){
            // OK
        }else
            error("Return value must be the same as the declared function-value");

        retVal.genCode(curFunc);
        Code.genInstr("","jmp",".exit$"+curFunc.name,"");
    }

    static ReturnStatm parse() {
        Log.enterParser("<return-statm>");

        ReturnStatm rs = new ReturnStatm();
        Scanner.skip(returnToken);
        rs.retVal = Expression.parse();
        Scanner.skip(semicolonToken);

        Log.leaveParser("</return-statm>");
        return rs;
    }

    @Override void printTree() {
        Log.wTree("return ");
        retVal.printTree();
        Log.wTreeLn(";");
    }
}

/*
 * A <while-statm>.
 */
class WhileStatm extends Statement {
    Expression test;
    StatmList body;

    @Override void check(DeclList curDecls) {
       test.check(curDecls);
       body.check(curDecls);

       Log.noteTypeCheck("while (t) ...", test.type, "t", lineNum);
       if (test.type instanceof ValueType) {
	    // OK
       } else {
           error("While-test must be a value.");
       }
    }

    @Override void genCode(FuncDecl curFunc) {
	String testLabel = Code.getLocalLabel(), 
	       endLabel  = Code.getLocalLabel();

	Code.genInstr(testLabel, "", "", "Start while-statement");
	test.genCode(curFunc);

	Code.genInstr("", "cmpl", "$0,%eax", "");
	Code.genInstr("", "je", endLabel, "");
	body.genCode(curFunc);

	Code.genInstr("", "jmp", testLabel, "");
	Code.genInstr(endLabel, "", "", "End while-statement");
    }

    static WhileStatm parse() {
	Log.enterParser("<while-statm>");

	WhileStatm ws = new WhileStatm();
	Scanner.skip(whileToken);
	Scanner.skip(leftParToken); // while(

	ws.test = Expression.parse();
	Scanner.skip(rightParToken); // while(expression)

	Scanner.skip(leftCurlToken);
	ws.body = StatmList.parse();
	Scanner.skip(rightCurlToken);

	Log.leaveParser("</while-statm>");
	return ws;
    }

    @Override void printTree() {
	Log.wTree("while (");  test.printTree();  Log.wTreeLn(") {");
    Log.indentTree();
    body.printTree();  
    Log.wTreeLn();
    Log.outdentTree();
	Log.wTreeLn("}");
    }
}

/*
 * An <Lhs-variable>
 */
class LhsVariable extends SyntaxUnit {
    int numStars = 0;
    Variable var;
    Type type;

    @Override void check(DeclList curDecls) {
	var.check(curDecls);
	type = var.type;
	for (int i = 1;  i <= numStars;  ++i) {
	    Type e = type.getElemType();
	    if (e == null) 
		error("Type error in left-hand side variable!");
	    type = e;
	}
    }

    @Override void genCode(FuncDecl curFunc) {
	var.genAddressCode(curFunc);
	for (int i = 1;  i <= numStars;  ++i)
	    Code.genInstr("", "movl", "(%eax),%eax", "  *");
    }

    static LhsVariable parse() {
	Log.enterParser("<lhs-variable>");

	LhsVariable lhs = new LhsVariable();
	while (Scanner.curToken == starToken) {
	    ++lhs.numStars;  Scanner.skip(starToken);
	}
	Scanner.check(nameToken);
	lhs.var = Variable.parse();

	Log.leaveParser("</lhs-variable>");
	return lhs;
    }

    @Override void printTree() {
	for (int i = 1;  i <= numStars;  ++i) Log.wTree("*");
	var.printTree();
    }
}

/*
 * An <assignment>.
 */
class Assignment extends SyntaxUnit{
    LhsVariable var;
    Expression exp;

    @Override void check(DeclList curDecls){
        var.check(curDecls);
        exp.check(curDecls);
        Log.noteTypeCheck("v = e", var.type, "v", lineNum);
        if(var.type instanceof ValueType){
            // OK
        }else{
            error("Assignemnt type of lhs-variable must be a value-type");
        }

        Log.noteTypeCheck("v = e", exp.type, "e", lineNum);
        if(exp.type == Types.intType || var.type == exp.type){
            // OK
        }else{
            error("Assignemnt type of expression must be the same as lhs-variable or int");
        }
    }

    @Override void genCode(FuncDecl curFunc){
        var.genCode(curFunc);
        Code.genInstr("", "pushl", "%eax", "");
        exp.genCode(curFunc);
        Code.genInstr("", "popl", "%edx", "");
        Code.genInstr("", "movl", "%eax,(%edx)", "");
    }

    @Override void printTree(){
        var.printTree();
        Log.wTree(" = ");
        exp.printTree();
    }

    static Assignment parse(){
        Log.enterParser("<assignment>");

        Assignment a = new Assignment();
        a.var = LhsVariable.parse();
        Scanner.skip(assignToken);
        a.exp = Expression.parse();

        Log.leaveParser("</assignment>");
        return a;
    }
}

/*
 * An <expression list>.
 */
class ExprList extends SyntaxUnit {
    Expression firstExpr = null;

    @Override void check(DeclList curDecls) {
        Expression cur = firstExpr;

        while(cur != null){
            cur.check(curDecls);
            cur = cur.nextExpr;
        }
    }

    @Override void genCode(FuncDecl curFunc) {
        Expression cur = firstExpr;

        while(cur != null){
            cur.genCode(curFunc);
            cur = cur.nextExpr;
        }
    }

    static ExprList parse() {
        Expression lastExpr = null;

        Log.enterParser("<expr list>");

        ExprList el = new ExprList();
        if(Scanner.curToken != rightParToken){ //check if arglist is empty
            el.firstExpr = Expression.parse();
            lastExpr = el.firstExpr;
        }
  
        while(Scanner.curToken != rightParToken){
            if(Scanner.curToken == commaToken && Scanner.nextToken == rightParToken)
                Error.expected("An expression");
            Scanner.skip(commaToken);
            lastExpr.nextExpr = Expression.parse();
            lastExpr = lastExpr.nextExpr;
        }

        Log.leaveParser("</expr list>");
        return el;
    }

    @Override void printTree() {
        Expression cur = firstExpr;
        while(cur != null){
            cur.printTree();
            cur = cur.nextExpr;
            if(cur != null)
                Log.wTree(", ");
        }
    }
}

/*
 * An <expression>
 */
class Expression extends SyntaxUnit {
    Expression nextExpr = null;
    Term firstTerm, secondTerm = null;
    Operator relOpr = null;
    Type type = null;

    @Override void check(DeclList curDecls) {
        firstTerm.check(curDecls);
        if(relOpr != null){
            secondTerm.check(curDecls);
            if(relOpr.oprToken == equalToken || relOpr.oprToken == notEqualToken){
                Log.noteTypeCheck("x == y (and !=)", firstTerm.type, "x", lineNum);
                Log.noteTypeCheck("x == y (and !=)", secondTerm.type, "y", lineNum);
                if(firstTerm.type instanceof ValueType && secondTerm.type instanceof ValueType){
                    // OK
                }else{
                    error("== and != expressions must have value-types as terms");
                }

                if(firstTerm.type == secondTerm.type || firstTerm.type == Types.intType || secondTerm.type == Types.intType){
                    // OK
                }else{
                    error("== and != expressions must have terms with the same types or int types");
                }
            }

            if(relOpr.oprToken == greaterToken || relOpr.oprToken == greaterEqualToken || relOpr.oprToken == lessToken || relOpr.oprToken == lessEqualToken){
                Log.noteTypeCheck("x < y (and <=, >, >=)", firstTerm.type, "x", lineNum);
                Log.noteTypeCheck("x < y (and <=, >, >=)", secondTerm.type, "y", lineNum);
                if(firstTerm.type == Types.intType && secondTerm.type == Types.intType){
                    // OK
                }else{
                    error("<, =<, > and >= expressions must have terms with int types");
                }
            }

        }
    }

    @Override void genCode(FuncDecl curFunc) {
        firstTerm.genCode(curFunc);
        if(relOpr != null){
            Code.genInstr("","pushl","%eax","");
            secondTerm.genCode(curFunc);
            Code.genInstr("","popl","%ecx","");
            Code.genInstr("","cmpl","%eax,%ecx","");
            Code.genInstr("","movl","$0,%eax","");
            if(relOpr.oprToken == equalToken)
                Code.genInstr("","sete","%al","");
            else if(relOpr.oprToken == notEqualToken)
                Code.genInstr("","setne","%al","");
            else if(relOpr.oprToken == lessToken)
                Code.genInstr("","setl","%al","");
            else if(relOpr.oprToken == lessEqualToken)
                Code.genInstr("","setle","%al","");
            else if(relOpr.oprToken == greaterToken)
                Code.genInstr("","setg","%al","");
            else if(relOpr.oprToken == greaterEqualToken)
                Code.genInstr("","setge","%al","");
        }
    }

    static Expression parse() {
	Log.enterParser("<expression>");

	Expression e = new Expression();
	e.firstTerm = Term.parse();
	if (Token.isRelOperator(Scanner.curToken)) {
	    e.relOpr = RelOpr.parse();
	    e.secondTerm = Term.parse();
        e.type = Types.intType;
	}else{
        e.type = Types.intType;
    }

	Log.leaveParser("</expression>");
	return e;
    }

    @Override void printTree() {
        if(firstTerm != null)
            firstTerm.printTree();
        if(relOpr != null && secondTerm != null){
            relOpr.printTree();
            secondTerm.printTree();
        }
    }
}

/*
 * A <primary>
 */
class Primary extends Factor {
    @Override void genCode(FuncDecl curFunc) {
        o.genCode(curFunc);
        if(prefixOpr == '-')
            Code.genInstr("","negl","%eax","");
        if(prefixOpr == '*')
            Code.genInstr("","movl","(%eax),%eax","");
        if(next != null)
            next.genCode(curFunc);
    }

    static Primary parse() {
        Log.enterParser("<primary>");

        Primary p = new Primary();

        if(isPrefixOperator(Scanner.curToken)){
            Log.enterParser("<prefix opr>");
            p.prefixOpr = (Scanner.curToken == starToken ? '*' : '-');
            Scanner.skip(Scanner.curToken);
            Log.leaveParser("</prefix opr>");
        }

        p.o = Operand.parse();

        if(p.prefixOpr == '*')
            p.type = new PointerType(Types.intType);
        else
            p.type = Types.intType;

        Log.leaveParser("</primary>");
        return p;
    }

    @Override void printTree() {
        if(prefixOpr != ' '){
            Log.wTree("" + prefixOpr);
        }
        if(o != null)
            o.printTree();
        if(next != null)
            next.printTree();
    }
}

/*
 * A <factor>
 */
class Factor extends Term {
    @Override void genCode(FuncDecl curFunc) {
        Code.genInstr("","pushl","%eax","");
        if(factOpr == '/'){
            next.o.genCode(curFunc);
            if(next.prefixOpr == '-')
                Code.genInstr("","negl","%eax","");
            if(next.prefixOpr == '*')
                Code.genInstr("","movl","(%eax),%eax","");
            Code.genInstr("","movl","%eax,%ecx","");
            Code.genInstr("","popl","%eax","");
            Code.genInstr("","cdq","","");
            Code.genInstr("","idivl","%ecx","");
            if(next.next instanceof Factor){
                if(next.next.factOpr == '*'){
                    Code.genInstr("","pushl","%eax","");
                    next.next.next.o.genCode(curFunc);
                    if(next.prefixOpr == '-')
                        Code.genInstr("","negl","%eax","");
                    if(next.prefixOpr == '*')
                        Code.genInstr("","movl","(%eax),%eax","");
                    Code.genInstr("","movl","%eax,%ecx","");
                    Code.genInstr("","popl","%eax","");
                    Code.genInstr("","imull","%ecx,%eax","");
                    if(next.next.next.next != null)
                        next.next.next.next.genCode(curFunc);
                }
            }
        }else if(factOpr == '*'){
            next.genCode(curFunc);
            Code.genInstr("","movl","%eax,%ecx","");
            Code.genInstr("","popl","%eax","");
            Code.genInstr("","imull","%ecx,%eax","");
        }
    }

    static Factor parse() {
        Log.enterParser("<factor>");

        Factor f = Primary.parse();
        Term last = f;
        while(isFactorOperator(Scanner.curToken)){
            Log.enterParser("<factor opr>");
            last.next = new Factor();
            last.next.factOpr = (Scanner.curToken == starToken ? '*' : '/');
            last = last.next;
            Scanner.skip(Scanner.curToken);
            Log.leaveParser("</factor opr>");
            last.next = Primary.parse();
            last = last.next;
        }
        last = f;

        Log.leaveParser("</factor>");
        return f;
    }

    @Override void printTree() {
        if(factOpr != ' ')
            Log.wTree("" + factOpr);
        if(next != null)
            next.printTree();
    }
}

/*
 * A <term>
 */
class Term extends SyntaxUnit {
    char termOpr = ' ';
    char prefixOpr = ' ';
    char factOpr = ' ';
    Term next = null;
    Operand o;
    Type type;

    @Override void check(DeclList curDecls) {
        if(o!=null){
            o.check(curDecls);
            if(prefixOpr == '-'){
                Log.noteTypeCheck("- x", type, "x", lineNum);
                if(type.isSameType(Types.intType)){
                // OK
                }else{
                    error("Prefix-operator '-' needs to be bound to an integer");
                }
            }

            if(prefixOpr == '*'){
                Log.noteTypeCheck("* x", type, "x", lineNum);
                if(type instanceof PointerType){
                // OK
                }else{
                    error("Prefix-operator '*' needs to be bound to a pointer");
                }
            }

            if(next != null){
                Log.noteTypeCheck("x + y (and -,*,/)", type, "x", lineNum);
                Log.noteTypeCheck("x + y (and -,*,/)", next.next.type, "y", lineNum);
                if(next.factOpr == '*' || next.factOpr == '/' || next.termOpr == '+' || next.termOpr == '-'){
                    if(next.next.type == Types.intType && (type.getElemType() == Types.intType || type == Types.intType)){
                // OK
                    }else{
                        error("Term and factor operators needs to be bound by two operands of type int");
                    }
                }
            }
        }
        if(next != null)
            next.check(curDecls);
    }

    @Override void genCode(FuncDecl curFunc) {
        Code.genInstr("","pushl","%eax","");
        if(next != null)
            next.genCode(curFunc);
        if(termOpr == '+'){
            Code.genInstr("","movl","%eax,%ecx","");
            Code.genInstr("","popl","%eax","");
            Code.genInstr("","addl","%ecx,%eax","");
        }else if(termOpr == '-'){
            Code.genInstr("","movl","%eax,%ecx","");
            Code.genInstr("","popl","%eax","");
            Code.genInstr("","subl","%ecx,%eax","");
        }
    }

    static Term parse() {
        Log.enterParser("<term>");

        Term t = new Term();
        Term last;
        last = t.next;

        t = Factor.parse();

        last = t;
        while(isPrefixOperator(Scanner.curToken) || isOperand(Scanner.curToken) || isFactorOperator(Scanner.curToken) || isTermOperator(Scanner.curToken)){
            if(isTermOperator(Scanner.curToken)){
                last.next = new Term();
                Log.enterParser("<term opr>");
                last.next.termOpr = (Scanner.curToken == subtractToken ? '-' : '+');
                Scanner.skip(Scanner.curToken);
                Log.leaveParser("</term opr>");
            }else
                last.next = Factor.parse();
            while(last.next != null)
                last = last.next;
        }
        Log.leaveParser("</term>");
        return t;
    }

    @Override void printTree() {
        if(termOpr != ' ')
            Log.wTree("" + termOpr);
        if(next != null)
            next.printTree();
    }
}

/*
 * An <operator>
 */
abstract class Operator extends SyntaxUnit {
    Operator nextOpr = null;
    Token oprToken;

    @Override void check(DeclList curDecls) {}  // Never needed.
}

/*
 * A <rel opr> (==, !=, <, <=, > or >=).
 */
class RelOpr extends Operator {
    @Override void genCode(FuncDecl curFunc) {
	Code.genInstr("", "popl", "%ecx", "");
	Code.genInstr("", "cmpl", "%eax,%ecx", "");
	Code.genInstr("", "movl", "$0,%eax", "");
	switch (oprToken) {
	case equalToken:        
	    Code.genInstr("", "sete", "%al", "Test ==");  break;
	case notEqualToken:
	    Code.genInstr("", "setne", "%al", "Test !=");  break;
	case lessToken:
	    Code.genInstr("", "setl", "%al", "Test <");  break;
	case lessEqualToken:
	    Code.genInstr("", "setle", "%al", "Test <=");  break;
	case greaterToken:
	    Code.genInstr("", "setg", "%al", "Test >");  break;
	case greaterEqualToken:
	    Code.genInstr("", "setge", "%al", "Test >=");  break;
	}
    }

    static RelOpr parse() {
	Log.enterParser("<rel opr>");

	RelOpr ro = new RelOpr();
	ro.oprToken = Scanner.curToken;
	Scanner.readNext();

	Log.leaveParser("</rel opr>");
	return ro;
    }

    @Override void printTree() {
	String op = "?";
	switch (oprToken) {
	case equalToken:        op = "==";  break;
	case notEqualToken:     op = "!=";  break;
	case lessToken:         op = "<";   break;
	case lessEqualToken:    op = "<=";  break;
	case greaterToken:      op = ">";   break;
	case greaterEqualToken: op = ">=";  break;
	}
	Log.wTree(" " + op + " ");
    }
}

/*
 * An <operand>
 */
abstract class Operand extends SyntaxUnit {
    Operand nextOperand = null;
    Type type;

    static Operand parse() {
	Log.enterParser("<operand>");

	Operand o = null;
	if (Scanner.curToken == numberToken) {
	    o = Number.parse();
	} else if (Scanner.curToken==nameToken && Scanner.nextToken==leftParToken) {
	    o = FunctionCall.parse();
	} else if (Scanner.curToken == nameToken) {
	    o = Variable.parse();
	} else if (Scanner.curToken == ampToken) {
	    o = Address.parse();
	} else if (Scanner.curToken == leftParToken) {
	    o = InnerExpr.parse();  
	} else {
	    Error.expected("An operand");
	}

	Log.leaveParser("</operand>");
	return o;
    }
}

/*
 * A <assign statm>.
 */
class AssignStatm extends Statement {
    Assignment a;

    @Override void check(DeclList curDecls) {
        a.check(curDecls);
    }

    @Override void genCode(FuncDecl curFunc) {
        a.genCode(curFunc);
    }

    static AssignStatm parse() {
        Log.enterParser("<assign statm>");

        AssignStatm as = new AssignStatm();
        as.a = Assignment.parse();
        Scanner.skip(semicolonToken);

        Log.leaveParser("</assign statm>");
        return as;
    }

    @Override void printTree() {
        a.printTree();
        Log.wTreeLn(";");
    }
}

/*
 * A <call-statm>.
 */
class CallStatm extends Statement {
    FunctionCall call;

    @Override void check(DeclList curDecls) {
        call.check(curDecls);
    }

    @Override void genCode(FuncDecl curFunc) {
        call.genCode(curFunc);
    }

    static CallStatm parse() {
        Log.enterParser("<call-statm>");

        CallStatm cs = new CallStatm();
        cs.call = FunctionCall.parse();
        Scanner.skip(semicolonToken);

        Log.leaveParser("</call-statm>");
        return cs;
    }

    @Override void printTree() {
        call.printTree();
        Log.wTreeLn(";");
    }
}

/*
 * A <function call>.
 */
class FunctionCall extends Operand {
    String funcName;
    ExprList paramList;

    @Override void check(DeclList curDecls) {
        Declaration funcDecl = curDecls.findDecl(funcName, this);
        Log.noteBinding(funcName, lineNum, funcDecl.lineNum);
        int count = 0;
        Expression cur = paramList.firstExpr;
        while(cur != null){
            count++;
            cur.check(curDecls);
            cur = cur.nextExpr;
        }

        funcDecl.checkWhetherFunction(count, this);
    }

    /**
     * Denne metoden kan få mindre kompleksitet ved å gjøre paramList om til en LIFO-liste
     */
    @Override void genCode(FuncDecl curFunc) {
        Expression e = paramList.firstExpr;
        int count = 0;
        while(e != null){
            count++;
            e = e.nextExpr;
        }
        e = paramList.firstExpr;

        int size = count;
        while(count > 0){
            int i = count;
            e = paramList.firstExpr;
            while(i > 1){
                e = e.nextExpr;
                i--;
            }
            e.genCode(curFunc);
            Code.genInstr("","pushl","%eax","");
            count--;
        }
        Code.genInstr("","call",funcName,"Call function " + funcName);
        if(size > 0)
            Code.genInstr("","addl","$"+size*4+",%esp","");
    }

    static FunctionCall parse() {
        Log.enterParser("<function call>");

        FunctionCall fc = new FunctionCall();
        fc.funcName = Scanner.curName;
        Scanner.skip(nameToken);
        Scanner.skip(leftParToken);
        fc.paramList = ExprList.parse();
        Scanner.skip(rightParToken);

        Log.leaveParser("</function call>");
        return fc;
    }

    @Override void printTree() {
        Log.wTree(funcName + "(");
        paramList.printTree();
        Log.wTree(")");
    }
}

/*
 * A <number>.
 */
class Number extends Operand {
    int numVal;

    @Override void check(DeclList curDecls) {
       // OK
    }
	
    @Override void genCode(FuncDecl curFunc) {
	Code.genInstr("", "movl", "$"+numVal+",%eax", ""+numVal); 
    }

    static Number parse() {
        Log.enterParser("<number>");

        Number n = new Number();
        n.numVal = Scanner.curNum;
        Scanner.skip(numberToken);

        Log.leaveParser("</number>");
        return n;
    }

    @Override void printTree() {
       Log.wTree("" + numVal);
    }
}

/*
 * A <variable>.
 */
class Variable extends Operand {
    String varName;
    VarDecl declRef = null;
    Expression index = null;

    @Override void check(DeclList curDecls) {
        Declaration d = curDecls.findDecl(varName,this);
        d.checkWhetherVariable(this);
        declRef = (VarDecl)d;
        Log.noteBinding(varName, lineNum, declRef.lineNum);

        if (index == null) {
           type = d.type;
       } else {
           index.check(curDecls);
           Log.noteTypeCheck("a[e]", d.type, "a", index.type, "e", lineNum);

           if (index.type == Types.intType) {
		// OK
           } else {
              error("Only integers may be used as index.");
          }
          if (d.type.mayBeIndexed()) {
		// OK
          } else {
              error("Only arrays and pointers may be indexed.");
          }
          type = d.type.getElemType();
      }
    }

    @Override void genCode(FuncDecl curFunc) {
        if (index == null) {
            if(declRef.type instanceof ArrayType)
                Code.genInstr("", "leal", declRef.assemblerName+",%eax", varName);
            else
                Code.genInstr("", "movl", declRef.assemblerName+",%eax", varName);
        } else {
            index.genCode(curFunc);
            if (declRef.type instanceof ArrayType) {
                if(declRef instanceof ParamDecl || declRef instanceof LocalVarDecl){
                    Code.genInstr("", "leal", declRef.assemblerName+",%edx", varName+"[...]");
                }else{
                    Code.genInstr("", "leal", declRef.assemblerName+",%edx", 
                      varName+"[...]");
                }
            } else {
                if(declRef instanceof ParamDecl || declRef instanceof LocalVarDecl){
                    Code.genInstr("", "movl", declRef.assemblerName+",%edx", varName+"[...]");
                }else{
                    Code.genInstr("", "movl", declRef.assemblerName+",%edx", 
                      varName+"[...]");
                }
            }
            Code.genInstr("", "movl", "(%edx,%eax,4),%eax", "");
        }
    }

    void genAddressCode(FuncDecl curFunc) {
	// Generate code to load the _address_ of the variable
	// rather than its value.
	if (index == null) {
	    Code.genInstr("", "leal", declRef.assemblerName+",%eax", varName);
	} else {
	    index.genCode(curFunc);
	    if (declRef.type instanceof ArrayType) {
		Code.genInstr("", "leal", declRef.assemblerName+",%edx", 
			      varName+"[...]");
	    } else {
		Code.genInstr("", "movl", declRef.assemblerName+",%edx", 
			      varName+"[...]");
	    }
	    Code.genInstr("", "leal", "(%edx,%eax,4),%eax", "");
	}
    }

    static Variable parse() {
        Log.enterParser("<variable> ");

        Variable v = new Variable();
        v.varName = Scanner.curName;
        Scanner.skip(nameToken);
        if(Scanner.curToken == leftBracketToken){
            Scanner.skip(leftBracketToken);
            v.index = Expression.parse();
            Scanner.skip(rightBracketToken);
        }
        v.type = Types.intType;

        Log.leaveParser("</variable>");
        return v;
    }

    @Override void printTree() {
        Log.wTree(varName);
        if(index != null){
            Log.wTree("[");
            index.printTree();
            Log.wTree("]");
        }
    }
}

/*
 * An <address>.
 */
class Address extends Operand {
    Variable var;

    @Override void check(DeclList curDecls) {
	var.check(curDecls);
	type = new PointerType(var.type);
    }

    @Override void genCode(FuncDecl curFunc) {
	var.genAddressCode(curFunc);
    }

    static Address parse() {
       Log.enterParser("<address>");

       Address a = new Address();
       Scanner.skip(ampToken);
       a.var = Variable.parse();

       Log.leaveParser("</address>");
       return a;
    }

    @Override void printTree() {
       Log.wTree("&");  var.printTree();
    }
}

/*
 * An <inner expr>.
 */
class InnerExpr extends Operand {
    Expression expr;

    @Override void check(DeclList curDecls) {
	expr.check(curDecls);
	type = expr.type;
    }

    @Override void genCode(FuncDecl curFunc) {
	expr.genCode(curFunc);
    }

    static InnerExpr parse() {
       Log.enterParser("<inner expr>");

       InnerExpr ie = new InnerExpr();
       Scanner.skip(leftParToken);
       ie.expr = Expression.parse();
       Scanner.skip(rightParToken);

       Log.leaveParser("</inner expr>");
       return ie;
    }

    @Override void printTree() {
       Log.wTree("(");  expr.printTree();  Log.wTree(")");
    }
}