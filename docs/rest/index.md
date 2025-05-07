# Implementing REST Connector

## Intial Steps in General

1. Get the documentation for service / application for which connector is written.
2. Determine connection & authentication parameters
3. Implement test connection
4. Determine an list of objects & concepts for identity & access management
4. Implement search support for User / account object class
    1. Get or Write schema for the object class
    2. Determine API endpoint for the object class
    3. Implement search handler for the object class
    4. (optional) implement filtering support for search
5. Implement search support for other object classes

## Additional tasks / steps

* Determine associations (relations) between object classes and how they are represented
* Implement support for associations for specific object classes
* Implement create operation for the object class
* Implement update operation for the object class
* Implement configuration discovery