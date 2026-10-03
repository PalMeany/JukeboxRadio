package su.nuv.radio.catalog;

/** A catalog failure whose message is safe to show to players. */
public class CatalogException extends RuntimeException {

    public CatalogException(String playerMessage) {
        super(playerMessage);
    }

    public CatalogException(String playerMessage, Throwable cause) {
        super(playerMessage, cause);
    }
}
