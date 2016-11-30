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
package uk.ac.rdg.resc.ncwms.controller;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.geotoolkit.geometry.GeneralDirectPosition;
import org.geotoolkit.geometry.GeneralEnvelope;
import org.geotoolkit.referencing.CRS;

import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.ui.RectangleInsets;
import org.joda.time.DateTime;
import org.joda.time.chrono.ISOChronology;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.geometry.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.AbstractController;

import uk.ac.rdg.resc.edal.cdm.PixelMap;
import uk.ac.rdg.resc.edal.cdm.PixelMap.PixelMapEntry;
import uk.ac.rdg.resc.edal.coverage.domain.Domain;
import uk.ac.rdg.resc.edal.coverage.domain.impl.HorizontalDomain;
import uk.ac.rdg.resc.edal.coverage.grid.GridCoordinates;
import uk.ac.rdg.resc.edal.coverage.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.coverage.grid.RectilinearGrid;
import uk.ac.rdg.resc.edal.coverage.grid.ReferenceableAxis;
import uk.ac.rdg.resc.edal.coverage.grid.RegularGrid;
import uk.ac.rdg.resc.edal.geometry.HorizontalPosition;
import uk.ac.rdg.resc.edal.geometry.LonLatPosition;
import uk.ac.rdg.resc.edal.geometry.impl.HorizontalPositionImpl;
import uk.ac.rdg.resc.edal.geometry.impl.LineString;
import uk.ac.rdg.resc.edal.util.Range;
import uk.ac.rdg.resc.edal.util.Ranges;
import uk.ac.rdg.resc.edal.util.Utils;
import uk.ac.rdg.resc.ncwms.exceptions.CurrentUpdateSequence;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidFormatException;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidUpdateSequence;
import uk.ac.rdg.resc.ncwms.exceptions.LayerNotDefinedException;
import uk.ac.rdg.resc.ncwms.exceptions.StyleNotDefinedException;
import uk.ac.rdg.resc.ncwms.exceptions.Wms1_1_1Exception;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.graphics.BilinearInterpolator;
import uk.ac.rdg.resc.ncwms.graphics.ColorPalette;
import uk.ac.rdg.resc.ncwms.graphics.ImageFormat;
import uk.ac.rdg.resc.ncwms.graphics.ImageProducer;
import uk.ac.rdg.resc.ncwms.graphics.KmzFormat;
import uk.ac.rdg.resc.ncwms.usagelog.UsageLogEntry;
import uk.ac.rdg.resc.ncwms.usagelog.UsageLogger;
import uk.ac.rdg.resc.ncwms.util.WmsUtils;
import uk.ac.rdg.resc.ncwms.wms.Dataset;
import uk.ac.rdg.resc.ncwms.wms.Layer;
import uk.ac.rdg.resc.ncwms.wms.ScalarLayer;
import uk.ac.rdg.resc.ncwms.wms.VectorLayer;

/**
 * <p>This Controller is the entry point for all standard WMS operations
 * (GetMap, GetCapabilities, GetFeatureInfo).  Only one WmsController object 
 * is created.  Spring manages the creation of this object and the injection 
 * of the objects that it needs (i.e. its dependencies), such as the
 * {@linkplain ServerConfig configuration object}.
 * The Spring configuration file <tt>web/WEB-INF/WMS-servlet.xml</tt>
 * defines all this information and also defines that this Controller will handle
 * all requests to the URI pattern <tt>/wms</tt>.  (See the SimpleUrlHandlerMapping
 * in <tt>web/WEB-INF/WMS-servlet.xml</tt>).</p>
 *
 * <p>See the {@link #handleRequestInternal handleRequestInternal()}
 * method for more information.</p>
 *
 * <p><i>(Note that we cannot use a CommandController here
 * because there is no (apparent) way in Spring to use case-insensitive parameter
 * names to bind request parameters to an object.)</i></p>
 *
 * @author Jon Blower
 */
public abstract class AbstractWmsController extends AbstractController {

    private static final Logger log = LoggerFactory.getLogger(AbstractWmsController.class);
    /**
     * The maximum number of layers that can be requested in a single GetMap
     * operation
     */
    private static final int LAYER_LIMIT = 1;
    private static final String FEATURE_INFO_XML_FORMAT = "text/xml";
    private static final String FEATURE_INFO_PNG_FORMAT = "image/png";

    // These objects will be injected by Spring
    protected ServerConfig serverConfig;
    protected UsageLogger usageLogger;

    /**
     * Called automatically by Spring after all the dependencies have been
     * injected.
     */
    public void init() throws Exception {
        // We initialize the ColorPalettes.  We need to do this from here
        // because we need a way to find out the real path of the 
        // directory containing the palettes.  Therefore we need a way of 
        // getting at the ServletContext object, which isn't available from
        // the ColorPalette class.
        try {
            File paletteLocationDir = this.serverConfig.getPaletteFilesLocation(
                this.getServletContext());
            if (paletteLocationDir != null && paletteLocationDir.exists()
                    && paletteLocationDir.isDirectory()) {
                ColorPalette.loadPalettes(paletteLocationDir);
            } else {
                log.info("Directory of palette files does not exist or is not a directory");
            }
        } catch (Exception e) {
            log.error("Problem finding directory of colour palettes", e);
        }
    }

    /**
     * <p>Entry point for all requests to the WMS.  This method first 
     * creates a <tt>RequestParams</tt> object from the URL query string.  This
     * object provides methods for retrieving parameter values, based on the fact that
     * WMS parameter <i>names</i> are case-insensitive.</p>
     * 
     * <p>Based on the value of the
     * REQUEST parameter this method then delegates to
     * {@link #getCapabilities getCapabilities()}, {@link #getMap getMap()}
     * or {@link #getFeatureInfo getFeatureInfo()}.
     * If the information returned from
     * this method is to be presented as an XML/JSON/HTML document, the method returns
     * a ModelAndView object containing the name of a JSP page and the data that the JSP
     * needs to render.  If the information is to be presented as an image, the method
     * writes the image to the servlet's output stream, then returns null.</p>
     *
     * <p>Any Exceptions that are thrown by this method or its delegates are 
     * automatically handled by Spring and converted to XML to be presented to the
     * user.  See the <a href="../exceptions/package-summary.html">Exceptions package</a>
     * for more details.</p>
     */
    @Override
    protected ModelAndView handleRequestInternal(HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) throws Exception {
        UsageLogEntry usageLogEntry = new UsageLogEntry(httpServletRequest);
        boolean logUsage = true;

        // Create an object that allows request parameters to be retrieved in
        // a way that is not sensitive to the case of the parameter NAMES
        // (but is sensitive to the case of the parameter VALUES).
        RequestParams params = new RequestParams(httpServletRequest.getParameterMap());

        try {
            // Check the REQUEST parameter to see if we're producing a capabilities
            // document, a map or a FeatureInfo
            String request = params.getMandatoryString("request");
            usageLogEntry.setWmsOperation(request);
            return this.dispatchWmsRequest(request, params, httpServletRequest,
                        httpServletResponse, usageLogEntry);
        } catch (WmsException wmse) {
            // We don't log these errors
            usageLogEntry.setException(wmse);
            String wmsVersion = params.getWmsVersion();
            if (wmsVersion != null && wmsVersion.equals("1.1.1")) {
                // We create a new exception type to ensure that the correct
                // JSP is used to render it.  This class also translates any
                // exception codes that are different in 1.1.1 (i.e. InvalidCRS/SRS)
                throw new Wms1_1_1Exception(wmse);
            }
            throw wmse;
        } catch (SocketException se) {
            // SocketExceptions usually happen when the client has aborted the
            // connection, so there's nothing we can do here
            return null;
        } catch (IOException ioe) {
            // Filter out Tomcat ClientAbortExceptions, which for some reason
            // don't inherit from SocketException.
            // We check the class name to avoid a compile-time dependency on the
            // Tomcat libraries
            if (ioe.getClass().getName().equals("org.apache.catalina.connector.ClientAbortException")) {
                return null;
            }
            // Other types of IOException are potentially interesting and
            // must be rethrown to avoid hiding errors (maybe they
            // represent internal errors when reading data for instance).
            throw ioe;
        } catch (Exception e) {
            // An unexpected (internal) error has occurred
            usageLogEntry.setException(e);
            e.printStackTrace();
            throw e;
        } finally {
            if (logUsage && this.usageLogger != null) {
                // Log this request to the usage log
                this.usageLogger.logUsage(usageLogEntry);
            }
        }
    }

