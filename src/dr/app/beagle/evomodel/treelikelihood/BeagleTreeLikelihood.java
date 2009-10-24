/*
 * TreeLikelihood.java
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

package dr.app.beagle.evomodel.treelikelihood;

import beagle.*;
import dr.app.beagle.evomodel.parsers.TreeLikelihoodParser;
import dr.app.beagle.evomodel.sitemodel.BranchSiteModel;
import dr.app.beagle.evomodel.sitemodel.SiteRateModel;
import dr.app.beagle.evomodel.substmodel.EigenDecomposition;
import dr.evolution.alignment.PatternList;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.TaxonList;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.branchratemodel.DefaultBranchRateModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * BeagleTreeLikelihoodModel - implements a Likelihood Function for sequences on a tree.
 *
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @author Marc Suchard
 * @version $Id$
 */

public class BeagleTreeLikelihood extends AbstractTreeLikelihood {

    // This property is a comma-delimited list of resource numbers (0 == CPU) to
    // allocate each BEAGLE instance to. If less than the number of instances then
    // will wrap around.
    private static final String RESOURCE_ORDER_PROPERTY = "beagle.resource.order";
    private static final String PREFERRED_FLAGS_PROPERTY = "beagle.preferred.flags";
    private static final String REQUIRED_FLAGS_PROPERTY = "beagle.required.flags";

    private static int instanceCount = 0;
    private static List<Integer> resourceOrder = null;

