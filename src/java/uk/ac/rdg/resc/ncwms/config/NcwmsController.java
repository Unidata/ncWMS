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
package uk.ac.rdg.resc.ncwms.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.joda.time.DateTime;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.web.servlet.ModelAndView;

import uk.ac.rdg.resc.edal.cdm.PixelMap;
import uk.ac.rdg.resc.edal.cdm.PixelMap.PixelMapEntry;
import uk.ac.rdg.resc.edal.coverage.domain.Domain;
import uk.ac.rdg.resc.edal.coverage.grid.RectilinearGrid;
import uk.ac.rdg.resc.edal.coverage.grid.ReferenceableAxis;
import uk.ac.rdg.resc.edal.coverage.grid.RegularGrid;
import uk.ac.rdg.resc.edal.geometry.HorizontalPosition;
import uk.ac.rdg.resc.edal.geometry.impl.HorizontalPositionImpl;
import uk.ac.rdg.resc.edal.util.Utils;
import uk.ac.rdg.resc.ncwms.cache.TileCache;
import uk.ac.rdg.resc.ncwms.cache.TileCacheKey;
import uk.ac.rdg.resc.ncwms.controller.AbstractWmsController;
import uk.ac.rdg.resc.ncwms.controller.RequestParams;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.exceptions.LayerNotDefinedException;
import uk.ac.rdg.resc.ncwms.exceptions.OperationNotSupportedException;
import uk.ac.rdg.resc.ncwms.exceptions.WmsException;
import uk.ac.rdg.resc.ncwms.graphics.BilinearInterpolator;
import uk.ac.rdg.resc.ncwms.usagelog.UsageLogEntry;
import uk.ac.rdg.resc.ncwms.wms.Dataset;
import uk.ac.rdg.resc.ncwms.wms.Layer;
import uk.ac.rdg.resc.ncwms.wms.ScalarLayer;

/**
 * <p>WmsController for ncWMS</p>
 *
 * @author Jon Blower
 */
public final class NcwmsController extends AbstractWmsController
{
    // This object handles requests for non-standard metadata
    private NcwmsMetadataController metadataController;

    // Cache of recently-extracted data arrays: will be set by Spring
    private TileCache tileCache;

    // Object that extracts layers from the config object, given a layer name
    private final LayerFactory LAYER_FACTORY = new LayerFactory()
    {
        @Override
        public Layer getLayer(String layerName) throws LayerNotDefinedException
        {
            // Split the layer name on the slash character
            int slashIndex = layerName.lastIndexOf("/");
            if (slashIndex > 0)
            {
                String datasetId = layerName.substring(0, slashIndex);
                Dataset ds = NcwmsController.this.getConfig().getDatasetById(datasetId);
                if (ds == null) throw new LayerNotDefinedException(layerName);

                String layerId = layerName.substring(slashIndex + 1);
                Layer layer = ds.getLayerById(layerId);
                if (layer == null) throw new LayerNotDefinedException(layerName);

                return layer;
            }
            else
            {
                // We don't bother looking for the position in the string where the
                // parse error occurs
                throw new LayerNotDefinedException(layerName);
            }
        }
    };

    /**
     * Called automatically by Spring after all the dependencies have been
     * injected.
     */
    @Override
    public void init() throws Exception
    {
        // Create a NcwmsMetadataController for handling non-standard metadata request
        this.metadataController = new NcwmsMetadataController(this.getConfig(), LAYER_FACTORY);
        super.init();
    }