    /**
     * Object that returns a Layer given a layer Name, which is unique within a
     * Capabilities document
     */
    public static interface LayerFactory
    {
        /**
         * Returns a Layer given a layer Name, which is unique within a
         * Capabilities document
         */
        public Layer getLayer(String layerName) throws LayerNotDefinedException;
    }

    /**
     * Dispatches the request to the relevant methods (e.g. getCapabilities(),
     * getMap()).  Subclasses can override this to perform any pre- or post-
     * processing before/after calling these handlers.
     * @param request The value of the REQUEST parameter from the client, e.g.
     * "GetCapabilities".  Will never be null.
     * @todo Use an enum instead of the request string
     */
    protected abstract ModelAndView dispatchWmsRequest(String request,
        RequestParams params, HttpServletRequest httpServletRequest,
        HttpServletResponse httpServletResponse, UsageLogEntry usageLogEntry)
        throws Exception;

    /**
     * Executes the GetCapabilities operation, returning a ModelAndView for
     * display of the information as an XML document.  If the user has
     * requested VERSION=1.1.1 the information will be rendered using
     * <tt>web/WEB-INF/jsp/capabilities_xml_1_1_1.jsp</tt>.  If the user
     * specifies VERSION=1.3.0 (or does not specify a version) the information
     * will be rendered using <tt>web/WEB-INF/jsp/capabilities_xml.jsp</tt>.
     * @param datasets The collection of datasets to include in the Capabilities
     * document.  Must not be null
     * @param lastUpdateTime The last update time of this Capabilities document,
     * or null if unknown
     * @throws IOException if there was an i/o error getting the dataset(s) from
     * the underlying data store
     */
    protected ModelAndView getCapabilities(Collection<? extends Dataset> datasets,
            DateTime lastUpdateTime, RequestParams params,
            HttpServletRequest httpServletRequest, UsageLogEntry usageLogEntry)
            throws WmsException, IOException {
        // Check the SERVICE parameter
        String service = params.getMandatoryString("service");
        if (!service.equals("WMS")) {
            throw new WmsException("The value of the SERVICE parameter must be \"WMS\"");
        }

        // Check the VERSION parameter (not compulsory for GetCapabilities)
        String versionStr = params.getWmsVersion();
        usageLogEntry.setWmsVersion(versionStr);

        // Check the FORMAT parameter
        String format = params.getString("format");
        usageLogEntry.setOutputFormat(format);
        // The WMS 1.3.0 spec says that we can respond with the default text/xml
        // format if the client has requested an unknown format.  Hence we do
        // nothing here.

        // Do UPDATESEQUENCE negotiation according to WMS 1.3.0 spec (sec 7.2.3.5)
        String updateSeqStr = params.getString("updatesequence");
        if (updateSeqStr != null) {
            DateTime updateSequence;
            try {
                updateSequence = WmsUtils.iso8601ToDateTime(updateSeqStr, ISOChronology.getInstanceUTC());
            } catch (IllegalArgumentException iae) {
                throw new InvalidUpdateSequence(updateSeqStr +
                        " is not a valid ISO date-time");
            }
            // We use isEqual(), which compares dates based on millisecond values
            // only, because we know that the calendar system will be
            // the same in each case (ISO).  Comparisons using equals() may return false
            // because updateSequence is read using UTC, whereas lastUpdate is
            // created in the server's time zone, meaning that the Chronologies
            // are different.
            if (updateSequence.isEqual(lastUpdateTime)) {
                throw new CurrentUpdateSequence(updateSeqStr);
            } else if (updateSequence.isAfter(lastUpdateTime)) {
                throw new InvalidUpdateSequence(updateSeqStr +
                        " is later than the current server updatesequence value");
            }
        }

        // Find out whether we are going to represent times in a verbose or
        // concise way in the Capabilities document.
        boolean verboseTimes = params.getBoolean("verbose", false);

        Map<String, Object> models = new HashMap<String, Object>();
        models.put("config", this.serverConfig);
        models.put("datasets", datasets);
        // We use the current time if the last update time is unknown
        models.put("lastUpdate", lastUpdateTime == null ? new DateTime() : lastUpdateTime);
        models.put("wmsBaseUrl", httpServletRequest.getRequestURL().toString());
        // Show only a subset of the CRS codes that we are likely to use.
        // Otherwise Capabilities doc gets very large indeed.
        // TODO: make configurable in admin app
        String[] supportedCrsCodes = new String[]{
            "EPSG:4326", "CRS:84", // Plate Carree
            "EPSG:41001", // Mercator
            "EPSG:27700", // British National Grid
            // See http://nsidc.org/data/atlas/ogc_services.html for useful
            // stuff about polar stereographic projections
            "EPSG:3408", // NSIDC EASE-Grid North
            "EPSG:3409", // NSIDC EASE-Grid South
            "EPSG:3857", "EPSG:900913", // Google Maps
            "EPSG:32661", // North Polar stereographic
            "EPSG:32761" // South Polar stereographic
        };
        models.put("supportedCrsCodes", supportedCrsCodes); //*/HorizontalGrid.SUPPORTED_CRS_CODES);
        models.put("supportedImageFormats", ImageFormat.getSupportedMimeTypes());
        models.put("layerLimit", LAYER_LIMIT);
        models.put("featureInfoFormats", new String[]{FEATURE_INFO_PNG_FORMAT,
                    FEATURE_INFO_XML_FORMAT});
        models.put("legendWidth", ColorPalette.LEGEND_WIDTH);
        models.put("legendHeight", ColorPalette.LEGEND_HEIGHT);
        models.put("paletteNames", ColorPalette.getAvailablePaletteNames());
        models.put("verboseTimes", verboseTimes);

        // Do WMS version negotiation.  From the WMS 1.3.0 spec:
        // * If a version unknown to the server and higher than the lowest
        //   supported version is requested, the server shall send the highest
        //   version it supports that is less than the requested version.
        // * If a version lower than any of those known to the server is requested,
        //   then the server shall send the lowest version it supports.
        // We take the version to be 1.3.0 if not specified
        WmsVersion wmsVersion = versionStr == null
                ? WmsVersion.VERSION_1_3_0
                : new WmsVersion(versionStr);
        if (wmsVersion.compareTo(WmsVersion.VERSION_1_3_0) >= 0) {
            // version is >= 1.3.0. Send 1.3.0 Capabilities
            return new ModelAndView("capabilities_xml", models);
        } else {
            // version is < 1.3.0. Send 1.1.1 Capabilities
            return new ModelAndView("capabilities_xml_1_1_1", models);
        }
    }

