package dr.evomodel.epidemiology.casetocase;

import dr.app.tools.NexusExporter;
import dr.evolution.coalescent.*;
import dr.evolution.tree.FlexibleNode;
import dr.evolution.tree.FlexibleTree;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.Taxon;
import dr.evolution.util.TaxonList;
import dr.evolution.util.Units;
import dr.evomodel.coalescent.DemographicModel;
import dr.evomodel.epidemiology.casetocase.periodpriors.AbstractPeriodPriorDistribution;
import dr.evomodel.tree.TreeModel;
import dr.inference.loggers.LogColumn;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.math.*;
import dr.xml.*;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.*;

/**
 * Intended to replace the tree prior; each subtree in the partition is considered a tree in its own right generated by
 * a coalescent process
 *
 * @author Matthew Hall
 */

public class WithinCaseCoalescent extends CaseToCaseTreeLikelihood {

    public static final String WITHIN_CASE_COALESCENT = "withinCaseCoalescent";

    private enum Mode {TRUNCATE, NORMAL}

    private double[] partitionTreeLogLikelihoods;
    private double[] storedPartitionTreeLogLikelihoods;
    private boolean[] recalculateCoalescentFlags;
    private HashMap<AbstractCase,Treelet> partitionsAsTrees;
    private HashMap<AbstractCase,Treelet> storedPartitionsAsTrees;
    private DemographicModel demoModel;
    private Mode mode;


    private double infectiousPeriodsLogLikelihood;
    private double storedInfectiousPeriodsLogLikelihood;
    private double coalescencesLogLikelihood;
    private double storedCoalescencesLogLikelihood;


    public WithinCaseCoalescent(PartitionedTreeModel virusTree, AbstractOutbreak caseData,
                                String startingNetworkFileName, Parameter maxFirstInfToRoot, DemographicModel demoModel,
                                Mode mode)
            throws TaxonList.MissingTaxonException {

        super(WITHIN_CASE_COALESCENT, virusTree, caseData, maxFirstInfToRoot);
        this.mode = mode;
        this.demoModel = demoModel;
        addModel(demoModel);
        addModel(outbreak);
        partitionTreeLogLikelihoods = new double[outbreak.getCases().size()];
        storedPartitionTreeLogLikelihoods = new double[outbreak.getCases().size()];
        recalculateCoalescentFlags = new boolean[outbreak.getCases().size()];

        partitionsAsTrees = new HashMap<AbstractCase, Treelet>();
        for(AbstractCase aCase: outbreak.getCases()){
            if(aCase.wasEverInfected()){
                partitionsAsTrees.put(aCase, null);
            }
        }

        storedPartitionsAsTrees = new HashMap<AbstractCase, Treelet>();


        prepareTree(startingNetworkFileName);

    }

