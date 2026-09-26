package piHAUPA;

import java.util.List;

public record PatternResult(List<String> items, double sumUtility, int length, PatternType type) {
    public double averageUtility() {
        return sumUtility / length;
    }

    public String displayName() {
        return String.join("", items);
    }
}