    /**
     * Executes the GetMap operation.  This methods performs the following steps:
     * <ol>
     * <li>Creates a {@link GetMapRequest} object from the given {@link RequestParams}.
     * This parses the parameters and checks their validity.</li>
     * <li>Finds the relevant {@link Layer} object from the config system.</li>
     * <li>Creates a {@link HorizontalGrid} object that represents the grid on
     * which the final image will sit (based on the requested CRS and image
     * width/height).</li>
     * <li>Looks for TIME and ELEVATION parameters (TIME may be expressed as a
     * start/end range, in which case we will produce an animation).</li>
     * <li>Extracts the data, returning an array of floats, representing
     * the data values at each pixel in the final image.</li>
     * <li>Uses an {@link ImageProducer} object to turn the array of data into
     * a {@link java.awt.image.BufferedImage} (or, in the case of an animation, several
     * {@link java.awt.image.BufferedImage}s).</li>
     * <li>Uses a {@link ImageFormat} object to write the image to the servlet's
     * output stream in the requested format.</li>
     * </ol>
     * @throws WmsException if the user has provided invalid parameters
     * @throws Exception if an internal error occurs
     * @see uk.ac.rdg.resc.ncwms.datareader.DataReader#read DataReader.read()
     * @see uk.ac.rdg.resc.ncwms.datareader.DefaultDataReader#read DefaultDataReader.read()
     * @todo Separate Model and View code more cleanly
     */
    protected ModelAndView getMap(RequestParams params, LayerFactory layerFactory,
            HttpServletResponse httpServletResponse, UsageLogEntry usageLogEntry)
            throws WmsException, Exception
    {
        // Parse the URL parameters
        GetMapRequest getMapRequest = new GetMapRequest(params);
        usageLogEntry.setGetMapRequest(getMapRequest);

        GetMapStyleRequest styleRequest = getMapRequest.getStyleRequest();

        // Get the ImageFormat object corresponding with the requested MIME type
        String mimeType = styleRequest.getImageFormat();
        // This throws an InvalidFormatException if the MIME type is not supported
        ImageFormat imageFormat = ImageFormat.get(mimeType);

        GetMapDataRequest dr = getMapRequest.getDataRequest();

        // Check the dimensions of the image
        if (dr.getHeight() > this.serverConfig.getMaxImageHeight() ||
            dr.getWidth()  > this.serverConfig.getMaxImageWidth()) {
            throw new WmsException("Requested image size exceeds the maximum of "
                + this.serverConfig.getMaxImageWidth() + "x"
                + this.serverConfig.getMaxImageHeight());
        }

        String layerName = getLayerName(dr);
        Layer layer = layerFactory.getLayer(layerName);
        usageLogEntry.setLayer(layer);

        // Get the grid onto which the data will be projected
        RegularGrid grid = WmsUtils.getImageGrid(dr);
        
        CoordinateReferenceSystem sourcecs = WmsUtils.getCrs(dr.getCrsCode());
        CoordinateReferenceSystem wgs84cs = WmsUtils.getCrs("CRS:84");
        GeneralEnvelope bbox = new GeneralEnvelope((Envelope)grid.getExtent());
        bbox = (GeneralEnvelope) CRS.transform(bbox, wgs84cs);
        
        double[] urc = bbox.getUpperCorner().getCoordinate();
        double[] llc = bbox.getLowerCorner().getCoordinate();
        // Assume grid does not cross equator.
        int equator_y_index = dr.getHeight();
        
        if (llc[1] < 0) {
          // Dataset is 100% in the southern hemisphere or crosses the equator
          GeneralDirectPosition gdp = new GeneralDirectPosition(wgs84cs);
          gdp.setLocation(llc[0],0);
          GeneralEnvelope yaxisbox = (GeneralEnvelope) CRS.transform(new GeneralEnvelope(gdp,gdp),sourcecs);
          HorizontalPosition hp = new HorizontalPositionImpl(yaxisbox.getUpperCorner().getCoordinate()[0], yaxisbox.getUpperCorner().getCoordinate()[1], sourcecs);
          GridCoordinates gc = grid.findNearestGridPoint(hp);
          if (gc != null) {
            // Crosses the Equator
            equator_y_index = dr.getHeight() - gc.getCoordinateValue(1);
          } else {
            // Does not cross the equator
            equator_y_index = 0;
          }
        }

        // Create an object that will turn data into BufferedImages
        Range<Float> scaleRange = styleRequest.getColorScaleRange();
        if (scaleRange == null) scaleRange = layer.getApproxValueRange();
        Boolean logScale = styleRequest.isScaleLogarithmic();
        if (logScale == null) logScale = layer.isLogScaling();
        ImageProducer.Style style = layer instanceof VectorLayer
                ? ImageProducer.Style.VECTOR
                : ImageProducer.Style.BOXFILL;
        ColorPalette palette = layer.getDefaultColorPalette();
        String[] styles = styleRequest.getStyles();
        boolean smoothed = false;
        if (styles.length > 0) {
            String[] styleStrEls = styles[0].split("/");

            // Get the style type
            String styleType = styleStrEls[0];
            if (styleType.equalsIgnoreCase("boxfill")) style = ImageProducer.Style.BOXFILL;
            else if (styleType.equalsIgnoreCase("vector")) style = ImageProducer.Style.VECTOR;
            else if (styleType.equalsIgnoreCase("arrows")) style = ImageProducer.Style.ARROWS;
            else if (styleType.equalsIgnoreCase("barb")) style = ImageProducer.Style.BARB;
            else if (styleType.equalsIgnoreCase("contour")){
            	style = ImageProducer.Style.CONTOUR;
            	smoothed = true;
            }
            else if (styleType.equalsIgnoreCase("fancyvec")) style = ImageProducer.Style.FANCYVEC;
            else if (styleType.equalsIgnoreCase("linevec")) style = ImageProducer.Style.LINEVEC;
            else if (styleType.equalsIgnoreCase("stumpvec")) style = ImageProducer.Style.STUMPVEC;
            else if (styleType.equalsIgnoreCase("trivec")) style = ImageProducer.Style.TRIVEC;
            else if (styleType.equalsIgnoreCase("prettyvec")) style = ImageProducer.Style.PRETTYVEC;
            else throw new StyleNotDefinedException("The style " + styles[0] +
                " is not supported by this server");

            // Now get the colour palette
            String paletteName = null;
            if (styleStrEls.length > 1) paletteName = styleStrEls[1];
            palette = ColorPalette.get(paletteName);
            if (palette == null) {
                throw new StyleNotDefinedException("There is no palette with the name "
                    + paletteName);
            }
        }

        ImageProducer imageProducer = new ImageProducer.Builder()
            .width(dr.getWidth())
            .height(dr.getHeight())
            .style(style)
            .palette(palette)
            .colourScaleRange(scaleRange)
            .backgroundColour(styleRequest.getBackgroundColour())
            .lowOutOfRangeColour(styleRequest.getLowOutOfRangeColour())
            .highOutOfRangeColour(styleRequest.getHighOutOfRangeColour())
            .transparent(styleRequest.isTransparent())
            .logarithmic(logScale)
            .opacity(styleRequest.getOpacity())
            .numColourBands(styleRequest.getNumColourBands())
            .numContours(styleRequest.getNumContours())
            .vectorScale(styleRequest.getVectorScaleFactor())
            .equator_y_index(equator_y_index)
            .build();
        // Need to make sure that the images will be compatible with the
        // requested image format
        if (imageProducer.isTransparent() && !imageFormat.supportsFullyTransparentPixels()) {
            throw new WmsException("The image format " + mimeType +
                    " does not support fully-transparent pixels");
        }
        if (imageProducer.getOpacity() < 100 && !imageFormat.supportsPartiallyTransparentPixels()) {
            throw new WmsException("The image format " + mimeType +
                    " does not support partially-transparent pixels");
        }

        double zValue = getElevationValue(dr.getElevationString(), layer);

        // Cycle through all the provided timesteps, extracting data for each step
        List<String> tValueStrings = new ArrayList<String>();
        List<DateTime> timeValues = getTimeValues(dr.getTimeString(), layer);
        if (timeValues.size() > 1 && !imageFormat.supportsMultipleFrames()) {
            throw new WmsException("The image format " + mimeType +
                    " does not support multiple frames");
        }
        usageLogEntry.setNumTimeSteps(timeValues.size());
        long beforeExtractData = System.currentTimeMillis();
        // Use a single null time value if the layer has no time axis
        if (timeValues.isEmpty()) timeValues = Arrays.asList((DateTime)null);
        boolean googleEarth = imageFormat instanceof KmzFormat;
        for (DateTime timeValue : timeValues) {
            // Only add a label if this is part of an animation
            String tValueStr = "";
            if (timeValues.size() > 1 && timeValue != null) {
                tValueStr = WmsUtils.dateTimeToISO8601(timeValue);
            }
            tValueStrings.add(tValueStr);

            if (layer instanceof ScalarLayer) {
                // Note that if the layer doesn't have a time axis, timeValue==null but this
                // will be ignored by readHorizontalPoints()
                List<Float> data = this.readDataGrid((ScalarLayer)layer, timeValue, zValue, grid, usageLogEntry, smoothed);
                imageProducer.addFrame(data, tValueStr);
            } else if (layer instanceof VectorLayer) {
                VectorLayer vecLayer = (VectorLayer)layer;
                
                List<Float>[] xyVals = vecLayer.readXYComponents(timeValue, zValue, grid);
                
                /*
                 * Now add the image frame
                 */
                imageProducer.addFrame(xyVals[0], xyVals[1], tValueStr);
            } else {
                throw new IllegalStateException("Unrecognized layer type");
            }
        }
        long timeToExtractData = System.currentTimeMillis() - beforeExtractData;
        usageLogEntry.setTimeToExtractDataMs(timeToExtractData);

        // We only create a legend object if the image format requires it
        BufferedImage legend = imageFormat.requiresLegend() ? imageProducer.getLegend(layer) : null;

        // Write the image to the client.
        // First we set the HTTP headers
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        httpServletResponse.setContentType(mimeType);
        // If this is a KMZ file give it a sensible filename
        if (googleEarth) {
            httpServletResponse.setHeader("Content-Disposition", "inline; filename=" +
                    layer.getDataset().getId() + "_" + layer.getId() + ".kmz");
        }
        // Render the images and write to the output stream
        imageFormat.writeImage(imageProducer.getRenderedFrames(),
                httpServletResponse.getOutputStream(), layer, tValueStrings,
                dr.getElevationString(), grid.getExtent(), legend);

        return null;
    }

