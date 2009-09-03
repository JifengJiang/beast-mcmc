/*
 * XMLDocumentationHandler.java
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

import java.io.*;
import java.util.*;

/**
 * A subclass of XMLDocumentationHandler that creates output in MediaWiki format
 */
public class WikiDocumentationHandler extends XMLDocumentationHandler {

    public WikiDocumentationHandler(XMLParser parser) {
        super(parser);
    }

    public void outputElements(PrintWriter writer) {

        writer.println("==BEAST elements==");
        writer.println("");
        writer.println("The following is a list of valid elements in a beast file.");
        writer.println("");

        Iterator iterator = parser.getParsers();
        while (iterator.hasNext()) {
            XMLObjectParser xmlParser = (XMLObjectParser)iterator.next();
            writer.println(xmlParser.toWiki(this));

            // convert the html into wiki...

            System.out.println("  outputting Wiki for element " + xmlParser.getParserName());
        }

    }


    /**
     * Outputs all types that appear as required attributes or elements in an HTML table to the given writer.
     */
    public void outputTypes(PrintWriter writer) {

        writer.println("==BEAST types==");
        writer.println("");
        writer.println("The following is a list of generic types that elements represent in a beast file.");
        writer.println("");


        // iterate through the types
        //Iterator iterator = requiredTypes.iterator();
        for (Class requiredType : requiredTypes) {
            if (requiredType != Object.class) {

                String name = ClassComparator.getName(requiredType);

                System.out.println("  outputting Wiki for generic type " + name);


                TreeSet<String> matchingParserNames = new TreeSet<String>();

                // find all parsers that match this required type
                Iterator i = parser.getParsers();
                while (i.hasNext()) {
                    XMLObjectParser xmlParser = (XMLObjectParser) i.next();
                    Class returnType = xmlParser.getReturnType();
                    if (requiredType.isAssignableFrom(returnType)) {
                        matchingParserNames.add(xmlParser.getParserName());
                    }
                }

                if (!(matchingParserNames.size() == 1 && matchingParserNames.iterator().next().equals(name))) {
                    
                    // output table row containing the type and the matching parser names
                    writer.println("===" + name + "===");
                    writer.println();
                    writer.println("Elements of this type include:");
                    writer.println();
                    i = matchingParserNames.iterator();
                    while (i.hasNext()) {
                        String parserName = (String) i.next();
                        writer.println(":*" + getWikiLink(parserName));
                    }
                    writer.println();
                }
            }

        }
    }


    public String getHTMLForClass(Class c) {
        return getWikiLink(ClassComparator.getName(c));
    }

    public String getWikiLink(String name) {
        if (Character.isUpperCase(name.charAt(0))) {
            // linking to a 'type'
            return "[[#" + name + "|" + name + "]]";
        } else {
            // linking to an 'element'
            return "[[#&lt;" + name + "&gt; element|" + name + "]]";
        }
    }
}