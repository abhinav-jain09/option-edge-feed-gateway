package app.feedgateway;

/** Structured assertion identity: no stack-trace text classifier and no runtime/service startup. */
public final class DirectionReaderNegativeProbe {
    public static void main(String[] args) {
        try {
            if(args.length!=1)throw new IllegalArgumentException("one test method required");
            var method=DirectionAvroRollingGatewayTest.class.getDeclaredMethod(args[0]);method.setAccessible(true);
            method.invoke(new DirectionAvroRollingGatewayTest());
            System.out.println("{\"status\":\"PASS\",\"assertion\":false}");
        } catch(Throwable error) {
            while(error instanceof java.lang.reflect.InvocationTargetException && error.getCause()!=null)error=error.getCause();
            boolean assertion=error instanceof AssertionError;
            System.out.println("{\"status\":\""+(assertion?"ASSERTION_KILLED":"INCIDENTAL_ERROR")+"\",\"assertion\":"+assertion+",\"type\":\""+error.getClass().getName()+"\"}");
            System.exit(assertion?1:2);
        }
    }
}
