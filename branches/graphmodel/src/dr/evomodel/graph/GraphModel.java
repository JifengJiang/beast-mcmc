package dr.evomodel.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evomodel.tree.TreeModel;
import dr.evomodel.tree.TreeModel.Node;
import dr.inference.model.CompoundParameter;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.math.MathUtils;

/*
 * A class to represent phylogenetic graphs where each node can have
 * up to two ancestors
 */
public class GraphModel extends TreeModel {

    public static final String GRAPH_MODEL = "graphModel";
	LinkedList<Integer> freeNodes = new LinkedList<Integer>();	// a list of nodes for which storage exists 
	ArrayList<Integer> activeNodeNumbers = new ArrayList<Integer>();
	
    public GraphModel(Tree tree, PartitionModel partitionModel) {
        this(tree, false, partitionModel);
	}

	public GraphModel(String id, Tree tree, PartitionModel partitionModel) {
        this(tree, false, partitionModel);
        setId(id);
	}

    /* 
     * Creates a new GraphModel
     */
   public GraphModel(Tree tree, boolean copyAttributes, PartitionModel partitionModel) 
   {
	   // use the superconstructor but then convert all nodes
	   // from tree nodes to graph nodes
       super(tree);
       Node[] tmp = new Node[nodes.length];
       Node[] tmp2 = new Node[nodes.length];
       for(int i=0; i<tmp.length; i++)
       {
    	   tmp[i] = new Node();
    	   tmp2[i] = new Node();
    	   tmp[i].number = i;
    	   tmp2[i].number = i;
       }
       super.copyNodeStructure(tmp);
       for(int i=0; i<tmp.length; i++)
       {
    	   tmp[i].taxon = nodes[i].taxon;
       }
       nodes = storedNodes;
       super.copyNodeStructure(tmp2);
       for(int i=0; i<tmp.length; i++)
       {
    	   tmp2[i].taxon = nodes[i].taxon;
       }
       nodes = tmp;
       storedNodes = tmp2;
       root = nodes[root.number];
       
       // add node numbers to active nodes
       for(int i=0; i<tmp.length; i++)
       {
    	   activeNodeNumbers.add(i);
       }
       
       // attach all partitions in the PartitionModel to each node
       for(int sr = 0; sr < partitionModel.getPartitionCount(); sr++){
           Partition range = partitionModel.getPartition(sr);
           for(int i=0; i<nodes.length; i++)
           {
        	   ((Node)nodes[i]).addObject(0, range);
           }
       }
       setupGraphHeightBounds();
   }

   // do nothing -- this overrides tree behavior
   public void setupHeightBounds() {}
   public void setupGraphHeightBounds() {

       for (int i = 0; i < nodeCount; i++) {
           ((GraphModel.Node)nodes[i]).setupHeightBounds();
       }
   }
   
   public enum NodeType {
	   LEAF, VERTICAL, RECOMBINANT
   }
   /**
    * Return array of leaf, recombinant, or vertical nodes
    * @param type   0 for leaf, 1 for vertical, 2 for recombinant
    * @return
    */
   public NodeRef[] getNodesByType(NodeType type){
	   ArrayList<NodeRef> a = new ArrayList<NodeRef>(nodes.length);
	   for(int i=0; i<nodes.length; i++){
		   if(nodes[i].parent!=null&&((GraphModel.Node)nodes[i]).parent2!=null)
		   {
			   if(type==NodeType.RECOMBINANT)a.add(nodes[i]);
		   }else if(nodes[i].leftChild==null&&nodes[i].rightChild==null){
			   if(type==NodeType.LEAF)a.add(nodes[i]);
		   }else
			   if(type==NodeType.VERTICAL)a.add(nodes[i]);
	   }
	   NodeRef[] b = new NodeRef[a.size()];
	   
	   return a.toArray(b);
   }
   
