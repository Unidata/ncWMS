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

package uk.ac.rdg.resc.ncwms.wms;

import java.io.IOException;
import java.util.List;

import org.joda.time.DateTime;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

import uk.ac.rdg.resc.edal.coverage.grid.RegularGrid;
import uk.ac.rdg.resc.ncwms.exceptions.InvalidDimensionValueException;

/**
 * A displayable Layer that is made up of two vector components (e.g. northward
 * and eastward velocities).
 * 
 * @author Jon
 */
public interface VectorLayer extends Layer {
    /** Returns the ScalarLayer representing the x-component */
    public ScalarLayer getXComponent();

    /** Returns the ScalarLayer representing the y-component */
    public ScalarLayer getYComponent();

    /**
     * Reads the X and Y components of the data. X and Y refer to the X/Y
     * directions on the specified grid.
     * 
     * @param dateTime
     *            The {@link DateTime} to read from
     * @param elevation
     *            The elevation to read from
     * @param grid
     *            The grid onto which the data should be read, and which the X/Y
     *            directions refer to.
     * @return A 2-element array containing the X/Y components of the data
     * @throws InvalidDimensionValueException
     * @throws IOException
     * @throws FactoryException
     * @throws TransformException
     */
    public List<Float>[] readXYComponents(DateTime dateTime, double elevation, RegularGrid grid)
            throws InvalidDimensionValueException, IOException, FactoryException,
            TransformException;
}