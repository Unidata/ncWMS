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

package uk.ac.rdg.resc.ncwms.wms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotoolkit.referencing.CRS;
import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.geometry.DirectPosition;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

import uk.ac.rdg.resc.edal.cdm.AbstractCurvilinearGrid;
import uk.ac.rdg.resc.edal.coverage.grid.GridCoordinates;
import uk.ac.rdg.resc.edal.coverage.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.coverage.grid.RegularGrid;
import uk.ac.rdg.resc.edal.geometry.HorizontalPosition;
import uk.ac.rdg.resc.edal.geometry.impl.HorizontalPositionImpl;
import uk.ac.rdg.resc.edal.util.Range;
import uk.ac.rdg.resc.edal.util.Ranges;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;
import uk.ac.rdg.resc.ncwms.graphics.ColorPalette;
import uk.ac.rdg.resc.ncwms.util.WmsUtils;

/**
 * Implementation of a {@link VectorLayer} that wraps two Layer objects, one for
 * the eastward and one for the northward component. Most of the properties are
 * derived directly from the eastward component. The components must share the
 * same domain, although this class does not verify this.
 * 
 * @author Jon
 * @author Jon
 */
public class SimpleVectorLayer implements VectorLayer {

    private final String id;
    private final ScalarLayer xLayer;
    private final ScalarLayer yLayer;

    public SimpleVectorLayer(String id, ScalarLayer xLayer, ScalarLayer yLayer) {
        this.id = id;
        this.xLayer = xLayer;
        this.yLayer = yLayer;
    }

    @Override
    public ScalarLayer getXComponent() {
        return this.xLayer;
    }

    @Override
    public ScalarLayer getYComponent() {
        return this.yLayer;
    }

