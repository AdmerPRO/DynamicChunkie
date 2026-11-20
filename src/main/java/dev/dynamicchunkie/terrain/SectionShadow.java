package dev.dynamicchunkie.terrain;

/** Conservative convex shadow test. A single occluder must cover every inflated target corner. */
public final class SectionShadow {
    private SectionShadow() {}
    public static boolean covers(double cx,double cy,double cz,int ox,int oy,int oz,int tx,int ty,int tz) {
        return covers(cx,cy,cz,ox,oy,oz,tx,ty,tz,16);
    }
    static boolean covers(double cx,double cy,double cz,int ox,int oy,int oz,int tx,int ty,int tz,int size) {
        if(Math.abs(cx-(tx+8))<24 && Math.abs(cy-(ty+8))<24 && Math.abs(cz-(tz+8))<24)return false;
        double minX=ox+.05,minY=oy+.05,minZ=oz+.05,maxX=ox+15.95,maxY=oy+15.95,maxZ=oz+15.95;
        if(cx>=minX && cx<=maxX && cy>=minY && cy<=maxY && cz>=minZ && cz<=maxZ)return false;
        // The ray to the centre must intersect as well. It can only reject a candidate.
        double centre=size*.5;
        if (!intersectsBefore(cx,cy,cz,tx+centre,ty+centre,tz+centre,minX,minY,minZ,maxX,maxY,maxZ)) return false;
        for(int i=0;i<8;i++) {
            // Inflation also protects geometry protruding slightly beyond a section boundary.
            double x=tx+((i&1)==0?-2:size+2), y=ty+((i&2)==0?-2:size+2), z=tz+((i&4)==0?-2:size+2);
            if(!intersectsBefore(cx,cy,cz,x,y,z,minX,minY,minZ,maxX,maxY,maxZ))return false;
        }
        return true;
    }
    static boolean intersectsBefore(double cx,double cy,double cz,double x,double y,double z,
            double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        double enter=0,exit=1;
        for(int axis=0;axis<3;axis++) {
            double start=axis==0?cx:axis==1?cy:cz;
            double delta=(axis==0?x:axis==1?y:z)-start;
            double lo=axis==0?minX:axis==1?minY:minZ,hi=axis==0?maxX:axis==1?maxY:maxZ;
            if(Math.abs(delta)<1e-12) {if(start<=lo || start>=hi)return false;}
            else {
                double a=(lo-start)/delta,b=(hi-start)/delta;
                enter=Math.max(enter,Math.min(a,b));exit=Math.min(exit,Math.max(a,b));
                if(exit-enter<=1e-9)return false;
            }
        }
        return enter>1e-9 && exit<1-1e-9;
    }
}
