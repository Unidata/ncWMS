/*
 * Copyright (c) 2010 The University of Reading
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package uk.ac.rdg.resc.ncwms.controller;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Ellipse2D;
import java.awt.image.IndexColorModel;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.Marker;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.time.FixedMillisecond;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.AbstractXYZDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.XYZDataset;
import org.jfree.ui.HorizontalAlignment;
import org.jfree.ui.RectangleAnchor;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.geometry.HorizontalPosition;
import uk.ac.rdg.resc.edal.geometry.LonLatPosition;
import uk.ac.rdg.resc.edal.geometry.impl.LineString;
import uk.ac.rdg.resc.edal.util.Range;
import uk.ac.rdg.resc.edal.util.Utils;
import uk.ac.rdg.resc.ncwms.graphics.ColorPalette;
import uk.ac.rdg.resc.ncwms.util.WmsUtils;
import uk.ac.rdg.resc.ncwms.wms.Layer;


/**
 * Code to produce various types of chart.  Used by the {@link AbstractWmsController}.
 * @author Jon Blower
 * @author Kevin X. Yang
 */
//final class Charting
public class Charting
{
    private static final Locale US_LOCALE = new Locale("us", "US");
    private static final Color TRANSPARENT = new Color(0,0,0,0);
    
    public static JFreeChart createTimeseriesPlot(Layer layer, LonLatPosition lonLat,
            Map<DateTime, Float> tsData)
    {
        TimeSeries ts = new TimeSeries("Data");
        for (Entry<DateTime, Float> entry : tsData.entrySet()) {
//            ts.add(new Millisecond(entry.getKey().toDate()), entry.getValue());
            ts.add(new FixedMillisecond(entry.getKey().toDate()), entry.getValue());
        }
        TimeSeriesCollection xydataset = new TimeSeriesCollection();
        xydataset.addSeries(ts);

        // Create a chart with no legend, tooltips or URLs
        String title = "Lon: " + lonLat.getLongitude() + ", Lat: " +
                lonLat.getLatitude();
        String yLabel = layer.getTitle() + " (" + layer.getUnits() + ")";
        JFreeChart chart = ChartFactory.createTimeSeriesChart(title,
                "Date / time", yLabel, xydataset, false, false, false);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesShape(0, new Ellipse2D.Double(-1.0, -1.0, 2.0, 2.0));
        renderer.setSeriesShapesVisible(0, true);
        chart.getXYPlot().setRenderer(renderer);
        chart.getXYPlot().setNoDataMessage("There is no data for your choice");
        chart.getXYPlot().setNoDataMessageFont(new Font("sansserif", Font.BOLD, 32));
        
        return chart;
    }
    
