/*
 * DataType.java
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

package dr.evolution.datatype;

import java.util.*;

/**
 * Base class for sequence data types.
 *
 * @version $Id: DataType.java,v 1.13 2005/05/24 20:25:56 rambaut Exp $
 *
 * @author Andrew Rambaut
 * @author Alexei Drummond
 */
public abstract class DataType
{
    public static final String DATA_TYPE = "dataType";


    public static final int NUCLEOTIDES = 0;
    public static final int AMINO_ACIDS = 1;
    public static final int CODONS = 2;
    public static final int TWO_STATES = 3;
    public static final int GENERAL = 4;
    public static final int COVARION = 5;

    public static final char UNKNOWN_CHARACTER = '?';
    public static final char GAP_CHARACTER = '-';

    protected int stateCount;
    protected int ambiguousStateCount;

    // this map contains all dataTypes in the class loader that have added themselves
    static private Map<String, DataType> registeredDataTypes = null;

    /**
     * Due to some unpleasant interactions between static initializations in the
     * different classes, I have changed this to a lazy initialization.
     */
    private static void lazyRegisterDataTypes() {
        if (registeredDataTypes == null) {
            registeredDataTypes = new Hashtable<String, DataType>();
            registerDataType(Nucleotides.DESCRIPTION, Nucleotides.INSTANCE);
            registerDataType(AminoAcids.DESCRIPTION, AminoAcids.INSTANCE);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.UNIVERSAL.getName(), Codons.UNIVERSAL);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.VERTEBRATE_MT.getName(), Codons.VERTEBRATE_MT);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.YEAST.getName(), Codons.YEAST);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.MOLD_PROTOZOAN_MT.getName(), Codons.MOLD_PROTOZOAN_MT);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.MYCOPLASMA.getName(), Codons.MYCOPLASMA);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.INVERTEBRATE_MT.getName(), Codons.INVERTEBRATE_MT);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.CILIATE.getName(), Codons.CILIATE);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.ECHINODERM_MT.getName(), Codons.ECHINODERM_MT);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.EUPLOTID_NUC.getName(), Codons.EUPLOTID_NUC);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.BACTERIAL.getName(), Codons.BACTERIAL);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.ALT_YEAST.getName(), Codons.ALT_YEAST);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.ASCIDIAN_MT.getName(), Codons.ASCIDIAN_MT);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.FLATWORM_MT.getName(), Codons.FLATWORM_MT);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.BLEPHARISMA_NUC.getName(), Codons.BLEPHARISMA_NUC);
            registerDataType(Codons.DESCRIPTION+"-"+GeneticCode.NO_STOPS.getName(), Codons.NO_STOPS);
            registerDataType(TwoStates.DESCRIPTION, TwoStates.INSTANCE);
            registerDataType(HiddenNucleotides.DESCRIPTION, HiddenNucleotides.INSTANCE);
            registerDataType(TwoStateCovarion.DESCRIPTION, TwoStateCovarion.INSTANCE);
	        registerDataType(HiddenCodons.DESCRIPTION+"2-"+ GeneticCode.UNIVERSAL.getName(), HiddenCodons.UNIVERSAL_HIDDEN_2);
	        registerDataType(HiddenCodons.DESCRIPTION+"3-"+ GeneticCode.UNIVERSAL.getName(), HiddenCodons.UNIVERSAL_HIDDEN_3);
            registerDataType(GeneralDataType.DESCRIPTION, GeneralDataType.INSTANCE);
        }
    }


    /**
     * Registers a data type with a (hopefully unique) name.
     * @param name
     * @param dataType
     */
    public static void registerDataType(String name, DataType dataType) {
        lazyRegisterDataTypes();
        registeredDataTypes.put(name,dataType);

    }

    /**
     * @param name the name that the datatype was registered under
     * @return the datatype with the given name
     */
    public static DataType getRegisteredDataTypeByName(String name) {
        lazyRegisterDataTypes();
        return registeredDataTypes.get(name);
    }

    public static String[] getRegisteredDataTypeNames() {
        lazyRegisterDataTypes();
        Set<String> set = registeredDataTypes.keySet();
        List<String> keys = new ArrayList<String>(set);
        String[] names = new String[keys.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = keys.get(i);
        }

        return names;
    }

    /**
     * guess data type suitable for a given sequence
     *
     * @param sequence a string of symbols representing a molecular sequence of unknown data type.
     *
     * @return suitable DataType object
     */
    public static DataType guessDataType(String sequence)
    {
        // count A, C, G, T, U, N
        long numNucs = 0;
        long numChars = 0;
        long numBins = 0;
        for (int i = 0; i < sequence.length(); i++)
        {
            char c = sequence.charAt(i);
            int s = Nucleotides.INSTANCE.getState(c);

            if (s != Nucleotides.UNKNOWN_STATE && s != Nucleotides.GAP_STATE) {
                numNucs++;
            }

            if (c != '-' && c != '?') numChars++;

            if (c == '0' || c == '1') numBins++;
        }

        if (numChars == 0) { numChars = 1; }

        // more than 85 % frequency advocates nucleotide data
        if ((double) numNucs / (double) numChars > 0.85) {
            return Nucleotides.INSTANCE;
        } else if ((double) numBins / (double) numChars > 0.2) {
            return TwoStates.INSTANCE;
        } else {
            return AminoAcids.INSTANCE;
        }
    }

    /**
     * Get number of unique states
     *
     * @return number of unique states
     */
    public int getStateCount() {
        return stateCount;
    }

    /**
     * Get number of states including ambiguous states
     *
     * @return number of ambiguous states
     */
    public int getAmbiguousStateCount() {
        return ambiguousStateCount;
    }

    /**
     * Get state corresponding to a character
     *
     * @param code state code
     *
     * @return state
     */
    public int getState(String code) {
        return getState(code.charAt(0));
    }

    /**
     * Get state corresponding to a character
     *
     * @param c character
     *
     * @return state
     */
    public int getState(char c) {
        return (int)c - 'A';
    }

    /**
     * Get state corresponding to an unknown
     *
     * @return state
     */
    public int getUnknownState() {
        return stateCount;
    }

    /**
     * Get state corresponding to a gap
     *
     * @return state
     */
    public int getGapState() {
        return stateCount + 1;
    }

    /**
     * Get character corresponding to a given state
     *
     * @param state state
     *
     * return corresponding character
     */
    public char getChar(int state) {
        return (char)(state + 'A');
    }

    /**
     * Get a string code corresponding to a given state. By default this
     * calls getChar but overriding classes may return multicharacter codes.
     *
     * @param state state
     *
     * return corresponding code
     */
    public String getCode(int state) {
        return String.valueOf(getChar(state));
    }

    /**
     * Get triplet string corresponding to a given state
     *
     * @param state state
     *
     * return corresponding triplet string
     */
    public String getTriplet(int state)
    {
        return " " + getChar(state) + " ";
    }

    /**
     * returns an array containing the non-ambiguous states that this state represents.
     */
    public int[] getStates(int state) {

        int[] states;
        if (!isAmbiguousState(state)) {
            states = new int[1];
            states[0] = state;
        } else {
            states = new int[stateCount];
            for (int i = 0; i < stateCount; i++) {
                states[i] = i;
            }
        }

        return states;
    }

    /**
     * returns an array containing the non-ambiguous states that this state represents.
     */
    public boolean[] getStateSet(int state) {

        boolean[] stateSet = new boolean[stateCount];
        if (!isAmbiguousState(state)) {
            for (int i = 0; i < stateCount; i++) {
                stateSet[i] = false;
            }

            stateSet[state] = true;
        } else {
            for (int i = 0; i < stateCount; i++) {
                stateSet[i] = true;
            }
        }

        return stateSet;
    }

    /**
     * returns the uncorrected distance between two states
     */
    public double getObservedDistance(int state1, int state2)
    {
        if (!isAmbiguousState(state1) && !isAmbiguousState(state2) && state1 != state2) {
            return 1.0;
        }

        return 0.0;
    }

    /**
     * returns the uncorrected distance between two states with full
     * treatment of ambiguity.
     */
    public double getObservedDistanceWithAmbiguity(int state1, int state2)
    {
        boolean[] stateSet1 = getStateSet(state1);
        boolean[] stateSet2 = getStateSet(state2);

        double sumMatch = 0.0;
        double sum1 = 0.0;
        double sum2 = 0.0;
        for (int i = 0; i < stateCount; i++) {
            if (stateSet1[i]) {
                sum1 += 1.0;
                if (stateSet1[i] == stateSet2[i]) {
                    sumMatch += 1.0;
                }
            }
            if (stateSet2[i]) {
                sum2 += 1.0;
            }
        }

        return (1.0 - (sumMatch / (sum1 * sum2)));
    }

    public String toString() {
        return getDescription();
    }

    /**
     * description of data type
     *
     * @return string describing the data type
     */
    public abstract String getDescription();

    /**
     * type of data type
     *
     * @return integer code for the data type
     */
    public abstract int getType();

    /**
     * @return true if this character is an ambiguous state
     */
    public boolean isAmbiguousChar(char c) {
        return isAmbiguousState(getState(c));
    }

    /**
     * @return true if this character is a gap
     */
    public boolean isUnknownChar(char c) {
        return isUnknownState(getState(c));
    }

    /**
     * @return true if this character is a gap
     */
    public boolean isGapChar(char c) {
        return isGapState(getState(c));
    }

    /**
     * returns true if this state is an ambiguous state.
     */
    public boolean isAmbiguousState(int state) {
        return (state >= stateCount);
    }

    /**
     * @return true if this state is an unknown state
     */
    public boolean isUnknownState(int state) {
        return (state == getUnknownState());
    }

    /**
     * @return true if this state is a gap
     */
    public boolean isGapState(int state) {
        return (state == getGapState());
    }

}