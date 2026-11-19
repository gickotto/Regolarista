import it.regolarista.app.ProgressPredictor;
public class ProgressPredictorTest {
    static void near(double a,double b,double tol,String message){if(Math.abs(a-b)>tol)throw new AssertionError(message+": "+a+" vs "+b);}
    public static void main(String[] args){
        ProgressPredictor p=new ProgressPredictor();
        p.observe(100,10,1000);
        near(p.tick(1100),101,0.01,"interpolation at 100ms");
        near(p.tick(1500),105,0.01,"interpolation at 500ms");
        double prior=p.meters();
        p.observe(106,10,1600);
        near(p.meters(),prior+1,0.01,"no step on GNSS measurement");
        double next=p.tick(1700);
        if(!(next>prior+1 && next<109))throw new AssertionError("GNSS correction must be gradual");
        p.tick(5000);
        double stale=p.meters();p.tick(6000);
        near(p.meters(),stale,0.00001,"stale GNSS must not drift forever");
        p.reset();near(p.tick(8000),0,0.00001,"reset");
        System.out.println("All ProgressPredictor tests passed");
    }
}
