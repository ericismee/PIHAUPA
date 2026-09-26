package piHAUPA;

public enum RescanPolicy {
    TIGHT("PIHAUPA mới - Tight Eq.(6)"),
    ORIGINAL("Thuật toán cũ - Original Eq.(5)");

    private final String label;

    RescanPolicy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
