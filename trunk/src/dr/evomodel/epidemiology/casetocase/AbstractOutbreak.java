package dr.evomodel.epidemiology.casetocase;

import dr.evolution.alignment.PatternList;
import dr.evolution.datatype.DataType;
import dr.evolution.datatype.GeneralDataType;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evolution.util.TaxonList;
import dr.inference.model.AbstractModel;

import java.util.*;

/**
 * Abstract class for outbreaks. Implements PatternList for ease of compatibility with AbstractTreeLikelihood, but there
 * is one and only one pattern.
 *
 * User: Matthew Hall
 * Date: 14/04/13
 */

public abstract class AbstractOutbreak extends AbstractModel implements PatternList {

    protected GeneralDataType caseDataType;
    protected TaxonList taxa;
    protected boolean hasLatentPeriods = false;
    protected boolean hasGeography;
    private final String CASE_NAME = "caseID";
    protected ArrayList<AbstractCase> cases;

    public AbstractOutbreak(String name, Taxa taxa){
        this(name, taxa, false);
    }

    public AbstractOutbreak(String name, Taxa taxa, boolean hasLatentPeriods){
        super(name);
        this.taxa = taxa;
        ArrayList<String> caseNames = new ArrayList<String>();
        for(int i=0; i<taxa.getTaxonCount(); i++){
            caseNames.add((String)taxa.getTaxonAttribute(i, CASE_NAME));
        }
        caseDataType = new GeneralDataType(caseNames);
        this.hasLatentPeriods = hasLatentPeriods;
    }

    public ArrayList<AbstractCase> getCases(){
        return new ArrayList<AbstractCase>(cases);
    }

    public boolean hasLatentPeriods(){
        return hasLatentPeriods;
    }

    public boolean hasGeography(){
        return hasGeography;
    }


    public double getKernelValue(AbstractCase a, AbstractCase b, SpatialKernel kernel){
        if(!hasGeography){
            return 1;
        } else {
            return kernel.value(getDistance(a,b));
        }
    }

    public double getKernelValue(AbstractCase a, AbstractCase b, SpatialKernel kernel, double alpha){
        if(!hasGeography){
            return 1;
        } else {
            return kernel.value(getDistance(a,b), alpha);
        }
    }

    public int getCaseIndex(AbstractCase thisCase){
        return cases.indexOf(thisCase);
    }

    public int size(){
        return cases.size();
    }

    public abstract double getDistance(AbstractCase a, AbstractCase b);

    public AbstractCase getCase(int i){
        return cases.get(i);
    }

    public AbstractCase getCase(String name){
        for(AbstractCase thisCase: cases){
            if(thisCase.getName().equals(name)){
                return thisCase;
            }
        }
        return null;
    }

    public TaxonList getTaxa(){
        return taxa;
    }

    public abstract double probXInfectedByYAtTimeT(AbstractCase X, AbstractCase Y, double T);

    public abstract double logProbXInfectedByYAtTimeT(AbstractCase X, AbstractCase Y, double T);

    public abstract double probXInfectedByYBetweenTandU(AbstractCase X, AbstractCase Y, double T, double U);

    public abstract double logProbXInfectedByYBetweenTandU(AbstractCase X, AbstractCase Y, double T, double U);

    public abstract double probXInfectiousByTimeT(AbstractCase X, double T);

    public abstract double logProbXInfectiousByTimeT(AbstractCase X, double T);

    public abstract double probXInfectedAtTimeT(AbstractCase X, double T);

    public abstract double logProbXInfectedAtTimeT(AbstractCase X, double T);

    public abstract double probXInfectedBetweenTandU(AbstractCase X, double T, double U);

    public abstract double logProbXInfectedBetweenTandU(AbstractCase X, double T, double U);


    //************************************************************************
    // PatternList implementation
    //************************************************************************

    // not considering the possibility that we are simultaneously reconstructing more than one transmission tree!

    public int getPatternCount(){
        return 1;
    }

    public int getStateCount(){
        return size();
    }

    public int getPatternLength(){
        return taxa.getTaxonCount();
    }

    // with an exact correspondence between taxa and states, the following five methods are ill-fitting, but here if
    // needed.
    // @todo if these are never going to be used, get them to throw exceptions

    public int[] getPattern(int patternIndex){
        int[] out = new int[cases.size()];
        for(int i=0; i<cases.size(); i++){
            out[i] = i;
        }
        return out;
    }

    public int getPatternState(int taxonIndex, int patternIndex){
        return taxonIndex;
    }

    public double getPatternWeight(int patternIndex){
        return 1;
    }

    public double[] getPatternWeights(){
        return new double[]{1};
    }

    public double[] getStateFrequencies(){
        double[] out = new double[cases.size()];
        Arrays.fill(out, 1/cases.size());
        return out;
    }

    public DataType getDataType(){
        return caseDataType;
    }

    //************************************************************************
    // TaxonList implementation
    //************************************************************************

    public int getTaxonCount(){
        return taxa.getTaxonCount();
    }

    public Taxon getTaxon(int taxonIndex){
        return taxa.getTaxon(taxonIndex);
    }

    public String getTaxonId(int taxonIndex){
        return taxa.getTaxonId(taxonIndex);
    }

    public int getTaxonIndex(String id){
        return taxa.getTaxonIndex(id);
    }

    public int getTaxonIndex(Taxon taxon){
        return taxa.getTaxonIndex(taxon);
    }

    public List<Taxon> asList(){
        return taxa.asList();
    }

    public Object getTaxonAttribute(int taxonIndex, String name){
        return taxa.getTaxonAttribute(taxonIndex, name);
    }

    public Iterator<Taxon> iterator() {
        if (taxa == null) throw new RuntimeException("Patterns has no TaxonList");
        return taxa.iterator();
    }

}
