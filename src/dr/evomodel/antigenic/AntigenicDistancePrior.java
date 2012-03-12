package dr.evomodel.antigenic;

import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evolution.util.TaxonList;
import dr.inference.model.*;
import dr.math.MathUtils;
import dr.math.distributions.NormalDistribution;
import dr.stats.Regression;
import dr.util.Author;
import dr.util.Citable;
import dr.util.Citation;
import dr.util.DataTable;
import dr.xml.*;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Andrew Rambaut
 * @author Trevor Bedford
 * @author Marc Suchard
 * @version $Id$
 */
public class AntigenicDistancePrior extends AbstractModelLikelihood implements Citable {

    public final static String ANTIGENIC_DISTANCE_PRIOR = "antigenicDistancePrior";

    public AntigenicDistancePrior(
            MatrixParameter locationsParameter,
            Parameter datesParameter,
            Parameter regressionSlopeParameter,
            Parameter regressionPrecisionParameter
    ) {

        super(ANTIGENIC_DISTANCE_PRIOR);

        this.locationsParameter = locationsParameter;
        addVariable(this.locationsParameter);

        this.datesParameter = datesParameter;
        addVariable(this.datesParameter);

        dimension = locationsParameter.getColumnDimension();

        this.regressionSlopeParameter = regressionSlopeParameter;
        addVariable(regressionSlopeParameter);
        regressionSlopeParameter.addBounds(new Parameter.DefaultBounds(Double.MAX_VALUE, 0.0, 1));

        this.regressionPrecisionParameter = regressionPrecisionParameter;
        addVariable(regressionPrecisionParameter);
        regressionPrecisionParameter.addBounds(new Parameter.DefaultBounds(Double.MAX_VALUE, 0.0, 1));

        likelihoodKnown = false;
    }


    @Override
    protected void handleModelChangedEvent(Model model, Object object, int index) {
    }

    @Override
    protected void handleVariableChangedEvent(Variable variable, int index, Variable.ChangeType type) {
        if (variable == locationsParameter) {
            likelihoodKnown = false;
        } else if (variable == datesParameter) {
            likelihoodKnown = false;
        }
    }

    @Override
    protected void storeState() {
    }

    @Override
    protected void restoreState() {
        likelihoodKnown = false;
    }

    @Override
    protected void acceptState() {
    }

    @Override
    public Model getModel() {
        return this;
    }

    @Override
    public double getLogLikelihood() {
        if (!likelihoodKnown) {
            logLikelihood = computeLogLikelihood();
        }

        return logLikelihood;
    }

    private double computeLogLikelihood() {

        logLikelihood = 0.0;

        double slope = regressionSlopeParameter.getParameterValue(0);
        double stdev = 1.0 / Math.sqrt(regressionPrecisionParameter.getParameterValue(0));

        for (int i = 0; i < locationsParameter.getRowDimension(); i++) {
            for (int j = i + 1; j < locationsParameter.getRowDimension(); j++) {
                double distance = computeDistance(i, j);
                double timeDiff = Math.abs(datesParameter.getParameterValue(i) - datesParameter.getParameterValue(j));

                logLikelihood += computeDateLikelihood(distance, timeDiff, slope, stdev);
            }
        }
        likelihoodKnown = true;

        return logLikelihood;
    }

    protected double computeDistance(int rowStrain, int columnStrain) {
        if (rowStrain == columnStrain) {
            return 0.0;
        }

        Parameter X = locationsParameter.getParameter(rowStrain);
        Parameter Y = locationsParameter.getParameter(columnStrain);
        double sum = 0.0;
        for (int i = 0; i < dimension; i++) {
            double difference = X.getParameterValue(i) - Y.getParameterValue(i);
            sum += difference * difference;
        }
        return Math.sqrt(sum);
    }

    private double computeDateLikelihood(double distance, double timeDiff, double slope, double stdev) {
        double mean = slope * timeDiff;
        double lnL = Math.log(NormalDistribution.pdf(distance, mean, stdev));
        if (Double.isNaN(lnL) || Double.isInfinite(lnL)) {
            throw new RuntimeException("infinite");
        }
        return lnL;
    }
    @Override
    public void makeDirty() {
        likelihoodKnown = false;
    }

    private final int dimension;
    private final Parameter datesParameter;
    private final MatrixParameter locationsParameter;
    private final Parameter regressionSlopeParameter;
    private final Parameter regressionPrecisionParameter;

    private double logLikelihood = 0.0;
    private boolean likelihoodKnown = false;

    // **************************************************************
    // XMLObjectParser
    // **************************************************************

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        public final static String LOCATIONS = "locations";
        public final static String DATES = "dates";
        public final static String REGRESSIONSLOPE = "regressionSlope";
        public final static String REGRESSIONPRECISION = "regressionPrecision";

        public String getParserName() {
            return ANTIGENIC_DISTANCE_PRIOR;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            MatrixParameter locationsParameter = (MatrixParameter) xo.getElementFirstChild(LOCATIONS);
            Parameter datesParameter = (Parameter) xo.getElementFirstChild(DATES);
            Parameter regressionSlopeParameter = (Parameter) xo.getElementFirstChild(REGRESSIONSLOPE);
            Parameter regressionPrecisionParameter = (Parameter) xo.getElementFirstChild(REGRESSIONPRECISION);

            AntigenicDistancePrior AGDP = new AntigenicDistancePrior(
                    locationsParameter,
                    datesParameter,
                    regressionSlopeParameter,
                    regressionPrecisionParameter);

//            Logger.getLogger("dr.evomodel").info("Using EvolutionaryCartography model. Please cite:\n" + Utils.getCitationString(AGL));

            return AGDP;
        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return "Provides the likelihood of a vector of coordinates in some multidimensional 'antigenic' space based on an expected relationship with time.";
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private final XMLSyntaxRule[] rules = {
                new ElementRule(LOCATIONS, MatrixParameter.class),
                new ElementRule(DATES, Parameter.class),
                new ElementRule(REGRESSIONSLOPE, Parameter.class),
                new ElementRule(REGRESSIONPRECISION, Parameter.class)
        };

        public Class getReturnType() {
            return ContinuousAntigenicTraitLikelihood.class;
        }
    };

    public List<Citation> getCitations() {
        List<Citation> citations = new ArrayList<Citation>();
        citations.add(new Citation(
                new Author[]{
                        new Author("T", "Bedford"),
                        new Author("MA", "Suchard"),
                        new Author("P", "Lemey"),
                        new Author("G", "Dudas"),
                        new Author("C", "Russell"),
                        new Author("D", "Smith"),
                        new Author("A", "Rambaut")
                },
                Citation.Status.IN_PREPARATION
        ));
        return citations;
    }
}
