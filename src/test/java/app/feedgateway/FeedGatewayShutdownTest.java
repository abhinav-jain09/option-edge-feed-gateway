package app.feedgateway;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedGatewayShutdownTest {
    @Test
    void completedExecutorIsNeverInterruptedDuringNormalShutdown() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(5L, TimeUnit.SECONDS)).thenReturn(true);

        FeedGatewayService.shutdownExecutorGracefully(executor);

        InOrder order = inOrder(executor);
        order.verify(executor).shutdown();
        order.verify(executor).awaitTermination(5L, TimeUnit.SECONDS);
        verify(executor, never()).shutdownNow();
    }

    @Test
    void stuckExecutorIsInterruptedOnlyAfterGracePeriodExpires() throws Exception {
        ExecutorService executor = mock(ExecutorService.class);
        when(executor.awaitTermination(5L, TimeUnit.SECONDS)).thenReturn(false, true);

        FeedGatewayService.shutdownExecutorGracefully(executor);

        InOrder order = inOrder(executor);
        order.verify(executor).shutdown();
        order.verify(executor).awaitTermination(5L, TimeUnit.SECONDS);
        order.verify(executor).shutdownNow();
        order.verify(executor).awaitTermination(5L, TimeUnit.SECONDS);
        verify(executor, times(2)).awaitTermination(5L, TimeUnit.SECONDS);
    }
}