    /**
     * Utility method for getting the layer name (unique within a Capabilities
     * document) from the given GetMapRequest, checking that there is only one
     * layer in the request
     */
    private static String getLayerName(GetMapDataRequest getMapDataRequest) throws WmsException
    {
        // Find which layer the user is requesting
        String[] layers = getMapDataRequest.getLayers();
        if (layers.length == 0)
        {
            throw new WmsException("Must provide a value for the LAYERS parameter");
        }
        // TODO: support more than one layer (superimposition, difference, mask)
        if (layers.length > LAYER_LIMIT)
        {
            throw new WmsException("You may only create a map from " +
                    LAYER_LIMIT  + " layer(s) at a time");
        }
        return layers[0];
    }

    /**
     * Executes the GetFeatureInfo operation
     * @throws WmsException if the user has provided invalid parameters
     * @throws Exception if an internal error occurs
     * @todo Separate Model and View code more cleanly
     */
    protected ModelAndView getFeatureInfo(
            RequestParams params,
            LayerFactory layerFactory,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse,
            UsageLogEntry usageLogEntry)
            throws WmsException, Exception
    {
        GetFeatureInfoRequest request = new GetFeatureInfoRequest(params);
        usageLogEntry.setGetFeatureInfoRequest(request);

        GetFeatureInfoDataRequest dr = request.getDataRequest();

        // Check the output format
        if (!request.getOutputFormat().equals(FEATURE_INFO_XML_FORMAT) &&
            !request.getOutputFormat().equals(FEATURE_INFO_PNG_FORMAT)) {
            throw new InvalidFormatException("The output format " +
                    request.getOutputFormat() + " is not valid for GetFeatureInfo");
        }

        String layerName = getLayerName(dr);
        Layer layer = layerFactory.getLayer(layerName);
        usageLogEntry.setLayer(layer);

        // Get the grid onto which the data is being projected
        RegularGrid grid = WmsUtils.getImageGrid(dr);
        // Get the real-world coordinate values of the point of interest
        // Remember that the vertical axis is flipped
        int j = dr.getHeight() - dr.getPixelRow() - 1;
        HorizontalPosition pos = grid.transformCoordinates(dr.getPixelColumn(), j);
        // Transform these coordinates into lon-lat
        LonLatPosition lonLat = Utils.transformToWgs84LonLat(pos);

        // Find out the i,j coordinates of this point in the source grid (could be null)
        HorizontalGrid horizGrid = layer.getHorizontalGrid();
        GridCoordinates gridCoords = horizGrid.findNearestGridPoint(pos);
        LonLatPosition gridCellCentre = null;
        if (gridCoords != null)
        {
            // Get the location of the centre of the grid cell
            HorizontalPosition gridCellCentrePos = horizGrid.transformCoordinates(gridCoords);
            gridCellCentre = Utils.transformToWgs84LonLat(gridCellCentrePos);
        }

        // Get the elevation value requested
        double zValue = getElevationValue(dr.getElevationString(), layer);

        // Get the requested timesteps.  If the layer doesn't have
        // a time axis then this will return a single-element List with value null.
        List<DateTime> tValues = getTimeValues(dr.getTimeString(), layer);
        usageLogEntry.setNumTimeSteps(tValues.size());

        // First we read the timeseries data.  If the layer doesn't have a time
        // axis we'll use ScalarLayer.readSinglePoint() instead.
        // TODO: this code is messy: refactor.
        List<Float> tsData;
        if (layer instanceof ScalarLayer) {
            ScalarLayer scalLayer = (ScalarLayer)layer;
            if (tValues.isEmpty()) {
                // The layer has no time axis
                Float val = scalLayer.readSinglePoint(null, zValue, pos);
                tsData = Arrays.asList(val);
            } else {
                tsData = scalLayer.readTimeseries(tValues, zValue, pos);
            }
        } else if (layer instanceof VectorLayer) {
            VectorLayer vecLayer = (VectorLayer)layer;
            ScalarLayer eastComp = vecLayer.getXComponent();
            ScalarLayer northComp = vecLayer.getYComponent();
            if (tValues.isEmpty()) {
                // The layer has no time axis
                Float eastVal = eastComp.readSinglePoint(null, zValue, pos);
                Float northVal = northComp.readSinglePoint(null, zValue, pos);
                if (eastVal == null || northVal == null) {
                    tsData = Arrays.asList((Float)null);
                } else {
                    tsData = Arrays.asList((float)Math.sqrt(eastVal * eastVal + northVal * northVal));
                }
            } else {
                // The layer has a time axis
                List<Float> tsDataEast  = eastComp.readTimeseries(tValues, zValue, pos);
                List<Float> tsDataNorth = northComp.readTimeseries(tValues, zValue, pos);
                tsData = WmsUtils.getMagnitudes(tsDataEast, tsDataNorth);
            }
        } else {
            throw new IllegalStateException("Unrecognized layer type");
        }

        // Internal consistency check: arrays should be the same length
        if (!tValues.isEmpty() && tValues.size() != tsData.size()) {
            throw new IllegalStateException("Internal error: timeseries length inconsistency");
        }

        // Now we map date-times to data values
        // The map is kept in order of ascending time
        Map<DateTime, Float> featureData = new LinkedHashMap<DateTime, Float>();
        if (tValues.isEmpty()) {
            featureData.put(null, tsData.get(0));
        } else {
            for (int i = 0; i < tValues.size(); i++) {
                featureData.put(tValues.get(i), tsData.get(i));
            }
        }

        if (request.getOutputFormat().equals(FEATURE_INFO_XML_FORMAT)) {
            Map<String, Object> models = new HashMap<String, Object>();
            models.put("longitude", lonLat.getLongitude());
            models.put("latitude", lonLat.getLatitude());
            models.put("gridCoords", gridCoords);
            models.put("gridCentre", gridCellCentre);
            models.put("data", featureData);
            return new ModelAndView("showFeatureInfo_xml", models);
        } else {
            // Must be PNG format: prepare and output the JFreeChart
            JFreeChart chart = Charting.createTimeseriesPlot(layer, lonLat, featureData);
            httpServletResponse.setContentType("image/png");
            ChartUtilities.writeChartAsPNG(httpServletResponse.getOutputStream(),
                    chart, 400, 300);
            return null;
        }
    }