    protected double calculateLogLikelihood(){

        //checkPartitions();

        if(DEBUG){

            super.debugOutputTree("bleh.nex", true);
        }

        double logL = 0;

        // you shouldn't need to do this, because C2CTransL will already have done it

        // super.prepareTimings();

        HashMap<String, ArrayList<Double>> infectiousPeriodsByCategory
                = new HashMap<String, ArrayList<Double>>();

        // todo do this only once? Using indexes?

        for (AbstractCase aCase : outbreak.getCases()) {
            if(aCase.wasEverInfected()) {

                String category = ((CategoryOutbreak) outbreak).getInfectiousCategory(aCase);

                if (!infectiousPeriodsByCategory.keySet().contains(category)) {
                    infectiousPeriodsByCategory.put(category, new ArrayList<Double>());
                }

                ArrayList<Double> correspondingList
                        = infectiousPeriodsByCategory.get(category);

                correspondingList.add(getInfectiousPeriod(aCase));
            }
        }

        infectiousPeriodsLogLikelihood = 0;

        for (String category : ((CategoryOutbreak) outbreak).getInfectiousCategories()) {

            Double[] infPeriodsInThisCategory = infectiousPeriodsByCategory.get(category)
                    .toArray(new Double[infectiousPeriodsByCategory.size()]);

            AbstractPeriodPriorDistribution hyperprior = ((CategoryOutbreak) outbreak)
                    .getInfectiousCategoryPrior(category);

            double[] values = new double[infPeriodsInThisCategory.length];

            for (int i = 0; i < infPeriodsInThisCategory.length; i++) {
                values[i] = infPeriodsInThisCategory[i];
            }

            infectiousPeriodsLogLikelihood += hyperprior.getLogLikelihood(values);

        }

        logL += infectiousPeriodsLogLikelihood;

        explodeTree();

        coalescencesLogLikelihood = 0;


        for(AbstractCase aCase : outbreak.getCases()){

            int number = outbreak.getCaseIndex(aCase);

            if(aCase.wasEverInfected()) {

                // and then the little tree calculations

                HashSet<AbstractCase> children = getInfectees(aCase);

                if (recalculateCoalescentFlags[number]) {
                    Treelet treelet = partitionsAsTrees.get(aCase);



                    if (children.size() != 0) {
                        SpecifiedZeroCoalescent coalescent = new SpecifiedZeroCoalescent(treelet, demoModel,
                                treelet.getZeroHeight(), mode == Mode.TRUNCATE);
                        partitionTreeLogLikelihoods[number] = coalescent.calculateLogLikelihood();
                        coalescencesLogLikelihood += partitionTreeLogLikelihoods[number];
                        if (DEBUG && partitionTreeLogLikelihoods[number] == Double.POSITIVE_INFINITY) {
                            debugOutputTree("infCoalescent.nex", false);
                            debugTreelet(treelet, aCase + "_partition.nex");
                        }
                    } else {
                        partitionTreeLogLikelihoods[number] = 0.0;
                    }
                    recalculateCoalescentFlags[number] = false;
                } else {
                    coalescencesLogLikelihood += partitionTreeLogLikelihoods[number];
                }
            } else {
                recalculateCoalescentFlags[number] = false;
            }
        }

        logL += coalescencesLogLikelihood;

        likelihoodKnown = true;

        if(DEBUG){
            debugOutputTree("outstandard.nex", false);
            debugOutputTree("outfancy.nex", true);
        }

        return logL;
    }

    public void storeState(){
        super.storeState();
        storedPartitionsAsTrees = new HashMap<AbstractCase, Treelet>(partitionsAsTrees);
        storedPartitionTreeLogLikelihoods = Arrays.copyOf(partitionTreeLogLikelihoods,
                partitionTreeLogLikelihoods.length);


        storedCoalescencesLogLikelihood = coalescencesLogLikelihood;
        storedInfectiousPeriodsLogLikelihood = infectiousPeriodsLogLikelihood;

    }

    public void restoreState(){
        super.restoreState();
        partitionsAsTrees = storedPartitionsAsTrees;
        partitionTreeLogLikelihoods = storedPartitionTreeLogLikelihoods;


        coalescencesLogLikelihood = storedCoalescencesLogLikelihood;
        infectiousPeriodsLogLikelihood = storedInfectiousPeriodsLogLikelihood;

    }

    protected void handleModelChangedEvent(Model model, Object object, int index) {

        super.handleModelChangedEvent(model, object, index);

        if(model == treeModel){

            if(object instanceof PartitionedTreeModel.PartitionsChangedEvent){
                HashSet<AbstractCase> changedPartitions =
                        ((PartitionedTreeModel.PartitionsChangedEvent)object).getCasesToRecalculate();
                for(AbstractCase aCase : changedPartitions){
                    recalculateCaseWCC(aCase);
                }
            }
        } else if(model == branchMap){
            if(object instanceof ArrayList){

                for(int i=0; i<((ArrayList) object).size(); i++){
                    BranchMapModel.BranchMapChangedEvent event
                            =  (BranchMapModel.BranchMapChangedEvent)((ArrayList) object).get(i);

                    recalculateCaseWCC(event.getOldCase());
                    recalculateCaseWCC(event.getNewCase());

                    NodeRef node = treeModel.getNode(event.getNodeToRecalculate());
                    NodeRef parent = treeModel.getParent(node);

                    if(parent!=null){
                        recalculateCaseWCC(branchMap.get(parent.getNumber()));
                    }
                }
            } else {
                throw new RuntimeException("Unanticipated model changed event from BranchMapModel");
            }
        } else if(model == demoModel){
            Arrays.fill(recalculateCoalescentFlags, true);
        } else if(model == outbreak){
            if(object instanceof AbstractCase){
                AbstractCase thisCase = (AbstractCase)object;

                recalculateCaseWCC(thisCase);

                AbstractCase parent = getInfector(thisCase);

                if(parent!=null){
                    recalculateCaseWCC(parent);
                }
            }
        }
    }

