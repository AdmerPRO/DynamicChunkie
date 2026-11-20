package dev.dynamicchunkie.adaptive;
import dev.dynamicchunkie.EnvironmentScore;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentCacheTest {
    private EnvironmentScore result() {return new EnvironmentScore(.5,16,.5,.5,.5,false,20,false,0,.5,"Cave");}
    @Test void matchingRevisionReusesResultButTtlAndUpdatesInvalidate() {
        var c=new EnvironmentCache<EnvironmentScore>();var key=new EnvironmentCache.Key(0,0,0,0,0);
        var value=result();c.put(key,value,1_000_000_000L,4,128,1024*1024);
        assertSame(value,c.get(key,2_000_000_000L,4,3000));
        assertNull(c.get(key,2_000_000_000L,5,3000));
        c.put(key,value,1_000_000_000L,4,128,1024*1024);
        assertNull(c.get(key,5_000_000_000L,4,3000));
    }
    @Test void bothEntryAndMemoryBudgetsAreEnforced() {
        var c=new EnvironmentCache<EnvironmentScore>();
        for (int i=0;i<20;i++) c.put(new EnvironmentCache.Key(i,0,0,0,0),result(),i,1,128,3*1024);
        assertEquals(3,c.size());assertEquals(3072,c.chargedBytes());
        c.clear();assertEquals(0,c.size());
    }
    @Test void nearbyRecentEntriesWinOverFarUnusedEntries() {
        var c=new EnvironmentCache<EnvironmentScore>();var near=new EnvironmentCache.Key(0,0,0,0,0);
        c.put(new EnvironmentCache.Key(10000,0,0,0,0),result(),1,1,2,2048);
        c.put(near,result(),2,1,2,2048);
        c.get(near,3,1,1000);
        c.put(new EnvironmentCache.Key(1,0,0,0,0),result(),4,1,2,2048);
        assertNotNull(c.get(near,5,1,1000));
        assertNull(c.get(new EnvironmentCache.Key(10000,0,0,0,0),5,1,1000));
    }
    @Test void maintenanceIsBoundedAndUnloadHandlesNegativeCoordinates() {
        var cache = new EnvironmentCache<EnvironmentScore>();
        for (int i = -3; i <= 3; i++)
            cache.put(new EnvironmentCache.Key(i,0,0,0,0),result(),0,1,128,128*1024);
        cache.removeChunk(-1,0);
        assertEquals(4, cache.size());
        assertEquals(2, cache.expire(2_000_000,1,2));
        assertEquals(2, cache.size());
    }
}