    @Override
    public List<Float>[] readXYComponents(DateTime dateTime, double elevation, RegularGrid destGrid)
            throws InvalidDimensionValueException, IOException, FactoryException,
            TransformException {
        @SuppressWarnings("unchecked")
        List<Float>[] xyComps = new List[2];

        List<Float> xSourceData = getXComponent().readHorizontalPoints(dateTime, elevation, destGrid);
        List<Float> ySourceData = getYComponent().readHorizontalPoints(dateTime, elevation, destGrid);

        /*
         * We have north and east data. We want x and y (in image CRS) data.
         */
        HorizontalGrid sourceGrid = xLayer.getHorizontalGrid();

        /*
         * Check if we have a curvilinear grid. If so, there's no mathematical
         * transformation, so we need to find derivatives by using adjacent grid
         * points which is less accurate.
         */
        if (!(sourceGrid instanceof AbstractCurvilinearGrid)) {
            /*
             * We use this to find the derivatives at all destination grid
             * points (if needed)
             */
            MathTransform trans = CRS.findMathTransform(destGrid.getCoordinateReferenceSystem(),
                    sourceGrid.getCoordinateReferenceSystem(), true);
            /*
             * Check if we need to transform the vectors
             */
            if (!trans.isIdentity()) {
                /*
                 * The 2 CRSs are equivalent, so we can just use the components directly
                 */
                xyComps[0] = xSourceData;
                xyComps[1] = ySourceData;
            } else {
                /*
                 * These will hold the x- and y-components of the data
                 */
                List<Float> xData = new ArrayList<Float>(xSourceData.size());
                List<Float> yData = new ArrayList<Float>(ySourceData.size());

                List<HorizontalPosition> domainObjects = destGrid.getDomainObjects();
                /*
                 * Use the bulk transform method to transform 3 positions for
                 * each grid point ([x,y], [x+dx,y], [x,y+dy])
                 */
                double[] gridPositions = new double[domainObjects.size() * 6];
                double dxy = 1e-6;
                for (int i = 0; i < domainObjects.size(); i++) {
                    int startIndex = 6 * i;
                    double x = domainObjects.get(i).getX();
                    double y = domainObjects.get(i).getY();
                    gridPositions[startIndex] = x;
                    gridPositions[startIndex + 1] = y;

                    gridPositions[startIndex + 2] = x + dxy;
                    gridPositions[startIndex + 3] = y;

                    gridPositions[startIndex + 4] = x;
                    gridPositions[startIndex + 5] = y + dxy;
                }
                trans.transform(gridPositions, 0, gridPositions, 0, 3 * domainObjects.size());
                /*
                 * Now we can use these transformed positions to calculate the
                 * required partial derivatives at all destination grid points
                 */
                for (int i = 0; i < domainObjects.size(); i++) {
                    /*
                     * First do a null check - we don't need to reproject if
                     * there's no data there
                     */
                    Float xSourceVal = xSourceData.get(i);
                    Float ySourceVal = ySourceData.get(i);
                    if (xSourceVal == null || ySourceVal == null) {
                        xData.add(null);
                        yData.add(null);
                    } else {
                        int startIndex = 6 * i;

                        /*
                         * The partial derivatives.
                         * 
                         * dXsdXd is dXs / dXd, where Xs is the source grid x-position,
                         * and Xd is the destination grid x-position etc.
                         * 
                         * These would normally be /ed by dxy, but we're going
                         * to normalise next, so there's no point
                         */
                        double dXsdXd = (gridPositions[startIndex + 2] - gridPositions[startIndex]);
                        double dYsdXd = (gridPositions[startIndex + 3] - gridPositions[startIndex + 1]);

                        double dXsdYd = (gridPositions[startIndex + 4] - gridPositions[startIndex]);
                        double dYsdYd = (gridPositions[startIndex + 5] - gridPositions[startIndex + 1]);

                        /*
                         * Normalise so that the magnitudes will be correct
                         */
                        double dXT = Math.sqrt(dXsdXd * dXsdXd + dXsdYd * dXsdYd);
                        double dYT = Math.sqrt(dYsdXd * dYsdXd + dYsdYd * dYsdYd);
                        dXsdXd /= dXT;
                        dXsdYd /= dXT;
                        dYsdXd /= dYT;
                        dYsdYd /= dYT;

                        /*
                         * Get the new components and add them to the new components lists
                         */
                        xData.add((float) ((dXsdXd * xSourceVal + dYsdXd * ySourceVal)));
                        yData.add((float) ((dXsdYd * xSourceVal + dYsdYd * ySourceVal)));
                    }
                }
                xyComps[0] = xData;
                xyComps[1] = yData;
            }
            return xyComps;
        } else {
            /*
             * We have a curvilinear grid. There is no analytical way of
             * calculating derivatives, so we use adjacent grid points. This is
             * not very accurate, but it is likely to be sufficient for plotting
             * vector arrows
             */
            
            /*
             * These will hold the x- and y-components of the data
             */
            List<Float> xData = new ArrayList<Float>(xSourceData.size());
            List<Float> yData = new ArrayList<Float>(xSourceData.size());

            List<HorizontalPosition> destDomainObjects = destGrid.getDomainObjects();
            /*
             * Calculate all the required partial derivatives
             */
            for (int i = 0; i < destDomainObjects.size(); i++) {
                /*
                 * First do a null check - we don't need to reproject if
                 * there's no data there
                 */
                Float xSourceVal = xSourceData.get(i);
                Float ySourceVal = ySourceData.get(i);
                if (xSourceVal == null || ySourceVal == null) {
                    xData.add(null);
                    yData.add(null);
                } else {
                    GridCoordinates nearestGridPoint = sourceGrid
                            .findNearestGridPoint(destDomainObjects.get(i));
                    GridEnvelope gridExtent = sourceGrid.getGridExtent();
                    int gridX = nearestGridPoint.getCoordinateValue(0);
                    int gridY = nearestGridPoint.getCoordinateValue(1);
                    
                    HorizontalPosition plusXPos;
                    HorizontalPosition plusYPos;
                    HorizontalPosition centrePos = sourceGrid.transformCoordinates(gridX, gridY);
                    
                    /*
                     * Calculate the positions directly, or extend the grid if we're at the edge
                     */
                    if (gridX + 1 < gridExtent.getHigh(0)) {
                        plusXPos = sourceGrid.transformCoordinates(gridX + 1, gridY);
                    } else {
                        plusXPos = sourceGrid.transformCoordinates(gridX - 1, gridY);
                        plusXPos = new HorizontalPositionImpl(2 * centrePos.getX()
                                - plusXPos.getX(), 2 * centrePos.getY() - plusXPos.getY(),
                                plusXPos.getCoordinateReferenceSystem());
                    }
                    
                    if (gridY + 1 < gridExtent.getHigh(1)) {
                        plusYPos = sourceGrid.transformCoordinates(gridX, gridY + 1);
                    } else {
                        plusYPos = sourceGrid.transformCoordinates(gridX, gridY - 1);
                        plusYPos = new HorizontalPositionImpl(2 * centrePos.getX()
                                - plusYPos.getX(), 2 * centrePos.getY() - plusYPos.getY(),
                                plusYPos.getCoordinateReferenceSystem());
                    }
                    
                    /*
                     * Calculate the partial derivatives.
                     * 
                     * Curvilinear grids will always return positions in lat-lon
                     * from transformCoordinates. If the destination grid is
                     * non-lat-lon, this is not what we want.
                     */
                    MathTransform trans = CRS.findMathTransform(DefaultGeographicCRS.WGS84, destGrid.getCoordinateReferenceSystem(), true);
                    double dXddXs;
                    double dYddXs;
                    double dXddYs;
                    double dYddYs;
                    if(!trans.isIdentity()) {
                        DirectPosition centre = trans.transform(centrePos.getDirectPosition(), null);
                        DirectPosition plusX = trans.transform(plusXPos.getDirectPosition(), null);
                        DirectPosition plusY = trans.transform(plusYPos.getDirectPosition(), null);
                        dXddXs = (plusX.getOrdinate(0) - centre.getOrdinate(0));
                        dYddXs = (plusX.getOrdinate(1) - centre.getOrdinate(1));
                        
                        dXddYs = (plusY.getOrdinate(0) - centre.getOrdinate(0));
                        dYddYs = (plusY.getOrdinate(1) - centre.getOrdinate(1));
                    } else {
                        dXddXs = (plusXPos.getX() - centrePos.getX());
                        dYddXs = (plusXPos.getY() - centrePos.getY());
                        
                        dXddYs = (plusYPos.getX() - centrePos.getX());
                        dYddYs = (plusYPos.getY() - centrePos.getY());
                    }
                    
                    /*
                     * Normalise so that the magnitudes will be correct
                     */
                    double dXT = Math.sqrt(dXddXs * dXddXs + dYddXs * dYddXs);
                    double dYT = Math.sqrt(dXddYs * dXddYs + dYddYs * dYddYs);
                    
                    dXddXs /= dXT;
                    dYddXs /= dXT;
                    
                    dXddYs /= dYT;
                    dYddYs /= dYT;

                    /*
                     * Get the new components
                     */
                    float xVal = (float) (dXddXs * xSourceVal + dXddYs * ySourceVal);
                    float yVal = (float) (dYddXs * xSourceVal + dYddYs * ySourceVal);

                    xData.add(xVal);
                    yData.add(yVal);
                }
            }
            xyComps[0] = xData;
            xyComps[1] = yData;
            return xyComps;
        }
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getLayerAbstract() {
        return "Automatically-generated vector field, composed of the fields "
                + this.xLayer.getTitle() + " and " + this.yLayer.getTitle();
    }

    /**
     * Returns a layer name that is unique on this server, created from the
     * {@link #getDataset() dataset} id and the {@link #getId() layer id} by the
     * {@link WmsUtils#createUniqueLayerName(java.lang.String, java.lang.String)}
     * method.
     */
    @Override
    public String getName() {
        return WmsUtils.createUniqueLayerName(this.getDataset().getId(), this.getId());
    }

    /** Returns {@link #getId()} */
    @Override
    public String getTitle() {
        return this.id;
    }

    @Override
    public Dataset getDataset() {
        return this.xLayer.getDataset();
    }

    @Override
    public String getUnits() {
        return this.xLayer.getUnits();
    }

    @Override
    public boolean isQueryable() { return this.xLayer.isQueryable(); }    
    
    @Override
    public boolean isIntervalTime() { return this.xLayer.isIntervalTime(); }

    @Override
    public GeographicBoundingBox getGeographicBoundingBox() {
        return this.xLayer.getGeographicBoundingBox();
    }

    @Override
    public HorizontalGrid getHorizontalGrid() {
        return this.xLayer.getHorizontalGrid();
    }

    @Override
    public Chronology getChronology() {
        return this.xLayer.getChronology();
    }

    @Override
    public List<DateTime> getTimeValues() {
        return this.xLayer.getTimeValues();
    }

    @Override
    public DateTime getCurrentTimeValue() {
        return this.xLayer.getCurrentTimeValue();
    }

    @Override
    public DateTime getDefaultTimeValue() {
        return this.xLayer.getDefaultTimeValue();
    }

    @Override
    public List<Double> getElevationValues() {
        return this.xLayer.getElevationValues();
    }

    @Override
    public double getDefaultElevationValue() {
        return this.xLayer.getDefaultElevationValue();
    }

    @Override
    public String getElevationUnits() {
        return this.xLayer.getElevationUnits();
    }

    @Override
    public boolean isElevationPositive() {
        return this.xLayer.isElevationPositive();
    }

    @Override
    public boolean isElevationPressure() {
        return this.xLayer.isElevationPressure();
    }

    @Override
    public ColorPalette getDefaultColorPalette() {
        return ColorPalette.get(null);
    }

    @Override
    public boolean isLogScaling() {
        return this.xLayer.isLogScaling();
    }

    @Override
    public int getDefaultNumColorBands() {
        return this.xLayer.getDefaultNumColorBands();
    }

    @Override
    public Range<Float> getApproxValueRange() {
        try {
            return WmsUtils.estimateValueRange(this);
        } catch (IOException ioe) {
            // There was an error reading from the source data.
            // Just return a guess at a range
            return Ranges.newRange(-50.0f, 50.0f);
        }
    }

}
