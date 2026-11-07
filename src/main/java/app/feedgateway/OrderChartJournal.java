package app.feedgateway;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Revision payload ownership. Retired entries stay charged until every delivery pin releases. */
final class OrderChartJournal implements AutoCloseable {
    static final long GLOBAL_BYTES=8L*1024*1024;
    static final int MAX_SCOPES=30, SCOPE_BYTES=256*1024, SCOPE_ENTRIES=128;
    // Conservative envelope/array bookkeeping charge in addition to the owned wire representation.
    static final int ENTRY_OVERHEAD=256, PIN_OVERHEAD=64;
    static final long PIN_BYTES=4L*1024*1024;
    private final Set<Scope> scopes=new HashSet<>();
    private final long globalLimit;
    private long bytes, pinBytes;
    private boolean closed;

    OrderChartJournal(){this(GLOBAL_BYTES);}
    OrderChartJournal(long globalLimit){if(globalLimit<1)throw new IllegalArgumentException("journal capacity");this.globalLimit=globalLimit;}
    synchronized Scope attach(){if(closed||scopes.size()>=MAX_SCOPES)return null;var scope=new Scope();scopes.add(scope);return scope;}
    synchronized long reservedBytes(){return bytes;}
    synchronized long reservedPinBytes(){return pinBytes;}
    synchronized int retainedScopes(){return scopes.size();}

    final class Scope implements AutoCloseable {
        private final ArrayDeque<Entry> replay=new ArrayDeque<>();
        private final Set<Entry> owned=new HashSet<>();
        private long reserved, revision;
        private boolean active=true;
        private Scope(){}
        /** False is an explicit nonreplayable publication; the source owner must reset recipients. */
        boolean append(long nextRevision,byte[] payload){
            Objects.requireNonNull(payload);
            synchronized(OrderChartJournal.this){
                if(closed||!active||nextRevision<=0||revision==Long.MAX_VALUE||nextRevision!=revision+1)return false;
                long charge=(long)payload.length+ENTRY_OVERHEAD;
                if(payload.length==0||charge>SCOPE_BYTES||charge>globalLimit)return false;
                long freed=0;int freedCount=0;var evict=new ArrayList<Entry>();
                for(Entry entry:replay){
                    if(reserved-freed+charge<=SCOPE_BYTES&&owned.size()-freedCount+1<=SCOPE_ENTRIES&&bytes-freed+charge<=globalLimit)break;
                    evict.add(entry);if(entry.pins==0){freed+=entry.charge;freedCount++;}
                }
                if(reserved-freed+charge>SCOPE_BYTES||owned.size()-freedCount+1>SCOPE_ENTRIES||bytes-freed+charge>globalLimit)return false;
                // Allocate before mutating the admitted cursor or eviction set.
                Entry fresh=new Entry(nextRevision,payload.clone(),charge);
                for(Entry entry:evict){replay.removeFirst();entry.retired=true;if(entry.pins==0)release(entry);}
                replay.addLast(fresh);owned.add(fresh);reserved+=charge;bytes+=charge;revision=nextRevision;return true;
            }
        }
        /** An oversized source change advances authority but deliberately invalidates earlier cursors. */
        boolean nonreplayable(long nextRevision){
            synchronized(OrderChartJournal.this){
                if(closed||!active||nextRevision<=0||revision==Long.MAX_VALUE||nextRevision!=revision+1)return false;
                for(Entry entry:replay){entry.retired=true;if(entry.pins==0)release(entry);}
                replay.clear();revision=nextRevision;return true;
            }
        }
        /** Null means unavailable/gapped; an empty replay is a valid current cursor. */
        List<Pin> pinAfter(long cursor){
            synchronized(OrderChartJournal.this){
                if(closed||!active||cursor<0||cursor>revision||cursor<(replay.isEmpty()?revision:replay.getFirst().revision-1))return null;
                long count=replay.stream().filter(entry->entry.revision>cursor).count();
                if(count*PIN_OVERHEAD>PIN_BYTES-pinBytes)return null;
                var pins=new ArrayList<Pin>();
                try{for(Entry entry:replay)if(entry.revision>cursor){var pin=new Pin(this,entry);pins.add(pin);entry.pins++;pinBytes+=PIN_OVERHEAD;}return List.copyOf(pins);}
                catch(RuntimeException|Error failure){for(Pin pin:pins)pin.close();throw failure;}
            }
        }
        long revision(){synchronized(OrderChartJournal.this){return revision;}}
        long reservedBytes(){synchronized(OrderChartJournal.this){return reserved;}}
        private void release(Entry entry){if(!owned.remove(entry))return;reserved-=entry.charge;bytes-=entry.charge;if(!active&&owned.isEmpty())scopes.remove(this);}
        @Override public void close(){synchronized(OrderChartJournal.this){if(!active)return;active=false;replay.clear();for(Entry entry:List.copyOf(owned)){entry.retired=true;if(entry.pins==0)release(entry);}if(owned.isEmpty())scopes.remove(this);}}
    }
    private static final class Entry {
        final long revision,charge;final byte[] payload;int pins;boolean retired;
        Entry(long revision,byte[] payload,long charge){this.revision=revision;this.payload=payload;this.charge=charge;}
    }
    final class Pin implements AutoCloseable {
        private final Scope scope;private final Entry entry;private boolean released;
        private Pin(Scope scope,Entry entry){this.scope=scope;this.entry=entry;}
        long revision(){return entry.revision;}
        // No public raw array: the codec consumes under this pin and accounts its own output copies.
        int size(){synchronized(OrderChartJournal.this){check();return entry.payload.length;}}
        void copyTo(byte[] target,int offset){synchronized(OrderChartJournal.this){check();System.arraycopy(entry.payload,0,target,offset,entry.payload.length);}}
        private void check(){if(released)throw new IllegalStateException("released journal pin");}
        @Override public void close(){synchronized(OrderChartJournal.this){if(released)return;released=true;entry.pins--;pinBytes-=PIN_OVERHEAD;if(entry.retired&&entry.pins==0)scope.release(entry);}}
    }
    @Override public void close(){synchronized(this){if(closed)return;closed=true;for(Scope scope:List.copyOf(scopes))scope.close();}}
}