    public BeagleTreeLikelihood(PatternList patternList,
                                TreeModel treeModel,
                                BranchSiteModel branchSiteModel,
                                SiteRateModel siteRateModel,
                                BranchRateModel branchRateModel,
                                boolean useAmbiguities,
                                PartialsRescalingScheme rescalingScheme
    ) {

        super(TreeLikelihoodParser.TREE_LIKELIHOOD, patternList, treeModel);

        try {
            final Logger logger = Logger.getLogger("dr.evomodel");

            logger.info("Using BEAGLE TreeLikelihood");

            this.siteRateModel = siteRateModel;
            addModel(this.siteRateModel);

            this.branchSiteModel = branchSiteModel;
            addModel(branchSiteModel);

            if (branchRateModel != null) {
                this.branchRateModel = branchRateModel;
                logger.info("  Branch rate model used: " + branchRateModel.getModelName());
            } else {
                this.branchRateModel = new DefaultBranchRateModel();
            }
            addModel(this.branchRateModel);

            this.categoryCount = this.siteRateModel.getCategoryCount();

            this.tipCount = treeModel.getExternalNodeCount();

            internalNodeCount = nodeCount - tipCount;

            int compactPartialsCount = tipCount;
            if (useAmbiguities) {
                // if we are using ambiguities then we don't use tip partials
                compactPartialsCount = 0;
            }

            // one partials buffer for each tip and two for each internal node (for store restore)
            partialBufferHelper = new BufferIndexHelper(nodeCount, tipCount);

            // two eigen buffers: for store and restore.
            eigenBufferHelper = new BufferIndexHelper(1, 0);

            // two matrices for each node less the root
            matrixBufferHelper = new BufferIndexHelper(nodeCount, 0);

            // one scaling buffer for each internal node plus an extra for the accumulation, then doubled for store/restore
            scaleBufferHelper = new BufferIndexHelper(getScaleBufferCount(), 0);

            // Attempt to get the resource order from the System Property
            if (resourceOrder == null) {
                resourceOrder = new ArrayList<Integer>();
                String r = System.getProperty(RESOURCE_ORDER_PROPERTY);
                if (r != null) {
                    String[] parts = r.split(",");
                    for (String part : parts) {
                        try {
                            int n = Integer.parseInt(part.trim());
                            resourceOrder.add(n);
                        } catch (NumberFormatException nfe) {
                            System.err.println("Invalid entry '"+part+"' in "+RESOURCE_ORDER_PROPERTY);
                        }
                    }
                }

            }

            int[] resourceList = null;
            long preferenceFlags = 0;
            long requirementFlags = 0;

            if (resourceOrder.size() > 0) {
                // added the zero on the end so that a CPU is selected if requested resource fails
                resourceList = new int[] { resourceOrder.get(instanceCount % resourceOrder.size()), 0 };
            }

            if (System.getProperty(PREFERRED_FLAGS_PROPERTY) != null) {
                preferenceFlags = Long.valueOf(System.getProperty(PREFERRED_FLAGS_PROPERTY));
            }

            if (System.getProperty(REQUIRED_FLAGS_PROPERTY) != null) {
                requirementFlags = Long.valueOf(System.getProperty(REQUIRED_FLAGS_PROPERTY));
            }

            if (preferenceFlags == 0 && resourceList == null) { // else determine dataset characteristics
                if ( stateCount == 4 && patternList.getPatternCount() < 1000) // TODO determine good cut-off
                    preferenceFlags |= BeagleFlag.CPU.getMask();
            }

            if (branchSiteModel.canReturnComplexDiagonalization())
                requirementFlags |= BeagleFlag.COMPLEX.getMask();

            instanceCount ++;

            beagle = BeagleFactory.loadBeagleInstance(
                    tipCount,
                    partialBufferHelper.getBufferCount(),
                    compactPartialsCount,
                    stateCount,
                    patternCount,
                    eigenBufferHelper.getBufferCount(),            // eigenBufferCount
                    matrixBufferHelper.getBufferCount(),
                    categoryCount,
                    scaleBufferHelper.getBufferCount(), // Always allocate; they may become necessary
                    resourceList,
                    preferenceFlags,
                    requirementFlags
            );

            InstanceDetails instanceDetails = beagle.getDetails();

            if (instanceDetails != null) {
                ResourceDetails resourceDetails = BeagleFactory.getResourceDetails(instanceDetails.getResourceNumber());
                if (resourceDetails != null) {
                    StringBuilder sb = new StringBuilder("  Using BEAGLE resource ");
                    sb.append(resourceDetails.getNumber()).append(": ");
                    sb.append(resourceDetails.getName()).append("\n");
                    if (resourceDetails.getDescription() != null) {
                        String[] description = resourceDetails.getDescription().split("\\|");
                        for (String desc : description) {
                            if (desc.trim().length() > 0) {
                                sb.append("    ").append(desc.trim()).append("\n");
                            }
                        }
                    }
                    sb.append("    with instance flags: ").append(instanceDetails.toString());
                    logger.info(sb.toString());
                } else {
                    logger.info("  Error retrieving BEAGLE resource for instance: " + instanceDetails.toString());
                }
            } else {
                logger.info("  No external BEAGLE resources available, or resource list/requirements not met, using Java implementation");
            }
            logger.info("  " + (useAmbiguities ? "Using" : "Ignoring") + " ambiguities in tree likelihood.");
            logger.info("  With " + patternList.getPatternCount() + " unique site patterns.");

            for (int i = 0; i < tipCount; i++) {
                // Find the id of tip i in the patternList
                String id = treeModel.getTaxonId(i);
                int index = patternList.getTaxonIndex(id);

                if (index == -1) {
                    throw new TaxonList.MissingTaxonException("Taxon, " + id + ", in tree, " + treeModel.getId() +
                            ", is not found in patternList, " + patternList.getId());
                } else {
                    if (useAmbiguities) {
                        setPartials(beagle, patternList, index, i);
                    } else {
                        setStates(beagle, patternList, index, i);
                    }
                }
            }

            switch(rescalingScheme) {
                case NONE:
                    alwaysRescale = false;
                    allowRescale = false;
                    break;
                case ALWAYS_RESCALE:
                    alwaysRescale = true;
                    allowRescale = true;
                    break;
                case DYNAMIC_RESCALING:
                    alwaysRescale = false;
                    allowRescale = true;
                    break;
                default:
                    throw new RuntimeException("Unknown PartialsRescalingScheme in BeagleTreeLikelihood.");
            }
            logger.info("  Partials scaling scheme used: " + rescalingScheme.getText());

            updateSubstitutionModel = true;
            updateSiteModel = true;

        } catch (TaxonList.MissingTaxonException mte) {
            throw new RuntimeException(mte.toString());
        }
        hasInitialized = true;
    }

    protected int getScaleBufferCount() {
        return internalNodeCount + 1;
    }