   /*
    * Add a new, unlinked node to the graph
    */
   public NodeRef newNode() {
       if (!inEdit) throw new RuntimeException("Must be in edit transaction to call this method!");
       if(freeNodes.size()==0){
	       // need to expand storage to accommodate additional nodes
	       TreeModel.Node[] tmp = new TreeModel.Node[nodes.length*2];
	       TreeModel.Node[] tmp2 = new TreeModel.Node[storedNodes.length*2];
	       System.arraycopy(nodes, 0, tmp, 0, nodes.length);
	       System.arraycopy(storedNodes, 0, tmp2, 0, storedNodes.length);
	       for(int i=nodes.length; i<tmp.length; i++)
	       {
	    	   tmp[i] = new Node();
	    	   tmp[i].setNumber(i);
	           tmp[i].heightParameter = new Parameter.Default(1.0);
	           tmp[i].heightParameter.setId("" + i);
	           addVariable(tmp[i].heightParameter);
	           tmp[i].setupHeightBounds();
	    	   freeNodes.addFirst(tmp[i].getNumber());
	    	   storedFreeNodes.addFirst(tmp[i].getNumber()); // this is generic storage unrelated to logical state, so add it to the backup state also
	       }
	       for(int i=storedNodes.length; i<tmp2.length; i++)
	       {
	    	   tmp2[i] = new Node();
	    	   tmp2[i].setNumber(i);
	           tmp2[i].heightParameter = tmp[i].heightParameter;
	       }
	       nodes = tmp;
	       storedNodes = tmp2;
       }

       // simply return a node from the free list
       Node newNode = (GraphModel.Node)nodes[freeNodes.removeLast()];
       internalNodeCount++;	// assume this is an internal node.  might not be true if there are partitions with subsets of taxa
       nodeCount++;
       activeNodeNumbers.add(newNode.getNumber());
       pushTreeChangedEvent(newNode);	// push a changed event onto the stack

       // add height, rate, and trait parameters
       modifyExportedParameters(newNode, true);
       nodeChanges.add(newNode.getNumber());	// record addition of node
       return newNode;
   }
   
   public boolean isRecombination(NodeRef node){
	   return !isBifurcation(node);
   }
   
   public boolean isBifurcation(NodeRef node){
	   Node mynode = (Node)node;
	   
	   if(mynode.parent==null || mynode.parent2==null){
		   return true;
	   }
	   return false;
   }
   
   /**
    * index = 0 left parent, index = 1 right parent
    */
   public NodeRef getParent(NodeRef child, int index){
	   if(index == 0){
		   return ((Node)child).parent;
	   }
	   
	   return ((Node)child).parent2;
   }

   /*
    * remove an unlinked node from the graph
    * @param node The node to remove
    */
   public void deleteNode(NodeRef node) {
       if (!inEdit) throw new RuntimeException("Must be in edit transaction to call this method!");
       Node n = (Node) node;
       if(!n.hasNoChildren()||n.parent!=null||n.parent2!=null){
    	   throw new RuntimeException("Deleted node is linked to others!");
       }
       freeNodes.push(n.getNumber());
       internalNodeCount--;
       nodeCount--;
       // remove from list of active nodes
       for(int i=0; i<activeNodeNumbers.size(); i++){
    	   if(activeNodeNumbers.get(i)==n.getNumber()){
    		   activeNodeNumbers.remove(i);
    		   break;
    	   }
       }
       
       // remove from height, rate, and trait parameters
       modifyExportedParameters(n, false);
       nodeChanges.add(-n.getNumber());	// record removal of node

       pushTreeChangedEvent(n);	// push a changed event onto the stack
   }
   
   public NodeRef getNode(int i) {
       return nodes[activeNodeNumbers.get(i)];
   }

