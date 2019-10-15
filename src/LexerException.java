

public class LexerException extends RuntimeException {

    public LexerException(String message, int yyline, int yycolumn) {
        super("Error at line " + (yyline + 1) + " column " + (yycolumn + 1) + ": " + message);
    }
}