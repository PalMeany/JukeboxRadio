package su.nuv.radio.platform;

import net.kyori.adventure.text.Component;

import java.net.URI;
import java.util.UUID;

/** A resource pack to push to a player as an extra pack. */
public record PackOffer(UUID id, URI uri, String sha1, boolean required, Component prompt) {
}