   public void removePartition(NodeRef node, Partition range)
   {
       if (!inEdit) throw new RuntimeException("Must be in edit transaction to call this method!");
	   Node n = (Node)node;
	   if(n.hasObject(0,range)) 
		   n.removeObject(0, range);
	   else if(n.hasObject(1,range)) 
		   n.removeObject(0, range);
	   else
		   throw new RuntimeException("Error, removing a nonexistant partition!");
   }
   public void removePartitionFollowToRoot(NodeRef node, Partition range)
   {
       if (!inEdit) throw new RuntimeException("Must be in edit transaction to call this method!");
	   // walk from node to root removing a site range
	   Node n = (Node)node;
	   while(n!=null){
		   if(!n.hasObject(0,range) && !n.hasObject(1, range)){
			   throw new RuntimeException("Error, removing a nonexistant partition!");
		   }
		   int i = n.hasObject(0, range) ? 0 : 1;
		   n.removeObject(i, range);
		   n = i == 0 ? (GraphModel.Node)n.parent : n.parent2;
	   }
   }
   
   int storedINC = -1;
   int storedNC = -1;
   LinkedList<Integer> storedFreeNodes = new LinkedList<Integer>();
   ArrayList<Integer> storedActiveNodeNumbers = new ArrayList<Integer>();
   LinkedList<Integer> nodeChanges = new LinkedList<Integer>();
   protected void storeState() {
	   super.storeState();
	   nodeChanges.clear();
	   storedINC = internalNodeCount;
	   storedNC = nodeCount;
	   equalizeLists(freeNodes, storedFreeNodes);
	   storedActiveNodeNumbers.clear();
	   storedActiveNodeNumbers.addAll(activeNodeNumbers);
   }
   protected void restoreState() {
	   super.restoreState();
	   internalNodeCount = storedINC;
	   nodeCount = storedNC;
	   equalizeLists(storedFreeNodes, freeNodes);
	   ArrayList<Integer> tmp = activeNodeNumbers;
	   activeNodeNumbers = storedActiveNodeNumbers;
	   storedActiveNodeNumbers = tmp;
	   // undo each allocation or deallocation
	   while(nodeChanges.size()>0){
		   Integer ii = nodeChanges.removeLast();
		   if(ii<0){
			   modifyExportedParameters((GraphModel.Node)nodes[-ii],true);
		   }else
			   modifyExportedParameters((GraphModel.Node)nodes[ii],false);
	   }
   }

   private void modifyExportedParameters(Node n, boolean add){
       if(nhp!=null){ 
    	   for(CompoundParameter cp : nhp){
    		   if(add){
    			   cp.addParameter(n.heightParameter);
    		   }else{
    			   cp.removeParameter(n.heightParameter);
    		   }
    	   }
       }
       if(nrp!=null&&n.rateParameter!=null&&add) nrp.addParameter(n.rateParameter);
       if(nrp!=null&&n.rateParameter!=null&&!add) nrp.removeParameter(n.rateParameter);
       if(ntp!=null) {
           for (Map.Entry<String, Parameter> entry : n.getTraitMap().entrySet()) {
    		   if(add){
    			   ntp.addParameter(entry.getValue());
    		   }else{
    			   ntp.removeParameter(entry.getValue());
    		   }
           }
       }
   }

   static private void equalizeLists(LinkedList<Integer> src, LinkedList<Integer> dest){
	   if(dest.size()<src.size()){
		   dest.addAll(src.subList(dest.size(), src.size()));
	   }
	   while(dest.size()>src.size()){
		   dest.removeLast();
	   }
   }

   public void addPartition(NodeRef node, int edge, Partition range)
   {
       if (!inEdit) throw new RuntimeException("Must be in edit transaction to call this method!");
	   // walk from node to root removing a site range
	   Node n = (Node)node;
	   n.addObject(edge, range);
	   pushTreeChangedEvent(n);
   }

