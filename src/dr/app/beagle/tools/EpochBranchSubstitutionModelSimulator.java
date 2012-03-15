package dr.app.beagle.tools;

import java.util.ArrayList;
import java.util.List;

import dr.app.beagle.evomodel.sitemodel.EpochBranchSubstitutionModel;
import dr.app.beagle.evomodel.sitemodel.GammaSiteRateModel;
import dr.app.beagle.evomodel.substmodel.FrequencyModel;
import dr.app.beagle.evomodel.substmodel.HKY;
import dr.app.beagle.evomodel.substmodel.SubstitutionModel;
import dr.app.seqgen.SequenceSimulator;
import dr.evolution.alignment.Alignment;
import dr.evolution.alignment.SimpleAlignment;
import dr.evolution.datatype.Nucleotides;
import dr.evolution.io.NewickImporter;
import dr.evolution.sequence.Sequence;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.evomodel.branchratemodel.DefaultBranchRateModel;
import dr.evomodel.sitemodel.SiteModel;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.math.MathUtils;
import dr.xml.AbstractXMLObjectParser;
import dr.xml.AttributeRule;
import dr.xml.ElementRule;
import dr.xml.XMLObject;
import dr.xml.XMLObjectParser;
import dr.xml.XMLParseException;
import dr.xml.XMLSyntaxRule;

/**
 * Class for performing random sequence generation for a given site model.
 * Sequences for the leave nodes in the tree are returned as an alignment.
 */

public class EpochBranchSubstitutionModelSimulator {

	/** nr of samples to generate **/
	private int nReplications;
	/** tree used for generating samples **/
	private Tree tree;
	/** site model used for generating samples **/
	private GammaSiteRateModel siteModel;
	/** branch rate model used for generating samples **/
	private BranchRateModel branchRateModel;
	/** nr of categories in site model **/
	private int categoryCount;
	/** nr of states in site model **/
	private int stateCount;

	private static boolean has_ancestralSequence = false;
	private Sequence ancestralSequence;

	/** an array used to transfer transition probabilities **/
	private double[][] m_probabilities;

	/**
	 * Constructor
	 * 
	 * @param tree
	 * @param siteModel
	 * @param branchRateModel
	 * @param sequenceLength: nr of sites to generate
	 */
	EpochBranchSubstitutionModelSimulator(Tree tree, List<SubstitutionModel> substModelList,
			List<FrequencyModel> frequencyModelList, Parameter epochTimes, int sequenceLength) {
		this.tree = tree;
		this.siteModel = siteModel;
		this.branchRateModel = branchRateModel;
		this.nReplications = sequenceLength;
		this.stateCount = siteModel.getSubstitutionModel().getDataType().getStateCount();
		this.categoryCount = siteModel.getCategoryCount();
		this.m_probabilities = new double[categoryCount][stateCount * stateCount];
	} // END: Constructor
	
	//suggestion: use this type of constructor
	EpochBranchSubstitutionModelSimulator(Tree tree, EpochBranchSubstitutionModel epochModel, GammaSiteRateModel siteModel, int sequenceLength) {
		this.tree = tree;
		this.siteModel = siteModel;
		this.nReplications = sequenceLength;
		//don't really need a siteModel to get the stateCount
		//too bad the siteModel only functions as a container with respect to the substitutionModel
		this.stateCount = epochModel.getSubstitutionModel(0,0).getDataType().getStateCount();
		//don't like this categoryCount though, does require a siteModel
		this.categoryCount = siteModel.getCategoryCount();
		this.m_probabilities = new double[categoryCount][stateCount * stateCount];
	} //end suggestion

	/**
	 * Convert integer representation of sequence into a Sequence
	 * 
	 * @param seq: integer representation of the sequence
	 * @param node: used to determine taxon for sequence
	 * @return Sequence
	 */
	Sequence intArray2Sequence(int[] seq, NodeRef node) {
		
		StringBuilder sSeq = new StringBuilder();
		
		for (int i = 0; i < nReplications; i++) {
			sSeq.append(siteModel.getSubstitutionModel().getDataType().getCode(seq[i]));
		}
		
		return new Sequence(tree.getNodeTaxon(node), sSeq.toString());
	} // intArray2Sequence

