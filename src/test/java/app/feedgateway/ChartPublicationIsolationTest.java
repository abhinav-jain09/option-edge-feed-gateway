package app.feedgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import static org.junit.jupiter.api.Assertions.*;

class ChartPublicationIsolationTest {
    private static ThreadPoolExecutor pool(FeedGatewayService service,String method)throws Exception{
        var m=FeedGatewayService.class.getDeclaredMethod(method);m.setAccessible(true);return (ThreadPoolExecutor)m.invoke(service);
    }
    @Test void saturatedCompatibilityPoolCannotBlockChartAndWatchdogRetriesItsRejectedWake()throws Exception{
        var service=new FeedGatewayService(new GatewaySettings(),new ObjectMapper(),new HpsfGatewayViewMapper(),null);
        var release=new CountDownLatch(1);var blocked=new CountDownLatch(2);ThreadPoolExecutor chart=null,compat=null;
        try{
            chart=pool(service,"outboundIdleExecutor");compat=pool(service,"outboundCompatibilityExecutor");assertNotSame(chart,compat);assertEquals(2,chart.getMaximumPoolSize());assertEquals(2,compat.getMaximumPoolSize());assertEquals(256,compat.getQueue().remainingCapacity());
            for(int i=0;i<2;i++)compat.execute(()->{blocked.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            assertTrue(blocked.await(5,TimeUnit.SECONDS));for(int i=0;i<256;i++)compat.execute(()->{});assertThrows(RejectedExecutionException.class,()->((ThreadPoolExecutor)getField(service,"compatibilityPublicationExecutor")).execute(()->{}));
            var writes=new OutboundIdleDispatcherTest.Manual();WebSocketSession socket=ChartOutboundChannelTest.session();var c=new OutboundChannel(socket,writes,task->{},chart,compat,100,1<<20,new ChartOutboundChannelTest.Metrics(),ignored->{});
            @SuppressWarnings("unchecked") var channels=(Map<String,OutboundChannel>)getField(service,"outbound");channels.put(c.socketId(),c);
            var chartRan=new CountDownLatch(1);var compatRan=new CountDownLatch(1);assertTrue(c.onIdle(new Object(),compatRan::countDown));assertNotNull(c.registerIdleOwner(new Object(),chartRan::countDown,()->fail("chart")));
            assertTrue(c.enqueue("quote",null));writes.all();assertTrue(chartRan.await(5,TimeUnit.SECONDS));assertEquals(1,compatRan.getCount());
            compat.getQueue().clear();release.countDown();compat.submit(()->{}).get(5,TimeUnit.SECONDS);
            // Real service watchdog must reach both dispatchers, without a new market event.
            service.enforceOutboundWriteDeadlines();assertTrue(compatRan.await(5,TimeUnit.SECONDS));assertFalse(c.isClosed());
        }finally{release.countDown();service.stop();if(chart!=null)assertTrue(chart.isShutdown());if(compat!=null)assertTrue(compat.isShutdown());}
    }
    private static Object getField(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
}