    protected void recalculateCaseWCC(int index){
        partitionsAsTrees.put(outbreak.getCase(index), null);
        recalculateCoalescentFlags[index] = true;
    }

    protected void recalculateCaseWCC(AbstractCase aCase){
        if(aCase.wasEverInfected()) {
            recalculateCaseWCC(outbreak.getCaseIndex(aCase));
        }
    }


    public void makeDirty(){
        super.makeDirty();
        Arrays.fill(recalculateCoalescentFlags, true);
        for(AbstractCase aCase : outbreak.getCases()){
            if(aCase.wasEverInfected()) {
                partitionsAsTrees.put(aCase, null);
            }
        }
    }

    // Tears the tree into small pieces. Indexes correspond to indexes in the outbreak.

    private void explodeTree(){
        if(DEBUG){
            debugOutputTree("test.nex", false);
        }
        for(int i=0; i<outbreak.size(); i++){
            AbstractCase aCase = outbreak.getCase(i);
            if(aCase.wasEverInfected() && partitionsAsTrees.get(aCase)==null){

                NodeRef partitionRoot = getEarliestNodeInPartition(aCase);

                double extraHeight;

                if(treeModel.isRoot(partitionRoot)){
                    extraHeight = maxFirstInfToRoot.getParameterValue(0) * aCase.getInfectionBranchPosition().getParameterValue(0);
                } else {
                    extraHeight = treeModel.getBranchLength(partitionRoot) * aCase.getInfectionBranchPosition().getParameterValue(0);
                }

                FlexibleNode newRoot = new FlexibleNode();

                FlexibleTree littleTree = new FlexibleTree(newRoot);
                littleTree.beginTreeEdit();

                if (!treeModel.isExternal(partitionRoot)) {
                    for (int j = 0; j < treeModel.getChildCount(partitionRoot); j++) {
                        copyPartitionToTreelet(littleTree, treeModel.getChild(partitionRoot, j), newRoot, aCase);
                    }
                }

                littleTree.endTreeEdit();

                littleTree.resolveTree();

                double sampleTipHeight = 0;

                if(littleTree.getExternalNodeCount()>1) {
                    for (int j = 0; j < littleTree.getExternalNodeCount(); j++) {
                        NodeRef node = littleTree.getExternalNode(j);
                        if (!littleTree.getNodeTaxon(node).getId().startsWith("Transmission_")) {
                            sampleTipHeight = littleTree.getNodeHeight(node);
                            break;
                        }

                    }
                }





                Treelet treelet = new Treelet(littleTree,
                        littleTree.getRootHeight() + extraHeight);




//                if(sampleTipHeight==-1){
//                    System.out.println();
//                }

//                double heightPlusRB = treelet.getZeroHeight() - sampleTipHeight;
//                double infectedTime = aCase.examTime - getInfectionTime(aCase);
//
//                if(heightPlusRB!=infectedTime){
//                    System.out.println();
//                }

                partitionsAsTrees.put(aCase, treelet);


            }
        }
    }

    public ArrayList<AbstractCase> postOrderTransmissionTreeTraversal(){
        return traverseTransmissionTree(branchMap.get(treeModel.getRoot().getNumber()));
    }

    private ArrayList<AbstractCase> traverseTransmissionTree(AbstractCase aCase){
        ArrayList<AbstractCase> out = new ArrayList<AbstractCase>();
        HashSet<AbstractCase> children = getInfectees(aCase);
        for(int i=0; i<getOutbreak().size(); i++){
            AbstractCase possibleChild = getOutbreak().getCase(i);
            // easiest way to maintain the set ordering of the outbreak?
            if(children.contains(possibleChild)){
                out.addAll(traverseTransmissionTree(possibleChild));
            }
        }
        out.add(aCase);
        return out;
    }

