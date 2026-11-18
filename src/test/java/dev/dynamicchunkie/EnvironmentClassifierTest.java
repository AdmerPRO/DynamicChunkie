package dev.dynamicchunkie;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EnvironmentClassifierTest {
    private RaycastAnalyzer.Sample sample(double horizontal,double front,double back,double left,double right,double up,double down) {
        return new RaycastAnalyzer.Sample(8,horizontal,(up+down)/2,0,front,front,back,left,right,front,up,down);
    }
    @Test void roomStaysEnclosedAtHighAltitudeAndPassageUsesDirectionalShape() {
        var room=sample(.2,.2,.2,.2,.2,.1,.1);
        assertEquals("Enclosed Room",EnvironmentClassifier.classify(room,false,240,.2,false));
        assertEquals("Narrow Tunnel",EnvironmentClassifier.classify(room,false,20,.05,false));
        var passage=sample(.35,.8,.8,.1,.1,.1,.1);
        assertEquals("Narrow Passage",EnvironmentClassifier.classify(passage,false,20,.35,false));
    }
    @Test void dropsAndSummitsRequireOpenDirectionalEvidence() {
        assertEquals("Chasm / Open Drop",EnvironmentClassifier.classify(sample(.5,.5,.5,.5,.5,.1,1),false,20,.5,false));
        assertEquals("Open Summit",EnvironmentClassifier.classify(sample(.9,.9,.9,.9,.9,1,.1),true,180,.9,false));
        assertEquals("Cave Exit Ahead",EnvironmentClassifier.classify(sample(.2,.2,.2,.2,.2,.1,.1),false,20,.2,true));
    }
}
