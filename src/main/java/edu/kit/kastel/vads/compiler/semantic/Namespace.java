package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
        this.allDefined = parent.isAllDefined();
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

    public void setAllDefined(T value) {
        this.allDefined = true;
        for (var entry : this.content.entrySet()) {
            this.content.merge(entry.getKey(), value, (_, replacement) -> replacement);
        }
        if (parent != null) {
            parent.setAllDefined(value);
        }
    }

    public boolean isAllDefined() {
        return allDefined;
    }

    public boolean isOnlyInitializedHere(Name name) {
        return (parent != null) &&
               (parent.content.get(name) == VariableStatusAnalysis.VariableStatus.DECLARED) &&
               (this.content.get(name) == VariableStatusAnalysis.VariableStatus.INITIALIZED);
    }

    public Set<Name> getValues() {
        return this.content.keySet();
    }
}
