package su.nuv.radio.catalog.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import su.nuv.radio.catalog.model.CollectionHit;
import su.nuv.radio.catalog.model.TrackCollection;
import su.nuv.radio.util.Http;
import su.nuv.radio.util.Json;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Album and playlist search on YouTube Music through its web client API, the one music.youtube.com
 * itself calls. Albums there are playlists ({@code OLAK5uy_...}), so every hit opens through
 * lavaplayer like a pasted playlist link.
 */
final class YtMusicSearch {

    private static final String URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false";
    /** Search filters of the web client (as in ytmusicapi): albums and playlists. */
    private static final String ALBUMS = "EgWKAQIYAWoMEA4QChADEAQQCRAF";
    private static final String PLAYLISTS = "EgWKAQIoAWoMEA4QChADEAQQCRAF";
    /** YouTube keeps accepting older web client versions for months; bump it if searches start failing. */
    private static final String CLIENT_VERSION = "1.20260901.01.00";

    private YtMusicSearch() {
    }

    /** Albums first, then playlists; a failed playlist search only drops the playlists. */
    static CompletableFuture<List<CollectionHit>> search(String query) {
        final CompletableFuture<List<CollectionHit>> albums = request(query, ALBUMS, TrackCollection.Kind.ALBUM);
        final CompletableFuture<List<CollectionHit>> playlists = request(query, PLAYLISTS, TrackCollection.Kind.PLAYLIST)
                .exceptionally(error -> List.of());
        return albums.thenCombine(playlists, (a, p) -> {
            final List<CollectionHit> all = new ArrayList<>(a);
            all.addAll(p);
            return all;
        });
    }

    private static CompletableFuture<List<CollectionHit>> request(String query, String filter, TrackCollection.Kind kind) {
        final JsonObject client = new JsonObject();
        client.addProperty("clientName", "WEB_REMIX");
        client.addProperty("clientVersion", CLIENT_VERSION);
        client.addProperty("hl", "ru");
        client.addProperty("gl", "RU");
        final JsonObject context = new JsonObject();
        context.add("client", client);
        final JsonObject body = new JsonObject();
        body.add("context", context);
        body.addProperty("query", query);
        body.addProperty("params", filter);
        final HttpRequest request = Http.request(URL)
                .header("Content-Type", "application/json")
                .header("Origin", "https://music.youtube.com")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return Http.getJson(request).thenApply(json -> {
            final List<CollectionHit> hits = new ArrayList<>();
            collect(json, kind, hits);
            return hits;
        });
    }

    /** Every result row, wherever the current response layout nests it. */
    private static void collect(JsonElement node, TrackCollection.Kind kind, List<CollectionHit> out) {
        if (node instanceof JsonObject object) {
            final JsonObject row = Json.obj(object, "musicResponsiveListItemRenderer");
            if (row != null) {
                final CollectionHit hit = hit(row, kind);
                if (hit != null) {
                    out.add(hit);
                }
                return;
            }
            for (String key : object.keySet()) {
                collect(object.get(key), kind, out);
            }
        } else if (node instanceof JsonArray array) {
            for (JsonElement element : array) {
                collect(element, kind, out);
            }
        }
    }

    private static CollectionHit hit(JsonObject row, TrackCollection.Kind kind) {
        final JsonArray columns = Json.arr(row, "flexColumns");
        final String title = column(columns, 0);
        if (title.isBlank()) {
            return null;
        }
        String playlistId = Json.str(Json.path(row, "overlay", "musicItemThumbnailOverlayRenderer", "content",
                "musicPlayButtonRenderer", "playNavigationEndpoint", "watchPlaylistEndpoint"), "playlistId");
        if (playlistId == null) {
            final String browseId = Json.str(Json.path(row, "navigationEndpoint", "browseEndpoint"), "browseId");
            if (browseId != null && browseId.startsWith("VL")) {
                playlistId = browseId.substring(2);
            }
        }
        if (playlistId == null) {
            return null;
        }
        final JsonArray thumbnails = Json.arr(Json.path(row, "thumbnail", "musicThumbnailRenderer", "thumbnail"),
                "thumbnails");
        final String cover = thumbnails.isEmpty() ? null : Json.str(thumbnails.get(thumbnails.size() - 1), "url");
        return new CollectionHit(YouTubeCatalog.ID, kind, title, column(columns, 1).replace(" • ", " · "), cover,
                "https://music.youtube.com/playlist?list=" + playlistId);
    }

    private static String column(JsonArray columns, int index) {
        if (index >= columns.size()) {
            return "";
        }
        final StringBuilder text = new StringBuilder();
        for (JsonObject run : Json.objects(Json.arr(Json.path(columns.get(index),
                "musicResponsiveListItemFlexColumnRenderer", "text"), "runs"))) {
            final String part = Json.str(run, "text");
            if (part != null) {
                text.append(part);
            }
        }
        return text.toString().strip();
    }
}
