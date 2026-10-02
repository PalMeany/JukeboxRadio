package su.nuv.radio.catalog.model;

/** What a link turned out to be. */
public sealed interface ResolveResult {

    record Single(TrackMeta track) implements ResolveResult {
    }

    record Many(TrackCollection collection) implements ResolveResult {
    }
}
