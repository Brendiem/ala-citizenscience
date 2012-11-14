package au.com.gaiaresources.bdrs.controller.attribute.formfield;

import au.com.gaiaresources.bdrs.controller.attribute.DisplayContext;
import au.com.gaiaresources.bdrs.model.record.Record;
import au.com.gaiaresources.bdrs.model.survey.Survey;
import au.com.gaiaresources.bdrs.model.taxa.Attribute;
import au.com.gaiaresources.bdrs.model.taxa.AttributeScope;
import au.com.gaiaresources.bdrs.model.taxa.AttributeType;
import au.com.gaiaresources.bdrs.model.taxa.AttributeValue;
import au.com.gaiaresources.bdrs.model.taxa.TypedAttributeValue;

/**
 * The <code>RecordAttributeFormField</code> is a representation of a
 * configurable field on the record form that stores its value in a
 * {@link AttributeValue}.
 */
public class RecordAttributeFormField extends AbstractRecordFormField implements TypedAttributeValueFormField {
    private TypedAttributeValue recordAttribute;
    private Attribute attribute;

    /**
     * Creates a new <code>RecordAttributeFormField</code> for the specified
     * survey attribute.
     * 
     * @param survey
     *            the survey containing the record
     * @param record
     *            the record to be updated
     * @param attribute
     *            the attribute represented by this field.
     * @param recordAttribute
     *            the current value of this field or null
     * @param prefix
     *            the prefix to be prepended to input names.
     */
    RecordAttributeFormField(Survey survey, Record record, Attribute attribute,
            TypedAttributeValue recordAttribute, String prefix) {

        super(survey, record, prefix);

        this.attribute = attribute;
        this.recordAttribute = recordAttribute;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWeight() {
        return this.attribute.getWeight();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAttributeFormField() {
        return true;
    }

    public TypedAttributeValue getRecordAttribute() {
        return recordAttribute;
    }

    public void setRecordAttribute(TypedAttributeValue recordAttribute) {
        this.recordAttribute = recordAttribute;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public void setAttribute(Attribute attribute) {
        this.attribute = attribute;
    }

	@Override
	public TypedAttributeValue getAttributeValue() {
		return this.recordAttribute;
	}

	@Override
	public void setAttributeValue(TypedAttributeValue attributeValue) {
		if(!(attributeValue instanceof AttributeValue)) {
			throw new IllegalArgumentException(String.format("Attribute Value %s is not an instance of RecordAttribute", attributeValue));
		} 
		this.recordAttribute = (TypedAttributeValue) attributeValue;
	}
	
    @Override
    public boolean isDisplayFormField() {
        return attribute != null && AttributeType.isHTMLType(attribute.getType());
    }
    
    @Override
    public boolean isModerationFormField() {
        return attribute != null && AttributeScope.isModerationScope(attribute.getScope());
    }

    /**
     * Returns true if this RecordAttributeFormField should be visible in the supplied DisplayContext.
     * @param context the context to check the visibility in.
     * @return true if this RecordAttributeFormField should be visible.
     */
    @Override
    public boolean isVisible(DisplayContext context) {
        return attribute.isVisible(context);
    }
    
    /*
     * (non-Javadoc)
     * @see au.com.gaiaresources.bdrs.controller.attribute.formfield.AbstractRecordFormField#isRequired()
     */
    @Override
    public boolean isRequired() {
        return attribute.isRequired();
    }
}