
import java_cup.runtime.Symbol;
import java_cup.runtime.ComplexSymbolFactory;
import java_cup.runtime.ComplexSymbolFactory.Location;

%%
%public
%class Lexer
%cup
%extends sym
%char
%line
%column

%{
    private StringBuilder string = new StringBuilder();
    private ComplexSymbolFactory symbolFactory;

    public Lexer(java.io.Reader in, ComplexSymbolFactory sf) {
    	this(in);
        symbolFactory = sf;
    }

    private Symbol symbol(String name, int sym) {
        Location left = new Location(yyline + 1, yycolumn + 1, yychar);
        Location right = new Location(yyline + 1, yycolumn + yylength(), yychar + yylength());
        return symbolFactory.newSymbol(name, sym, left, right);
    }

    private Symbol symbol(String name, int sym, Object val) {
        Location left = new Location(yyline + 1, yycolumn + 1, yychar);
        Location right = new Location(yyline + 1, yycolumn + yylength(), yychar + yylength());
        return symbolFactory.newSymbol(name, sym, left, right, val);
    }
%}

%eofval{
     return symbolFactory.newSymbol("EOF", EOF, new Location(yyline+1,yycolumn+1,yychar), new Location(yyline+1,yycolumn+1,yychar+1));
%eofval}


Ident = [a-z][a-zA-Z0-9_]*
CName = [A-Z][a-zA-Z0-9_]*
IntLiteral = [0-9]+
new_line = \r|\n|\r\n
white_space = {new_line} | [ \t\f]

%state STRINGLIT
%state SINGLELINE_COMMENT
%state MULTILINE_COMMENT

%%

<YYINITIAL>{
    /* keywords */
    "class" { return symbol("class", sym.CLASS); }
    "this" { return symbol("this", sym.THIS); }
    "new" { return symbol("new", sym.NEW); }
    "main" { return symbol("main", sym.MAIN); }
    "Void" { return symbol("Void", sym.VOID); }
    "Int" { return symbol("Int", sym.INT); }
    "Bool" { return symbol("Bool", sym.BOOL); }
    "String" { return symbol("String", sym.STRING); }
    "if" { return symbol("if", sym.IF); }
    "else" { return symbol("else", sym.ELSE); }
    "while" { return symbol("while", sym.WHILE); }
    "readln" { return symbol("readln", sym.READLN); }
    "println" { return symbol("println", sym.PRINTLN); }
    "return" { return symbol("return", sym.RETURN); }

    /* literals */
    "true" { return symbol("TRUE", sym.TRUE); }
    "false" { return symbol("FALSE", sym.FALSE); }
    "null" { return symbol("NULL", sym.NULL); }
    {IntLiteral}      { return symbol("Intconst", sym.INTEGER_LITERAL, new Integer(Integer.parseInt(yytext()))); }
    \"   { yybegin(STRINGLIT); string.setLength(0); }

    /* identifiers */
    {Ident}     { return symbol("Ident:" + yytext(), IDENT, yytext()); }
    {CName}     { return symbol("CName:" + yytext(), CNAME, yytext()); }

    /* separators */
    "("               { return symbol("(", sym.LPAREN); }
    ")"               { return symbol(")", sym.RPAREN); }
    "{"               { return symbol("{", sym.LBRACE); }
    "}"               { return symbol("}", sym.RBRACE); }
    ";"               { return symbol(";", sym.SEMICOLON); }
    "."               { return symbol(".", sym.DOT); }
    ","               { return symbol(",", sym.COMMA); }

    /* operators */
    "+"               { return symbol("+", sym.PLUS); }
    "-"               { return symbol("+", sym.MINUS); }
    "*"               { return symbol("+", sym.TIMES); }
    "/"               { return symbol("+", sym.DIV); }
    "<"               { return symbol("<", sym.LT); }
    ">"               { return symbol(">", sym.GT); }
    "<="              { return symbol("<=", sym.LEQ); }
    ">="              { return symbol(">=", sym.GEQ); }
    "=="              { return symbol("==", sym.EQ); }
    "="               { return symbol("=", sym.ASSIGN); }
    "!="              { return symbol("!=", sym.NEQ); }
    "!"               { return symbol("!", sym.NOT); }
    "||"              { return symbol("||", sym.OROR); }
    "&&"              { return symbol("&&", sym.ANDAND); }

    /* comments */
    "/*"              { yybegin(MULTILINE_COMMENT); }
    "//"              { yybegin(SINGLELINE_COMMENT); }

    {white_space}     { /* ignore */ }
}

<STRINGLIT> {
    \" { yybegin(YYINITIAL); return symbol("STRING_LITERAL", sym.STRING_LITERAL, string.toString()); }
    [^\n\r\"\\]+                   { string.append( yytext() ); }
    \\t                            { string.append('\t'); }
    \\n                            { string.append('\n'); }

    \\r                            { string.append('\r'); }
    \\b                            { string.append('\b'); }
    \\\"                           { string.append('\"'); }
    \\\\                           { string.append('\\'); }
    \\[0-3]?[0-7]?[0-7]            { char val = (char) Integer.parseInt(yytext().substring(1), 8); string.append(val); }
    \\x[0-9a-f]?[0-9a-f]           { char val = (char) Integer.parseInt(yytext().substring(2), 16); string.append(val); }
    \\. { throw new LexerException("Illegal escape sequence \"" + yytext() + "\"", yyline, yycolumn); }
    {new_line} { throw new LexerException("Unterminated string at end of line", yyline, yycolumn); }
}

<MULTILINE_COMMENT> {
    "*/"                           { yybegin(YYINITIAL); }
    .                              { /* ignore */ }
    {new_line}                     { /* ignore */ }
}

<MULTILINE_COMMENT><<EOF>> {
    throw new LexerException("Unterminated multiline comment at end of file.", yyline, yycolumn);
}

<SINGLELINE_COMMENT> {
    {new_line}                     { yybegin(YYINITIAL); }
    .                              { /* ignore */ }
}

/* error fallback */
[^]              {  throw new LexerException("Illegal character <"+ yytext()+">", yyline, yycolumn);
                 }