    private void copyPartitionToTreelet(FlexibleTree littleTree, NodeRef oldNode, NodeRef newParent,
                                        AbstractCase partition){
        if(partition.wasEverInfected()) {
            if (branchMap.get(oldNode.getNumber()) == partition) {
                if (treeModel.isExternal(oldNode)) {
                    NodeRef newTip = new FlexibleNode(new Taxon(treeModel.getNodeTaxon(oldNode).getId()));
                    littleTree.addChild(newParent, newTip);
                    littleTree.setBranchLength(newTip, treeModel.getBranchLength(oldNode));
                } else {
                    NodeRef newChild = new FlexibleNode();
                    littleTree.addChild(newParent, newChild);
                    littleTree.setBranchLength(newChild, treeModel.getBranchLength(oldNode));
                    for (int i = 0; i < treeModel.getChildCount(oldNode); i++) {
                        copyPartitionToTreelet(littleTree, treeModel.getChild(oldNode, i), newChild, partition);
                    }
                }
            } else {
                // we need a new tip
                NodeRef transmissionTip = new FlexibleNode(
                        new Taxon("Transmission_" + branchMap.get(oldNode.getNumber()).getName()));
                double parentTime = getNodeTime(treeModel.getParent(oldNode));
                double childTime = getInfectionTime(branchMap.get(oldNode.getNumber()));
                littleTree.addChild(newParent, transmissionTip);
                littleTree.setBranchLength(transmissionTip, childTime - parentTime);
            }
        }
    }

    private class Treelet extends FlexibleTree {

        private double zeroHeight;

        private Treelet(FlexibleTree tree, double zeroHeight){
            super(tree);
            this.zeroHeight = zeroHeight;

        }

        private double getZeroHeight(){
            return zeroHeight;
        }



        private void setZeroHeight(double rootBranchLength){
            this.zeroHeight = zeroHeight;
        }
    }

    private Treelet transformTreelet(Treelet treelet){

        double[] transformedNodeTimes = new double[treelet.getNodeCount()];

        double totalHeight = treelet.getZeroHeight();

        double willMapToZero = totalHeight - 1;

        for(int i=0; i<treelet.getNodeCount(); i++){
            NodeRef node = treelet.getNode(i);

            double time =  treelet.getNodeHeight(node) - totalHeight;

            transformedNodeTimes[i] = -Math.log(-(time));
        }

        double first = Double.POSITIVE_INFINITY;
        for (double transformedNodeTime : transformedNodeTimes) {
            if (transformedNodeTime < first) {
                first = transformedNodeTime;
            }
        }

        double zeroHeight = -first;

        Treelet copy = new Treelet(treelet, zeroHeight);

        for(int i=0; i<copy.getNodeCount(); i++){
            NodeRef node = copy.getNode(i);

            copy.setNodeHeight(node, transformedNodeTimes[i] - first);
        }

        copy.resolveTree();

        return copy;

    }


    private class SpecifiedZeroCoalescent extends Coalescent {

        private double zeroHeight;
        boolean truncate;

        private SpecifiedZeroCoalescent(Tree tree, DemographicModel demographicModel, double zeroHeight,
                                        boolean truncate){
            super(tree, demographicModel.getDemographicFunction());

            this.zeroHeight = zeroHeight;
            this.truncate = truncate;

        }

        public double calculateLogLikelihood() {

            return calculatePartitionTreeLogLikelihood(getIntervals(), getDemographicFunction(), 0, zeroHeight,
                    truncate);
        }

    }