    @Override
    protected ModelAndView dispatchWmsRequest(
            String request,
            RequestParams params,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse,
            UsageLogEntry usageLogEntry) throws Exception
    {
        if (request.equals("GetCapabilities"))
        {
            return this.getCapabilities(params, httpServletRequest, usageLogEntry);
        }
        else if (request.equals("GetMap"))
        {
            return getMap(params, LAYER_FACTORY, httpServletResponse, usageLogEntry);
        }
        else if (request.equals("GetFeatureInfo"))
        {
            // Look to see if we're requesting data from a remote server
            String url = params.getString("url");
            if (url != null && !url.trim().equals(""))
            {
                usageLogEntry.setRemoteServerUrl(url);
                NcwmsMetadataController.proxyRequest(url, httpServletRequest, httpServletResponse);
                return null;
            }
            return getFeatureInfo(params, LAYER_FACTORY, httpServletRequest,
                    httpServletResponse, usageLogEntry);
        }
        // The REQUESTs below are non-standard and could be refactored into
        // a different servlet endpoint
        else if (request.equals("GetMetadata"))
        {
            // This is a request for non-standard metadata.  (This will one
            // day be replaced by queries to Capabilities fragments, if possible.)
            // Delegate to the NcwmsMetadataController
            return this.metadataController.handleRequest(httpServletRequest,
                    httpServletResponse, usageLogEntry);
        }
        else if (request.equals("GetLegendGraphic"))
        {
            // This is a request for an image that contains the colour scale
            // and range for a given layer
            return getLegendGraphic(params, LAYER_FACTORY, httpServletResponse);
        /*} else if (request.equals("GetKML")) {
            // This is a request for a KML document that allows the selected
            // layer(s) to be displayed in Google Earth in a manner that
            // supports region-based overlays.  Note that this is distinct
            // from simply setting "KMZ" as the output format of a GetMap
            // request: GetKML will give generally better results, but relies
            // on callbacks to this server.  Requesting KMZ files from GetMap
            // returns a standalone KMZ file.
            return getKML(params, httpServletRequest);
        } else if (request.equals("GetKMLRegion")) {
            // This is a request for a particular sub-region from Google Earth.
            logUsage = false; // We don't log usage for this operation
            return getKMLRegion(params, httpServletRequest); */
        }
        else if (request.equals("GetTransect"))
        {
            return getTransect(params, LAYER_FACTORY, httpServletResponse, usageLogEntry);
        }
        else if (request.equals("GetVerticalProfile"))
        {
            return getVerticalProfile(params, LAYER_FACTORY, httpServletResponse, usageLogEntry);
        }
        else if (request.equals("GetVerticalSection"))
        {
            return getVerticalSection(params, LAYER_FACTORY, httpServletResponse, usageLogEntry);
        }
        else
        {
            throw new OperationNotSupportedException(request);
        }
    }

    /**
     * Performs the GetCapabilities operation.
     */
    private ModelAndView getCapabilities(RequestParams params,
            HttpServletRequest httpServletRequest, UsageLogEntry usageLogEntry)
            throws WmsException, IOException
    {
        DateTime lastUpdate;
        Collection<? extends Dataset> datasets;

        // The DATASET parameter is an optional parameter that allows a
        // Capabilities document to be generated for a single dataset only
        String datasetId = params.getString("dataset");

        if (datasetId == null || datasetId.trim().equals(""))
        {
            // No specific dataset has been chosen so we create a Capabilities
            // document including every dataset.
            // First we check to see that the system admin has allowed us to
            // create a global Capabilities doc (this can be VERY large)
            Map<String, ? extends Dataset> allDatasets = this.getConfig().getAllDatasets();
            if (this.getConfig().getAllowsGlobalCapabilities())
            {
                datasets = allDatasets.values();
            }
            else
            {
                throw new WmsException("Cannot create a Capabilities document "
                    + "that includes all datasets on this server. "
                    + "You must specify a dataset identifier with &amp;DATASET=");
            }
            // The last update time for the Capabilities doc is the last time
            // any of the datasets were updated
            lastUpdate = this.getConfig().getLastUpdateTime();
        }
        else
        {
            // Look for this dataset
            Dataset ds = this.getConfig().getDatasetById(datasetId);
            if (ds == null)
            {
                throw new WmsException("There is no dataset with ID " + datasetId);
            }
            else if (!ds.isReady())
            {
                throw new WmsException("The dataset with ID " + datasetId +
                    " is not ready for use");
            }
            datasets = Arrays.asList(ds);
            // The last update time for the Capabilities doc is the last time
            // this particular dataset was updated
            lastUpdate = ds.getLastUpdateTime();
        }

        return this.getCapabilities(datasets, lastUpdate, params,
                httpServletRequest, usageLogEntry);
    }

