package dev.dynamicchunkie.terrain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SectionShadowTest {
    @Test void fullSectionBetweenCameraAndTargetCoversTarget() {
        assertTrue(SectionShadow.covers(0,104,8,16,96,0,64,96,0));
    }
    @Test void partialCoverageAndCameraInsideOccluderNeverCull() {
        assertFalse(SectionShadow.covers(0,104,8,16,96,0,64,144,0));
        assertFalse(SectionShadow.covers(20,104,8,16,96,0,64,96,0));
        assertFalse(SectionShadow.covers(0,104,8,80,96,0,64,96,0));
    }
    @Test void translationAndNegativeCoordinatesAreEquivalent() {
        assertTrue(SectionShadow.covers(-160,-56,-152,-144,-64,-160,-96,-64,-160));
    }
    @Test void everyAcceptedBoxAlsoCoversInteriorSamples() {
        var random=new java.util.Random(8137);
        int accepted=0;
        for(int n=0;n<3000;n++) {
            double cy=96+random.nextDouble()*16,cz=random.nextDouble()*16;
            int tx=48+16*random.nextInt(6),ty=80+16*random.nextInt(4),tz=-16+16*random.nextInt(4);
            if(!SectionShadow.covers(0,cy,cz,16,96,0,tx,ty,tz))continue;
            accepted++;
            for(int a=0;a<=4;a++)for(int b=0;b<=4;b++)for(int c=0;c<=4;c++)
                assertTrue(SectionShadow.intersectsBefore(0,cy,cz,tx-2+a*5,ty-2+b*5,tz-2+c*5,
                        16.05,96.05,.05,31.95,111.95,15.95));
        }
        assertTrue(accepted>100);
    }
}
