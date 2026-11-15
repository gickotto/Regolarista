package it.regolarista.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Foreground GNSS recording service. Keeps updating progress and errors if the screen is switched off. */
public class GpsService extends Service implements LocationListener {
    public interface Listener {void changed();}
    public static volatile GpsService instance;
    public static volatile Listener listener;
    public final RegularityEngine engine=new RegularityEngine();
    public Track track;
    public boolean running=false,armed=false,beepEnabled=true,autoGeographic=false;
    public long autoStartEpochMs=0,startElapsedMs=0;
    public double progressMeters=0, correctionMeters=0, speedKmh=0, accuracy=Double.NaN, lastError=0;
    public boolean validFix=false,matched=true;
    public String gpsInfo="In attesa GNSS",sessionFile="";
    private LocationManager lm;
    private Location prev;
    private long lastFixElapsed=0,lastToneElapsed=0;
    private PrintWriter writer;
    private ToneGenerator tone;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){public void run(){
        if(armed && !autoGeographic && System.currentTimeMillis()>=autoStartEpochMs) startNow();
        if(running){
            lastError=engine.errorSeconds(elapsedSeconds(),effectiveProgress());
            if(beepEnabled && lastFixElapsed>0 && SystemClock.elapsedRealtime()-lastFixElapsed<5000){
                long now=SystemClock.elapsedRealtime();double period=1000.0/engine.beepHz(lastError);
                if(now-lastToneElapsed>=period){
                    if(tone!=null)tone.startTone(ToneGenerator.TONE_PROP_BEEP,65);
                    lastToneElapsed=now;
                }
            }
        }
        if(listener!=null)listener.changed();handler.postDelayed(this,100);
    }};
    @Override public void onCreate(){super.onCreate();instance=this;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("gps","Regolarista GNSS",NotificationManager.IMPORTANCE_LOW));
        Intent launch=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,launch,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"gps").setContentTitle("Regolarista")
            .setContentText("GNSS e registrazione attivi").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(12,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(12,n);
        try{tone=new ToneGenerator(AudioManager.STREAM_MUSIC,60);}catch(Exception ignored){}
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        try{if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,200,0,this,Looper.getMainLooper());
        }else gpsInfo="Permesso GPS assente";}catch(Exception e){gpsInfo=e.getMessage();}
        handler.post(ticker);
    }
    public void setTrack(Track t){track=t;prev=null;progressMeters=0;correctionMeters=0;matched=true;}
    public void armManual(){armed=false;startNow();}
    public void armClock(long when){autoStartEpochMs=when;armed=true;autoGeographic=false;running=false;}
    public void armGeo(){if(track==null)throw new IllegalStateException("Importare prima un GPX");armed=true;autoGeographic=true;running=false;}
    public void startNow(){
        if(running)stopSession();
        correctionMeters=0;progressMeters=0;prev=null;lastError=0;
        startElapsedMs=SystemClock.elapsedRealtime();lastToneElapsed=startElapsedMs;
        running=true;armed=false;validFix=false;
        File dir=getExternalFilesDir("sessions");if(dir==null)dir=getFilesDir();if(!dir.exists())dir.mkdirs();
        sessionFile=new File(dir,"regolarista_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".csv").getAbsolutePath();
        try{writer=new PrintWriter(new FileWriter(sessionFile));writer.println("timestamp_iso,lat,lon,accuracy_m,gps_speed_kmh,progress_m,correction_m,error_s,matched");writer.flush();}
        catch(Exception e){gpsInfo="Errore salvataggio: "+e.getMessage();}
    }
    public void stopSession(){running=false;armed=false;if(writer!=null){writer.flush();writer.close();writer=null;}}
    public double elapsedSeconds(){return running?(SystemClock.elapsedRealtime()-startElapsedMs)/1000.0:0;}
    public double effectiveProgress(){return Math.max(0,progressMeters+correctionMeters);}
    public void adjust(int meters){correctionMeters+=meters;if(listener!=null)listener.changed();}
    @Override public void onLocationChanged(Location loc){
        long now=SystemClock.elapsedRealtime();lastFixElapsed=now;
        accuracy=loc.hasAccuracy()?loc.getAccuracy():Double.NaN;
        validFix=(!loc.hasAccuracy()||accuracy<=35) && (loc.getElapsedRealtimeNanos()==0||now*1000000L-loc.getElapsedRealtimeNanos()<5000000000L);
        gpsInfo=validFix?String.format(Locale.ITALY,"GNSS ±%.0f m",accuracy):"GNSS impreciso";
        speedKmh=loc.hasSpeed()?loc.getSpeed()*3.6:0;
        if(armed){
            if(autoGeographic && track!=null && validFix && speedKmh>=3 && prev!=null){
                Track.Point a=track.start(),b=track.second();
                double dx=(b.lon-a.lon)*Math.cos(Math.toRadians(a.lat)),dy=b.lat-a.lat;
                double before=(prev.getLongitude()-a.lon)*Math.cos(Math.toRadians(a.lat))*dx+(prev.getLatitude()-a.lat)*dy;
                double after=(loc.getLongitude()-a.lon)*Math.cos(Math.toRadians(a.lat))*dx+(loc.getLatitude()-a.lat)*dy;
                double distance=Track.meters(a.lat,a.lon,loc.getLatitude(),loc.getLongitude());
                if(before<0 && after>=0 && distance<18)startNow();
            }
        }
        if(!running){prev=loc;return;}
        if(!validFix)return;
        if(track==null){
            if(prev!=null){
                double dt=(loc.getElapsedRealtimeNanos()-prev.getElapsedRealtimeNanos())/1e9;
                double d=prev.distanceTo(loc);
                // Drop teleportation and accuracy noise. Stationary raw GNSS is not a reliable odometer.
                if(dt>0.05 && dt<10 && d<Math.max(8,dt*45) && (d>=Math.max(5,loc.getAccuracy()*1.5) || speedKmh>3)){
                    progressMeters+=d;
                }
            }
            matched=true;
        }else{
            // Search corridor based on expected travel between valid fixes, not entire track.
            double corridor=Math.max(35,Math.min(180,3*speedKmh+35));
            Track.Match m=track.match(loc.getLatitude(),loc.getLongitude(),prev==null?-1:progressMeters,corridor,25);
            matched=m.valid;
            if(m.valid){
                if(prev==null || m.progress>=progressMeters-15){progressMeters=Math.max(progressMeters,m.progress);}
            }else gpsInfo="Fuori traccia · progressiva congelata";
        }
        prev=loc;
        lastError=engine.errorSeconds(elapsedSeconds(),effectiveProgress());
        if(writer!=null){
            writer.printf(Locale.US,"%s,%.8f,%.8f,%.1f,%.2f,%.2f,%.2f,%.3f,%s%n",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date()),
                loc.getLatitude(),loc.getLongitude(),accuracy,speedKmh,progressMeters,correctionMeters,lastError,matched);
            writer.flush();
        }
    }
    @Override public void onProviderDisabled(String provider){gpsInfo="GPS disattivato";validFix=false;}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){stopSession();handler.removeCallbacksAndMessages(null);
        if(lm!=null)lm.removeUpdates(this);if(tone!=null)tone.release();instance=null;super.onDestroy();}
}
