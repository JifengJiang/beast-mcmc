/*
 * ExpConstExpDemographicModel.java
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

package dr.evomodel.coalescent;

import dr.evolution.coalescent.DemographicFunction;
import dr.evolution.coalescent.ExpConstExpDemographic;
import dr.evoxml.XMLUnits;
import dr.inference.model.Parameter;
import dr.xml.*;

/**
 * This class models a two growth-phase demographic with a plateau in the middle
 * 
 * @version $Id: ExpConstExpDemographicModel.java,v 1.2 2006/08/18 07:44:25 alexei Exp $
 *
 * @author Alexei Drummond
 * @author Andrew Rambaut
 */
public class ExpConstExpDemographicModel extends DemographicModel
{
	
	//
	// Public stuff
	//
	
	public static String POPULATION_SIZE = "populationSize";
	public static String RELATIVE_PLATEAU_SIZE = "relativePlateauSize";
    public static String RELATIVE_TIME_OF_MODERN_GROWTH = "relTimeModGrowth";
	public static String TIME_PLATEAU = "plateauStartTime";
	public static String ANCIENT_GROWTH_RATE = "ancientGrowthRate";
	
	public static String EXP_CONST_EXP_MODEL = "expConstExp";

	

	/**
	 * Construct demographic model with default settings
	 */
	public ExpConstExpDemographicModel(
            Parameter N0Parameter,
            Parameter N1Parameter,
            Parameter growthRateParameter,
            Parameter timeParameter,
            Parameter relTimeParameter,
            int units) {
	
		this(EXP_CONST_EXP_MODEL, N0Parameter, N1Parameter, growthRateParameter,  timeParameter, relTimeParameter, units);
	}

	/**
	 * Construct demographic model with default settings
	 */
	public ExpConstExpDemographicModel(String name, Parameter N0Parameter, Parameter N1Parameter, Parameter growthRateParameter, Parameter timeParameter, Parameter relTimeParameter, int units) {
	
		super(name);
		
		expConstExp = new ExpConstExpDemographic(units);
		
		this.N0Parameter = N0Parameter;
		addParameter(N0Parameter);
		N0Parameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, 0.0, 1));
		
		this.N1Parameter = N1Parameter;
		addParameter(N1Parameter);
		N1Parameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, 0.0, 1));

		this.growthRateParameter = growthRateParameter;
		addParameter(growthRateParameter);
		growthRateParameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, Double.MIN_VALUE, 1));

		this.timeParameter = timeParameter;
		addParameter(timeParameter);
		timeParameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, Double.MIN_VALUE, 1));

        this.relTimeParameter = relTimeParameter;
        addParameter(relTimeParameter);
        relTimeParameter.addBounds(new Parameter.DefaultBounds(1.0, Double.MIN_VALUE, 1));

		setUnits(units);
	}


	// general functions

	public DemographicFunction getDemographicFunction() {

        expConstExp.setN0(N0Parameter.getParameterValue(0));

        double relTime = relTimeParameter.getParameterValue(0);
        double time2 = timeParameter.getParameterValue(0);

        //System.out.println("relTime=" + relTime);
        //System.out.println("time2=" + (time2));


        double timeInModernGrowthPhase = time2 * relTime;

        double r = -Math.log(N1Parameter.getParameterValue(0))/timeInModernGrowthPhase;

        //System.out.println("N0=" + N0Parameter.getParameterValue(0));
        //System.out.println("r=" + r);
        //System.out.println("r2=" + growthRateParameter.getParameterValue(0));
        //System.out.println("time1=" + timeInModernGrowthPhase);
        //System.out.println("plateauTime=" + (time2-timeInModernGrowthPhase));

        expConstExp.setGrowthRate(r);
        expConstExp.setGrowthRate2(growthRateParameter.getParameterValue(0));

        expConstExp.setTime1(timeInModernGrowthPhase);
        expConstExp.setPlateauTime(time2-timeInModernGrowthPhase);

		return expConstExp;
	}
	
	/**
	 * Parses an element from an DOM document into a ExponentialGrowth. 
	 */
	public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {
		
		public String getParserName() { return EXP_CONST_EXP_MODEL; }
			
		public Object parseXMLObject(XMLObject xo) throws XMLParseException {
			
			int units = XMLParser.Utils.getUnitsAttr(xo);
			
			XMLObject cxo = (XMLObject)xo.getChild(POPULATION_SIZE);
			Parameter N0Param = (Parameter)cxo.getChild(Parameter.class);
			
			cxo = (XMLObject)xo.getChild(RELATIVE_PLATEAU_SIZE);
			Parameter N1Param = (Parameter)cxo.getChild(Parameter.class);
			
			cxo = (XMLObject)xo.getChild(RELATIVE_TIME_OF_MODERN_GROWTH);
			Parameter  rtParam = (Parameter)cxo.getChild(Parameter.class);
			
			cxo = (XMLObject)xo.getChild(TIME_PLATEAU);
			Parameter tParam = (Parameter)cxo.getChild(Parameter.class);

            cxo = (XMLObject)xo.getChild(ANCIENT_GROWTH_RATE);
            Parameter rParam = (Parameter)cxo.getChild(Parameter.class);

			return new ExpConstExpDemographicModel(N0Param, N1Param, rParam, tParam, rtParam, units);
		}
		
		//************************************************************************
		// AbstractXMLObjectParser implementation
		//************************************************************************
		
		public String getParserDescription() {
			return "A demographic model of exponential growth.";
		}

		public Class getReturnType() { return ExpConstExpDemographicModel.class; }

		public XMLSyntaxRule[] getSyntaxRules() { return rules; }
	
		private XMLSyntaxRule[] rules = new XMLSyntaxRule[] {
			new ElementRule(POPULATION_SIZE, 
				new XMLSyntaxRule[] { new ElementRule(Parameter.class) }),
			new ElementRule(RELATIVE_PLATEAU_SIZE,
				new XMLSyntaxRule[] { new ElementRule(Parameter.class) }, 
					"The size of plateau relative to modern population size." ),
			new ElementRule(RELATIVE_TIME_OF_MODERN_GROWTH,
				new XMLSyntaxRule[] { new ElementRule(Parameter.class) }, 
					"The time spanned by modern growth phase relative to time back to start of plateau phase." ),
			new ElementRule(TIME_PLATEAU,
				new XMLSyntaxRule[] { new ElementRule(Parameter.class) }, 
					"The time of the start of plateauPhase." ),
            new ElementRule(ANCIENT_GROWTH_RATE,
                    new XMLSyntaxRule[] { new ElementRule(Parameter.class) },
                        "The growth rate of early growth phase" ),
			XMLUnits.SYNTAX_RULES[0]
		};	
	};
	//
	// protected stuff
	//

	Parameter N0Parameter = null;	
	Parameter N1Parameter = null;	
	Parameter growthRateParameter = null;	
	Parameter timeParameter = null;	
    Parameter relTimeParameter = null;
	ExpConstExpDemographic expConstExp = null;
}