    public static double calculatePartitionTreeLogLikelihood(IntervalList intervals,
                                                             DemographicFunction demographicFunction, double threshold,
                                                             double zeroHeight, boolean truncate) {

        double logL = 0.0;

        double startTime = -zeroHeight;
        final int n = intervals.getIntervalCount();

        //TreeIntervals sets up a first zero-length interval with a lineage count of zero - skip this one

        for (int i = 0; i < n; i++) {

            if(truncate) {

                // time zero corresponds to the date of first infection

                final double duration = intervals.getInterval(i);
                final double finishTime = startTime + duration;

                // if this has happened the run is probably pretty unhappy

                if (finishTime == 0) {
                    return Double.NEGATIVE_INFINITY;
                }

                final double intervalArea = demographicFunction.getIntegral(startTime, finishTime);
                final double normalisationArea = demographicFunction.getIntegral(startTime, 0);

                if (intervalArea == 0 && duration != 0) {
                    return Double.NEGATIVE_INFINITY;
                }

                final int lineageCount = intervals.getLineageCount(i);

                if (lineageCount >= 2) {

                    final double kChoose2 = Binomial.choose2(lineageCount);

                    if (intervals.getIntervalType(i) == IntervalType.COALESCENT) {

                        logL += -kChoose2 * intervalArea;

                        final double demographicAtCoalPoint = demographicFunction.getDemographic(finishTime);

                        if (duration == 0.0 || demographicAtCoalPoint * (intervalArea / duration) >= threshold) {
                            logL -= Math.log(demographicAtCoalPoint);
                        } else {
                            return Double.NEGATIVE_INFINITY;
                        }

                    } else {
                        double numerator = Math.exp(-kChoose2 * intervalArea) - Math.exp(-kChoose2 * normalisationArea);
                        logL += Math.log(numerator);

                    }

                    // normalisation

                    double normExp = Math.exp(-kChoose2 * normalisationArea);

                    double logDenominator;

                    // the denominator has an irritating tendency to round to zero

                    if (normExp != 1) {
                        logDenominator = Math.log1p(-normExp);
                    } else {
                        logDenominator = handleDenominatorUnderflow(-kChoose2 * normalisationArea);
                    }


                    logL -= logDenominator;

                }

                startTime = finishTime;
            } else {
                if(!(demographicFunction instanceof LinearGrowth)){

                    throw new RuntimeException("Function must have zero population at t=0 if truncate=false");
                }

                final double duration = intervals.getInterval(i);
                final double finishTime = startTime + duration;

                final double intervalArea = demographicFunction.getIntegral(startTime, finishTime);
                if( intervalArea == 0 && duration != 0 ) {
                    return Double.NEGATIVE_INFINITY;
                }
                final int lineageCount = intervals.getLineageCount(i);
                final double kChoose2 = Binomial.choose2(lineageCount);
                // common part
                logL += -kChoose2 * intervalArea;

                if (intervals.getIntervalType(i) == IntervalType.COALESCENT) {

                    final double demographicAtCoalPoint = demographicFunction.getDemographic(finishTime);

                    // if value at end is many orders of magnitude different than mean over interval reject the interval
                    // This is protection against cases where ridiculous infinitesimal population size at the end of a
                    // linear interval drive coalescent values to infinity.

                    if( duration == 0.0 || demographicAtCoalPoint * (intervalArea/duration) >= threshold ) {
                        //                if( duration == 0.0 || demographicAtCoalPoint >= threshold * (duration/intervalArea) ) {
                        logL -= Math.log(demographicAtCoalPoint);
                    } else {
                        // remove this at some stage
                        //  System.err.println("Warning: " + i + " " + demographicAtCoalPoint + " " + (intervalArea/duration) );
                        return Double.NEGATIVE_INFINITY;
                    }

                }

                startTime = finishTime;
            }
        }



        return logL;
    }


    private static double handleDenominatorUnderflow(double input){
        BigDecimal bigDec = new BigDecimal(input);
        BigDecimal expBigDec = BigDecimalUtils.exp(bigDec, bigDec.scale());
        BigDecimal one = new BigDecimal(1.0);
        BigDecimal oneMinusExpBigDec = one.subtract(expBigDec);
        BigDecimal logOneMinusExpBigDec = BigDecimalUtils.ln(oneMinusExpBigDec, oneMinusExpBigDec.scale());
        return logOneMinusExpBigDec.doubleValue();
    }

