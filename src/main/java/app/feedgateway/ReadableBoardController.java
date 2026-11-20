package app.feedgateway;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <b>C-61 / C-63</b>: serves the readable board page as plain text, in-cluster.
 *
 * <p><b>Ungated here, and deliberately.</b> The gateway authorises route by route inside its
 * controllers; this route carries no gate, so it is readable by anything that can reach the gateway
 * inside the cluster. That is the same trust relationship the web application's own public board
 * upstream already documents — an unauthenticated in-cluster read, reached with no credential,
 * because the public tier strips the caller's. The <b>secret</b> is the web application's path, not
 * this one: that is where the owner's condition lives, and that is the only hop reachable from the
 * internet.
 *
 * <p><b>The page is complete in these bytes.</b> No markup, no script, nothing fetched afterwards —
 * the reviewer's web read runs no script, so anything loaded later would make the page unreadable
 * to the one reader it exists for.
 */
@RestController
public class ReadableBoardController {

    /** Not a secret, and not meant to be: the secret address is the web application's. */
    public static final String PATH = "/api/readable-board";

    private final FeedGatewayService service;

    public ReadableBoardController(FeedGatewayService service) {
        this.service = service;
    }

    /**
     * <b>C-66</b>: one page of the split record, by name, with an optional part number.
     *
     * <p>Why by name rather than one long page: the record no longer fits in something a reader
     * can take in, and C-66 splits rather than cuts. A name that is not a page answers with the
     * list of names, not with a 404 — a reader at a secret address cannot tell a 404 from a wrong
     * address, so this route never makes him guess which it was.
     */
    @GetMapping(PATH + "/{name}")
    public ResponseEntity<String> namedPage(
            @org.springframework.web.bind.annotation.PathVariable("name") String name,
            @org.springframework.web.bind.annotation.RequestParam(value = "part",
                    required = false, defaultValue = "1") int part) {
        String body = service.readableNamedPage(name, part);
        if (body == null) {
            body = "nosuchpage|" + name + "|pages are "
                    + String.join(", ", app.feedgateway.readable.ReadableBoardRenderer.PAGES)
                    + "\n";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store")
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(body);
    }

    @GetMapping(PATH)
    public ResponseEntity<String> page() {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                // A board cached for even a minute lies about its own write time, and the first
                // line of this page IS a timestamp.
                .header("Cache-Control", "no-store")
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(service.readableBoardPage());
    }
}
