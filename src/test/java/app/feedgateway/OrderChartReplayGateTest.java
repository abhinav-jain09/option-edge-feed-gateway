package app.feedgateway;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class OrderChartReplayGateTest {
    private final OrderChartReplayGate gate = new OrderChartReplayGate();
    private final TopicPartition p = new TopicPartition("bars",0), q = new TopicPartition("bars",1);
    private boolean ready() { return gate.read(() -> true, () -> false); }
    @Test void capabilityAndUncapturedOrPartialReplayNeverMeanComplete() {
        Object owner=new Object();assertFalse(ready());gate.begin(owner,List.of(p,q));
        gate.capture(owner,Map.of(p,5L));gate.advance(owner,t->100);assertFalse(ready());
        gate.capture(owner,Map.of(q,7L));gate.advance(owner,t->t.equals(p)?5:6);assertFalse(ready());
        gate.advance(owner,t->t.equals(p)?5:7);assertTrue(ready());
    }
    @Test void latePartitionReopensAndRetainsPriorBarriers() {
        Object owner=new Object();gate.begin(owner,List.of(p));gate.capture(owner,Map.of(p,5L));gate.advance(owner,t->5);assertTrue(ready());
        gate.begin(owner,List.of(q));assertFalse(ready());gate.capture(owner,Map.of(q,7L));
        gate.advance(owner,t->t.equals(p)?4:7);assertFalse(ready());gate.advance(owner,t->t.equals(p)?5:7);assertTrue(ready());
    }
    @Test void retryAndFailureFenceThePriorAttempt() {
        Object first=new Object(),next=new Object();gate.begin(first,List.of(p));gate.capture(first,Map.of(p,5L));gate.advance(first,t->5);assertTrue(ready());
        gate.invalidate();assertFalse(ready());gate.advance(first,t->100);assertFalse(ready());
        gate.begin(next,List.of(p));gate.capture(first,Map.of(p,0L));gate.advance(first,t->100);assertFalse(ready());
        gate.capture(next,Map.of(p,8L));gate.advance(next,t->{throw new IllegalStateException("unassigned");});assertFalse(ready());
        gate.advance(next,t->8);assertTrue(ready());
    }
    @Test void missingPartitionsRemainLoadingAndEmptyCapturedPartitionCanComplete() {
        Object owner=new Object();gate.begin(owner,List.of());gate.advance(owner,t->0);assertFalse(ready());
        gate.begin(owner,List.of(p));gate.capture(owner,Map.of(p,0L));gate.advance(owner,t->0);assertTrue(ready());
    }
}