	void setAncestralSequence(Sequence seq) {
		ancestralSequence = seq;
		has_ancestralSequence = true;
	}

	int[] sequence2intArray(Sequence seq) {

		if (seq.getLength() != nReplications) {
			throw new RuntimeException("Ancestral sequence length has "
					+ seq.getLength() + " characters " + "expecting "
					+ nReplications + " characters");
		}

		int array[] = new int[nReplications];
		for (int i = 0; i < nReplications; i++) {
			array[i] = siteModel.getSubstitutionModel().getDataType().getState(
					seq.getChar(i));
		}
		
		return array;
	}// END: sequence2intArray

	/**
	 * perform the actual sequence generation
	 * 
	 * @return alignment containing randomly generated sequences for the nodes
	 *         in the leaves of the tree
	 */
	public Alignment simulate() {

		NodeRef root = tree.getRoot();

		double[] categoryProbs = siteModel.getCategoryProportions();
		int[] category = new int[nReplications];
		for (int i = 0; i < nReplications; i++) {
			category[i] = MathUtils.randomChoicePDF(categoryProbs);
		}

		int[] seq = new int[nReplications];

		if (has_ancestralSequence) {

			seq = sequence2intArray(ancestralSequence);

		} else {

			FrequencyModel frequencyModel = siteModel.getSubstitutionModel()
					.getFrequencyModel();
			for (int i = 0; i < nReplications; i++) {
				seq[i] = MathUtils.randomChoicePDF(frequencyModel
						.getFrequencies());
			}

		}// END: ancestral sequence check

		SimpleAlignment alignment = new SimpleAlignment();
		alignment.setReportCountStatistics(true);
		alignment.setDataType(siteModel.getSubstitutionModel().getDataType());

		traverse(root, seq, category, alignment);

		return alignment;
	} // END: simulate

	// TODO: traverse for Epoch model
	void traverse(NodeRef node, int[] parentSequence, int[] category,
			SimpleAlignment alignment) {

		for (int iChild = 0; iChild < tree.getChildCount(node); iChild++) {

			NodeRef child = tree.getChild(node, iChild);
			for (int i = 0; i < categoryCount; i++) {
				getTransitionProbabilities(tree, child, i, m_probabilities[i]);
			}

			int[] seq = new int[nReplications];
			double[] cProb = new double[stateCount];
			for (int i = 0; i < nReplications; i++) {
				System.arraycopy(m_probabilities[category[i]],
						parentSequence[i] * stateCount, cProb, 0, stateCount);
				seq[i] = MathUtils.randomChoicePDF(cProb);
			}

			if (tree.getChildCount(child) == 0) {
				alignment.addSequence(intArray2Sequence(seq, child));
			}
			traverse(tree.getChild(node, iChild), seq, category, alignment);
		}
	} // END: traverse

	void getTransitionProbabilities(Tree tree, NodeRef node, int rateCategory,
			double[] probs) {

		NodeRef parent = tree.getParent(node);

		final double branchRate = branchRateModel.getBranchRate(tree, node);

		// Get the operational time of the branch
		final double branchTime = branchRate
				* (tree.getNodeHeight(parent) - tree.getNodeHeight(node));

		if (branchTime < 0.0) {
			throw new RuntimeException("Negative branch length: " + branchTime);
		}

		double branchLength = siteModel.getRateForCategory(rateCategory)
				* branchTime;

//		if (siteModel.getSubstitutionModel() instanceof SubstitutionEpochModel) {
//			((SubstitutionEpochModel) siteModel.getSubstitutionModel())
//					.getTransitionProbabilities(tree.getNodeHeight(node), tree
//							.getNodeHeight(parent), branchLength, probs);
//			return;
//		}

		siteModel.getSubstitutionModel().getTransitionProbabilities(
				branchLength, probs);
	} // END: getTransitionProbabilities

