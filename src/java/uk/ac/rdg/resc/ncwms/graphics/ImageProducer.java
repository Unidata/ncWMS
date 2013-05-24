/*
 * Copyright (c) 2007 The University of Reading
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

package uk.ac.rdg.resc.ncwms.graphics;

import gov.noaa.pmel.sgt.CartesianGraph;
import gov.noaa.pmel.sgt.CartesianRenderer;
import gov.noaa.pmel.sgt.ContourLevels;
import gov.noaa.pmel.sgt.DefaultContourLineAttribute;
import gov.noaa.pmel.sgt.GridAttribute;
import gov.noaa.pmel.sgt.JPane;
import gov.noaa.pmel.sgt.LinearTransform;
import gov.noaa.pmel.sgt.dm.SGTData;
import gov.noaa.pmel.sgt.dm.SGTGrid;
import gov.noaa.pmel.sgt.dm.SimpleGrid;
import gov.noaa.pmel.util.Dimension2D;
import gov.noaa.pmel.util.Range2D;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.rdg.resc.edal.util.Range;
import uk.ac.rdg.resc.edal.util.Ranges;
import uk.ac.rdg.resc.ncwms.util.WmsUtils;
import uk.ac.rdg.resc.ncwms.wms.Layer;

/**
 * An object that is used to render data into images.  Instances of this class
 * must be created through the {@link Builder}.
 *
 * @author Jon Blower
 */
public final class ImageProducer
{
    private static final Logger logger = LoggerFactory.getLogger(ImageProducer.class);

    //public static enum Style {BOXFILL, VECTOR, CONTOUR, BARB, ARROWS};
    public static enum Style {BOXFILL, VECTOR, CONTOUR, BARB, STUMPVEC, TRIVEC, LINEVEC, FANCYVEC};
    
    private Style style;
    // Width and height of the resulting picture
    private int picWidth;
    private int picHeight;
    private boolean transparent;
    private int opacity;
    private int numColourBands;
    private int numContours;
    private boolean logarithmic;  // True if the colour scale is to be logarithmic,
                                  // false if linear
    private Color bgColor;
    private Color lowColor;
    private Color highColor;
    private ColorPalette colorPalette;
    
    /**
     * Colour scale range of the picture.  An {@link Range#isEmpty() empty Range}
     * means that the picture will be auto-scaled.
     */
    private Range<Float> scaleRange;
    
    /**
     * The length of arrows in pixels, only used for vector plots
     */
    private float arrowLength = 14.0f;
    private float barbLength = 28.0f;
    private String units;
    private int equator_y_index;
    public float vectorScale;
    
    // set of rendered images, ready to be turned into a picture
    private List<BufferedImage> renderedFrames = new ArrayList<BufferedImage>();
    
    // If we need to cache the frame data and associated labels (we do this if
    // we have to auto-scale the image) this is where we put them.
    private static final class Components {
        private final List<Float> x;
        private final List<Float> y;
        public Components(List<Float> x, List<Float> y) {
            this.x = x;
            this.y = y;
        }
        public Components(List<Float> x) {
            this(x, null);
        }
        public List<Float> getMagnitudes() {
            return this.y == null ? this.x : WmsUtils.getMagnitudes(this.x, this.y);
        }
    }
    private List<Components> frameData;
    
    private List<String> labels;

    /** Prevents direct instantiation */
    private ImageProducer() {}

    public BufferedImage getLegend(Layer layer)
    {
        return this.colorPalette.createLegend(this.numColourBands, layer.getTitle(),
            layer.getUnits(), this.logarithmic, this.scaleRange);
    }
    
    public int getPicWidth()
    {
        return picWidth;
    }
    
    public int getPicHeight()
    {
        return picHeight;
    }
    
    public boolean isTransparent()
    {
        return transparent;
    }
    
    /**
     * Adds a frame of scalar data to this ImageProducer.  If the data cannot yet be rendered
     * into a BufferedImage, the data and label are stored.
     */
    public void addFrame(List<Float> data, String label)
    {
        this.addFrame(data, null, label);
    }
    
    /**
     * Adds a frame of vector data to this ImageProducer.  If the data cannot yet be rendered
     * into a BufferedImage, the data and label are stored.
     */
    public void addFrame(List<Float> xData, List<Float> yData, String label)
    {
        logger.debug("Adding frame with label {}", label);
        Components comps = new Components(xData, yData);
        if (this.scaleRange.isEmpty())
        {
            logger.debug("Auto-scaling, so caching frame");
            if (this.frameData == null)
            {
                this.frameData = new ArrayList<Components>();
                this.labels = new ArrayList<String>();
            }
            this.frameData.add(comps);
            this.labels.add(label);
        }
        else
        {
            logger.debug("Scale is set, so rendering image");
            this.renderedFrames.add(this.createImage(comps, label));
        }
    }

