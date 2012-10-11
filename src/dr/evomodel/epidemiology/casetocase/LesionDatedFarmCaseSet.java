package dr.evomodel.epidemiology.casetocase;

import dr.inference.distribution.GammaDistributionModel;
import dr.inference.model.Parameter;
import dr.math.RiemannApproximation;
import dr.xml.*;

import java.util.ArrayList;

/**
 * Created with IntelliJ IDEA.
 * User: Matthew Hall
 * Date: 07/09/2012
 * Time: 16:17
 * To change this template use File | Settings | File Templates.
 */
public class LesionDatedFarmCaseSet extends AbstractCaseSet{

    public LesionDatedFarmCaseSet(GammaDistributionModel incubationPeriodDistribution,
                                  ArrayList<AbstractCase> farms, Integer riemannSampleSize){
        this.incubationPeriodDistribution = incubationPeriodDistribution;
        this.cases = farms;
        numericalIntegrator = new RiemannApproximation(riemannSampleSize);
        for(AbstractCase farm : farms){
            ((LesionDatedFarmCase)farm).installNumericalIntegrator(numericalIntegrator);
        }
    }

    /* Likelihood of the root branch (the farm is infectious by the root node time)*/

    public double rootBranchLikelihood(AbstractCase farm, Integer farmInfectiousBy) {
        return Math.exp(rootBranchLogLikelihood(farm, farmInfectiousBy));
    }

    public double rootBranchLogLikelihood(AbstractCase farm, Integer farmInfectiousBy) {
        if(farm.culledYet(farmInfectiousBy)){
            return Double.NEGATIVE_INFINITY;
        } else {
            return Math.log(((LesionDatedFarmCase) farm).infectiousCDF(farmInfectiousBy));
        }
    }

    /* Likelihood of a non-root branch (the farm is infected at the parent node time and infectious by the child node
    time). */

    public double branchLikelihood(AbstractCase parent, AbstractCase child, Integer childInfected, Integer
            childInfectiousBy) {
        return Math.exp(branchLogLikelihood(parent, child, childInfected, childInfectiousBy));
    }

    public double branchLogLikelihood(AbstractCase parent, AbstractCase child, Integer childInfected, Integer
            childInfectiousBy) {
        if(child.culledYet(childInfectiousBy)){
            return Double.NEGATIVE_INFINITY;
        } else if(parent==child){
            return 0;
        } else {
            return Math.log(((LesionDatedFarmCase)child).periodInfectionDistribution(childInfected - 1, childInfected,
                    childInfectiousBy));
        }
    }

    public ArrayList<AbstractCase> getCases() {
        return new ArrayList<AbstractCase>(cases);
    }

    /* Parser. */

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {
        public static final String INCUBATION_PERIOD_DISTRIBUTION = "incubationPeriodDistribution";
        public static final String RIEMANN_SAMPLE_SIZE = "riemannSampleSize";

        @Override
        public Object parseXMLObject(XMLObject xo) throws XMLParseException {
            final GammaDistributionModel incubationPeriodDistribution =
                    (GammaDistributionModel) xo.getElementFirstChild(INCUBATION_PERIOD_DISTRIBUTION);
            ArrayList<AbstractCase> tempFarms = new ArrayList<AbstractCase>();
            for(int i=0; i<xo.getChildCount(); i++){
                if(xo.getChild(i) instanceof LesionDatedFarmCase){
                    tempFarms.add((LesionDatedFarmCase)xo.getChild(i));
                }
            }
            final Parameter riemannSampleSize = (Parameter) xo.getElementFirstChild(RIEMANN_SAMPLE_SIZE);
            final ArrayList<AbstractCase> farms = tempFarms;
            return new LesionDatedFarmCaseSet(incubationPeriodDistribution, farms,
                    (int)riemannSampleSize.getParameterValue(0));
        }

        @Override
        public String getParserDescription(){
            return "Parses a set of lesion dated farm cases and the information that they all share";
        }

        @Override
        public Class getReturnType(){
            return LesionDatedFarmCaseSet.class;
        }

        public String getParserName(){
            return NEW_MODEL_FARM_SET;
        }

        @Override
        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private final XMLSyntaxRule[] rules = {
                new ElementRule(INCUBATION_PERIOD_DISTRIBUTION, GammaDistributionModel.class, "The probability " +
                        "distribution of incubation periods for this set of cases", false),
                new ElementRule(LesionDatedFarmCase.class, 1, Integer.MAX_VALUE),
                new ElementRule(RIEMANN_SAMPLE_SIZE, Parameter.class, "The sample size for the Riemann numerical" +
                        "integration method, used by all child cases.", true)
        };
    };

    public static final String NEW_MODEL_FARM_SET = "newModelFarmSet";
    private GammaDistributionModel incubationPeriodDistribution;
    public RiemannApproximation numericalIntegrator;

}
