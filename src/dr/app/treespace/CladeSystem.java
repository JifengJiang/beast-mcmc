package dr.app.treespace;

import dr.evolution.tree.*;
import dr.evolution.util.TaxonList;

import java.util.*;

/**
 * @author Andrew Rambaut
 * @version $Id$
 */
public class CladeSystem {
    /**
     */
    public CladeSystem() {
    }

    /**
     * adds all the clades in the tree
     */
    public void add(Tree tree, boolean includeTips) {
        if (taxonList == null) {
            taxonList = tree;
        }

        // Recurse over the tree and add all the clades (or increment their
        // frequency if already present). The root clade is added too (for
        // annotation purposes).
        addClades(tree, tree.getRoot(), includeTips);
    }

    private BitSet addClades(Tree tree, NodeRef node, boolean includeTips) {

        BitSet bits = new BitSet();

        if (tree.isExternal(node)) {

            int index = taxonList.getTaxonIndex(tree.getNodeTaxon(node).getId());
            bits.set(index);

            if (includeTips) {
                addClade(bits, taxonList.getTaxon(index).getId());
            }

        } else {

            for (int i = 0; i < tree.getChildCount(node); i++) {

                NodeRef node1 = tree.getChild(node, i);

                bits.or(addClades(tree, node1, includeTips));
            }

            addClade(bits);
        }

        return bits;
    }

    private void addClade(BitSet bits) {
        addClade(bits, null);
    }

    private void addClade(BitSet bits, String label) {
        Clade clade = cladeMap.get(bits);
        if (clade == null) {
            clade = new Clade(bits);
            cladeMap.put(bits, clade);
        }
        clade.setCount(clade.getCount() + 1);
        clade.label = label;
    }

    /**
     * adds all the clades in the tree
     */
    public void addCooccurances(Tree tree) {
        addCladeCooccurances(tree, tree.getRoot(), null);
    }

    private BitSet addCladeCooccurances(Tree tree, NodeRef node, Clade parent) {

        BitSet bits = getCladeBitset(tree, node);
        Clade clade = cladeMap.get(bits);

        if (!tree.isExternal(node)) {
            if (clade == null) {
                throw new IllegalArgumentException("Clade missing for cooccurance network");
            }

            for (int i = 0; i < tree.getChildCount(node); i++) {
                bits.or(addCladeCooccurances(tree, tree.getChild(node, i), clade));
            }
        }

        if (clade != null && parent != null) {
            if (clade.parents == null) {
                clade.parents = new HashMap<Clade, Integer>();
            }

            Integer frequency = clade.parents.get(parent);
            if (frequency == null) {
                frequency = 1;
            } else {
                frequency += 1;
            }
            clade.parents.put(parent, frequency);
        }

        return bits;
    }

    private BitSet getCladeBitset(Tree tree, NodeRef node) {

        BitSet bits = new BitSet();

        if (tree.isExternal(node)) {

            int index = taxonList.getTaxonIndex(tree.getNodeTaxon(node).getId());
            bits.set(index);

        } else {

            for (int i = 0; i < tree.getChildCount(node); i++) {
                bits.or(getCladeBitset(tree, tree.getChild(node, i)));
            }
        }

        return bits;
    }

    public Map<BitSet, Clade> getCladeMap() {
        return cladeMap;
    }

    public void normalizeClades(int totalTreesUsed) {
        int i = 0;
        for (Clade clade : cladeMap.values()) {

            if (clade.getCount() > totalTreesUsed) {

                throw new AssertionError("clade.getCount=(" + clade.getCount() +
                        ") should be <= totalTreesUsed = (" + totalTreesUsed + ")");
            }

            clade.setCredibility(((double) clade.getCount()) / (double) totalTreesUsed);
            clade.index = i;
            i++;
        }
    }


    public double getLogCladeCredibility(Tree tree, NodeRef node, BitSet bits) {

        double logCladeCredibility = 0.0;

        if (tree.isExternal(node)) {

            int index = taxonList.getTaxonIndex(tree.getNodeTaxon(node).getId());
            bits.set(index);
        } else {

            BitSet bits2 = new BitSet();
            for (int i = 0; i < tree.getChildCount(node); i++) {

                NodeRef node1 = tree.getChild(node, i);

                logCladeCredibility += getLogCladeCredibility(tree, node1, bits2);
            }

            logCladeCredibility += Math.log(getCladeCredibility(bits2));

            if (bits != null) {
                bits.or(bits2);
            }
        }

        return logCladeCredibility;
    }

    private double getCladeCredibility(BitSet bits) {
        Clade clade = cladeMap.get(bits);
        if (clade == null) {
            return 0.0;
        }
        return clade.getCredibility();
    }

    public BitSet removeClades(Tree tree, NodeRef node, boolean includeTips) {

        BitSet bits = new BitSet();

        if (tree.isExternal(node)) {

            int index = taxonList.getTaxonIndex(tree.getNodeTaxon(node).getId());
            bits.set(index);

            if (includeTips) {
                removeClade(bits);
            }

        } else {

            for (int i = 0; i < tree.getChildCount(node); i++) {

                NodeRef node1 = tree.getChild(node, i);

                bits.or(removeClades(tree, node1, includeTips));
            }

            removeClade(bits);
        }

        return bits;
    }

    private void removeClade(BitSet bits) {
        Clade clade = cladeMap.get(bits);
        if (clade != null) {
            clade.setCount(clade.getCount() - 1);
        }

    }

    public List<Clade> getClades() {
        return new ArrayList<Clade>(cladeMap.values());
    }

    class Clade {
        public Clade(BitSet bits) {
            this.bits = bits;
            count = 0;
            credibility = 0.0;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public double getCredibility() {
            return credibility;
        }

        public void setCredibility(double credibility) {
            this.credibility = credibility;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final Clade clade = (Clade) o;

            return !(bits != null ? !bits.equals(clade.bits) : clade.bits != null);

        }

        public int hashCode() {
            return (bits != null ? bits.hashCode() : 0);
        }

        public String toString() {
            return "clade " + bits.toString();
        }

        int count;
        double credibility;
        BitSet bits;
        String label;
        int index;
        Map<Clade, Integer> parents;
    }

    //
    // Private stuff
    //
    TaxonList taxonList = null;
    Map<BitSet, Clade> cladeMap = new HashMap<BitSet, Clade>();

    Tree targetTree;
}