   public void addPartitionUntilCoalescence(NodeRef node, Partition partition)
   {
	   Node n = (Node)node;
	   while(n!=null){
		   if(n.hasObject(0,partition)||n.hasObject(1, partition))
			   break;
		   if(n.parent != null && n.parent2 != null )
		   {
			   boolean b = MathUtils.nextBoolean();
			   n.addObject(b ? 0 : 1,partition);
			   n = b ? (Node)n.parent : n.parent2;
		   }else{
			   n = (Node)n.parent;
			   n.addObject(0,partition);
		   }
	   }
   }

   public void addChild(NodeRef p, NodeRef c) {
       if (!inEdit) throw new RuntimeException("Must be in edit transaction to call this method!");
       Node parent = (Node) p;
       Node child = (Node) c;
       parent.addChild(child);
       pushTreeChangedEvent(parent);
   }
   
   /**
    * Uses TreeModel to copy the data, then links up the second parent
    */
   public void copyNodeStructure(TreeModel.Node[] destination) {
	   super.copyNodeStructure(destination);
	   if(destination.length>0 && !(destination[0] instanceof GraphModel.Node))
		   return;	// these are really TreeModel.Node.  get the hell outta Dodge!

       for (int i = 0, n = nodes.length; i < n; i++) {
           Node node0 = (GraphModel.Node)nodes[i];
           Node node1 = (GraphModel.Node)destination[i];
           if (node0.parent2 != null) {
               node1.parent2 = (GraphModel.Node)destination[node0.parent2.getNumber()];
           } else {
               node1.parent2 = null;
           }
       }
   }

   public void removeChild(NodeRef p, NodeRef c) {
       if (!inEdit) throw new RuntimeException("Must be in edit transaction to call this method!");
       ((GraphModel.Node)p).removeChild((GraphModel.Node)c);
   }


   ArrayList<CompoundParameter> nhp = new ArrayList<CompoundParameter>();
   CompoundParameter nrp = null, ntp = null;
   public Parameter createNodeHeightsParameter(boolean rootNode, boolean internalNodes, boolean leafNodes) {	   
	   CompoundParameter tmp = (CompoundParameter)super.createNodeHeightsParameter(rootNode, internalNodes, leafNodes);
	   if(internalNodes) nhp.add(tmp);
	   return tmp;
   }
   public Parameter createNodeRatesParameter(double[] initialValues, boolean rootNode, boolean internalNodes, boolean leafNodes) {
	   nrp = (CompoundParameter)super.createNodeRatesParameter(initialValues, rootNode, internalNodes, leafNodes);
	   return nrp;
   }
   public Parameter createNodeTraitsParameter(String name, int dim, double[] initialValues,
           boolean rootNode, boolean internalNodes,
           boolean leafNodes, boolean firesTreeEvents) {
	   ntp = (CompoundParameter)createNodeTraitsParameter(name, dim, initialValues, rootNode, internalNodes, leafNodes, firesTreeEvents);
	   return ntp;
   }
   
   
   protected void handleModelChangedEvent(Model model, Object object, int index) 
   {
       // presumably a constituent partition has changed
   }
   
   /**
    * Converts all graph links to a string
    * @return a string
    */
   public String linkDump(){
	   StringBuilder sb = new StringBuilder();
	   for(int i=0; i<nodes.length; i++)
		   sb.append(((GraphModel.Node)nodes[i]).linksToString() + "\n");
	   return sb.toString();
   }

    // **************************************************************
    // Private inner classes
    // **************************************************************
   
   	public class Node extends TreeModel.Node {

    	// a treeNode will have parent2 == null
    	// an argNode will have rightNode == null

    	public Node parent2 = null;	// an extra parent for recombinant nodes

    	// TODO: make store/restore friendly
    	protected HashSet<Object> objects0;	// arbitrary objects tied to this node.
    	protected HashSet<Object> objects1;	// arbitrary objects tied to this node.  
    	    	
        public Node() {
        	super();        	
        	objects0 = new HashSet<Object>();
        	objects1 = new HashSet<Object>();
        }

