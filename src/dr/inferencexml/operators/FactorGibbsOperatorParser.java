package dr.inferencexml.operators;

import dr.evomodel.continuous.LatentFactorModel;
import dr.inference.distribution.MultivariateDistributionLikelihood;
import dr.math.Polynomial;
import dr.inference.operators.FactorGibbsOperator;
import dr.xml.*;

/**
 * Created by Max on 5/5/14.
 */
public class FactorGibbsOperatorParser extends AbstractXMLObjectParser {
    private final String FACTOR_GIBBS_SAMPLER="factorGibbsOperator";
    private final String WEIGHT="weight";


    @Override
    public Object parseXMLObject(XMLObject xo) throws XMLParseException {
        String weightTemp= (String) xo.getAttribute(WEIGHT);
        Double weight=Double.parseDouble(weightTemp);
        LatentFactorModel LFM =(LatentFactorModel) xo.getChild(LatentFactorModel.class);
        return new FactorGibbsOperator(LFM, weight);
    }

    @Override
    public XMLSyntaxRule[] getSyntaxRules() {
        return rules;
    }

    private XMLSyntaxRule[] rules = new XMLSyntaxRule[]{
            new ElementRule(LatentFactorModel.class),
//            new ElementRule(CompoundParameter.class),
            AttributeRule.newDoubleRule(WEIGHT),
    };

    @Override
    public String getParserDescription() {
        return null;
    }

    @Override
    public Class getReturnType() {
        return FactorGibbsOperator.class;
    }

    @Override
    public String getParserName() {
        return FACTOR_GIBBS_SAMPLER;
    }
}
