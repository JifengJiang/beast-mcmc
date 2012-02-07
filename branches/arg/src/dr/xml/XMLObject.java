/*
 * XMLObject.java
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

package dr.xml;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * This class wraps a DOM Element for the purposes of parsing.
 *
 * @version $Id: XMLObject.java,v 1.30 2005/07/11 14:06:25 rambaut Exp $
 * 
 * @author Alexei Drummond
 */
public class XMLObject {

	/**
	 * Creates an XMLObject from the given element and the given object store.
	 */
	public XMLObject(Element e, ObjectStore store) { 
		this.element = e;
//		this.store = store;
	}
	
	/**
	 * @return the number of children this XMLObject has.
	 */
	public final int getChildCount() { return children.size(); }
	
	/** 
	 * @return the ith child in native format if available, otherwise as
	 * an XMLObject.
	 */
	public Object getChild(int i) { 
		
		Object obj = getRawChild(i);
		
		if (obj instanceof XMLObject) {
			XMLObject xo = (XMLObject)obj;
			if (xo.hasNativeObject()) return xo.getNativeObject();
		} else if (obj instanceof Reference) {
			XMLObject xo = ((Reference)obj).getReferenceObject();
			if (xo.hasNativeObject()) return xo.getNativeObject();
		}
		return obj;
	}
		
	/** 
	 * @return the first child with a native format of the given class, or null if no such child exists.
	 */
	public Object getChild(Class c) { 
		
		for (int i =0; i < getChildCount(); i++) {
			Object child = getChild(i);
			if (c.isInstance(child)) {
				return child;
			}
		}
		return null;
	}
	
	/** 
	 * @return the first child of type XMLObject with a given name, or null if no such child exists.
	 */
	public Object getChild(String name) { 
		
		for (int i =0; i < getChildCount(); i++) {
			Object child = getChild(i);
			if (child instanceof XMLObject) {
				if (((XMLObject)child).getName().equals(name)) {
					return child;
				}
			}
		}
		return null;
	}
	
	/**
	 * Convenience method for getting the first child element out of a named XMLObject element
	 */
	public Object getSocketChild(String socketName) throws XMLParseException {
		Object socket = getChild(socketName);
		if (socket == null) throw new XMLParseException("Socket element called " + socketName + " does not exist inside element " + getName());
		if (!(socket instanceof XMLObject)) throw new XMLParseException("Socket element called " + socketName + " inside element " + getName() + " is not an XMLObject.");
		return ((XMLObject)socket).getChild(0);
	}
	
	public String getChildName(int i) { 
		
		Object obj = getRawChild(i);
		XMLObject xo;
		
		if (obj instanceof XMLObject) {
			xo = (XMLObject)obj;
		} else if (obj instanceof Reference) {
			xo = ((Reference)obj).getReferenceObject();
		} else {
			return "";
		}
		
		return xo.getName();
	}
	
	/**
	 * @return true if a child socket element of the given name exists.
	 */
	public boolean hasSocket(String socketName) {
		Object socket = getChild(socketName);
		return (socket != null) && (socket instanceof XMLObject);
	}
	
	/** @return the ith child as a double. */
	public boolean getBooleanChild(int i) throws XMLParseException { return getBoolean(getChild(i)); }

	/** @return the ith child as a double. */
	public double getDoubleChild(int i) throws XMLParseException { return getDouble(getChild(i)); }

	/** @return the ith child as a double[]. */
	public double[] getDoubleArrayChild(int i) throws XMLParseException { return getDoubleArray(getChild(i)); }

	/** @return the ith child as a double. */
	public int getIntegerChild(int i) throws XMLParseException { return getInteger(getChild(i)); }

	/** @return the ith child as a double. */
	public String getStringChild(int i) throws XMLParseException { return getString(getChild(i)); }

	/** @return the ith child as a double. */
	public String[] getStringArrayChild(int i) throws XMLParseException { return getStringArray(getChild(i)); }

	/** @return the named attribute */
	public Object getAttribute(String name) throws XMLParseException { return getAndTest(name); }
	
