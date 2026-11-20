package dev.dynamicchunkie.sections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HotSoftGridTest {
    @Test void allTwentySixTouchingNeighboursAreSoftIncludingDiagonals() {
        var grid=new HotSoftGrid();grid.add(0,0,0);int soft=0;
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++) {
            if(x==0 && y==0 && z==0)assertEquals(HotSoftGrid.State.HOT,grid.state(x,y,z));
            else {assertEquals(HotSoftGrid.State.SOFT,grid.state(x,y,z));soft++;}
        }
        assertEquals(26,soft);assertEquals(26,grid.softCount());assertEquals(HotSoftGrid.State.COLD,grid.state(2,0,0));
    }
    @Test void overlappingHalosDeduplicateAndHotWins() {
        var grid=new HotSoftGrid();grid.add(0,0,0);grid.add(1,0,0);
        assertEquals(2,grid.hotCount());assertEquals(34,grid.softCount());
        assertEquals(HotSoftGrid.State.HOT,grid.state(1,0,0));
        grid.clear();assertEquals(HotSoftGrid.State.COLD,grid.state(0,0,0));
    }
    @Test void removingOneHotCellPreservesTheSharedHalo() {
        var grid=new HotSoftGrid();
        var next=new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        next.add(HotSoftGrid.key(0,0,0));
        next.add(HotSoftGrid.key(2,0,0));
        assertTrue(grid.update(next));
        assertFalse(grid.update(next));
        next.remove(HotSoftGrid.key(0,0,0));
        assertTrue(grid.update(next));
        assertEquals(HotSoftGrid.State.COLD,grid.state(-1,0,0));
        assertEquals(HotSoftGrid.State.SOFT,grid.state(1,0,0));
        assertEquals(26,grid.softCount());
    }
    @Test void negativeAndWorldBorderCoordinatesRoundTrip() {
        for(int x:new int[]{-1875000,-1,0,1875000})for(int y:new int[]{-64,-1,0,320})for(int z:new int[]{-1875000,-1,1875000}) {
            long key=HotSoftGrid.key(x,y,z);assertEquals(x,HotSoftGrid.x(key));assertEquals(y,HotSoftGrid.y(key));assertEquals(z,HotSoftGrid.z(key));
        }
    }
    @Test void chargeRetainsReservationAfterClearingMembership() {
        var grid=new HotSoftGrid();
        for(int x=0;x<100;x++)grid.add(x,0,0);
        long reserved=grid.chargedBytes();grid.clear();
        assertEquals(0,grid.hotCount());assertEquals(reserved,grid.chargedBytes());
    }
    @Test void packingLimitsNeverWrapHaloAcrossWorld() {
        var grid=new HotSoftGrid();grid.add(2097151,0,0);
        assertEquals(HotSoftGrid.State.COLD,grid.state(-2097152,0,0));
        assertThrows(IllegalArgumentException.class,()->HotSoftGrid.key(2097152,0,0));
    }
}