    public static JFreeChart createTimeseriesPlot(String lon, String lat,
            Map<DateTime, Float> tsData, String variableName, String variableUnit)
    {
        TimeSeries ts = new TimeSeries("Data");
        for (Entry<DateTime, Float> entry : tsData.entrySet()) {
            ts.add(new Millisecond(entry.getKey().toDate()), entry.getValue());
        }
        TimeSeriesCollection xydataset = new TimeSeriesCollection();
        xydataset.addSeries(ts);

        // Create a chart with no legend, tooltips or URLs
        String title = "Lon: " + lon + ", Lat: " + lat;
        
        //String yLabel = layer.getTitle() + " (" + layer.getUnits() + ")";        
        String yLabel = variableName +"(" +variableUnit+")";
                       
        JFreeChart chart = ChartFactory.createTimeSeriesChart(title,
                "Date / time", yLabel, xydataset, false, false, false);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesShape(0, new Ellipse2D.Double(-1.0, -1.0, 2.0, 2.0));
        renderer.setSeriesShapesVisible(0, true);
        chart.getXYPlot().setRenderer(renderer);
        chart.getXYPlot().setNoDataMessage("There is no data for your choice");
        chart.getXYPlot().setNoDataMessageFont(new Font("sansserif", Font.BOLD, 32));
        
        return chart;
    }
    
    
    public static JFreeChart createMultiTimeseriesPlot(List<Map<DateTime, Float>> tsData,
            List<String> labels, String variableUnit) {
        if(tsData.size() != labels.size()){
            throw new IllegalArgumentException("tsData and labels must be the same length");
        }
        
        TimeSeriesCollection xydataset = new TimeSeriesCollection();
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        
        /*
         * Auto range seems to fail randomly, so we have to do it manually...
         */
        float low = Float.MAX_VALUE;
        float high = Float.MIN_VALUE;
        
        for(int i = 0; i < tsData.size(); i++){
            Map<DateTime, Float> tsDatum = tsData.get(i);
            String label = labels.get(i);
            TimeSeries ts = new TimeSeries(label);
            for (Entry<DateTime, Float> entry : tsDatum.entrySet()) {
                Float val = entry.getValue();
                if(val < low)
                    low = val;
                if(val > high)
                    high = val;
                ts.add(new Millisecond(entry.getKey().toDate()), entry.getValue());
            }
            xydataset.addSeries(ts);
            renderer.setSeriesShape(i, new Ellipse2D.Double(-1.0, -1.0, 2.0, 2.0));
            renderer.setSeriesShapesVisible(i, true);
        }
        
        String title = "";
        
        String yLabel = variableUnit;
        
        JFreeChart chart = ChartFactory.createTimeSeriesChart(title,
                "Date / time", yLabel, xydataset, true, false, false);
        chart.getXYPlot().setRenderer(renderer);
        chart.getXYPlot().setNoDataMessage("There is no data for your choice");
        chart.getXYPlot().setNoDataMessageFont(new Font("sansserif", Font.BOLD, 32));

        chart.getXYPlot().getDomainAxis().setAutoRange(true);
        chart.getXYPlot().getRangeAxis().setRange(low, high);
        return chart;
    }
    
    public static JFreeChart createVerticalProfilePlot(double lon, double lat, List<Float> dataValues, List<Double> elevationValues, String variableName,String variableUnit, DateTime dateTime)
    {
        if (elevationValues.size() != dataValues.size())
        {
            throw new IllegalArgumentException("Z values and data values not of same length");
        }

        String zAxisLabel = "Depth";
        boolean invertYAxis =true;
      
        NumberAxis zAxis = new NumberAxis(zAxisLabel + " (meters)");
        zAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        if (invertYAxis) zAxis.setInverted(true);
        ZAxisAndValues zAxisAndValues = new ZAxisAndValues(zAxis, elevationValues);
                
        //ZAxisAndValues zAxisAndValues = getZAxisAndValues(layer, elevationValues);
        // The elevation values might have been reversed
        elevationValues = zAxisAndValues.zValues;
        NumberAxis elevationAxis = zAxisAndValues.zAxis;
        elevationAxis.setAutoRangeIncludesZero(false);

        //String axisLabel = variableName + "(meters)";
        String axisLabel = variableName +"("+variableUnit+")"; 
        
        NumberAxis valueAxis = new NumberAxis(axisLabel);
        valueAxis.setAutoRangeIncludesZero(false);

        XYSeries series = new XYSeries("data", true); // TODO: more meaningful title
        for (int i = 0; i < elevationValues.size(); i++) {
            series.add(elevationValues.get(i), dataValues.get(i));
        }
        XYSeriesCollection xySeriesColl = new XYSeriesCollection();
        xySeriesColl.addSeries(series);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesShape(0, new Ellipse2D.Double(-1.0, -1.0, 2.0, 2.0));
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesShapesVisible(0, true);

        XYPlot plot = new XYPlot(xySeriesColl, elevationAxis, valueAxis, renderer);
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(Color.white);
        plot.setOrientation(PlotOrientation.HORIZONTAL);

        // Find the position of the profile in lon-lat coordinates for the label
        // HorizontalPosition lonLatPos = Utils.transformPosition(pos, DefaultGeographicCRS.WGS84);
        //double lon = Double.parseDouble(longi);
        String lonStr = Double.toString(Math.abs(lon)) + ((lon >= 0.0) ? "E" : "W");
        //double lat = Double.parseDouble(latt);
        String latStr = Double.toString(Math.abs(lat)) + ((lat >= 0.0) ? "N" : "S");
        String title = String.format("Profile of %s at %s, %s",variableName, lonStr, latStr);
        if (dateTime != null) {
            title += " at " + WmsUtils.dateTimeToISO8601(dateTime);
        }

        // Use default font and don't create a legend
        return new JFreeChart(title, null, plot, false);        
    }
    