    /**
     * Sets the partials from a sequence in an alignment.
     */
    protected final void setPartials(Beagle beagle,
                                     PatternList patternList,
                                     int sequenceIndex,
                                     int nodeIndex) {
        double[] partials = new double[patternCount * stateCount * categoryCount];

        boolean[] stateSet;

        int v = 0;
        for (int i = 0; i < patternCount; i++) {

            int state = patternList.getPatternState(sequenceIndex, i);
            stateSet = dataType.getStateSet(state);

            for (int j = 0; j < stateCount; j++) {
                if (stateSet[j]) {
                    partials[v] = 1.0;
                } else {
                    partials[v] = 0.0;
                }
                v++;
            }
        }

        // if there is more than one category then replicate the partials for each
        int n = patternCount * stateCount;
        int k = n;
        for (int i = 1; i < categoryCount; i++) {
            System.arraycopy(partials, 0, partials, k, n);
            k += n;
        }

        beagle.setPartials(nodeIndex, partials);
    }

    /**
     * Sets the partials from a sequence in an alignment.
     */
    protected final void setStates(Beagle beagle,
                                   PatternList patternList,
                                   int sequenceIndex,
                                   int nodeIndex) {
        int i;

        int[] states = new int[patternCount];

        for (i = 0; i < patternCount; i++) {

            states[i] = patternList.getPatternState(sequenceIndex, i);
        }

        beagle.setTipStates(nodeIndex, states);
    }

    // **************************************************************
    // ModelListener IMPLEMENTATION
    // **************************************************************

    /**
     * Handles model changed events from the submodels.
     */
    protected void handleModelChangedEvent(Model model, Object object, int index) {

        if (model == treeModel) {
            if (object instanceof TreeModel.TreeChangedEvent) {

                if (((TreeModel.TreeChangedEvent) object).isNodeChanged()) {
                    // If a node event occurs the node and its two child nodes
                    // are flagged for updating (this will result in everything
                    // above being updated as well. Node events occur when a node
                    // is added to a branch, removed from a branch or its height or
                    // rate changes.
                    updateNodeAndChildren(((TreeModel.TreeChangedEvent) object).getNode());

                } else if (((TreeModel.TreeChangedEvent) object).isTreeChanged()) {
                    // Full tree events result in a complete updating of the tree likelihood
                    // Currently this event type is not used.
                    System.err.println("Full tree update event - these events currently aren't used\n" +
                            "so either this is in error or a new feature is using them so remove this message.");
                    updateAllNodes();
                } else {
                    // Other event types are ignored (probably trait changes).
                    //System.err.println("Another tree event has occured (possibly a trait change).");
                }
            }

        } else if (model == branchRateModel) {
            if (index == -1) {
                if (COUNT_TOTAL_OPERATIONS)
                    totalRateUpdateAllCount++;
                updateAllNodes();
            } else {
                if (COUNT_TOTAL_OPERATIONS)
                    totalRateUpdateSingleCount++;
                updateNode(treeModel.getNode(index));
            }

        } else if (model == branchSiteModel) {

            updateSubstitutionModel = true;
            updateAllNodes();

        } else if (model == siteRateModel) {

            updateSiteModel = true;
            updateAllNodes();

        } else {

            throw new RuntimeException("Unknown componentChangedEvent");
        }

        super.handleModelChangedEvent(model, object, index);
    }

    // **************************************************************
    // Model IMPLEMENTATION
    // **************************************************************

    /**
     * Stores the additional state other than model components
     */
    protected void storeState() {
        partialBufferHelper.storeState();
        eigenBufferHelper.storeState();
        matrixBufferHelper.storeState();

        if (useScaleFactors) { // Only store when actually used
            storedUseScaleFactors = useScaleFactors;
            scaleBufferHelper.storeState();
            System.arraycopy(scaleBufferIndices, 0, storedScaleBufferIndices, 0, scaleBufferIndices.length);
        }

        super.storeState();

    }

    /**
     * Restore the additional stored state
     */
    protected void restoreState() {
        updateSiteModel = true; // this is required to upload the categoryRates to BEAGLE after the restore

        partialBufferHelper.restoreState();
        eigenBufferHelper.restoreState();
        matrixBufferHelper.restoreState();

        useScaleFactors = storedUseScaleFactors;
        if (useScaleFactors ) {
            scaleBufferHelper.restoreState();
            int[] tmp = storedScaleBufferIndices;
            storedScaleBufferIndices = scaleBufferIndices;
            scaleBufferIndices = tmp;
        }

        super.restoreState();

    }