    /**
     * Returns the {@link IndexColorModel} which will be used by this ImageProducer
     */
    public IndexColorModel getColorModel()
    {
        return this.colorPalette.getColorModel(this.numColourBands,
            this.opacity, this.bgColor, this.lowColor, this.highColor, this.transparent);
    }
    
    /**
     * Creates and returns a single frame as an Image, based on the given data.
     * Adds the label if one has been set.  The scale must be set before
     * calling this method.
     */
    private BufferedImage createImage(Components comps, String label) {
        if (style == Style.CONTOUR) {
            return createContourImage(comps, label);
        }

        byte[] pixels = new byte[this.picWidth * this.picHeight];
        String arrowStyle = "TRIVEC";

        List<Float> magnitudes = comps.getMagnitudes();
        //if (style != Style.ARROWS && style != Style.BARB) {
        if (!isArrowStyle(style) && style != Style.BARB) {
            // We get the magnitude of the input data (takes care of the case
            // in which the data are two components of a vector)
            for (int i = 0; i < pixels.length; i++) {
                // The image coordinate system has the vertical axis increasing
                // downward, but the data's coordinate system has the vertical
                // axis
                // increasing upwards. The method below flips the axis
                int dataIndex = this.getDataIndex(i);
                pixels[i] = (byte) this.getColourIndex(magnitudes.get(dataIndex));
            }
            arrowStyle = "STUMPVEC";
        } else {
            Arrays.fill(pixels, (byte) this.numColourBands);
        }
        // Create a ColorModel for the image
        ColorModel colorModel = this.getColorModel();

        // Create the Image
        DataBuffer buf = new DataBufferByte(pixels, pixels.length);
        SampleModel sampleModel = colorModel.createCompatibleSampleModel(this.picWidth,
                this.picHeight);
        WritableRaster raster = Raster.createWritableRaster(sampleModel, buf, null);
        BufferedImage image = new BufferedImage(colorModel, raster, false, null);

        // Add the label to the image
        // TODO: colour needs to change with different palettes!
        if (label != null && !label.equals("")) {
            Graphics2D gfx = (Graphics2D) image.getGraphics();
            gfx.setPaint(new Color(0, 0, 143));
            gfx.fillRect(1, image.getHeight() - 19, image.getWidth() - 1, 18);
            gfx.setPaint(new Color(255, 151, 0));
            gfx.drawString(label, 10, image.getHeight() - 5);
        }

        if (style == Style.VECTOR || isArrowStyle(style) || style == Style.BARB) {
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(0, 0, 0));

            float stepScale = 1.1f;
            float imageLength = this.arrowLength;

            if (this.style == Style.BARB) {
                imageLength = this.barbLength * this.vectorScale;
                stepScale = 1.2f * this.vectorScale;
            } else {
                imageLength = this.arrowLength * this.vectorScale;
                stepScale = 1.1f * this.vectorScale;
            }

            int index;
            int dataIndex;
            double radangle;
            Double mag;
            Float eastVal;
            Float northVal;

            for (int i = 0; i < this.picWidth; i += Math.ceil(imageLength + stepScale)) {
                for (int j = 0; j < this.picHeight; j += Math.ceil(imageLength + stepScale)) {
                    dataIndex = this.getDataIndex(i, j);
                    eastVal = comps.x.get(dataIndex);
                    northVal = comps.y.get(dataIndex);
                    if (eastVal != null && northVal != null) {
                        radangle = Math.atan2(eastVal.doubleValue(), northVal.doubleValue());
                        mag = new Double(magnitudes.get(dataIndex));

                        // Color arrow
                        index = this.getColourIndex(mag.floatValue());
                        if (this.style != Style.VECTOR) {
                            g.setColor(new Color(colorModel.getRGB(index)));
                        }
                        if (this.style == Style.BARB) {
                            g.setStroke(new BasicStroke(1));
                            BarbFactory.renderWindBarbForSpeed(mag, radangle, i, j, this.units,
                                    this.vectorScale, j >= this.equator_y_index, g);
                        } else {
                            // Arrows. We need to pick the style arrow now
                        	arrowStyle = style.toString();
                            VectorFactory.renderVector(arrowStyle, mag, radangle, i, j,
                                    this.vectorScale, g);
                        }
                    }
                }
            }
        }
        return image;
    }
    
    private BufferedImage createContourImage(Components comps, String label) {
        double[] values = new double[picWidth * picHeight];
        double[] xAxis = new double[picWidth];
        double[] yAxis = new double[picHeight];

        int count = 0;
        List<Float> magnitudes = comps.getMagnitudes();
        for (int i = 0; i < picWidth; i++) {
            xAxis[i] = i;
            for (int j = 0; j < picHeight; j++) {
                yAxis[j] = picHeight - j - 1;
                int index = i + (picHeight - j - 1) * picWidth;
                Float value = magnitudes.get(index);
                if(value == null) {
                    values[count] = Double.NaN;
                } else {
                    values[count] = value;
                }
                count++;
            }
        }
        
        Float scaleMin = scaleRange.getMinimum();
        Float scaleMax = scaleRange.getMaximum();
        
        SGTGrid sgtGrid = new SimpleGrid(values, xAxis, yAxis, null);

        CartesianGraph cg = getCartesianGraph(sgtGrid, picWidth, picHeight);

        double contourSpacing = (scaleMax - scaleMin) / numContours;

        Range2D contourValues = new Range2D(scaleMin, scaleMax, contourSpacing);

        ContourLevels clevels = ContourLevels.getDefault(contourValues);

        DefaultContourLineAttribute defAttr = new DefaultContourLineAttribute();

        defAttr.setLabelEnabled(true);
        clevels.setDefaultContourLineAttribute(defAttr);

        GridAttribute attr = new GridAttribute(clevels);
        attr.setStyle(GridAttribute.CONTOUR);

        CartesianRenderer renderer = CartesianRenderer.getRenderer(cg, sgtGrid, attr);

        BufferedImage image = new BufferedImage(picWidth, picHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        renderer.draw(g);
        return image;
    }

    private static CartesianGraph getCartesianGraph(SGTData data, int width, int height) {
        /*
         * To get fixed size labels we need to set a physical size much smaller
         * than the pixel size (since pixels can't represent physical size).
         * Since the SGT code is so heavily tied into the display mechanism, and
         * a factor of around 100 seems to produce decent results, it's almost
         * certainly measured in inches (96dpi being a fairly reasonable monitor
         * resolution).
         * 
         * Anyway, setting the physical size as a constant factor of the pixel
         * size gives good results.
         * 
         * Font size seems to be ignored.
         */
        double factor = 96;
        double physWidth = width / factor;
        double physHeight = height / factor;

        gov.noaa.pmel.sgt.Layer layer = new gov.noaa.pmel.sgt.Layer("", new Dimension2D(physWidth, physHeight));
        JPane pane = new JPane("id", new Dimension(width, height));
        layer.setPane(pane);
        layer.setBounds(0, 0, width, height);

        CartesianGraph graph = new CartesianGraph();
        // Create Ranges representing the size of the image
        Range2D physXRange = new Range2D(0, physWidth);
        Range2D physYRange = new Range2D(0, physHeight);
        // These transforms convert x and y coordinates to pixel indices
        LinearTransform xt = new LinearTransform(physXRange, data.getXRange());
        LinearTransform yt = new LinearTransform(physYRange, data.getYRange());
        graph.setXTransform(xt);
        graph.setYTransform(yt);
        layer.setGraph(graph);
        return graph;
    }
    

    /**
     * Calculates the index of the data point in a data array that corresponds
     * with the given index in the image array, taking into account that the
     * vertical axis is flipped.
     */
    private int getDataIndex(int imageIndex)
    {
        int imageI = imageIndex % this.picWidth;
        int imageJ = imageIndex / this.picWidth;
        return this.getDataIndex(imageI, imageJ);
    }

    /**
     * Calculates the index of the data point in a data array that corresponds
     * with the given index in the image array, taking into account that the
     * vertical axis is flipped.
     */
    private int getDataIndex(int imageI, int imageJ)
    {
        int dataJ = this.picHeight - imageJ - 1;
        int dataIndex = dataJ * this.picWidth + imageI;
        return dataIndex;
    }
    
    /**
     * @return the colour index that corresponds to the given value
     */
    public int getColourIndex(Float value) {
        if (value == null) {
            return this.numColourBands; // represents a background pixel
        } else if (value < scaleRange.getMinimum()) {
            return this.numColourBands + 1; // represents a low out-of-range pixel
        } else if (value > scaleRange.getMaximum()) {
            return this.numColourBands + 2; // represents a high out-of-range pixel
        } else {
            float scaleMin = this.scaleRange.getMinimum().floatValue();
            float scaleMax = this.scaleRange.getMaximum().floatValue();
            double min = this.logarithmic ? Math.log(scaleMin) : scaleMin;
            double max = this.logarithmic ? Math.log(scaleMax) : scaleMax;
            double val = this.logarithmic ? Math.log(value) : value;
            double frac = (val - min) / (max - min);
            // Compute and return the index of the corresponding colour
            int index = (int) (frac * this.numColourBands);
            // For values very close to the maximum value in the range, this
            // index might turn out to be equal to this.numColourBands due to
            // rounding error. In this case we subtract one from the index to
            // ensure that such pixels are not displayed as background pixels.
            if (index == this.numColourBands)
                index--;
            return index;
        }
    }
    
    /**
     * Gets the frames as BufferedImages, ready to be turned into a picture or
     * animation.  This is called just before the picture is due to be created,
     * so subclasses can delay creating the BufferedImages until all the data
     * has been extracted (for example, if we are auto-scaling an animation,
     * we can't create each individual frame until we have data for all the frames)
     * @return List of BufferedImages
     */
    public List<BufferedImage> getRenderedFrames()
    {
        this.setScale(); // Make sure the colour scale is set before proceeding
        // We render the frames if we have not done so already
        if (this.frameData != null)
        {
            logger.debug("Rendering image frames...");
            for (int i = 0; i < this.frameData.size(); i++)
            {
                logger.debug("    ... rendering frame {}", i);
                Components comps = this.frameData.get(i);
                this.renderedFrames.add(this.createImage(comps, this.labels.get(i)));
            }
        }
        return this.renderedFrames;
    }
    
    /**
     * Makes sure that the scale is set: if we are auto-scaling, this reads all
     * of the data we have stored to find the extremes.  If the scale has
     * already been set, this does nothing.
     */
    private void setScale()
    {
        if (this.scaleRange.isEmpty())
        {
            Float scaleMin = null;
            Float scaleMax = null;
            logger.debug("Setting the scale automatically");
            // We have a cache of image data, which we use to generate the colour scale
            for (Components comps : this.frameData)
            {
                // We only use the first component if this is a vector quantity
                Range<Float> range = Ranges.findMinMax(comps.x);
                // TODO: could move this logic to the Range/Ranges class
                if (!range.isEmpty())
                {
                    if (scaleMin == null || range.getMinimum().compareTo(scaleMin) < 0)
                    {
                        scaleMin = range.getMinimum();
                    }
                    if (scaleMax == null || range.getMaximum().compareTo(scaleMax) > 0)
                    {
                        scaleMax = range.getMaximum();
                    }
                }
            }
            this.scaleRange = Ranges.newRange(scaleMin, scaleMax);
        }
    }
    
    private boolean isArrowStyle(Style style){
    	
    	return style == Style.BARB || style == Style.FANCYVEC || style == Style.STUMPVEC || style == Style.TRIVEC || style == Style.LINEVEC;
    }

    public int getOpacity()
    {
        return opacity;
    }

    /**
     * Builds an ImageProducer
     * @todo make error handling and validity-checking more consistent
     */
    public static final class Builder
    {
        private int picWidth = -1;
        private int picHeight = -1;
        private boolean transparent = false;
        private int opacity = 100;
        private float vectorScale = 1;
        private int numColourBands = ColorPalette.MAX_NUM_COLOURS;
        private int numContours = 10;
        private Boolean logarithmic = null;
        private Color bgColor = Color.WHITE;
        private Color lowColor = null;
        private Color highColor = null;
        private Range<Float> scaleRange = null;
        private Style style = null;
        private ColorPalette colorPalette = null;
        private String units = null;
        private int equator_y_index = 0;

        /**
         * Sets the style to be used.  If not set or if the parameter is null,
         * {@link Style#BOXFILL} will be used
         */
        public Builder style(Style style)  {
            this.style = style;
            return this;
        }

        /**
         * Sets the colour palette.  If not set or if the parameter is null,
         * the default colour palette will be used.
         * {@see ColorPalette}
         */
        public Builder palette(ColorPalette palette) {
            this.colorPalette = palette;
            return this;
        }

        /** Sets the width of the picture (must be set: there is no default) */
        public Builder width(int width) {
            if (width < 0) throw new IllegalArgumentException();
            this.picWidth = width;
            return this;
        }

        /** Sets the height of the picture (must be set: there is no default) */
        public Builder height(int height) {
            if (height < 0) throw new IllegalArgumentException();
            this.picHeight = height;
            return this;
        }

        /** Sets whether or not background pixels should be transparent
         * (defaults to false) */
        public Builder transparent(boolean transparent) {
            this.transparent = transparent;
            return this;
        }

        /** Sets the opacity of the picture, from 0 to 100 (default 100) */
        public Builder opacity(int opacity) {
            if (opacity < 0 || opacity > 100) throw new IllegalArgumentException();
            this.opacity = opacity;
            return this;
        }
        
        /** Sets the vectorScale (defaults to 1.0) */
        public Builder vectorScale(float scale) {
            if (scale <= 0) throw new IllegalArgumentException();
            this.vectorScale = scale;
            return this;
        }
        
        /** Sets the layers units */
        public Builder units(String units) {
            this.units = units;
            return this;
        }

        /**
         * Sets the yindex that the hemisphere switches to southern value is 0
         * if it does not touch the southern hemisphere.
         */
        public Builder equator_y_index(int equator_y_index) {
            this.equator_y_index = equator_y_index;
            return this;
        }

        /**
         * Sets the colour scale range.  If not set (or if set to null), the min
         * and max values of the data will be used.
         */
        public Builder colourScaleRange(Range<Float> scaleRange) {
            this.scaleRange = scaleRange;
            return this;
        }

        /** Sets the number of colour bands to use in the image, from 0 to 254
         * (default 254) */
        public Builder numColourBands(int numColourBands) {
            if (numColourBands < 0 || numColourBands > ColorPalette.MAX_NUM_COLOURS) {
                throw new IllegalArgumentException();
            }
            this.numColourBands = numColourBands;
            return this;
        }
        
        /** Sets the number of contours to use in the image, from 2 (default 10) */
        public Builder numContours(int numContours) {
            if (numContours < 2) {
                throw new IllegalArgumentException();
            }
            this.numContours = numContours;
            return this;
        }

        /**
         * Sets whether or not the colour scale is to be spaced logarithmically
         * (default is false)
         */
        public Builder logarithmic(Boolean logarithmic) {
            this.logarithmic = logarithmic;
            return this;
        }

        /**
         * Sets the background colour, which is used only if transparent==false,
         * for background pixels.  Defaults to white.  If the passed-in color
         * is null, it is ignored.
         */
        public Builder backgroundColour(Color bgColor) {
            if (bgColor != null) this.bgColor = bgColor;
            return this;
        }
        
        public Builder lowOutOfRangeColour(Color lowColor) {
            this.lowColor = lowColor;
            return this;
        }
        
        public Builder highOutOfRangeColour(Color highColor) {
            this.highColor = highColor;
            return this;
        }

        /**
         * Checks the fields for internal consistency, then creates and returns
         * a new ImageProducer object.
         * @throws IllegalStateException if the builder cannot create a valid
         * ImageProducer object
         */
        public ImageProducer build()
        {
            if (this.picWidth < 0 || this.picHeight < 0) {
                throw new IllegalStateException("picture width and height must be >= 0");
            }

            ImageProducer ip = new ImageProducer();
            ip.picWidth = this.picWidth;
            ip.picHeight = this.picHeight;
            ip.opacity = this.opacity;
            ip.vectorScale = this.vectorScale;
            ip.units = this.units == null ? "" : this.units;
            ip.equator_y_index = this.equator_y_index;
            ip.transparent = this.transparent;
            ip.bgColor = this.bgColor;
            ip.lowColor = this.lowColor;
            ip.highColor = this.highColor;
            ip.numColourBands = this.numColourBands;
            ip.numContours = this.numContours;
            ip.style = this.style == null
                ? Style.BOXFILL
                : this.style;
            ip.colorPalette = this.colorPalette == null
                ? ColorPalette.get(null)
                : this.colorPalette;
            ip.logarithmic = this.logarithmic == null
                ? false
                : this.logarithmic.booleanValue();
            // Signifies auto-scaling
            Range<Float> emptyRange = Ranges.emptyRange();
            ip.scaleRange = this.scaleRange == null
                ? emptyRange
                : this.scaleRange;

            return ip;
        }
    }
}
