/*
 * BeautiFrame.java
 *
 * (c) 2002-2005 BEAST Development Core Team
 *
 * This package may be distributed under the
 * Lesser Gnu Public Licence (LGPL)
 */
package dr.app.beauti;

import dr.app.beauti.ancestralStatesPanel.AncestralStatesOptionsPanel;
import dr.app.beauti.ancestralStatesPanel.AncestralStatesPanel;
import dr.app.beauti.clockModelsPanel.OldClockModelsPanel;
import dr.app.beauti.components.ComponentFactory;
import dr.app.beauti.components.ancestralstates.AncestralStatesComponentFactory;
import dr.app.beauti.components.continuous.ContinuousComponentFactory;
import dr.app.beauti.components.discrete.DiscreteTraitsComponentFactory;
import dr.app.beauti.components.dollo.DolloComponentFactory;
import dr.app.beauti.components.hpm.HierarchicalModelComponentFactory;
import dr.app.beauti.components.sequenceerror.SequenceErrorModelComponentFactory;
import dr.app.beauti.components.tipdatesampling.TipDateSamplingComponentFactory;
import dr.app.beauti.datapanel.DataPanel;
import dr.app.beauti.generator.BeastGenerator;
import dr.app.beauti.generator.Generator;
import dr.app.beauti.mcmcpanel.MCMCPanel;
import dr.app.beauti.operatorspanel.OperatorsPanel;
import dr.app.beauti.options.BeautiOptions;
import dr.app.beauti.options.PartitionTreePrior;
import dr.app.beauti.options.STARBEASTOptions;
import dr.app.beauti.options.TraitData;
import dr.app.beauti.priorsPanel.DefaultPriorDialog;
import dr.app.beauti.priorsPanel.PriorsPanel;
import dr.app.beauti.siteModelsPanel.SiteModelsPanel;
import dr.app.beauti.taxonsetspanel.SpeciesSetPanel;
import dr.app.beauti.taxonsetspanel.TaxonSetPanel;
import dr.app.beauti.tipdatepanel.TipDatesPanel;
import dr.app.beauti.traitspanel.TraitsPanel;
import dr.app.beauti.treespanel.TreesPanel;
import dr.app.beauti.util.BEAUTiImporter;
import dr.app.beauti.util.TextUtil;
import dr.app.gui.FileDrop;
import dr.app.util.OSType;
import dr.app.util.Utils;
import dr.evolution.io.Importer.ImportException;
import dr.evolution.io.NexusImporter.MissingBlockException;
import jam.framework.DocumentFrame;
import jam.framework.Exportable;
import jam.util.IconUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.BorderUIResource;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyBoundsListener;
import java.awt.event.HierarchyEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @version $Id: BeautiFrame.java,v 1.22 2006/09/09 16:07:06 rambaut Exp $
 */
public class BeautiFrame extends DocumentFrame {

    private static final boolean ENABLE_ANCESTRAL_STATES = true;

    private static final long serialVersionUID = 2114148696789612509L;

    public final static String DATA_PARTITIONS = "Partitions";
    public final static String TAXON_SETS = "Taxa";
    public final static String TIP_DATES = "Tips";
    public final static String TRAITS = "Traits";
    public final static String SITE_MODELS = "Sites";
    public final static String CLOCK_MODELS = "Clocks";
    public final static String TREES = "Trees";
    public final static String ANCESTRAL_STATES = "States";
    public final static String PRIORS = "Priors";
    public final static String OPERATORS = "Operators";
    public final static String MCMC = "MCMC";

    private final BeautiOptions options;
    private final BeastGenerator generator;

    public final JTabbedPane tabbedPane = new JTabbedPane();
    public final JLabel statusLabel = new JLabel();

    private DataPanel dataPanel;
    private TipDatesPanel tipDatesPanel;
    private TraitsPanel traitsPanel;
    private TaxonSetPanel taxonSetPanel;
    private SpeciesSetPanel speciesSetPanel;
    private SiteModelsPanel siteModelsPanel;
    private AncestralStatesPanel ancestralStatesPanel;
    private OldClockModelsPanel clockModelsPanel;
    private TreesPanel treesPanel;
    private PriorsPanel priorsPanel;
    private OperatorsPanel operatorsPanel;
    private MCMCPanel mcmcPanel;

    private BeautiPanel currentPanel;

