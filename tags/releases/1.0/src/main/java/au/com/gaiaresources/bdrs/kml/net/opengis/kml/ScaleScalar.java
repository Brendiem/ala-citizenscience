//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-661 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2008.12.16 at 01:50:30 PM WST 
//


package au.com.gaiaresources.bdrs.kml.net.opengis.kml;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

public class ScaleScalar
    extends JAXBElement<Double>
{

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	protected final static QName NAME = new QName("http://www.opengis.net/kml/2.2", "scale");

    @SuppressWarnings("unchecked")
    public ScaleScalar(Double value) {
        super(NAME, ((Class) Double.class), null, value);
    }

}