    /**
     * Creates and returns a PNG image with the colour scale and range for 
     * a given Layer
     */
    protected ModelAndView getLegendGraphic(RequestParams params,
            LayerFactory layerFactory, HttpServletResponse httpServletResponse)
            throws Exception {
        BufferedImage legend;

        // numColourBands defaults to ColorPalette.MAX_NUM_COLOURS if not set
        int numColourBands = GetMapStyleRequest.getNumColourBands(params);

        String paletteName = params.getString("palette");

        // Find out if we just want the colour bar with no supporting text
        String colorBarOnly = params.getString("colorbaronly", "false");
        if (colorBarOnly.equalsIgnoreCase("true")) {
            // We're only creating the colour bar so we need to know a width
            // and height
            int width = params.getPositiveInt("width", 50);
            int height = params.getPositiveInt("height", 200);
            // Find the requested colour palette, or use the default if not set
            ColorPalette palette = ColorPalette.get(paletteName);
            legend = palette.createColorBar(width, height, numColourBands);
        } else {
            // We're creating a legend with supporting text so we need to know
            // the colour scale range and the layer in question
            String layerName = params.getMandatoryString("layer");
            Layer layer = layerFactory.getLayer(layerName);

            // We default to the layer's default palette if none is specified
            ColorPalette palette = paletteName == null
                ? layer.getDefaultColorPalette()
                : ColorPalette.get(paletteName);

            // See if the client has specified a logarithmic scaling, defaulting
            // to the layer's default
            Boolean isLogScale = GetMapStyleRequest.isLogScale(params);
            boolean logarithmic = isLogScale == null ? layer.isLogScaling() : isLogScale.booleanValue();

            // Now get the colour scale range
            Range<Float> colorScaleRange = GetMapStyleRequest.getColorScaleRange(params);
            if (colorScaleRange == null) {
                // Use the layer's default range if none is specified
                colorScaleRange = layer.getApproxValueRange();
            } else if (colorScaleRange.isEmpty()) {
                throw new WmsException("Cannot automatically create a colour scale "
                    + "for a legend graphic.  Use COLORSCALERANGE=default or specify "
                    + "the scale extremes explicitly.");
            }

            boolean transparent = params.getBoolean("transparent", false);
            Color backgroundColor = params.getColor("bgcolor", Color.black);

            // Now create the legend image
            legend = palette.createLegend(numColourBands, layer.getTitle(), layer.getUnits(),
                                          logarithmic, colorScaleRange,
                                          transparent, backgroundColor);
        }
        httpServletResponse.setContentType("image/png");
        ImageIO.write(legend, "png", httpServletResponse.getOutputStream());

        return null;
    }

