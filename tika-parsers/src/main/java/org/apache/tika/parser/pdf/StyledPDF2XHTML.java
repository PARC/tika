package org.apache.tika.parser.pdf;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.commons.collections4.map.HashedMap;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDF2XHTML;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.XHTMLContentHandler;
import org.bouncycastle.asn1.cmp.Challenge;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
Detect Bold and Italic
 */
public class StyledPDF2XHTML extends PDF2XHTML
{
    
	
	Map<String, String> font2HtmlMap;
	
	
    public StyledPDF2XHTML(PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
            PDFParserConfig config) throws IOException {
    		super(document, handler, context, metadata, config);
    		this.font2HtmlMap = new HashedMap<String, String>();
    		this.font2HtmlMap.put("Bold", "b");
    		this.font2HtmlMap.put("Italic", "i");
}

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException
    {
    	
    		Set<String> currentStyle = Collections.singleton("Undefined");
    		
    		List<String> stack = new ArrayList<String>();
    		 		
    		/*
    		 * Iterate over text positions - each text position is a character from PDF with position and font info
    		 */
    		
    		boolean first = true;
        for (TextPosition textPosition : textPositions)
        {
        	
        		if(first) {
	        		Matrix m = textPosition.getTextMatrix();
	        		writeLineInfo(m.getTranslateX(), m.getTranslateY(), textPosition.getFontSizeInPt(), textPosition.getFont().toString());
	        		first = false;
        		}
        	
            Set<String> style = determineStyle(textPosition);
            
            if (!style.equals(currentStyle))
            {
            	
            		for(int i = stack.size()-1; i >= 0; i--) {
            			
            			String item = stack.get(i);
            			
            			if(!style.contains(item)) {
            				this.unstackItem(item, i, stack);
            			}
            		}
            		
            		for(String item : style) {
            			if(!stack.contains(item)) {
            				this.stackItem(item, stack);
            			}
            		}
            		
            	currentStyle = style;		
            	}   
            
            try {           	
	            xhtml.characters(textPosition.getUnicode());
            }catch(SAXException ex) {
            	ex.printStackTrace();
            }
            
        }
        
        for(int i = stack.size()-1; i >= 0; i--) {
        		this.endElement(this.font2HtmlMap.get(stack.get(i)));
        }
        
    }

    /**
     * Converts the given PDF document (and related metadata) to a stream
     * of XHTML SAX events sent to the given content handler.
     *
     * @param document PDF document
     * @param handler  SAX content handler
     * @param metadata PDF metadata
     * @throws SAXException  if the content handler fails to process SAX events
     * @throws TikaException if there was an exception outside of per page processing
     */
    public static void process(
            PDDocument document, ContentHandler handler, ParseContext context, Metadata metadata,
            PDFParserConfig config)
            throws SAXException, TikaException {
        StyledPDF2XHTML pdf2XHTML = null;
        try {
            // Extract text using a dummy Writer as we override the
            // key methods to output to the given content
            // handler.
            pdf2XHTML = new StyledPDF2XHTML(document, handler, context, metadata, config);
            config.configure(pdf2XHTML);

            pdf2XHTML.writeText(document, new Writer() {
                @Override
                public void write(char[] cbuf, int off, int len) {
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        } catch (IOException e) {
            if (e.getCause() instanceof SAXException) {
                throw (SAXException) e.getCause();
            } else {
                throw new TikaException("Unable to extract PDF content", e);
            }
        }
        if (pdf2XHTML.exceptions.size() > 0) {
            //throw the first
            throw new TikaException("Unable to extract PDF content", pdf2XHTML.exceptions.get(0));
        }
    }

    private void stackItem(String item, List<String> stack) throws IOException {
    	
    		if(!stack.contains(item)){
    			stack.add(item);
    			this.startElement(this.font2HtmlMap.get(item));
    		}
    }
    
    private void unstackItem(String item, int itemIndex, List<String> stack) throws IOException {
    	
    		for(int i = stack.size() -1; i>itemIndex; i--) {
    			this.endElement(this.font2HtmlMap.get((stack.get(i))));
    		}
    	
    		this.endElement(this.font2HtmlMap.get(item));
    		
    		for(int i = stack.size() -1; i>itemIndex; i--) {
    			this.startElement(this.font2HtmlMap.get((stack.get(i))));
    		}
    		
    		stack.remove(itemIndex);

    }
    
    
    private void startElement(String element) throws IOException {
    	
		try {
				xhtml.startElement(element);
	    } catch (SAXException e) {
	        throw new IOException("Unable to start "+element, e);
	    }
    	
    }
    
    private void endElement(String element) throws IOException {
    	
		try {
				xhtml.endElement(element);
	    } catch (SAXException e) {
	        throw new IOException("Unable to end "+element, e);
	    }
    	
    }
  
    private Set<String> determineStyle(TextPosition textPosition)
    {
        Set<String> result = new HashSet<String>();

        for(String fontStyle : this.font2HtmlMap.keySet()) {
        
	        if (textPosition.getFont().getName().toLowerCase(Locale.getDefault()).contains(fontStyle.toLowerCase(Locale.getDefault())))
	            result.add(fontStyle);
        }
        

        return result;
    }
    
    @Override
    protected void writeLineSeparator() throws IOException {

        		writeParagraphEnd();
        		writeParagraphStart();

    }
    
    private void writeLineInfo(float X, float Y, float fontSize, String font) throws IOException {
    	
    	try {
	        AttributesImpl attributes = new AttributesImpl();
	
	        attributes.addAttribute(XHTMLContentHandler.XHTML, "x_pos", "x_pos", "", Float.toString(X));
	        attributes.addAttribute(XHTMLContentHandler.XHTML, "y_pos", "y_pos", "", Float.toString(Y));
	        attributes.addAttribute(XHTMLContentHandler.XHTML, "font_size", "font_size", "", Float.toString(fontSize));
	        attributes.addAttribute(XHTMLContentHandler.XHTML, "font_type", "font_type", "", font);
	        
			xhtml.startElement("span", attributes);
			xhtml.endElement("span");
		} catch (SAXException e) {
			throw new IOException("Unable to end span");
		}
    }

}
