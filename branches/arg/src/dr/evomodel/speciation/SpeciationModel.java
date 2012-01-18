/*
 * SpeciationModel.java
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

package dr.evomodel.speciation;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.Units;
import dr.evomodel.arg.ARGModel;
import dr.inference.model.AbstractModel;
import dr.inference.model.Model;
import dr.inference.model.Parameter;

/**
 * This interface provides methods that describe a speciation model.
 *
 * @author Andrew Rambaut
 */
public abstract class SpeciationModel extends AbstractModel implements Units {

	public SpeciationModel(String name, int units) {
		super(name);
		setUnits(units);
	}

	//
	// functions that define a speciation model
	//
	public abstract double logTreeProbability(int taxonCount);

	//
	// functions that define a speciation model
	//
	public abstract double logNodeProbability(Tree tree, NodeRef node);


	public double logReassortmentProbability(ARGModel tree) {
		return 0.0;
	}

	// **************************************************************
	// Model IMPLEMENTATION
	// **************************************************************

	protected void handleModelChangedEvent(Model model, Object object, int index) {
		// no intermediates need to be recalculated...
	}

	protected void handleParameterChangedEvent(Parameter parameter, int index) {
		// no intermediates need to be recalculated...
	}

	protected void storeState() {
	} // no additional state needs storing

	protected void restoreState() {
	} // no additional state needs restoring

	protected void acceptState() {
	} // no additional state needs accepting

	protected void adoptState(Model source) {
	} // no additional state needs adopting

	// **************************************************************
	// Units IMPLEMENTATION
	// **************************************************************

	/**
	 * Units in which population size is measured.
	 */
	private int units;

	/**
	 * sets units of measurement.
	 *
	 * @param u units
	 */
	public void setUnits(int u) {
		units = u;
	}

	/**
	 * returns units of measurement.
	 */
	public int getUnits() {
		return units;
	}
}