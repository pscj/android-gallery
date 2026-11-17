package com.netflixbar.gallery.atmosphere;

import org.junit.Test;
import static org.junit.Assert.*;

public class ThreeSourceAtmosphereTest {
    @Test public void transitionCentersAndWeightsAreContinuousAndSumToOne() {
        assertEquals(0.5,AtmosphereMath.weights(1.0/3,3)[0],0.000001);
        assertEquals(0.5,AtmosphereMath.weights(2.0/3,3)[2],0.000001);
        double[] previous=AtmosphereMath.weights(0,3);
        for(int x=0;x<=1000;x++) {
            double[] weights=AtmosphereMath.weights(x/1000.0,3);
            assertEquals(1,weights[0]+weights[1]+weights[2],0.000001);
            for(int i=0;i<3;i++) { assertTrue(weights[i]>=0); assertTrue(Math.abs(weights[i]-previous[i])<0.007); }
            previous=weights;
        }
    }

    @Test public void threeColdWarmAndExtremeSourcesAreBalancedWithoutChangingInputs() {
        int[][] colors={{0xffa08060,0xff304060,0xff608060},{0xff000000,0xffffffff,0xff0000ff}};
        for(int[] set:colors) {
            AtmosphereMath.Sample[] samples=new AtmosphereMath.Sample[3];
            int[][] pixels=new int[3][96*54];
            for(int i=0;i<3;i++) {
                samples[i]=AtmosphereMath.sample(new int[]{set[i]});
                java.util.Arrays.fill(pixels[i],set[i]);
            }
            AtmosphereMath.Result result=AtmosphereMath.compose(pixels,96,54,samples);
            assertTrue(result.difference<=0.15);
            for(int i=0;i<3;i++) assertEquals(set[i],pixels[i][0]);
        }
    }

    @Test public void twoSourceWeightsRemainCompatible() {
        for(int i=0;i<=100;i++) assertEquals(AtmosphereMath.crossfadeWeight(i/100.0),
                AtmosphereMath.weights(i/100.0,2)[1],0.000001);
    }
}