    public static JFreeChart createMultiVerticalProfilePlot(List<Map<Double, Float>> dataValues,
            List<String> labels, String variableUnit, DateTime dateTime) {
        if(dataValues.size() != labels.size()){
            throw new IllegalArgumentException("must provide labels for each data series");
        }
        
        String zAxisLabel = "Depth";
        boolean invertYAxis = true;
        
        NumberAxis zAxis = new NumberAxis(zAxisLabel + " (meters)");
        zAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        zAxis.setInverted(invertYAxis);
        zAxis.setAutoRangeIncludesZero(false);

        NumberAxis valueAxis = new NumberAxis(variableUnit);
        valueAxis.setAutoRangeIncludesZero(false);
        
        XYSeriesCollection xySeriesColl = new XYSeriesCollection();
        for(int i=0; i<dataValues.size(); i++){
            Map<Double, Float> datumValues = dataValues.get(i);
            XYSeries series = new XYSeries(labels.get(i), true);
            for(Entry<Double, Float> entryPair : datumValues.entrySet()){
                series.add(entryPair.getKey(), entryPair.getValue());
            }
            xySeriesColl.addSeries(series);
        }
        valueAxis.setAutoRange(true);
        
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesShapesVisible(0, true);
        for(int i=0; i<dataValues.size(); i++)
            renderer.setSeriesShape(i, new Ellipse2D.Double(-1.0, -1.0, 2.0, 2.0));
        
        XYPlot plot = new XYPlot(xySeriesColl, zAxis, valueAxis, renderer);
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(Color.white);
        plot.setOrientation(PlotOrientation.HORIZONTAL);
        
        String title = "";
        if (dateTime != null) {
            title += " at " + WmsUtils.dateTimeToISO8601(dateTime);
        }
        
        // Use default font and don't create a legend
        return new JFreeChart(title, null, plot, true);        
    }
    
    

    public static JFreeChart createVerticalProfilePlot(Layer layer, HorizontalPosition pos,
            List<Double> elevationValues, List<Float> dataValues, DateTime dateTime)
    {
        if (elevationValues.size() != dataValues.size())
        {
            throw new IllegalArgumentException("Z values and data values not of same length");
        }

        ZAxisAndValues zAxisAndValues = getZAxisAndValues(layer, elevationValues);
        // The elevation values might have been reversed
        elevationValues = zAxisAndValues.zValues;
        NumberAxis elevationAxis = zAxisAndValues.zAxis;
        elevationAxis.setAutoRangeIncludesZero(false);

        NumberAxis valueAxis = new NumberAxis(getAxisLabel(layer));
        valueAxis.setAutoRangeIncludesZero(false);
        valueAxis.setAutoRange(true);

        XYSeries series = new XYSeries("data", true); // TODO: more meaningful title
        for (int i = 0; i < elevationValues.size(); i++) {
            series.add(elevationValues.get(i), dataValues.get(i));
        }
        XYSeriesCollection xySeriesColl = new XYSeriesCollection();
        xySeriesColl.addSeries(series);

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesShape(0, new Ellipse2D.Double(-1.0, -1.0, 2.0, 2.0));
        renderer.setSeriesPaint(0, Color.RED);
        renderer.setSeriesShapesVisible(0, true);

        XYPlot plot = new XYPlot(xySeriesColl, elevationAxis, valueAxis, renderer);
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(Color.white);
        plot.setOrientation(PlotOrientation.HORIZONTAL);

        // Find the position of the profile in lon-lat coordinates for the label
        HorizontalPosition lonLatPos = Utils.transformPosition(pos, DefaultGeographicCRS.WGS84);
        double lon = lonLatPos.getX();
        String lonStr = Double.toString(Math.abs(lon)) + ((lon >= 0.0) ? "E" : "W");
        double lat = lonLatPos.getY();
        String latStr = Double.toString(Math.abs(lat)) + ((lat >= 0.0) ? "N" : "S");
        String title = String.format("Profile of %s at %s, %s",
            layer.getTitle(), lonStr, latStr);
        if (dateTime != null) {
            title += " at " + WmsUtils.dateTimeToISO8601(dateTime);
        }

        // Use default font and don't create a legend
        return new JFreeChart(title, null, plot, false);
    }