    // **************************************************************
    // Likelihood IMPLEMENTATION
    // **************************************************************

    /**
     * Calculate the log likelihood of the current state.
     *
     * @return the log likelihood.
     */
    protected double calculateLogLikelihood() {

        if (patternLogLikelihoods == null) {
            patternLogLikelihoods = new double[patternCount];
        }

        if (matrixUpdateIndices == null) {
            matrixUpdateIndices = new int[nodeCount];
            branchLengths = new double[nodeCount];
            scaleBufferIndices = new int[internalNodeCount];
            storedScaleBufferIndices = new int[internalNodeCount];
        }

        if (operations == null) {
            operations = new int[internalNodeCount * Beagle.OPERATION_TUPLE_SIZE];
        }

        if (alwaysRescale) {
            useScaleFactors = true;
            recomputeScaleFactors = true;
        }

        branchUpdateCount = 0;
        operationCount = 0;

        final NodeRef root = treeModel.getRoot();
        traverse(treeModel, root, null, true);

        if (updateSubstitutionModel) {
            // we are currently assuming a homogeneous model...
            EigenDecomposition ed = branchSiteModel.getEigenDecomposition(0, 0);

            eigenBufferHelper.flipOffset(0);

            beagle.setEigenDecomposition(
                    eigenBufferHelper.getOffsetIndex(0),
                    ed.getEigenVectors(),
                    ed.getInverseEigenVectors(),
                    ed.getEigenValues());
        }

        if (updateSiteModel) {
            double[] categoryRates = this.siteRateModel.getCategoryRates();
            beagle.setCategoryRates(categoryRates);
        }

        beagle.updateTransitionMatrices(
                eigenBufferHelper.getOffsetIndex(0),
                matrixUpdateIndices,
                null,
                null,
                branchLengths,
                branchUpdateCount);

        if (COUNT_TOTAL_OPERATIONS)
               totalMatrixUpdateCount += branchUpdateCount;

        beagle.updatePartials(operations, operationCount, -1);

        if (COUNT_TOTAL_OPERATIONS) {
            totalOperationCount += operationCount;
        }

        int rootIndex = partialBufferHelper.getOffsetIndex(root.getNumber());

        double[] categoryWeights = this.siteRateModel.getCategoryProportions();
        double[] frequencies = branchSiteModel.getStateFrequencies(0);

        int cumulateScaleBufferIndex = Beagle.NONE;
        if (useScaleFactors) {

            if (alwaysRescale)
                scaleBufferHelper.flipOffset(internalNodeCount);

            cumulateScaleBufferIndex = scaleBufferHelper.getOffsetIndex(internalNodeCount);

            if (alwaysRescale) { // Only needs to be done if the scalings change
                beagle.resetScaleFactors(cumulateScaleBufferIndex);
                beagle.accumulateScaleFactors(scaleBufferIndices,internalNodeCount,cumulateScaleBufferIndex);
            }
        }

        beagle.calculateRootLogLikelihoods(new int[] { rootIndex }, categoryWeights, frequencies,
                new int[] { cumulateScaleBufferIndex }, 1, patternLogLikelihoods);

        double logL = 0.0;
        for (int i = 0; i < patternCount; i++) {
            logL += patternLogLikelihoods[i] * patternWeights[i];
        }

        if  (alwaysRescale && (Double.isNaN(logL) || Double.isInfinite(logL))) {
            logL = Double.NEGATIVE_INFINITY; // TODO Why is BEAGLE returning +\infty???
        }

        // Attempt dynamic rescaling if over/under-flow
        if ( !alwaysRescale && allowRescale && (Double.isNaN(logL) || Double.isInfinite(logL) ) ) {

            useScaleFactors = true;
            recomputeScaleFactors = true;

            // System.err.println("Potential under/over-flow; going to attempt a partials rescaling.");

            updateAllNodes();
            branchUpdateCount = 0;
            operationCount = 0;
            traverse(treeModel, root, null, false);

            if (COUNT_TOTAL_OPERATIONS) {
                totalOperationCount += operationCount;
            }

//            beagle.updateTransitionMatrices(    // Should not be necessary, will remove after debugging
//                     eigenBufferHelper.getOffsetIndex(0),
//                     matrixUpdateIndices,
//                     null,
//                     null,
//                     branchLengths,
//                     branchUpdateCount);

            beagle.updatePartials(operations, operationCount, -1);

            // flip cumulateScaleBuffer to hold new total
            scaleBufferHelper.flipOffset(internalNodeCount);
            cumulateScaleBufferIndex = scaleBufferHelper.getOffsetIndex(internalNodeCount);

            // accumulate all the scaling factors and store them in the additional 'root' buffer
            beagle.resetScaleFactors(cumulateScaleBufferIndex);
            beagle.accumulateScaleFactors(scaleBufferIndices, internalNodeCount, cumulateScaleBufferIndex);

            beagle.calculateRootLogLikelihoods(new int[] { rootIndex }, categoryWeights, frequencies,
                    new int[] { cumulateScaleBufferIndex }, 1, patternLogLikelihoods);

            logL = 0.0;
            for (int i = 0; i < patternCount; i++) {
                logL += patternLogLikelihoods[i] * patternWeights[i];
            }
            recomputeScaleFactors = false; // Only recompute after under/over-flow

            if (Double.isNaN(logL) || Double.isInfinite(logL)) {
                logL = Double.NEGATIVE_INFINITY;
                // TODO Discussion pt:
                // There are several reasons for poor calculation:
                // (1) a bug in BEAGLE (BAD)
                // (2) no rescaling is sufficient (BEAGLE should throw error), or
                // (3) BSSVS can return -\infty if the chain is not connected (should pass along -\infty)
            }
        }

        //********************************************************************
        // after traverse all nodes and patterns have been updated --
        //so change flags to reflect this.
        for (int i = 0; i < nodeCount; i++) {
            updateNode[i] = false;
        }

        updateSubstitutionModel = false;
        updateSiteModel = false;
        //********************************************************************

        return logL;
    }

