package uk.ac.rdg.resc.ncwms.graphics;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
public class BilinearInterpolator {
    private List<Double> xVals;
    private List<Double> yVals;
    private Float[][] data;
   
    private double[] xRanges;
    private double[] yRanges;
    public BilinearInterpolator(Collection<Double> xVals, Collection<Double> yVals, Float[][] data) {
        this.xVals = new ArrayList<Double>(xVals);
        this.yVals = new ArrayList<Double>(yVals);
        this.data = data;
        if(data.length != xVals.size() || data[0].length != yVals.size()) {
            throw new IllegalArgumentException("Data size must match axis sizes");
        }
       
        xRanges = new double[xVals.size()];
        yRanges = new double[yVals.size()];
       
        for(int i=1; i < xVals.size(); i++) {
            xRanges[i] = this.xVals.get(i) - this.xVals.get(i-1);
        }
        for(int i=1; i < this.yVals.size(); i++) {
            yRanges[i] = this.yVals.get(i) - this.yVals.get(i-1);
        }
    }
   
    public Float getValue(double x, double y) {
        /*
         * Find the indices above the x and y values 
         */
        int xIndex = 0;
        int yIndex = 0;
        while(xVals.get(xIndex) <= x && xIndex < xVals.size() - 1) {
            xIndex++;
        }
        while(yVals.get(yIndex) <= y && yIndex < yVals.size() - 1) {
            yIndex++;
        }
        double xFAlong;
        double yFAlong;
        if(xIndex == 0) {
            xIndex++;
            xFAlong = 1.0;
        } else {
            xFAlong = (x - xVals.get(xIndex - 1)) / xRanges[xIndex];
        }
        if(yIndex == 0) {
            yIndex++;
            yFAlong = 1.0;
        } else {
            yFAlong = (y - yVals.get(yIndex - 1)) / yRanges[yIndex];
        }
       
        try{
            Float f = (float) (xFAlong * yFAlong * data[xIndex][yIndex] +
                    xFAlong * (1.0-yFAlong) * data[xIndex][yIndex-1] +
                    (1.0-xFAlong) * yFAlong * data[xIndex-1][yIndex] +
                    (1.0-xFAlong) * (1.0-yFAlong) * data[xIndex-1][yIndex-1]);
           
            return f;
        } catch (NullPointerException npe) {
            return null;
        }
    }
}
