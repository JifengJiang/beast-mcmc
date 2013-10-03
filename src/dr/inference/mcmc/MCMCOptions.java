/*
 * MCMCOptions.java
 *
 * Copyright (C) 2002-2006 Alexei Drummond and Andrew Rambaut
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.inference.mcmc;

/**
 * A class that brings together the auxillary information associated
 * with an MCMC analysis.
 *
 * @author Alexei Drummond
 * @version $Id: MCMCOptions.java,v 1.7 2005/05/24 20:25:59 rambaut Exp $
 */
public class MCMCOptions {

    private final long chainLength;
    private final long fullEvaluationCount;
    private final int minOperatorCountForFullEvaluation;
    private final boolean coercion;
    private final long coercionDelay;
    private final double temperature;

    /**
     * constructor
     * @param chainLength
     */
    public MCMCOptions(long chainLength) {
        this(chainLength, 2000, 1, true, 0, 1.0);
    }

    /**
     * constructor
     * @param chainLength
     * @param fullEvaluationCount
     * @param minOperatorCountForFullEvaluation
     * @param coercion
     * @param coercionDelay
     * @param temperature
     */
    public MCMCOptions(long chainLength, long fullEvaluationCount, int minOperatorCountForFullEvaluation, boolean coercion, long coercionDelay, double temperature) {
        this.chainLength = chainLength;
        this.fullEvaluationCount = fullEvaluationCount;
        this.minOperatorCountForFullEvaluation = minOperatorCountForFullEvaluation;
        this.coercion = coercion;
        this.coercionDelay = coercionDelay;
        this.temperature = temperature;
    }

    /**
     * @return the chain length of the MCMC analysis
     */
    public final long getChainLength() {
        return chainLength;
    }

    public final long fullEvaluationCount() {
        return fullEvaluationCount;
    }

    public final boolean useCoercion() {
        return coercion;
    }


    public final long getCoercionDelay() {
        return coercionDelay;
    }

    public final double getTemperature() {
        return temperature;
    }

    public int minOperatorCountForFullEvaluation() {
        return minOperatorCountForFullEvaluation;
    }
}
