package it.regolarista.app;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** GPX track and continuous matching to its measured progress, with rejection of distant fixes. */
public final class Track {
    public static final class Point {
        public final double lat,lon,cumulative;
        Point(double lat,double lon,double cumulative){this.lat=lat;this.lon=lon;this.cumulative=cumulative;}
    }
    public static final class Match {
        public final double progress,offTrack;
        public final boolean valid;
        Match(double progress,double offTrack,boolean valid){this.progress=progress;this.offTrack=offTrack;this.valid=valid;}
    }
    private final List<Point> points = new ArrayList<>();
    public static double meters(double lat1,double lon1,double lat2,double lon2){
        double R=6371000,latRad=Math.toRadians((lat1+lat2)/2);
        double dx=Math.toRadians(lon2-lon1)*Math.cos(latRad)*R;
        double dy=Math.toRadians(lat2-lat1)*R;
        return Math.hypot(dx,dy);
    }
    public static Track read(InputStream input) throws Exception{
        Track track=new Track();XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(input,"UTF-8");
        int evt=p.getEventType();double cumulative=0;
        while(evt!=XmlPullParser.END_DOCUMENT){
            if(evt==XmlPullParser.START_TAG && ("trkpt".equals(p.getName()) || "rtept".equals(p.getName()))){
                String lat=p.getAttributeValue(null,"lat"),lon=p.getAttributeValue(null,"lon");
                if(lat!=null && lon!=null){
                    double a=Double.parseDouble(lat),b=Double.parseDouble(lon);
                    if(Math.abs(a)<=90 && Math.abs(b)<=180){
                        if(!track.points.isEmpty()){
                            Point prev=track.points.get(track.points.size()-1);
                            cumulative+=meters(prev.lat,prev.lon,a,b);
                        }
                        if(track.points.isEmpty() || cumulative>track.points.get(track.points.size()-1).cumulative+0.05)
                            track.points.add(new Point(a,b,cumulative));
                    }
                }
            }
            evt=p.next();
        }
        if(track.points.size()<2)throw new IllegalArgumentException("GPX privo di traccia valida");
        return track;
    }
    public double length(){return points.get(points.size()-1).cumulative;}
    public int size(){return points.size();}
    public Point start(){return points.get(0);}
    public Point second(){return points.get(1);}
    /** Closest projected segment, restricted to a plausible progress corridor to prevent jumping across hairpins. */
    public Match match(double lat,double lon,double lastProgress,double corridorMeters,double maxLateralMeters){
        double R=6371000,cy=Math.cos(Math.toRadians(lat));
        double best=Double.POSITIVE_INFINITY,progress=0;
        for(int i=1;i<points.size();i++){
            Point a=points.get(i-1),b=points.get(i);
            if(lastProgress>=0 && (b.cumulative<lastProgress-corridorMeters || a.cumulative>lastProgress+corridorMeters))continue;
            double ax=Math.toRadians(a.lon-lon)*cy*R,ay=Math.toRadians(a.lat-lat)*R;
            double bx=Math.toRadians(b.lon-lon)*cy*R,by=Math.toRadians(b.lat-lat)*R;
            double vx=bx-ax,vy=by-ay,den=vx*vx+vy*vy;
            if(den<0.0001)continue;
            double t=Math.max(0,Math.min(1,-(ax*vx+ay*vy)/den));
            double d=Math.hypot(ax+t*vx,ay+t*vy);
            if(d<best){best=d;progress=a.cumulative+t*(b.cumulative-a.cumulative);}
        }
        return new Match(progress,best,best<=maxLateralMeters);
    }
}