    private JFileChooser importChooser; // make JFileChooser chooser remember previous path
    private JFileChooser exportChooser; // make JFileChooser chooser remember previous path

    final Icon gearIcon = IconUtils.getIcon(this.getClass(), "images/gear.png");

    public BeautiFrame(String title) {
        super();

        setTitle(title);

        // Prevent the application to close in requestClose()
        // after a user cancel or a failure in beast file generation
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        getOpenAction().setEnabled(false);
        getSaveAction().setEnabled(false);

        getFindAction().setEnabled(false);

        getZoomWindowAction().setEnabled(false);

        ComponentFactory[] components = {
                AncestralStatesComponentFactory.INSTANCE,
                ContinuousComponentFactory.INSTANCE,
                DiscreteTraitsComponentFactory.INSTANCE,
//                DnDsComponentFactory.INSTANCE,
                DolloComponentFactory.INSTANCE,
                HierarchicalModelComponentFactory.INSTANCE,
                SequenceErrorModelComponentFactory.INSTANCE,
                TipDateSamplingComponentFactory.INSTANCE
        };

        options = new BeautiOptions(components);
        generator = new BeastGenerator(options, components);

        this.getContentPane().addHierarchyBoundsListener(new HierarchyBoundsListener() {
            public void ancestorMoved(HierarchyEvent e) {
            }

            public void ancestorResized(HierarchyEvent e) {
                setStatusMessage();
            }
        });
    }

    public void initializeComponents() {

        dataPanel = new DataPanel(this, getImportAction(), getDeleteAction()/*, getImportTraitsAction()*/);
        tipDatesPanel = new TipDatesPanel(this);
        traitsPanel = new TraitsPanel(this, dataPanel, getImportTraitsAction());
        taxonSetPanel = new TaxonSetPanel(this);
        speciesSetPanel = new SpeciesSetPanel(this);
        siteModelsPanel = new SiteModelsPanel(this, getDeleteAction());
        ancestralStatesPanel = new AncestralStatesPanel(this);
        clockModelsPanel = new OldClockModelsPanel(this);
//        oldTreesPanel = new OldTreesPanel(this);
        treesPanel = new TreesPanel(this, getDeleteAction());
//        speciesTreesPanel = new SpeciesTreesPanel(this);
        priorsPanel = new PriorsPanel(this, false);
        operatorsPanel = new OperatorsPanel(this);
        mcmcPanel = new MCMCPanel(this);

        tabbedPane.addTab(DATA_PARTITIONS, dataPanel);
        tabbedPane.setToolTipTextAt(0, "<html>" +
                "Import sequence alignments, organize data partitions,<br>" +
                "link models between partitions and select *BEAST</html>");

        tabbedPane.addTab(TAXON_SETS, taxonSetPanel);
        tabbedPane.setToolTipTextAt(1, "<html>" +
                "Create and edit sets of taxa which can be used to <br>" +
                "define times of most recent common ancestors and <br>" +
                "to keep groups monophyletic.</html>");
//        tabbedPane.addTab("Species Sets", speciesSetPanel);
        tabbedPane.addTab(TIP_DATES, tipDatesPanel);
        tabbedPane.setToolTipTextAt(2, "<html>" +
                "Specify sampling dates of tips for use in temporal <br>" +
                "analyses of measurably evolving populations.</html>");
        tabbedPane.addTab(TRAITS, traitsPanel);
        tabbedPane.setToolTipTextAt(3, "<html>" +
                "Import and organize continuous and discrete traits <br>" +
                "for taxa, convert them into data partitions for evolutionary<br>" +
                "analysis.</html>");
        tabbedPane.addTab(SITE_MODELS, siteModelsPanel);
        tabbedPane.setToolTipTextAt(4, "<html>" +
                "Select evolutionary models to be used for each data <br>" +
                "partition including substitution models, codon partitioning<br>" +
                "and trait evolution models.</html>");
        tabbedPane.addTab(CLOCK_MODELS, clockModelsPanel);
        tabbedPane.setToolTipTextAt(5, "<html>" +
                "Select relaxed molecular clock models to be used across <br>" +
                "the tree. Specify sampling of rates.</html>");
        tabbedPane.addTab(TREES, treesPanel);
        tabbedPane.setToolTipTextAt(6, "<html>" +
                "Select the priors on trees including coalescent models<br>" +
                "birth-death speciation models and the *BEAST gene tree,<br>" +
                "species tree options.</html>");
       if (ENABLE_ANCESTRAL_STATES) {
            tabbedPane.addTab(ANCESTRAL_STATES, ancestralStatesPanel);
           tabbedPane.setToolTipTextAt(7, "<html>" +
                   "Select options for sampling ancestral states at specific<br>" +
                   "or all common ancestors and models of sequencing error<br>" +
                   "for data partitions.</html>");
        }
        tabbedPane.addTab(PRIORS, priorsPanel);
        tabbedPane.setToolTipTextAt(8, "<html>" +
                "Specify prior probability distributions on each and every<br>" +
                "parameter of the current model.</html>");
        tabbedPane.addTab(OPERATORS, operatorsPanel);
        tabbedPane.setToolTipTextAt(9, "<html>" +
                "Select and adjust the menu of operators that will be used<br>" +
                "to propose changes to the parameters. Switch off operators<br>" +
                "on certain parameters to fix them to initial values.</html>");
        tabbedPane.addTab(MCMC, mcmcPanel);
        tabbedPane.setToolTipTextAt(10, "<html>" +
                "Specify the details of MCMC sampling. This includes chain<br>" +
                "length, sampling frequencies, log file names and more.</html>");

        for (int i = 1; i < tabbedPane.getTabCount(); i++) {
            tabbedPane.setEnabledAt(i, false);
        }

        currentPanel = (BeautiPanel) tabbedPane.getSelectedComponent();

        tabbedPane.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                BeautiPanel selectedPanel = (BeautiPanel) tabbedPane.getSelectedComponent();
                if (selectedPanel == dataPanel) {
                    dataPanel.selectionChanged();
                } else {
                    getDeleteAction().setEnabled(false);
                }
                currentPanel.getOptions(options);
                setAllOptions();
                currentPanel = selectedPanel;
            }
        });

        JPanel basePanel = new JPanel(new BorderLayout(6, 6));
        basePanel.setBorder(new BorderUIResource.EmptyBorderUIResource(new java.awt.Insets(12, 12, 12, 12)));