	/** @return the named attribute as a boolean. */
	public boolean getBooleanAttribute(String name) throws XMLParseException { return getBoolean(getAndTest(name)); }	
		
	/** @return the named attribute as a double. */
	public double getDoubleAttribute(String name) throws XMLParseException { return getDouble(getAndTest(name)); }	
	
	/** @return the named attribute as a double[]. */
	public double[] getDoubleArrayAttribute(String name) throws XMLParseException { return getDoubleArray(getAndTest(name)); } 

	/** @return the named attribute as a double[]. */
	public int[] getIntegerArrayAttribute(String name) throws XMLParseException { return getIntegerArray(getAndTest(name)); } 
	
	/** @return the named attribute as an integer. */
	public int getIntegerAttribute(String name) throws XMLParseException { return getInteger(getAndTest(name)); }

	/** @return the named attribute as a string. */
	public String getStringAttribute(String name) throws XMLParseException { return getString(getAndTest(name)); }	
	
	/** @return the named attribute as a String[]. */
	public String[] getStringArrayAttribute(String name) throws XMLParseException { return getStringArray(getAndTest(name)); }	
	
	/**
	 * @param valueList if this ArrayList is not null it is populated by the double array
	 * that the given string represents. 
	 * @return true if the given string represents a whitespaced-delimited array of doubles.
	 */
	public static boolean isDoubleArray(String s, ArrayList valueList) {
		try {
			StringTokenizer st = new StringTokenizer(s);
			while (st.hasMoreTokens()) {
				Double d = new Double(st.nextToken());
				if (valueList != null) valueList.add(d); 
			}
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	
	/** 
	 * @param valueList if this ArrayList is not null it is populated by the integer array
	 * that the given string represents. 
	 * @return true if the given string represents a whitespaced-delimited array of integers.
	 */
	public static boolean isIntegerArray(String s, ArrayList valueList) {
		try {
			StringTokenizer st = new StringTokenizer(s);
			while (st.hasMoreTokens()) {
				Integer d = new Integer(st.nextToken());
				if (valueList != null) valueList.add(d); 
			}
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}
	
	final static String ID = "id";
	public boolean hasId() { return hasAttribute(ID); }
	public String getId() throws XMLParseException { return getStringAttribute(ID); }
	
	/**
	 * @return true if either an attribute exists.
	 */
	public boolean hasAttribute(String name) { return (element.hasAttribute(name)); }
		
	public String getName() { return element.getTagName(); }
	
	public Object getNativeObject() { return nativeObject; }
	
	public boolean hasNativeObject() { return nativeObject != null; }
	
	public String toString() {
	
		String prefix = getName();
		if (hasId()) { 
			try {
				prefix += ":" + getId(); 
			} catch (XMLParseException e) { 
				// this shouldn't happen
			}
		}
		//if (nativeObject != null) return prefix + ":" + nativeObject.toString();
		return prefix;
	}
	
	public String content() {
		
		if (nativeObject != null) {
			if (nativeObject instanceof dr.util.XHTMLable) {
				return ((dr.util.XHTMLable)nativeObject).toXHTML();
			} else {
				return nativeObject.toString();
			}
		}
		return "";
	}
	
	//*********************************************************************
	// Package functions
	//*********************************************************************
	
	/** 
	 * Adds a child.
	 */
	void addChild(Object child) { 
		if (child instanceof XMLObject ||
			child instanceof Reference ||
			child instanceof String) {
		
			children.add(child);
		} else throw new IllegalArgumentException(); 
	}
		
	/**
	 * @return the ith child of this XMLObject, without processing.
	 */
	public Object getRawChild(int i) { return children.get(i); }
	
	/**
	 * Sets the native object represented by this XMLObject.
	 */
	public void setNativeObject(Object obj) {
		nativeObject = obj;
	}
	
	boolean isReference(int child) {
		return (getRawChild(child) instanceof Reference);
	}
	
	//*********************************************************************
	// Static members
	//*********************************************************************
	
	

	//*********************************************************************
	// Private methods
	//*********************************************************************

	/** @return the object as a boolean if possible */
	private boolean getBoolean(Object obj) throws XMLParseException { 
		
		if (obj instanceof Boolean) return ((Boolean)obj).booleanValue();
		if (obj instanceof String) {
			if (obj.equals("true")) return true;
			if (obj.equals("false")) return false;
		}
		throw new XMLParseException("Expected a boolean (true|false), but got " + obj);
	}

	/** @return the object as an double if possible */
	private double getDouble(Object obj) throws XMLParseException {
		if (obj instanceof Number) return ((Number)obj).doubleValue();
		try {
			return Double.parseDouble((String)obj); 
		} catch (NumberFormatException e) {
			throw new XMLParseException("Expected double precision number, but got " + obj);
		}
	}
	
	/** @return the object as an double[] if possible */
	private double[] getDoubleArray(Object obj) throws XMLParseException {
		
		if (obj instanceof Number) return new double[] {((Number)obj).doubleValue()};
		if (obj instanceof double[]) return (double[])obj;
		if (obj instanceof String) {
			ArrayList valueList = new ArrayList();
			if (isDoubleArray((String)obj, valueList)) {
				double[] values = new double[valueList.size()];
				for (int i =0; i < values.length; i++) {
					values[i] = ((Double)valueList.get(i)).doubleValue();
				}
				return values;
			} else {
				throw new XMLParseException("Expected array of double precision numbers, but got " + obj);
			}
		}
		throw new XMLParseException("Expected array of double precision numbers, but got " + obj);
	}
	
	/** @return the object as an double[] if possible */
	private int[] getIntegerArray(Object obj) throws XMLParseException {
		
		if (obj instanceof Number) return new int[] {((Number)obj).intValue()};
		if (obj instanceof int[]) return (int[])obj;
		if (obj instanceof String) {
			ArrayList valueList = new ArrayList();
			if (isIntegerArray((String)obj, valueList)) {
				int[] values = new int[valueList.size()];
				for (int i =0; i < values.length; i++) {
					values[i] = ((Number)valueList.get(i)).intValue();
				}
				return values;
			} else {
				throw new XMLParseException("Expected array of integers, but got " + obj);
			}
		}
		throw new XMLParseException("Expected array of integers, but got " + obj);
	}
	
	/** @return the object as an integer if possible */
	private int getInteger(Object obj) throws XMLParseException {
		if (obj instanceof Number) return ((Number)obj).intValue();
		try {
			return Integer.parseInt((String)obj); 
		} catch (NumberFormatException e) {
			throw new XMLParseException("Expected integer, got " + obj);
		}
	}
	
	/** @return the object as a string if possible */
	private String getString(Object obj) throws XMLParseException { 
		
		if (obj instanceof String) return (String)obj;
		throw new XMLParseException("Expected string, but got " + obj);
	}
	
	/** @return the object as an double[] if possible */
	private String[] getStringArray(Object obj) throws XMLParseException {
		
		if (obj instanceof String[]) return (String[])obj;
		if (obj instanceof String) {
			ArrayList stringList = new ArrayList();
			StringTokenizer st = new StringTokenizer((String)obj);
			while (st.hasMoreTokens()) {
				stringList.add(st.nextToken());
			}
			String[] strings = new String[stringList.size()];
			for (int i =0; i < strings.length; i++) {
				strings[i] = (String)stringList.get(i);
			}
			return strings;
		}
		throw new XMLParseException("Expected array of double precision numbers, but got " + obj);
	}

	/** @return the named attribute if it exists, throws XMLParseException otherwise. */
	private final Object getAndTest(String name) throws XMLParseException {
		
		if (element.hasAttribute(name)) { return element.getAttribute(name); } 
		throw new XMLParseException("'" + name + "' attribute was not found in " + element.getTagName() + " element.");
	}
	
	//*********************************************************************
	// Private instance variables
	//*********************************************************************

	private Vector children = new Vector();
	private Element element = null;	
	
	private Object nativeObject;

	// The objectStore representing the local scope of this element.
//	private ObjectStore store;
}