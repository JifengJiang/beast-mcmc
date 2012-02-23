/*
 * VarianceStatistic.java
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

package dr.inference.model;

import dr.xml.*;
import dr.stats.DiscreteStatistics;

import java.util.Vector;

/**
 * @version $Id: VarianceStatistic.java,v 1.3 2005/05/24 20:26:00 rambaut Exp $
 *
 * @author Alexei Drummond
 */
public class VarianceStatistic extends Statistic.Abstract {
	
	public static String VARIANCE_STATISTIC = "varianceStatistic";

	public VarianceStatistic(String name) {
		super(name);
	}
	
	public void addStatistic(Statistic statistic) {
		statistics.add(statistic);
        int dimensionCount = 0;
        for (int i = 0; i < statistics.size(); i++) {
			statistic = (Statistic)statistics.get(i);
			dimensionCount += statistic.getDimension();
        }
        values = new double[dimensionCount];
	}
	
	public int getDimension() { return 1; }

	/** @return variance of contained statistics */
	public final double getStatisticValue(int dim) {
		int n;
		Statistic statistic;

        int index = 0;
        for (int i = 0; i < statistics.size(); i++) {
            statistic = (Statistic)statistics.get(i);
			n = statistic.getDimension();
            for (int j = 0; j < n; j++) {
				values[index] = statistic.getStatisticValue(j);
			    index += 1;
            }
		}
		
		return DiscreteStatistics.variance(values);
	}
		
	public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {
		
		public String getParserName() { return VARIANCE_STATISTIC; }
		
		public Object parseXMLObject(XMLObject xo) throws XMLParseException {
			
			VarianceStatistic varStatistic = new VarianceStatistic(VARIANCE_STATISTIC);
			
			for (int i =0; i < xo.getChildCount(); i++) {
				Object child = xo.getChild(i);
				if (child instanceof Statistic) {
					varStatistic.addStatistic((Statistic)child);
				} else {
					throw new XMLParseException("Unknown element found in " + getParserName() + " element:" + child);
				}
			}
				
			return varStatistic;
		}
		
		//************************************************************************
		// AbstractXMLObjectParser implementation
		//************************************************************************
		
		public String getParserDescription() {
			return "This element returns a statistic that is the variance of the child statistics.";
		}
		
		public Class getReturnType() { return VarianceStatistic.class; }
		
		public XMLSyntaxRule[] getSyntaxRules() { return rules; }
		
		private XMLSyntaxRule[] rules = new XMLSyntaxRule[] {
			new ElementRule(Statistic.class, 1, Integer.MAX_VALUE )
		};		
	};
	

	// ****************************************************************
	// Private and protected stuff
	// ****************************************************************
	
	private Vector statistics = new Vector();
    private double[] values = null;
}