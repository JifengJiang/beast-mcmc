package dr.evomodelxml.branchratemodel;

import dr.evolution.util.TaxonList;
import dr.evomodel.branchratemodel.FixedDriftModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Parameter;
import dr.xml.*;

import java.util.logging.Logger;

/**
 * Created by mandevgill on 12/22/14.
 */
public class FixedDriftModelParser extends AbstractXMLObjectParser {
    public static final String FIXED_DRIFT = "fixedDriftModel";
    /*
    public static final String RATE_ONE = "rateOne";
    public static final String RATE_TWO = "rateTwo";
    public static final String REMAINING_RATES = "remainingRates";
    public static final String RATE_ONE_ID = "rateOneID";
    public static final String RATE_TWO_ID = "rateTwoID";
    */
    public static final String BACKBONE_DRIFT = "backboneDrift";

    public static final String OTHER_DRIFT = "otherDrift";

    public static final String BACKBONE_TAXON_LIST = "backboneTaxonList";

    public String getParserName() {
        return FIXED_DRIFT;
    }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {

        /*
        String idOne = xo.getStringAttribute(RATE_ONE_ID);

        String idTwo = xo.getStringAttribute(RATE_TWO_ID);

        Parameter rateOne = (Parameter) xo.getElementFirstChild(RATE_ONE);

        Parameter rateTwo = (Parameter) xo.getElementFirstChild(RATE_TWO);

        Parameter remainingRates = (Parameter) xo.getElementFirstChild(REMAINING_RATES);
        */

        TaxonList taxonList = (TaxonList) xo.getElementFirstChild(BACKBONE_TAXON_LIST);

        TreeModel treeModel = (TreeModel) xo.getChild(TreeModel.class);

        Parameter backboneDrift = (Parameter) xo.getElementFirstChild(BACKBONE_DRIFT);

        Parameter otherDrift = (Parameter) xo.getElementFirstChild(OTHER_DRIFT);

        Logger.getLogger("dr.evomodel").info("Using fixed drift model.");


        return new FixedDriftModel(treeModel, backboneDrift, otherDrift, taxonList);
        //  return new FixedDriftModel(rateOne, rateTwo, remainingRates, idOne, idTwo);
    }

    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public String getParserDescription() {
        return
                "This element returns a relaxed drift model.";
    }

    public Class getReturnType() {
        return FixedDriftModel.class;
    }

    public XMLSyntaxRule[] getSyntaxRules() {
        return rules;
    }

    private XMLSyntaxRule[] rules = new XMLSyntaxRule[]{
            new ElementRule(BACKBONE_DRIFT, Parameter.class, "backbone drift rate", false),
            new ElementRule(OTHER_DRIFT, Parameter.class, "other drift rate", false)
           /*
            AttributeRule.newStringRule(RATE_ONE_ID, false),
            AttributeRule.newStringRule(RATE_TWO_ID, false),
            new ElementRule(RATE_ONE, Parameter.class, "rate one parameter", false),
            new ElementRule(RATE_TWO, Parameter.class, "rate two parameter", false),
            new ElementRule(REMAINING_RATES, Parameter.class, "remaining rates parameter", false)
            */
    };


}
