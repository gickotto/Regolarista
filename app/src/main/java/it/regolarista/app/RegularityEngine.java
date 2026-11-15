package it.regolarista.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Pure Java: progress is meters, speed is km/h, elapsed seconds are monotonic. */
public final class RegularityEngine {
    public static final class Section {
        public final double fromMeters, speedKmh;
        public Section(double fromMeters,double speedKmh) {
            if(fromMeters<0 || speedKmh<=0 || !Double.isFinite(fromMeters) || !Double.isFinite(speedKmh)) throw new IllegalArgumentException("Invalid section");
            this.fromMeters=fromMeters;this.speedKmh=speedKmh;
        }
    }
    private final List<Section> sections = new ArrayList<>();
    public RegularityEngine(){ sections.add(new Section(0,45)); }
    public void setSections(List<Section> newSections){
        if(newSections==null || newSections.isEmpty() || newSections.get(0).fromMeters!=0) throw new IllegalArgumentException("First section must begin at 0 m");
        double last=-1;
        for(Section s:newSections) {if(s.fromMeters<=last) throw new IllegalArgumentException("Sections must be in increasing order");last=s.fromMeters;}
        sections.clear();sections.addAll(newSections);
    }
    public List<Section> sections(){return Collections.unmodifiableList(sections);}
    public double theoreticalSeconds(double distanceM){
        double s=Math.max(0,distanceM), t=0;
        for(int i=0;i<sections.size();i++){
            Section v=sections.get(i);
            if(s<=v.fromMeters) break;
            double next=i+1<sections.size()?sections.get(i+1).fromMeters:Double.POSITIVE_INFINITY;
            t+=(Math.min(s,next)-v.fromMeters)*3.6/v.speedKmh;
            if(s<=next) break;
        }
        return t;
    }
    public double distanceAtSeconds(double timeSeconds){
        double remaining=Math.max(0,timeSeconds);
        for(int i=0;i<sections.size();i++){
            Section s=sections.get(i);
            double next=i+1<sections.size()?sections.get(i+1).fromMeters:Double.POSITIVE_INFINITY;
            double segmentTime=(next-s.fromMeters)*3.6/s.speedKmh;
            if(remaining<=segmentTime) return s.fromMeters+remaining*s.speedKmh/3.6;
            remaining-=segmentTime;
        }
        return 0;
    }
    public double speedAt(double distanceM){
        double speed=sections.get(0).speedKmh;
        for(Section s:sections){if(distanceM>=s.fromMeters) speed=s.speedKmh;else break;}
        return speed;
    }
    /** Positive error = behind schedule, negative = ahead. */
    public double errorSeconds(double elapsedSeconds,double progressMeters){return elapsedSeconds-theoreticalSeconds(progressMeters);}
    /** Target average speed across next horizon, computed through medium changes. */
    public double targetSpeedKmh(double elapsedSeconds,double progressMeters,double horizonSeconds){
        if(horizonSeconds<=0) throw new IllegalArgumentException("Horizon must be positive");
        double target=distanceAtSeconds(elapsedSeconds+horizonSeconds);
        return Math.max(0,(target-progressMeters)*3.6/horizonSeconds);
    }
    /** One beep/sec on time; increasingly fast ahead (max 2 Hz), slower behind (min 1/3 Hz). */
    public double beepHz(double errorSeconds){
        return errorSeconds<0?1+Math.min(1,-errorSeconds):1.0/(1+Math.min(2,errorSeconds));
    }
    public static List<Section> parseSections(String text){
        List<Section> a=new ArrayList<>();
        for(String raw:text.split("[\\r\\n;]+")){
            String l=raw.trim();if(l.isEmpty())continue;
            String[] parts=l.split("[=:,]",2);
            if(parts.length!=2)throw new IllegalArgumentException("Format: metri=km/h (es. 4000=38)");
            double meter=Double.parseDouble(parts[0].trim());
            double speed=Double.parseDouble(parts[1].trim());
            a.add(new Section(meter,speed));
        }
        RegularityEngine test=new RegularityEngine();test.setSections(a);
        return a;
    }
    public static String formatTime(double sec){
        int whole=(int)Math.max(0,Math.floor(sec));return String.format(Locale.ITALY,"%02d:%02d",whole/60,whole%60);
    }
}
