

import java_cup.runtime.ComplexSymbolFactory;
import java_cup.runtime.Symbol;

public class NamedSymbolFactory extends ComplexSymbolFactory {

    @Override
    public Symbol newSymbol(String name, int id, Location left, Location right, Object o) {
        ComplexSymbol symbol = (ComplexSymbol) super.newSymbol(name, id, left, right, o);
        if (o instanceof Ast.Locatable) {
            Ast.Locatable locatable = (Ast.Locatable) o;
            locatable.setPosition(new Ast.Location(symbol.getLeft().getLine(), symbol.getLeft().getColumn()));
        }
        return symbol;
    }

    @Override
    public Symbol newSymbol(String name, int id, Symbol left, Object o) {
        ComplexSymbol symbol = (ComplexSymbol) super.newSymbol(name, id, left, o);
        if (o instanceof Ast.Locatable) {
            Ast.Locatable locatable = (Ast.Locatable) o;
            locatable.setPosition(new Ast.Location(symbol.getLeft().getLine(), symbol.getLeft().getColumn()));
        }
        return symbol;
    }

    @Override
    public Symbol newSymbol(String name, int id, Symbol left, Symbol right, Object o) {
        ComplexSymbol symbol = (ComplexSymbol) super.newSymbol(name, id, left, right, o);
        if (o instanceof Ast.Locatable) {
            Ast.Locatable locatable = (Ast.Locatable) o;
            locatable.setPosition(new Ast.Location(symbol.getLeft().getLine(), symbol.getLeft().getColumn()));
        }
        return symbol;
    }

}