    /**
     * Outputs a transect (data value versus distance along a path) in PNG or
     * XML format.
     */
    protected ModelAndView getTransect(RequestParams params, LayerFactory layerFactory,
            HttpServletResponse response, UsageLogEntry usageLogEntry)
            throws Exception
    {
        // Parse the request parameters
        String layerStr = params.getMandatoryString("layer");
        Layer layer = layerFactory.getLayer(layerStr);

        String crsCode = params.getMandatoryString("crs");
        String lineString = params.getMandatoryString("linestring");
        String outputFormat = params.getMandatoryString("format");
        List<DateTime> tValues = getTimeValues(params.getString("time"), layer);
        DateTime tValue = tValues.isEmpty() ? null : tValues.get(0);
        double zValue = getElevationValue(params.getString("elevation"), layer);

        if (!outputFormat.equals(FEATURE_INFO_PNG_FORMAT) &&
            !outputFormat.equals(FEATURE_INFO_XML_FORMAT)) {
            throw new InvalidFormatException(outputFormat);
        }

        usageLogEntry.setLayer(layer);
        usageLogEntry.setOutputFormat(outputFormat);
        usageLogEntry.setWmsOperation("GetTransect");

        // Parse the line string, which is in the form "x1 y1, x2 y2, x3 y3"
        final LineString transect = new LineString(lineString, crsCode, params.getWmsVersion());
        log.debug("Got {} control points", transect.getControlPoints().size());

        // Find the optimal number of points to sample the layer's source grid
        Domain<HorizontalPosition> transectDomain = getOptimalTransectDomain(layer, transect);
        log.debug("Using transect consisting of {} points", transectDomain.getDomainObjects().size());

        // Read the data from the data source, without using the tile cache
        List<Float> transectData;
        if (layer instanceof ScalarLayer) {
            transectData = ((ScalarLayer)layer).readHorizontalPoints(tValue, zValue, transectDomain);
        } else if (layer instanceof VectorLayer) {
            VectorLayer vecLayer = (VectorLayer)layer;
            List<Float> tsDataEast  = vecLayer.getXComponent() .readHorizontalPoints(tValue, zValue, transectDomain);
            List<Float> tsDataNorth = vecLayer.getYComponent().readHorizontalPoints(tValue, zValue, transectDomain);
            transectData = WmsUtils.getMagnitudes(tsDataEast, tsDataNorth);
        } else {
            throw new IllegalStateException("Unrecognized layer type");
        }
        log.debug("Transect: Got {} dataValues", transectData.size());

        // Now output the data in the selected format
        response.setContentType(outputFormat);
        if (outputFormat.equals(FEATURE_INFO_PNG_FORMAT))
        {
            JFreeChart chart = Charting.createTransectPlot(layer, transect, transectData);
            int width = 400;
            int height = 300;

            // If we have a layer with more than one elevation value, let's also
            // create a vertical section plot underneath.
            if (layer.getElevationValues().size() > 1)
            {
                // Create the chart
                JFreeChart verticalSectionChart = createVerticalSectionChart(
                    params, layer, tValue, transect, transectDomain);

                // Create the combined chart with both the transect and the
                // vertical section
                CombinedDomainXYPlot plot = new CombinedDomainXYPlot(
                        new NumberAxis("distance along path (arbitrary units)"));
                plot.setGap(20.0);
                plot.add(chart.getXYPlot(), 1);
                plot.add(verticalSectionChart.getXYPlot(), 1);
                plot.setOrientation(PlotOrientation.VERTICAL);
                String title = WmsUtils.removeDuplicatedWhiteSpace(layer.getTitle()) + " (" + layer.getUnits() + ")" + " at " + zValue +layer.getElevationUnits();
                chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
                RectangleInsets r = new RectangleInsets(0,10,0,0);  // set left margin to 10 to avoid number wrap at color bar
                chart.setPadding(r); 
                
                // Use the legend from the vertical section chart
                chart.addSubtitle(verticalSectionChart.getSubtitle(0));

                height = 600;
            }           

            ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, width, height);
        }
        else if (outputFormat.equals(FEATURE_INFO_XML_FORMAT))
        {
            // Output data as XML using a template
            // First create an ordered map of ProjectionPoints to data values
            Map<HorizontalPosition, Float> dataPoints = new LinkedHashMap<HorizontalPosition, Float>();
            List<? extends HorizontalPosition> points = transectDomain.getDomainObjects();
            for (int i = 0; i < points.size(); i++) {
                dataPoints.put(points.get(i), transectData.get(i));
            }

            Map<String, Object> models = new HashMap<String, Object>();
            models.put("crs", crsCode);
            models.put("layer", layer);
            models.put("linestring", lineString);
            models.put("data", dataPoints);
            return new ModelAndView("showTransect_xml", models);
        }
        return null;
    }

    /**
     * Outputs a vertical profile plot in PNG format.
     */
    protected ModelAndView getVerticalProfile(RequestParams params, LayerFactory layerFactory,
            HttpServletResponse response, UsageLogEntry usageLogEntry)
            throws WmsException, IOException
    {
        // Parse the request parameters
        String layerStr = params.getMandatoryString("layer");
        Layer layer = layerFactory.getLayer(layerStr);

        String crsCode = params.getMandatoryString("crs");
        String point = params.getMandatoryString("point");
        List<DateTime> tValues = getTimeValues(params.getString("time"), layer);
        DateTime tValue = tValues.isEmpty() ? null : tValues.get(0);
        
        String outputFormat = params.getMandatoryString("format");
        if (!"image/png".equals(outputFormat) &&
            !"image/jpeg".equals(outputFormat) &&
            !"image/jpg".equals(outputFormat)) {
            throw new InvalidFormatException(outputFormat + " is not a valid output format");
        }

        usageLogEntry.setLayer(layer);
        usageLogEntry.setOutputFormat(outputFormat);
        usageLogEntry.setWmsOperation("GetVerticalProfile");

        // Get the required coordinate reference system, forcing longitude-first
        // axis order.
        final CoordinateReferenceSystem crs = WmsUtils.getCrs(crsCode);
        
        // The location of the vertical profile
        String[] coords = point.trim().split(" +"); // allows one or more spaces to be used as a delimiter
        if (coords.length != 2) {
            throw new WmsException("Invalid POINT format");
        }
        int lonIndex = 0;
        int latIndex = 1;
        // If we have lat lon order...
        if(crsCode.equalsIgnoreCase("EPSG:4326") && params.getWmsVersion().equalsIgnoreCase("1.3.0")){
            // Swap the co-ordinates to lon lat order
            latIndex = 0;
            lonIndex = 1;
        }
        
        double x, y;
        try {
            x = Double.parseDouble(coords[lonIndex]);
            y = Double.parseDouble(coords[latIndex]);
        } catch (NumberFormatException nfe) {
            throw new WmsException("Invalid POINT format");
        }
        HorizontalPosition pos = new HorizontalPositionImpl(x, y, crs);
        Domain<HorizontalPosition> domain = new HorizontalDomain(pos);
        
        // Read data from each elevation in the source grid
        // We reuse the readVerticalSection code: a profile is essentially a 
        // one-element vertical section
        List<Double> zValues = new ArrayList<Double>();
        List<Float> profileData = new ArrayList<Float>();
        if (layer instanceof ScalarLayer) {
             ScalarLayer scalarLayer = (ScalarLayer) layer;
             List<List<Float>> data = scalarLayer.readVerticalSection(tValue, layer.getElevationValues(), domain);
             // Filter out null values
             int i = 0;
             for (Double zValue : layer.getElevationValues()) {
                 Float d = data.get(i).get(0); // We know there is only one point in the list
                 if (d != null) {
                     profileData.add(d); 
                     zValues.add(zValue);
                 }
                 i++;
             }
        }
        else if (layer instanceof VectorLayer) {
             VectorLayer vecLayer = (VectorLayer)layer;
             List<List<Float>> sectionDataEast  = vecLayer.getXComponent()
                 .readVerticalSection(tValue, layer.getElevationValues(), domain);
             List<List<Float>> sectionDataNorth = vecLayer.getYComponent()
                 .readVerticalSection(tValue, layer.getElevationValues(), domain);
             // Calculate magnitudes and filter out null values
             int i = 0;
             for (Double zValue : layer.getElevationValues()) {
                 Float mag = WmsUtils.getMagnitudes(sectionDataEast.get(i), sectionDataNorth.get(i)).get(0);
                 if (mag != null) {
                     profileData.add(mag);
                     zValues.add(zValue);
                 }
                 i++;
             }
        }
        else {
            // Shouldn't happen
            throw new UnsupportedOperationException("Unsupported layer type");
        }

        // Now create the vertical profile plot
        JFreeChart chart = Charting.createVerticalProfilePlot(layer, pos, zValues, profileData, tValue);

        response.setContentType(outputFormat);
        int width = 500;
        int height = 400;
        if ("image/png".equals(outputFormat)) {
            ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, width, height);
        } else {
            // Must be a JPEG
            ChartUtilities.writeChartAsJPEG(response.getOutputStream(), chart, width, height);
        }
        
        return null;
    }
  
    private static JFreeChart createVerticalSectionChart(RequestParams params,
            Layer layer, DateTime tValue, LineString lineString,
            Domain<HorizontalPosition> transectDomain)
            throws WmsException, InvalidDimensionValueException, IOException
    {
        // Look for styling parameters in the URL
        int numColourBands = GetMapStyleRequest.getNumColourBands(params);
        Range<Float> scaleRange = GetMapStyleRequest.getColorScaleRange(params);
        if (scaleRange == null) scaleRange = layer.getApproxValueRange();
        // TODO: deal with auto scale ranges - look at actual values extracted
        Boolean logScale = GetMapStyleRequest.isLogScale(params);
        if (logScale == null) logScale = layer.isLogScaling();
        // TODO: repeats code from GetLegendGraphic
        String paletteName = params.getString("palette");
        ColorPalette palette = paletteName == null
            ? layer.getDefaultColorPalette()
            : ColorPalette.get(paletteName);

        // Read data from each elevation in the source grid
        List<Double> zValues = new ArrayList<Double>();
        List<List<Float>> sectionData = new ArrayList<List<Float>>();
        if (layer instanceof ScalarLayer) {
             ScalarLayer scalarLayer = (ScalarLayer) layer;
             List<List<Float>> data = scalarLayer.readVerticalSection(tValue, layer.getElevationValues(), transectDomain);
             // Filter out all-null levels
             int i = 0;
             for (Double zValue : layer.getElevationValues()) {
                 List<Float> d = data.get(i);
                 if (!allNull(d)) {
                     sectionData.add(d);
                     zValues.add(zValue);
                 }
                 i++;
             }
        }
        else if (layer instanceof VectorLayer) {
             VectorLayer vecLayer = (VectorLayer)layer;
             List<List<Float>> sectionDataEast  = vecLayer.getXComponent()
                 .readVerticalSection(tValue, layer.getElevationValues(), transectDomain);
             List<List<Float>> sectionDataNorth = vecLayer.getYComponent()
                 .readVerticalSection(tValue, layer.getElevationValues(), transectDomain);
             // Calculate magnitudes and filter out all-null levels
             int i = 0;
             for (Double zValue : layer.getElevationValues()) {
                 List<Float> mags = WmsUtils.getMagnitudes(sectionDataEast.get(i), sectionDataNorth.get(i));
                 if (!allNull(mags)) {
                     sectionData.add(mags);
                     zValues.add(zValue);
                 }
                 i++;
             }
        }
        else {
            // Shouldn't happen
            throw new UnsupportedOperationException("Unsupported layer type");
        }

        // If the user has specified COLORSCALERANGE=auto, we will use the actual
        // minimum and maximum values of the extracted data to generate the scale
        float max = Float.NEGATIVE_INFINITY;
        float min = Float.POSITIVE_INFINITY;
        if (scaleRange.isEmpty()) {
            for (List<Float> data : sectionData) {
                Range<Float> minMax = Ranges.findMinMax(data);
                max = Math.max(max, minMax.getMaximum());
                min = Math.min(min, minMax.getMinimum());
            }
            scaleRange = Ranges.newRange(min, max);
        }
        
        double zValue = getElevationValue(params.getString("elevation"), layer);
         
        return Charting.createVerticalSectionChart(layer, lineString,
                zValues, sectionData, scaleRange, palette, numColourBands, logScale,zValue);
    }    
    
      
    /**
     * Generate the vertical section JfreeChart object
     */
    protected ModelAndView getVerticalSection(RequestParams params, LayerFactory layerFactory,
            HttpServletResponse response, UsageLogEntry usageLogEntry)
            throws Exception {

        // Parse the request parameters
        // TODO repeats code from getTransect()
        String layerStr = params.getMandatoryString("layer");
        Layer layer = layerFactory.getLayer(layerStr);

        String crsCode = params.getMandatoryString("crs");
        String lineStr = params.getMandatoryString("linestring");
        List<DateTime> tValues = getTimeValues(params.getString("time"), layer);
        DateTime tValue = tValues.isEmpty() ? null : tValues.get(0);
        usageLogEntry.setLayer(layer);

        // Parse the parameters connected with styling
        // TODO repeats code from GetMap and GetLegendGraphic
        String outputFormat = params.getMandatoryString("format");
        if (!"image/png".equals(outputFormat) &&
            !"image/jpeg".equals(outputFormat) &&
            !"image/jpg".equals(outputFormat)) {
            throw new InvalidFormatException(outputFormat + " is not a valid output format");
        }
        usageLogEntry.setOutputFormat(outputFormat);
        usageLogEntry.setWmsOperation("GetVerticalSection");

        // Parse the line string, which is in the form "x1 y1, x2 y2, x3 y3"
        final LineString lineString = new LineString(lineStr, crsCode, params.getMandatoryWmsVersion());
        log.debug("Got {} control points", lineString.getControlPoints().size());

        // Find the optimal number of points to sample the layer's source grid
        Domain<HorizontalPosition> transectDomain = getOptimalTransectDomain(layer, lineString);
        log.debug("Using transect consisting of {} points", transectDomain.getDomainObjects().size());

        JFreeChart chart = createVerticalSectionChart(params, layer, tValue,
             lineString, transectDomain);

        response.setContentType(outputFormat);
        int width = 500;
        int height = 400;
        if ("image/png".equals(outputFormat)) {
            ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, width, height);
        } else {
            // Must be a JPEG
            ChartUtilities.writeChartAsJPEG(response.getOutputStream(), chart, width, height);
        }

        return null;
    }

    
    /**
     * Gets a HorizontalDomain that contains (near) the minimum necessary number of
     * points to sample a layer's source grid of data.  That is to say,
     * creating a HorizontalDomain at higher resolution would not result in sampling
     * significantly more points in the layer's source grid.
     * @param layer The layer for which the transect will be generated
     * @param transect The transect as specified in the request
     * @return a HorizontalDomain that contains (near) the minimum necessary number of
     * points to sample a layer's source grid of data.
     */
    private static Domain<HorizontalPosition> getOptimalTransectDomain(Layer layer,
            LineString transect) throws Exception {
        // We need to work out how many points we need to include in order to
        // completely sample the data grid (i.e. we need the resolution of the
        // points to be higher than that of the data grid).  It's hard to work
        // this out neatly (data grids can be irregular) but we can estimate
        // this by creating transects at progressively higher resolution, and
        // working out how many grid points will be sampled.
        int numTransectPoints = 500; // a bit more than the final image width
        int lastNumUniqueGridPointsSampled = -1;
        HorizontalDomain pointList = null;
        while (true) {
            // Create a transect with the required number of points, interpolating
            // between the control points in the line string
            List<HorizontalPosition> points = transect.getPointsOnPath(numTransectPoints);
            // Create a HorizontalDomain from the interpolated points
            HorizontalDomain testPointList = new HorizontalDomain(points, transect.getCoordinateReferenceSystem());

            // Work out how many grid points will be sampled by this transect
            // Relies on equals() being implemented correctly for the GridCoordinates
            Set<GridCoordinates> gridCoords = new HashSet<GridCoordinates>();
            for (GridCoordinates coords : layer.getHorizontalGrid().findNearestGridPoints(testPointList)) {
                gridCoords.add(coords);
            }
            int numUniqueGridPointsSampled = gridCoords.size();
            log.debug("With {} transect points, we'll sample {} grid points",
                    numTransectPoints, numUniqueGridPointsSampled);

            // If this increase in resolution results in at least 10% more points
            // being sampled we'll go around the loop again
            if (numUniqueGridPointsSampled > lastNumUniqueGridPointsSampled * 1.1) {
                // We need to increase the transect resolution and try again
                lastNumUniqueGridPointsSampled = numUniqueGridPointsSampled;
                numTransectPoints += 500;
                pointList = testPointList;
            } else {
                // We've gained little advantage by the last resolution increase
                return pointList;
            }
        }
    }


    /**
     * Returns true if all the values in the given list are null
     * @param data
     * @return
     */
    private static boolean allNull(List<Float> data)
    {
        for (Float val : data) {
            if (val != null) return false;
        }
        return true;
    }
    
    

    /**
     * Gets the elevation value requested by the client.
     * @param zValue the value of the ELEVATION string from the request
     * @return the elevation value requested by the client.  Returns
     * {@link Layer#getDefaultElevationValue() layer.getDefaultElevationValue()}
     * if zValue is null and the layer supports a default elevation value.
     * Returns {@link Double#NaN} if the layer does not have an elevation axis.
     * @throws InvalidDimensionValueException if the provided z value is not
     * a valid number, or if zValue is null and the layer does not support
     * a default elevation value
     */
    static double getElevationValue(String zValue, Layer layer) throws InvalidDimensionValueException
    {
        if (layer.getElevationValues().isEmpty()) return Double.NaN;
        if (zValue == null)
        {
            double defaultVal = layer.getDefaultElevationValue();
            if (Double.isNaN(defaultVal))
            {
                throw new InvalidDimensionValueException("elevation", "null");
            }
            return defaultVal;
        }

        // Check to see if this is a single value (the
        // user hasn't requested anything of the form z1,z2 or start/stop/step)
        if (zValue.contains(",") || zValue.contains("/")) {
            throw new InvalidDimensionValueException("elevation", zValue);
        }

        try {
            return Double.parseDouble(zValue);
        } catch (NumberFormatException nfe) {
            throw new InvalidDimensionValueException("elevation", zValue);
        }
    }

    /**
     * Gets the list of time values requested by the client.  If the layer does
     * not have a time axis the timeString will be ignored and an empty List will
     * be returned.
     * @param timeString the string provided for the TIME parameter, or null
     * if there was no TIME parameter in the client's request
     * @return the list of time values requested by the client or an empty List
     * if the layer does not have a time axis.
     * @throws InvalidDimensionValueException if the time string cannot be parsed,
     * or if any of the requested times are not valid times for the layer
     */
    static List<DateTime> getTimeValues(String timeString, Layer layer)
            throws InvalidDimensionValueException {

        // If the layer does not have a time axis return an empty list
        if (layer.getTimeValues().isEmpty()) return Collections.emptyList();
        
        // Use the default time if none is specified
        if (timeString == null) {
            DateTime defaultDateTime = layer.getDefaultTimeValue();
            if (defaultDateTime == null) {
                // Must specify a TIME: this layer does not support a default time value
                throw new InvalidDimensionValueException("time", timeString);
            }
            return Arrays.asList(defaultDateTime);
        }

        // Interpret the time specification
        List<DateTime> tValues = new ArrayList<DateTime>();
        for (String t : timeString.split(",")) {
            String[] startStop = t.split("/");
            if (startStop.length == 1) {
                // This is a single time value
                tValues.add(findTValue(startStop[0], layer));
            } else if (startStop.length == 2) {
                // Use all time values from start to stop inclusive
                tValues.addAll(findTValues(startStop[0], startStop[1], layer));
            } else {
                throw new InvalidDimensionValueException("time", t);
            }
        }
        return tValues;
    }
    
    /**
     * Gets the index of the DateTime corresponding with the given ISO string,
     * checking that the time is valid for the given layer.
     * @throws InvalidDimensionValueException if the layer does not contain
     * the given time, or if the given ISO8601 string is not valid.
     */
    static int findTIndex(String isoDateTime, Layer layer)
        throws InvalidDimensionValueException
    {
        DateTime target;
        if (isoDateTime.equals("current")) {
            target = layer.getCurrentTimeValue();
        } else {
            try {
                target = WmsUtils.iso8601ToDateTime(isoDateTime, layer.getChronology());
            } catch(IllegalArgumentException iae) {
                throw new InvalidDimensionValueException("time", isoDateTime);
            }
        }

        // Find the equivalent DateTime in the Layer.  Note that we can't simply
        // use the contains() method of the List, since this is based on equals().
        // We want to find the DateTime with the same millisecond instant.
        int index = WmsUtils.findTimeIndex(layer.getTimeValues(), target);
        if (index < 0)
        {
            throw new InvalidDimensionValueException("time", isoDateTime);
        }
        return index;
    }

    /**
     * Gets the DateTime corresponding with the given ISO string, checking
     * that the time is valid for the given layer.
     * @throws InvalidDimensionValueException if the layer does not contain
     * the given time, or if the given ISO8601 string is not valid.
     */
    private static DateTime findTValue(String isoDateTime, Layer layer)
        throws InvalidDimensionValueException
    {
        return layer.getTimeValues().get(findTIndex(isoDateTime, layer));
    }

    /**
     * Gets a List of integers representing indices along the time axis
     * starting from isoDateTimeStart and ending at isoDateTimeEnd, inclusive.
     * @param isoDateTimeStart ISO8601-formatted String representing the start time
     * @param isoDateTimeEnd ISO8601-formatted String representing the start time
     * @return List of Integer indices
     * @throws InvalidDimensionValueException if either of the start or end
     * values were not found in the axis, or if they are not valid ISO8601 times.
     */
    private static List<DateTime> findTValues(String isoDateTimeStart,
        String isoDateTimeEnd, Layer layer) throws InvalidDimensionValueException
    {
        int startIndex = findTIndex(isoDateTimeStart, layer);
        int endIndex = findTIndex(isoDateTimeEnd, layer);
        if (startIndex > endIndex)
        {
            throw new InvalidDimensionValueException("time",
                isoDateTimeStart + "/" + isoDateTimeEnd);
        }
        List<DateTime> layerTValues = layer.getTimeValues();
        List<DateTime> tValues = new ArrayList<DateTime>();
        for (int i = startIndex; i <= endIndex; i++)
        {
            tValues.add(layerTValues.get(i));
        }
        return tValues;
    }

    /**
     * Reads a grid of data from the given layer, used by the GetMap operation.
     * <p>This implementation simply defers to {@link
     * ScalarLayer#readPointList(org.joda.time.DateTime, double,
     * uk.ac.rdg.resc.ncwms.datareader.PointList) layer.readPointList()},
     * ignoring the usage log entry.  No data are cached.  Other implementations
     * may choose to implement in a different way, perhaps to allow for caching
     * of the data.  If implementations return cached data they must indicate
     * this by setting {@link UsageLogEntry#setUsedCache(boolean)}.</p>
     * @param layer The layer containing the data
     * @param time The time instant for which we require data.  If this does not
     * match a time instant in {@link Layer#getTimeValues()} an {@link InvalidDimensionValueException}
     * will be thrown.  (If this Layer has no time axis, this parameter will be ignored.)
     * @param elevation The elevation for which we require data (in the
     * {@link Layer#getElevationUnits() units of this Layer's elevation axis}).  If
     * this does not match a valid {@link Layer#getElevationValues() elevation value}
     * in this Layer, this method will throw an {@link InvalidDimensionValueException}.
     * (If this Layer has no elevation axis, this parameter will be ignored.)
     * @param imageGrid The grid of points, one point per pixel in the image that will
     * be created in the GetMap operation
     * @param usageLogEntry
     * @return a List of data values, one for each point in
     * the {@code grid}, in the same order.
     * @throws InvalidDimensionValueException if {@code dateTime} or {@code elevation}
     * do not represent valid values along the time and elevation axes.
     * @throws IOException if there was an error reading from the data source
     */
    protected List<Float> readDataGrid(ScalarLayer layer, DateTime dateTime,
        double elevation, RegularGrid imageGrid, UsageLogEntry usageLogEntry, boolean smoothed)
        throws InvalidDimensionValueException, IOException
    {
        if(!smoothed) {
            return layer.readHorizontalPoints(dateTime, elevation, imageGrid);
        } else {
            int width = imageGrid.getXAxis().getSize();
            int height = imageGrid.getYAxis().getSize();
            
            RectilinearGrid dataGrid;
            if(!(layer.getHorizontalGrid() instanceof RectilinearGrid)) {
                return layer.readHorizontalPoints(dateTime, elevation, imageGrid);
            } else {
                dataGrid = (RectilinearGrid) layer.getHorizontalGrid();
            }
            PixelMap pixelMap = new PixelMap(dataGrid, imageGrid);
            /*
             * Check whether it is worth smoothing this data
             */
            int imageSize = width * height;
            if(pixelMap.getNumUniqueIJPairs() >= imageSize || pixelMap.getNumUniqueIJPairs() == 0) {
                /*
                 * We don't need to smooth the data
                 */
                return layer.readHorizontalPoints(dateTime, elevation, imageGrid);
            }
            
            ReferenceableAxis xAxis = dataGrid.getXAxis();
            ReferenceableAxis yAxis = dataGrid.getYAxis();
            SortedSet<Double> xCoords = new TreeSet<Double>();
            SortedSet<Double> yCoords = new TreeSet<Double>();
            double minX = imageGrid.getXAxis().getCoordinateValue(0);
           
            /*
             * Loop through all points on the data grid which are needed, and add
             * them to sorted sets
             */
            Iterator<PixelMapEntry> iterator = pixelMap.iterator();
            while(iterator.hasNext()) {
                PixelMapEntry pme = iterator.next();
                xCoords.add(Utils.getNextEquivalentLongitude(minX,
                        xAxis.getCoordinateValue(pme.getSourceGridIIndex())));
                yCoords.add(yAxis.getCoordinateValue(pme.getSourceGridJIndex()));
            }
            Float[][] data = new Float[xCoords.size()][yCoords.size()];
            final CoordinateReferenceSystem crs = layer.getHorizontalGrid().getCoordinateReferenceSystem();
            final List<HorizontalPosition> pointsToRead = new ArrayList<HorizontalPosition>();
            
            /*
             * Loop through required coords and add to a list to read
             */
            for(Double x : xCoords) {
                for(Double y : yCoords) {
                    pointsToRead.add(new HorizontalPositionImpl(x, y, crs));
                }               
            }
            /*
             * Use the list to read all required points at once
             */
            List<Float> points;
            try {
                points = layer.readHorizontalPoints(dateTime, elevation, new Domain<HorizontalPosition>() {
                    @Override
                    public CoordinateReferenceSystem getCoordinateReferenceSystem() {
                        return crs;
                    }
                    @Override
                    public List<HorizontalPosition> getDomainObjects() {
                        return pointsToRead;
                    }
                    @Override
                    public long size() {
                        return pointsToRead.size();
                    }
                });
            } catch (InvalidDimensionValueException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Problem reading data");
            } catch (IOException e) {
                e.printStackTrace();
                throw new IllegalArgumentException("Problem reading data");
            }
            /*
             * Now populate the data array
             */
            int index = 0;
            for (int i = 0; i < xCoords.size(); i++) {
                for (int j = 0; j < yCoords.size(); j++) {
                    data[i][j] = points.get(index++);
                }
            }
           
            /*
             * All data reading is done at this point. We now interpolate onto each
             * image point, using a BilinearInterpolator
             */
            BilinearInterpolator interpolator = new BilinearInterpolator(xCoords, yCoords, data);
               
            List<Float> retData = new ArrayList<Float>();
           
            for (int j = 0; j < height; j++) {
                double y = imageGrid.getYAxis().getCoordinateValue(j);
                for (int i = 0; i < width; i++) {
                    double x = imageGrid.getXAxis().getCoordinateValue(i);
                    retData.add(interpolator.getValue(x, y));
                }
            }
            return retData;
        }
    }

    /**
     * Called by Spring to shutdown the controller.  This implementation does
     * nothing: subclasses should override if necessary to free resources.
     */
    public void shutdown() {}

    /**
     * Called by the Spring framework to inject the object that represents the
     * server's configuration.
     */
    public void setServerConfig(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
    }

    /**
     * Called by Spring to inject the usage logger
     */
    public void setUsageLogger(UsageLogger usageLogger) {
        this.usageLogger = usageLogger;
    }
}
