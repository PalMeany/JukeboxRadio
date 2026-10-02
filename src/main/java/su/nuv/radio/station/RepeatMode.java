package su.nuv.radio.station;

public enum RepeatMode {
    OFF("Повтор выкл."),
    ALL("Повтор очереди"),
    ONE("Повтор трека");

    private final String label;

    RepeatMode(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }

    public RepeatMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }
}