    private static String getAxisLabel(Layer layer)
    {
        return WmsUtils.removeDuplicatedWhiteSpace(layer.getTitle()) + " (" + layer.getUnits() + ")";
    }

    public static JFreeChart createTransectPlot(Layer layer, LineString transectDomain,
            List<Float> transectData)
    {
        JFreeChart chart;
        XYPlot plot; 
        XYSeries series = new XYSeries("data", true); // TODO: more meaningful title
        for (int i = 0; i < transectData.size(); i++) {
            series.add(i, transectData.get(i));
        }
        XYSeriesCollection xySeriesColl = new XYSeriesCollection();
        xySeriesColl.addSeries(series);                  
  
        // If we have a layer with more than one elevation value, we create a transect chart
        // using standard XYItem Renderer to keep the plot renderer consistent with that of vertical section plot

        if (layer.getElevationValues().size() > 1)
        {
            final XYItemRenderer renderer1 = new StandardXYItemRenderer();
            final NumberAxis rangeAxis1 = new NumberAxis(getAxisLabel(layer));
            plot = new XYPlot(xySeriesColl, null, rangeAxis1, renderer1);
            plot.setRangeAxisLocation(AxisLocation.BOTTOM_OR_LEFT);
            plot.setBackgroundPaint(Color.lightGray);
            plot.setDomainGridlinesVisible(false);
            plot.setRangeGridlinePaint(Color.white);       
            plot.getRenderer().setSeriesPaint(0, Color.RED);
            plot.setOrientation(PlotOrientation.VERTICAL);
            chart = new JFreeChart(plot);
        }
        else   // If we have a layer which only has one elevation value, we simply create XY Line chart  
        {           
            chart = ChartFactory.createXYLineChart(
                    "Transect for " + layer.getTitle(), // title
                    "distance along transect (arbitrary units)", // TODO more meaningful x axis label
                    layer.getTitle() + " (" + layer.getUnits() + ")",
                    xySeriesColl,
                    PlotOrientation.VERTICAL,
                    false, // show legend
                    false, // show tooltips (?)
                    false // urls (?)
                    );                  
            plot = chart.getXYPlot();     
        }       
        if (layer.getDataset().getCopyrightStatement() != null) {
            final TextTitle textTitle = new TextTitle(layer.getDataset().getCopyrightStatement());
            textTitle.setFont(new Font("SansSerif", Font.PLAIN, 10));
            textTitle.setPosition(RectangleEdge.BOTTOM);
            textTitle.setHorizontalAlignment(HorizontalAlignment.RIGHT);
            chart.addSubtitle(textTitle);
        }
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();

        rangeAxis.setAutoRangeIncludesZero(false);
        plot.setNoDataMessage("There is no data for what you have chosen.");

        //Iterate through control points to show segments of transect
        Double prevCtrlPointDistance = null;
        for (int i = 0; i < transectDomain.getControlPoints().size(); i++) {
            double ctrlPointDistance = transectDomain.getFractionalControlPointDistance(i);
            if (prevCtrlPointDistance != null) {
                //determine start end end value for marker based on index of ctrl point
                IntervalMarker target = new IntervalMarker(
                        transectData.size() * prevCtrlPointDistance,
                        transectData.size() * ctrlPointDistance
                );
                // TODO: printing to two d.p. not always appropriate
                target.setLabel("[" + printTwoDecimals(transectDomain.getControlPoints().get(i - 1).getY())
                        + "," + printTwoDecimals(transectDomain.getControlPoints().get(i - 1).getX()) + "]");
                target.setLabelFont(new Font("SansSerif", Font.ITALIC, 11));
                //alter color of segment and position of label based on odd/even index
                if (i % 2 == 0) {
                    target.setPaint(new Color(222, 222, 255, 128));
                    target.setLabelAnchor(RectangleAnchor.TOP_LEFT);
                    target.setLabelTextAnchor(TextAnchor.TOP_LEFT);
                } else {
                    target.setPaint(new Color(233, 225, 146, 128));
                    target.setLabelAnchor(RectangleAnchor.BOTTOM_LEFT);
                    target.setLabelTextAnchor(TextAnchor.BOTTOM_LEFT);
                }
                //add marker to plot
                plot.addDomainMarker(target);
            }
            prevCtrlPointDistance = transectDomain.getFractionalControlPointDistance(i);
        } 

        return chart;
    }

