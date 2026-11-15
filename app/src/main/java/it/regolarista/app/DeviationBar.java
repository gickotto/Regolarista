package it.regolarista.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.Locale;

/** The dominant dial: +/-1s uses 70% of the width; remaining 30% is for +/-1..3s. */
public final class DeviationBar extends View {
    private double error=0;
    private final Paint p=new Paint(3);
    public DeviationBar(Context context){super(context);setMinimumHeight(dp(180));}
    public void setError(double v){error=v;invalidate();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private float x(double e,float width){
        double a=Math.min(3,Math.abs(e));double fraction=a<=1?a*0.35:0.35+(a-1)*0.075;
        return (float)(width*(.5+Math.signum(e)*fraction));
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float w=getWidth(),h=getHeight();
        float left=dp(10),right=w-dp(10),middle=w/2;
        float y1=h*.34f,y2=h*.7f;
        p.setColor(Color.rgb(32,134,85));c.drawRoundRect(new RectF(left,y1,middle,y2),dp(10),dp(10),p);
        p.setColor(Color.rgb(179,51,61));c.drawRoundRect(new RectF(middle,y1,right,y2),dp(10),dp(10),p);
        for(int i=-30;i<=30;i++){
            double val=i/10.0;
            if(Math.abs(val)>1 && i%5!=0)continue;
            float xx=x(val,w);
            p.setStrokeWidth(val==0?dp(3):dp(1));p.setColor(val==0?Color.WHITE:0xAACDE1D9);
            c.drawLine(xx,y1,xx,val==0?y2:Math.abs(val)<=1?y1+dp(23):y1+dp(15),p);
        }
        p.setTextAlign(Paint.Align.CENTER);p.setColor(Color.WHITE);p.setTextSize(dp(13));
        for(int v:new int[]{-3,-1,0,1,3})c.drawText(v>0?"+"+v:""+v,x(v,w),y1-dp(11),p);
        float marker=x(error,w);
        p.setColor(Color.WHITE);p.setStrokeWidth(dp(3));c.drawLine(marker,y2+dp(4),marker,y2+dp(18),p);
        android.graphics.Path tri=new android.graphics.Path();tri.moveTo(marker,y2);tri.lineTo(marker-dp(9),y2+dp(13));tri.lineTo(marker+dp(9),y2+dp(13));tri.close();c.drawPath(tri,p);
        p.setTextAlign(Paint.Align.LEFT);p.setTextSize(dp(13));p.setColor(0xFF60EBA2);c.drawText("ANTICIPO",left,h-dp(7),p);
        p.setTextAlign(Paint.Align.RIGHT);p.setColor(0xFFFF7B7B);c.drawText("RITARDO",right,h-dp(7),p);
    }
}
