/*
 * Copyright (c) 2011 The University of Reading
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
package uk.ac.rdg.resc.edal.cdm;

import java.io.IOException;
import java.util.List;
import org.geotoolkit.metadata.iso.extent.DefaultGeographicBoundingBox;
import ucar.ma2.Array;
import ucar.ma2.ArrayDouble;
import ucar.ma2.ArrayFloat;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.dataset.NetcdfDataset;
import uk.ac.rdg.resc.edal.coverage.grid.RegularAxis;
import uk.ac.rdg.resc.edal.coverage.grid.RegularGrid;
import uk.ac.rdg.resc.edal.coverage.grid.impl.RegularGridImpl;

/**
 * Reads a NetCDF file using the ncWMS libs and writes out a new file
 * @author Jon
 */
public final class WriteNetcdf {
    
    public static void main(String[] args) throws IOException {
        // NEMO 1/4 degree global model
        String filename = "C:\\Godiva2_data\\ORCA025-R07-MEAN\\Exp4-Annual\\ORCA025-R07_y2004_ANNUAL_gridT2.nc";
        String varname = "sossheig_sqd";
        // We'll resample the data onto a regular lat-long grid of the same size
        // as the original grid
        int width = 1442;
        int height = 1021;
        RegularGrid regGrid = new RegularGridImpl(DefaultGeographicBoundingBox.WORLD, width, height);
        // Open the file using the Unidata CDM
        NetcdfDataset ncin = null;
        NetcdfFileWriteable ncout = null;
        float fillValue = -999.0f;
        try {
            ncin = NetcdfDataset.openDataset(filename);
            
            List<Float> data = CdmUtils.readHorizontalPoints(ncin, varname, 0, 0, regGrid);
            
            ncout = NetcdfFileWriteable.createNew("C:\\Users\\Jon\\Desktop\\reg_nemo.nc", false);
            
            // Create dimensions
            Dimension lonDim = ncout.addDimension("lon", width);
            Dimension latDim = ncout.addDimension("lat", height);
            
            // Create coordinate variables
            ncout.addVariable("lon", DataType.DOUBLE, new Dimension[]{lonDim});
            ncout.addVariable("lat", DataType.DOUBLE, new Dimension[]{latDim});
            // Create data variable
            ncout.addVariable(varname, DataType.FLOAT, new Dimension[]{latDim, lonDim});
            
            // Add attributes
            ncout.addVariableAttribute("lon", "units", "degrees_east");
            ncout.addVariableAttribute("lat", "units", "degrees_north");
            ncout.addVariableAttribute(varname, "_FillValue", fillValue);
            
            // Create the file
            ncout.create();
            
            // Now add data values
            Array lonArr = new ArrayDouble(new int[]{width});
            RegularAxis xAxis = regGrid.getXAxis();
            for (int i = 0; i < width; i++) {
                lonArr.setDouble(i, xAxis.getCoordinateValue(i));
            }
            
            Array latArr = new ArrayDouble(new int[]{height});
            RegularAxis yAxis = regGrid.getYAxis();
            for (int i = 0; i < height; i++) {
                latArr.setDouble(i, yAxis.getCoordinateValue(i));
            }
            
            Array dataArr = new ArrayFloat(new int[]{height, width});
            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    int index = j * width + i;
                    Float f = data.get(index);
                    float val = f == null ? fillValue : f.floatValue();
                    dataArr.setFloat(index, val);
                }
            }
            
            ncout.write("lon", lonArr);
            ncout.write("lat", latArr);
            ncout.write(varname, dataArr);
            
        } catch (InvalidRangeException ire) {
            ire.printStackTrace();
        } finally {
            if (ncin != null) ncin.close();
            if (ncout != null) ncout.close();
        }
    }
    
}