    /**
     * Prints a double-precision number to 2 decimal places
     * @param d the double
     * @return rounded value to 2 places, as a String
     */
    private static String printTwoDecimals(double d)
    {
        DecimalFormat twoDForm = new DecimalFormat("#.##");
        // We need to set the Locale properly, otherwise the DecimalFormat doesn't
        // work in locales that use commas instead of points.
        // Thanks to Justino Martinez for this fix!
        DecimalFormatSymbols decSym = DecimalFormatSymbols.getInstance(US_LOCALE);
        twoDForm.setDecimalFormatSymbols(decSym);
        return twoDForm.format(d);
    }

    /** Simple class to hold a z axis and its values for a vertical section or
     * profile plot  */
    private static final class ZAxisAndValues
    {
        private final NumberAxis zAxis;
        private final List<Double> zValues;
        public ZAxisAndValues(NumberAxis zAxis, List<Double> zValues)
        {
            this.zAxis = zAxis;
            this.zValues = zValues;
        }
    }

    /** Creates a vertical axis for plotting the given elevation values from
     * the given layer */
    private static ZAxisAndValues getZAxisAndValues(Layer layer, List<Double> elevationValues)
    {
        // We can deal with three types of vertical axis: Height, Depth and Presssure.
        // The code for this is very messy in ncWMS, sorry about that...  We should
        // improve this but there are possible knock-on effects, so it's not a very
        // easy job.

        final String zAxisLabel;
        final boolean invertYAxis;
        if (layer.isElevationPositive()) {
            zAxisLabel = "Height";
            invertYAxis = false;
        } else if (layer.isElevationPressure()) {
            zAxisLabel = "Pressure";
            invertYAxis = true;
        } else {
            zAxisLabel = "Depth";
            // If this is a depth axis, all the values in elevationValues will be
            // negative, so we must reverse this (see CdmUtils.getZValues())
            List<Double> newElValues = new ArrayList<Double>(elevationValues.size());
            for (Double zVal : elevationValues) {
                newElValues.add(-zVal);
            }
            elevationValues = newElValues;
            invertYAxis = true;
        }

        NumberAxis zAxis = new NumberAxis(zAxisLabel + " (" + layer.getElevationUnits() + ")");
        zAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        if (invertYAxis) zAxis.setInverted(true);

        return new ZAxisAndValues(zAxis, elevationValues);
    }
    
    
    
    

