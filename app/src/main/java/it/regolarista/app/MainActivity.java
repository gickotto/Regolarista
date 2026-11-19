package it.regolarista.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_GPX=33,EXPORT_CSV=34,REQUEST_LOCATION=35;
    private LinearLayout root;
    private TextView errorView,stateView,speedView,recoveryView,odoView,partialView,gpsView,infoView,clockView;
    private DeviationBar bar;
    private Button beepButton,startButton,stopButton;
    private GpsService s;
    private double maxRecovery=90;
    private String sectionText="0=45";
    private String selectedGpx="";
    private final GpsService.Listener callback=this::refresh;
    @Override public void onCreate(Bundle b){super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        SharedPreferences pref=getPreferences(MODE_PRIVATE);sectionText=pref.getString("sections","0=45");maxRecovery=Double.longBitsToDouble(pref.getLong("maxspeed",Double.doubleToLongBits(90)));
        selectedGpx=pref.getString("gpx","");
        drawInterface();
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_LOCATION);
        else enableGps();
    }
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView label(String text,int size,int color){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);return t;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(16);b.setOnClickListener(v->action.run());return b;
    }
    private void addGap(LinearLayout l,int height){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private void drawInterface(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xFF0B121B);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(10),dp(12),dp(10));scroll.addView(root);setContentView(scroll);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=label("REGOLARISTA  ·  v0.2",12,0xFFB4C4D3);name.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        top.addView(name,new LinearLayout.LayoutParams(0,dp(30),1));
        gpsView=label("GNSS...",12,0xFF6BE3A1);top.addView(gpsView);
        root.addView(top);
        addGap(root,5);
        errorView=label("0,0 s",65,Color.WHITE);errorView.setTypeface(null,1);
        root.addView(errorView,new LinearLayout.LayoutParams(-1,dp(85)));
        stateView=label("IN MEDIA",17,Color.WHITE);stateView.setTypeface(null,1);root.addView(stateView);
        bar=new DeviationBar(this);root.addView(bar,new LinearLayout.LayoutParams(-1,dp(175)));
        LinearLayout data=new LinearLayout(this);data.setOrientation(LinearLayout.HORIZONTAL);
        speedView=label("ATTUALE\n-- km/h",17,Color.WHITE);speedView.setBackgroundColor(0xFF1C2B39);speedView.setPadding(dp(5),dp(7),dp(5),dp(7));
        recoveryView=label("RECUPERO IN 3 s\n-- km/h",17,Color.WHITE);recoveryView.setBackgroundColor(0xFF1C2B39);recoveryView.setPadding(dp(5),dp(7),dp(5),dp(7));
        LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,dp(80),1);half.setMargins(0,0,dp(4),0);data.addView(speedView,half);
        LinearLayout.LayoutParams other=new LinearLayout.LayoutParams(0,dp(80),1);other.setMargins(dp(4),0,0,0);data.addView(recoveryView,other);
        root.addView(data);addGap(root,8);
        LinearLayout odoRow=new LinearLayout(this);odoRow.setGravity(Gravity.CENTER_VERTICAL);
        odoView=label("0,00 km",27,Color.WHITE);odoView.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        odoRow.addView(odoView,new LinearLayout.LayoutParams(0,dp(57),1));
        odoRow.addView(button("−10 m",()->adjust(-10)));
        odoRow.addView(button("+10 m",()->adjust(10)));
        root.addView(odoRow);
        LinearLayout partialRow=new LinearLayout(this);partialRow.setGravity(Gravity.CENTER_VERTICAL);
        partialView=label("PARZIALE  0,00 km",21,0xFFBCE6FF);
        partialView.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        partialRow.addView(partialView,new LinearLayout.LayoutParams(0,dp(53),1));
        partialRow.addView(button("AZZERA PARZIALE",()->{if(service()){s.resetPartial();refresh();}}));
        root.addView(partialRow);
        beepButton=button("Beep: ON",()->{if(service()){s.beepEnabled=!s.beepEnabled;refresh();}});
        root.addView(beepButton,new LinearLayout.LayoutParams(-1,dp(51)));
        clockView=label("TEMPO 00:00  ·  MEDIA 45,0 km/h",13,0xFFB1C4D4);root.addView(clockView);
        infoView=label("Avvia GPS per iniziare",12,0xFFFFC977);root.addView(infoView);
        addGap(root,7);
        LinearLayout starts=new LinearLayout(this);
        startButton=button("▶ PARTENZA",()->{if(service()){s.armManual();refresh();}});
        stopButton=button("■ STOP",()->{if(service()){s.stopSession();refresh();}});
        starts.addView(startButton,new LinearLayout.LayoutParams(0,dp(54),2));
        starts.addView(stopButton,new LinearLayout.LayoutParams(0,dp(54),1));root.addView(starts);
        LinearLayout tools=new LinearLayout(this);
        tools.addView(button("TRACCIA",this::trackMenu),new LinearLayout.LayoutParams(0,dp(50),1));
        tools.addView(button("MEDIE",this::setAverages),new LinearLayout.LayoutParams(0,dp(50),1));
        tools.addView(button("AUTO START",this::showAutoStart),new LinearLayout.LayoutParams(0,dp(50),2));
        root.addView(tools);
        root.addView(button("ESPORTA SESSIONE CSV",this::exportCsv),new LinearLayout.LayoutParams(-1,dp(49)));
        addGap(root,5);
        root.addView(label("Uso solo per allenamento · Non seguire suggerimenti contrari alla sicurezza stradale",11,0xFF98A9B8));
        refresh();
    }
    private boolean service(){s=GpsService.instance;if(s==null){toast("Servizio GNSS non pronto: controlla i permessi");return false;}return true;}
    private void enableGps(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},99);
        try{startForegroundService(new Intent(this,GpsService.class));}
        catch(Exception e){toast("Avvio GNSS: "+e.getMessage());}
        root.postDelayed(()->{s=GpsService.instance;if(s!=null){restoreConfig();refresh();}},350);
    }
    private void restoreConfig(){
        if(s==null)return;
        try{s.engine.setSections(RegularityEngine.parseSections(sectionText));}catch(Exception e){toast("Medie non valide");}
        if(!selectedGpx.isEmpty() && s.track==null){
            try{Uri uri=Uri.parse(selectedGpx);try(InputStream in=getContentResolver().openInputStream(uri)){s.setTrack(Track.read(in));}}
            catch(Exception e){selectedGpx="";toast("GPX non più accessibile: importalo di nuovo");}
        }
    }
    private void refresh(){
        s=GpsService.instance;if(s==null || errorView==null)return;
        double err=s.running?s.lastError:0;
        errorView.setText(String.format(Locale.ITALY,"%+.1f s",err));
        int color=Math.abs(err)<0.05?Color.WHITE:err>0?0xFFFF7474:0xFF68E6A3;
        errorView.setTextColor(color);stateView.setText(!s.running?(s.armed?"IN ATTESA PARTENZA":"PRONTO"):err>=0.05?"RITARDO":err<=-0.05?"ANTICIPO":"IN MEDIA");stateView.setTextColor(color);
        bar.setError(err);
        speedView.setText(String.format(Locale.ITALY,"VELOCITÀ ATTUALE\n%.1f km/h",s.speedKmh));
        double target=s.engine.targetSpeedKmh(s.elapsedSeconds(),s.effectiveProgress(),3);
        recoveryView.setText(s.running?(target>maxRecovery?"RECUPERO IN 3 s\nNON FATTIBILE":String.format(Locale.ITALY,"RECUPERO IN 3 s\n%.1f km/h",target)):"RECUPERO IN 3 s\n-- km/h");
        odoView.setText(String.format(Locale.ITALY,"%.2f km",s.effectiveProgress()/1000));
        partialView.setText(String.format(Locale.ITALY,"PARZIALE  %.2f km",s.partialProgress()/1000));
        beepButton.setText("BEEP " +(s.beepEnabled?"ON":"OFF"));
        gpsView.setText(s.gpsInfo);
        clockView.setText(String.format(Locale.ITALY,"TEMPO %s · MEDIA %.1f km/h",RegularityEngine.formatTime(s.elapsedSeconds()),s.engine.speedAt(s.effectiveProgress())));
        infoView.setText((s.track!=null?"TRACCIA "+s.track.size()+" punti":"SENZA TRACCIA") + (s.running?" · REGISTRAZIONE":s.armed?" · IN ATTESA":"")+(!s.matched?" · FUORI TRACCIA":""));
        startButton.setEnabled(!s.running);stopButton.setEnabled(s.running||s.armed);
    }
    private void adjust(int m){if(service())s.adjust(m);}
    private void trackMenu(){
        if(s!=null && s.running){toast("Ferma prima la prova");return;}
        new AlertDialog.Builder(this).setTitle("Traccia del percorso")
            .setItems(new String[]{"Importa GPX", "Allenamento senza traccia"},(d,which)->{
                if(which==0)chooseGpx();else{
                    if(service()){s.setTrack(null);selectedGpx="";
                        getPreferences(MODE_PRIVATE).edit().remove("gpx").apply();refresh();
                        toast("Modalità senza traccia attiva");}
                }
            }).show();
    }
    private void chooseGpx(){if(s!=null && s.running){toast("Ferma prima la prova");return;}
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_GPX);
    }
    private void setAverages(){
        EditText input=new EditText(this);input.setText(sectionText);input.setTextSize(18);input.setMinLines(5);
        new AlertDialog.Builder(this).setTitle("Cambi media · metri=km/h").setMessage("Una riga per ogni media. Esempio:\n0=45\n4000=38\n6200=50").setView(input)
            .setPositiveButton("Salva",(dialog,w)->{
                try{String value=input.getText().toString();List<RegularityEngine.Section> sections=RegularityEngine.parseSections(value);
                    if(service())s.engine.setSections(sections);sectionText=value;
                    getPreferences(MODE_PRIVATE).edit().putString("sections",sectionText).apply();refresh();}
                catch(Exception e){toast("Errore medie: "+e.getMessage());}
            }).setNegativeButton("Annulla",null).show();
    }
    private void showAutoStart(){if(!service())return;
        String[] opts={"Partenza a orario (HH:mm:ss)","Partenza geografica su inizio GPX","Velocità massima suggeribile"};
        new AlertDialog.Builder(this).setTitle("Avvio automatico e limiti").setItems(opts,(dialog,which)->{
            if(which==0)askClock();else if(which==1){
                try{s.armGeo();toast("Armato: partenza entro 12 m dall'inizio GPX, in movimento");refresh();}
                catch(Exception e){toast(e.getMessage());}
            }else askMax();
        }).show();
    }
    private void askClock(){EditText input=new EditText(this);input.setHint("HH:mm:ss");
        input.setText(new SimpleDateFormat("HH:mm:ss",Locale.ITALY).format(new Date(System.currentTimeMillis()+60000)));
        new AlertDialog.Builder(this).setTitle("Partenza automatica all'ora locale").setView(input)
            .setPositiveButton("Arma",(d,w)->{
                try{String[] parts=input.getText().toString().trim().split(":");
                    if(parts.length!=3)throw new IllegalArgumentException("Scrivi HH:mm:ss");
                    int h=Integer.parseInt(parts[0]),m=Integer.parseInt(parts[1]),sec=Integer.parseInt(parts[2]);
                    if(h>23||m>59||sec>59||h<0||m<0||sec<0)throw new IllegalArgumentException("Ora non valida");
                    Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,m);c.set(Calendar.SECOND,sec);c.set(Calendar.MILLISECOND,0);
                    if(c.getTimeInMillis()<=System.currentTimeMillis())c.add(Calendar.DAY_OF_MONTH,1);
                    s.armClock(c.getTimeInMillis());refresh();toast("Partenza armata: "+input.getText());
                }catch(Exception e){toast(e.getMessage());}
            }).setNegativeButton("Annulla",null).show();
    }
    private void askMax(){EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setText(""+(int)maxRecovery);
        new AlertDialog.Builder(this).setTitle("Limite suggerimento km/h").setView(input).setPositiveButton("Salva",(d,w)->{
            try{double val=Double.parseDouble(input.getText().toString());if(val<1||val>200)throw new Exception();maxRecovery=val;
                getPreferences(MODE_PRIVATE).edit().putLong("maxspeed",Double.doubleToLongBits(val)).apply();}
            catch(Exception e){toast("Valore non valido");}
        }).setNegativeButton("Annulla",null).show();
    }
    private void exportCsv(){if(!service()||s.sessionFile.isEmpty()){toast("Nessuna sessione da esportare");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE,new File(s.sessionFile).getName());startActivityForResult(i,EXPORT_CSV);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==PICK_GPX){
            try{if(s!=null&&s.running){toast("Ferma la prova");return;}
                Uri uri=data.getData();getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try(InputStream in=getContentResolver().openInputStream(uri)){
                    Track track=Track.read(in);if(service())s.setTrack(track);
                    selectedGpx=uri.toString();getPreferences(MODE_PRIVATE).edit().putString("gpx",selectedGpx).apply();
                    toast(String.format(Locale.ITALY,"GPX: %d punti · %.2f km",track.size(),track.length()/1000));
                }refresh();
            }catch(Exception e){toast("Import GPX: "+e.getMessage());}
        }else if(request==EXPORT_CSV){
            try(InputStream in=new FileInputStream(s.sessionFile);OutputStream out=getContentResolver().openOutputStream(data.getData())){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);toast("CSV esportato");
            }catch(Exception e){toast("Export: "+e.getMessage());}
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){super.onRequestPermissionsResult(request,perms,grants);
        if(request==REQUEST_LOCATION){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)enableGps();else toast("Il permesso di posizione è necessario per GNSS");}
    }
    @Override protected void onResume(){super.onResume();GpsService.listener=callback;refresh();}
    @Override protected void onPause(){GpsService.listener=null;super.onPause();}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
}