	/** generate simple site model, for testing purposes **/
	static GammaSiteRateModel getDefaultGammaSiteRateModel() {
		
		List<FrequencyModel> frequencyModelList = new ArrayList<FrequencyModel>();
		List<SubstitutionModel> substModelList = new ArrayList<SubstitutionModel>();
		Parameter epochTransitionTimes = new Parameter.Default(1, 20);
		
		Parameter freqs = new Parameter.Default(new double[] { 0.25, 0.25,
				0.25, 0.25 });
		FrequencyModel freqModel = new FrequencyModel(Nucleotides.INSTANCE, freqs);
		
		Parameter kappa1 = new Parameter.Default(1, 1);
		Parameter kappa2 = new Parameter.Default(10, 1);
		
		HKY hky1 = new HKY(kappa1, freqModel);
		HKY hky2 = new HKY(kappa2, freqModel);
		
		substModelList.add((SubstitutionModel) hky1);
		substModelList.add((SubstitutionModel) hky2);
		
		frequencyModelList.add(freqModel);
		
		EpochBranchSubstitutionModel beagleSubstitutionEpochModel =	new EpochBranchSubstitutionModel(substModelList, frequencyModelList, epochTransitionTimes);
		
		Model model = (Model)beagleSubstitutionEpochModel;
		
		//TODO: pass epoch model to site model
		GammaSiteRateModel gsrm = new GammaSiteRateModel("dupa");
		gsrm.setSubstitutionModel(hky1);

		return gsrm;
	} // END: getDefaultGammaSiteRateModel
	
	public static final String EPOCH_SEQUENCE_SIMULATOR = "epochSequenceSimulator";
    public static final String SITE_MODEL = SiteModel.SITE_MODEL;
    public static final String TREE = "tree";
    public static final String EPOCH_BRANCH_SUBSTITUTION_MODEL = "epochBranchSubstitutionModel";
    public static final String REPLICATIONS = "replications";

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        public String getParserName() {
            return EPOCH_SEQUENCE_SIMULATOR;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

        	int nReplications = xo.getIntegerAttribute(REPLICATIONS);

            Tree tree = (Tree) xo.getChild(Tree.class);
            GammaSiteRateModel siteModel = (GammaSiteRateModel) xo.getChild(SiteModel.class);
            EpochBranchSubstitutionModel epochModel = (EpochBranchSubstitutionModel)xo.getChild(EpochBranchSubstitutionModel.class);
            
            Sequence ancestralSequence = (Sequence)xo.getChild(Sequence.class);
            
            EpochBranchSubstitutionModelSimulator s = new EpochBranchSubstitutionModelSimulator(tree, epochModel, siteModel, nReplications);

            if(ancestralSequence != null) {
                s.setAncestralSequence(ancestralSequence);
            }
            
            return s.simulate();
        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return "An EpochSequenceSimulator that generates random sequences for a given tree, sitemodel and epoch substitution model";
        }

        public Class getReturnType() {
            return Alignment.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private XMLSyntaxRule[] rules = new XMLSyntaxRule[]{
                new ElementRule(Tree.class),
                new ElementRule(SiteModel.class),
                new ElementRule(EpochBranchSubstitutionModel.class, true),
                new ElementRule(Sequence.class, true),
                AttributeRule.newIntegerRule(REPLICATIONS)
        };
    };

	public static void main(String[] args) {

		try {

			int nReplications = 10;

			// create tree
			NewickImporter importer = new NewickImporter(
					"((A:1.0,B:1.0)AB:1.0,(C:1.0,D:1.0)CD:1.0)ABCD;");
			Tree tree = importer.importTree(null);

			// create site model
			GammaSiteRateModel siteModel = getDefaultGammaSiteRateModel();

			// create branch rate model
			BranchRateModel branchRateModel = new DefaultBranchRateModel();

			// feed to sequence simulator and generate leaves
//			EpochBranchSubstitutionModelSimulator treeSimulator = new EpochBranchSubstitutionModelSimulator(
//					tree, siteModel, branchRateModel, nReplications);

			Sequence ancestralSequence = new Sequence();
			ancestralSequence.appendSequenceString("TCAGGTCAAG");
//			treeSimulator.setAncestralSequence(ancestralSequence);

//			System.out.println(treeSimulator.simulate().toString());

		} catch (Exception e) {
			e.printStackTrace();
		}// END: try-catch block

	} // END: main

} // class SequenceSimulator