    public void debugTreelet(Tree treelet, String fileName){
        try{
            FlexibleTree treeCopy = new FlexibleTree(treelet);
            for(int j=0; j<treeCopy.getNodeCount(); j++){
                FlexibleNode node = (FlexibleNode)treeCopy.getNode(j);
                node.setAttribute("Number", node.getNumber());
            }
            NexusExporter testTreesOut = new NexusExporter(new PrintStream(fileName));
            testTreesOut.exportTree(treeCopy);
        } catch (IOException ignored) {System.out.println("IOException");}
    }

    public LogColumn[] passColumns(){
        ArrayList<LogColumn> columns = new ArrayList<LogColumn>(Arrays.asList(super.passColumns()));

        if(outbreak instanceof CategoryOutbreak) {

            for (AbstractPeriodPriorDistribution hyperprior : ((CategoryOutbreak) outbreak).getInfectiousMap().values()) {
                columns.addAll(Arrays.asList(hyperprior.getColumns()));
            }

            columns.add(new LogColumn.Abstract("inf_LL") {
                protected String getFormattedValue() {
                    return String.valueOf(infectiousPeriodsLogLikelihood);
                }
            });
            for (int i = 0; i < outbreak.size(); i++) {
                if(outbreak.getCase(i).wasEverInfected()) {
                    final int finalI = i;
                    columns.add(new LogColumn.Abstract("coal_LL_" + i) {
                        protected String getFormattedValue() {
                            return String.valueOf(partitionTreeLogLikelihoods[finalI]);
                        }
                    });
                }
            }
            columns.add(new LogColumn.Abstract("total_coal_LL") {
                protected String getFormattedValue() {
                    return String.valueOf(coalescencesLogLikelihood);
                }
            });

            return columns.toArray(new LogColumn[columns.size()]);
        }
        return null;

    }


    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {
        public static final String STARTING_NETWORK = "startingNetwork";
        public static final String MAX_FIRST_INF_TO_ROOT = "maxFirstInfToRoot";
        public static final String DEMOGRAPHIC_MODEL = "demographicModel";
        public static final String TRUNCATE = "truncate";

        public String getParserName() {
            return WITHIN_CASE_COALESCENT;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            PartitionedTreeModel virusTree = (PartitionedTreeModel) xo.getChild(TreeModel.class);

            String startingNetworkFileName=null;

            if(xo.hasChildNamed(STARTING_NETWORK)){
                startingNetworkFileName = (String) xo.getElementFirstChild(STARTING_NETWORK);
            }

            AbstractOutbreak caseSet = (AbstractOutbreak) xo.getChild(AbstractOutbreak.class);

            CaseToCaseTreeLikelihood likelihood;

            Parameter earliestFirstInfection = (Parameter) xo.getElementFirstChild(MAX_FIRST_INF_TO_ROOT);

            DemographicModel demoModel = (DemographicModel) xo.getElementFirstChild(DEMOGRAPHIC_MODEL);

            Mode mode = xo.hasAttribute(TRUNCATE) & xo.getBooleanAttribute(TRUNCATE) ? Mode.TRUNCATE : Mode.NORMAL;

            try {
                likelihood = new WithinCaseCoalescent(virusTree, caseSet, startingNetworkFileName,
                        earliestFirstInfection, demoModel, mode);
            } catch (TaxonList.MissingTaxonException e) {
                throw new XMLParseException(e.toString());
            }

            return likelihood;
        }

        public String getParserDescription() {
            return "This element provides a tree prior for a partitioned tree, with each partitioned tree generated" +
                    "by a coalescent process";
        }

        public Class getReturnType() {
            return WithinCaseCoalescent.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private final XMLSyntaxRule[] rules = {
                new ElementRule(PartitionedTreeModel.class, "The tree"),
                new ElementRule(CategoryOutbreak.class, "The set of cases", 0,1),
                new ElementRule(CategoryOutbreak.class, "The set of cases", 0,1),
                new ElementRule("startingNetwork", String.class, "A CSV file containing a specified starting network",
                        true),
                new ElementRule(MAX_FIRST_INF_TO_ROOT, Parameter.class, "The maximum time from the first infection to" +
                        "the root node"),
                new ElementRule(DEMOGRAPHIC_MODEL, DemographicModel.class, "The demographic model for within-case" +
                        "evolution"),
                AttributeRule.newBooleanRule(TRUNCATE)
        };
    };



}