    /**
     * Traverse the tree calculating partial likelihoods.
     */
    private boolean traverse(Tree tree, NodeRef node, int[] operatorNumber, boolean flip) {

        boolean update = false;

        int nodeNum = node.getNumber();

        NodeRef parent = tree.getParent(node);

        if (operatorNumber != null) {
            operatorNumber[0] = -1;
        }

        // First update the transition probability matrix(ices) for this branch
        if (parent != null && updateNode[nodeNum]) {

            final double branchRate = branchRateModel.getBranchRate(tree, node);

            // Get the operational time of the branch
            final double branchTime = branchRate * (tree.getNodeHeight(parent) - tree.getNodeHeight(node));

            if (branchTime < 0.0) {
                throw new RuntimeException("Negative branch length: " + branchTime);
            }

            if (flip)
                // first flip the matrixBufferHelper
                matrixBufferHelper.flipOffset(nodeNum);

            // then set which matrix to update
            matrixUpdateIndices[branchUpdateCount] = matrixBufferHelper.getOffsetIndex(nodeNum);

            branchLengths[branchUpdateCount] = branchTime;
            branchUpdateCount++;

            update = true;
        }

        // If the node is internal, update the partial likelihoods.
        if (!tree.isExternal(node)) {

            // Traverse down the two child nodes
            NodeRef child1 = tree.getChild(node, 0);
            final int[] op1 = { -1 };
            final boolean update1 = traverse(tree, child1, op1, flip);

            NodeRef child2 = tree.getChild(node, 1);
            final int[] op2 = { -1 };
            final boolean update2 = traverse(tree, child2, op2, flip);

            // If either child node was updated then update this node too
            if (update1 || update2) {

                int x = operationCount * Beagle.OPERATION_TUPLE_SIZE;

                if (flip)
                    // first flip the partialBufferHelper
                    partialBufferHelper.flipOffset(nodeNum);

                operations[x] = partialBufferHelper.getOffsetIndex(nodeNum);

                if (useScaleFactors) {
                    // get the index of this scaling buffer
                    int n = nodeNum - tipCount;

                    if (recomputeScaleFactors) {
                        // flip the indicator: can take either n or (internalNodeCount + 1) - n
                        scaleBufferHelper.flipOffset(n);
                        // store the index
                        scaleBufferIndices[n] = scaleBufferHelper.getOffsetIndex(n);

                        operations[x + 1] = scaleBufferIndices[n]; // Write new scaleFactor
                        operations[x + 2] = Beagle.NONE;
                    } else {
                        operations[x + 1] = Beagle.NONE;
                        operations[x + 2] = scaleBufferIndices[n]; // Read existing scaleFactor
                    }

                } else {
                    operations[x + 1] = Beagle.NONE; // Not using scaleFactors
                    operations[x + 2] = Beagle.NONE;
                }
                operations[x + 3] = partialBufferHelper.getOffsetIndex(child1.getNumber()); // source node 1
                operations[x + 4] = matrixBufferHelper.getOffsetIndex(child1.getNumber()); // source matrix 1
                operations[x + 5] = partialBufferHelper.getOffsetIndex(child2.getNumber()); // source node 2
                operations[x + 6] = matrixBufferHelper.getOffsetIndex(child2.getNumber()); // source matrix 2

                operationCount ++;

                update = true;
            }
        }

        return update;

    }

