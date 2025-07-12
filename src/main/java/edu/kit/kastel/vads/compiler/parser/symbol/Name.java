package edu.kit.kastel.vads.compiler.parser.symbol;

import edu.kit.kastel.vads.compiler.lexer.Identifier;
import edu.kit.kastel.vads.compiler.lexer.Keyword;

public sealed interface Name permits IdentName, KeywordName, FuncIdentName {

    static Name forKeyword(Keyword keyword) {
        return new KeywordName(keyword.type());
    }

    static Name forIdentifier(Identifier identifier) {
        return new IdentName(identifier.value());
    }

    static Name forFuncIdentifier(Identifier identifier) {
        return new FuncIdentName(identifier.value());
    }

    String asString();
}
