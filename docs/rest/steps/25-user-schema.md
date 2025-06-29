Write a  Groovy script describing the model in JSON using Groovy builder like following:

```groovy
objectClass("User") {
     attribute("created") {
         jsonType "string";
         openApiFormat "date-time";
         description "Time when user was created.";
     }
    attribute("id") {
        jsonType "number";
        openApiFormat "int64";
        description "ID of user";
    }
}
```

The available attributes properties are:
 * `jsonType` - JSON Type which is used to represent data in JSON
 * `openApiFormat` - OpenAPI Format (from https://spec.openapis.org/registry/format/), which describes formatting of data, allowed values
 * `description` - Description of attribute as obtained from documentation
 * `required` - Is attribute required?
 * `creatable` - Can attribute be used during create operation, default is true, can be omitted.
 * `readable` -  Can attribute be read, default is true, can be omitted.
 * `updatable` - Can attribute be modified default is true, can be omitted.
 * `returnedByDefault` - Is attribute returned by default