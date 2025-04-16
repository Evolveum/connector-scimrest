import com.unboundid.scim2.common.types.AttributeDefinition;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;

public class SchemaConverter {


    void createAttribute(AttributeDefinition definition) {
        AttributeInfoBuilder attributeInfoBuilder = new AttributeInfoBuilder();
        attributeInfoBuilder.setName(definition.getName());


    }
}