    /**
     * Creates and returns a vertical section chart.
     * @param layer The Layer from which data have been read
     * @param horizPath The horizontal path described by the vertical section
     * @param elevationValues The elevation values for which we have data
     * @param sectionData The data values for the section data. Each
     * List&lt;Float&gt contains the values for each point on the horizontal path
     * for one of the elevation values.
     * @return
     */
    public static JFreeChart createVerticalSectionChart(Layer layer, LineString horizPath,
        List<Double> elevationValues, List<List<Float>> sectionData, Range<Float> colourScaleRange,
        ColorPalette palette, int numColourBands, boolean logarithmic,double zValue)
    {
        ZAxisAndValues zAxisAndValues = getZAxisAndValues(layer, elevationValues);
        // The elevation values might have been reversed
        elevationValues = zAxisAndValues.zValues;
        
        double minElValue = 0.0;
        double maxElValue = 1.0;
        if(elevationValues.size() != 0 && sectionData.size() != 0){
            minElValue = elevationValues.get(0);
            maxElValue = elevationValues.get(elevationValues.size() - 1);
        }


        // Sometimes values on the axes are reversed
        if (minElValue > maxElValue) {
            double temp = minElValue;
            minElValue = maxElValue;
            maxElValue = temp;
        }

        // TODO expand the minElValue and maxElValue a bit

        // The number of elevation values that will be represented in the final
        // dataset.  TODO: calculate this based on the minimum elevation spacing
        int numElValues = 300;

        XYZDataset dataset = new VerticalSectionDataset(elevationValues,
                sectionData, minElValue, maxElValue, numElValues);
        
        NumberAxis xAxis = new NumberAxis("Distance along path (arbitrary units)");
        xAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        PaintScale scale = createPaintScale(palette, colourScaleRange,
                numColourBands, logarithmic);

        NumberAxis colorScaleBar = new NumberAxis();
        org.jfree.data.Range colorBarRange = new org.jfree.data.Range(
            colourScaleRange.getMinimum(),
            colourScaleRange.getMaximum()
        );
        colorScaleBar.setRange(colorBarRange);

        PaintScaleLegend paintScaleLegend = new PaintScaleLegend(scale, colorScaleBar);
        paintScaleLegend.setPosition(RectangleEdge.BOTTOM);

        XYBlockRenderer renderer = new XYBlockRenderer();
        double elevationResolution = (maxElValue - minElValue) / numElValues;
        renderer.setBlockHeight(elevationResolution);
        renderer.setPaintScale(scale);
       
        XYPlot plot = new XYPlot(dataset, xAxis, zAxisAndValues.zAxis, renderer);
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinesVisible(false);
        plot.setRangeGridlinePaint(Color.white);

        // Iterate through control points to show segments of transect
        Double prevCtrlPointDistance = null;
        int xAxisLength = 0;
        if(sectionData.size() > 0)
            xAxisLength = sectionData.get(0).size();
        for (int i = 0; i < horizPath.getControlPoints().size(); i++) {
            double ctrlPointDistance = horizPath.getFractionalControlPointDistance(i);
            if (prevCtrlPointDistance != null) {
                //determine start end end value for marker based on index of ctrl point
                IntervalMarker target = new IntervalMarker(
                        xAxisLength * prevCtrlPointDistance,
                        xAxisLength * ctrlPointDistance
                );
                target.setPaint(TRANSPARENT);
                //add marker to plot
                plot.addDomainMarker(target);
                // add line marker to vertical section plot         
                final Marker verticalLevel = new ValueMarker(Math.abs(zValue));
                verticalLevel.setPaint(Color.lightGray);
                verticalLevel.setLabel("at "+zValue +"  level ");
                verticalLevel.setLabelAnchor(RectangleAnchor.BOTTOM_RIGHT);
                verticalLevel.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
                plot.addRangeMarker(verticalLevel); 
                
            }
            prevCtrlPointDistance = horizPath.getFractionalControlPointDistance(i);
        }
        
        JFreeChart chart = new JFreeChart(layer.getTitle() + " (" + layer.getUnits() + ")", plot);
        chart.removeLegend();
        chart.addSubtitle(paintScaleLegend);
        chart.setBackgroundPaint(Color.white);
        return chart;
    }

    /**
     * An {@link XYZDataset} that is created by interpolating a set of values
     * from a discrete set of elevations.
     */
    private static class VerticalSectionDataset extends AbstractXYZDataset
    {
        private static final long serialVersionUID = 1L;
        private final int horizPathLength;
        private final List<List<Float>> sectionData;
        private final List<Double> elevationValues;
        private final double minElValue;
        private final double elevationResolution;
        private final int numElevations;

        public VerticalSectionDataset(List<Double> elevationValues,
                List<List<Float>> sectionData, double minElValue, double maxElValue,
                int numElevations)
        {
            if(sectionData.size() > 0)
                this.horizPathLength = sectionData.get(0).size();
            else
                this.horizPathLength = 0;
            this.sectionData = sectionData;
            this.elevationValues = elevationValues;
            this.minElValue = minElValue;
            this.numElevations = numElevations;
            this.elevationResolution = (maxElValue - minElValue) / numElevations;
        }

