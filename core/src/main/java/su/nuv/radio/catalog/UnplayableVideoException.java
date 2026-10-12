package su.nuv.radio.catalog;

/**
 * This very video cannot be played by anyone asking the way we do: removed, private, blocked, or
 * behind an age check. Asking again or asking differently will not help; another upload of the
 * same song might.
 */
public class UnplayableVideoException extends CatalogException {

    public UnplayableVideoException(String playerMessage) {
        super(playerMessage);
    }
}
