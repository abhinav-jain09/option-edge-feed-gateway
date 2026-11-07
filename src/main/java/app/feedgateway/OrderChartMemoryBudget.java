package app.feedgateway;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Shared source/serialization admission. Journal and wire-copy categories have separate owners. */
final class OrderChartMemoryBudget implements AutoCloseable {
    enum Category {
        SOURCE(32L*1024*1024), SNAPSHOT(8L*1024*1024), PENDING(8L*1024*1024);
        final long ceiling;
        Category(long ceiling){this.ceiling=ceiling;}
    }
    static final int MAX_RESERVATIONS=128;
    private final Object lock=new Object();
    private final EnumMap<Category,Long> bytes=new EnumMap<>(Category.class);
    private final Set<Reservation> owners=new HashSet<>();
    private final Runnable released;
    private boolean closed;
    OrderChartMemoryBudget(Runnable released){this.released=Objects.requireNonNull(released);for(Category c:Category.values())bytes.put(c,0L);}
    /** Reserve the conservative maximum before allocation. Refusal leaves all existing ownership intact. */
    Reservation reserve(Category category,long amount){
        Objects.requireNonNull(category);
        synchronized(lock){
            if(closed||amount<1||amount>category.ceiling||owners.size()>=MAX_RESERVATIONS||bytes.get(category)>category.ceiling-amount)return null;
            var owner=new Reservation(category,amount);owners.add(owner);bytes.put(category,bytes.get(category)+amount);return owner;
        }
    }
    long reserved(Category category){synchronized(lock){return bytes.get(Objects.requireNonNull(category));}}
    int reservations(){synchronized(lock){return owners.size();}}
    final class Reservation implements AutoCloseable {
        private final Category category;
        private long amount;
        private boolean active=true;
        private Reservation(Category category,long amount){this.category=category;this.amount=amount;}
        long bytes(){synchronized(lock){return active?amount:0;}}
        /** Reconcile actual owned bytes. An increase must succeed before growing the allocation. */
        boolean resize(long actual){
            boolean freed;
            synchronized(lock){
                if(closed||!active||actual<1||actual>category.ceiling)return false;
                long used=OrderChartMemoryBudget.this.bytes.get(category),change=actual-amount;
                if(change>0&&used>category.ceiling-change)return false;
                OrderChartMemoryBudget.this.bytes.put(category,used+change);freed=change<0;amount=actual;
            }
            if(freed)notifyReleased();return true;
        }
        @Override public void close(){
            synchronized(lock){if(!active)return;active=false;OrderChartMemoryBudget.this.bytes.put(category,OrderChartMemoryBudget.this.bytes.get(category)-amount);owners.remove(this);amount=0;}
            notifyReleased();
        }
    }
    private void notifyReleased(){try{released.run();}catch(RuntimeException|Error ignored){/* A bounded publication watchdog retries parked work. */}}
    /** Outstanding allocations remain charged until their actual owner releases them. */
    @Override public void close(){synchronized(lock){closed=true;}}
}