        @Override
        public int getSeriesCount() { return 1; }

        @Override
        public String getSeriesKey(int series) {
            checkSeries(series);
            return "Vertical section";
        }

        @Override
        public int getItemCount(int series) {
            checkSeries(series);
            return this.horizPathLength * this.numElevations;
        }

        @Override
        public Integer getX(int series, int item) {
            checkSeries(series);
            // The x coordinate is just the integer index of the point along
            // the horizontal path
            return item % this.horizPathLength;
        }

        /**
         * Gets the value of elevation, assuming linear variation between min
         * and max.
         */
        @Override
        public Double getY(int series, int item) {
            checkSeries(series);
            int yIndex = item / this.horizPathLength;
            return this.minElValue + yIndex * this.elevationResolution;
        }

        /**
         * Gets the data value corresponding with the given item, interpolating
         * between the recorded data values using nearest-neighbour interpolation
         */
        @Override
        public Float getZ(int series, int item) {
            checkSeries(series);
            int xIndex = item % this.horizPathLength;
            double elevation = this.getY(series, item);
            // What is the index of the nearest elevation in the list of elevations
            // for which we have data?
            // TODO: factor this out into a utility routine
            int nearestElevationIndex = -1;
            double minDiff = Double.POSITIVE_INFINITY;
            for (int i = 0; i < this.elevationValues.size(); i++) {
                double el = this.elevationValues.get(i);
                double diff = Math.abs(el - elevation);
                if (diff < minDiff) {
                    minDiff = diff;
                    nearestElevationIndex = i;
                }
            }
            return sectionData.get(nearestElevationIndex).get(xIndex);
        }

        /**
         * @throws IllegalArgumentException if the argument is not zero.
         */
        private static void checkSeries(int series) {
            if (series != 0) throw new IllegalArgumentException("Series must be zero");
        }
    }

    /**
     * Creates and returns a JFreeChart {@link PaintScale} that converts data values
     * to {@link Color}s.
     */
    public static PaintScale createPaintScale(ColorPalette colorPalette,
            final Range<Float> colourScaleRange, final int numColourBands,
            final boolean logarithmic)
    {
        final IndexColorModel cm = colorPalette.getColorModel(numColourBands, 100,
                Color.white, Color.black, Color.black, true);

        return new PaintScale()
        {
            @Override
            public double getLowerBound() {
                return colourScaleRange.getMinimum();
            }

            @Override
            public double getUpperBound() {
                return colourScaleRange.getMaximum();
            }

            @Override
            public Color getPaint(double value) {
                // TODO: replicate/factor out code in ImageProducer.java
                int index = this.getColourIndex(value);
                return new Color(cm.getRGB(index));
            }

            /**
             * @return the colour index that corresponds to the given value
             * @todo This is adapted from ImageProducer.
             */
            private int getColourIndex(double value)
            {
                if (Double.isNaN(value)) {
                    return numColourBands; // represents a background pixel
                } else if (value < this.getLowerBound()) {
                    /*
                     * represents a low out-of-range pixel
                     */
                    return numColourBands + 1; 
                } else if (value > this.getUpperBound()) {
                    /*
                     * represents a high out-of-range pixel
                     */
                    return numColourBands + 2; 
                } else {
                    double min = logarithmic ? Math.log(this.getLowerBound()) : this
                            .getLowerBound();
                    double max = logarithmic ? Math.log(this.getUpperBound()) : this
                            .getUpperBound();
                    double val = logarithmic ? Math.log(value) : value;
                    double frac = (val - min) / (max - min);
                    // Compute and return the index of the corresponding colour
                    int index = (int) (frac * numColourBands);
                    // For values very close to the maximum value in the range,
                    // this
                    // index might turn out to be equal to this.numColourBands
                    // due to
                    // rounding error. In this case we subtract one from the
                    // index to
                    // ensure that such pixels are not displayed as background
                    // pixels.
                    if (index == numColourBands)
                        index--;
                    return index;
                }
            }
        };
    }

}
