package dev.dynamicchunkie;
/** Local shape heuristics. Sky/height never override an enclosed directional sample. */
final class EnvironmentClassifier {
    private EnvironmentClassifier() {}
    static String classify(RaycastAnalyzer.Sample r,boolean sky,double altitude,double open,boolean exitAhead) {
        double longAxis=Math.max(Math.min(r.front(),r.back()),Math.min(r.left(),r.right()));
        double shortAxis=Math.min(Math.max(r.front(),r.back()),Math.max(r.left(),r.right()));
        return exitAhead ? "Cave Exit Ahead" : r.down()>.8 && r.horizontal()>.35 ? "Chasm / Open Drop" :
                !sky && longAxis>.45 && shortAxis<.25 ? "Narrow Passage" :
                open<.13 ? "Narrow Tunnel" : !sky && r.horizontal()<.35 && r.up()<.4 ? "Enclosed Room" :
                !sky ? "Large Cave / Covered Area" : r.horizontal()>.7 && r.up()>.8 && altitude>120 ? "Open Summit" :
                open<.78 ? "Surface" : "High Open Sky";
    }
}
