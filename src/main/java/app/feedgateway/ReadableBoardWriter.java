package app.feedgateway;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <b>C-61</b>: writes the readable board page every 30 seconds.
 *
 * <p>The owner's words were "the file has to be written in every 30 seconds" and "it has to be by
 * the service", so this is the thing that does the writing and it is a service of the platform
 * rather than a hand. The cadence is configurable but its default is his figure.
 *
 * <p><b>It never lets a render failure stop the schedule.</b> A throw out of a {@code @Scheduled}
 * method silently cancels nothing in Spring, but it does lose that tick, and a page that stopped
 * being written while the gateway stayed healthy would read as a stalled market rather than a
 * stalled renderer. So the failure is caught, counted and left visible through the page's own
 * render time, which the first line carries.
 */
@Component
public class ReadableBoardWriter {

    private final FeedGatewayService service;
    private long failures;

    public ReadableBoardWriter(FeedGatewayService service) {
        this.service = service;
    }

    @Scheduled(
            initialDelayString = "${GATEWAY_READABLE_BOARD_MS:30000}",
            fixedDelayString = "${GATEWAY_READABLE_BOARD_MS:30000}")
    public void write() {
        try {
            service.renderReadableBoard();
        } catch (RuntimeException e) {
            failures++;
            // The class, never the message: a message can carry a record into a log line, and this
            // page's inputs include the owner's whole board.
            System.err.println("readable board not rendered this tick (" + e.getClass().getName()
                    + "); the previous page is still served and its own render time shows its age. "
                    + "failures=" + failures);
        }
    }

    /** Renders that threw, for a health read. */
    public long failures() {
        return failures;
    }
}
