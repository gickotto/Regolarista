import it.regolarista.app.RegularityEngine;
import java.util.List;

public class EngineTest {
    private static void eq(double a,double b,double tol,String what){if(Math.abs(a-b)>tol)throw new AssertionError(what+": "+a+" vs "+b);}
    public static void main(String[] args){
        RegularityEngine e=new RegularityEngine();
        eq(e.theoreticalSeconds(3000),240,1e-9,"3km at 45km/h");
        eq(e.errorSeconds(242.4,3000),2.4,1e-9,"delay sign");
        eq(e.errorSeconds(237.6,3000),-2.4,1e-9,"advance sign");
        eq(e.distanceAtSeconds(240),3000,1e-9,"inverse time");
        eq(e.targetSpeedKmh(242.4,3000,3),81,1e-6,"recover in 3s");
        eq(e.beepHz(0),1,1e-9,"on pace beep");
        eq(e.beepHz(-2),2,1e-9,"ahead beep saturation");
        eq(e.beepHz(2),1.0/3,1e-9,"behind beep saturation");
        e.setSections(List.of(new RegularityEngine.Section(0,45),new RegularityEngine.Section(4000,36)));
        eq(e.theoreticalSeconds(5000),420,1e-9,"section changes");
        eq(e.distanceAtSeconds(420),5000,1e-9,"inverse section changes");
        eq(e.targetSpeedKmh(319,3990,3),36,1e-6,"future speed across change");
        System.out.println("All RegularityEngine tests passed");
    }
}