    // **************************************************************
    // INSTANCE VARIABLES
    // **************************************************************

    private int[] matrixUpdateIndices;
    private double[] branchLengths;
    private int branchUpdateCount;
    private int[] scaleBufferIndices;
    private int[] storedScaleBufferIndices;

    private int[] operations;
    private int operationCount;

    protected BufferIndexHelper partialBufferHelper;
    private final BufferIndexHelper eigenBufferHelper;
    protected BufferIndexHelper matrixBufferHelper;
    protected BufferIndexHelper scaleBufferHelper;

    protected final int tipCount;
    protected final int internalNodeCount;

    protected boolean useScaleFactors = false;
    private boolean recomputeScaleFactors = false;
    private boolean alwaysRescale = false;
    private boolean allowRescale = true;

    private boolean storedUseScaleFactors = false;

    /**
     * the branch-site model for these sites
     */
    protected final BranchSiteModel branchSiteModel;

    /**
     * the site model for these sites
     */
    protected final SiteRateModel siteRateModel;
    /**
     * the branch rate model
     */
    protected final BranchRateModel branchRateModel;

    /**
     * the pattern likelihoods
     */
    protected double[] patternLogLikelihoods = null;

    /**
     * the number of rate categories
     */
    protected int categoryCount;

    /**
     * an array used to transfer tip partials
     */
    protected double[] tipPartials;

    /**
     * the BEAGLE library instance
     */
    protected Beagle beagle;

    /**
     * Flag to specify that the substitution model has changed
     */
    protected boolean updateSubstitutionModel;
    protected boolean storedUpdateSubstitutionModel;

    /**
     * Flag to specify that the site model has changed
     */
    protected boolean updateSiteModel;
    protected boolean storedUpdateSiteModel;

//    /***
//     * Flag to specify if LikelihoodCore supports dynamic rescaling
//     */
//    private boolean dynamicRescaling = false;

    protected class BufferIndexHelper {
        /**
         *
         * @param maxIndexValue the number of possible input values for the index
         * @param minIndexValue the minimum index value to have the mirrored buffers
         */
        BufferIndexHelper(int maxIndexValue, int minIndexValue) {
            this.maxIndexValue = maxIndexValue;
            this.minIndexValue = minIndexValue;

            offsetCount = maxIndexValue - minIndexValue;
            indexOffsets = new int[offsetCount];
            storedIndexOffsets = new int[offsetCount];
        }

        public int getBufferCount() {
            return 2 * offsetCount + minIndexValue;
        }

        void flipOffset(int i) {
            if (i >= minIndexValue) {
                indexOffsets[i - minIndexValue] = offsetCount - indexOffsets[i - minIndexValue];
            } // else do nothing
        }

        int getOffsetIndex(int i) {
            if (i < minIndexValue) {
                return i;
            }
            return indexOffsets[i - minIndexValue] + i;
        }

        void getIndices(int[] outIndices) {
            for (int i = 0; i < maxIndexValue; i++) {
                outIndices[i] = getOffsetIndex(i);
            }
        }

        void storeState() {
            System.arraycopy(indexOffsets, 0, storedIndexOffsets, 0, indexOffsets.length);

        }

        void restoreState() {
            int[] tmp = storedIndexOffsets;
            storedIndexOffsets = indexOffsets;
            indexOffsets = tmp;
        }

        private final int maxIndexValue;
        private final int minIndexValue;
        private final int offsetCount;

        private int[] indexOffsets;
        private int[] storedIndexOffsets;

    }
}