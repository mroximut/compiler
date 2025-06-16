package edu.kit.kastel.vads.compiler.semantic;

import edu.kit.kastel.vads.compiler.parser.ast.NameTree;
import edu.kit.kastel.vads.compiler.parser.symbol.Name;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;

public class Namespace<T> {

    private final Map<Name, T> content;
    private final Namespace<T> parent;
    private boolean allDefined = false;
    private Set<Name> allValues;

    public Namespace() {
        this.content = new HashMap<>();
        this.parent = null;
        this.allValues = new HashSet<>();
    }

    public Namespace(Namespace<T> parent) {
        this.allValues = new HashSet<>();
        this.content = new HashMap<>();
        this.parent = parent;
        this.allDefined = parent.isAllDefined();
        this.allValues.addAll(parent.allValues);
    }

    public void put(NameTree name, T value, BinaryOperator<T> merger) {
        this.content.merge(name.name(), value, merger);
        this.allValues.add(name.name());
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
        //System.out.println(name);
        //System.out.println(parent.get(new NameTree(name, null)));
        //System.out.println(this.content.get(name));
        return (parent != null) &&
               (parent.get(new NameTree(name, null)) == VariableStatusAnalysis.VariableStatus.DECLARED) &&
               (this.content.get(name) == VariableStatusAnalysis.VariableStatus.INITIALIZED);
    }

    public Set<Name> getValues() {
        return this.allValues;
    }
}