//        basePanel.setPreferredSize(new java.awt.Dimension(800, 600));

        getExportAction().setEnabled(false);
        JButton generateButton = new JButton(getExportAction());
        generateButton.putClientProperty("JButton.buttonType", "roundRect");

        JPanel panel2 = new JPanel(new BorderLayout(6, 6));
        panel2.add(statusLabel, BorderLayout.WEST);
        panel2.add(generateButton, BorderLayout.EAST);
        panel2.setMinimumSize(new java.awt.Dimension(10, 10));

        basePanel.add(tabbedPane, BorderLayout.CENTER);
        basePanel.add(panel2, BorderLayout.SOUTH);

        add(basePanel, BorderLayout.CENTER);

        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension d = tk.getScreenSize();
        System.out.println("Screen width = " + d.width);
        System.out.println("Screen height = " + d.height);

        if (d.width < 1000 || d.height < 700) {
            setSize(new java.awt.Dimension(700, 550));
        } else {
            setSize(new java.awt.Dimension(1024, 768));
        }

        if (OSType.isMac()) {
            setMinimumSize(new java.awt.Dimension(640, 480));
        }

        setAllOptions();

        // make JFileChooser chooser remember previous path
        exportChooser = new JFileChooser(Utils.getCWD());
        exportChooser.setFileFilter(new FileNameExtensionFilter("BEAST XML File", "xml", "beast"));
        exportChooser.setDialogTitle("Generate BEAST XML File...");


        importChooser = new JFileChooser(Utils.getCWD());

        importChooser.setMultiSelectionEnabled(true);
        importChooser.setFileFilter(new FileNameExtensionFilter(
                "Microsatellite (tab-delimited *.txt) Files", "txt"));
        importChooser.setFileFilter(new FileNameExtensionFilter(
                "NEXUS (*.nex) & BEAST (*.xml) Files", "nex", "nexus", "nx", "xml", "beast", "fa", "fasta", "afa"));
        importChooser.setDialogTitle("Import Aligment...");

        Color focusColor = UIManager.getColor("Focus.color");
        Border focusBorder = BorderFactory.createMatteBorder(2, 2, 2, 2, focusColor);
        dataPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        new FileDrop(null, dataPanel, focusBorder, new FileDrop.Listener() {
            public void filesDropped(java.io.File[] files) {
                importFiles(files);
            }   // end filesDropped
        }); // end FileDrop.Listener


    }

    /**
     * set all the options for all panels
     */
    public void setAllOptions() {
        try {
            dataPanel.setOptions(options);
            tipDatesPanel.setOptions(options);
            traitsPanel.setOptions(options);
            if (options.useStarBEAST) {
                speciesSetPanel.setOptions(options);
            } else {
                taxonSetPanel.setOptions(options);
            }
            siteModelsPanel.setOptions(options);
            clockModelsPanel.setOptions(options);
            treesPanel.setOptions(options);
            if (ENABLE_ANCESTRAL_STATES) {
                ancestralStatesPanel.setOptions(options);
            }
            priorsPanel.setOptions(options);
            operatorsPanel.setOptions(options);
            mcmcPanel.setOptions(options);

            setStatusMessage();
        } catch (IllegalArgumentException illegEx) {
            JOptionPane.showMessageDialog(this, illegEx.getMessage(),
                    "Illegal Argument Exception", JOptionPane.ERROR_MESSAGE);
        }

        // enable/disable the other tabs and generate option depending on whether any
        // data has been loaded.
        boolean enabled = options.getDataPartitions().size() > 0;
        for (int i = 1; i < tabbedPane.getTabCount(); i++) {
            tabbedPane.setEnabledAt(i, enabled);
        }
        getExportAction().setEnabled(enabled);
    }

    /**
     * get all the options for all panels
     */
    private void getAllOptions() {
        try {
            dataPanel.getOptions(options);
            tipDatesPanel.getOptions(options);
            traitsPanel.getOptions(options);
            if (options.useStarBEAST) {
                speciesSetPanel.getOptions(options);
            } else {
                taxonSetPanel.getOptions(options);
            }
            siteModelsPanel.getOptions(options);
            clockModelsPanel.getOptions(options);
            treesPanel.getOptions(options);
            if (ENABLE_ANCESTRAL_STATES) {
                ancestralStatesPanel.getOptions(options);
            }
            priorsPanel.getOptions(options);
            operatorsPanel.getOptions(options);
            mcmcPanel.getOptions(options);
        } catch (IllegalArgumentException illegEx) {
            JOptionPane.showMessageDialog(this, illegEx.getMessage(),
                    "Illegal Argument Exception", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void doSelectAll() {
        if (currentPanel == dataPanel) {
            dataPanel.selectAll();
        }
    }

    public final void dataSelectionChanged(boolean isSelected) {
        getDeleteAction().setEnabled(isSelected);
    }

    public final void modelSelectionChanged(boolean isSelected) {
        getDeleteAction().setEnabled(isSelected);
    }

    public void doDelete() {
        if (tabbedPane.getSelectedComponent() == dataPanel) {
            dataPanel.removeSelection();
//        } else if (tabbedPane.getSelectedComponent() == modelsPanel) {
//            modelsPanel.delete();
//        } else if (tabbedPane.getSelectedComponent() == treesPanel) {
//        	treesPanel.delete();
        } else {
            throw new RuntimeException("Delete should only be accessable from the Data and Models panels");
        }

        setStatusMessage();
    }

    public boolean requestClose() {
        if (isDirty() && options.hasData()) {
            int option = JOptionPane.showConfirmDialog(this,
                    "You have made changes but have not generated\n" +
                            "a BEAST XML file. Do you wish to generate\n" +
                            "before closing this window?",
                    "Unused changes",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (option == JOptionPane.YES_OPTION) {
                return doGenerate();
            } else if (option == JOptionPane.CANCEL_OPTION || option == JOptionPane.DEFAULT_OPTION) {
                return false;
            }
            return true;
        }
        return true;
    }

    public void doApplyTemplate() {
        FileDialog dialog = new FileDialog(this,
                "Apply Template",
                FileDialog.LOAD);
        dialog.setVisible(true);
        if (dialog.getFile() != null) {
            File file = new File(dialog.getDirectory(), dialog.getFile());
            try {
                readFromFile(file);
            } catch (FileNotFoundException fnfe) {
                JOptionPane.showMessageDialog(this, "Unable to open template file: File not found",
                        "Unable to open file",
                        JOptionPane.ERROR_MESSAGE);
            } catch (IOException ioe) {
                JOptionPane.showMessageDialog(this, "Unable to read template file: " + ioe.getMessage(),
                        "Unable to read file",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    protected boolean readFromFile(File file) throws IOException {
        return false;
    }

    public String getDefaultFileName() {
        return options.fileNameStem + ".beauti";
    }

    protected boolean writeToFile(File file) throws IOException {
        return false;
    }

    public final void doImport() {
        int returnVal = importChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File[] files = importChooser.getSelectedFiles();
            importFiles(files);
            tabbedPane.setSelectedComponent(dataPanel);
        }
    }

    private void importFiles(File[] files) {
        for (File file : files) {
            if (file == null || file.getName().equals("")) {
                JOptionPane.showMessageDialog(this, "Invalid file name",
                        "Invalid file name", JOptionPane.ERROR_MESSAGE);
            } else {
                try {
                    BEAUTiImporter beautiImporter = new BEAUTiImporter(this, options);
                    beautiImporter.importFromFile(file);

                    setDirty();
//                    } catch (FileNotFoundException fnfe) {
//                        JOptionPane.showMessageDialog(this, "Unable to open file: File not found",
//                                "Unable to open file", JOptionPane.ERROR_MESSAGE);
                } catch (IOException ioe) {
                    JOptionPane.showMessageDialog(this, "File I/O Error unable to read file: " + ioe.getMessage(),
                            "Unable to read file", JOptionPane.ERROR_MESSAGE);
                    ioe.printStackTrace();
                    // there may be other files in the list so don't return
//                    return;

                } catch (MissingBlockException ex) {
                    JOptionPane.showMessageDialog(this, "TAXON, DATA or CHARACTERS block is missing in Nexus file: " + ex,
                            "Missing Block in Nexus File",
                            JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();

                } catch (ImportException ime) {
                    JOptionPane.showMessageDialog(this, "Error parsing imported file: " + ime,
                            "Error reading file",
                            JOptionPane.ERROR_MESSAGE);
                    ime.printStackTrace();
                    // there may be other files in the list so don't return
//                    return;
                } catch (IllegalArgumentException illegEx) {
                    JOptionPane.showMessageDialog(this, illegEx.getMessage(),
                            "Illegal Argument Exception", JOptionPane.ERROR_MESSAGE);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Fatal exception: " + ex,
                            "Error reading file",
                            JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                    return;
                }
            }
        }

        if (options.hasDiffTaxa(options.dataPartitions)) {
            dataPanel.selectAll();
            dataPanel.unlinkTrees();
        }

        setAllOptions();

    }

    public final boolean doImportTraits() {
        if (options.taxonList != null) { // validation of check empty taxonList
            FileDialog dialog = new FileDialog(this,
                    "Import Traits File...",
                    FileDialog.LOAD);

            dialog.setVisible(true);
            if (dialog.getFile() != null) {
                final File file = new File(dialog.getDirectory(), dialog.getFile());

                try {
                    BEAUTiImporter beautiImporter = new BEAUTiImporter(this, options);
                    beautiImporter.importTraits(file);
                } catch (FileNotFoundException fnfe) {
                    JOptionPane.showMessageDialog(this, "Unable to open file: File not found",
                            "Unable to open file",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                } catch (IOException ioe) {
                    JOptionPane.showMessageDialog(this, "Unable to read file: " + ioe.getMessage(),
                            "Unable to read file",
                            JOptionPane.ERROR_MESSAGE);
                    return false;
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Fatal exception: " + ex,
                            "Error reading file",
                            JOptionPane.ERROR_MESSAGE);
                    ex.printStackTrace();
                    return false;
                }
            } else {
                return false;
            }

            traitsPanel.fireTraitsChanged();
            setAllOptions();

            tabbedPane.setSelectedComponent(traitsPanel);
            return true;

        } else {
            JOptionPane.showMessageDialog(this, "No taxa loaded yet, please import Alignment file.",
                    "No taxa loaded", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    public boolean validateTraitName(String traitName) {
        // check that the name is valid
        if (traitName.trim().length() == 0) {
            Toolkit.getDefaultToolkit().beep();
            return false;
        }

        // disallow a trait called 'date'
        if (traitName.equalsIgnoreCase("date")) {
            JOptionPane.showMessageDialog(this,
                    "This trait name has a special meaning. Use the 'Tip Date' panel\n" +
                            " to set dates for taxa.",
                    "Reserved trait name",
                    JOptionPane.WARNING_MESSAGE);

            return false;
        }

        if (options.useStarBEAST && traitName.equalsIgnoreCase(TraitData.TRAIT_SPECIES)) {
            JOptionPane.showMessageDialog(this,
                    "This trait name is already in used to denote species\n" +
                            "for *BEAST. Please select a different name.",
                    "Reserved trait name",
                    JOptionPane.WARNING_MESSAGE);

            return false;
        }

        // check that the trait name doesn't exist
        if (options.traitExists(traitName)) {
            int option = JOptionPane.showConfirmDialog(this,
                    "A trait of this name already exists. Do you wish to replace\n" +
                            "it with this new trait? This may result in the loss or change\n" +
                            "in trait values for the taxa.",
                    "Overwrite trait?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);

            if (option == JOptionPane.NO_OPTION) {
                return false;
            }
        }

        return true;
    }

    public void setupStarBEAST(boolean useStarBEAST) {
        if (useStarBEAST) {
            dataPanel.selectAll();
            dataPanel.unlinkAll();

            options.starBEASTOptions = new STARBEASTOptions(options);
            options.fileNameStem = "StarBEASTLog";

            if (!options.traitExists(TraitData.TRAIT_SPECIES)) {
                if (!traitsPanel.addTrait(
                        "<html><p>" +
                                "StarBEAST requires a trait to give species designations<br>" +
                                "for each taxon. Create or import a discrete trait<br>" +
                                "labelled 'species'.</p></html>",
                        TraitData.TRAIT_SPECIES,
                        true /* isSpeciesTrait */
                )) {
                    dataPanel.useStarBEASTCheck.setSelected(false); // go back to unchecked
                    useStarBEAST = false;
                }
            } else if (options.getDataPartitions(options.getTrait(TraitData.TRAIT_SPECIES)).size() > 0) {
                int option = JOptionPane.showConfirmDialog(this,
                        "The trait named '" + TraitData.TRAIT_SPECIES + "', used to denote species in *BEAST, is\n" +
                                "already in use as a data partition. Do you wish to continue?",
                        "Species trait already in use",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (option == JOptionPane.NO_OPTION) {
                    return;
                }

            }

            tabbedPane.removeTabAt(1);
            tabbedPane.insertTab("Species Sets", null, speciesSetPanel, null, 1);

        } else { // remove species
            // why delete this? The user may want to use it again
            // because it is how *BEAST reverse to normal BEAST, otherwise after uncheck, everything goes wrong.

            // AR - it is a fundamental point of UI design that we don't delete the user's work.
            // If the user had hand typed all the species designations in and then just switched *BEAST
            // off for a moment and lost all that work they would be annoyed. We need to work out what
            // why turning off *BEAST doesn't fully work.

//            if (options.traitExists(TraitData.TRAIT_SPECIES)) {
//                traitsPanel.removeTrait(TraitData.TRAIT_SPECIES);
            options.fileNameStem = MCMCPanel.fileNameStem;
//            }

            tabbedPane.removeTabAt(1);
            tabbedPane.insertTab("Taxon Sets", null, taxonSetPanel, null, 1);
        }

        options.useStarBEAST = useStarBEAST;

        treesPanel.updatePriorPanelForSpeciesAnalysis();

        setStatusMessage();
    }

    public void updateDiscreteTraitAnalysis() {
        setStatusMessage();
    }

    public void setupEBSP() {
        dataPanel.selectAll();

        dataPanel.unlinkAll();

        setAllOptions();
    }

    public PartitionTreePrior getCurrentPartitionTreePrior() {
        treesPanel.setOptions(options); // need this to refresh the currentTreeModel
        return treesPanel.currentTreeModel.getPartitionTreePrior();
    }

    public void setStatusMessage() {
        int width = this.getWidth() - 260; // minus generate button size
        if (width < 100) width = 100; // prevent too narrow
        String tw = TextUtil.wrapText(options.statusMessage(), statusLabel, width);
//        System.out.println(this.getWidth() + "   " + tw);
        statusLabel.setText(tw);
    }

    public final boolean doGenerate() {

        try {
            generator.checkOptions();
        } catch (Generator.GeneratorException ge) {
            JOptionPane.showMessageDialog(this, ge.getMessage(), "Invalid BEAUti setting : ",
                    JOptionPane.ERROR_MESSAGE);
            if (ge.getSwitchToPanel() != null) {
                for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                    if (tabbedPane.getTitleAt(i).equals(ge.getSwitchToPanel())) {
                        tabbedPane.setSelectedIndex(i);
                        break;
                    }
                }
            }
            return false;
        }

        DefaultPriorDialog defaultPriorDialog = new DefaultPriorDialog(this);
        if (!defaultPriorDialog.showDialog(options)) {
            return false;
        }

        // offer stem as default
        exportChooser.setSelectedFile(new File(options.fileNameStem + ".xml"));

        final int returnVal = exportChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = exportChooser.getSelectedFile();

            int n = JOptionPane.YES_OPTION;

            if (file.exists()) {
                n = JOptionPane.showConfirmDialog(this, file.getName(),
                        "Overwrite the existing file?", JOptionPane.YES_NO_OPTION);
            }

            if (n == JOptionPane.YES_OPTION) {
                try {
                    getAllOptions();
                    generator.generateXML(file);

                } catch (IOException ioe) {
                    JOptionPane.showMessageDialog(this, "Unable to generate file due to I/O issue: " + ioe.getMessage(),
                            "Unable to generate file", JOptionPane.ERROR_MESSAGE);
                    return false;
                } catch (Generator.GeneratorException e) {
                    JOptionPane.showMessageDialog(this, "The BEAST XML is incomplete because :\n" + e.getMessage(),
                            "The BEAST XML is incomplete", JOptionPane.ERROR_MESSAGE);
                    return false;
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Unable to generate file: " + e.getMessage(),
                            "Unable to generate file", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            } else {
                doGenerate();
            }
        }

        clearDirty();
        return true;
    }

    public JComponent getExportableComponent() {

        JComponent exportable = null;
        Component comp = tabbedPane.getSelectedComponent();

        if (comp instanceof Exportable) {
            exportable = ((Exportable) comp).getExportableComponent();
        } else if (comp instanceof JComponent) {
            exportable = (JComponent) comp;
        }

        return exportable;
    }

    public boolean doSave() {
        return doSaveAs();
    }

    public boolean doSaveAs() {
        FileDialog dialog = new FileDialog(this,
                "Save Template As...",
                FileDialog.SAVE);

        dialog.setVisible(true);
        if (dialog.getFile() == null) {
            // the dialog was cancelled...
            return false;
        }

        File file = new File(dialog.getDirectory(), dialog.getFile());

        try {
            if (writeToFile(file)) {

                clearDirty();
            }
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(this, "Unable to save file: " + ioe,
                    "Unable to save file",
                    JOptionPane.ERROR_MESSAGE);
        }

        return true;
    }

    public Action getOpenAction() {
        return openTemplateAction;
    }

    private final AbstractAction openTemplateAction = new AbstractAction("Apply Template...") {
        private static final long serialVersionUID = 2450459627280385426L;

        public void actionPerformed(ActionEvent ae) {
            doApplyTemplate();
        }
    };

    public Action getSaveAction() {
        return saveAsAction;
    }

    public Action getSaveAsAction() {
        return saveAsAction;
    }

    private final AbstractAction saveAsAction = new AbstractAction("Save Template As...") {
        private static final long serialVersionUID = 2424923366448459342L;

        public void actionPerformed(ActionEvent ae) {
            doSaveAs();
        }
    };

    public Action getImportAction() {
        return importAlignmentAction;
    }

    protected AbstractAction importAlignmentAction = new AbstractAction("Import Data...") {
        private static final long serialVersionUID = 3217702096314745005L;

        public void actionPerformed(java.awt.event.ActionEvent ae) {
            doImport();
        }
    };

    public Action getImportTraitsAction() {
        return importTraitsAction;
    }

    protected AbstractAction importTraitsAction = new AbstractAction("Import Traits") {
        private static final long serialVersionUID = 3217702096314745005L;

        public void actionPerformed(java.awt.event.ActionEvent ae) {
            doImportTraits();
        }
    };

    public Action getExportAction() {
        return generateAction;
    }

    protected AbstractAction generateAction = new AbstractAction("Generate BEAST File...", gearIcon) {
        private static final long serialVersionUID = -5329102618630268783L;

        public void actionPerformed(java.awt.event.ActionEvent ae) {
            doGenerate();
        }
    };

}
