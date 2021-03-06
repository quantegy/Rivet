package org.e2k;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class UserIdentifier {
	
	private String errorMessage=null;
	
	// A public wrapper method that given a MMSI returns a ship object
	public Ship getShipDetails (String mmsi)	{
		try	{
			Ship xship=getShipFromXML(mmsi);
			if (xship!=null) return xship;
		}
		catch (Exception e)	{
			setErrorMessage(e.getMessage());
			return null;
		}
		return null;
	}
	
	// Return a ship object from the ships.xml file given an MMSI
	// if no ship exists then return a null
	private Ship getShipFromXML(String Tmmsi) throws SAXException, IOException,ParserConfigurationException {
			// Create a parser factory and use it to create a parser
			SAXParserFactory parserFactory=SAXParserFactory.newInstance();
			SAXParser parser=parserFactory.newSAXParser();
			// This is the name of the file you're parsing
			String filename="ships.xml";
			// Instantiate a DefaultHandler subclass to handle events
			ShipsXMLFileHandler handler=new ShipsXMLFileHandler();
			handler.setSearchMMSI(Tmmsi);
			// Start the parser. It reads the file and calls methods of the handler.
			parser.parse(new File(filename),handler);
			return handler.getShip();
	}
	
	
	public String getErrorMessage() {
		return errorMessage;
	}



	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}


	// This class handles the ships.xml SAX events
	public class ShipsXMLFileHandler extends DefaultHandler {
			String value,mmsi,name,flag;
			String searchMMSI;
			Ship foundShip=null;
			// Handle an XML start element
			public void endElement(String namespaceURI,String localName,String qName) throws SAXException {	
				// Look for a <ship> end tag
				if (qName.equals("ship"))	{
					// Do we have a MMSI match
					if (mmsi.equals(searchMMSI))	{
						// Put the values in a ship object
						Ship ship=new Ship();
						ship.setMmsi(mmsi);
						ship.setName(name);
						ship.setFlag(flag);
						// save this object
						foundShip=ship;
					}
				}
			}

			public void characters(char[] ch,int start,int length) throws SAXException {
				// Extract the element value as a string //
				String tval=new String(ch);
				value=tval.substring(start,(start+length));
			}
			
			// Handle an XML start element //
			public void startElement(String uri, String localName, String qName,Attributes attributes) throws SAXException {
				// Check an element has a value //
				if (attributes.getLength()>0) {
					// Get the elements value //
					String aval=attributes.getValue(0);
					// MMSI
					if (qName.equals("mmsi")) {
						mmsi=aval;
					}
					// Name
					if (qName.equals("name")) {
						name=aval;
					}					
					// Flag
					if (qName.equals("flag"))	{
						flag=aval;
					}
					
				}	
				
			}
			
			// Set the search MMSI
			public void setSearchMMSI (String ms)	{
				searchMMSI=ms;
			}
			
			// Return the ship that was found
			public Ship getShip()	{
				return foundShip;
			}
			
		}

}
