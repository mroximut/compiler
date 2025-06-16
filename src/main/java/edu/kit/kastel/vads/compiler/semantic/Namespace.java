package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

public class Namespace<T> {

    private final Map<Name, T> content;
    private final Namespace<T> parent;
    private boolean allDefined = false;

    public Namespace() {
        this.content = new HashMap<>();
        this.parent = null;
    }

    public Namespace(Namespace<T> parent) {
        this.content = new HashMap<>();
        this.parent = parent;
    }

    public void put(NameTree name, T value, BinaryOperator<T> merger) {
        this.content.merge(name.name(), value, merger);
    }

    public @Nullable T get(NameTree name) {
        T value = this.content.get(name.name());
        if (value == null && parent != null) {
            return parent.get(name);
        }
        return value;
    }

    public void setAllDefined(boolean allDefined) {
        this.allDefined = allDefined;
    }

    public boolean isAllDefined() {
        return allDefined;
    }
}