        public Node getChild(int n) {
        	return (Node)super.getChild(n);
        }

        /**
         * add new child node
         *
         * @param node new child node
         */
        public void addChild(Node node) {
            if (leftChild == null) {
                leftChild = node;
            } else if (rightChild == null) {
                rightChild = node;
            } else {
                throw new IllegalArgumentException("GraphModel.Nodes can only have 2 children");
            }
            if(node.parent==null){
            	node.parent = this;
            }else if(node.parent2==null){
            	node.parent2 = this;
            }else{
                throw new IllegalArgumentException("GraphModel.Nodes can only have 2 parents");
            }
        }

        /**
         * remove child
         *
         * @param node child to be removed
         */
        public Node removeChild(Node node) {
            if (leftChild == node) {
                leftChild = null;
            } else if (rightChild == node) {
                rightChild = null;
            } else {
                throw new IllegalArgumentException("Unknown child node");
            }
            if (node.parent == this) {
                node.parent = node.parent2;
                node.parent2 = null;
            } else if (node.parent2 == this) {
                node.parent2 = null;
            } else {
                throw new IllegalArgumentException("Unknown parent node");
            }
            return node;
        }

        /**
         * remove child
         *
         * @param n number of child to be removed
         */
        public Node removeChild(int n) {
            if (n == 0) {
                return removeChild((GraphModel.Node)leftChild);
            } else if (n == 1) {
                return removeChild((GraphModel.Node)rightChild);
            } else {
                throw new IllegalArgumentException("TreeModel.Nodes can only have 2 children");
            }
        }
        
        public final void setupHeightBounds() {
            heightParameter.addBounds(new GraphModel.NodeHeightBounds(heightParameter));
        }

        public HashSet<Object> getObjects(int i) {
        	return i == 0 ? objects0 : objects1;
        }
        public boolean hasObject(int i, Object o) {
        	return i == 0 ? objects0.contains(o) : objects1.contains(o);
        }
        public void addObject(int i, Object o){
        	if(i==0)
        		objects0.add(o);
       		else
       			objects1.add(o);
        }
        public void removeObject(int i, Object o){
        	if(i==0)
        		objects0.remove(o);
        	else
        		objects1.remove(o);
        }


        public String toString() {
            return "node " + number + ", height=" + getHeight() + (taxon != null ? ": " + taxon.getId() : "");
        }
        
        public String linksToString() {
        	StringBuilder sb = new StringBuilder();
        	sb.append("node " + number);
        	if(heightParameter!=null) sb.append(" (height= " + heightParameter.getParameterValue(0) + ")\t");
        	if(parent!=null) sb.append(" parent1 " + parent.number);
        	if(parent2!=null) sb.append(" parent2 " + parent2.number);
        	if(leftChild!=null) sb.append(" leftChild " + leftChild.number);
        	if(rightChild!=null) sb.append(" rightChild " + rightChild.number);
        	return sb.toString();
        }
    }
    protected class NodeHeightBounds extends TreeModel.NodeHeightBounds {
        public NodeHeightBounds(Parameter parameter) {
            super(parameter);
        }
        public Double getUpperLimit(int i) {
            Node node = (GraphModel.Node)getNodeOfParameter(nodeHeightParameter);
            if (node.isRoot()) {
                return Double.POSITIVE_INFINITY;
            } else if(node.parent2!=null){
                return Math.min(node.parent.getHeight(), node.parent2.getHeight());
            }else{
                return node.parent.getHeight();
            }
        }

        public Double getLowerLimit(int i) {
            Node node = (GraphModel.Node)getNodeOfParameter(nodeHeightParameter);
        	double l = node.leftChild != null ? node.leftChild.getHeight() : 0.0;
        	double r = node.rightChild != null ? node.rightChild.getHeight() : 0.0;
            return Math.max(l,r);
        }
    }
}