    /**
     * <p>This implementation uses a {@link TileCache} to store data arrays,
     * speeding up repeat requests.</p>
     */
    @Override
    protected List<Float> readDataGrid(ScalarLayer layer, DateTime dateTime,
        double elevation, RegularGrid grid, UsageLogEntry usageLogEntry, boolean smoothed)
        throws InvalidDimensionValueException, IOException
    {
        // We know that this Config object only returns LayerImpl objects
        LayerImpl layerImpl = (LayerImpl)layer;
        // Find which file contains this time, and which index it is within the file
        LayerImpl.FilenameAndTimeIndex fti = layerImpl.findAndCheckFilenameAndTimeIndex(dateTime);
        // Find the z index within the file
        int zIndex = layerImpl.findAndCheckElevationIndex(elevation);

        // Create a key for searching the cache
        TileCacheKey key = new TileCacheKey(
            fti.filename,
            layer,
            grid,
            fti.tIndexInFile,
            zIndex
        );

        List<Float> data = null;
        // Search the cache.  Returns null if key is not found
        boolean cacheEnabled = this.getConfig().getCache().isEnabled();
        if (cacheEnabled) data = this.tileCache.get(key);

        // Record whether or not we got a hit in the cache
        usageLogEntry.setUsedCache(data != null);

        if (data == null)
        {
            // We didn't get any data from the cache, so we have to read from
            // the source data.
            // We call layerImpl.readHorizDomain() directly to save repeating
            // the call to findAndCheckFilenameAndTimeIndex().
            if(smoothed) {
                data = readSmoothedDataGrid(layerImpl, dateTime, elevation, grid, usageLogEntry);
            } else {
                data = layerImpl.readHorizontalDomain(fti, zIndex, grid);
            }
            // Put the data in the tile cache
            if (cacheEnabled) this.tileCache.put(key, data);
        }

        return data;
    }
    
    private List<Float> readSmoothedDataGrid(ScalarLayer layer, DateTime dateTime,
            double elevation, RegularGrid imageGrid, UsageLogEntry usageLogEntry)
            throws InvalidDimensionValueException, IOException {
        int width = imageGrid.getXAxis().getSize();
        int height = imageGrid.getYAxis().getSize();

        RectilinearGrid dataGrid;
        if (!(layer.getHorizontalGrid() instanceof RectilinearGrid)) {
            return layer.readHorizontalPoints(dateTime, elevation, imageGrid);
        } else {
            dataGrid = (RectilinearGrid) layer.getHorizontalGrid();
        }
        PixelMap pixelMap = new PixelMap(dataGrid, imageGrid);
        /*
         * Check whether it is worth smoothing this data
         */
        int imageSize = width * height;
        if (pixelMap.getNumUniqueIJPairs() >= imageSize || pixelMap.getNumUniqueIJPairs() == 0) {
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
        while (iterator.hasNext()) {
            PixelMapEntry pme = iterator.next();
            xCoords.add(Utils.getNextEquivalentLongitude(minX,
                    xAxis.getCoordinateValue(pme.getSourceGridIIndex())));
            yCoords.add(yAxis.getCoordinateValue(pme.getSourceGridJIndex()));
        }
        Float[][] data = new Float[xCoords.size()][yCoords.size()];
        final CoordinateReferenceSystem crs = layer.getHorizontalGrid()
                .getCoordinateReferenceSystem();
        final List<HorizontalPosition> pointsToRead = new ArrayList<HorizontalPosition>();

        /*
         * Loop through required coords and add to a list to read
         */
        for (Double x : xCoords) {
            for (Double y : yCoords) {
                pointsToRead.add(new HorizontalPositionImpl(x, y, crs));
            }
        }
        /*
         * Use the list to read all required points at once
         */
        List<Float> points;
        try {
            points = layer.readHorizontalPoints(dateTime, elevation,
                    new Domain<HorizontalPosition>() {
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
//                        retData[i][height - 1 - j] = interpolator.getValue(x,y);
            }
        }
        return retData;
    }

    /**
     * Called by Spring to shut down the controller.  This shuts down the tile
     * cache.
     */
    @Override
    public void shutdown()
    {
        this.tileCache.shutdown();
    }

    /** Returns the server configuration cast down to a {@link Config} object */
    private Config getConfig()
    {
        return (Config)this.serverConfig;
    }

    /** Called by Spring to set the tile cache */
    public void setTileCache(TileCache tileCache)
    {
        this.tileCache = tileCache;
    }
}
